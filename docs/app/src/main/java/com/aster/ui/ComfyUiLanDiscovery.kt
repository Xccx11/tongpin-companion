package com.aster.ui

import android.content.Context
import android.net.wifi.WifiManager
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Explicitly discovers a ComfyUI server on the current Wi-Fi subnet. It never changes saved settings itself. */
object ComfyUiLanDiscovery {
    fun localIpv4(context: Context): String? {
        val raw = context.applicationContext.getSystemService(WifiManager::class.java)
            ?.connectionInfo?.ipAddress ?: return null
        if (raw == 0) return null
        return listOf(raw and 0xff, raw shr 8 and 0xff, raw shr 16 and 0xff, raw shr 24 and 0xff).joinToString(".")
    }

    suspend fun findServer(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val localIp = requireNotNull(localIpv4(context)) { "Wi-Fi address is unavailable" }
            val octets = localIp.split('.')
            require(octets.size == 4) { "Wi-Fi address is unavailable" }
            val prefix = octets.take(3).joinToString(".")
            val ownHost = octets.last().toIntOrNull()
            var discovered: String? = null
            for (port in candidatePorts()) {
                discovered = scanSubnet(prefix, ownHost, port)
                if (discovered != null) break
            }
            requireNotNull(discovered) { "No ComfyUI server was found on this Wi-Fi" }
        }
    }

    fun candidatePorts(): List<Int> = listOf(8188, 8189, 8190, 8191, 8192)

    private suspend fun scanSubnet(prefix: String, ownHost: Int?, port: Int): String? = coroutineScope {
        val semaphore = Semaphore(SCAN_CONCURRENCY)
        (1..254).filter { it != ownHost }.map { host ->
            async {
                semaphore.withPermit {
                    val address = "http://$prefix.$host:$port"
                    if (isComfyUi(address)) address else null
                }
            }
        }.awaitAll().firstOrNull { it != null }
    }

    private fun isComfyUi(address: String): Boolean = runCatching {
        val connection = URL("$address/system_stats").openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = CONNECT_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.useCaches = false
        try {
            connection.responseCode == HttpURLConnection.HTTP_OK
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    private const val CONNECT_TIMEOUT_MS = 450
    private const val SCAN_CONCURRENCY = 24
}
