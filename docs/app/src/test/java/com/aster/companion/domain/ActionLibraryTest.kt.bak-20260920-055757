package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLibraryTest {

    @Test
    fun `entertainment need returns tv game stroll candidates`() {
        val ids = ActionLibrary.candidatesFor("entertainment").map { it.id }
        assertTrue(ids.containsAll(listOf("watch_tv", "game", "stroll")))
    }

    @Test
    fun `emotional need returns chat vent comfort`() {
        val ids = ActionLibrary.candidatesFor("emotional").map { it.id }
        assertTrue(ids.containsAll(listOf("chat", "vent", "comfort")))
    }

    @Test
    fun `family need returns meal remind help care`() {
        val ids = ActionLibrary.candidatesFor("family").map { it.id }
        assertTrue(ids.containsAll(listOf("meal_remind", "help", "care")))
    }

    @Test
    fun `social need includes chat`() {
        assertTrue(ActionLibrary.candidatesFor("social").any { it.id == "chat" })
    }

    @Test
    fun `unknown need type returns empty`() {
        assertTrue(ActionLibrary.candidatesFor("unknown").isEmpty())
    }

    @Test
    fun `all candidates deduplicated`() {
        val all = ActionLibrary.ALL
        assertEquals(all.size, all.map { it.id to it.needType }.distinct().size)
    }
}
