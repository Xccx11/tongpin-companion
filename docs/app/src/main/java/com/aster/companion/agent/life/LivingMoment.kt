package com.aster.companion.agent.life

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 她**此刻**的样子——不是日程表上的一格。
 *
 * ## 为什么不是「时间段」
 *
 * 第一版把它做成了排好的一天（起床、通勤、午饭、晚饭各占一段），结果是「天天给自己排班」，
 * 一看就假。真人没有表，只有**一件正在做的事**，做多久看心情和状态：
 * 看剧看入迷能看三集，也可能十分钟就腻了；本来要出门，懒了一下就没去。
 *
 * ## [reviewAt] 不是结束时间
 *
 * 它是「**下次可以问她一句**要不要换」的时间。问到之前她一直在做这件事；
 * 问到之后，她**多半也会说继续**——人是有惯性的。这一点是整套设计的关键：
 * 换不换由她决定，系统只负责在该问的时候问一声。
 */
@Serializable
data class LivingMoment(
    /** 她正在做的一件事，一句话，比如「在看剧」「在图书馆翻书」。 */
    val activity: String,
    /** 这件事从什么时候开始（epoch millis）。 */
    val startedAt: Long,
    /** 这会儿的心情。 */
    val mood: String = "",
    /** 结构化地点 id，对齐 `WorldLocationCatalog`。 */
    val locationId: String = "home",
    /** 她自己的打算，比如「看完这集再说」。空表示没想那么多。 */
    val intent: String = "",
    /** 下次可以问她要不要换的时间——**不是**必须换的时间。 */
    val reviewAt: Long,
    /** 如果她是换过来的，她自己的理由（「有点饿了」）。 */
    val reason: String = ""
) {
    fun durationMs(now: Long): Long = (now - startedAt).coerceAtLeast(0L)

    fun isDueForReview(now: Long): Boolean = now >= reviewAt

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(raw: String?): LivingMoment? {
            if (raw.isNullOrBlank()) return null
            return try {
                json.decodeFromString(serializer(), raw)
            } catch (_: Exception) {
                null
            }
        }
    }
}
