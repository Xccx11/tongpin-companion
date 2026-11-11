package com.aster.companion.agent

import java.util.concurrent.ConcurrentHashMap

/** Prevents the notification listener and polling loop from planning one event twice. */
class EventProcessingGate {
    private val activeEventIds = ConcurrentHashMap.newKeySet<String>()

    fun tryAcquire(eventId: String): Boolean = activeEventIds.add(eventId)

    fun release(eventId: String) {
        activeEventIds.remove(eventId)
    }
}
