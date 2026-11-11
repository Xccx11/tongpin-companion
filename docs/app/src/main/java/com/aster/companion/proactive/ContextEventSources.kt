package com.aster.companion.proactive

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.location.LocationManager
import android.location.Geocoder
import android.os.Build
import android.os.BatteryManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.CancellationSignal
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.aster.service.AsterNotificationListenerService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class ContextEvent(val source: String, val summary: String, val dedupeKey: String)

object BatteryStatePolicy {
    fun shouldEmit(previous: String?, current: String, lastRecordedAt: Long, now: Long): Boolean =
        previous != current || now - lastRecordedAt >= REFRESH_INTERVAL_MS

    const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L
}

@Singleton
class BatteryEventSource @Inject constructor(@ApplicationContext private val context: Context) {
    private var lastState: String? = null
    private var lastRecordedAt = 0L

    fun poll(now: Long): ContextEvent? {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return null
        val percent = level * 100 / scale
        val chargingStatus = status.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING || chargingStatus == BatteryManager.BATTERY_STATUS_FULL
        val state = "${if (charging) "charging" else "discharging"}:${percent / 5}"
        if (!BatteryStatePolicy.shouldEmit(lastState, state, lastRecordedAt, now)) return null
        lastState = state
        lastRecordedAt = now
        val summary = "当前电量 $percent%，${if (charging) "正在充电" else "未在充电"}。"
        return ContextEvent("device:battery", summary, "battery:$state:${now / BatteryStatePolicy.REFRESH_INTERVAL_MS}")
    }
}

/** Network awareness is intentionally category-only: no SSID, carrier, IP, or network identifier. */
object NetworkStatePolicy {
    fun classify(hasNetwork: Boolean, validated: Boolean, wifi: Boolean, cellular: Boolean): String = when {
        !hasNetwork || !validated -> "offline"
        wifi -> "wifi"
        cellular -> "cellular"
        else -> "other"
    }

    fun shouldEmit(previous: String?, current: String, lastRecordedAt: Long, now: Long): Boolean =
        previous != current || now - lastRecordedAt >= REFRESH_INTERVAL_MS

    const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L
}

@Singleton
class NetworkEventSource @Inject constructor(@ApplicationContext private val context: Context) {
    private var lastState: String? = null
    private var lastRecordedAt = 0L

    fun poll(now: Long): ContextEvent? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = manager.activeNetwork
        val capabilities = network?.let(manager::getNetworkCapabilities)
        val state = NetworkStatePolicy.classify(
            hasNetwork = network != null,
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        )
        if (!NetworkStatePolicy.shouldEmit(lastState, state, lastRecordedAt, now)) return null
        lastState = state
        lastRecordedAt = now
        val summary = when (state) {
            "wifi" -> "当前网络状态：已连接 Wi-Fi。"
            "cellular" -> "当前网络状态：正在使用移动数据。"
            "offline" -> "当前网络状态：没有可用的已验证网络。"
            else -> "当前网络状态：已连接其他网络。"
        }
        return ContextEvent("device:network", summary, "network:$state:${now / NetworkStatePolicy.REFRESH_INTERVAL_MS}")
    }
}

/** Reads only the active track title and artist from the user-authorized media session. */
@Singleton
class MediaPlaybackEventSource @Inject constructor(@ApplicationContext private val context: Context) {
    private var lastRecordedAt = 0L
    private var lastSignature: String? = null
    private var latestDiagnostic = "not_started"

    fun poll(now: Long): ContextEvent? {
        val playing = context.getSystemService(AudioManager::class.java)?.isMusicActive == true
        val track = if (playing) activeTrack() else null
        val signature = track?.let(MediaPlaybackPolicy::signature) ?: if (playing) "playing_without_track" else "not_playing"
        if (lastSignature == signature && now - lastRecordedAt < REFRESH_INTERVAL_MS) return null
        lastSignature = signature
        lastRecordedAt = now
        if (!playing) latestDiagnostic = "not_playing"
        return ContextEvent(
            "device:media",
            track?.let(MediaPlaybackPolicy::summary)
                ?: if (playing) "Media audio is currently playing on the user's device, but its title is unavailable."
                else "No media audio is currently playing on the user's device.",
            "media:$signature:${now / REFRESH_INTERVAL_MS}"
        )
    }

    fun diagnostic(): String = latestDiagnostic

