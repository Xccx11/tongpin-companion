package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalImageReplyPolicyTest {
    @Test
    fun `does not expose an internal image prompt or workflow as a role reply`() {
        val reply = ImageGenerationVisibleReplyPolicy.fromModel(
            rawCandidate = "看这种提示词就行，我去调用模型生成。",
            characterName = "林见夏"
        )

        assertEquals(ImageGenerationVisibleReplyPolicy.FALLBACK_REPLY, reply)
    }
}
