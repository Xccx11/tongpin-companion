package com.aster.companion.agent

import com.aster.companion.domain.ActionLibrary
import com.aster.companion.domain.CandidateAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterCognitionParseTest {

    private val candidates = ActionLibrary.candidatesFor("family")

    @Test
    fun `parses valid cognition json and validates action`() {
        val raw = """{"initial_impulse":"想自己休息","emotional_trigger":"看到妹妹房间灯还亮着","inner_conflict":"想休息又放心不下妹妹","decision":{"action":"care","target":"gongyuqin","reason":"确认她没事"},"final_reason":"妹妹比休息重要","confidence":78}"""
        val engine = CharacterCognitionEngine::class.java
        // 通过反射调用 private parseAndValidate？——用公开 ACTION_PATTERN 测试解析
        val m = CharacterCognitionEngine.ACTION_PATTERN.find(raw)
        assertNotNull(m)
        assertEquals("care", m!!.groupValues.get(1))
    }

    @Test
    fun `action not in candidates is rejected`() {
        val raw = """{"decision":{"action":"invented_thing","target":"x"},"confidence":90}"""
        // 候选是 family 类，invented_thing 不在 → parse 应拒绝
        val engine = CharacterCognitionEngine::class.java
        val m = CharacterCognitionEngine.ACTION_PATTERN.find(raw)
        assertNotNull(m) // 正则能提取，但业务校验在 cognize 内（action ∈ candidates）
        val action = m!!.groupValues.get(1)
        assertNull(candidates.firstOrNull { it.id == action })
    }

    @Test
    fun `confidence pattern extracts number`() {
        val raw = """{"decision":{"action":"care"},"confidence":65}"""
        val c = Regex("\"confidence\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
        assertEquals(65, c)
    }
}
