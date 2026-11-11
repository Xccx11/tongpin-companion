package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.DecisionChainEntity
import java.util.UUID
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.1.5 角色认知引擎：模拟角色主观心理权衡（不是行为选择器）。
 * 心理过程：初始冲动 → 情绪触发 → 内心矛盾 → 最终决定 → 最终原因。
 * 输出只存"心理摘要"（emotion/motivation/conflict/reason），不保存完整隐藏思考过程。
 * 调用时机受控（不进 30 秒 Tick）：新需求/主动互动/收到请求/重要事件/用户触发。
 */
@Singleton
class CharacterCognitionEngine @Inject constructor(
    private val dao: CompanionDao,
    private val client: OpenAiCompatibleClient,
    private val secretCipher: com.aster.companion.data.CompanionSecretCipher
) {
    /** 认知结果（完整心理过程，供调用方使用；落库只存摘要）。 */
    data class CognitionResult(
        val initialImpulse: String,        // 第一反应（文本）
        val emotionalTrigger: String,      // 触发因素
        val innerConflict: String,         // 心理矛盾
        val action: String,                // 最终选择（∈ 候选）
        val target: String,                // 目标角色 id
        val finalReason: String,           // 最终原因
        val confidence: Int                // 0-100
    )

    /**
     * 执行角色认知。候选列表必须由调用方（代码）提供；返回的 action 只接受候选内的值。
     */
    suspend fun cognize(
        characterId: String,
        candidates: List<com.aster.companion.domain.CandidateAction>,
        triggerContext: String
    ): CognitionResult? {
        if (candidates.isEmpty()) return null
        val character = dao.character(characterId) ?: return null
        val personality = dao.personality(characterId)
        val living = dao.livingState(characterId)
        val goals = dao.activeGoals(characterId, System.currentTimeMillis())
        val recentChains = dao.recentDecisionChains(characterId, 3)
        val memories = dao.observeMemories().firstOrNull { list -> list.any { it.characterId == characterId } }
            ?.filter { it.characterId == characterId }
            ?.take(3) ?: emptyList()

        val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
            ?: return null

        val candidateLines = candidates.joinToString("\n") { "- ${it.id}（${it.label}）" }
        val systemPrompt = buildString {
            appendLine("你是${character.name}，现在在心里权衡一件事。你不是在系统里做决定——你是这个角色，在真实地想。")
            appendLine()
            appendLine("【你是什么样的人】")
            appendLine("性格：${personality?.coreDescription?.ifBlank { character.personalityPrompt.take(120) } ?: character.personalityPrompt.take(120)}")
            if (!personality?.behaviorTendency.isNullOrBlank()) appendLine("行为倾向：${personality.behaviorTendency}")
            appendLine("倾向强度：关心 ${personality?.care ?: 50} / 主动 ${personality?.initiative ?: 50} / 交流 ${personality?.conversation ?: 50} / 独立 ${personality?.independence ?: 50}")
            appendLine()
            appendLine("【你现在】心情 ${living?.mood ?: "平静"}；任务 ${living?.currentActivity?.ifBlank { "空闲" } ?: "空闲"}；${if (living?.sleepUntil?.let { System.currentTimeMillis() < it } == true) "在睡觉" else "清醒"}")
            if (goals.isNotEmpty()) {
                appendLine("【你最近的目标】")
                goals.take(2).forEach { appendLine("- ${it.goal}（重要度 ${it.importance}）") }
            }
            if (recentChains.isNotEmpty()) {
                appendLine("【你最近的几次心思】")
                recentChains.forEach { appendLine("- 上次${it.action}：情绪 ${it.emotionSummary}；矛盾 ${it.conflictSummary}") }
            }
            if (memories.isNotEmpty()) {
                appendLine("【你记得的最近的事】")
                memories.forEach { appendLine("- ${it.content.take(80)}") }
            }
            appendLine()
            appendLine("【触发】$triggerContext")
            appendLine()
            appendLine("【你现在能做的事（只能从这些里选）】")
            appendLine(candidateLines)
            appendLine()
            appendLine("请按真实心理过程走一遍（像这个角色一样想）：")
            appendLine("1. initial_impulse：我第一反应想做什么？（自然想法，不必是候选里的）")
            appendLine("2. emotional_trigger：是什么让我有这个感觉？（具体的事/画面/想起的话）")
            appendLine("3. inner_conflict：我有没有犹豫？两边是什么？（想X但Y）")
            appendLine("4. decision：权衡后我选哪个？（必须从候选里选一个 action，并写 target）")
            appendLine("5. final_reason：我为什么这么定？（给行为一个说得通的理由）")
            appendLine()
            appendLine("约束（不能违反）：不能改变自己的性格；不能编造不存在的事；不能创造新人物；只能从候选里选 decision.action。")
            appendLine("输出 JSON：{\"initial_impulse\":\"\",\"emotional_trigger\":\"\",\"inner_conflict\":\"\",\"decision\":{\"action\":\"\",\"target\":\"\",\"reason\":\"\"},\"final_reason\":\"\",\"confidence\":0}")
        }
        val raw = client.chat(
            profile = profile,
            apiKey = secretCipher.decrypt(requireNotNull(profile.encryptedApiKey)),
            systemPrompt = systemPrompt.toString(),
            userPrompt = "（作为${character.name}想一想）$triggerContext"
        ).getOrNull() ?: return null
        return parseAndValidate(raw, candidates)
    }

    private fun parseAndValidate(raw: String, candidates: List<com.aster.companion.domain.CandidateAction>): CognitionResult? {
        val action = Regex("\"action\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)?.trim()
        val known = action != null && candidates.any { it.id == action }
        if (!known) return null
        val str = { pattern: String -> Regex(pattern).find(raw)?.groupValues?.get(1)?.trim().orEmpty() }
        val confidence = Regex("\"confidence\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 50
        return CognitionResult(
            initialImpulse = str("\"initial_impulse\"\\s*:\\s*\"([^\"]*)\""),
            emotionalTrigger = str("\"emotional_trigger\"\\s*:\\s*\"([^\"]*)\""),
            innerConflict = str("\"inner_conflict\"\\s*:\\s*\"([^\"]*)\""),
            action = action!!,
            target = str("\"target\"\\s*:\\s*\"([^\"]*)\""),
            finalReason = str("\"final_reason\"\\s*:\\s*\"([^\"]*)\""),
            confidence = confidence.coerceIn(0, 100)
        )
    }

    /** 记录心理摘要到决策链（不保存完整 CoT）。 */
    suspend fun recordSummary(characterId: String, result: CognitionResult, now: Long) {
        dao.upsertDecisionChain(
            DecisionChainEntity(
                id = "chain:${UUID.randomUUID()}",
                characterId = characterId,
                action = result.action,
                emotionSummary = result.initialImpulse.take(120),
                motivation = result.emotionalTrigger.take(120),
                conflictSummary = result.innerConflict.take(120),
                decisionReason = result.finalReason.take(160),
                confidence = result.confidence,
                createdAt = now
            )
        )
    }

    companion object {
        internal val ACTION_PATTERN = Regex("\"action\"\\s*:\\s*\"([^\"]+)\"")
    }
}
