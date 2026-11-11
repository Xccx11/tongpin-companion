package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 场景链路里**唯一**的模型调用出口：超时 + 后台许可 + 请求追踪都收在这里。
 *
 * 从 `ScenePromptCoordinator` 抽出来的原因：原来"发请求"这件事和"决定拍什么/写提示词"
 * 混在一个类里，改超时策略要翻整个协调器。现在它对上层只暴露一个 `chatOnce`。
 */
@Singleton
class SceneTransport @Inject constructor(
    private val client: OpenAiCompatibleClient,
) {
    /**
     * 调一次模型，返回文本；超时或失败返回 null（**不抛**，让上层决定回退策略）。
     * 每次调用都会记进 [com.aster.companion.diag.RequestTrace]。
     */
    suspend fun chatOnce(
        profile: ModelProfileEntity,
        apiKey: String,
        system: String,
        user: String,
        useBackgroundGate: Boolean,
    ): String? {
        val startedAt = System.currentTimeMillis()
        val block = suspend {
            withTimeoutOrNull(SCENE_CHAT_TIMEOUT_MS) {
                client.chat(profile, apiKey, system, user).getOrNull()
            }
        }
        val result = if (useBackgroundGate) {
            com.aster.companion.lite.LiteBackgroundLlmGate.withPermit(block)
        } else {
            block()
        }
        com.aster.companion.diag.RequestTrace.record(
            path = "scene:" + profile.modelName,
            durationMs = System.currentTimeMillis() - startedAt,
            ok = result != null,
        )
        return result
    }

    companion object {
        /** 单次场景模型调用的超时：90 秒（出图链路本来就慢，超时太短会白白重试）。 */
        const val SCENE_CHAT_TIMEOUT_MS = 90_000L
    }
}