package com.aster.companion.agent

import com.aster.companion.domain.GroupMemberContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupDirectAddressPolicyTest {
    private val members = listOf(
        GroupMemberContext("older", "林夕（姐姐）", ""),
        GroupMemberContext("younger", "宫雨琴（妹妹）", "")
    )

    @Test fun `direct character question gives the named member one reply opportunity`() {
        assertEquals("older", GroupDirectAddressPolicy.questionedMemberId("姐姐姐姐，你在干啥呀？", "younger", members))
    }

    @Test fun `ordinary character statement does not force a reply`() {
        assertNull(GroupDirectAddressPolicy.questionedMemberId("我刚刚看完那部电影。", "younger", members))
    }

    @Test fun `directly named member gets a reply opportunity without a question mark`() {
        assertEquals("older", GroupDirectAddressPolicy.questionedMemberId("姐姐，我刚才说到你了。", "younger", members))
    }
}
