package com.aster.companion.agent.life


import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * 「要不要换」这件事的纯逻辑层：prompt、解析、下一次该在什么时候问。
 *
 * ## 整套设计的重心在这里：默认是「继续」
 *
 * 真人大部分时候是在接着做手头的事。所以：
 * - prompt 明确说了「没做完、没腻、没被打断，就接着做」；
 * - **解析失败也当继续**（[parse] 返回 null 时，调用方按继续处理）；
 * - 换的**理由必须来自她的状态**（饿了/困了/腻了/被人叫走），不是「到点了」。
 *
 * 这样即使模型抽风，最坏结果也只是「她一直在做同一件事」——那也还是个能接受的样子，
 * 不会退回到「90 秒换一件事」。
 */
object LifeSwitchPolicy {

    /** Continue is also a valid first decision: give the capsule a stable visible state. */
    fun activityForContinue(currentActivity: String?): String =
        currentActivity?.trim()?.takeIf { it.isNotEmpty() } ?: "安静待着"

    /** 她的决定。 */
    sealed interface Decision {
        /** 接着做。大多数时候都是这个。 */
        data class Continue(val intent: String = "", /** 她自己说的"还要做多久"（分钟）。 */ val durationMinutes: Int? = null) : Decision

        /** 换一件事。[locationId] 可能为空或不在目录里，调用方负责校验。 */
        data class Switch(
            val activity: String,
            val locationId: String?,
            val mood: String,
            val reason: String,
            val intent: String,
            /** 她自己说的"还要做多久"（分钟）。 */
            val durationMinutes: Int? = null
        ) : Decision

        /** 困了，去睡。 */
        data class Sleep(val intent: String = "") : Decision
    }

    data class Input(
        val characterName: String,
        val personalityPrompt: String = "",
        val relationshipPrompt: String = "",
        val moment: LivingMoment,

        val now: Long,
        val zone: ZoneId = ZoneId.systemDefault(),
        /** 最近几条聊天短摘录——她被谁叫走、聊到什么都可能影响她接下来做什么。 */
        val recent: List<String> = emptyList()
    )

    fun system(input: Input): String = buildString {
        val name = input.characterName.trim()
        appendLine("你是「$name」，正在过自己的日子。下面是你要判断的一件事：**接下来还做不做手头这件事**。")
        input.personalityPrompt.trim().take(PERSONALITY_LIMIT).takeIf { it.isNotEmpty() }
            ?.let { appendLine("你的人设（≤$PERSONALITY_LIMIT 字）：$it") }
        input.relationshipPrompt.trim().take(RELATIONSHIP_LIMIT).takeIf { it.isNotEmpty() }
            ?.let { appendLine("你和对方的关系（一句）：$it") }
        appendLine()
        appendLine("你**现在正在做的事**：${input.moment.activity}")
        appendLine("（已经持续 ${input.moment.durationMs(input.now) / 60_000L} 分钟。${input.moment.intent.takeIf { it.isNotBlank() }?.let { "你原本打算：$it。" } ?: ""}）")

        appendLine("现在是 ${clock(input.now, input.zone)}。")
        if (input.recent.isNotEmpty()) {
            appendLine("你们最近聊了几句：")
            input.recent.take(LAST_CHAT_LINES).forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("判断的要求：")
        appendLine("- **手头这件事没做完、没腻、也没被打断，就接着做**。真人大部分时候都是在接着做。")
        appendLine("- 只有下面几种情况才换：饿了；困了（该睡了）；这件事做完了或者不想做了；有人叫你、或者你突然想起别的事。")
        appendLine("- 换的话，换什么由你自己定，理由要是你自己的状态，不是「时间到了」。")
        appendLine("- 不许编造对方在场，也不许编造没发生过的共同事件。")
    }.trim()

    fun user(input: Input): String = buildString {
        appendLine("从下面三种里选一种回，只回一行。**最后一段一律要回“还要做多久”，单位分钟**（10~480 的整数）：")
        appendLine("继续|<你接下来的打算>|<还要做多久>")
        appendLine("换|<在做什么>|${placeHint()}|<心情>|<为什么>|<打算>|<还要做多久>")
        appendLine("睡了|<打算>")
        appendLine()
        appendLine("例如：")
        appendLine("继续|看完这集再说|35")
        appendLine("换|去楼下便利店买点吃的|outside.convenience|有点馋|有点饿了|顺便走走|25")
        appendLine("睡了|眼睛睁不开了")
        appendLine()
        appendLine("时间按你自己的感觉给：看剧可能 40 分钟，写作业可能 90 分钟，逛街可能 120 分钟。")
    }.trim()

    /**
     * 解析模型输出。
     *
     * **认不出来就返回 null，调用方按「继续」处理**——这是惯性的兜底。
     *
     * 模型常常先说一句「好的，我明白了」再给结果，所以这里逐行往下找**第一行能认出来的**，
     * 而不是只看第一行（只看第一行的话，它随口寒暄一句就整条作废）。
     */
    fun parse(raw: String): Decision? = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("```") }
        .mapNotNull { parseLine(it) }
        .firstOrNull()

