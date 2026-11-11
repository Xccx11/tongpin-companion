package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRoleInternalContinuityFormatterTest {
    @Test
    fun `renders role-only memories and worlds outside public group facts`() {
        val context = GroupRoleInternalContinuityFormatter.render(
            characterName = "Lan",
            memories = listOf("User dislikes bitter coffee"),
            semanticMemories = listOf("Diary: user had an exam last week"),
            privateWorld = listOf("Lan has been practicing piano"),
            sharedWorld = listOf("The user and Lan planned a bookstore visit")
        )

        assertTrue(context.first().contains("Lan's private continuity"))
        assertTrue(context.any { it.contains("User dislikes bitter coffee") })
        assertTrue(context.any { it.contains("exam last week") })
        assertTrue(context.any { it.contains("practicing piano") })
        assertTrue(context.any { it.contains("bookstore visit") })
        assertFalse(context.any { it.startsWith("[Family group shared facts]") })
    }
}
