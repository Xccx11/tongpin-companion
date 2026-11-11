package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 规则切换：提示词形态跟着当前图像模型走。
 * wai/illustrious 出英文 tag 串；z-image/lumina 走 Qwen 系编码器，出中文自然语言。
 */
class ScenePromptFormatTest {

    @Test
    fun `所有模型统一走英文 tag`() {
        listOf("z-image-turbo-nsfw", "z_image_nsfw_v2-Q8_0", "Lumina2", "qwen-image-edit", "zimage-turbo").forEach { name ->
            assertEquals(name, ScenePromptFormat.ENGLISH_TAGS, ScenePromptFormat.forModel(name))
        }
    }

    @Test
    fun `wai 系与认不出的模型走英文 tag`() {
        listOf("waiNSFWIllustrious_v90", "wai-nsfw-illustrious", "illustrious-xl", "some-sdxl-model", "").forEach { name ->
            assertEquals(name, ScenePromptFormat.ENGLISH_TAGS, ScenePromptFormat.forModel(name))
        }
    }

    /** 关键回归：以前 sanitizeScene 无条件剥中文，会把 z-image 的中文提示词整段清空。 */
    @Test
    fun `自然语言形态保留中文`() {
        val zh = "一位银灰色长发的年轻女性，坐在夜晚窗边的沙发上，怀里抱着橘猫，暖色台灯从侧面照来"
        assertEquals(zh, ComfyTagPromptPolicy.sanitizeScene(zh, stripCjk = false))
    }

    @Test
    fun `英文 tag 形态仍然剥掉中文`() {
        assertEquals("1girl, sitting", ComfyTagPromptPolicy.sanitizeScene("1girl, 卧室, sitting", stripCjk = true))
    }

    @Test
    fun `占位符两种形态都清掉`() {
        assertEquals(
            "坐在沙发上",
            ComfyTagPromptPolicy.sanitizeScene("[outfit from previous context] 坐在沙发上", stripCjk = false)
        )
        assertEquals(
            "sitting on a sofa",
            ComfyTagPromptPolicy.sanitizeScene("wearing [outfit from previous context] sitting on a sofa", stripCjk = true)
        )
    }

    @Test
    fun `内置 z-image 规则带官方字段顺序`() {
        val rule = ImagePromptDebugConfig.DEFAULT_Z_IMAGE_RULE
        assertTrue(rule.contains("[主体]+[动作/姿势]+[环境/场景]+[风格/媒介]+[技术细节]+[构图]"))
        assertTrue(rule.contains("不要自造 masterpiece"))
        assertTrue("规则要说明视点是人眼、且不能有相机语义", rule.contains("人的眼睛") && rule.contains("相机语义"))
    }

    @Test
    fun `内置规则一律不要求写光影`() {
        val zRule = ImagePromptDebugConfig.DEFAULT_Z_IMAGE_RULE
        val sceneRule = ImagePromptDebugConfig.DEFAULT_RULE
        assertFalse("z-image 规则不许再要求写光线", zRule.contains("4) 光线"))
        assertFalse("z-image 规则不许再要求写光线", zRule.contains("[光线]"))
        assertFalse("场景规则不许把 lighting 列进允许写的属性", sceneRule.contains("gaze direction, lighting"))
        assertFalse("场景规则不许把 lighting 列进只允许新增的项", sceneRule.contains("camera angle, lighting"))
    }

    @Test
    fun `自然语言简报要中文且不要 tag 串`() {
        val user = ScenePromptBuilder.user(
            ScenePromptBuilder.Briefing(
                characterName = "林夏",
                appearancePrompt = "1girl, long silver hair",
                recentMessages = emptyList(),
                intent = "刚洗完澡",
                viewpoint = true,
                decidedShot = "靠在墙边拍上半身",
                shotType = CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER
            ),
            wantReason = false,
            format = ScenePromptFormat.NATURAL_LANGUAGE
        )
        assertTrue(user.contains("中文自然语言"))
        assertTrue(user.contains("不要输出英文 tag 串"))
        assertTrue("中文侧要用人的眼睛说清视角", user.contains("人的眼睛"))
        assertFalse("不能出现英文 tag 写法", user.contains("selfie, looking at viewer"))
    }

    @Test
    fun `英文 tag 形态仍是原来的 tag 写法`() {
        val user = ScenePromptBuilder.user(
            ScenePromptBuilder.Briefing(
                characterName = "林夏",
                appearancePrompt = "1girl",
                recentMessages = emptyList(),
                intent = "刚洗完澡",
                viewpoint = true,
                decidedShot = "靠在墙边拍上半身",
                shotType = CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER
            ),
            wantReason = false,
            format = ScenePromptFormat.ENGLISH_TAGS
        )
        assertTrue(user.contains("looking at viewer"))
        assertTrue("必须禁止相机语义", user.contains("Do NOT use selfie"))
        assertTrue(user.contains("Return only the scene positive prompt text"))
    }

    @Test
    fun `自然语言形态用中文逗号拼接且不按逗号切句子`() {
        val prompt = ImageGenerationPromptPolicy.forRenderer(
            "一位银灰色长发的年轻女性",
            "坐在夜晚窗边的沙发上，怀里抱着一只橘猫",
            ScenePromptFormat.NATURAL_LANGUAGE
        )
        assertEquals("一位银灰色长发的年轻女性，坐在夜晚窗边的沙发上，怀里抱着一只橘猫", prompt)
    }

    @Test
    fun `自然语言形态的多行回复只压成一行不塞逗号`() {
        val multi = "一位银灰色长发的年轻女性\n坐在窗边\n暖色台灯从侧面照来"
        assertEquals(
            "一位银灰色长发的年轻女性 坐在窗边 暖色台灯从侧面照来",
            ImageGenerationPromptPolicy.singleLine(multi, ScenePromptFormat.NATURAL_LANGUAGE)
        )
    }

    @Test
    fun `英文 tag 形态仍按逗号压平`() {
        assertEquals(
            "1girl, sitting by a window",
            ImageGenerationPromptPolicy.singleLine("1girl,\n\nsitting by a window", ScenePromptFormat.ENGLISH_TAGS)
        )
    }
}
