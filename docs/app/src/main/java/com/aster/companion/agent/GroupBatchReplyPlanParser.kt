package com.aster.companion.agent

import com.aster.companion.domain.GroupAtmosphere
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class GroupBatchReplyAction(
    val capabilityId: String,
    val arguments: Map<String, String>
)

data class GroupBatchReplyTurn(
    val characterId: String,
    val message: String,
    val delivery: GroupReplyDelivery = GroupReplyDelivery.GROUP,
    val actions: List<GroupBatchReplyAction>
)

data class GroupBatchReplyPlan(val turns: List<GroupBatchReplyTurn>)

/**
 * A whole visible group exchange is planned by one model response. This parser only accepts
 * current members, keeps the returned speaking order, and applies the group activity ceiling.
 */
object GroupBatchReplyPlanParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        raw: String,
        memberIds: Set<String>,
        atmosphere: GroupAtmosphere,
        memberNameToId: Map<String, String> = emptyMap(),
        fallbackCharacterId: String? = null,
        fallbackCharacterIds: List<String> = emptyList()
    ): GroupBatchReplyPlan = runCatching {
        val root = json.parseToJsonElement(raw.jsonCandidate())
        val maxTurns = atmosphere.maxFollowUps + 1
        val items = replyItems(root)
        val orderedFallbacks = (listOfNotNull(fallbackCharacterId) + fallbackCharacterIds + memberIds.toList())
            .filter { it in memberIds && !isGenericSpeaker(it) }
            .distinct()
        val turns = items
            .mapIndexedNotNull { index, element ->
                val item = asReplyObject(element) ?: return@mapIndexedNotNull null
                val speakerValues = readValues(
                    item,
                    "character_id", "characterId", "speaker_id", "speakerId", "responder_id", "responderId",
                    "member_id", "memberId", "author_id", "authorId", "sender_id", "senderId",
                    "character_name", "characterName", "speaker_name", "speakerName",
                    "responder_name", "responderName", "member_name", "memberName",
                    "speaker", "character", "member", "author", "sender", "name"
                )
                val characterId = resolveSpeaker(
                    candidates = speakerValues,
                    memberIds = memberIds,
                    memberNameToId = memberNameToId
                )
                    ?: fallbackFor(
                        index = index,
                        speakerValues = speakerValues,
                        orderedFallbacks = orderedFallbacks,
                        item = item
                    )
                    ?: return@mapIndexedNotNull null
                val message = readString(
                    item,
                    "message", "content", "text", "reply", "response", "body",
                    "reply_text", "replyText", "answer", "utterance", "dialogue", "say"
                )?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.take(MAX_MESSAGE_LENGTH)
                    ?: return@mapIndexedNotNull null
                val requestedDelivery = readString(item, "delivery", "destination", "target")
                    ?.uppercase()
                    ?.let { value -> GroupReplyDelivery.entries.firstOrNull { it.name == value } }
                    ?: GroupReplyDelivery.GROUP
                val actions = parseActions(item["actions"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty())
                // A visible "I'll DM you" plus a private-message action is one private delivery.
                val privateAction = actions.firstOrNull { it.capabilityId == "companion.send_private_message" }
                val delivery = if (privateAction != null) GroupReplyDelivery.PRIVATE else requestedDelivery
                val deliveredMessage = privateAction?.arguments?.get("message")?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.take(MAX_MESSAGE_LENGTH)
                    ?: message
                GroupBatchReplyTurn(
                    characterId,
                    deliveredMessage,
                    delivery,
                    actions
                )
            }
            .fold(mutableListOf<GroupBatchReplyTurn>()) { accepted, candidate ->
                if (
                    accepted.size < maxTurns &&
                    accepted.lastOrNull()?.characterId != candidate.characterId &&
                    accepted.lastOrNull()?.delivery != GroupReplyDelivery.PRIVATE
                ) {
                    accepted += candidate
                }
                accepted
            }
        GroupBatchReplyPlan(turns)
    }.getOrDefault(GroupBatchReplyPlan(emptyList()))

    /** Keeps a plain-text model response usable when a provider ignored the JSON-only contract. */
    fun plainTextFallback(raw: String, characterId: String, atmosphere: GroupAtmosphere): GroupBatchReplyPlan {
        val text = raw.withoutThinking()
            .trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (text.isBlank() || text.any { it == '{' || it == '}' || it == '[' || it == ']' }) {
            return GroupBatchReplyPlan(emptyList())
        }
        return GroupBatchReplyPlan(
            listOf(
                GroupBatchReplyTurn(
                    characterId = characterId,
                    message = text.take(MAX_MESSAGE_LENGTH),
                    actions = emptyList()
                )
            ).take((atmosphere.maxFollowUps + 1).coerceAtLeast(1))
        )
    }

    private fun readString(item: JsonObject, vararg names: String): String? = names.asSequence()
        .mapNotNull { name -> item[name]?.let(::stringValue)?.trim() }
        .firstOrNull(String::isNotBlank)

    private fun readValues(item: JsonObject, vararg names: String): List<String> = names
        .asSequence()
        .flatMap { name -> item[name]?.let(::stringValues).orEmpty().asSequence() }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .toList()

    private fun replyItems(element: JsonElement): List<JsonElement> = when (element) {
        is JsonArray -> element.toList()
        is JsonPrimitive -> decodeNested(element)?.let(::replyItems).orEmpty()
        is JsonObject -> {
            // Providers sometimes add a top-level summary next to the real turns. Prefer
            // the nested exchange before interpreting that summary as a single reply.
            val nestedReplies = REPLY_CONTAINER_KEYS
                .asSequence()
                .mapNotNull { key -> element[key]?.let(::decodeNested) }
                .map(::replyItems)
                .firstOrNull { it.isNotEmpty() }
            nestedReplies ?: listOf(element)
        }
        else -> emptyList()
    }

    private fun asReplyObject(element: JsonElement): JsonObject? = when (element) {
        is JsonObject -> element
        is JsonPrimitive -> {
            val decoded = decodeNested(element)
            if (decoded != null) {
                asReplyObject(decoded)
            } else {
                element.contentOrNull
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let { text -> buildJsonObject { put("message", text) } }
            }
        }
        else -> null
    }

    private fun fallbackFor(
        index: Int,
        speakerValues: List<String>,
        orderedFallbacks: List<String>,
        item: JsonObject
    ): String? {
        if (orderedFallbacks.isEmpty()) return null
        // An explicit unknown id is a model error and must not be silently assigned to a
        // different person. Missing speakers and generic placeholders such as "group" are
        // recoverable because the planner still returned a grounded message.
        val hasSpeakerField = item.keys.any { it in SPEAKER_FIELDS }
        val canRecover = speakerValues.isEmpty() || speakerValues.all(::isGenericSpeaker) || !hasSpeakerField
        if (!canRecover) return null
        return orderedFallbacks[index % orderedFallbacks.size]
    }

    private fun stringValue(element: JsonElement, depth: Int = 0): String? {
        if (depth > 3) return null
        return when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> NESTED_VALUE_KEYS.asSequence()
                .mapNotNull { key -> element[key]?.let { stringValue(it, depth + 1) } }
                .firstOrNull(String::isNotBlank)
            else -> null
        }
    }

    private fun stringValues(element: JsonElement, depth: Int = 0): List<String> {
        if (depth > 3) return emptyList()
        return when (element) {
            is JsonPrimitive -> listOfNotNull(element.contentOrNull)
            is JsonObject -> NESTED_VALUE_KEYS
                .asSequence()
                .flatMap { key -> element[key]?.let { stringValues(it, depth + 1) }.orEmpty().asSequence() }
                .toList()
            else -> emptyList()
        }
    }

    private fun decodeNested(element: JsonElement): JsonElement? = when (element) {
        is JsonPrimitive -> element.contentOrNull?.let { content ->
            runCatching { json.parseToJsonElement(content) }.getOrNull()
        }
        else -> element
    }

    private fun resolveSpeaker(
        candidates: List<String>,
        memberIds: Set<String>,
        memberNameToId: Map<String, String>
    ): String? {
        return candidates
            .filterNot(::isGenericSpeaker)
            .mapNotNull { candidate ->
                val normalized = candidate.trim().removePrefix("@").removePrefix("#").trim()
                normalized.takeIf(memberIds::contains)
                    ?: memberNameToId[normalized]
                    ?: memberNameToId.entries.firstOrNull { (key, _) -> key.equals(normalized, ignoreCase = true) }?.value
            }
            .firstOrNull()
            ?.takeIf(memberIds::contains)
    }

    private fun parseActions(raw: List<kotlinx.serialization.json.JsonElement>): List<GroupBatchReplyAction> = raw.mapNotNull { element ->
        val item = asReplyObject(element) ?: return@mapNotNull null
        val capabilityId = readString(item, "capability_id", "capabilityId", "tool", "action", "name")
            ?: return@mapNotNull null
        val arguments = item["arguments"]
            ?.let(::decodeNested)
            ?.let { runCatching { it.jsonObject }.getOrNull() }
            ?.mapNotNull { (key, value) -> stringValue(value)?.let { key to it } }
            ?.toMap()
            .orEmpty()
        GroupBatchReplyAction(capabilityId, arguments)
    }.distinctBy { action -> "${action.capabilityId}:${action.arguments}" }

    private fun String.jsonCandidate(): String {
        val trimmed = withoutThinking().trim()
        val candidate = trimmed.jsonCandidates()
            .asReversed()
            .firstOrNull { candidate -> runCatching { json.parseToJsonElement(candidate) }.isSuccess }
        if (candidate != null) return candidate

        // Some OpenAI-compatible gateways serialize the model's JSON content one extra time.
        val decoded = runCatching {
            json.parseToJsonElement(trimmed).jsonPrimitive.contentOrNull
        }.getOrNull()
        return if (!decoded.isNullOrBlank() && decoded != trimmed) decoded.jsonCandidate() else trimmed
    }

    /** Extracts complete top-level JSON values without confusing nested objects or quoted braces. */
    private fun String.jsonCandidates(): List<String> {
        val candidates = mutableListOf<String>()
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false

        forEachIndexed { index, character ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                return@forEachIndexed
            }
            if (character == '"') {
                inString = true
                return@forEachIndexed
            }
            if (depth == 0 && (character == '{' || character == '[')) {
                start = index
                depth = 1
                return@forEachIndexed
            }
            if (depth > 0 && (character == '{' || character == '[')) {
                depth += 1
                return@forEachIndexed
            }
            if (depth > 0 && (character == '}' || character == ']')) {
                depth -= 1
                if (depth == 0 && start >= 0) {
                    candidates += substring(start, index + 1)
                    start = -1
                }
            }
        }
        return candidates
    }

    private fun String.withoutThinking(): String = replace(THINKING_BLOCK, "").trim()

    private fun isGenericSpeaker(value: String): Boolean = value.trim()
        .trim('"', '\'', '`')
        .lowercase()
        .let { it in GENERIC_SPEAKER_VALUES }

    private const val MAX_MESSAGE_LENGTH = 1_200
    private val REPLY_CONTAINER_KEYS = listOf(
        "turns", "messages", "replies", "responses", "exchange", "items",
        "plan", "reply_plan", "batch_reply_plan", "batch", "group", "group_reply", "group_response",
        "conversation", "chat", "result", "data", "output", "payload"
    )
    private val NESTED_VALUE_KEYS = listOf(
        "id", "character_id", "characterId", "member_id", "memberId", "name", "value",
        "text", "content", "message", "reply", "response", "answer"
    )
    private val GENERIC_SPEAKER_VALUES = setOf(
        "group", "groups", "群", "群聊", "群组", "chat", "conversation", "assistant", "system",
        "user", "用户", "character", "角色", "speaker", "member", "unknown", "unknown-character",
        "null", "none", "n/a"
    )
    private val THINKING_BLOCK = Regex("<think>.*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SPEAKER_FIELDS = setOf(
        "character_id", "characterId", "speaker_id", "speakerId", "responder_id", "responderId",
        "member_id", "memberId", "character_name", "characterName", "speaker_name", "speakerName",
        "responder_name", "responderName", "speaker", "character", "member", "author", "sender", "name"
    )
    private val MESSAGE_FIELDS = setOf(
        "message", "content", "text", "reply", "response", "body", "reply_text", "replyText",
        "answer", "utterance", "dialogue", "say"
    )
}
