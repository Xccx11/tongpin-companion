package com.aster.companion.agent

import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.AgentActionRequest
import com.aster.companion.domain.ConfirmationRequirement

/** The feed is independent from the private proactive-contact selection. */
object MomentsProactiveCharacterPolicy {
    fun eligibleCharacterIds(allCharacterIds: List<String>): List<String> =
        allCharacterIds.asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .toList()
}

/** Renders source-labelled facts so the feed planner cannot confuse group lines with user lines. */
object MomentsGrounding {
    data class Fact(
        val id: String,
        val source: String,
        val speaker: String,
        val body: String
    )

    fun render(facts: List<Fact>): String = facts.joinToString("\n") { fact ->
        "${fact.id} | ${fact.source} | speaker=${fact.speaker}: ${fact.body.trim()}"
    }
}

/** A feed photo is a user-visible generation task and always requires approval. */
object MomentsImageActionPolicy {
    fun request(characterId: String, postId: String, scene: String): AgentActionRequest =
        AgentActionRequest(
            capabilityId = "companion.generate_moment_image",
            transport = ActionTransport.LOCAL,
            confirmation = ConfirmationRequirement.REQUIRED,
            arguments = mapOf(
                "character_id" to characterId,
                "post_id" to postId,
                "scene" to scene.trim()
            )
        )
}
