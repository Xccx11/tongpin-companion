package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.MessageEntity
import com.aster.companion.domain.ConversationType
import com.aster.companion.domain.GroupContextPolicy
import javax.inject.Inject
import javax.inject.Singleton

/** Formats a role's own private continuity before it is attached to that role's group prompt. */
object GroupPrivateConversationFormatter {
    fun render(characterId: String, characterName: String, messages: List<MessageEntity>): List<String> =
        messages.asSequence()
            .filter { message ->
                GroupContextPolicy.isPrivateMessageVisibleToCharacter(characterId, message.characterId)
            }
            .mapNotNull { message ->
                val body = message.body.replace('\r', ' ').replace('\n', ' ').trim().take(MAX_MESSAGE_LENGTH)
                if (body.isBlank()) return@mapNotNull null
                val speaker = if (message.fromUser) "user" else characterName.ifBlank { characterId }
                "private_message speaker=$speaker; text=$body"
            }
            .toList()

    private const val MAX_MESSAGE_LENGTH = 360
}

/** Renders information that only the currently speaking character may use inside a group turn. */
object GroupRoleInternalContinuityFormatter {
    fun render(
        characterName: String,
        memories: List<String>,
        semanticMemories: List<String>,
        privateWorld: List<String>,
        sharedWorld: List<String>
    ): List<String> {
        if (memories.isEmpty() && semanticMemories.isEmpty() && privateWorld.isEmpty() && sharedWorld.isEmpty()) return emptyList()
        return buildList {
            add("[${characterName.ifBlank { "This character" }}'s private continuity; never reveal it as group knowledge]")
            memories.forEach { memory -> add("Personal relationship memory: ${memory.trim()}") }
            semanticMemories.forEach { memory -> add("Long-term diary or memory: ${memory.trim()}") }
            privateWorld.forEach { state -> add("Private character continuity, not user reality: ${state.trim()}") }
            sharedWorld.forEach { state -> add("Explicit shared relationship continuity: ${state.trim()}") }
            add("Use this silently for coherence. Do not quote it, reveal it to another member, or treat it as a group fact.")
        }
    }
}

