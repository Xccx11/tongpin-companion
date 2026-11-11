package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationPromptPolicyTest {

    private val appearance = "1girl, solo, long black hair, blue eyes, white knitted sweater, denim skirt"

    @Test
    fun `外貌锚点原样拼在场景最前`() {
        val prompt = ImageGenerationPromptPolicy.forRenderer(appearance, "sitting by a window, warm light")
        assertTrue(prompt.startsWith(appearance))
        assertTrue(prompt.contains("sitting by a window, warm light"))
    }

    @Test
    fun `场景只复述了外貌开头时也不能丢掉整段外貌`() {
        // 旧实现按「前 40 字相同」判断，会整段跳过外貌，出图就不像角色了。
        val scene = "1girl, solo, long black hair, blue eyes, standing in the rain"
        val prompt = ImageGenerationPromptPolicy.forRenderer(appearance, scene)
        assertTrue("外貌锚点必须仍在提示词里", prompt.contains(appearance))
        assertTrue(prompt.contains("standing in the rain"))
    }

    @Test
    fun `场景已完整包含外貌时不重复拼接`() {
        val scene = "$appearance, standing in the rain"
        val prompt = ImageGenerationPromptPolicy.forRenderer(appearance, scene)
        assertEquals(scene, prompt)
    }

    @Test
    fun `没有外貌锚点时只发场景`() {
        assertEquals("sitting by a window", ImageGenerationPromptPolicy.forRenderer("", "sitting by a window"))
    }

    @Test
    fun `外貌和场景合成一行用逗号分开`() {
        val prompt = ImageGenerationPromptPolicy.forRenderer(
            appearance,
            "wet hair\n\nsitting on the sofa\nwarm lamp light"
        )
        assertFalse("渲染提示词不能有换行", prompt.contains("\n"))
        assertTrue(prompt.startsWith(appearance))
        assertTrue(prompt.contains("wet hair, sitting on the sofa, warm lamp light"))
    }

    @Test
    fun `外貌本身多行也会压成一行`() {
        val prompt = ImageGenerationPromptPolicy.forRenderer("1girl,\n\nlong black hair", "smiling")
        assertFalse(prompt.contains("\n"))
        assertEquals("1girl, long black hair, smiling", prompt)
    }

    @Test
    fun `清理重复逗号和多条逗号`() {
        assertEquals("a, b", ImageGenerationPromptPolicy.singleLine("a,,  ,\n b,"))
        assertEquals("a, b", ImageGenerationPromptPolicy.singleLine("  a , b  "))
    }

    @Test
    fun `不追加任何风格标签`() {
        val prompt = ImageGenerationPromptPolicy.forRenderer(appearance, "smiling")
        assertFalse(prompt.contains("photorealistic"))
        assertFalse(prompt.contains("masterpiece"))
    }

    @Test
    fun `外貌锚点为空或中文时给出提示`() {
        assertNotNull(ImageGenerationPromptPolicy.anchorWarning(""))
        assertNotNull(ImageGenerationPromptPolicy.anchorWarning("黑色长发，大眼睛，白裙子"))
        assertNull(ImageGenerationPromptPolicy.anchorWarning(appearance))
    }
}