    private fun activeTrack(): MediaTrack? {
        if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
            latestDiagnostic = "notification_listener_required"
            return null
        }
        val sessions = runCatching {
            context.getSystemService(MediaSessionManager::class.java)
                ?.getActiveSessions(ComponentName(context, AsterNotificationListenerService::class.java))
                .orEmpty()
        }.getOrElse {
            latestDiagnostic = "media_session_unavailable"
            return null
        }
        val controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val metadata = controller?.metadata
        val track = MediaPlaybackPolicy.track(
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        )
        latestDiagnostic = when {
            track != null -> "track_ready"
            controller != null -> "track_metadata_unavailable"
            else -> "no_active_media_session"
        }
        return track
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L
    }
}

/** Stores a coarse human-readable area, never coordinates, for relationship context. */
@Singleton
class PlaceEventSource @Inject constructor(@ApplicationContext private val context: Context) {
    private var lastPollAt = 0L
    private var lastArea: String? = null
    private var lastRecordedAt = 0L
    private var latestDiagnostic = "not_started"

    suspend fun poll(now: Long): ContextEvent? {
        if (now - lastPollAt < POLL_INTERVAL_MS) return null
        lastPollAt = now
        if (!hasLocationPermission()) {
            latestDiagnostic = "location_permission_missing"
            return null
        }
        val location = latestUsableLocation(now)
        if (location == null) {
            latestDiagnostic = "location_unavailable"
            return null
        }
        val area = reverseGeocodeArea(location.latitude, location.longitude)
        if (area.isNullOrBlank()) {
            latestDiagnostic = "area_unavailable"
            return null
        }
        latestDiagnostic = "area_ready"
        if (area == lastArea && now - lastRecordedAt < REFRESH_INTERVAL_MS) return null
        lastArea = area
        lastRecordedAt = now
        return ContextEvent(
            "device:place",
            "Current user area: $area. This is approximate area context only, never an exact coordinate or address.",
            "place:$area:${now / REFRESH_INTERVAL_MS}"
        )
    }

    fun diagnostic(): String = latestDiagnostic

    private suspend fun latestUsableLocation(now: Long): android.location.Location? {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val cached = suspendCancellableCoroutine<android.location.Location?> { continuation ->
            fused.lastLocation
                .addOnSuccessListener { location -> if (continuation.isActive) continuation.resume(location) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        }
        cached?.takeIf { LocationFreshnessPolicy.isUsable(it.latitude, it.longitude, it.accuracy, it.time, now) }?.let { return it }
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
                .filter { LocationFreshnessPolicy.isUsable(it.latitude, it.longitude, it.accuracy, it.time, now) }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeArea(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.let { address -> listOfNotNull(address.locality, address.subAdminArea, address.adminArea).firstOrNull()?.trim() }
        }.getOrNull()
    }

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val POLL_INTERVAL_MS = 10 * 60 * 1000L
        const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
    }
}

