package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 照片里不能出现任何拍摄器材——出现手机/相机就会把构图切开。
 * 规则文本已经交代过，但模型会漏，所以这里验的是**硬过滤**这一层。
 */
class SceneDeviceSuppressionTest {

    @Test
    fun `英文 tag 里丢掉器材与举手机的写法`() {
        val cleaned = SceneDeviceSuppression.stripTags(
            "holding phone, smiling, phone in hand, sitting on a sofa, soft light"
        )
        assertEquals("smiling, sitting on a sofa, soft light", cleaned)
    }

    @Test
    fun `英文 tag 里丢掉相机取景框类写法`() {
        val cleaned = SceneDeviceSuppression.stripTags("viewfinder, close-up, camera, looking at viewer")
        assertEquals("close-up, looking at viewer", cleaned)
    }

    @Test
    fun `中文自然语言里丢掉含器材的短句`() {
        val cleaned = SceneDeviceSuppression.stripSentences(
            "手机前置自拍，手臂伸向镜头，只拍到上半身"
        )
        assertEquals("只拍到上半身。", cleaned)
    }

    @Test
    fun `干净的自然语言原样保留`() {
        assertEquals(
            "她坐在夜晚窗边的沙发上，暖色台灯从侧面照来。",
            SceneDeviceSuppression.stripSentences("她坐在夜晚窗边的沙发上，暖色台灯从侧面照来。")
        )
    }

    @Test
    fun `按形态分派`() {
        assertFalse(
            SceneDeviceSuppression
                .strip("holding phone, smiling", ScenePromptFormat.ENGLISH_TAGS)
                .contains("phone")
        )
        assertFalse(
            SceneDeviceSuppression
                .strip("她举着手机自拍", ScenePromptFormat.NATURAL_LANGUAGE)
                .contains("手机")
        )
    }

    @Test
    fun `全是器材词时结果为空（交给校验去重试）`() {
        assertEquals("", SceneDeviceSuppression.stripTags("holding phone"))
        assertTrue(ComfyTagPromptPolicy.validate(SceneDeviceSuppression.stripTags("camera")).reasons.contains("empty"))
    }

    /**
     * 回归：krea2 形态下**只抹器材词，不删分句**。
     *
     * 以前按逗号切分后把含器材词的分句整条丢掉，于是"a candid photo taken with a phone of a
     * young East Asian woman with an oval face"整句被删，外貌跟着消失——最终提示词只剩
     * `defined jawline …`，用户看到的就是「外貌设定被篡改」。
     */
    @Test
    fun `krea2 抹掉器材词但保住同句里的外貌描述`() {
        val cleaned = SceneDeviceSuppression.strip(
            "A candid photo taken with a phone of a young East Asian woman with an oval face, " +
                "a sharply defined jawline, lying on her side on a messy bed.",
            ScenePromptFormat.ENGLISH_NATURAL
        )

        assertFalse(cleaned.lowercase().contains("phone"))
        assertTrue(cleaned.contains("young East Asian woman"))
        assertTrue(cleaned.contains("oval face"))
        assertTrue(cleaned.contains("defined jawline"))
        assertTrue(cleaned.contains("messy bed"))
    }

    @Test
    fun `krea2 不再把英文标点改写成全角逗号`() {
        val cleaned = SceneDeviceSuppression.strip(
            "She lies on a messy bed, her long black hair spread out, soft light from the window.",
            ScenePromptFormat.ENGLISH_NATURAL
        )

        assertFalse(cleaned.contains("，"))
        assertTrue(cleaned.contains(", her long black hair spread out,"))
    }
}
