package com.aster.companion.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 主动性节律（Urge）：她"想开口"的时机，**不该是一个固定冷却**。
 *
 * ## 它解决什么
 *
 * 现在的主动联系用的是"距上次开口满 N 分钟就允许探一次"。这有两个毛病：
 *
 * 1. **不看节奏**——刚聊完两小时的热络，和冷场三天，判据一模一样；
 * 2. **太准点**——冷却一到就必然考虑开口，像定时任务而不像一个人。
 *
 * 上游的做法是：先把"最近有多活跃"算成一个 0..1 的密度，再用密度在**两个端点之间插值**
 * 出下一次忍耐时间，最后加一点随机。热络时她惦记着，忍不了多久；冷下来之后她也能自己待着。
 *
 * ## 公式（对齐上游）
 *
 * ```
 * density = min(最近 15 分钟内的 2 分钟桶数 / 6, 1) × 0.5 ^ (距最后一个桶的分钟数 / 半衰期)
 * delay   = idle − density × (idle − hot)          // 再乘 ±jitter 的随机
 * ```
 *
 * - 桶只统计"有没有消息"，不数条数——**连发十条不算更热**，那只是同一句话；
 * - 衰减项让"刚说过话"和"一小时没动静"明显区分开；
 * - [Frequency] 提供 idle / hot 两端：LOW 是"她本来就不太主动"，HIGH 是"她很黏"。
 *
 * ## 与可打扰性的分工（**不要混**）
 *
 * [CompanionAgencyPolicy] 管的是"**能不能**"（睡着、设备、场合、24 小时配额、硬间隔），
 * 这里管的是"**想不想**"。两者是**与**关系：想说话也得先过得了那几道硬门。
 * 另外本模块只产出"下次什么时候可以考虑"，**不产生任何内容**，也不写事实库、不碰记忆、
 * 不进 Alter 的素材。
 *
 * ## 默认关闭
 *
 * 上游默认 `enabled = false`。这里同样：不开就是**完全保留旧行为**（固定冷却），
 * 开了才由节律接管"多久之后可以考虑"。
 */
object UrgePolicy {

    /** 状态存放的 world state scope（免 DB 迁移）。 */
    const val SCOPE = "URGE"

    fun rowId(threadId: String): String = "urge:$threadId"

    /** 活跃度的桶宽度与观察窗。 */
    const val BUCKET_MS = 2 * 60_000L
    const val WINDOW_MS = 15 * 60_000L

    /** 窗口内到这个桶数就算"满活跃"。 */
    const val SATURATION_BUCKETS = 6

    /** 她本来有多主动。 */
    enum class Frequency { LOW, MEDIUM, HIGH }

    /**
     * 节律参数。
     *
     * `idle` / `hot` 两个端点的取值**是本地标定的**（上游只给了"由 frequency 决定"，
     * 没给具体分钟数）：MEDIUM 的 60 / 15 分钟与同频原有的 20 分钟冷却同一个量级，
     * 所以打开之后不会突然变得话痨或突然不说话。
     */
    data class Config(
        val enabled: Boolean = false,
        val frequency: Frequency = Frequency.MEDIUM,
        /** 密度衰减的半衰期：45 分钟前的活跃度只剩一半。 */
        val halfLifeMinutes: Int = 45,
        /** 随机抖动比例（±15%）。 */
        val jitterRatio: Double = 0.15,
        /**
         * "偶尔极端"的概率。
         *
         * 上游参数名是 `extremeChance = 0.03`，但**规格里没写它到底取哪一端**；
         * 这里解释为"偶尔突然很想说"（延迟缩到 1/4）——沉默里的人突然冒一句，比
         * "偶尔多忍一会儿"更像活人。
         */
        val extremeChance: Double = 0.03
    )

    /** 随机源。抽出来是为了让单测给定值（生产用 [Sampler.random]）。 */
    fun interface Sampler {
        /** 返回 0.0..1.0。 */
        fun next(): Double

        companion object {
            val random = Sampler { Math.random() }
        }
    }

