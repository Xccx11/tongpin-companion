package com.aster.companion.domain

import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 情绪底色（Alter）：让"她这会儿的语气"带上惯性，而不是每轮从头开始。
 *
 * ## 要解决的问题
 *
 * 同一段对话里氛围是会**攒**起来的：一句一句顶上去之后，就算话题换了，语气也不该立刻回到初始值；
 * 反过来被泼了冷水之后，也不该下一句就恢复如常。以前没有任何东西承载这件事——每一轮的语气都由
 * 模型独立决定，于是要么一直很平，要么突然跳一下，而且**跳完就没了**，攒不起来。
 *
 * ## 方向语义（**别搞反**）
 *
 * 变化量是 `-5..+5` 的整数，含义是**本轮氛围相对上一轮往哪边位移**：
 *
 * - **正数 = 更严肃 / 更收敛**（话变短、变正式、变克制、往正经处走）；
 * - **负数 = 更轻松 / 更活跃**（话变多、放得开、开玩笑、往热络处走）；
 * - `0` = 这一轮没有明显位移（多数时候应该是 0 或 ±1）。
 *
 * 注意"更活跃/更轻松"是**负方向**：它不是"变得更亲密"，也不是"变得更黏"。
 *
 * ## 机制（纯逻辑：无 IO、无模型调用、不读时钟）
 *
 * 1. 主对话每成功一轮产出整数变化量 [MIN_CHANGE]..[MAX_CHANGE]，非法/缺失一律忽略（视为 0）；
 * 2. 变化量**带符号累积**在 [State.alterValue] 上，同时按来源分桶累积在 [State.pendingScopes]；
 * 3. 累积到**阈值**就该换底色了。阈值不是常数：最近越活跃阈值越低
 *    （`max(base×0.5, base×(1 − density×densityFactor))`，基础 10 平滑降到 7），
 *    但永不低于基础值的一半，避免密集对话里每轮都换；
 * 4. 已经有一层底色时：**同向**变化让它更结实（权重上升），**反向**变化让它消退（权重下降）。
 *    权重掉到 [MIN_WEIGHT] 以下说明"这股劲过去了"，清除底色——而反向那部分位移仍留在
 *    [State.alterValue] 里，所以新方向可以自己攒到触发，不需要额外状态；
 * 5. 触发时由调用方去要一句底色描写；**方向由宿主累计值决定、强度由宿主公式算**，
 *    侧端**只允许返回描写**（不许它自己定方向/强度）。**生成失败不清空累积值**，只记退避时间；
 * 6. 主模型 payload 里**不要**带上累计值、上次方向、尝试时间与评分轨迹——那些是自我强化源。
 *
 * ## 接线约定
 *
 * - 状态落点：`companion_world_states` 的一个独立 `scope`（免 DB 迁移），内容是 [State] 的 JSON；
 * - 注入点：system 里**独立的一块**（[render] 的产物），不要塞进「此刻：」。那一块是她的生活状态，
 *   受"对话静默 30 分钟才允许后台改"的约束；底色是语气，两者混在一起以后就没法单独关掉了。
 */
object EmotionalOffsetPolicy {

    /** 单轮变化量的合法范围。 */
    const val MIN_CHANGE = -5
    const val MAX_CHANGE = 5

    /** 基础阈值：活跃度最低时需要的累积量。 */
    const val BASE_THRESHOLD = 10.0

    /**
     * 活跃度对阈值的最大削减比例：0.3 表示最活跃时阈值降到 70%（10 → 7）。
     *
     * 密集对话里她的语气本来就在连续变化，不需要攒那么久；反过来冷场时攒得慢一点，
     * 避免"半小时才说一句话，两轮就换了个底色"。
     */
    const val DENSITY_FACTOR = 0.3

    /** 阈值永远不低于基础值的这个比例。 */
    const val MIN_THRESHOLD_RATIO = 0.5

    /** 判定活跃度的窗口长度。 */
    const val DENSITY_WINDOW_MS = 60 * 60 * 1000L

    /** 窗口内到这个**有效回合数**就算满活跃（再多也不继续降阈值）。 */
    const val DENSITY_SATURATION_TURNS = 10

    /** 同向增强：每 1 点同向 |变化量| 让底色权重上升这么多。 */
    const val SAME_DIRECTION_BOOST = 0.05

