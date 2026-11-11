package com.aster.companion.agent

import com.aster.companion.lite.LiteProductConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteSystemPromptBuilderTest {

    @Test
    fun `languages persona relationship and current moment into a short system`() {
        val prompt = LiteSystemPromptBuilder.build(
            characterName = "夜鸦",
            personalityPrompt = "冷静、话少、喜欢在深夜看雨。",
            relationshipPrompt = "用户的同居女友",
            location = "厨房",
            activity = "煮面",
            mood = "放松"
        )

        assertTrue(prompt.contains("你是「夜鸦」，正在和用户微信聊天。"))
        assertTrue(prompt.contains("冷静、话少、喜欢在深夜看雨。"))
        assertTrue(prompt.contains("用户的同居女友"))
        assertTrue(prompt.contains("此刻：厨房 · 煮面 · 心情 放松"))
        // 语气规则（DEFAULT_STYLE）可被远程 `chat.liteStyle` 覆盖，所以只断言不会变的语义点，
        // 不绑定具体措辞——以前断言"像真人发微信""2～4 句"这种原句，文案一改就误报。
        assertTrue(prompt.contains("第一人称"))
        assertTrue(prompt.contains("()、（）或 <>"))
        // 最近对话**不再**进 system（那是真正的 messages 该干的事）：只留结构化状态。
        // 注意：语气规则（远程 chat.liteStyle）里本来就有"接上最近聊天里的称呼"这种措辞，
        // 所以这里只断言"聊天日志块"的标题不在了。
        assertFalse(prompt.contains("最近聊天（必须接着这里聊"))
        assertTrue(prompt.contains("真实换行"))
        assertTrue(prompt.contains("那你周末过来？"))
    }

    @Test
    fun `never leaks comfy rules tool schema device facts or group rules`() {
        val prompt = LiteSystemPromptBuilder.build(
            characterName = "夜鸦",
            personalityPrompt = "冷静",
            relationshipPrompt = "女友",
            location = "客厅",
            activity = "看书",
            mood = "平静"
        )

        assertFalse(prompt.contains("ComfyUI"))
        assertFalse(prompt.contains("HARD RULE"))
        assertFalse(prompt.contains("tool_calls"))
        assertFalse(prompt.contains("capability"))
        assertFalse(prompt.contains("Shizuku"))
        assertFalse(prompt.contains("家庭群"))
        assertFalse(prompt.contains("电量"))
    }

    @Test
    fun `truncates an overlong personality to the lite limit`() {
        val personality = "人".repeat(LiteProductConfig.PERSONALITY_LIMIT + 300)

        val prompt = LiteSystemPromptBuilder.build(
            characterName = "夜鸦",
            personalityPrompt = personality
        )

        val rendered = prompt.substringAfter("人设：")
            .substringBefore("\n")
        assertEquals(LiteProductConfig.PERSONALITY_LIMIT, rendered.length)
    }

    @Test
    fun `falls back to neutral situation when state is missing`() {
        val prompt = LiteSystemPromptBuilder.build(characterName = "夜鸦", personalityPrompt = "")

        assertTrue(prompt.contains("此刻：家中 · 安静待着 · 心情 平静"))
        assertFalse(prompt.contains("人设（"))
        assertFalse(prompt.contains("关系（"))
    }

    @Test
    fun `appends remembered facts at the end of the system`() {
        val prompt = LiteSystemPromptBuilder.build(
            characterName = "夜鸦",
            personalityPrompt = "冷静",
            rememberedFacts = listOf("她答应周末一起看电影", "不喜欢香菜")
        )

        assertTrue(prompt.contains("记得的事（每条前缀是发生时间，按时间理解先后）："))
        assertTrue(prompt.contains("- 她答应周末一起看电影"))
        assertTrue(prompt.contains("- 不喜欢香菜"))
    }

    /** 结构化状态（代替散文摘要）以「当前状态」块进入 system；它比"最近聊天"日志更省也更准。 */
    @Test
    fun `embeds structured state instead of a chat transcript`() {
        val prompt = LiteSystemPromptBuilder.build(
            characterName = "夜鸦",
            personalityPrompt = "冷静",
            stateBlock = "人物：夜鸦=室友\n称谓：她叫他哥\n地点时间：家里，晚上\n已完成：无\n未完成：周末看电影\n关键事实：无"
        )
        assertTrue(prompt.contains("当前状态"))
        assertTrue(prompt.contains("称谓：她叫他哥"))
        assertTrue(prompt.contains("未完成：周末看电影"))
        assertFalse("不能再往 system 里塞聊天日志", prompt.contains("最近聊天（必须接着这里聊"))
        assertFalse("也不该出现旧的摘要前缀", prompt.contains("更早之前聊过，摘要"))
    }

    /** 上下文预算不够时会用更小的人设上限重拼一次。 */
    @Test
    fun `honours a reduced personality limit for the context budget`() {
        val personality = "人".repeat(2000)

        val prompt = LiteSystemPromptBuilder.build(
            characterName = "夜鸦",
            personalityPrompt = personality,
            personalityLimit = 600
        )

        val rendered = prompt.substringAfter("人设：").substringBefore("\n")
        assertEquals(600, rendered.length)
    }

    @Test
    fun `injects user phone current time for sync`() {
        val prompt = LiteSystemPromptBuilder.build(
            characterName = "夜鸦",
            personalityPrompt = "冷静",
            currentTime = "2026年9月11日 星期四 20:15"
        )
        assertTrue(prompt.contains("当前时间"))
        assertTrue(prompt.contains("2026年9月11日 星期四 20:15"))
    }

    @Test
    fun `encourages short dimmed stage directions but bans novel narration`() {
        val prompt = LiteSystemPromptBuilder.build(
            characterName = "夜鸦",
            personalityPrompt = "冷静"
        )
        assertTrue(prompt.contains("动作神态"))
        assertTrue(prompt.contains("小说腔"))
        assertTrue(prompt.contains("第三人称"))
    }

    @Test
    fun `omits the remembered block when there is nothing to remember`() {
        val prompt = LiteSystemPromptBuilder.build(characterName = "夜鸦", personalityPrompt = "冷静")

        assertFalse(prompt.contains("记得的事"))
    }

    /** 角色要知道自己发过照片：内容与时间都要进 system，并且不能自称没拍过。 */
    @Test
    fun `发过的照片会写进私聊 system`() {
        val withPhoto = LiteSystemPromptBuilder.build(
            characterName = "林夏",
            personalityPrompt = "爱撒娇",
            lastPhoto = "靠在窗边拍上半身，穿白色毛衣（今天 18:30）"
        )
        assertTrue(withPhoto.contains("你最近给他发过一张照片"))
        assertTrue(withPhoto.contains("靠在窗边拍上半身，穿白色毛衣（今天 18:30）"))
        assertTrue("要明确不要说自己没拍过", withPhoto.contains("不要说自己没拍过"))

        val without = LiteSystemPromptBuilder.build(characterName = "林夏", personalityPrompt = "爱撒娇")
        assertFalse("没发过就不要出现这句", without.contains("你最近给他发过一张照片"))
    }
}
