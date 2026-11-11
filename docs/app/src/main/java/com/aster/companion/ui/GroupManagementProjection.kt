package com.aster.companion.ui

import com.aster.companion.data.FamilyGroupFactEntity
import com.aster.companion.data.GroupMemberStateEntity
import com.aster.companion.data.GroupSettingsEntity
import com.aster.companion.domain.GroupAtmosphere

data class GroupManagementSnapshot(
    val atmosphere: GroupAtmosphere,
    val proactiveEnabled: Boolean,
    val groupType: String = "GENERAL",
    val facts: List<FamilyGroupFactEntity>,
    val memberStates: List<GroupMemberStateEntity>
)

/** Keeps the group management surface scoped to one thread and its current members. */
object GroupManagementProjection {
    fun forThread(
        threadId: String,
        memberIds: Set<String>,
        settings: List<GroupSettingsEntity>,
        facts: List<FamilyGroupFactEntity>,
        memberStates: List<GroupMemberStateEntity>
    ): GroupManagementSnapshot = GroupManagementSnapshot(
        atmosphere = settings.firstOrNull { it.groupThreadId == threadId }?.atmosphere ?: GroupAtmosphere.DAILY,
        proactiveEnabled = settings.firstOrNull { it.groupThreadId == threadId }?.proactiveEnabled ?: true,
        groupType = settings.firstOrNull { it.groupThreadId == threadId }?.groupType ?: "GENERAL",
        facts = facts
            .asSequence()
            .filter { it.groupThreadId == threadId }
            .sortedByDescending { it.createdAt }
            .toList(),
        memberStates = memberStates
            .asSequence()
            .filter { it.groupThreadId == threadId && it.characterId in memberIds }
            .sortedWith(compareBy<GroupMemberStateEntity> { it.lastSpokeAt ?: Long.MIN_VALUE }.thenBy { it.characterId })
            .toList()
    )
}
