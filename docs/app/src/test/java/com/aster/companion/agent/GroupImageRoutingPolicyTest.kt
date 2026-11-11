package com.aster.companion.agent

import com.aster.companion.domain.GroupImageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GroupImageRoutingPolicyTest {
    private val members = listOf(
        GroupImageActionPolicy.MemberRef("older", "姐姐"),
        GroupImageActionPolicy.MemberRef("younger", "妹妹")
    )

    @Test
    fun `explicit named request keeps a single character instead of forcing a group photo`() {
        val proposal = GroupImageActionPolicy.explicitUserRequest(
            userText = "让妹妹给我发一张睡衣照片",
            members = members,
            fallbackCharacterId = "older"
        )

        assertNotNull(proposal)
        assertEquals(listOf("younger"), proposal!!.participantCharacterIds)
        assertEquals(GroupImageMode.SINGLE, proposal.mode)
    }

    @Test
    fun `plural request without arrangement stays separate rather than becoming a composite`() {
        val proposal = GroupImageActionPolicy.explicitUserRequest(
            userText = "让她们都给我发照片",
            members = members,
            fallbackCharacterId = "older"
        )

        assertNotNull(proposal)
        assertEquals(members.map { it.id }, proposal!!.participantCharacterIds)
        assertEquals(GroupImageMode.SEPARATE, proposal.mode)
    }

    @Test
    fun `group text can express a together photo and retain both identities`() {
        val proposal = ImageProposalIntentPolicy.fromGroupText(
            characterId = "younger",
            characterName = "妹妹",
            text = "我和姐姐一起拍张合照给你看，好不好？",
            members = members,
            contextualScene = "Current group photo offer"
        )

        assertNotNull(proposal)
        assertEquals(listOf("older", "younger"), proposal!!.participantCharacterIds)
        assertEquals(GroupImageMode.TOGETHER, proposal.mode)
    }

    @Test
    fun `unrelated group text does not create a photo action`() {
        assertNull(
            ImageProposalIntentPolicy.fromGroupText(
                characterId = "younger",
                characterName = "妹妹",
                text = "我刚才已经把作业写完了。",
                members = members,
                contextualScene = "Current group photo offer"
            )
        )
    }
}
