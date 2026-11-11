package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoIntentDecisionParserTest {
    @Test
    fun `accepts a semantic photo intent decision from DeepSeek`() {
        assertTrue(PhotoIntentDecisionParser.wantsCharacterPhoto("{\"wants_character_photo\":true}"))
    }

    @Test
    fun `does not treat ordinary conversation as an image request`() {
        assertFalse(PhotoIntentDecisionParser.wantsCharacterPhoto("{\"wants_character_photo\":false}"))
        assertFalse(PhotoIntentDecisionParser.wantsCharacterPhoto("我今天有点累"))
    }
}
