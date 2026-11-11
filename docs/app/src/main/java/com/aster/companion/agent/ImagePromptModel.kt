package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.lite.LiteProductConfig

/**
 * 「图片提示词」这一步用哪个模型 —— **唯一取值点**。
 *
 * 这一步（把上下文总结成最终出图提示词）以前写死用对话模型，所以
 * "聊天用便宜的小模型、总结出图提示词用聪明的大模型"做不到。
 * 现在设置里多了一个「图片提示词」profile（[LiteProductConfig.IMAGE_PROMPT_MODEL]）：
 *
 * - 配了（启用 + 有 key）→ 用它；
 * - 没配 / 停用 / 没 key → **沿用对话模型**（升级后行为不变）。
 *
 * 覆盖四条链路：私聊场景照（ScenePromptCoordinator）、偷看照片（PeekPhotoCoordinator，两条）、
 * 朋友圈配图（MomentsAgentCoordinator）。
 */
internal object ImagePromptModel {
    suspend fun resolve(dao: CompanionDao): ModelProfileEntity? =
        endpointResolved(
            dao,
            dao.modelProfile(LiteProductConfig.IMAGE_PROMPT_MODEL)
                ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
                ?: dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
        )

    /**
     * 顺手把地址按「自动 / 内网 / 外网」解析掉（见 [com.aster.companion.net.EndpointResolver]）：
     * 在家走 192.168.0.100、出门自动回退 ngrok —— 这一步的四个调用方都不用再关心地址。
     * 解析失败一律用原 profile，绝不因为解析把出图弄挂。
     */
    private suspend fun endpointResolved(
        dao: CompanionDao,
        profile: ModelProfileEntity?
    ): ModelProfileEntity? = profile?.let {
        runCatching { com.aster.companion.net.EndpointResolver.resolve(dao, it) }.getOrDefault(it)
    }
}
