package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class WeatherProviderTest {
    @Test fun cacheAndStaleThresholdsAreExplicit() {
        assertEquals(30 * 60 * 1000L, WeatherPolicy.MAX_AGE_MS)
        assertEquals(2 * 60 * 60 * 1000L, WeatherPolicy.STALE_AGE_MS)
        assertTrue(WeatherPolicy.CACHE_KEY_PREFIX.startsWith("weather_cache"))
    }

    @Test fun snapshotReportsEvidenceConfidence() {
        val fresh = WeatherSnapshot(22.5, 0.0, 1, 1000)
        val stale = fresh.copy(stale = true)
        assertEquals(0.9, fresh.confidence, 0.001)
        assertEquals(0.25, stale.confidence, 0.001)
        assertFalse(stale.source.isBlank())
    }
}
