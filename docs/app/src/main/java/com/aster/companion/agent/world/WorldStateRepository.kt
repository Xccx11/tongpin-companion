package com.aster.companion.agent.world

import com.aster.companion.data.CharacterEntity
import com.aster.companion.data.CharacterLivingStateEntity
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.WorldLocationEntity
import com.aster.companion.data.WorldStateRowEntity
import com.aster.companion.data.WorldUserStateEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorldStateRepository @Inject constructor(
    private val dao: CompanionDao
) {
    suspend fun initialize(now: Long = System.currentTimeMillis()) {
        dao.upsertWorldLocations(WorldLocationCatalog.defaults(now))
        if (dao.primaryWorldState() == null) {
            dao.upsertWorldStateRow(defaultWorldState(now))
        }
        if (dao.worldUserState() == null) {
            dao.upsertWorldUserState(defaultUserState(now))
        }
    }

    suspend fun snapshot(now: Long = System.currentTimeMillis()): WorldEngineSnapshot {
        initialize(now)
        return WorldEngineSnapshot(
            world = requireNotNull(dao.primaryWorldState()),
            user = requireNotNull(dao.worldUserState()),
            locations = dao.worldLocations(),
            characters = dao.allCharacters(),
            livingStates = dao.allLivingStates()
        )
    }

    // 这里原本有 observeScene()：把世界状态投影成"第一人称场景"给世界页用。
    // 世界页已删除（[com.aster.ui.MainActivity] 的 MainTab.World），投影类型 WorldSceneProjection
    // 也一并删除 —— 留着它等于每次写世界状态都白算一遍场景文案。

    suspend fun recordUserState(
        locationId: String,
        visibility: String,
        action: String,
        now: Long = System.currentTimeMillis()
    ) {
        initialize(now)
        val resolvedLocation = dao.worldLocation(locationId)?.id ?: WorldLocationCatalog.UNKNOWN_ID
        dao.upsertWorldUserState(
            WorldUserStateEntity(
                locationId = resolvedLocation,
                visibility = visibility,
                lastAction = action.take(MAX_ACTION_LENGTH),
                lastActionAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun markAdvanced(now: Long = System.currentTimeMillis()) {
        initialize(now)
        val current = requireNotNull(dao.primaryWorldState())
        dao.upsertWorldStateRow(
            current.copy(
                worldTime = now,
                timePhase = timePhase(now),
                lastAdvancedAt = now,
                updatedAt = now,
                worldVersion = current.worldVersion + 1
            )
        )
    }

    suspend fun recordPlanningAttempt(
        now: Long,
        retryAfter: Long
    ) {
        initialize(now)
        val current = requireNotNull(dao.primaryWorldState())
        dao.upsertWorldStateRow(
            current.copy(
                lastPlanAttemptAt = now,
                planRetryAfter = retryAfter,
                updatedAt = now
            )
        )
    }

    private fun defaultWorldState(now: Long) = WorldStateRowEntity(
        id = WORLD_ID,
        worldTime = now,
        weather = "",
        location = WorldLocationCatalog.HOME_ID,
        updatedAt = now,
        timePhase = timePhase(now),
        environment = "",
        lastAdvancedAt = now
    )

    private fun defaultUserState(now: Long) = WorldUserStateEntity(
        locationId = WorldLocationCatalog.HOME_ID,
        visibility = "VISIBLE",
        lastAction = "",
        lastActionAt = now,
        updatedAt = now
    )

    private fun timePhase(now: Long): String {
        val hour = java.time.Instant.ofEpochMilli(now)
            .atZone(java.time.ZoneId.systemDefault())
            .hour
        return when (hour) {
            in 5..10 -> "早晨"
            in 11..16 -> "白天"
            in 17..20 -> "傍晚"
            else -> "晚上"
        }
    }

    private companion object {
        const val WORLD_ID = "world"
        const val MAX_ACTION_LENGTH = 160
    }
}

data class WorldEngineSnapshot(
    val world: WorldStateRowEntity,
    val user: WorldUserStateEntity,
    val locations: List<WorldLocationEntity>,
    val characters: List<CharacterEntity>,
    val livingStates: List<CharacterLivingStateEntity>
)

object WorldLocationCatalog {
    const val HOME_ID = "home"
    const val OUTSIDE_ID = "outside"
    const val UNKNOWN_ID = "home.unknown"

    fun defaults(now: Long): List<WorldLocationEntity> = listOf(
        location(HOME_ID, "家中", null, now),
        location("home.living_room", "客厅", HOME_ID, now),
        location("home.kitchen", "厨房", HOME_ID, now),
        location("home.bedroom", "卧室", HOME_ID, now),
        location("home.bathroom", "卫生间", HOME_ID, now),
        location(OUTSIDE_ID, "外面", null, now),
        // 室外地点：让角色真的能「过自己的生活」——按人设去不同地方活动，而不是永远待在家里。
        location("outside.classroom", "教室", OUTSIDE_ID, now),
        location("outside.library", "图书馆", OUTSIDE_ID, now),
        location("outside.canteen", "食堂", OUTSIDE_ID, now),
        location("outside.cafe", "咖啡店", OUTSIDE_ID, now),
        location("outside.milk_tea", "奶茶店", OUTSIDE_ID, now),
        location("outside.supermarket", "超市", OUTSIDE_ID, now),
        location("outside.convenience", "便利店", OUTSIDE_ID, now),
        location("outside.mall", "商场", OUTSIDE_ID, now),
        location("outside.street", "街上", OUTSIDE_ID, now),
        location("outside.park", "公园", OUTSIDE_ID, now),
        location("outside.gym", "健身房", OUTSIDE_ID, now),
        location("outside.playground", "操场", OUTSIDE_ID, now),
        location("outside.office", "公司", OUTSIDE_ID, now),
        location("outside.restaurant", "餐厅", OUTSIDE_ID, now),
        location("outside.metro", "地铁", OUTSIDE_ID, now),
        location("outside.bookstore", "书店", OUTSIDE_ID, now),
        location("outside.friend_home", "朋友家", OUTSIDE_ID, now),
        location("outside.night_market", "夜市", OUTSIDE_ID, now),
        location("outside.internet_cafe", "网吧", OUTSIDE_ID, now),
        location("outside.fitting_room", "试衣间", OUTSIDE_ID, now),
        location("outside.hospital", "医院", OUTSIDE_ID, now),
        location("outside.hair_salon", "理发店", OUTSIDE_ID, now)
    )

    /**
     * 常见口语说法 → 地点 id。
     *
     * 模型不总按 displayName 写：学生会写「宿舍」「床上」「寝室」，角色会说「家里」「沙发上」「路上」。
     * 只按目录名匹配的话这些全落空，她明明在家也会被判成"在外面"。
     */
    private val ALIASES: Map<String, String> = mapOf(
        "宿舍" to "home.bedroom", "寝室" to "home.bedroom", "宿舍楼" to "home.bedroom",
        "床上" to "home.bedroom", "床" to "home.bedroom", "房间" to "home.bedroom",
        "书桌" to "home.bedroom", "家里" to HOME_ID, "在家" to HOME_ID, "屋内" to HOME_ID,
        "沙发上" to "home.living_room", "沙发" to "home.living_room",
        "灶台" to "home.kitchen", "餐桌" to "home.kitchen",
        "浴室" to "home.bathroom", "厕所" to "home.bathroom", "洗手间" to "home.bathroom",
        "教学楼" to "outside.classroom", "咖啡厅" to "outside.cafe", "奶茶" to "outside.milk_tea",
        "小卖部" to "outside.convenience", "步行街" to "outside.street", "路上" to "outside.street",
        "小区" to "outside.street", "办公室" to "outside.office", "工位" to "outside.office",
        "食堂门口" to "outside.canteen"
    )

    /**
     * 按显示名找地点 id（推演落库时用）：去掉首尾空白和开头的「在」后，
     * 依次尝试：别名精确匹配 → displayName 精确匹配 → 别名包含匹配 → displayName 包含匹配。
     * 都匹配不到返回 null —— 调用方要保持原地点，不要一律判成"在外面"。
     * 只用 [defaults] 那份静态表，不查库。
     */
    fun matchByName(name: String): String? {
        val needle = name.trim().removePrefix("在").trim()
        if (needle.isEmpty()) return null
        ALIASES[needle]?.let { return it }
        val table = defaults(0L)
        table.firstOrNull { it.displayName == needle }?.let { return it.id }
        ALIASES.entries.firstOrNull { needle.contains(it.key) }?.let { return it.value }
        return table.firstOrNull { it.displayName.contains(needle) || needle.contains(it.displayName) }?.id
    }

    private fun location(id: String, displayName: String, parentId: String?, now: Long) = WorldLocationEntity(
        id = id,
        displayName = displayName,
        parentId = parentId,
        createdAt = now,
        updatedAt = now
    )
}
