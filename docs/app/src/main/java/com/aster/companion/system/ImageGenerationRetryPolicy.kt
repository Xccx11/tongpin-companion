package com.aster.companion.system

import com.aster.companion.data.PendingActionEntity

object ImageGenerationRetryPolicy {
    /**
     * 上一版**真正送去渲染**的那份最终提示词（出图成功时按任务写进 `argumentsJson`）。
     *
     * 「重新生成」= 重新生成**照片**：有这份就直接原样重出 —— 先按同一份提示词去拉取那一张
     * （provider 任务还在就直接收它，网关按「同提示词」命中缓存就秒回），拉不到才用同一份
     * 提示词重新提交。**永远不要**在重试/重新生成时让模型重写一遍提示词：提示词一变，
     * 出的就不是同一张图，网关的同提示词缓存也永远命中不了。
     */
    const val RENDERED_PROMPT_KEY = "rendered_prompt"

    fun canRetry(action: PendingActionEntity): Boolean =
        action.state == "FAILED" && action.capabilityId in IMAGE_CAPABILITIES

    /** 只清 provider 任务号：[RENDERED_PROMPT_KEY] 和原始请求必须原样留下。 */
    fun cleanArgumentsForRetry(arguments: Map<String, String>): Map<String, String> =
        arguments.filterKeys { key -> !key.startsWith(PROVIDER_TASK_PREFIX) }

    private const val PROVIDER_TASK_PREFIX = "provider_task_"
    private val IMAGE_CAPABILITIES = setOf(
        "companion.generate_image",
        "companion.propose_image",
        "companion.share_life_moment",
        "companion.propose_private_image",
        "companion.generate_moment_image"
    )
}
