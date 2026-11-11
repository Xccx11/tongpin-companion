package com.aster.companion.lite.memory

import com.aster.companion.agent.OpenAiCompatibleClient
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「记得的事」的统一选取入口（记忆分册 §4.3）。
 *
 * 桥提供 `/v1/embeddings` 且配了 Embedding 模型时，用向量相似度排序；
 * 未配置、请求失败或向量不可比时，静默回退到无向量的关键词 + 重要度规则。
 * 任何情况下都不会因为 Embedding 不可用而让聊天失败。
 */
@Singleton
class LiteMemorySelector @Inject constructor(
    private val dao: CompanionDao,
    private val client: OpenAiCompatibleClient,
    private val cipher: CompanionSecretCipher
) {

    /**
     * 事实向量缓存（进程内）。
     *
     * 一次回复要挑最多 [MAX_EMBED_CANDIDATES] 条候选事实，**每条各发一次** `/v1/embeddings`
     * （L2 表没有向量列，只能现算）。这些事实在好几轮里不会变，不缓存就等于每轮都要重算一遍，
     * 白白多等十几秒 —— 回复"转圈"有很大一部分来自这里。
     */
    private val vectorCache = MemoryVectorCache()

    suspend fun select(
        facts: List<LiteMemoryFact>,
        userText: String,
        budget: MemoryBudget = MemoryBudgetPolicy.default
    ): List<String> {
        if (facts.isEmpty()) return emptyList()
        val byVector = runCatching { selectByEmbedding(facts, userText, budget) }.getOrNull()
        return byVector ?: MemoryRetrievalPolicy.select(facts, userText, budget)
    }

    /** 返回 null 表示「本次不用向量」，交由调用方回退。 */
    private suspend fun selectByEmbedding(
        facts: List<LiteMemoryFact>,
        userText: String,
        budget: MemoryBudget
    ): List<String>? {
        if (userText.isBlank()) return null
        val profile = dao.modelProfile(EMBEDDING_MODEL) ?: return null
        if (!profile.enabled || profile.encryptedApiKey.isNullOrBlank()) return null
        val apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
        val query = client.embed(profile, apiKey, userText).getOrNull() ?: return null
        // 每条事实都要现算向量（L2 表没有向量列），所以只取高价值的前 N 条做候选。
        val candidates = facts
            .filter { !it.blockedFromMention && MemoryBudgetPolicy.isInjectable(it.kind) }
            .sortedWith(
                compareByDescending<LiteMemoryFact> { it.importance }
                    .thenByDescending { it.updatedAt }
            )
            .take(MAX_EMBED_CANDIDATES)
        if (candidates.isEmpty()) return emptyList()
        val vectors = candidates.mapNotNull { fact ->
            val key = vectorCache.key(profile.modelName, fact.id, fact.updatedAt)
            vectorCache.get(key)?.let { cached -> return@mapNotNull fact to cached }
            client.embed(profile, apiKey, fact.content).getOrNull()?.let { vector ->
                vectorCache.put(key, vector)
                fact to vector
            }
        }
        if (vectors.isEmpty()) return null
        return MemoryEmbeddingPolicy.selectBySimilarity(vectors, query, budget)
    }

    private companion object {
        /** 与设置页「语义检索」一致的模型 kind。 */
        const val EMBEDDING_MODEL = "embedding"
        const val MAX_EMBED_CANDIDATES = 12
    }
}
