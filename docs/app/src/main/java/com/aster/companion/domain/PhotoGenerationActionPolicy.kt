package com.aster.companion.domain

/** Direct requests are product commands, not optional roleplay tool choices. */
object PhotoGenerationActionPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun directRequest(characterName: String, userRequest: String, recentConversation: List<String> = emptyList()): AgentActionRequest = AgentActionRequest(
        capabilityId = "companion.propose_image",
        transport = ActionTransport.LOCAL,
        confirmation = ConfirmationRequirement.REQUIRED,
        arguments = mapOf(
            // The conversation model owns context resolution. The gateway receives only
            // the current visual request; appearance is sent in its separate field.
            "scene" to userRequest.trim().take(MAX_REQUEST_CHARS)
        )
    )

    @Suppress("UNUSED_PARAMETER")
    fun contextualizeModelScene(scene: String, recentConversation: List<String>): String {
        return scene.trim().take(MAX_SCENE_CHARS)
    }

    fun visibleReply(): String = "好呀，我去准备一张给你。你点一下确认，我就开始生成。"

    private const val MAX_REQUEST_CHARS = 350
    private const val MAX_SCENE_CHARS = 1_000
}
