package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.MessageEntity
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.DayPlanPolicy
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 日程预排的读写与每日审查。
 *
 * 状态机与判定全在 [DayPlanPolicy] 里（纯逻辑、有单测），这里只做三件事：
 * 从库里读、按模型的提案写、把未来这一段投影成 system 里的一块。
 *
 * ## 什么时候审一次
 *
 * 由调度器每拍调用 [reviewIfDue]，它自己判"该不该跑"：
 * - **同一天只审一次**（[DayPlanPolicy.Plan.reviewedOn]）；
 * - 本地时间要过了 [DayPlanPolicy.REVIEW_AFTER_HOUR] 点；
 * - 失败要退避（[RETRY_BACKOFF_MS]），不然每拍都会去撞一次模型。
 *
 * ## 模型只许给四种结论
 *
 * 输出第一行是结论（`UNCHANGED` / `EXTEND` / `PATCH` / `REPLACE`），后面每行一个块
 * `KIND HH:MM-HH:MM 说明`。**任何一行不合法就整体拒绝**，旧计划继续有效——
 * 见 [DayPlanPolicy.parseProposal] 与 [DayPlanPolicy.apply]。
 *
 * ## 提示词
 *
 * 内置常量，不走网关远程提示词：远程那份要先有人在 `prompts/` 下建文件，
 * 否则就是"代码支持一个没人提供的 key"。要调风格直接改这里（或以后统一搬过去）。
 */