    /** 反向消退：每 1 点反向 |变化量| 让底色权重下降这么多。 */
    const val OPPOSITE_DECAY = 0.15

    /** 权重低于这个值就认为那股劲过去了，清除底色。 */
    const val MIN_WEIGHT = 0.2

    /** 强度的合法上限（写进 prompt 的是数值，别让它无限涨）。 */
    const val MAX_INTENSITY = 2.0

    /** 底色描写长度上限（只占 system 一小段，不能长）。 */
    const val MAX_DESCRIPTION_CHARS = 60

    /** 评分轨迹保留条数（只用于侧端分析的上下文，不注入主模型）。 */
    const val HISTORY_LIMIT = 20

    /**
     * 底色的最大存活时长。
     *
     * **注意：这一条是同频自己加的，上游规格没有规定底色的清除时点**（上游只靠"反向消退"把
     * 权重压到 [MIN_WEIGHT] 以下来清除）。保留一个上限是为了避免"用户再没反驳过，某个语气
     * 就一直挂着几天"。真要完全对齐，把它设成 [Long.MAX_VALUE] 即可。
     */
    const val MAX_OFFSET_AGE_MS = 24 * 60 * 60 * 1000L

    /** 生成失败后的最短重试间隔（失败不清空累积值，只是先别急着再试）。 */
    const val RETRY_BACKOFF_MS = 5 * 60 * 1000L

    /** 底色方向。 */
    @Serializable
    enum class Direction {
        /** 更严肃 / 更收敛（由**正**变化量累积而来）。 */
        SERIOUS,

        /** 更轻松 / 更活跃（由**负**变化量累积而来）。 */
        RELAXED
    }

    /** 给模型看的方向词。 */
    fun directionWord(direction: Direction): String = when (direction) {
        Direction.SERIOUS -> "更严肃、更收敛"
        Direction.RELAXED -> "更轻松、更活跃"
    }

    /** 变化量的符号 → 方向；0 没有方向。 */
    fun directionOf(change: Int): Direction? = when {
        change > 0 -> Direction.SERIOUS
        change < 0 -> Direction.RELAXED
        else -> null
    }

    /** 一层底色：注入主模型的内容。方向与强度都由宿主决定，侧端只提供 [description]。 */
    @Serializable
    data class Offset(
        val direction: Direction,
        val description: String,
        val intensity: Double,
        val generatedAt: Long
    )

    /** 评分轨迹：只喂给侧端分析，**不进主模型 payload**。 */
    @Serializable
    data class HistoryEntry(
        /** 递增的轮次序号（[State.turn]）。 */
        val turn: Long,
        /** 这一轮的相位：`user-message` / `advance` / `intent-due` 等，由调用方给。 */
        val phase: String,
        /** 本轮的变化量。 */
        val alter: Int,
        /** 本轮吃下之后的累计值。 */
        val alterValue: Double,
        val timestamp: Long,
        /** 这一轮的主要参与者；null = 全局生活剧本（与具体参与者无关）。 */
        val participantId: String? = null
    )

    /**
     * 按来源分开的待分析累计。
     *
     * 侧端分析只该看到**真正贡献了这次触发的证据**——所以除了全局 [State.alterValue]，
     * 还按来源分别记账。
     */
    @Serializable
    data class PendingScope(
        val participantId: String? = null,
        val alterValue: Double = 0.0
    )

    /**
     * 全部状态。用 [toJson] / [fromJson] 存进 world state 的 `content`。
     *
     * 所有字段都有默认值：字段以后增加时旧数据也读得进来。
     * （注：本版本把原来的 `pending` 改名成了 `alterValue`，旧 JSON 里的 `pending` 会被忽略，
     * 累计值等价于清零一次——这个功能刚上线，没有需要迁移的真实数据。）
     */
    @Serializable
    data class State(
        /** 累积中的位移（带符号）。到 `|alterValue| ≥ 阈值` 就该换底色了。 */
        val alterValue: Double = 0.0,
        /** 当前底色的结实程度 0..1。 */
        val weight: Double = 0.0,
        /** 上一次成功触发的方向：+1 = 严肃，-1 = 轻松，0 = 还没触发过。 */
        val lastTriggerDirection: Int = 0,
        /** 当前底色；null = 没有底色。 */
        val offset: Offset? = null,
        /** 评分轨迹（最近 [HISTORY_LIMIT] 条），只给侧端分析看。 */
        val history: List<HistoryEntry> = emptyList(),
        /** 按来源分开的待分析累计。 */
        val pendingScopes: List<PendingScope> = emptyList(),
        /** 内部轮次计数（[HistoryEntry.turn] 用它）。 */
        val turn: Long = 0L,
        val lastUpdatedAt: Long = 0L,
        /** 上次尝试生成（含失败）的时刻，用于退避。 */
        val lastAttemptAt: Long = 0L
    ) {
        fun toJson(): String = json.encodeToString(serializer(), this)

        companion object {
            private val json = Json { ignoreUnknownKeys = true }

            fun fromJson(raw: String?): State {
                if (raw.isNullOrBlank()) return State()
                return runCatching { json.decodeFromString(serializer(), raw) }.getOrDefault(State())
            }
        }
    }

