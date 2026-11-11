package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A single durable rendering lane with duplicate suppression for the same action. */
@Singleton
class GlobalImageGenerationQueue @Inject constructor(
    private val dao: CompanionDao
) {
    private val queue = ImageGenerationQueueGate()

    suspend fun <T> run(actionId: String, block: suspend () -> T): T? {
        return queue.run(actionId, {
            dao.pendingAction(actionId)?.let { isRunnableState(it.state) } == true
        }, block)
    }

    companion object {
        internal fun isDuplicateInvocation(actionId: String, activeActionIds: Set<String>): Boolean =
            actionId in activeActionIds

        fun isRunnableState(state: String): Boolean = state == "RUNNING" || state == "RETRYING"
    }

}

/** Serializes all image work while allowing recovery ticks to ignore duplicates. */
internal class ImageGenerationQueueGate {
    private val mutex = Mutex()
    private val activeActionIds = ConcurrentHashMap.newKeySet<String>()

    suspend fun <T> run(
        actionId: String,
        shouldRun: suspend () -> Boolean,
        block: suspend () -> T
    ): T? {
        if (!activeActionIds.add(actionId)) return null
        return try {
            mutex.withLock {
                if (shouldRun()) block() else null
            }
        } finally {
            activeActionIds.remove(actionId)
        }
    }
}
