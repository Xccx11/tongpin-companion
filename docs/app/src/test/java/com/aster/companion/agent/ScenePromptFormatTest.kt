package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 出图提示词格式的**约定测试**（A4）。
 *
 * 项目当前的约定是：无论什么模型，都按英文 tag 组织提示词（见 docs/DECISIONS.md D2）。
 * 这条测试把约定钉死 —— 以后有人按模型名偷偷切回自然语言，它会立刻红。
 */
class ScenePromptFormatTest {

    @Test
    fun `forModel always returns english tags`() {
        listOf("krea2", "flux", "qwen-image", "unknown-model", "").forEach { model ->
            assertEquals(
                "模型 $model 的提示词格式应为英文 tag",
                ScenePromptFormat.ENGLISH_TAGS,
                ScenePromptFormat.forModel(model),
            )
        }
    }
}