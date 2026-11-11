package com.aster.companion.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WeatherSourceDiagnosticsTest {
    @Test fun `diagnostics distinguish the location source without exposing coordinates`() {
        assertEquals("current_location", WeatherSourceDiagnostics.currentLocation())
        assertEquals("system_current_location", WeatherSourceDiagnostics.systemCurrentLocation())
        assertEquals("fresh_cache", WeatherSourceDiagnostics.freshCache())

        val diagnostic = WeatherSourceDiagnostics.invalidOrUnavailable("31.2304,121.4737")

        assertEquals("invalid_or_unavailable", diagnostic)
        assertFalse(diagnostic.contains("31.2304"))
        assertFalse(diagnostic.contains("121.4737"))
    }
}
