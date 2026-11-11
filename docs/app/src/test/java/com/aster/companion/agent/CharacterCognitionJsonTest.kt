package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 认知引擎 JSON 解析核心性质。 */
class CharacterCognitionJsonTest {

    @Test
    fun `action pattern extracts decision action`() {
        val raw = """{"decision":{"action":"care","target":"gongyuqin"}}"""
        val m = CharacterCognitionEngine.ACTION_PATTERN.find(raw)
        assertNotNull(m)
        assertEquals("care", m!!.groupValues.get(1))
    }

    @Test
    fun `confidence extracted and bounded`() {
        val raw = """{"confidence":120}"""
        val c = Regex("\"confidence\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
        assertNotNull(c)
        assertTrue(c!! <= 120)
    }

    @Test
    fun `full cognition json parseable`() {
        val raw = """{"initial_impulse":"x","emotional_trigger":"y","inner_conflict":"z","decision":{"action":"chat","target":"t","reason":"r"},"final_reason":"f","confidence":50}"""
        val m = CharacterCognitionEngine.ACTION_PATTERN.find(raw)
        assertNotNull(m)
        assertEquals("chat", m!!.groupValues.get(1))
    }
}
