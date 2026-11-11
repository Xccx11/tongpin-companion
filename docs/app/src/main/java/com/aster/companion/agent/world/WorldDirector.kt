package com.aster.companion.agent.world

import com.aster.companion.agent.AgentTurnCoordinator
import com.aster.companion.agent.OpenAiCompatibleClient
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces possible future events. It never changes a character state and it
 * never makes a behavioural decision for a character.
 */
@Singleton
class WorldDirector @Inject constructor(
    private val dao: CompanionDao,
    private val client: OpenAiCompatibleClient,
    private val secretCipher: CompanionSecretCipher
) {
    suspend fun generateOpportunities(
        snapshot: WorldEngineSnapshot,
        timelineId: String = "timeline:${UUID.randomUUID()}",
        windowStartAt: Long = System.currentTimeMillis()
    ): List<WorldOpportunity> {
        val profile = plannerProfile() ?: return emptyList()
        val knownCharacterIds = snapshot.characters.map { it.id }.toSet()
        val knownLocationIds = snapshot.locations.map { it.id }.toSet()
        if (knownCharacterIds.isEmpty() || knownLocationIds.isEmpty()) return emptyList()

        val raw = client.chat(
            profile = profile,
            apiKey = secretCipher.decrypt(requireNotNull(profile.encryptedApiKey)),
            systemPrompt = SYSTEM_PROMPT,
            userPrompt = buildPrompt(snapshot, windowStartAt),
            longRunning = true
        ).getOrNull() ?: return emptyList()

        return WorldOpportunityCodec.parseDirectorResponse(
            raw = raw,
            timelineId = timelineId,
            windowStartAt = windowStartAt,
            knownCharacterIds = knownCharacterIds,
            knownLocationIds = knownLocationIds
        )
    }

    /**
     * Compatibility helper for callers that still render the old timeline
     * snapshot. New runtime code must use [generateOpportunities].
     */
    suspend fun generateFuturePlan(snapshot: WorldSnapshot): FutureTimeline? {
        val now = System.currentTimeMillis()
        val locations = dao.worldLocations().ifEmpty { WorldLocationCatalog.defaults(now) }
        val characters = dao.allCharacters()
        val livingStates = dao.allLivingStates()
        val opportunities = generateOpportunities(
            snapshot = WorldEngineSnapshot(
                world = com.aster.companion.data.WorldStateRowEntity(
                    id = "world",
                    worldTime = now,
                    weather = snapshot.weather,
                    location = WorldLocationCatalog.HOME_ID,
                    updatedAt = now
                ),
                user = com.aster.companion.data.WorldUserStateEntity(
                    locationId = WorldLocationCatalog.HOME_ID,
                    visibility = "VISIBLE",
                    lastAction = "",
                    lastActionAt = now,
                    updatedAt = now
                ),
                locations = locations,
                characters = characters,
                livingStates = livingStates
            ),
            windowStartAt = now
        )
        if (opportunities.isEmpty()) return null
        return FutureTimeline(
            id = opportunities.first().timelineId,
            startTime = now,
            endTime = now + WorldSchedulerPolicy.TARGET_HORIZON_MS,
            events = opportunities.map { it.toLegacyPlannedEvent() }
        )
    }

    /** Builds an old presentation snapshot without deriving locations from prose. */
    suspend fun buildSnapshot(): WorldSnapshot {
        val now = System.currentTimeMillis()
        val locations = dao.worldLocations().associateBy { it.id }
        val characters = dao.allCharacters().map { character ->
            val living = dao.livingState(character.id)
            CharacterSnapshot(
                name = character.name,
                location = locations[living?.locationId]?.displayName ?: "unknown",
                state = living?.currentActivity?.ifBlank { "idle" } ?: "idle"
            )
        }
        return WorldSnapshot(
            time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(TIME_FORMATTER),
            weather = dao.primaryWorldState()?.weather.orEmpty(),
            location = locations[dao.worldUserState()?.locationId]?.displayName ?: "home",
            characters = characters,
            recentEvents = dao.recentWorldEvents(5).map { it.result }
        )
    }

    private suspend fun plannerProfile() =
        dao.modelProfile(AgentTurnCoordinator.PLANNING_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
            ?: dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
                ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }

    private fun buildPrompt(snapshot: WorldEngineSnapshot, windowStartAt: Long): String = buildString {
        appendLine("World time: ${Instant.ofEpochMilli(windowStartAt).atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)}")
        appendLine("Time phase: ${snapshot.world.timePhase}")
        appendLine("Weather: ${snapshot.world.weather}")
        appendLine("User state: location=${snapshot.user.locationId}, visibility=${snapshot.user.visibility}")
        appendLine("Registered locations: ${snapshot.locations.joinToString { "${it.id} (${it.displayName})" }}")
        appendLine("Characters. IDs are authoritative; never use display names as IDs.")
        val livingByCharacter = snapshot.livingStates.associateBy { it.characterId }
        snapshot.characters.forEach { character ->
            val living = livingByCharacter[character.id]
            appendLine("- id=${character.id}; name=${character.name}; location=${living?.locationId ?: WorldLocationCatalog.UNKNOWN_ID}; public_activity=${living?.currentActivity.orEmpty()}")
        }
    }

    private companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val SYSTEM_PROMPT = """
            You are a world opportunity planner. Return JSON only.
            You propose plausible opportunities for the next 60 minutes. An opportunity
            is not a command: it must never force a character, promise a response, or
            make a decision on behalf of the user. Character cognition decides later.

            Contract:
            {"events":[{
              "id":"optional-stable-id",
              "time_offset_minutes":0,
              "latest_start_offset_minutes":15,
              "duration_minutes":20,
              "type":"activity",
              "initiator_character_id":"character-id-or-null",
              "required_character_ids":["character-id"],
              "optional_character_ids":[],
              "user_participation":"NONE|OPTIONAL|REQUIRED",
              "location_id":"registered-location-id",
              "description":"brief public description",
              "prerequisite_event_ids":[],
              "prerequisites":{},
              "expected_effects":{},
              "flexibility":"FLEXIBLE|FIXED",
              "interrupt_strategy":"KEEP|RESCHEDULE|REEVALUATE|CANCEL"
            }]}

            Rules: use only supplied character IDs and registered location IDs. Do not
            put the user in character ID arrays. Do not invent names, locations, or
            facts. Keep character life independent of whether the user responds.
        """.trimIndent()
    }
}

internal fun WorldOpportunity.toLegacyPlannedEvent(): PlannedEvent = PlannedEvent(
    id = id,
    time = Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
    durationMinutes = durationMinutes,
    type = type,
    participants = requiredCharacterIds,
    description = description,
    status = status.name.lowercase()
)
