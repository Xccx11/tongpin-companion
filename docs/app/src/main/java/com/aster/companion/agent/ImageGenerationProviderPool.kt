package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.ModelProfileEntity
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class ImageGenerationProvider(
    val id: String,
    val profile: ModelProfileEntity,
    val apiKey: String
)

/**
 * 出图时用哪一个图像 API。
 *
 * **主配置是"后备"**（设置页文案：「主配置作为 API 池的后备」）：池里有可用条目就只用池；
 * 池空（或条目全被停用/没 key）才轮到主配置。以前主配置也被塞进轮转列表里，
 * 于是池里配了 n 个条目时约 1/(n+1) 的图是用主配置出的 —— 与文案不符，
 * 也会让"池里专门配的那套"时灵时不灵。
 */
@Singleton
class ImageGenerationProviderPool @Inject constructor(
    private val dao: CompanionDao,
    private val cipher: CompanionSecretCipher
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val nextIndex = AtomicInteger(0)

    suspend fun <T> useProvider(
        fallback: ModelProfileEntity?,
        preferredProviderId: String? = null,
        action: suspend (ImageGenerationProvider) -> T
    ): T? {
        val rotation = rotationProviders(fallback)
        if (rotation.isEmpty()) return null

        val ordered = if (!preferredProviderId.isNullOrBlank()) {
            // 指定了"上一次出图用过的 provider"：能找到就继续用它；找不到（例如那条已经从池里删了、
            // 或者键是旧的任务号）就退回正常轮转，而不是让整次生成直接失败。
            listOfNotNull(providerById(preferredProviderId, fallback)).ifEmpty { rotate(rotation) }
        } else {
            rotate(rotation)
        }
        val free = ordered.firstOrNull { locks.getOrPut(it.id) { Mutex() }.tryLock() }
        val selected = free ?: ordered.first()
        val lock = locks.getOrPut(selected.id) { Mutex() }
        if (free == null) lock.lock()
        return try {
            action(selected)
        } finally {
            lock.unlock()
        }
    }

    /**
     * 按 id 找 provider：**池里找不到时也认主配置**。
     *
     * 必须认：旧版本是用轮转生成的，任务里存的 provider id 可能是主配置的
     * [FALLBACK_PROVIDER_ID]，它还得能被拉取/取消（否则那些任务再也撤不掉）。
     */
    suspend fun providerById(id: String, fallback: ModelProfileEntity?): ImageGenerationProvider? =
        poolProviders().firstOrNull { it.id == id } ?: fallbackProvider(fallback)?.takeIf { it.id == id }

    private fun rotate(providers: List<ImageGenerationProvider>): List<ImageGenerationProvider> {
        val start = Math.floorMod(nextIndex.getAndIncrement(), providers.size)
        return providers.indices.map { providers[(start + it) % providers.size] }
    }

    /** 轮转候选：池优先，池空才用主配置。 */
    private suspend fun rotationProviders(fallback: ModelProfileEntity?): List<ImageGenerationProvider> =
        poolProviders().ifEmpty { listOfNotNull(fallbackProvider(fallback)) }

    private suspend fun poolProviders(): List<ImageGenerationProvider> =
        dao.enabledImageApiPool().map { entry ->
            ImageGenerationProvider(
                id = entry.id,
                profile = ModelProfileEntity(
                    id = entry.id,
                    kind = "image_generation",
                    baseUrl = entry.baseUrl,
                    modelName = entry.modelName,
                    enabled = true,
                    timeoutSeconds = 120,
                    encryptedApiKey = entry.encryptedApiKey,
                    lastTestResult = null,
                    imageProtocol = entry.imageProtocol
                ),
                apiKey = cipher.decrypt(entry.encryptedApiKey)
            )
        }

    private fun fallbackProvider(fallback: ModelProfileEntity?): ImageGenerationProvider? =
        fallback?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }?.let { profile ->
            ImageGenerationProvider(
                id = FALLBACK_PROVIDER_ID,
                profile = profile,
                apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
            )
        }

    companion object {
        /** 主配置在任务参数里的 provider id（历史上就是这个名字，别改）。 */
        const val FALLBACK_PROVIDER_ID = "legacy-image-model"
    }
}
