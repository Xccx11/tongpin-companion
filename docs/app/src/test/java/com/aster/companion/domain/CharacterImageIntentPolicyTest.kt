package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterImageIntentPolicyTest {
    @Test
    fun `recognizes a character asking to send a photo`() {
        assertTrue(CharacterImageIntentPolicy.expressesImageDesire("我想给你发一张我现在的照片，你要不要看？"))
        assertTrue(CharacterImageIntentPolicy.expressesImageDesire("等我拍一张发给你"))
    }

    @Test
    fun `does not treat ordinary visual language as an image request`() {
        assertFalse(CharacterImageIntentPolicy.expressesImageDesire("我看见你发来的图片了"))
        assertFalse(CharacterImageIntentPolicy.expressesImageDesire("今天的天气看起来不错"))
    }
}
