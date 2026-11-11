package com.aster.companion.agent.world

/** Keeps objective world history separate from selective role-scoped memory. */
object WorldMemoryPolicy {
    fun shouldWriteParticipantMemory(
        participants: List<String>,
        userParticipated: Boolean,
        expectedEffectsJson: String
    ): Boolean = participants.distinct().size >= 2 ||
        userParticipated ||
        expectedEffectsJson.trim().let { it.isNotEmpty() && it != "{}" }
}
