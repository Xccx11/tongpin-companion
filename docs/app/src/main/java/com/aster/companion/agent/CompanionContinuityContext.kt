package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.TimelineEventEntity
import javax.inject.Inject
import javax.inject.Singleton

/** A small, source-labelled bridge between chat, public feed, group history, and completed actions. */
data class CompanionContinuityContext(
    val groupContext: List<String> = emptyList(),
    val momentContext: String = "",
    val activityContext: List<String> = emptyList()
)

/** Labels long-term material so the model never mistakes character fiction for fresh user reality. */
object DirectConversationLongTermContext {
    fun render(
        savedMemories: List<String>,
        semanticMemories: List<String>,
        privateWorld: List<String>,
        sharedWorld: List<String>
    ): List<String> = buildList {
        savedMemories.forEach { add("Confirmed relationship memory: ${it.trim()}") }
        semanticMemories.forEach { add("Long-term diary or memory: ${it.trim()}") }
        privateWorld.forEach { add("Private character continuity, not user reality: ${it.trim()}") }
        sharedWorld.forEach { add("Explicitly shared relationship continuity: ${it.trim()}") }
    }
}

@Singleton
class CompanionContinuityContextAssembler @Inject constructor(
    private val dao: CompanionDao,
    private val familyGroupContext: FamilyGroupContextAssembler
) {
    suspend fun forPrivateCharacter(
        characterId: String,
        threadId: String,
        now: Long = System.currentTimeMillis(),
        memorySince: Long? = null
    ): CompanionContinuityContext {
        val boundary = memorySince ?: dao.thread(threadId)?.let(ConversationMemoryVisibilityPolicy::boundaryFor) ?: 0L
        val timeline = (dao.recentTimelineForCharacter(characterId, TIMELINE_LIMIT) +
            dao.recentTimelineForThread(threadId, TIMELINE_LIMIT))
            .distinctBy(TimelineEventEntity::id)
            .filter { ConversationMemoryVisibilityPolicy.visibleTimeline(it, boundary) }
        return CompanionContinuityContext(
            groupContext = familyGroupContext.forPrivateCharacter(characterId, now, boundary),
            momentContext = MomentConversationContext.forCharacter(
                characterId = characterId,
                posts = dao.recentMomentPosts(MOMENT_LIMIT),
                comments = dao.allMomentComments()
            ),
            activityContext = CompanionContinuityContextPolicy.forPrivate(characterId, threadId, timeline)
        )
    }

    suspend fun forGroup(
        threadId: String,
        memberIds: Set<String>,
        now: Long = System.currentTimeMillis(),
        characterId: String? = null,
        memorySince: Long? = null
    ): CompanionContinuityContext {
        val boundary = memorySince ?: dao.thread(threadId)?.let(ConversationMemoryVisibilityPolicy::boundaryFor) ?: 0L
        val timeline = (memberIds.flatMap { dao.recentTimelineForCharacter(it, TIMELINE_LIMIT) } +
            dao.recentTimelineForThread(threadId, TIMELINE_LIMIT))
            .filter { ConversationMemoryVisibilityPolicy.visibleTimeline(it, boundary) }
        return CompanionContinuityContext(
            groupContext = if (characterId == null) {
                familyGroupContext.forGroup(threadId, now, boundary)
            } else {
                familyGroupContext.forGroupCharacter(threadId, characterId, now, null, boundary)
            },
            momentContext = MomentConversationContext.forGroup(
                memberIds = memberIds,
                posts = dao.recentMomentPosts(MOMENT_LIMIT),
                comments = dao.allMomentComments()
            ),
            activityContext = CompanionContinuityContextPolicy.forGroup(threadId, memberIds, timeline.distinctBy(TimelineEventEntity::id))
        )
    }

    private companion object {
        const val TIMELINE_LIMIT = 24
        const val MOMENT_LIMIT = 16
    }
}

/** Prevents raw chat and sensing payloads from being reintroduced through the shared timeline. */
object CompanionContinuityContextPolicy {
    fun forPrivate(
        characterId: String,
        threadId: String,
        events: List<TimelineEventEntity>
    ): List<String> = render(
        events.filter { event ->
            when {
                event.source.startsWith(MOMENT_PREFIX) -> event.characterId == characterId
                isCompletedAction(event) -> event.threadId == threadId || event.characterId == characterId
                else -> false
            }
        }
    )

    fun forGroup(
        threadId: String,
        memberIds: Set<String>,
        events: List<TimelineEventEntity>
    ): List<String> = render(
        events.filter { event ->
            when {
                event.source.startsWith(MOMENT_PREFIX) -> event.characterId in memberIds
                isCompletedAction(event) -> event.threadId == threadId
                else -> false
            }
        }
    )

    private fun isCompletedAction(event: TimelineEventEntity): Boolean =
        event.source.startsWith(TOOL_PREFIX) || event.source.startsWith(IMAGE_PREFIX)

    private fun render(events: List<TimelineEventEntity>): List<String> = events
        .asSequence()
        .filter { it.summary.isNotBlank() }
        .sortedBy(TimelineEventEntity::occurredAt)
        .toList()
        .takeLast(MAX_ACTIVITY_ITEMS)
        .map { event -> "[${labelFor(event.source)}] ${event.summary.trim().take(MAX_SUMMARY_LENGTH)}" }

    private fun labelFor(source: String): String = when {
        source.startsWith(MOMENT_PREFIX) -> "Public feed activity"
        source.startsWith(IMAGE_PREFIX) -> "Image task result"
        else -> "Completed companion action"
    }

    private const val TOOL_PREFIX = "tool:"
    private const val IMAGE_PREFIX = "image:"
    private const val MOMENT_PREFIX = "moment:"
    private const val MAX_ACTIVITY_ITEMS = 8
    private const val MAX_SUMMARY_LENGTH = 360
}
