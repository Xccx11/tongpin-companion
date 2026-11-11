package com.aster.companion.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCapabilityPromptTest {
    @Test
    fun `private chat prompt teaches the character to execute a group request with an allowed target`() {
        val type = Class.forName("com.aster.companion.agent.CharacterCapabilityPrompt")
        val instance = type.getField("INSTANCE").get(null)
        val render = type.getMethod("render", String::class.java, Set::class.java, List::class.java, Boolean::class.javaPrimitiveType)

        @Suppress("UNCHECKED_CAST")
        val prompt = render.invoke(
            instance,
            "lin-xia",
            setOf("companion.send_group_message", "companion.generate_image"),
            listOf("group_thread_id=family; title=家庭群"),
            false
        ) as String

        assertTrue(prompt.contains("companion.send_group_message"))
        assertTrue(prompt.contains("group_thread_id=family"))
        assertTrue(prompt.contains("EXECUTE THE ACTION"))
        assertTrue(prompt.contains("do not merely promise"))
    }

    @Test
    fun `private chat prompt allows a character to choose a relevant group message without a user command`() {
        val prompt = CharacterCapabilityPrompt.render(
            characterId = "lin-xia",
            capabilityIds = setOf("companion.send_group_message"),
            availableGroupTargets = listOf("group_thread_id=family; title=family"),
            isGroupChat = false
        )

        assertTrue(prompt.contains("current visible group context"))
        assertTrue(prompt.contains("without waiting for the user"))
    }

    @Test
    fun `private chat prompt keeps autonomous image offers confirmable`() {
        val prompt = CharacterCapabilityPrompt.render(
            characterId = "lin-xia",
            capabilityIds = setOf("companion.propose_image"),
            availableGroupTargets = emptyList(),
            isGroupChat = false
        )

        assertTrue(prompt.contains("companion.propose_image when you want to offer one first"))
        assertTrue(prompt.contains("ask the user before generating"))
    }
}
