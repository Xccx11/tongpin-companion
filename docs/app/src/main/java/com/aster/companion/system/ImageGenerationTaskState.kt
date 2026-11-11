package com.aster.companion.system

object ImageGenerationTaskState {
    const val RUNNING = "RUNNING"
    const val RETRYING = "RETRYING"

    fun afterApproval(): String = RUNNING

    fun afterTransientFailure(): String = RETRYING
}
