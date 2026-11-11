package com.aster.companion.proactive

import android.content.pm.ServiceInfo

/** Chooses only foreground-service capabilities that are currently authorized. */
object SystemModeForegroundTypePolicy {
    fun resolve(
        hasCoarseLocationPermission: Boolean,
        hasFineLocationPermission: Boolean
    ): Int {
        val base = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        return if (hasCoarseLocationPermission || hasFineLocationPermission) {
            base or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            base
        }
    }
}