    /** 她的节律状态。只放调度元数据，**不放内容**。 */
    @Serializable
    data class State(
        /** 下次"可以考虑开口"的时刻。 */
        val nextAt: Long = 0L,
        /** 上次真正开口的时刻（0 = 还没开过口）。 */
        val lastAt: Long = 0L,
        /** 上次算出来的忍耐分钟数，纯诊断用（状态卡能看到"她这次忍了多久"）。 */
        val lastDelayMinutes: Double = 0.0
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

    /** 空闲端：完全不活跃时她愿意等多久。 */
    fun idleMinutes(frequency: Frequency): Double = when (frequency) {
        Frequency.LOW -> 90.0
        Frequency.MEDIUM -> 60.0
        Frequency.HIGH -> 30.0
    }

    /** 热络端：满活跃时她最多能忍多久。 */
    fun hotMinutes(frequency: Frequency): Double = when (frequency) {
        Frequency.LOW -> 25.0
        Frequency.MEDIUM -> 15.0
        Frequency.HIGH -> 8.0
    }

    /**
     * 活跃度 0..1。
     *
     * [messageTimes] 是**最近的收发时间**（谁的都算：她刚说过话也算热络）。
     * 只数落在 [WINDOW_MS] 里的桶；衰减按"距最后一个桶"算。
     */
    fun densityOf(
        messageTimes: List<Long>,
        now: Long,
        halfLifeMinutes: Int = 45
    ): Double {
        val halfLife = halfLifeMinutes.coerceAtLeast(1).toDouble() * 60_000L
        val recent = messageTimes.filter { now - it in 0 until WINDOW_MS }
        if (recent.isEmpty()) return 0.0
        val buckets = recent.map { it / BUCKET_MS }.toSet().size
        val saturation = (buckets.toDouble() / SATURATION_BUCKETS).coerceAtMost(1.0)
        val sinceLastBucket = (now - recent.max()) / 60_000.0
        val decay = Math.pow(0.5, sinceLastBucket / (halfLife / 60_000.0))
        return (saturation * decay).coerceIn(0.0, 1.0)
    }

    /** 由密度插值出的忍耐分钟数（还没加随机）。 */
    fun delayMinutes(density: Double, frequency: Frequency): Double {
        val idle = idleMinutes(frequency)
        val hot = hotMinutes(frequency)
        val d = density.coerceIn(0.0, 1.0)
        return idle - d * (idle - hot)
    }

    /**
     * 重排下一次"可以考虑开口"的时刻。
     *
     * [sampler] 依次被用于：极端判定 → 抖动。所以给定采样序列就能把结果钉死。
     */
    fun schedule(
        state: State,
        now: Long,
        messageTimes: List<Long>,
        config: Config = Config(),
        sampler: Sampler = Sampler.random
    ): State {
        val density = densityOf(messageTimes, now, config.halfLifeMinutes)
        var minutes = delayMinutes(density, config.frequency)
        if (sampler.next() < config.extremeChance) {
            minutes *= EXTREME_RATIO
        }
        val jitter = 1.0 + config.jitterRatio * (sampler.next() * 2.0 - 1.0)
        val delay = (minutes * jitter).coerceAtLeast(MIN_DELAY_MINUTES)
        return state.copy(
            nextAt = now + (delay * 60_000L).toLong(),
            lastDelayMinutes = delay
        )
    }

    /** 现在是不是"可以考虑开口"了。没排过期（`nextAt = 0`）也算可以——首次开口不该被无缘无故拖住。 */
    fun isDue(state: State, now: Long): Boolean = state.nextAt <= now

    /** 记下她这次真的开口了（之后必须重新 [schedule]）。 */
    fun recordSpoke(state: State, at: Long): State = state.copy(lastAt = at)

    /** 延迟下限：抖不动、也算不出负的。 */
    const val MIN_DELAY_MINUTES = 2.0

    /** "偶尔极端"时延迟缩到这个比例。 */
    const val EXTREME_RATIO = 0.25
}
