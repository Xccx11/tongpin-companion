package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 视角写法：她发来的照片必须读成「她自己举着手机拍的」。
 * 实测只写 `holding a phone` 会被画成「别人拍她在摆弄手机」。
 */
class ScenePromptBuilderFramingTest {

    private fun userOf(type: CharacterPhotoShotPolicy.PhotoShotType): String =
        ScenePromptBuilder.user(
            ScenePromptBuilder.Briefing(
                characterName = "林夏",
                appearancePrompt = "1girl, long silver hair",
                recentMessages = emptyList(),
                intent = "刚洗完澡",
                currentSituation = "在浴室 · 放松",
                viewpoint = true,
                decidedShot = "靠在墙边拍上半身",
                decidedWhy = "想让你看看我",
                shotType = type
            )
        )

    /** 视点必须是人的眼睛（她本人或旁人），不是相机。 */
    @Test
    fun `看她自己时是人的眼睛且禁止相机语义`() {
        val user = userOf(CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER)
        assertTrue(user.contains("HUMAN EYE's view"))
        assertTrue(user.contains("looking at viewer"))
        assertTrue("必须明确禁用 selfie 这类相机语义", user.contains("Do NOT use selfie"))
        assertTrue(user.contains("must not depict the act of photographing"))
    }

    @Test
    fun `看她眼前的东西也是人的眼睛且没有器材`() {
        val user = userOf(CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_SCENE)
        assertTrue(user.contains("HUMAN EYE's view"))
        assertTrue(user.contains("pov, first person view"))
        assertTrue(user.contains("must not contain a phone, a camera, a lens or any viewfinder"))
        assertFalse("不能再写只有手（会把手机带进画面）", user.contains("only hands visible"))
    }

    @Test
    fun `视角写法不能出现在给用户看的两行里`() {
        val user = userOf(CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER)
        assertTrue(user.contains("Her decision (Chinese, her own point of view): SHOT: 靠在墙边拍上半身"))
        assertFalse("这不是渲染 tag 的一部分", user.contains("Chinese, her own point of view): SHOT: selfie"))
    }
}
