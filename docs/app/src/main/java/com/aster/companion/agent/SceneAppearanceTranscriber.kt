package com.aster.companion.agent

import android.util.Log
import com.aster.companion.data.CharacterEntity
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.lite.LiteProductConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 把用户写的外貌设定**转写成一句可用于出图的英文/中文描述**。
 *
 * 从 `ScenePromptCoordinator` 抽出：这是一次独立的模型调用（收口在 [SceneTransport] 里），
 * 失败时退回原文，与"决定拍什么 / 组装提示词"无关。
 */
@javax.inject.Singleton
class SceneAppearanceTranscriber @Inject constructor(
    private val transport: SceneTransport,
    private val imageRuleResolver: ImageGenerationRuleResolver,
    private val renderPromptBuilder: CharacterRenderPromptBuilder,
) {
    suspend fun transcribeAppearance(
        profile: ModelProfileEntity,
        apiKey: String,
        appearance: String,
        useBackgroundGate: Boolean
    ): String {
        val anchor = appearance.trim()
        if (anchor.isEmpty()) return anchor
        val rule = runCatching { imageRuleResolver.resolveAppearanceTranscribeRule() }
            .getOrElse { renderPromptBuilder.identityFor(anchor) }
        val reply = transport.chatOnce(
            profile,
            apiKey,
            rule,
            "【她的外貌设定（用户亲手写的，逐项对照，一项都不能省）】\n$anchor",
            useBackgroundGate
        )
        val translated = reply?.let { ComfyTagPromptPolicy.stripFences(it).trim() }.orEmpty()
        if (translated.isBlank()) {
            Log.w(TAG, "appearance transcribe failed, falling back to the raw setting")
            return anchor
        }
        Log.i(TAG, "appearance transcribed: ${translated.take(120)}")
        return translated
    }

    private companion object {
        const val TAG = "SceneAppearance"
    }
}
