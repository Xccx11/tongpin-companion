package com.aster.companion.ui

import com.aster.companion.data.FamilyGroupFactEntity
import com.aster.companion.data.GroupMemberStateEntity
import com.aster.companion.data.GroupSettingsEntity
import com.aster.companion.domain.GroupAtmosphere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupManagementProjectionTest {
    @Test
    fun projectionKeepsOnlySelectedGroupDataAndCurrentMembers() {
        val snapshot = GroupManagementProjection.forThread(
            threadId = "group-1",
            memberIds = setOf("a", "b"),
            settings = listOf(GroupSettingsEntity("group-1", GroupAtmosphere.LIVELY, 9L)),
            facts = listOf(
                fact("f-current", "group-1", "今天吃火锅"),
                fact("f-other", "group-2", "不能混进来")
            ),
            memberStates = listOf(
                GroupMemberStateEntity("group-1", "a", 1, 2, 3, "想抢话", 10L),
                GroupMemberStateEntity("group-1", "removed", 9, 9, 9, "不应显示", 11L),
                GroupMemberStateEntity("group-2", "b", 9, 9, 9, "别串群", 12L)
            )
        )

        assertEquals(GroupAtmosphere.LIVELY, snapshot.atmosphere)
        assertEquals(listOf("f-current"), snapshot.facts.map { it.id })
        assertEquals(listOf("a"), snapshot.memberStates.map { it.characterId })
    }

    @Test
    fun projectionDefaultsToDailyWhenGroupSettingsAreMissing() {
        val snapshot = GroupManagementProjection.forThread(
            threadId = "group-1",
            memberIds = setOf("a"),
            settings = emptyList(),
            facts = emptyList(),
            memberStates = emptyList()
        )

        assertEquals(GroupAtmosphere.DAILY, snapshot.atmosphere)
        assertTrue(snapshot.facts.isEmpty())
    }

    private fun fact(id: String, groupThreadId: String, summary: String) = FamilyGroupFactEntity(
        id = id,
        groupThreadId = groupThreadId,
        ownerCharacterId = null,
        summary = summary,
        sourceThreadId = groupThreadId,
        sourceKind = "GROUP",
        createdAt = 1L,
        expiresAt = null,
        sensitivity = "NORMAL",
        allowedInGroup = true
    )
}
