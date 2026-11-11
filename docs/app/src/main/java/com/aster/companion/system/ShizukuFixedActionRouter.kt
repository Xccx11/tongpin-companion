package com.aster.companion.system

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class ShizukuActionResult(val success: Boolean, val summary: String)

/** Runs schema-owned commands directly through Shizuku's authorized Binder, without a UserService child process. */
@Singleton
class ShizukuFixedActionRouter @Inject constructor(
    private val status: ShizukuStatusProvider
) {
    val isCommandChannelAlive: Boolean get() = runCatching { status.current().ready && Shizuku.pingBinder() }.getOrDefault(false)

    suspend fun connect(): ShizukuActionResult = withContext(Dispatchers.IO) {
        if (!status.current().ready) return@withContext ShizukuActionResult(false, "Shizuku is not authorized")
        runCatching {
            requireService()
            status.refresh()
            ShizukuActionResult(true, "Shizuku command channel connected")
        }.getOrElse { ShizukuActionResult(false, it.message ?: "Unable to connect Shizuku command channel") }
    }

    suspend fun readBattery(): ShizukuActionResult = execute("device.read_battery", null)
    suspend fun readForegroundApp(): ShizukuActionResult = execute("device.read_foreground_app", null)
    suspend fun setAlarm(hour: Int, minute: Int): ShizukuActionResult {
        require(hour in 0..23 && minute in 0..59) { "Invalid alarm time" }
        return execute("device.set_alarm", "$hour:$minute")
    }
    suspend fun openKnownApp(packageName: String): ShizukuActionResult = execute("app.open_known", packageName)
    suspend fun dial(phoneNumber: String): ShizukuActionResult = execute("phone.dial", phoneNumber)
    suspend fun composeSms(phoneNumber: String): ShizukuActionResult = execute("sms.compose", phoneNumber)

    private suspend fun execute(capability: String, argument: String?): ShizukuActionResult = withContext(Dispatchers.IO) {
        if (!status.current().ready) return@withContext ShizukuActionResult(false, "Shizuku is not authorized")
        runCatching {
            val process = requireService().newProcess(FixedShizukuCommand.forAction(capability, argument).toTypedArray(), null, null)
            try {
                coroutineScope {
                    val stdout = async { read(process.inputStream) }
                    val stderr = async { read(process.errorStream) }
                    val exit = process.waitFor()
                    val output = stdout.await().ifBlank { stderr.await() }.take(1_024)
                    ShizukuActionResult(exit == 0, if (exit == 0) output.ifBlank { "Completed" } else "FAILED:$exit ${output.take(240)}")
                }
            } finally {
                runCatching { process.destroy() }
            }
        }.getOrElse { ShizukuActionResult(false, it.message ?: "Shizuku action failed") }
    }

    private fun requireService(): IShizukuService {
        require(Shizuku.pingBinder()) { "Shizuku Binder is unavailable" }
        return requireNotNull(IShizukuService.Stub.asInterface(Shizuku.getBinder())) { "Shizuku service is unavailable" }
    }

    private fun read(descriptor: ParcelFileDescriptor): String =
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        }
}

object PackageNamePolicy {
    private val blockedFragments = listOf("bank", "payment", "wallet", "paytm", "phonepe", "alipay", "wechat", "paypal", "authenticator", "password", "settings", "permission", "security")
    fun isAllowed(packageName: String): Boolean = packageName.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")) && blockedFragments.none { packageName.contains(it, true) }
}

object PhoneNumberPolicy {
    fun isAllowed(phoneNumber: String): Boolean = phoneNumber.matches(Regex("\\+?[0-9]{3,20}"))
}
