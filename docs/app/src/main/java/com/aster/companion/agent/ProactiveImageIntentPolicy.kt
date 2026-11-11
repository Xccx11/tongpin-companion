package com.aster.companion.agent

import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.AgentDecision
import com.aster.companion.domain.AgentActionRequest
import com.aster.companion.domain.AgentDecisionKind
import com.aster.companion.domain.CharacterImageIntentPolicy
import com.aster.companion.domain.ConfirmationRequirement

/** Converts a character's explicit natural-language image wish into the confirmable action path. */
object ProactiveImageIntentPolicy {
    /** A positive image wish is actionable even when the planner picked a non-action branch. */
    fun attachProposal(decision: AgentDecision, proposal: AgentActionRequest): AgentDecision {
        if (decision.actions.any { it.isImageAction() }) return decision
        return decision.copy(
            kind = AgentDecisionKind.TOOL_CALLS,
            actions = decision.actions + proposal
        )
    }

    fun proposalAction(
        characterId: String,
        characterName: String,
        text: String,
        currentContext: String
    ): AgentActionRequest? {
        if (!CharacterImageIntentPolicy.expressesImageDesire(text)) return null
        return AgentActionRequest(
            capabilityId = "companion.propose_image",
            transport = ActionTransport.LOCAL,
            confirmation = ConfirmationRequirement.REQUIRED,
            arguments = mapOf(
                "character_id" to characterId,
                "scene" to buildString {
                    append("I am ")
                    append(characterName)
                    append(". I want to send the user a photo from this current moment. Preserve the real visual intent without inventing details: ")
                    append(currentContext.trim().take(MAX_CONTEXT_CHARS))
                }
            )
        )
    }

    private const val MAX_CONTEXT_CHARS = 800

    private fun AgentActionRequest.isImageAction(): Boolean =
        capabilityId == "companion.generate_image" || capabilityId == "companion.propose_image"
}
