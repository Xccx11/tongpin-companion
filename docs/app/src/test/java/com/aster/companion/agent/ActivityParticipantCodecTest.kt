package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityParticipantCodecTest {
    @Test
    fun `world activity participants preserve multiple character ids and user participation`() {
        val encoded = ActivityParticipantCodec.encodeWorld(
            characterIds = listOf("linxi", "gongyuli"),
            userParticipated = true
        )

        val decoded = ActivityParticipantCodec.decode(encoded)

        assertEquals(listOf("linxi", "gongyuli"), decoded.characterIds)
        assertTrue(decoded.userParticipated)
    }

    @Test
    fun `legacy sender receiver payload remains readable`() {
        val decoded = ActivityParticipantCodec.decode("""{"sender":"linxi","receiver":"gongyuli"}""")

        assertEquals(listOf("linxi", "gongyuli"), decoded.characterIds)
        assertFalse(decoded.userParticipated)
    }
}