    /** 一次触发的全部事实：方向、累计值、当时的阈值、强度。侧端只能拿到描写。 */
    data class Trigger(
        val direction: Direction,
        val value: Double,
        val threshold: Double,
        val intensity: Double
    )

    /** 底色此刻是否可用（存在、有描写、且没超过 [MAX_OFFSET_AGE_MS]）。 */
    fun activeOffset(state: State, now: Long): Offset? {
        val offset = state.offset ?: return null
        if (offset.description.isBlank()) return null
        if (offset.generatedAt <= 0L) return null
        if (now - offset.generatedAt > MAX_OFFSET_AGE_MS) return null
        return offset
    }

    /** 阈值：活跃度越高越低。[recentTurns] = 最近 [DENSITY_WINDOW_MS] 内的**有效回合数**。 */
    fun thresholdFor(recentTurns: Int): Double {
        val density = (recentTurns.toDouble() / DENSITY_SATURATION_TURNS).coerceIn(0.0, 1.0)
        val raw = BASE_THRESHOLD * (1.0 - density * DENSITY_FACTOR)
        return raw.coerceAtLeast(BASE_THRESHOLD * MIN_THRESHOLD_RATIO)
    }

    /** 强度：累计位移相对阈值有多满，夹在 `[0, MAX_INTENSITY]`。 */
    fun intensityOf(alterValue: Double, threshold: Double): Double {
        if (threshold <= 0.0) return 0.0
        return (abs(alterValue) / threshold).coerceIn(0.0, MAX_INTENSITY)
    }

    /**
     * 现在该不该去要一句底色描写；不该就返回 null。
     *
     * 只有三个条件：累积值够阈值、不在失败退避窗口里、累计值不为零。
     * **刻意没有"已有底色就不触发"这道闸**——上游的触发条件只看累计值，而成功生成会把
     * 累计值清零，所以"再攒够一次"自然就是下一次触发。
     */
    fun triggerFor(state: State, now: Long, recentTurns: Int): Trigger? {
        val direction = directionOf(signOf(state.alterValue)) ?: return null
        val threshold = thresholdFor(recentTurns)
        if (abs(state.alterValue) < threshold) return null
        if (state.lastAttemptAt > 0L && now - state.lastAttemptAt < RETRY_BACKOFF_MS) return null
        return Trigger(
            direction = direction,
            value = state.alterValue,
            threshold = threshold,
            intensity = intensityOf(state.alterValue, threshold)
        )
    }

    /**
     * 吃掉一轮的变化量。
     *
     * [change] 为 null 或 0 时原样返回（非法/缺失一律忽略）。
     * [sourceId] 是这一轮的主要参与者（私聊=对方角色；群聊=发言者；null=全局生活剧本）。
     */
    fun observe(
        state: State,
        change: Int?,
        sourceId: String?,
        phase: String,
        now: Long,
        recentTurns: Int
    ): State {
        val clamped = (change ?: 0).coerceIn(MIN_CHANGE, MAX_CHANGE)
        if (clamped == 0) return state

        val magnitude = abs(clamped).toDouble()
        val sign = signOf(clamped.toDouble())
        val nextValue = state.alterValue + clamped.toDouble()

        // 同向增强 / 反向消退：只作用于"已经存在的那层底色"。
        var weight = state.weight
        var offset = state.offset
        if (state.lastTriggerDirection != 0) {
            if (sign == state.lastTriggerDirection) {
                weight = (weight + magnitude * SAME_DIRECTION_BOOST).coerceAtMost(1.0)
            } else {
                val next = weight - magnitude * OPPOSITE_DECAY
                if (next < MIN_WEIGHT) {
                    // 这股劲过去了：清掉底色（累计值不动，新方向可以自己攒到触发）。
                    weight = 0.0
                    offset = null
                } else {
                    weight = next
                }
            }
        }

        val turn = state.turn + 1
        val history = (state.history + HistoryEntry(
            turn = turn,
            phase = phase,
            alter = clamped,
            alterValue = nextValue,
            timestamp = now,
            participantId = sourceId
        )).takeLast(HISTORY_LIMIT)

        val scopes = state.pendingScopes.toMutableList()
        val index = scopes.indexOfFirst { it.participantId == sourceId }
        if (index >= 0) {
            scopes[index] = scopes[index].copy(alterValue = scopes[index].alterValue + clamped.toDouble())
        } else {
            scopes += PendingScope(participantId = sourceId, alterValue = clamped.toDouble())
        }

        return state.copy(
            alterValue = nextValue,
            weight = weight.coerceIn(0.0, 1.0),
            offset = offset,
            history = history,
            pendingScopes = scopes,
            turn = turn,
            lastUpdatedAt = now
        )
    }

