package com.aster.companion.agent

import com.aster.companion.lite.LiteProductConfig

/**
 * 第一步：**角色自己决定**这次要拍什么、怎么拍。
 *
 * 关键点是她自己想，而不是把聊天内容翻译成画面：
 * - 以她此刻真实的生活状态为主（正在做什么、心情、在哪、什么时段、身上穿的）；
 * - 最近聊天只是参考之一，不能反过来决定照片；
 * - 她可以拍自己，也可以拍她眼前/手边的东西；
 * - 不编造用户在场，也不编造没发生过的共同事件。
 *
 * 输出三行中文（只给用户看，不进渲染 tag）：
 * `SHOT: <她决定拍的画面>` / `HOW: <怎么拍的>` / `WHY: <为什么现在想让对方看>`
 *
 * [PhotoShot.type] 决定渲染时用哪套视角写法——这一层必须由她的话决定，不能默认第三人称。
 */
object CharacterPhotoShotPolicy {

    /** 她自己的决定。[shot] 是必须的，[how] 与 [why] 可以没有。 */
    data class PhotoShot(
        val shot: String,
        /** 她的拍法，如「前置自拍」「对着镜子」「拍窗外的雨」。 */
        val how: String? = null,
        val why: String? = null
    ) {
        /** 按她的拍法决定画面视角；没说清楚时按前置自拍处理（她发来的照片默认是自拍）。 */
        val type: PhotoShotType get() = PhotoShotType.from(how)
    }

    /**
     * 这张照片的视角。
     *
     * 原则：画面必须是**人的眼睛**看到的——她的眼睛，或旁边某个人的眼睛；
     * **不是相机拍出来的那种**：不出现手机/相机/镜头/取景框，也不出现"在拍照"这件事。
     */
    enum class PhotoShotType {
        /** 看她自己：人眼视角（她本人或旁边的人在看），画面里没有任何拍摄器材。 */
        EYES_ON_HER,

        /** 看她眼前的东西：人眼视角，画面里没有拍摄器材，也没有她的脸。 */
        EYES_ON_SCENE;

        companion object {
            fun from(how: String?): PhotoShotType {
                val text = how.orEmpty()
                return when {
                    POV_WORDS.any { text.contains(it) } -> EYES_ON_SCENE
                    else -> EYES_ON_HER
                }
            }

            private val POV_WORDS = listOf(
                "窗外", "眼前", "面前", "我看到的", "看到的东西", "不拍自己", "拍东西",
                "桌上的", "手上的东西", "手里的东西", "天空", "窗外", "街上", "拍吃的", "拍食物", "拍风景"
            )
        }
    }

    data class Input(
        val characterName: String,
        val personalityPrompt: String = "",
        val relationshipPrompt: String = "",
        /** 她此刻在做什么、什么心情、在哪（来自她的生活状态）。 */
        val currentSituation: String = "",
        /** 「深夜」「下午」这类时段。 */
        val timeOfDay: String = "此刻",
        /** 最近几条聊天短摘录，仅作参考。 */
        val recent: List<String> = emptyList(),
        /** 她刚说的话（她要发照片时用的原话）；用户主动要图时可为空。 */
        val herOwnWords: String = ""
    )

    fun system(input: Input): String = buildString {
        appendLine("你是「${input.characterName.trim()}」，一个过着自己生活的人。")
        input.personalityPrompt.trim().take(LiteProductConfig.PERSONALITY_LIMIT).takeIf { it.isNotEmpty() }
            ?.let { appendLine("人设（≤${LiteProductConfig.PERSONALITY_LIMIT} 字）：$it") }
        input.relationshipPrompt.trim().take(LiteProductConfig.RELATIONSHIP_LIMIT).takeIf { it.isNotEmpty() }
            ?.let { appendLine("关系（一句）：$it") }
        if (input.currentSituation.isNotBlank()) {
            appendLine("你此刻的真实状态：${input.currentSituation.trim()}")
        }
        appendLine("现在大约是${input.timeOfDay}。")
        if (input.recent.isNotEmpty()) {
            appendLine("你们最近聊过（他在这段里说了想看什么、要拍什么，就按他说的拍；别的话题只当背景）：")
            input.recent.takeLast(LiteProductConfig.SCENE_RECENT_MESSAGES).forEach { appendLine("- $it") }
        }
        if (input.herOwnWords.isNotBlank()) {
            appendLine("你刚说的话：${input.herOwnWords.trim().take(HER_WORDS_LIMIT)}")
        }
        // 指令块走远程：调「她怎么决定拍什么」不用重装 App；下面是内置兜底。
        appendLine(com.aster.companion.update.RemotePromptStore.get("photo.shotRules") ?: DEFAULT_SHOT_RULES)
    }.trim()

