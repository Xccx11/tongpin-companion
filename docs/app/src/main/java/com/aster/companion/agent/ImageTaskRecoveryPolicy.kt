package com.aster.companion.agent

/** A provider task that may already exist must be queried before a new request is allowed. */
enum class ImageTaskRecoveryDecision {
    POLL_EXISTING,
    SUBMIT_NEW
}

/** A background image task has no client-side render deadline once the provider accepted it. */
object ImageTaskPollingPolicy {
    fun shouldContinue(status: ComfyGatewayTaskStatus): Boolean =
        status == ComfyGatewayTaskStatus.Queued || status == ComfyGatewayTaskStatus.Running
}

object ImageTaskRecoveryPolicy {
    fun afterTimeout(providerTaskId: String?): ImageTaskRecoveryDecision =
        if (providerTaskId.isNullOrBlank()) ImageTaskRecoveryDecision.SUBMIT_NEW
        else ImageTaskRecoveryDecision.POLL_EXISTING

    fun maySubmitNewTask(providerTaskId: String?): Boolean = providerTaskId.isNullOrBlank()

    fun shouldPlanPrompt(providerTaskId: String?): Boolean = providerTaskId.isNullOrBlank()
}

/** The request reached a provider task boundary; the id must survive a timeout or process death. */
class ImageProviderTaskException(
    val providerTaskId: String,
    val terminal: Boolean,
    message: String
) : IllegalStateException(message)

class ImageTaskCancelledException(
    providerTaskId: String
) : IllegalStateException("Image generation was cancelled: $providerTaskId")
