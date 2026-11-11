package com.aster.companion.domain

enum class ConversationType { PRIVATE, GROUP }
/**
 * `DIARY` 是**给用户看的恋爱日记**，和 `EVENT`（共同活动 / 互动 / 世界事件这类系统流水）分开。
 * 日记以前共用 `EVENT`，结果两个页面互相污染：日记页里混着系统事件，
 * 而「今天的日记是哪条」也认不准（见 DiaryEntryPolicy）。
 */
enum class MemoryKind { PREFERENCE, PLAN, RELATIONSHIP, EVENT, CONVERSATION, DIARY }
enum class EventState { NEW, PRESENTED, DEFERRED, CONSUMED, EXPIRED }
enum class AgentDecisionKind { SEND_NOW, DEFER, IGNORE, TOOL_CALLS }
enum class ActionTransport { LOCAL, SHIZUKU, ACCESSIBILITY }
enum class ConfirmationRequirement { NONE, REQUIRED }

data class CharacterCard(
    val id: String,
    val name: String,
    val personalityPrompt: String,
    val appearancePrompt: String,
    val referenceImageUri: String? = null,
    val relationshipPrompt: String = ""
)

// ConversationThread / ConversationMessage / MemoryRecord / CompanionEvent 四个"旧领域模型"
// 已删除：实际落库和读库走的是 Room 实体（CharacterEntity / ThreadEntity / MessageEntity /
// MemoryEntity / EventEntity），这四份只是同名影子类型，全项目零引用，留着只会让人以为有两套模型。

data class Capability(
    val id: String,
    val transport: ActionTransport,
    val confirmation: ConfirmationRequirement
)

data class CompanionCapabilitySnapshot(
    val version: Long,
    val capabilities: Set<Capability>
) {
    companion object {
        fun empty() = CompanionCapabilitySnapshot(0, emptySet())
    }
}

data class AgentActionRequest(
    val capabilityId: String,
    val transport: ActionTransport,
    val confirmation: ConfirmationRequirement,
    val arguments: Map<String, String> = emptyMap()
)

data class AgentDecision(
    val kind: AgentDecisionKind,
    val threadType: ConversationType,
    val message: String? = null,
    val deferUntil: Long? = null,
    val actions: List<AgentActionRequest> = emptyList()
)

sealed interface DecisionValidation {
    data object Accepted : DecisionValidation
    data class Rejected(val reason: String) : DecisionValidation
}

object CompanionInvariants {
    fun validateDecision(
        decision: AgentDecision,
        snapshot: CompanionCapabilitySnapshot
    ): DecisionValidation {
        if (decision.kind == AgentDecisionKind.SEND_NOW && decision.message.isNullOrBlank()) {
            return DecisionValidation.Rejected("send_now requires a message")
        }
        if (decision.kind == AgentDecisionKind.DEFER && decision.deferUntil == null) {
            return DecisionValidation.Rejected("defer requires a time")
        }
        val available = snapshot.capabilities.associateBy { it.id }
        for (action in decision.actions) {
            val capability = available[action.capabilityId]
                ?: return DecisionValidation.Rejected("capability unavailable: ${action.capabilityId}")
            if (capability.transport != action.transport) {
                return DecisionValidation.Rejected("transport mismatch: ${action.capabilityId}")
            }
            if (capability.confirmation != action.confirmation) {
                return DecisionValidation.Rejected("confirmation mismatch: ${action.capabilityId}")
            }
        }
        return DecisionValidation.Accepted
    }
}