    fun user(): String = "现在想清楚拍什么了吗？只输出 SHOT、HOW、WHY 三行中文。"

    /**
     * 解析她的决定。SHOT 必须是中文且非空，否则视为这次没想清楚（调用方回退单步流程）。
     */
    fun parse(raw: String): PhotoShot? {
        val shot = capture(raw, SHOT_LINE) ?: return null
        if (!shot.any { it.code in CJK_RANGE }) return null
        return PhotoShot(
            shot = shot,
            how = capture(raw, HOW_LINE),
            why = capture(raw, WHY_LINE)
        )
    }

    private fun capture(raw: String, pattern: Regex): String? =
        pattern.find(raw)?.groupValues?.get(1)?.trim()?.take(MAX_LINE_CHARS)?.ifBlank { null }

    /** 「她怎么决定拍什么」的内置兜底；正常情况用网关上的 photo.shotRules。 */
    private val DEFAULT_SHOT_RULES = """
        现在你要给他拍一张照片。先搞清楚这次是谁要的照片：

        一、如果是他明确要你拍什么（他说了拍哪里、拍什么东西、什么姿势、穿什么、要拍的部位），
            就**照他说的拍**：SHOT 里必须把他要的那些点都写进去，不许换成你自己更想拍的画面。
        二、如果这次是你自己主动想发（他没指定要看什么）：**先看你们正在聊的事**。
            你刚说的那句话里要拍什么、要给他看什么，就拍那个，不要换成别的；他说过想看什么，也照他说的来。
            这些话里都没有具体指向时，才按你此刻的处境拍（在做什么、穿什么、眼前有什么、什么心情）。
            不管哪种，都不能拍一个跟你们正在聊的事完全不搭的画面。

        两种情况都要：
        - SHOT 要写清楚具体拍什么，让人一眼就知道画面是什么：拍谁或拍哪里、是全身还是只拍某处、重点是什么；
        - 用你此刻真实的样子：在做什么、穿的什么、在哪；不要照抄设定里那身衣服；
        - 拍的是你自己时，画面里不要出现手机、相机、取景框。

        输出三行，都是中文，不要别的字：
        SHOT: 你要拍的画面（一句，≤40 字）
        HOW: 你怎么把这一刻留下来（一句，≤20 字）。二选一：
             ① 拍你自己的样子（人的眼睛看到的，画面里不要出现手机、相机、取景框）
             ② 拍你眼前/手边的东西（不拍自己）
        WHY: 你为什么现在想让他看这个（一句，≤20 字）
        禁止：写信或问候、列表、书面腔、提模型/系统/提示词、编造他就在你身边、编造没发生过的事。
    """.trimIndent()

    private const val HER_WORDS_LIMIT = 200
    private const val MAX_LINE_CHARS = 60
    private val CJK_RANGE = 0x3400..0x9FFF

    private val SHOT_LINE = Regex("(?im)^[ \\t]*(?:SHOT|画面|想拍|拍什么)[ \\t]*[:：][ \\t]*(.+?)[ \\t]*$")
    private val HOW_LINE = Regex("(?im)^[ \\t]*(?:HOW|怎么拍|拍法|视角)[ \\t]*[:：][ \\t]*(.+?)[ \\t]*$")
    private val WHY_LINE = Regex("(?im)^[ \\t]*(?:WHY|为什么|原因)[ \\t]*[:：][ \\t]*(.+?)[ \\t]*$")
}
