package com.aster.companion.agent

import com.aster.companion.domain.DurationClass
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 单个角色的批量推演结果：模型决定该角色此刻的心情/活动是否变化。
 *
 * 字段全部带默认值 + 允许缺失：模型输出缺字段时不抛异常，
 * changed 缺省为 false（无变化）。
 */
/** 分享意愿：角色此刻想不想把这一刻给某个人看（生活分享确认卡的来源）。 */
@Serializable
data class ShareMoment(
    val want: Boolean = false,
    val what: String? = null,
    val words: String? = null,
    val to: String? = null
)

@Serializable
data class WorldCharacterPlan(
    val character_id: String? = null,
    val mood: String? = null,
    val activity: String? = null,
    val duration_class: String? = null,
    /** 模型给出的睡眠时长（分钟）；只在 duration_class=SLEEP 时有意义，缺失时按兜底时长。 */
    val sleep_minutes: Long? = null,
    val changed: Boolean = false,
    val memory: String? = null,
    val share_moment: ShareMoment? = null
) {
    /** 出口意愿（一次推演产出多出口候选）：NONE / POST（朋友圈）/ CONTACT（主动联系）/ SHARE（生活分享）。 */
    val momentIntent: String
        get() = share_moment?.takeIf { it.want }?.let { "SHARE" } ?: "NONE"

    /** 时长分类容错解析：非法值一律归为 ONGOING，保证任何输入都不抛异常。 */
    val durationClass: DurationClass
        get() = when (duration_class?.trim()?.uppercase()) {
            "INSTANT" -> DurationClass.INSTANT
            "TRANSITION" -> DurationClass.TRANSITION
            "SLEEP" -> DurationClass.SLEEP
            else -> DurationClass.ONGOING
        }
}

/** 模型输出的多角色批量推演 JSON：{"characters": [...]}。 */
@Serializable
data class WorldBatchPlan(
    val characters: List<WorldCharacterPlan> = emptyList()
) {
    /** 仅保留模型确认「需要变化」且有角色标识的条目；其余条目不落库。 */
    fun isEmpty(): Boolean = changedPlans().isEmpty()

    fun changedPlans(): List<WorldCharacterPlan> =
        characters.filter { it.changed && !it.character_id.isNullOrBlank() }
}

/** 解析模型输出的多角色批量推演 JSON，畸形输入一律回退为空计划而非抛异常。 */
object WorldBatchPlanParser {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parse(raw: String): WorldBatchPlan {
        val jsonObject = extractJsonObject(raw) ?: return WorldBatchPlan()
        return runCatching { json.decodeFromString(WorldBatchPlan.serializer(), jsonObject) }
            .getOrDefault(WorldBatchPlan())
    }

    /** 模型常用 Markdown 代码块或前言包裹 JSON：提取首个 { 到最后一个 } 之间的内容。 */
    private fun extractJsonObject(raw: String): String? {
        val firstBrace = raw.indexOf('{')
        val lastBrace = raw.lastIndexOf('}')
        if (firstBrace < 0 || lastBrace <= firstBrace) return null
        return raw.substring(firstBrace, lastBrace + 1)
    }
}
