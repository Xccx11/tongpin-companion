package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGenerationActionPolicyTest {
    @Test
    fun `direct photo request creates a confirmable image proposal regardless of model choice`() {
        val action = PhotoGenerationActionPolicy.directRequest("林见夏", "给我发一张你的睡衣照片")

        assertEquals("companion.propose_image", action.capabilityId)
        assertEquals(ConfirmationRequirement.REQUIRED, action.confirmation)
        assertTrue(action.arguments.getValue("scene").contains("睡衣照片"))
        assertTrue(!action.arguments.getValue("scene").contains("我是"))
    }

    @Test
    fun `fallback action remains available only when a contextual model scene is absent`() {
        val action = PhotoGenerationActionPolicy.directRequest(
            "林见夏",
            "就按刚才那套睡衣和房间来一张",
            listOf("Character: 我刚换好蓝色睡衣，在暖光卧室里。", "User: 就按刚才那套睡衣和房间来一张")
        )

        assertTrue(action.arguments.getValue("scene").contains("刚才那套睡衣"))
        assertTrue(!action.arguments.getValue("scene").contains("蓝色睡衣"))
        assertEquals(ConfirmationRequirement.REQUIRED, action.confirmation)
    }

    @Test
    fun `model scene forwarding never appends recent chat context`() {
        val scene = PhotoGenerationActionPolicy.contextualizeModelScene(
            "wearing the requested pajamas beside the warm lamp",
            listOf("Character: I changed into blue pajamas", "User: use the previous setting")
        )

        assertEquals("wearing the requested pajamas beside the warm lamp", scene)
        assertTrue(!scene.contains("blue pajamas"))
        assertTrue(!scene.contains("User:"))
    }
}