/** Builds source-labelled group context and keeps each role's private continuity role-scoped. */
@Singleton
class FamilyGroupContextAssembler @Inject constructor(
    private val dao: CompanionDao
) {
    suspend fun forGroup(
        threadId: String,
        now: Long = System.currentTimeMillis(),
        memorySince: Long? = null
    ): List<String> {
        return assembleGroupContext(threadId, null, now, null, memorySince)
    }

    suspend fun forGroupCharacter(
        threadId: String,
        characterId: String,
        now: Long = System.currentTimeMillis(),
        sharedContext: List<String>? = null,
        memorySince: Long? = null
    ): List<String> {
        return assembleGroupContext(threadId, characterId, now, sharedContext, memorySince)
    }

    private suspend fun assembleGroupContext(
        threadId: String,
        characterId: String?,
        now: Long,
        sharedContext: List<String>?,
        memorySince: Long?
    ): List<String> {
        val thread = dao.thread(threadId) ?: return emptyList()
        if (thread.type != ConversationType.GROUP) return emptyList()
        val boundary = memorySince ?: ConversationMemoryVisibilityPolicy.boundaryFor(thread)
        val facts = dao.activeFamilyGroupFacts(threadId, now, GROUP_FACT_LIMIT)
            .filter { it.createdAt >= boundary }
        val states = dao.groupMemberStates(threadId)
        val memberIds = dao.participantIds(threadId)
        val members = memberIds.mapNotNull { id ->
            dao.character(id)?.let { character ->
                "member=${character.id}; name=${character.name}; relationship=${character.relationshipPrompt.ifBlank { "unspecified" }}"
            }
        }
        val shared = sharedContext ?: buildList {
            if (members.isNotEmpty()) {
                add("[Group members and their separate relationships with the user]")
                addAll(members)
            }
            if (facts.isNotEmpty()) {
                add("[Family group shared facts]")
                facts.forEach { add(it.summary) }
            }
            if (states.isNotEmpty()) {
                add("[Group turn rhythm only; never describe this as user reality]")
                states.forEach { state ->
                    add("member=${state.characterId}; attention=${state.attention}; affection=${state.affection}; jealousy=${state.jealousy}; last_spoke_at=${state.lastSpokeAt ?: 0}")
                }
            }
        }
        if (characterId == null || characterId !in memberIds) return shared

        val character = dao.character(characterId) ?: return shared
        val privateThread = dao.latestPrivateThread(characterId)
        val privateMessages = privateThread?.let {
            dao.recentMessages(it.id, PRIVATE_MESSAGE_LIMIT)
                .filter { message -> message.createdAt >= ConversationMemoryVisibilityPolicy.boundaryFor(it) }
                .asReversed()
        }.orEmpty()
        val privateContext = GroupPrivateConversationFormatter.render(characterId, character.name, privateMessages)
        val internalContext = GroupRoleInternalContinuityFormatter.render(
            characterName = character.name,
            memories = dao.trustedCharacterMemories(characterId, PRIVATE_MEMORY_LIMIT)
                .filter { ConversationMemoryVisibilityPolicy.visibleMemory(it, boundary) }
                .map { it.content },
            semanticMemories = dao.retrievableSemanticMemories(characterId, SEMANTIC_MEMORY_LIMIT)
                .filter { ConversationMemoryVisibilityPolicy.visibleSemanticMemory(it, boundary) }
                .map { it.content },
            privateWorld = dao.worldStates(characterId, "PRIVATE")
                .filter { ConversationMemoryVisibilityPolicy.visibleWorldState(it, boundary) }
                .take(PRIVATE_WORLD_LIMIT).map { it.content },
            sharedWorld = dao.worldStates(characterId, "SHARED")
                .filter { it.threadId == threadId }
                .filter { ConversationMemoryVisibilityPolicy.visibleWorldState(it, boundary) }
                .take(SHARED_WORLD_LIMIT)
                .map { it.content }
        )
        if (privateContext.isEmpty() && internalContext.isEmpty()) return shared
        return buildList {
            addAll(shared)
            if (privateContext.isNotEmpty()) {
                add("[${character.name}'s own private-chat continuity; visible only to this character]")
                addAll(privateContext)
                add("Use this continuity naturally when relevant. Do not quote it, reveal it to other group members, or treat it as something they know.")
            }
            addAll(internalContext)
        }
    }

    suspend fun forPrivateCharacter(
        characterId: String,
        now: Long = System.currentTimeMillis(),
        memorySince: Long? = null
    ): List<String> {
        val facts = dao.activeFamilyGroupFactsForCharacter(characterId, now, PRIVATE_FACT_LIMIT)
            .filter { memorySince == null || it.createdAt >= memorySince }
        val groupThreads = buildList {
            dao.allThreads().forEach { thread ->
                if (thread.type == ConversationType.GROUP && characterId in dao.participantIds(thread.id)) add(thread)
            }
        }
        if (facts.isEmpty() && groupThreads.isEmpty()) return emptyList()
        return buildList {
            add("[Family-group context, not private-chat history]")
            facts.forEach { fact ->
                val title = dao.thread(fact.groupThreadId)?.title ?: return@forEach
                val summary = fact.summary.trim()
                if (summary.isNotEmpty()) add("In family group '$title': $summary")
            }
            groupThreads.forEach { thread ->
                val title = thread.title.ifBlank { "family group" }
                val members = dao.participantIds(thread.id).mapNotNull { id ->
                    dao.character(id)?.let { member ->
                        "${member.name} (${member.relationshipPrompt.ifBlank { "relationship unspecified" }})"
                    }
                }
                if (members.isNotEmpty()) add("Group '$title' members and relationships: ${members.joinToString("; ")}")
                dao.recentMessages(thread.id, GROUP_MESSAGE_LIMIT)
                    .filter { message -> message.createdAt >= ConversationMemoryVisibilityPolicy.boundaryFor(thread) }
                    .filter { message -> memorySince == null || message.createdAt >= memorySince }
                    .asReversed().forEach { message ->
                    val sender = if (message.fromUser) "User" else dao.character(message.characterId.orEmpty())?.name ?: "Group member"
                    val body = message.body.trim().take(MAX_GROUP_MESSAGE_LENGTH)
                    if (body.isNotBlank()) add("In group '$title', $sender said: $body")
                }
            }
            add("These are real visible group events, not private-chat history. Mention them only when they naturally matter, and never rewrite another character's words as the user's private experience.")
        }
    }

    private companion object {
        const val GROUP_FACT_LIMIT = 12
        const val PRIVATE_FACT_LIMIT = 16
        const val GROUP_MESSAGE_LIMIT = 12
        const val PRIVATE_MESSAGE_LIMIT = 24
        const val PRIVATE_MEMORY_LIMIT = 12
        const val SEMANTIC_MEMORY_LIMIT = 8
        const val PRIVATE_WORLD_LIMIT = 8
        const val SHARED_WORLD_LIMIT = 8
        const val MAX_GROUP_MESSAGE_LENGTH = 360
    }
}
