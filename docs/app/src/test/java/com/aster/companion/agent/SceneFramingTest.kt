package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 景别：**构图由模型自己决定，代码不插手**（2026-10-11 用户要求）。
 *
 * 这里以前是相反的一套契约——把模型写的半身类景别词（medium 系列 / close-up / upper body / waist up…）
 * **全部抹掉**，再强行补一句 `full body, from head to toe, feet included`。
 *
 * 真机实测的后果（已被用户报为 bug）：
 * - 提示词里**景别自相矛盾**——场景正文写"大腿到头顶"、结尾却被追加"从头到脚带脚"；
 * - 出图模型只认正文那句，脸的像素被压到画面 ~1/8；
 * - 于是**提示词里表情写得很足，图里却看不出来**（用户现象："人物没有表情"）。
 *
 * 所以现在的契约是：**[SceneFraming.ensure] 只做 trim，原样返回**。
 * 下面每条测试都在钉这一点——**改回"抹掉+补全身"之前请先看这段注释**。
 */
class SceneFramingTest {

    private val her = CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER
    private val scene = CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_SCENE

    @Test
    fun `没有景别时不再补全身`() {
        val src = "She sits on the bed reading a book."

        val out = SceneFraming.ensure(src, her, ScenePromptFormat.ENGLISH_NATURAL)

        assertEquals(src, out)
        assertFalse("不该再追加全身兜底", out.contains("full body"))
    }

    @Test
    fun `medium shot 原样保留`() {
        val src = "She sits on the bed, medium shot from her left."

        val out = SceneFraming.ensure(src, her, ScenePromptFormat.ENGLISH_NATURAL)

        assertEquals(src, out)
        assertTrue("模型自己写的景别必须留着", out.contains("medium"))
    }

    /** `medium full-body` 这种混搭是模型自己的选择，代码不再替它裁决。 */
    @Test
    fun `medium full-body 混搭原样保留`() {
        val src = "The shot is a medium full-body view from her left."

        assertEquals(src, SceneFraming.ensure(src, her, ScenePromptFormat.ENGLISH_NATURAL))
    }

    /** 模型自己写 close-up（近景/特写）正是"表情看得清"的前提，绝不能删。 */
    @Test
    fun `close-up 原样保留`() {
        val src = "A close-up of her face in a dim room."

        val out = SceneFraming.ensure(src, her, ScenePromptFormat.ENGLISH_NATURAL)

        assertEquals(src, out)
        assertTrue(out.contains("close-up"))
    }

    @Test
    fun `已经写了全身也不改动`() {
        val src = "She stands by the window, full body, from head to toe."

        val out = SceneFraming.ensure(src, her, ScenePromptFormat.ENGLISH_NATURAL)

        assertEquals(src, out)
        assertEquals("不该出现第二份景别说法", 1, Regex("full body").findAll(out).count())
    }

    @Test
    fun `上下文明确只要局部时也不动`() {
        val src = "只拍她的手，搭在桌沿上。"

        assertEquals(src, SceneFraming.ensure(src, her, ScenePromptFormat.CHINESE_NATURAL))
    }

    @Test
    fun `拍眼前的东西同样不动`() {
        val src = "A cup of coffee on the desk, steam rising."

        assertEquals(src, SceneFraming.ensure(src, scene, ScenePromptFormat.ENGLISH_NATURAL))
    }

    @Test
    fun `中文形态也不再补全身`() {
        val src = "她坐在床边看书。"

        val out = SceneFraming.ensure(src, her, ScenePromptFormat.CHINESE_NATURAL)

        assertEquals(src, out)
        assertFalse(out.contains("全身"))
        assertFalse(out.contains("从头到脚"))
    }

    @Test
    fun `空串原样返回`() {
        assertEquals("", SceneFraming.ensure("", her))
    }

    // ---- 脸不许被裁掉（2026-10-11）----
    //
    // 实测 bug：模型写「from her thighs to her chest」把头顶截掉，同一段里却细写眼睛和鬓角。
    // 现在这种自相矛盾会被前置一句「脸完整在画面里、没被裁」。

    @Test
    fun `构图把上边界截在胸口时，把脸未被裁前置到最前`() {
        val src = "She leans back against the seat. The shot is a close-up frame from her thighs to " +
            "her chest, her eyes half-lidded and glossy."

        val out = SceneFraming.ensure(src, her, ScenePromptFormat.ENGLISH_NATURAL)

        assertTrue("必须明确要求脸留在画面里", out.startsWith("Her whole face is fully in frame"))
        assertTrue("模型自己写的正文一个字不改，原样跟在后面", out.endsWith(src))
    }

    @Test
    fun `没提到脸时不做任何事`() {
        val src = "The shot is a close-up frame from her thighs to her chest."

        assertEquals(src, SceneFraming.ensure(src, her, ScenePromptFormat.ENGLISH_NATURAL))
    }

    @Test
    fun `与构图无关的 chest 不误伤`() {
        val src = "She sits on the bed reading a book, the blanket pulled up to her chest."

        assertEquals(src, SceneFraming.ensure(src, her, ScenePromptFormat.ENGLISH_NATURAL))
    }
}
