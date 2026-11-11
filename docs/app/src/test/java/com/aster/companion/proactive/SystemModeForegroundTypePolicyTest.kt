package com.aster.companion.proactive

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemModeForegroundTypePolicyTest {
    @Test
    fun `uses special use only until location permission is granted`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            SystemModeForegroundTypePolicy.resolve(
                hasCoarseLocationPermission = false,
                hasFineLocationPermission = false
            )
        )
    }

    @Test
    fun `includes location when either location permission is granted`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            SystemModeForegroundTypePolicy.resolve(
                hasCoarseLocationPermission = true,
                hasFineLocationPermission = false
            )
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            SystemModeForegroundTypePolicy.resolve(
                hasCoarseLocationPermission = false,
                hasFineLocationPermission = true
            )
        )
    }
}
