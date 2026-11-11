package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.SemanticMemoryEntity
import com.aster.companion.domain.SemanticMemoryRanker
import com.aster.companion.domain.SemanticVectorCandidate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemanticMemoryRetriever @Inject constructor(
    private val dao: CompanionDao,
    private val cipher: CompanionSecretCipher,
    private val client: OpenAiCompatibleClient
) {
    suspend fun retrieve(characterId: String, query: String, limit: Int, memorySince: Long = 0L): List<SemanticMemoryEntity> {
        val candidates = if (memorySince > 0) {
            dao.allRetrievableSemanticMemoriesSince(characterId, memorySince)
        } else {
            dao.allRetrievableSemanticMemories(characterId)
        }
        val profile = dao.modelProfile(EMBEDDING_MODEL)
        if (profile?.enabled != true || profile.encryptedApiKey.isNullOrBlank()) return SemanticMemoryFallbackPolicy.rank(candidates, limit)
        val queryVector = client.embed(profile, cipher.decrypt(profile.encryptedApiKey), query).getOrNull()
            ?: return SemanticMemoryFallbackPolicy.rank(candidates, limit)
        val byId = candidates.associateBy { it.id }
        val ranked = SemanticMemoryRanker.rank(queryVector, candidates.map { memory ->
            SemanticVectorCandidate(memory.id, memory.embedding?.let(::decode), memory.retrievable)
        }, limit)
        return ranked.mapNotNull(byId::get).ifEmpty { SemanticMemoryFallbackPolicy.rank(candidates, limit) }
    }

    companion object {
        const val EMBEDDING_MODEL = "embedding"
        fun encode(values: FloatArray): ByteArray = ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).also { buffer -> values.forEach(buffer::putFloat) }.array()
        fun decode(bytes: ByteArray): FloatArray = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).let { buffer -> FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float } }
    }
}
