package com.aster.companion.agent

/** Retry policy for status requests. It has no total deadline: the provider task is durable. */
object ImagePollingRetryPolicy {
    fun shouldKeepTracking(status: ComfyGatewayTaskStatus): Boolean =
        status == ComfyGatewayTaskStatus.Queued || status == ComfyGatewayTaskStatus.Running

    fun isTransientHttpStatus(code: Int): Boolean =
        code == 408 || code == 429 || code in 500..599

    fun isTransientMessage(message: String?): Boolean {
        val detail = message.orEmpty().lowercase()
        return detail.contains("timeout") ||
            detail.contains("connection abort") ||
            detail.contains("connection reset") ||
            detail.contains("broken pipe") ||
            Regex("\\bHTTP\\s+(408|429|5\\d{2})\\b").containsMatchIn(detail)
    }

    fun retryDelayMillis(consecutiveFailures: Int): Long =
        (500L shl consecutiveFailures.coerceIn(0, 4)).coerceAtMost(5_000L)
}
