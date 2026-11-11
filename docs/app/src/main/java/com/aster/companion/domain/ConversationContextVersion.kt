package com.aster.companion.domain

object ConversationContextVersion {
    fun isCurrent(requestVersion: Long, currentVersion: Long): Boolean = requestVersion == currentVersion
}