    /**
     * 生成成功：换上新的底色、权重归 1、记录方向、**累计值清零**。
     *
     * [description] 会折叠空白、只取第一行、截到 [MAX_DESCRIPTION_CHARS]。
     * 方向与强度一律取自 [trigger]（宿主算的），不采信侧端返回的东西。
     * 描写为空视为**失败**（只推退避时间，不清累计值）——侧端只能返回描写，返回空就是没产出。
     */
    fun onGenerated(state: State, trigger: Trigger, description: String, now: Long): State {
        val clean = cleanDescription(description)
        if (clean.isBlank()) return state.copy(lastAttemptAt = now)

        return state.copy(
            alterValue = 0.0,
            weight = 1.0,
            lastTriggerDirection = signOf(trigger.value),
            offset = Offset(
                direction = trigger.direction,
                description = clean,
                intensity = trigger.intensity,
                generatedAt = now
            ),
            pendingScopes = emptyList(),
            lastAttemptAt = now,
            lastUpdatedAt = now
        )
    }

    /**
     * 生成失败：**保留**累积值与轨迹（攒起来的东西不能因为一次失败就丢），
     * 只把退避时间推到现在。
     */
    fun onGenerationFailed(state: State, now: Long): State =
        state.copy(lastAttemptAt = now)

    /**
     * 拼进 system 的那一块；没有可用底色时返回空串（调用方据此不 append 任何东西）。
     *
     * 形态对齐上游注入的 `{direction, description, intensity}`：方向和强度都给出去，
     * 措辞写清"这是延续下来的"，否则模型会把它当成这一轮的新设定去演。
     */
    fun render(state: State, now: Long): String {
        val offset = activeOffset(state, now) ?: return ""
        return "情绪底色（这是从前面几轮延续下来的语气，不是这一轮才出现的；倾向=" +
            "${directionWord(offset.direction)}，强度=${formatIntensity(offset.intensity)}）：" +
            offset.description
    }

    /** 强度保留一位小数（`1.2`、`2.0`）。 */
    fun formatIntensity(intensity: Double): String =
        String.format(java.util.Locale.US, "%.1f", intensity.coerceIn(0.0, MAX_INTENSITY))

    /**
     * 解析模型给出的这一轮变化量：取第一个整数（允许 `"+3"` / `"3 分"` / `"变化：-2"`）。
     *
     * 认不出来返回 null（调用方按"这一轮没有位移"处理），认出来则夹进合法范围。
     */
    fun parseChange(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("[-+]?\\d+").find(raw.trim()) ?: return null
        val value = match.value.toIntOrNull() ?: return null
        return value.coerceIn(MIN_CHANGE, MAX_CHANGE)
    }

    /** [parseChange] 的宽松版：认不出来当作 0。 */
    fun parseChangeOrZero(raw: String?): Int = parseChange(raw) ?: 0

    /** 描写清洗：只取第一行、折叠空白、截断。 */
    fun cleanDescription(raw: String?): String = raw
        .orEmpty()
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.replace(Regex("\\s+"), " ")
        ?.take(MAX_DESCRIPTION_CHARS)
        .orEmpty()

    private fun signOf(value: Double): Int = when {
        value > 0.0 -> 1
        value < 0.0 -> -1
        else -> 0
    }
}
