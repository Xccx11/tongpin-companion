package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归用例就是**用户那次真实出图**的提示词：它同时犯了三样
 * ——写了光线/阴影、写了 blush、以及"该行为词 + 还穿着中长裙和内裤"自相矛盾，
 * 结果渲染出来相关部位一个都看不见。
 */
class SceneExposureScrubPolicyTest {

    private val offending = """
        In the dimly lit kitchen she wears a sophisticated silk blouse with rolled sleeves and a tight midi skirt,
        though her inner garments are being manipulated. An Asian man's hand is buried inside her pussy, causing
        her opening to be stretched into a pale taut oval. The soft glow from the phone screen illuminates her face,
        casting delicate shadows beneath her cheekbones. She has light blush on her cheeks.
        Full body shot from head to toe in a vertical frame. Sharp focus on the woman's face and torso.
    """.trimIndent()

    @Test
    fun `真实那份提示词会被判出全部四类问题并改掉`() {
        val result = SceneExposureScrubPolicy.scrub(offending, ScenePromptFormat.ENGLISH_NATURAL)
        val text = result.text.lowercase()

        // ① 禁令词：光线、阴影、blush 都不该留下
        assertFalse("光线相关词要抹掉", text.contains("dimly lit"))
        assertFalse(text.contains("soft glow"))
        assertFalse(text.contains("shadows"))
        assertFalse("blush 是硬禁令", text.contains("blush"))

        // ② 冲突：该行为画面里不该还留着遮挡衣物
        assertFalse("矛盾的下身衣物要删掉", text.contains("midi skirt"))
        assertFalse(text.contains("inner garments"))

        // ③ 含糊器官说法 → 具体词
        assertFalse("不许用 opening 含糊带过", text.contains("her opening"))

        // ④ 景别归一：不再同时有"全身从头到脚"
        assertFalse("全身+对焦冲突要按近景处理", text.contains("from head to toe"))

        // 每一项都要有可核对的记录
        assertTrue("命中项要记进 notes", result.notes.size >= 3)
    }

    @Test
    fun `没有该行为词时不动衣物`() {
        // 关键：普通场景里穿的裙子是她该有的描写，绝不能顺手删掉。
        val plain = "She wears a tight midi skirt and a silk blouse, sitting on the sofa, holding a cup of tea."
        val result = SceneExposureScrubPolicy.scrub(plain, ScenePromptFormat.ENGLISH_NATURAL)
        assertTrue("非该行为画面必须保留衣着", result.text.contains("midi skirt"))
        assertTrue(result.notes.isEmpty())
    }

    @Test
    fun `中文自然语言形态同样处理`() {
        val zh = "厨房里她穿着短裙，他的手插进她的小穴，那里已经被撑开。灯光很暗，她脸上有红晕。全身，从头到脚。近景特写。"
        val result = SceneExposureScrubPolicy.scrub(zh, ScenePromptFormat.CHINESE_NATURAL)
        assertFalse(result.text.contains("短裙"))
        assertFalse(result.text.contains("那里"))
        assertFalse("红晕属于禁令", result.text.contains("红晕"))
        assertFalse("灯光属于禁令", result.text.contains("灯光"))
    }
}
