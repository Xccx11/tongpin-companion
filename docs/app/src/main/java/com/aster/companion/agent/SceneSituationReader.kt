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
 * 读她"此刻在做什么"（生活状态里的一句话），用来给场景决策与提示词提供依据。
 *
 * 从 `ScenePromptCoordinator` 抽出：这是纯读库操作，与提示词/模型调用无关。
 */
@javax.inject.Singleton
class SceneSituationReader @Inject constructor(
    private val dao: com.aster.companion.data.CompanionDao,
) {
    suspend fun situationOf(characterId: String): String {
        val living = dao.livingState(characterId)
        val place = living?.locationId?.takeIf { it.isNotBlank() }
            ?.let { dao.worldLocation(it)?.displayName ?: it }
        val detail = listOfNotNull(
            living?.currentActivity?.takeIf { it.isNotBlank() },
            place?.let { "在$it" },
            living?.mood?.takeIf { it.isNotBlank() }?.let { "心情 $it" }
        ).joinToString(" · ")
        // 这段是**背景**，不是这次的拍摄指令。原先拼成"她此刻正在：XXX"，
        // 生活引擎排的活动看起来最像重点，于是压过了对话内容——
        // 她那边走生活时间线，和你们聊的内容没关系，照它出图就出戏。
        if (detail.isBlank()) return ""
        return "她的生活背景（仅供参考，不要照搬；和你们正在聊的内容对不上时，以聊天内容为准）：$detail"
    }
}
