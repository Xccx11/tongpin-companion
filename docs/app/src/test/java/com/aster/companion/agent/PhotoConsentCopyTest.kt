package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoConsentCopyTest {
    @Test
    fun `uses the role's contextual reply stored with an image approval`() {
        val arguments = """{"scene":"portrait","character_id":"summer","consent_note":"别催，我去换件睡衣拍给你。"}"""

        assertEquals("别催，我去换件睡衣拍给你。", PhotoConsentCopy.fromActionArguments(arguments))
    }
}
