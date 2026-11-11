package com.aster.companion.agent

import com.aster.companion.domain.GroupAtmosphere
import com.aster.companion.domain.GroupProactiveIntent
import com.aster.companion.domain.GroupTurnPlan

/** Shared language for the planner and each sequential group speaker. */
object GroupProactivePrompt {
    fun immediatePreviousSpeakerName(lines: List<GroupTranscriptLine>): String? =
        lines.lastOrNull()?.takeIf { !it.isUser }?.speakerName?.trim()?.takeIf(String::isNotBlank)

    fun roleContextInstruction(characterName: String): String =
        "${characterName}'s own private-chat continuity may inform this message when relevant. Do not reveal it to other group members, quote it as a group fact, or treat it as something they know."

    fun selectionSystemPrompt(atmosphere: GroupAtmosphere): String {
        val cadence = when (atmosphere) {
            GroupAtmosphere.QUIET -> "Prefer no follow-up; use one only when a direct character response is essential."
            GroupAtmosphere.DAILY -> "Use one or two follow-ups only when the immediately previous character leaves a real point to answer."
            GroupAtmosphere.LIVELY -> "When there is a real grounded hook and multiple members can contribute, prefer two or three short follow-ups that form one continuous exchange. The first speaker may re-enter after another character has spoken when that gives the exchange a natural response or closing line."
        }
        return """
        You are the director of a real multi-character family group chat.
        Decide what the group should do after an idle moment from the complete supplied context.
        Return JSON only with this shape:
        {"intent":"SILENT|CONTINUE|START_TOPIC","main_speaker_id":"id or null","topic":"short grounded topic or empty","invited_speaker_id":"id or null"}
        CONTINUE means the main speaker naturally continues the latest visible user or character thread.
        START_TOPIC means the main speaker opens a small new conversational topic grounded in an actual visible group message, a current valid group fact, or an explicitly supplied life signal. It must not invent a user fact.
        When START_TOPIC is selected, topic must say exactly what the main speaker will bring up, and invited_speaker_id may name one current member who would naturally join. The main speaker may invite another character when the context supports it, but must never speak for that character.
        Select only the first speaker. Every possible follow-up is chosen later from the updated transcript after the prior character message is actually persisted. $cadence
        This group's proactive switch is enabled, so favor CONTINUE or START_TOPIC whenever the authoritative context contains any real conversational hook. Do not use silence merely because there is no new user message: characters may continue each other or open a grounded topic. Use SILENT only when there is genuinely no visible context, group fact, or current life signal worth responding to.
        Do not invent user facts, private-chat content, locations, actions, feelings, or shared history. Character messages are not user messages.
        Atmosphere=${atmosphere.name}.
    """.trimIndent()
    }

    fun replyDirective(
        plan: GroupTurnPlan,
        responderId: String,
        responderName: String,
        previousSpeakerName: String?
    ): String = buildString {
        when (plan.intent) {
            GroupProactiveIntent.START_TOPIC -> {
                if (responderId in plan.followUpSpeakerIds) {
                    append("Respond to the main speaker's newly written message in your own voice. Do not reopen the topic or repeat the main speaker; add only a short reaction or contribution that follows from what they actually said.")
                } else {
                    append("Open the grounded topic in your own voice: ")
                    append(plan.topic.orEmpty())
                    append(". Do not present the topic cue as a fact that is absent from the transcript.")
                    if (plan.invitedSpeakerId != null && plan.invitedSpeakerId != responderId) {
                        append(" Naturally leave room for the invited group member to answer; do not speak for them.")
                    }
                }
            }
            GroupProactiveIntent.CONTINUE -> {
                append("Continue the latest real thread in your own voice. Do not repeat an earlier answer or manufacture a new user question.")
            }
            GroupProactiveIntent.SILENT -> append("No proactive message should be generated.")
        }
        if (previousSpeakerName != null && previousSpeakerName != responderName) {
            append(" The immediately previous visible speaker was $previousSpeakerName; read that message and react to it when it gives you something natural to answer.")
        }
        append(" You are writing only the next message for responder_id=$responderId; responder_name=$responderName.")
    }

    fun continuationDirective(responderId: String, responderName: String, previousSpeakerName: String?): String = buildString {
        append("The previous group message has already been written. Respond only when its real content gives you something natural to add, answer, disagree with, or gently close. Do not reopen the user's older message, repeat the earlier point, or manufacture a question.")
        if (previousSpeakerName != null && previousSpeakerName != responderName) {
            append(" The immediately previous visible speaker was $previousSpeakerName; react to that actual message in your own voice.")
        }
        append(" You are writing only the next message for responder_id=$responderId; responder_name=$responderName.")
    }
}
