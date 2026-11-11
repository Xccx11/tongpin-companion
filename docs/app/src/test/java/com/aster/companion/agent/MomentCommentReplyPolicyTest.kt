package com.aster.companion.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class MomentCommentReplyPolicyTest {
    @Test
    fun `reply prompt makes the exact user comment the required answer target`() {
        val prompt = MomentCommentReplyPolicy.prompt(
            characterName = "雨宫琴",
            personality = "嘴硬心软",
            postAuthorName = "用户",
            postBody = "哥哥不在家，有点无聊呢",
            targetComment = "小琴你在干啥！！",
            thread = "user: 小琴你在干啥！！"
        )

        assertTrue(prompt.contains("小琴你在干啥！！"))
        assertTrue(prompt.contains("must answer directly"))
        assertTrue(prompt.contains("must not repeat or paraphrase the post"))
    }
}
