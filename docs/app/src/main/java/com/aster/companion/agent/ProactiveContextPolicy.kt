package com.aster.companion.agent

/**
 * Proactive turns may only ground claims in primary records. AI-generated
 * diary summaries and world reflections are useful for browsing, but are not
 * evidence that the user said or experienced something.
 */
object ProactiveContextPolicy {
    fun planningContext(
        recentConversation: List<String>,
        userSavedMemories: List<String>,
        semanticMemories: List<String> = emptyList(),
        privateWorld: List<String> = emptyList(),
        sharedWorld: List<String> = emptyList(),
        groupContext: List<String> = emptyList()
    ): List<String> = buildList {
        addAll(recentConversation)
        addAll(userSavedMemories)
        // Character continuity is context, never proof of a user's real-world action.
        addAll(semanticMemories)
        addAll(privateWorld)
        addAll(sharedWorld)
        addAll(groupContext)
    }

    fun groundingFacts(
        recentConversation: List<String>,
        userSavedMemories: List<String>,
        realEvents: List<String>
    ): List<String> = recentConversation + userSavedMemories + realEvents
}
