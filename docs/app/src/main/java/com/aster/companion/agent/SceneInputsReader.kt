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
 * 组装"这一次场景要用的输入"：角色、当前对话、生活状态、模型档案与密钥。
 *
 * 从 `ScenePromptCoordinator` 抽出：只做**读与组装**、不调模型。
 * 注意返回类型是协调器的嵌套类型 `ScenePromptInputs`，已用全限定引用。
 */
@javax.inject.Singleton
class SceneInputsReader @Inject constructor(
    private val dao: com.aster.companion.data.CompanionDao,
    private val imageRuleResolver: ImageGenerationRuleResolver,
) {
    suspend fun inputsFor(characterId: String): ScenePromptCoordinator.ScenePromptInputs? {
        val character = dao.character(characterId) ?: return null
        return ScenePromptCoordinator.ScenePromptInputs(
            character = character,
            conversationProfile = ImagePromptModel.resolve(dao),
            format = imageRuleResolver.format()
        )
    }
}