@Singleton
class DayPlanCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val client: OpenAiCompatibleClient,
    private val cipher: CompanionSecretCipher
) {

    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val lastAttemptAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** 读她的日程；没有就返回空计划。 */
    suspend fun planFor(characterId: String): DayPlanPolicy.Plan = runCatching {
        dao.worldStates(characterId, DayPlanPolicy.SCOPE)
            .maxByOrNull { it.updatedAt }
            ?.content
            ?.let(DayPlanPolicy.Plan::fromJson)
            ?: DayPlanPolicy.Plan()
    }.getOrDefault(DayPlanPolicy.Plan())

    /**
     * 投影成 system 里的一小块（从此刻起未来约 12 小时）。
     *
     * 没有块、或她这会儿没有安排时返回空串——调用方据此**不 append 任何东西**。
     */
    suspend fun blockFor(characterId: String, now: Long, zone: ZoneId): String {
        val plan = planFor(characterId)
        if (plan.blocks.isEmpty()) return ""
        val local = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        val minuteOfDay = local.hour * 60 + local.minute
        val blocks = DayPlanPolicy.resolve(
            plan = plan,
            dateKey = local.toLocalDate().toString(),
            weekday = local.dayOfWeek.value,
            fromMinute = minuteOfDay
        )
        return DayPlanPolicy.render(blocks)
    }

    /**
     * 该审就审一次。
     *
     * [recent] 是最近的对话（用来让计划贴合她真的提过的事）。返回是否真的跑了模型。
     */
    suspend fun reviewIfDue(
        characterId: String,
        threadId: String?,
        recent: List<MessageEntity>,
        now: Long,
        zone: ZoneId
    ): Boolean {
        val local = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        val dateKey = local.toLocalDate().toString()
        val plan = planFor(characterId)
        if (!DayPlanPolicy.needsReview(plan, dateKey, local.hour)) return false
        val lastAttempt = lastAttemptAt[characterId] ?: 0L
        if (now - lastAttempt < RETRY_BACKOFF_MS) return false

        val lock = locks.getOrPut(characterId) { Mutex() }
        if (lock.isLocked) return false
        return lock.withLock {
            // 拿到锁之后再确认一次：这一拍可能已经被别的路径审过了。
            val fresh = planFor(characterId)
            if (!DayPlanPolicy.needsReview(fresh, dateKey, local.hour)) return@withLock false
            val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL) ?: return@withLock false
            if (!profile.enabled || profile.encryptedApiKey.isNullOrBlank()) return@withLock false
            val character = dao.character(characterId) ?: return@withLock false
            lastAttemptAt[characterId] = now
            val raw = runCatching {
                client.chat(
                    profile = profile,
                    apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey)),
                    systemPrompt = reviewSystem(character.name),
                    userPrompt = reviewUser(fresh, dateKey, recent)
                ).getOrNull()
            }.getOrNull() ?: return@withLock false

            val review = DayPlanPolicy.parseReview(raw)
            val next = DayPlanPolicy.apply(fresh, review.proposal, review.blocks, dateKey, now)
            persist(characterId, next, now)
            true
        }
    }

    private suspend fun persist(characterId: String, plan: DayPlanPolicy.Plan, now: Long) {
        runCatching {
            dao.upsertWorldState(
                WorldStateEntity(
                    id = DayPlanPolicy.rowId(characterId),
                    characterId = characterId,
                    threadId = null,
                    scope = DayPlanPolicy.SCOPE,
                    content = plan.toJson(),
                    updatedAt = now
                )
            )
        }
    }

    private fun reviewSystem(characterName: String): String = buildString {
        appendLine("你在给一个角色排**今天大概会怎么过**。这不是剧本，是软约束的日程。")
        appendLine("第一行只输出一个结论词，必须是下列之一：")
        appendLine("UNCHANGED（不用改）／EXTEND（在现有基础上追加）／PATCH（只改某几段）／REPLACE（整体重排）")
        appendLine("从第二行起，每行一个时间块，格式**严格**是：")
        appendLine("KIND HH:MM-HH:MM 说明")
        appendLine("KIND 只能是 FIXED（上学/考试/明确约定）、ROUTINE（通勤/午休/作息）、FLEXIBLE（爱好或可调）、OPEN（空闲）")
        appendLine("说明不超过 ${DayPlanPolicy.MAX_TITLE_CHARS} 字中文，不要标点、不要引号。")
        appendLine("结论是 UNCHANGED 时后面不要写任何块。")
        appendLine("禁止输出 markdown、代码块、解释、多余的空行。")
        appendLine("只依据给她的资料和最近对话里她真的提过的事，**不要编造**她没提过的安排。")
        appendLine("角色名：${characterName.trim()}")
    }.trim()

    private fun reviewUser(plan: DayPlanPolicy.Plan, dateKey: String, recent: List<MessageEntity>): String = buildString {
        appendLine("今天：$dateKey")
        if (plan.blocks.isEmpty()) {
            appendLine("她目前没有任何日程记录。请给出一份**平稳、可信、不需要特别事件**的日常安排，")
            appendLine("覆盖醒来之后的白天即可；不确定的地方用 OPEN 或 ROUTINE，不要编具体约会。")
        } else {
            appendLine("她现有的日程（块越硬越该保留）：")
            plan.blocks.forEach { block ->
                appendLine(
                    "- ${block.kind.name} ${DayPlanPolicy.formatMinute(block.startMinute)}-" +
                        "${DayPlanPolicy.formatMinute(block.endMinute)} ${block.title}"
                )
            }
        }
        val excerpt = recent.filter { it.body.isNotBlank() }.takeLast(EXCERPT_MESSAGES)
        if (excerpt.isNotEmpty()) {
            appendLine("最近对话里她提过的事（是否要并进日程由你判断）：")
            excerpt.forEach { message ->
                val speaker = if (message.fromUser) "User" else "她"
                appendLine("- $speaker: ${message.body.trim().replace(WHITESPACE, " ").take(EXCERPT_CHARS)}")
            }
        }
    }.trim()

    private companion object {
        /** 失败之后的退避：不然每拍都会去撞一次模型。 */
        const val RETRY_BACKOFF_MS = 30 * 60_000L
        const val EXCERPT_MESSAGES = 12
        const val EXCERPT_CHARS = 80
        val WHITESPACE = Regex("\\s+")
    }
}
