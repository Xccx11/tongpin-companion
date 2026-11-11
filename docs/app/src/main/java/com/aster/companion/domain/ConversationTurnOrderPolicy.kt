package com.aster.companion.domain

/** A visible user message must reach the local conversation stream before group planning can wait on a model. */
object ConversationTurnOrderPolicy {
    fun shouldPersistUserMessageBeforeSpeakerSelection(
        threadType: ConversationType,
        persistUserMessage: Boolean
    ): Boolean = threadType == ConversationType.GROUP && persistUserMessage
}
