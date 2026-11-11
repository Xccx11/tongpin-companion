package com.aster.companion.agent

import com.aster.companion.domain.ProactiveMessageCooldown
import com.aster.companion.proactive.RelationshipPulseKind

object ProactiveDecisionInstruction {
    fun forEvent(source: String, summary: String, relationshipPulse: RelationshipPulseKind? = null): String =
        if (ProactiveMessageCooldown.isUrgent(source, summary)) {
            "SYSTEM DECISION CONSTRAINT: This is a confirmed delivery event. Output decision=send_now and write one concise, natural in-character private reminder."
        } else if (relationshipPulse == RelationshipPulseKind.FOLLOW_UP) {
            "RELATIONSHIP PULSE: Your last message has been sitting naturally for a few minutes. " +
                "Read the whole current context: the full recent back-and-forth, your last message, whether the user replied, memories, your world state, and fresh life facts. " +
                "Decide from that context, not the user's last message alone. You must send a new, forward-moving in-character message: " +
                "gently tease, say you miss the user, or switch to a fresh thought. You must not repeat, paraphrase, or explain the older user message again."
        } else if (relationshipPulse == RelationshipPulseKind.PRESENCE) {
            "PRESENCE PULSE: The user is still quiet after your prior reply. This is a fresh chance to initiate contact as a relationship, " +
                "not a request to repeat any old answer. Use your current emotion, memories, private world, the full conversation, and fresh real-life facts. " +
                "A naturally clingy message is welcome when it has a real emotional direction; choose silence only when there is genuinely nothing to say."
        } else {
            "Decide whether a proactive message is worthwhile from the full current context. " +
                "Treat the verified current-life facts supplied below as first-class decision input, not as chat decoration. " +
                "The triggering event is only why this turn started; you may combine it with current weather, battery, " +
                "network, foreground app, calendar, notifications, and the complete recent conversation, or choose not to send anything."
        } + "\n" + DeviceFactOwnershipInstruction.text()
}