    /** 单行解析；认不出来返回 null，交给 [parse] 继续看下一行。 */
    fun parseLine(line: String): Decision? {
        val parts = line.split('|').map { it.trim() }
        if (parts.isEmpty()) return null
        return when (parts[0]) {
            "继续", "继续。", "CONTINUE" -> Decision.Continue(
                intent = parts.getOrNull(1).orEmpty().cap(INTENT_MAX),
                durationMinutes = parts.getOrNull(2).let(::durationOf)
            )
            "睡了", "睡", "SLEEP" -> Decision.Sleep(intent = parts.getOrNull(1).orEmpty().cap(INTENT_MAX))
            "换", "SWITCH" -> {
                val activity = parts.getOrNull(1).orEmpty()
                if (activity.length !in ACTIVITY_MIN..ACTIVITY_MAX) return null
                Decision.Switch(
                    activity = activity,
                    locationId = parts.getOrNull(2)?.takeIf { it.isNotBlank() && it in KNOWN_PLACES },
                    mood = parts.getOrNull(3).orEmpty().cap(MOOD_MAX),
                    reason = parts.getOrNull(4).orEmpty().cap(REASON_MAX),
                    intent = parts.getOrNull(5).orEmpty().cap(INTENT_MAX),
                    durationMinutes = parts.getOrNull(6).let(::durationOf)
                )
            }

            else -> null
        }
    }

    /**
     * 下一次该在什么时候问她。
     *
     * **这不是「结束时间」**，是「可以问一句的时间」。所以区间要宽，而且要随机——
     * 真人没有固定节奏：一件事可能 20 分钟就问一次，也可能一两个小时才想起来。
     */
    fun nextReviewAt(now: Long, decision: Decision, rnd: Random): Long = when (decision) {
        // **她说多久就多久**（用户要求）：App 按她给的时间开下一次状态更新，
        // 只有她没给/给得离谱时才退回随机区间。
        is Decision.Continue -> decision.durationMinutes?.let { now + clampReview(it) }
            ?: (now + rnd.nextLong(CONTINUE_MIN_MS, CONTINUE_MAX_MS))
        is Decision.Switch -> decision.durationMinutes?.let { now + clampReview(it) }
            ?: (now + rnd.nextLong(SWITCH_MIN_MS, SWITCH_MAX_MS))
        is Decision.Sleep -> now + rnd.nextLong(SLEEP_MIN_MS, SLEEP_MAX_MS)
    }

    /** 把"还要做多久"夹进合理区间：太短会让她像每隔几分钟换一次，太长会像卡住。 */
    private fun clampReview(minutes: Int): Long =
        (minutes.toLong() * 60_000L).coerceIn(REVIEW_MIN_MS, REVIEW_MAX_MS)

    /** 解析她说的时间（分钟）；认不出返回 null。 */
    private fun durationOf(raw: String?): Int? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            Regex("\\d+").find(text)?.value?.toIntOrNull()?.takeIf { it in REVIEW_INPUT_MIN..REVIEW_INPUT_MAX }
        }

    /** 给模型看的合法地点清单。对齐 `WorldLocationCatalog.defaults()`。 */
    private fun placeHint(): String = KNOWN_PLACES.joinToString("/")

    private fun clock(now: Long, zone: ZoneId): String {
        val time = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
        return "%02d:%02d".format(time.hour, time.minute)
    }

    private fun String.cap(max: Int): String = trim().replace(WHITESPACE, " ").take(max)

    private val WHITESPACE = Regex("\\s+")

    private const val PERSONALITY_LIMIT = 2000
    private const val RELATIONSHIP_LIMIT = 120
    private const val ACTIVITY_MIN = 2
    /** 一件事的说法要短。超过这个长度就是在写作文，不是「在做什么」。 */
    private const val ACTIVITY_MAX = 24
    private const val MOOD_MAX = 8
    private const val REASON_MAX = 24
    private const val INTENT_MAX = 40
    private const val LAST_CHAT_LINES = 40

    /** 她自己说的时间允许范围（分钟）：10 分钟 ~ 8 小时。 */
    private const val REVIEW_INPUT_MIN = 10
    private const val REVIEW_INPUT_MAX = 480

    /** 真正落到调度上的区间：比输入再收一点，避免"几分钟就换一次"或"一整天不动"。 */
    private const val REVIEW_MIN_MS = 10 * 60_000L
    private const val REVIEW_MAX_MS = 6 * 3_600_000L

    /** 问一次「继续吗」的间隔：一件小事 20~60 分钟。 */
    private const val CONTINUE_MIN_MS = 20 * 60_000L
    private const val CONTINUE_MAX_MS = 60 * 60_000L

    /** 换了一件事之后，让她先专心一阵子。 */
    private const val SWITCH_MIN_MS = 30 * 60_000L
    private const val SWITCH_MAX_MS = 120 * 60_000L

    /** 睡了就别老问，6~9 小时之后再说。 */
    private const val SLEEP_MIN_MS = 6 * 3_600_000L
    private const val SLEEP_MAX_MS = 9 * 3_600_000L

    /** 合法地点。对齐 `WorldLocationCatalog.defaults()`。 */
    val KNOWN_PLACES: Set<String> = setOf(
        "home", "home.living_room", "home.kitchen", "home.bedroom", "home.bathroom",
        "outside", "outside.office", "outside.classroom", "outside.library", "outside.canteen",
        "outside.cafe", "outside.milk_tea", "outside.supermarket", "outside.convenience",
        "outside.mall", "outside.street", "outside.park", "outside.gym", "outside.playground",
        "outside.restaurant", "outside.metro", "outside.bookstore", "outside.friend_home",
        "outside.night_market", "outside.internet_cafe", "outside.fitting_room",
        "outside.hospital", "outside.hair_salon"
    )
}
