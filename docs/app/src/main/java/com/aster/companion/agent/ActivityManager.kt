package com.aster.companion.agent

import com.aster.companion.data.ActivityEntity
import com.aster.companion.data.CharacterLivingStateEntity
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.MemoryEntity
import com.aster.companion.domain.ActivityEffects
import com.aster.companion.domain.MemoryKind
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.1 共同活动生命周期：START(创建) → RUNNING(进行中) → 到期 FINISH(结束) → EFFECT(关系/状态影响) → MEMORY(记忆)。
 * 数值全部代码计算，AI 不参与。
 */
@Singleton
class ActivityManager @Inject constructor(
    private val dao: CompanionDao
) {
    /** Starts a V3 activity only after the agreement coordinator confirms it. */
    suspend fun startWorldActivity(
        eventId: String,
        type: String,
        characterIds: List<String>,
        userParticipated: Boolean,
        locationId: String,
        durationMinutes: Int,
        description: String,
        now: Long
    ): ActivityEntity? {
        val activity = WorldActivityFactory.create(
            eventId = eventId,
            type = type,
            characterIds = characterIds,
            userParticipated = userParticipated,
            locationId = locationId,
            durationMinutes = durationMinutes,
            now = now
        ) ?: return null
        dao.upsertActivity(activity)
        characterIds.distinct().forEach { characterId ->
            val existing = dao.livingState(characterId)
            val base = existing ?: CharacterLivingStateEntity(
                characterId = characterId,
                mood = "calm",
                currentActivity = description,
                activityStartedAt = now,
                sleepUntil = null,
                scheduleJson = "",
                updatedAt = now,
                locationId = locationId
            )
            dao.upsertLivingState(
                base.copy(
                    currentActivity = description,
                    activityStartedAt = now,
                    updatedAt = now,
                    locationId = locationId
                )
            )
        }
        return activity
    }

    /** 检查进行中的活动：到期则结束并应用效果。 */
    suspend fun updateRunningActivities(now: Long): List<ActivityEntity> {
        val finished = mutableListOf<ActivityEntity>()
        dao.runningActivities().forEach { activity ->
            val durationMs = activity.durationMinutes * 60_000L
            if (now - activity.startedAt >= durationMs) {
                runCatching { finish(activity, now) }.onSuccess { finished += activity }
            }
        }
        return finished
    }

    private suspend fun finish(activity: ActivityEntity, now: Long) {
        val participants = ActivityParticipantCodec.decode(activity.participantsJson).characterIds
        // 心情是否积极：任一方当前 mood 含 开心/满足/放松 等
        val moodPositive = participants.any { id ->
            dao.livingState(id)?.mood?.let { mood ->
                mood.contains("开心") || mood.contains("满足") || mood.contains("放松") || mood.contains("暖")
            } == true
        }
        val effect = ActivityEffects.calculate(activity.type, moodPositive)
        // EFFECT：关系 + 状态（数值代码）
        if (participants.size >= 2) {
            participants.forEachIndexed { index, a ->
                participants.drop(index + 1).forEach { b ->
                    updateRelationship(a, b, effect.affectionDelta, effect.trustDelta, effect.understandingDelta, effect.conflictDelta, now)
                    updateRelationship(b, a, effect.affectionDelta / 2, effect.trustDelta / 2, effect.understandingDelta / 2, effect.conflictDelta, now)
                }
            }
            participants.forEach { id -> applyLifeStateDelta(id, effect, now) }
        }
        // MEMORY：活动完成记忆
        val names = participants.mapNotNull { dao.character(it)?.name }
        if (names.size >= 2) {
            dao.upsertMemory(
                MemoryEntity(
                    id = "memory:activity:${UUID.randomUUID()}",
                    characterId = participants[1],
                    threadId = null,
                    kind = MemoryKind.EVENT,
                    content = "${names[0]}和${names[1]}一起${actionLabel(activity.type)}了${activity.durationMinutes}分钟（在${activity.location}），聊得很自然。",
                    blockedFromMention = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        // 落库完成
        val effectJson = """{"affection":${effect.affectionDelta},"stress":${effect.stressDelta},"energy":${effect.energyDelta}}"""
        dao.finishActivity(activity.id, "finished", now, effectJson)
    }

    private suspend fun updateRelationship(
        aId: String, bId: String,
        affection: Int, trust: Int, understanding: Int, conflict: Int, now: Long
    ) {
        val base = dao.relationship(aId, bId) ?: com.aster.companion.data.RelationshipEntity(aId, bId, updatedAt = now)
        dao.upsertRelationship(
            base.copy(
                affection = (base.affection + affection).coerceIn(0, 100),
                trust = (base.trust + trust).coerceIn(0, 100),
                understanding = (base.understanding + understanding).coerceIn(0, 100),
                conflict = (base.conflict + conflict).coerceIn(0, 100),
                updatedAt = now
            )
        )
    }

    private suspend fun applyLifeStateDelta(id: String, effect: com.aster.companion.domain.ActivityEffect, now: Long) {
        val base = dao.lifeState(id) ?: com.aster.companion.data.CharacterLifeStateEntity(characterId = id, updatedAt = now)
        dao.upsertCharacterLifeState(
            base.copy(
                stress = (base.stress + effect.stressDelta).coerceIn(0, 100),
                energy = (base.energy + effect.energyDelta).coerceIn(5, 100),
                loneliness = (base.loneliness + effect.lonelinessDelta).coerceIn(0, 100),
                socialNeed = (base.socialNeed - 15).coerceIn(0, 100),
                updatedAt = now
            )
        )
    }

    private fun actionLabel(action: String): String = when (action) {
        "watch_tv" -> "看电视"
        "game" -> "打游戏"
        "stroll" -> "散步"
        "chat" -> "聊天"
        "vent" -> "说心里话"
        "comfort" -> "相互安慰"
        "meal_remind" -> "一起吃饭"
        "help" -> "互相帮忙"
        "care" -> "互相关心"
        else -> "待在一起"
    }
}