@Singleton
class WeatherEventSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    private var lastPollAt = 0L
    private var lastDiagnostic: String? = null

    suspend fun poll(now: Long): ContextEvent? {
        if (now - lastPollAt < POLL_INTERVAL_MS) return null
        lastPollAt = now
        if (!hasLocationPermission()) {
            report(WeatherSourceDiagnostics.locationPermissionMissing())
            return null
        }
        val location = currentOrFreshCachedLocation(now) ?: return null
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=rain,precipitation,weather_code"
        val body = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull()
        if (body.isNullOrBlank()) {
            report(WeatherSourceDiagnostics.weatherRequestFailed())
            return null
        }
        val current = runCatching { Json.parseToJsonElement(body).jsonObject["current"]?.jsonObject }.getOrNull()
        if (current == null) {
            report(WeatherSourceDiagnostics.weatherParseFailed())
            return null
        }
        val rain = current["rain"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val precipitation = current["precipitation"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val code = current["weather_code"]?.jsonPrimitive?.content ?: "unknown"
        report(WeatherSourceDiagnostics.weatherEventReady())
        return ContextEvent("weather", "当前位置天气：降雨 ${rain}mm，降水 ${precipitation}mm，天气代码 $code。请自行判断是否值得联系用户。", "weather:${now / POLL_INTERVAL_MS}:$code:$rain")
    }

    private suspend fun currentOrFreshCachedLocation(now: Long): android.location.Location? {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val current = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine<android.location.Location?> { continuation ->
                val token = CancellationTokenSource()
                continuation.invokeOnCancellation { token.cancel() }
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resume(null) }
            }
        }
        current?.takeIf { location ->
            LocationFreshnessPolicy.isUsable(location.latitude, location.longitude, location.accuracy, location.time, now)
        }?.let {
            report(WeatherSourceDiagnostics.currentLocation())
            return it
        }
        systemCachedLocation(now)?.let {
            report(WeatherSourceDiagnostics.freshCache())
            return it
        }
        val cached = suspendCancellableCoroutine<android.location.Location?> { continuation ->
            fused.lastLocation
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        }
        cached?.takeIf { location ->
            LocationFreshnessPolicy.isUsable(location.latitude, location.longitude, location.accuracy, location.time, now)
        }?.let {
            report(WeatherSourceDiagnostics.freshCache())
            return it
        }
        currentSystemLocation(now)?.let {
            report(WeatherSourceDiagnostics.systemCurrentLocation())
            return it
        }
        report(WeatherSourceDiagnostics.invalidOrUnavailable("location"))
        return null
    }

    private fun systemCachedLocation(now: Long): android.location.Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return SYSTEM_PROVIDERS
            .asSequence()
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { location ->
                LocationFreshnessPolicy.isUsable(location.latitude, location.longitude, location.accuracy, location.time, now)
            }
            .maxByOrNull { location -> location.time }
    }

    private suspend fun currentSystemLocation(now: Long): android.location.Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        for (provider in SYSTEM_PROVIDERS) {
            if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) continue
            val location = withTimeoutOrNull(SYSTEM_LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<android.location.Location?> { continuation ->
                    val cancellation = CancellationSignal()
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    runCatching {
                        manager.getCurrentLocation(
                            provider,
                            cancellation,
                            ContextCompat.getMainExecutor(context)
                        ) { result -> continuation.resume(result) }
                    }.onFailure { continuation.resume(null) }
                }
            }
            if (location != null && LocationFreshnessPolicy.isUsable(
                    location.latitude,
                    location.longitude,
                    location.accuracy,
                    location.time,
                    now
                )
            ) return location
        }
        return null
    }

    private fun report(value: String) {
        if (value != lastDiagnostic) {
            lastDiagnostic = value
            Log.i(TAG, "Weather sensing state=$value")
        }
    }

    private fun hasLocationPermission() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private companion object {
        const val POLL_INTERVAL_MS = 45 * 60 * 1000L
        const val LOCATION_TIMEOUT_MS = 12_000L
        const val SYSTEM_LOCATION_TIMEOUT_MS = 6_000L
        const val TAG = "TongpinSensing"
        val SYSTEM_PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    }
}

/** Stable, coordinate-free weather source diagnostics for device verification. */
object WeatherSourceDiagnostics {
    fun locationPermissionMissing() = "location_permission_missing"
    fun currentLocation() = "current_location"
    fun systemCurrentLocation() = "system_current_location"
    fun freshCache() = "fresh_cache"
    fun invalidOrUnavailable(ignoredRawValue: String) = "invalid_or_unavailable"
    fun weatherRequestFailed() = "weather_request_failed"
    fun weatherParseFailed() = "weather_parse_failed"
    fun weatherEventReady() = "weather_event_ready"
}

data class CalendarPollResult(val events: List<ContextEvent>, val status: String)

object CalendarPollingPolicy {
    fun status(hasPermission: Boolean, eventCount: Int, querySucceeded: Boolean): String = when {
        !hasPermission -> "permission_missing"
        !querySucceeded -> "query_failed"
        eventCount == 0 -> "no_upcoming"
        else -> "events_available"
    }
}

@Singleton
class CalendarEventSource @Inject constructor(@ApplicationContext private val context: Context) {
    private var lastPollAt = 0L

    fun poll(now: Long): CalendarPollResult? {
        if (now - lastPollAt < POLL_INTERVAL_MS) return null
        lastPollAt = now
        val hasPermission = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return CalendarPollResult(emptyList(), CalendarPollingPolicy.status(false, 0, false))
        val until = now + UPCOMING_WINDOW_MS
        val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().appendPath(now.toString()).appendPath(until.toString()).build()
        val queried = runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < MAX_EVENTS) {
                        val id = cursor.getLong(0)
                        val title = cursor.getString(1).orEmpty().take(200)
                        val begin = cursor.getLong(2)
                        add(ContextEvent("calendar", "未来两小时日程：$title，开始时间 $begin。请自行决定是否需要提醒。", "calendar:$id:$begin"))
                    }
                }
            }.orEmpty()
        }
        val events = queried.getOrDefault(emptyList())
        return CalendarPollResult(events, CalendarPollingPolicy.status(true, events.size, queried.isSuccess))
    }

    private companion object {
        const val POLL_INTERVAL_MS = 10 * 60 * 1000L
        const val UPCOMING_WINDOW_MS = 2 * 60 * 60 * 1000L
        const val MAX_EVENTS = 8
    }
}
