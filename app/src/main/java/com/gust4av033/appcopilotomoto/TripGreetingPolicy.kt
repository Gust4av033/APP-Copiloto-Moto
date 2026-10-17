package com.gust4av033.appcopilotomoto

import kotlin.math.roundToInt

enum class TripGreetingMode { VOICE_SHORT, TONE_ONLY, SILENT }
enum class TripAnnouncement { VOICE, TONE, NONE }
enum class TripGreetingSkip { SILENT_SETTING, AUDIO_CYCLE, NO_HEADSET_ROUTE, UNEXPECTED_STOP, MOVING, ALREADY_SENT }

data class TripGreetingDecision(val announcement: TripAnnouncement,
    val skip: TripGreetingSkip? = null, val lowBatteryPercent: Int? = null,
    val gpsUnavailable: Boolean = false, val distanceKilometers: Int? = null,
    val durationMinutes: Int? = null)

/** A decision has no microphone, music-control or TTS side effects. */
object TripGreetingPolicy {
    const val LOW_BATTERY_BELOW_PERCENT = 20
    const val MIN_SUMMARY_DISTANCE_METERS = 500.0
    const val MIN_SUMMARY_DURATION_MS = 120_000L
    const val MAX_ROUTE_WAIT_MS = 5_000L

    fun start(mode: TripGreetingMode, headsetRouteReady: Boolean, audioCycleActive: Boolean,
        battery: ContextValue<BatteryContext>?, nowElapsedMs: Long,
        locationEnabled: Boolean, gpsUnavailableConfirmed: Boolean): TripGreetingDecision {
        if (mode == TripGreetingMode.SILENT) return TripGreetingDecision(TripAnnouncement.NONE, TripGreetingSkip.SILENT_SETTING)
        if (audioCycleActive) return TripGreetingDecision(TripAnnouncement.NONE, TripGreetingSkip.AUDIO_CYCLE)
        if (!headsetRouteReady) return TripGreetingDecision(TripAnnouncement.NONE, TripGreetingSkip.NO_HEADSET_ROUTE)
        if (mode == TripGreetingMode.TONE_ONLY) return TripGreetingDecision(TripAnnouncement.TONE)
        val lowBattery = battery?.takeIf { it.freshness(nowElapsedMs) == Freshness.FRESH &&
            !it.value.charging && it.value.percent < LOW_BATTERY_BELOW_PERCENT }?.value?.percent
        return TripGreetingDecision(TripAnnouncement.VOICE, lowBatteryPercent = lowBattery,
            gpsUnavailable = locationEnabled && gpsUnavailableConfirmed)
    }

    fun end(mode: TripGreetingMode, expected: Boolean, moving: Boolean,
        audioCycleActive: Boolean, headsetRouteReady: Boolean,
        locationEnabled: Boolean, summary: TripSummary?): TripGreetingDecision {
        if (!expected) return TripGreetingDecision(TripAnnouncement.NONE, TripGreetingSkip.UNEXPECTED_STOP)
        if (moving) return TripGreetingDecision(TripAnnouncement.NONE, TripGreetingSkip.MOVING)
        if (audioCycleActive) return TripGreetingDecision(TripAnnouncement.NONE, TripGreetingSkip.AUDIO_CYCLE)
        if (mode == TripGreetingMode.SILENT) return TripGreetingDecision(TripAnnouncement.NONE, TripGreetingSkip.SILENT_SETTING)
        if (!headsetRouteReady) return TripGreetingDecision(TripAnnouncement.NONE, TripGreetingSkip.NO_HEADSET_ROUTE)
        if (mode == TripGreetingMode.TONE_ONLY) return TripGreetingDecision(TripAnnouncement.TONE)
        val valid = summary?.takeIf { locationEnabled && it.sampleCount >= 2 &&
            it.distanceMeters.isFinite() && it.distanceMeters >= MIN_SUMMARY_DISTANCE_METERS &&
            it.durationMs >= MIN_SUMMARY_DURATION_MS }
        return TripGreetingDecision(TripAnnouncement.VOICE,
            distanceKilometers = valid?.let { (it.distanceMeters / 1000).roundToInt().coerceAtLeast(1) },
            durationMinutes = valid?.let { (it.durationMs / 60_000.0).roundToInt().coerceAtLeast(1) })
    }
}

interface GreetingSessionMarker {
    var lastWelcomedSessionId: String?
}

/** Shared marker survives a service or process recreation for the same session ID. */
class TripGreetingOnce(private val marker: GreetingSessionMarker) {
    fun claim(sessionId: String?): Boolean {
        if (sessionId.isNullOrBlank() || marker.lastWelcomedSessionId == sessionId) return false
        marker.lastWelcomedSessionId = sessionId
        return true
    }
}

internal class SavedGreetingSessionMarker(context: android.content.Context) : GreetingSessionMarker {
    private val prefs = context.getSharedPreferences("trip_greeting", android.content.Context.MODE_PRIVATE)
    override var lastWelcomedSessionId: String?
        get() = prefs.getString("last_welcomed_session_id", null)
        set(value) { prefs.edit().putString("last_welcomed_session_id", value).commit() }
}

internal object TripGreetingSettings {
    private const val KEY = "trip_greeting_mode"
    fun selected(context: android.content.Context): TripGreetingMode = runCatching {
        TripGreetingMode.valueOf(context.getSharedPreferences("ui_settings", android.content.Context.MODE_PRIVATE)
            .getString(KEY, TripGreetingMode.VOICE_SHORT.name).orEmpty())
    }.getOrDefault(TripGreetingMode.VOICE_SHORT)
    fun select(context: android.content.Context, mode: TripGreetingMode) {
        context.getSharedPreferences("ui_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }
}
