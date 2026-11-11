package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyGroupModelsTest {
    @Test
    fun `atmosphere caps natural follow ups`() {
        assertEquals(0, GroupAtmosphere.QUIET.maxFollowUps)
        assertEquals(2, GroupAtmosphere.DAILY.maxFollowUps)
        assertEquals(3, GroupAtmosphere.LIVELY.maxFollowUps)
    }

    @Test
    fun `group proactive policy rejects an empty or paused group`() {
        assertFalse(GroupProactivePolicy.canStart(isSystemModeEnabled = true, isPaused = false, hasMembers = false))
        assertFalse(GroupProactivePolicy.canStart(isSystemModeEnabled = true, isPaused = true, hasMembers = true))
    }

    @Test
    fun `group proactive policy follows the master system switch`() {
        assertTrue(GroupProactivePolicy.canStart(isSystemModeEnabled = true, isPaused = false, hasMembers = true))
        assertFalse(GroupProactivePolicy.canStart(isSystemModeEnabled = false, isPaused = false, hasMembers = true))
    }

    @Test
    fun `group proactive policy follows its own group switch`() {
        assertFalse(
            GroupProactivePolicy.canStart(
                isSystemModeEnabled = true,
                isPaused = false,
                hasMembers = true,
                isGroupProactiveEnabled = false
            )
        )
    }

    @Test
    fun `paused members are excluded from a proactive group turn`() {
        assertEquals(
            listOf("lan", "yuan"),
            GroupProactivePolicy.eligibleMembers(
                memberIds = listOf("lan", "xue", "lan", "yuan"),
                pausedMemberIds = setOf("xue")
            )
        )
    }

    @Test
    fun `group speaker context keeps each relationship attached to its own character`() {
        val rendered = GroupMemberContextFormatter.render(
            listOf(
                GroupMemberContext("lan", "岚", "用户的恋人，亲近但尊重边界"),
                GroupMemberContext("yuan", "予安", "用户的生活搭子，可靠直接")
            )
        )

        assertTrue(rendered.contains("lan=岚; relationship=用户的恋人，亲近但尊重边界"))
        assertTrue(rendered.contains("yuan=予安; relationship=用户的生活搭子，可靠直接"))
    }
}
