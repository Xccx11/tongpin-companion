package com.aster.companion.proactive

/** Guards weather context against stale, placeholder, and unusably imprecise locations. */
object LocationFreshnessPolicy {
    fun isUsable(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        observedAt: Long,
        now: Long
    ): Boolean =
        latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0) &&
            accuracyMeters in 0f..MAX_ACCURACY_METERS &&
            observedAt <= now &&
            now - observedAt <= MAX_LOCATION_AGE_MS

    private const val MAX_ACCURACY_METERS = 5_000f
    private const val MAX_LOCATION_AGE_MS = 15 * 60 * 1000L
}
