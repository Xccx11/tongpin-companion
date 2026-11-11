package com.aster.companion.system

import com.aster.companion.data.PendingActionEntity

/**
 * 「重新生成」的准入。
 *
 * 和 [ImageGenerationRetryPolicy] 不同：那条只管**终态 FAILED** 的重试；
 * 这条放行**正在生成中**（RUNNING / RETRYING）—— 用户点"重新生成"的意思就是
 * "这次不要了，重开一版"，所以正在跑的时候也应该能按。
 *
 * 判据直接复用 [ImageGenerationCancellationPolicy.canCancel]：它已经是
 * "RUNNING/RETRYING + 是图片任务"这一个条件，语义上就是"这次现在可以被换掉"。
 * 不另抄一份能力清单，免得两处漂移。
 *
 * 终态不走这条路：FAILED 走 retryImage；CANCELLED 是终态、recovery 永不复活。
 */
object ImageGenerationRegeneratePolicy {
    fun canRegenerate(action: PendingActionEntity): Boolean =
        ImageGenerationCancellationPolicy.canCancel(action)
}
