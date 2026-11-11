package com.aster.companion.agent.world

import com.aster.companion.data.WorldTimelineEventEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal object WorldOpportunityPersistence {
    private val json = Json { ignoreUnknownKeys = true }

    fun toEntity(opportunity: WorldOpportunity, now: Long): WorldTimelineEventEntity =
        WorldTimelineEventEntity(
            id = opportunity.id,
            timelineId = opportunity.timelineId,
            scheduledAt = opportunity.scheduledAt,
            latestStartAt = opportunity.latestStartAt,
            durationMinutes = opportunity.durationMinutes,
            type = opportunity.type,
            initiatorCharacterId = opportunity.initiatorCharacterId,
            requiredCharacterIdsJson = encodeIds(opportunity.requiredCharacterIds),
            optionalCharacterIdsJson = encodeIds(opportunity.optionalCharacterIds),
            userParticipation = opportunity.userParticipation.name,
            locationId = opportunity.locationId,
            description = opportunity.description,
            prerequisiteEventIdsJson = encodeIds(opportunity.prerequisiteEventIds),
            prerequisiteJson = opportunity.prerequisiteJson,
            expectedEffectsJson = opportunity.expectedEffectsJson,
            flexibility = opportunity.flexibility.name,
            interruptStrategy = opportunity.interruptStrategy.name,
            status = opportunity.status.name,
            decisionJson = opportunity.decisionJson,
            createdAt = now,
            updatedAt = now
        )

    fun fromEntity(entity: WorldTimelineEventEntity): WorldOpportunity = WorldOpportunity(
        id = entity.id,
        timelineId = entity.timelineId,
        scheduledAt = entity.scheduledAt,
        latestStartAt = entity.latestStartAt,
        durationMinutes = entity.durationMinutes,
        type = entity.type,
        initiatorCharacterId = entity.initiatorCharacterId,
        requiredCharacterIds = decodeIds(entity.requiredCharacterIdsJson),
        optionalCharacterIds = decodeIds(entity.optionalCharacterIdsJson),
        userParticipation = enumOrDefault(entity.userParticipation, UserParticipation.NONE),
        locationId = entity.locationId,
        description = entity.description,
        prerequisiteEventIds = decodeIds(entity.prerequisiteEventIdsJson),
        prerequisiteJson = entity.prerequisiteJson,
        expectedEffectsJson = entity.expectedEffectsJson,
        flexibility = enumOrDefault(entity.flexibility, WorldEventFlexibility.FLEXIBLE),
        interruptStrategy = enumOrDefault(entity.interruptStrategy, WorldEventInterruptStrategy.REEVALUATE),
        status = enumOrDefault(entity.status, WorldEventStatus.PLANNED),
        decisionJson = entity.decisionJson
    )

    fun encodeIds(ids: List<String>): String = JsonArray(
        ids.distinct().map(::JsonPrimitive)
    ).toString()

    fun decodeIds(value: String): List<String> = runCatching {
        json.parseToJsonElement(value).jsonArray
            .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
    }.getOrDefault(emptyList())

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == raw.uppercase() } ?: default
}
