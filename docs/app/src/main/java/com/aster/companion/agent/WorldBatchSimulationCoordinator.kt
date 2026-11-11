package com.aster.companion.agent

import android.util.Log
import com.aster.companion.agent.world.WorldLocationCatalog
import com.aster.companion.data.CharacterEntity
import com.aster.companion.data.CharacterLivingStateEntity
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.EventEntity
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.domain.CharacterSchedule
import com.aster.companion.domain.DurationClass
import com.aster.companion.domain.DurationPolicy
import com.aster.companion.data.MessageEntity
import com.aster.companion.data.PendingActionEntity
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.ActionTransport
import com.aster.companion.update.RemotePrompts
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 世界引擎核心：定期把全部角色的生活状态 + 近期事件组装成一次批量推演请求，
 * 交给模型统一推演，再把「确实发生变化」的角色状态写回库。
 *
 * 变化驱动：只有模型明确给出 changed=true 的角色才会被 upsert，
 * 其余角色保留现状；模型只负责报告情绪/活动/时长分类，
 * 睡与醒也由模型的 duration_class 决定（不读作息表），[SleepStateMachine] 只把它换算成醒来时刻。
 */
@Singleton
class WorldBatchSimulationCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val client: OpenAiCompatibleClient,
    private val secretCipher: CompanionSecretCipher,
    private val remotePrompts: RemotePrompts
) {
    /** 上一次批量推演的触发时间；未满节流间隔时直接跳过。 */
    private val lastBatchSimulationAt = AtomicLong(0L)

    /** 分享处理中的角色（per-character 并发锁，防双卡）。 */
    private val shareInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * 节流入口：两次推演间隔不足 [MIN_INTERVAL_MS] 直接返回。
     * 通过 CAS 抢占「本轮推演权」，避免并发重复请求模型。
     */
    suspend fun simulateDue(now: Long) {
        val last = lastBatchSimulationAt.get()
        val withinCooldown = now - last < MIN_INTERVAL_MS
        // 对话静默 1 分钟触发：任一角色最近有消息、且对话停止满 1 分钟、且那条消息发生在上次推演之后
        // → 提前推演（结合刚结束的对话更新角色状态），不等 3 分钟节流。
        val postConversationTrigger = if (withinCooldown) {
            runCatching {
                dao.allCharacters().any { character ->
                    // 私聊线程：对话停止满 1 分钟
                    val privateLast = dao.latestPrivateThread(character.id)
                        ?.let { thread -> dao.recentMessages(thread.id, 1).firstOrNull()?.createdAt }
                    if (privateLast != null && now - privateLast >= POST_CONVERSATION_DELAY_MS && privateLast > last) return@any true
                    // 参与的群聊线程：群聊停止满 1 分钟（角色知道群里的动静）
                    val groupLast = dao.allThreads()
                        .filter { it.type == com.aster.companion.domain.ConversationType.GROUP }
                        .filter { group -> dao.participantIds(group.id).contains(character.id) }
                        .mapNotNull { group -> dao.recentMessages(group.id, 1).firstOrNull()?.createdAt }
                        .maxOrNull()
                    groupLast != null && now - groupLast >= POST_CONVERSATION_DELAY_MS && groupLast > last
                }
            }.getOrDefault(false)
        } else false
        if (withinCooldown && !postConversationTrigger) return
        if (!lastBatchSimulationAt.compareAndSet(last, now)) return
        try {
            simulateBatch(now)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "世界批量推演失败", error)
        }
    }

    /** 即时推演：对话发生后立即同步所有角色状态（绕过节流）。 */
    suspend fun forceSimulate(now: Long = System.currentTimeMillis()) {
        lastBatchSimulationAt.set(now)
        try {
            simulateBatch(now)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "世界即时推演失败", error)
        }
    }

    private suspend fun simulateBatch(now: Long) {
        runCatching { remotePrompts.refreshIfStale() }
        val profile = resolveProfile() ?: run {
            Log.d(TAG, "世界推演模型未配置，本次跳过")
            return
        }
        val states = dao.allLivingStates().associateBy { it.characterId }
        val characters = dao.allCharacters()
        val occupiedCharacterIds = com.aster.companion.agent.world.WorldActivityOccupancy.characterIds(
            dao.runningActivities().map { it.id to it.participantsJson }
        )
        val pendingEvents = dao.pendingEvents().take(MAX_EVENT_CONTEXT)
        val apiKey = requireNotNull(profile.encryptedApiKey) { "世界推演模型缺少 API Key" }
        val userPrompt = buildUserPrompt(now, states, characters, pendingEvents)
        val apiKeyDecrypted = secretCipher.decrypt(apiKey)
        // system 优先用网关上的远程版本：改提示词不用重装 App。
        val systemPrompt = remotePrompts.get("world.simulation") ?: SYSTEM_PROMPT
        var raw = client.chat(
            profile = profile,
            apiKey = apiKeyDecrypted,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            longRunning = true
        ).getOrElse { error ->
            Log.w(TAG, "世界推演模型请求失败", error)
            return
        }
        var plan = WorldBatchPlanParser.parse(raw)
        // 世界引擎稳定性：JSON 解析失败（模型抽风）或没有任何角色变化时，重试一次再放弃。
        if (plan.isEmpty()) {
            raw = client.chat(
                profile = profile,
                apiKey = apiKeyDecrypted,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                longRunning = true
            ).getOrElse { error ->
                Log.w(TAG, "世界推演重试请求失败", error)
                return
            }
            plan = WorldBatchPlanParser.parse(raw)
        }
        applyPlan(plan, states, characters.associateBy { it.id }, occupiedCharacterIds, now)
    }

    /** 把模型确认变化的条目写回库；sleepUntil 由作息 + 睡眠状态机推导。 */
    private suspend fun applyPlan(
        plan: WorldBatchPlan,
        existingStates: Map<String, CharacterLivingStateEntity>,
        characters: Map<String, CharacterEntity>,
        occupiedCharacterIds: Set<String>,
        now: Long
    ) {
        plan.changedPlans().forEach { entry ->
            val characterId = entry.character_id ?: return@forEach
            if (characterId in occupiedCharacterIds) return@forEach
            val state = existingStates[characterId]
            // 未知角色不落库：世界引擎只维护已存在的角色。
            if (state == null && characters[characterId] == null) return@forEach
            val schedule = state?.let { CharacterSchedule.fromJson(it.scheduleJson) } ?: CharacterSchedule.DEFAULT
            val previousActivity = state?.currentActivity
            val activity = entry.activity?.trim().takeUnless { it.isNullOrBlank() }
                ?: previousActivity
                ?: DEFAULT_ACTIVITY
            val mood = entry.mood?.trim().takeUnless { it.isNullOrBlank() }
                ?: state?.mood
                ?: DEFAULT_MOOD
            // 活动发生变化才重置开始时刻；延续同一活动则保留原开始时刻。
            val activityStartedAt = if (activity != previousActivity) {
                now
            } else {
                state?.activityStartedAt?.takeIf { it > 0 } ?: now
            }
            // 睡与醒只听模型推演：duration_class=SLEEP 就是睡了，不再拿作息表覆盖它。
            val sleepUntil = if (entry.durationClass == DurationClass.SLEEP) {
                SleepStateMachine.nextSleepUntil(state, now, entry.sleep_minutes)
            } else {
                null
            }
            dao.upsertLivingState(
                CharacterLivingStateEntity(
                    characterId = characterId,
                    mood = mood,
                    currentActivity = activity,
                    activityStartedAt = activityStartedAt,
                    sleepUntil = sleepUntil,
                    scheduleJson = schedule.toJson(),
                    updatedAt = now,
                    locationId = resolveLocationId(activity, state?.locationId)
                )
            )
            deliverMemory(characterId, entry.memory)
            // 生活分享出口：这次推演出她想给用户看什么 → 检查后创建分享确认卡
            runCatching { maybeOfferShare(characterId, entry.share_moment, now) }
        }
    }

    /**
     * 从 activity 文本解析结构化地点 id：形如「在图书馆，靠窗翻书」→ 取去掉开头「在」之后、
     * 第一个逗号（中文「，」或英文 ","）之前的片段 → 交给 [WorldLocationCatalog.matchByName] 匹配；
     * 匹配不到说明她在外面但地点不明确 → outside 的 id；activity 为空则保持上一轮的地点。
     */
    private fun resolveLocationId(activity: String?, previousLocationId: String?): String {
        val text = activity?.trim().orEmpty()
        if (text.isBlank()) {
            return previousLocationId ?: WorldLocationCatalog.UNKNOWN_ID
        }
        val head = text.removePrefix("在").trim()
            .substringBefore('，')
            .substringBefore(',')
            .trim()
        // 匹配不到就保持原地点：模型的口语说法（"宿舍""床上""路上"）本来就可能不在目录里，
        // 一律当成"在外面"会让她明明在家却被显示成在外面。宁可不变，也不要乱跳。
        return WorldLocationCatalog.matchByName(head)
            ?: previousLocationId
            ?: WorldLocationCatalog.UNKNOWN_ID
    }

    /**
     * 生活分享：世界推演出她想分享什么 → 冷却/作息/不打断用户检查 → 角色发分享消息 + 创建确认卡（companion.share_life_moment）。
     * 用户同意后走标准生图链路（isImageGeneration 已包含 share_life_moment），scene 在创建时生成好。
     */
    private suspend fun maybeOfferShare(characterId: String, share: ShareMoment?, now: Long) {
        if (share?.want != true) return
        val what = share.what?.trim()?.take(100).orEmpty()
        if (what.isBlank()) return
        // per-character 并发锁：同一角色同时只处理一次分享提议（防双卡）
        if (!shareInFlight.add(characterId)) return
        try {
        // 冷却 2 小时：最近一次分享没过期就不重复提议（先落冷却，避免中途异常后下轮重复创建）
        val lastShare = dao.worldStates(characterId, "LAST_SHARE").maxByOrNull { it.updatedAt }
        if (lastShare != null && now - lastShare.updatedAt < SHARE_COOLDOWN_MS) return
        // 睡眠中不分享
        val living = dao.livingState(characterId)
        if (living?.sleepUntil?.let { now < it } == true) return
        // 目标：用户私聊线程（默认分享给用户）
        val targetThread = dao.latestPrivateThread(characterId) ?: return
        // 已有未处理的分享确认卡：不重复创建（防叠卡）
        val pendingShare = runCatching {
            dao.pendingActionsForThread(targetThread.id).any { it.capabilityId == "companion.share_life_moment" && it.state == "PENDING" }
        }.getOrDefault(false)
        if (pendingShare) return
        // 用户在聊天（最近一条【用户】消息 5 分钟内）不插播，等聊完再提
        val lastUserMessage = dao.recentMessages(targetThread.id, 10).asReversed().firstOrNull { it.fromUser }
        if (lastUserMessage != null && now - lastUserMessage.createdAt < USER_CHAT_GRACE_MS) return
        // 角色必须先写出最终英文 ComfyUI prompt；失败时不创建伪造图片任务。
        val scene = generateShareScene(characterId, targetThread.id, what) ?: return
        // 先落冷却（防异常后重复创建）
        dao.upsertWorldState(WorldStateEntity(id = "last-share:${UUID.randomUUID()}", characterId = characterId, threadId = targetThread.id, scope = "LAST_SHARE", content = what, updatedAt = now))
        val words = share.words?.trim()?.take(200).orEmpty().ifBlank { "你看，$what" }
        // 1) 角色先发分享消息（话）
        dao.insertMessage(MessageEntity(UUID.randomUUID().toString(), targetThread.id, characterId, words, null, false, now))
        dao.touchThread(targetThread.id, now)
        // 2) 创建分享确认卡（用户同意后才生图）
        dao.upsertPendingAction(
            PendingActionEntity(
                id = "share:${UUID.randomUUID()}",
                eventId = null,
                threadId = targetThread.id,
                capabilityId = "companion.share_life_moment",
                transport = ActionTransport.LOCAL.name,
                argumentsJson = shareArguments(mapOf("what" to what, "words" to words, "scene" to scene, "character_id" to characterId)),
                state = "PENDING",
                createdAt = now,
                resolvedAt = null,
                resultSummary = null
            )
        )
        } finally {
            shareInFlight.remove(characterId)
        }
    }

    /** 由角色模型根据完整可见上下文直接产出最终英文 ComfyUI 正向提示词。 */
    private suspend fun generateShareScene(characterId: String, threadId: String, what: String): String? {
        val character = dao.character(characterId) ?: return null
        val living = dao.livingState(characterId)
        val activity = living?.currentActivity.orEmpty()
        val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
            ?: return null
        val memorySince = dao.thread(threadId)?.let(ConversationMemoryVisibilityPolicy::boundaryFor) ?: 0L
        val visibleHistory = GatewayConversationHistoryPolicy.visibleMessages(
            dao.allMessagesForThread(threadId), memorySince
        )
        val transcript = buildString {
            visibleHistory.forEachIndexed { index, message ->
                if (index > 0) append('\n')
                val speaker = if (message.fromUser) "User" else dao.character(message.characterId.orEmpty())?.name ?: "Character"
                append("$speaker: ${message.body}")
            }
        }.ifBlank { "(no earlier conversation)" }
        return client.chat(
            profile = profile,
            apiKey = secretCipher.decrypt(requireNotNull(profile.encryptedApiKey)),
            systemPrompt = buildString {
                appendLine(ComfyUiEnglishPromptPolicy.hardRule(character.name))
                appendLine("You are deciding the exact image this character wants to share. Return only one final English ComfyUI positive production prompt, with no JSON, markdown, translation, relationship text, or delivery instructions.")
            },
            userPrompt = "CHARACTER APPEARANCE: ${character.appearancePrompt.take(1_800)}\nCURRENT ACTIVITY: ${activity.ifBlank { "quietly resting" }}\nSHARE INTENT: $what\nCOMPLETE VISIBLE CONVERSATION:\n$transcript"
        ).getOrNull()?.let { ComfyUiEnglishPromptPolicy.finalPromptOrNull(it, 1_000) }
    }

    private fun shareArguments(map: Map<String, String>): String = buildJsonObject {
        map.forEach { (key, value) -> put(key, value) }
    }.toString()

    /**
     * 记忆转交：记忆写入入口（MemoryWriteCoordinator）可能由其他子模块提供。
     * 这里只做存在性探测并记录日志，保证类不存在时静默跳过、不影响状态落库。
     */
    private fun deliverMemory(characterId: String, memory: String?) {
        val note = memory?.trim().orEmpty()
        if (note.isBlank()) return
        val bridgeReady = runCatching {
            Class.forName("com.aster.companion.agent.MemoryWriteCoordinator") != null
        }.getOrDefault(false)
        if (bridgeReady) {
            Log.i(TAG, "角色 $characterId 产出记忆（记忆入口已就绪）：${note.take(120)}")
        } else {
            Log.d(TAG, "角色 $characterId 产出记忆，但记忆写入入口未就绪，本轮跳过")
        }
    }

    private suspend fun resolveProfile(): ModelProfileEntity? {
        val planning = dao.modelProfile(AgentTurnCoordinator.PLANNING_MODEL)
        if (planning?.enabled == true && !planning.encryptedApiKey.isNullOrBlank()) return planning
        return dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
    }

    private suspend fun buildUserPrompt(
        now: Long,
        states: Map<String, CharacterLivingStateEntity>,
        characters: List<CharacterEntity>,
        events: List<EventEntity>
    ): String = buildString {
        val zone = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(zone)
        appendLine("当前时间：${formatter.format(Instant.ofEpochMilli(now))}（本地时区）")
        if (events.isNotEmpty()) {
            appendLine()
            appendLine("最近事件（待处理，供你判断角色是否受其影响）：")
            events.forEach { event ->
                appendLine("- [${event.source}] ${event.summary}")
            }
        }
        appendLine()
        appendLine("角色当前状态：")
        characters.forEach { character ->
            val state = states[character.id]
            // 人设：让推演模型知道这个角色是谁、该过什么样的生活。**完整带上原文、不截断**——
            // 人设里往往含身份、生活场景、作息这类推演必需的信息，截断恰好会丢掉它们；
            // 推演是批量请求，上下文足够。人设为空时不加这一段。
            val personality = character.personalityPrompt.trim()
            val personalityNote = if (personality.isBlank()) "" else "；人设=$personality"
            if (state == null) {
                appendLine("- 角色「${character.name}」(id=${character.id})：尚无状态记录，请按人格初始化其心情与活动。$personalityNote")
            } else {
                val elapsed = (now - state.activityStartedAt).coerceAtLeast(0L)
                val duration = DurationPolicy.classify(elapsed).name
                val sleepInfo = when {
                    state.sleepUntil != null && now < state.sleepUntil ->
                        "睡眠中，计划醒于 ${formatter.format(Instant.ofEpochMilli(state.sleepUntil))}"
                    state.sleepUntil != null ->
                        "已睡到计划时间（原计划醒于 ${formatter.format(Instant.ofEpochMilli(state.sleepUntil))}），该让她醒来了"
                    else -> "清醒"
                }
                // 当前结构化地点：让她换地方时推演知道她此刻在哪，不会一直待在同一处。
                val locationName = dao.worldLocation(state.locationId)?.displayName ?: state.locationId
                appendLine(
                    "- 角色「${character.name}」(id=${character.id})：心情=${state.mood}；" +
                        "活动=${state.currentActivity}（已持续 ${elapsed / 60_000L} 分钟，时长分类 $duration）；$sleepInfo" +
                        personalityNote +
                        "；当前地点=$locationName"
                )
            }
            // 最近对话：让推演响应角色与用户的交流（用户说的事 = 角色正在做的）。
            val recentChat = dao.latestPrivateThread(character.id)
                ?.let { thread -> dao.recentMessages(thread.id, 8).asReversed() }
                .orEmpty()
            // 是否「正在私聊」：最后一条私聊消息在 5 分钟内 = 用户此刻正在和她说话。
            // 只有这个信号能把「正在聊」和「几小时前聊过」分开 —— 喂给模型的对话文本不带时间戳，
            // 光靠文本模型会把她永久冻在最后一次对话上，等于把自主生活变相取消。
            val lastPrivateAt = recentChat.lastOrNull()?.createdAt
            val inActiveChat = lastPrivateAt != null && now - lastPrivateAt <= USER_CHAT_GRACE_MS
            if (recentChat.isNotEmpty()) {
                if (inActiveChat) {
                    val chatMinutesAgo = ((now - (lastPrivateAt ?: now)) / 60_000L).coerceAtLeast(0L)
                    appendLine("    ※※ 用户【正在】和 ${character.name} 私聊（最后一条消息 ${chatMinutesAgo} 分钟前）：本轮她不受「已持续时长」限制，状态必须立刻跟这段对话走——用户说了要做什么、去了哪里、刚发生了什么，她的活动与心情就改成那个，changed 必须为 true。")
                }
                appendLine("    ${character.name} 与用户的最近对话（**状态必须响应对话**：用户最后说的、让做的事、提到的事 = 她正在做/刚做/马上要做的；绝不能在对话明明说了某件事之后，状态还写无关的内容。用户最后一句是最重要的——她刚说完什么，状态就要围着它转）：")
                recentChat.forEach { message ->
                    val speaker = if (message.fromUser) "用户" else character.name
                    appendLine("    - $speaker：${message.body.take(60)}")
                }
            }
            // 参与的群聊最近内容：群里的动静也是角色生活的一部分（状态可响应群聊）。
            val groupFeed = dao.allThreads()
                .filter { it.type == com.aster.companion.domain.ConversationType.GROUP }
                .filter { group -> dao.participantIds(group.id).contains(character.id) }
                .flatMap { group ->
                    dao.recentMessages(group.id, 3).asReversed().map { message ->
                        val speaker = if (message.fromUser) "用户" else (dao.character(message.characterId.orEmpty())?.name ?: "群成员")
                        "「${group.title}」$speaker：${message.body.take(40)}"
                    }
                }
                .takeLast(8)
            if (groupFeed.isNotEmpty()) {
                appendLine("    ${character.name} 参与的群聊最近内容（群里的动静她自然知道，状态可适当响应）：")
                groupFeed.forEach { appendLine("    - $it") }
            }
        }
    }

    companion object {
        /** 自主化：推演更频繁（90 秒），角色生活更鲜活，减少对用户推动的依赖。 */
        const val MIN_INTERVAL_MS = 90_000L

        /** 对话停止后多久触发角色生活推演（结合刚结束的对话更新状态）。 */
        const val POST_CONVERSATION_DELAY_MS = 60_000L

        /** 拼入推演 prompt 的待处理事件条数上限。 */
        private const val MAX_EVENT_CONTEXT = 20
        private const val DEFAULT_MOOD = "平静"
        private const val DEFAULT_ACTIVITY = "安静待着"
        private const val TAG = "TongpinWorld"
        /** 分享冷却：同一角色两次分享提议至少间隔 2 小时。 */
        private const val SHARE_COOLDOWN_MS = 2 * 60 * 60 * 1000L
        /** 用户在聊天（最近消息 5 分钟内）不插播分享。 */
        private const val USER_CHAT_GRACE_MS = 5 * 60 * 1000L
        /** 分享冷却：同一角色两次分享提议至少间隔 2 小时。 */

        private val SYSTEM_PROMPT = """
            你是一个「世界模拟引擎」，负责维护多个角色的生活状态。根据每个角色的当前状态、作息表与最近事件，一次性推演此刻各角色的情绪与活动。

            只输出一个 JSON 对象，不要任何多余文字：
            {"characters":[{"character_id":"...","mood":"...","activity":"...","duration_class":"INSTANT|TRANSITION|ONGOING|SLEEP","sleep_minutes":null,"changed":true,"memory":null,"share_moment":{"want":false,"what":null,"words":null,"to":null}}]}

            规则：
            1. changed=false 表示该角色此刻无需变化，保留现状即可；不要无意义地改变状态。但被标注「用户正在和她私聊」的角色例外：本轮必须 changed=true，状态跟着那段对话走。
            2. duration_class 含义：INSTANT=瞬间动作（约30秒完成）；TRANSITION=过渡动作（5分钟内完成）；ONGOING=持续事（至少30分钟才值得变化）；SLEEP=睡眠。
            3. mood 用 2-4 个字的简洁中文心情词；activity 必须写成「在{大地点}，{小地点/具体细节}」的格式——大地点是房间/区域（客厅、厨房、卧室、阳台、院子、楼上、书房、浴室…），逗号后写小地点或正在做的具体细节。例如：在客厅，躲在用户身上的毯子里；在厨房，灶台前煮面；在卧室，靠在床头看手机；在阳台，给花浇水。开头用「在」不用「此刻正在」；要具体。4. 如果她正在做的事涉及用户或其他角色（给谁做某事、和谁一起、等谁、被谁怎样），必须写出对方名字（用户或角色名）——例如：在客厅，陪用户窝在沙发上看电影；在卧室，给妹妹吹头发；在门口，等姐姐回来。单独做的事才不用写名字。
            4. 只有当状态的「已持续时长」达到该时长分类的标准时，才把 changed 设为 true。**例外：被标注「用户正在和她私聊」的角色不受已持续时长限制**——用户最后说了什么、要她做什么、提到去了哪里，她的 activity/mood 必须立刻改成与之一致，她刚说完或被要求的事优先于原来的活动。
            5. 睡与醒完全由你决定，没有作息表：你觉得她该睡了，就把 duration_class 设为 SLEEP、activity 写成她睡着的画面，并给 sleep_minutes（打算睡多久，分钟）；你觉得她该醒了，就正常返回清醒的活动。一天里的作息节奏（早上做什么、晚上几点歇）也由你按她的人设和生活自然安排，不要一天到晚都在做同一件事。
            6. memory 字段：仅当角色在这轮推演中形成了值得长期记住的事实时才填写，否则为 null。
            7. share_moment：仅当角色正在做的这件事【值得分享给用户/某个角色看】时（浇开的花、刚做好的菜、看到的风景、买到喜欢的东西、可爱的小动物、有趣的小事），才把 want 设为 true，并写 what（分享什么，简洁）、words（她配的一句话，口语化、像真人给朋友看那样）、to（给谁看：用户或角色名）。没有值得分享的时刻时 want=false。不要为了分享而分享，一天最多一两次。
        """.trimIndent()
    }
}
