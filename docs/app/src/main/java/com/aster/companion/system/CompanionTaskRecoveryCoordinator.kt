package com.aster.companion.system

import com.aster.companion.data.DurableTaskStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Replays only tasks that were already approved or running before process death. */
@Singleton
class CompanionTaskRecoveryCoordinator @Inject constructor(
    private val durableTasks: DurableTaskStore,
    private val actions: CompanionActionCoordinator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var schedulerStarted = false

    suspend fun recoverOnAppStart() {
        durableTasks.recoverInterruptedTasks()
        actions.resumeInterruptedImageActions()
        if (!schedulerStarted) {
            schedulerStarted = true
            scope.launch {
                while (isActive) {
                    // A foreground image service can be stopped while the app process survives.
                    // Re-entering a RUNNING action polls its persisted provider task before any new submit.
                    actions.resumeInterruptedImageActions()
                    val dueImageActions = durableTasks.dueImageActionIds()
                    actions.resumeDueImageActions(dueImageActions)
                    delay(RETRY_POLL_INTERVAL_MS)
                }
            }
        }
    }

    private companion object { const val RETRY_POLL_INTERVAL_MS = 5_000L }
}
