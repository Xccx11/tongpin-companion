package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterPhotoShotPolicyTest {

    @Test
    fun `解析她自己决定的画面和原因`() {
        val raw = "SHOT: 刚洗完澡，坐在沙发上用手机拍上半身，重点让他看湿头发\nWHY: 想让他看看我现在懒懒的样子"
        val shot = CharacterPhotoShotPolicy.parse(raw)
        assertNotNull(shot)
        assertEquals("刚洗完澡，坐在沙发上用手机拍上半身，重点让他看湿头发", shot!!.shot)
        assertEquals("想让他看看我现在懒懒的样子", shot.why)
    }

    @Test
    fun `中文冒号和近义词也认`() {
        val raw = "想拍：窗台上刚开的那盆花，凑近拍\n为什么：今天第一次开花"
        val shot = CharacterPhotoShotPolicy.parse(raw)
        assertNotNull(shot)
        assertEquals("窗台上刚开的那盆花，凑近拍", shot!!.shot)
        assertEquals("今天第一次开花", shot.why)
    }

    @Test
    fun `只有画面没有原因也能用`() {
        val raw = "SHOT: 厨房里刚出炉的饼干，俯拍特写"
        val shot = CharacterPhotoShotPolicy.parse(raw)
        assertNotNull(shot)
        assertEquals("厨房里刚出炉的饼干，俯拍特写", shot!!.shot)
        assertNull(shot.why)
        // 没说怎么拍时按前置自拍处理（她发来的照片默认是自拍）
        assertEquals(CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER, shot.type)
    }

    @Test
    fun `解析她的拍法并映射到画面视角`() {
        val selfie = CharacterPhotoShotPolicy.parse(
            "SHOT: 靠在床头拍上半身\nHOW: 举着手机前置自拍\nWHY: 想让你看看我现在"
        )
        assertNotNull(selfie)
        assertEquals("举着手机前置自拍", selfie!!.how)
        assertEquals(CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER, selfie.type)

        // 不再有「对镜自拍」：镜子会把手机和镜框带进画面，统一按第一人称处理
        val mirrorish = CharacterPhotoShotPolicy.parse("SHOT: 换了新裙子\nHOW: 对着镜子拍一张")
        assertEquals(CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER, mirrorish!!.type)

        val pov = CharacterPhotoShotPolicy.parse("SHOT: 窗外正在下雨\nHOW: 拍窗外的雨，不拍自己")
        assertEquals(CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_SCENE, pov!!.type)
    }

    @Test
    fun `提示词要求她说清怎么拍`() {
        val system = CharacterPhotoShotPolicy.system(
            CharacterPhotoShotPolicy.Input(characterName = "林夏", currentSituation = "刚洗完澡")
        )
        assertTrue(system.contains("HOW:"))
        assertTrue(system.contains("拍你自己的样子"))
        assertTrue(system.contains("人的眼睛看到的"))
        assertTrue(system.contains("画面里不要出现手机、相机、取景框"))
        assertTrue(system.contains("不拍自己"))
    }

    @Test
    fun `她把画面写成了英文时判定为没想清楚`() {
        val raw = "SHOT: sitting on the sofa with wet hair\nWHY: want you to see me now"
        assertNull(CharacterPhotoShotPolicy.parse(raw))
    }

    @Test
    fun `完全没按格式输出时判定为没想清楚`() {
        assertNull(CharacterPhotoShotPolicy.parse("嗯……我想想哦，要不还是算了吧。"))
        assertNull(CharacterPhotoShotPolicy.parse(""))
    }

    @Test
    fun `提示词要求她自己决定而不是复述聊天`() {
        val system = CharacterPhotoShotPolicy.system(
            CharacterPhotoShotPolicy.Input(
                characterName = "林夏",
                personalityPrompt = "爱撒娇",
                relationshipPrompt = "女朋友",
                currentSituation = "在厨房烤饼干 · 开心",
                timeOfDay = "下午",
                recent = listOf("User: 今天好累", "林夏: 我在烤饼干"),
                herOwnWords = "我给你发一张照片"
            )
        )
        // 指令块优先取远程 `photo.shotRules`，内置兜底文案也会改；
        // 所以只断言结构与状态，不绑死某句原话（以前盯"拍什么、怎么拍都由你自己决定"这种原句会误报）。
        assertTrue(system.contains("在厨房烤饼干"))
        assertTrue(system.contains("我给你发一张照片"))
        assertTrue(system.contains("SHOT"))
        assertTrue(system.contains("WHY"))
    }
}
