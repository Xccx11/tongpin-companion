package com.aster.companion.agent.world

/** V3.0 世界数据模型：世界状态快照 / 计划事件 / 未来时间线。 */

/** 计划事件（WorldDirector 输出的时间线事件）。 */
data class PlannedEvent(
    val id: String,
    val time: String,               // "19:20"（HH:mm）
    val durationMinutes: Int,
    val type: String,               // activity / rest / meal ...
    val participants: List<String>,
    val description: String,
    val status: String = "planned"  // planned / running / finished / cancelled
)

/** 未来时间线（一次规划产物）。 */
data class FutureTimeline(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val events: List<PlannedEvent>,
    val status: String = "planned"
)

/** 角色快照（WorldDirector 输入）。 */
data class CharacterSnapshot(
    val name: String,
    val location: String,
    val state: String,
    val relationship: Map<String, String> = emptyMap()
)

/** 世界状态快照（WorldDirector 输入）。 */
data class WorldSnapshot(
    val time: String,
    val weather: String,
    val location: String,
    val characters: List<CharacterSnapshot>,
    val recentEvents: List<String> = emptyList(),
    val importantMemory: List<String> = emptyList()
)
