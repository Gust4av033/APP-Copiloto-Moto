package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class TripGreetingPolicyTest {
    private fun battery(percent: Int, charging: Boolean, elapsed: Long = 100L) =
        ContextValue(BatteryContext(percent, charging), ContextSource.REPLAY_FIXTURE,
            100L, elapsed, 1f, 1_000L, isSimulated = true)
    private fun start(mode: TripGreetingMode = TripGreetingMode.VOICE_SHORT, route: Boolean = true,
        cycle: Boolean = false, battery: ContextValue<BatteryContext>? = battery(72, false),
        now: Long = 100L, location: Boolean = false, noGps: Boolean = false) =
        TripGreetingPolicy.start(mode, route, cycle, battery, now, location, noGps)
    private fun end(mode: TripGreetingMode = TripGreetingMode.VOICE_SHORT, expected: Boolean = true,
        moving: Boolean = false, cycle: Boolean = false, route: Boolean = true,
        location: Boolean = false, summary: TripSummary? = null) =
        TripGreetingPolicy.end(mode, expected, moving, cycle, route, location, summary)

    @Test fun startKeepsSuccessShortAndReportsOnlyFreshProblems() {
        assertEquals(TripAnnouncement.VOICE, start().announcement)
        assertNull(start().lowBatteryPercent)
        assertFalse(start().gpsUnavailable)
        assertEquals(19, start(battery = battery(19, false)).lowBatteryPercent)
        assertNull(start(battery = battery(19, true)).lowBatteryPercent)
        assertNull(start(battery = battery(19, false), now = 1_100L).lowBatteryPercent)
        assertNull(start(battery = null).lowBatteryPercent)
        assertTrue(start(location = true, noGps = true).gpsUnavailable)
        assertFalse(start(location = false, noGps = true).gpsUnavailable)
        assertEquals(TripGreetingSkip.NO_HEADSET_ROUTE, start(route = false).skip)
        assertEquals(TripGreetingSkip.AUDIO_CYCLE, start(cycle = true).skip)
        assertEquals(TripAnnouncement.TONE, start(mode = TripGreetingMode.TONE_ONLY).announcement)
        assertEquals(TripGreetingSkip.SILENT_SETTING, start(mode = TripGreetingMode.SILENT).skip)
    }

    @Test fun endNeverInventsSummaryOrSpeaksWhileRiding() {
        assertEquals(TripGreetingSkip.UNEXPECTED_STOP, end(expected = false).skip)
        assertEquals(TripGreetingSkip.MOVING, end(moving = true).skip)
        assertEquals(TripGreetingSkip.AUDIO_CYCLE, end(cycle = true).skip)
        assertEquals(TripGreetingSkip.NO_HEADSET_ROUTE, end(route = false).skip)
        assertNull(end(location = true).distanceKilometers)
        assertNull(end(location = false, summary = TripSummary(14_000.0, 32 * 60_000L, 100)).distanceKilometers)
        assertNull(end(location = true, summary = TripSummary(Double.NaN, 32 * 60_000L, 100)).distanceKilometers)
        assertNull(end(location = true, summary = TripSummary(400.0, 32 * 60_000L, 100)).distanceKilometers)
        assertNull(end(location = true, summary = TripSummary(14_000.0, 60_000L, 100)).distanceKilometers)
        assertEquals(14, end(location = true, summary = TripSummary(14_000.0, 32 * 60_000L, 100)).distanceKilometers)
        assertEquals(32, end(location = true, summary = TripSummary(14_000.0, 32 * 60_000L, 100)).durationMinutes)
        assertEquals(TripAnnouncement.TONE, end(mode = TripGreetingMode.TONE_ONLY).announcement)
        assertEquals(TripGreetingSkip.SILENT_SETTING, end(mode = TripGreetingMode.SILENT).skip)
    }

    @Test fun onceMarkerSurvivesCoordinatorRecreation() {
        val marker = object : GreetingSessionMarker { override var lastWelcomedSessionId: String? = null }
        assertTrue(TripGreetingOnce(marker).claim("session-1"))
        assertFalse(TripGreetingOnce(marker).claim("session-1"))
        assertTrue(TripGreetingOnce(marker).claim("session-2"))
        assertFalse(TripGreetingOnce(marker).claim(null))
    }
}
