package com.aster.companion.system

import android.content.pm.PackageManager
import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.Capability
import com.aster.companion.domain.CompanionCapabilitySnapshot
import com.aster.companion.domain.ConfirmationRequirement
import rikka.shizuku.Shizuku
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ShizukuStatus(
    val binderAlive: Boolean,
    val authorized: Boolean,
    val commandChannelAlive: Boolean
) {
    /** Binder authorization is sufficient for the fixed-command router. */
    val ready: Boolean get() = binderAlive && authorized
}

@Singleton
class ShizukuStatusProvider @Inject constructor() {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky { refresh() }
            Shizuku.addBinderDeadListener { refresh() }
            Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        }
    }

    fun current(commandChannelAlive: Boolean = false): ShizukuStatus {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val authorized = binderAlive && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return ShizukuStatus(binderAlive, authorized, commandChannelAlive)
    }

    fun requestPermission(requestCode: Int) {
        check(Shizuku.pingBinder()) { "Shizuku is not running" }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(requestCode)
        }
        refresh()
    }

    fun refresh() { _revision.value = System.nanoTime() }
}

object CapabilitySnapshotFactory {
    fun create(status: ShizukuStatus, version: Long = System.currentTimeMillis()): CompanionCapabilitySnapshot {
        val capabilities = mutableSetOf(
            Capability("companion.send_private_message", ActionTransport.LOCAL, ConfirmationRequirement.NONE),
            Capability("companion.send_group_message", ActionTransport.LOCAL, ConfirmationRequirement.NONE),
            Capability("companion.generate_image", ActionTransport.LOCAL, ConfirmationRequirement.NONE),
            Capability("companion.propose_image", ActionTransport.LOCAL, ConfirmationRequirement.REQUIRED),
            Capability("companion.propose_private_image", ActionTransport.LOCAL, ConfirmationRequirement.REQUIRED),
            Capability("companion.generate_moment_image", ActionTransport.LOCAL, ConfirmationRequirement.REQUIRED)
        )
        if (status.ready) {
            capabilities += Capability("device.read_battery", ActionTransport.SHIZUKU, ConfirmationRequirement.NONE)
            capabilities += Capability("device.read_foreground_app", ActionTransport.SHIZUKU, ConfirmationRequirement.NONE)
            capabilities += Capability("device.set_alarm", ActionTransport.SHIZUKU, ConfirmationRequirement.REQUIRED)
            capabilities += Capability("app.open_known", ActionTransport.SHIZUKU, ConfirmationRequirement.REQUIRED)
            capabilities += Capability("phone.dial", ActionTransport.SHIZUKU, ConfirmationRequirement.REQUIRED)
            capabilities += Capability("sms.compose", ActionTransport.SHIZUKU, ConfirmationRequirement.REQUIRED)
        }
        return CompanionCapabilitySnapshot(version, capabilities)
    }
}
