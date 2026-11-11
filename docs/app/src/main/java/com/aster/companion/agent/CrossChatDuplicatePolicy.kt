package com.aster.companion.agent

/** Prevents a just-delivered private message from being echoed into the source group. */
object CrossChatDuplicatePolicy {
    fun isRecentDuplicate(groupCandidate: String, recentPrivateBodies: List<String>): Boolean {
        val candidate = canonical(groupCandidate)
        if (candidate.isBlank()) return false
        return recentPrivateBodies.any { canonical(it) == candidate }
    }

    private fun canonical(value: String): String = value
        .lowercase()
        .replace(Regex("[\\s，。！？、,.!?~～()（）<>《》\\[\\]【】]+"), "")
        .trim()
}
