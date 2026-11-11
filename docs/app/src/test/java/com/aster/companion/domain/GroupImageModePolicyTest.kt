package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupImageModePolicyTest {
    @Test
    fun `ai selected mode is preserved for one person, separate photos, and a group photo`() {
        assertEquals(GroupImageMode.SINGLE, GroupImagePromptPolicy.resolveMode("SINGLE", listOf("lan")))
        assertEquals(GroupImageMode.SEPARATE, GroupImagePromptPolicy.resolveMode("SEPARATE", listOf("lan", "xue")))
        assertEquals(GroupImageMode.TOGETHER, GroupImagePromptPolicy.resolveMode("TOGETHER", listOf("lan", "xue")))
    }

    @Test
    fun `missing mode is inferred from the selected people without asking the user`() {
        assertEquals(GroupImageMode.SINGLE, GroupImagePromptPolicy.resolveMode(null, listOf("lan")))
        assertNull(GroupImagePromptPolicy.resolveMode(null, listOf("lan", "xue")))
    }

    @Test
    fun `missing group mode can be inferred from explicit scene wording without defaulting to a group photo`() {
        assertEquals(
            GroupImageMode.TOGETHER,
            GroupImagePromptPolicy.inferMode("让林见夏和岚一起拍一张合照", listOf("lan", "xue"))
        )
        assertEquals(
            GroupImageMode.SEPARATE,
            GroupImagePromptPolicy.inferMode("我们各自拍一张自己的照片", listOf("lan", "xue"))
        )
        assertEquals(
            GroupImageMode.SEPARATE,
            GroupImagePromptPolicy.inferMode("想分别发自己的照片", listOf("lan", "xue"))
        )
    }

    @Test
    fun `mode validation rejects an invalid number of selected people`() {
        assertTrue(GroupImagePromptPolicy.isValidMode(GroupImageMode.SINGLE, listOf("lan")))
        assertFalse(GroupImagePromptPolicy.isValidMode(GroupImageMode.SINGLE, listOf("lan", "xue")))
        assertFalse(GroupImagePromptPolicy.isValidMode(GroupImageMode.TOGETHER, listOf("lan")))
        assertNull(GroupImagePromptPolicy.resolveMode("unknown", listOf("lan")))
    }
}
