package com.gust4av033.appcopilotomoto

/** An explicit instruction only. Neither destination text nor ETA is a maneuver. */
enum class ManeuverOrigin { OBSERVED, APP_ROAD_ROUTE }

data class ManeuverObservation(val id: String, val instruction: String, val distanceMeters: Int,
    val capturedAtElapsedMs: Long, val ttlMs: Long, val origin: ManeuverOrigin) {
    init { require(id.isNotBlank() && instruction.isNotBlank() && distanceMeters >= 0 && ttlMs > 0) }
}

interface ManeuverSource {
    fun current(snapshot: TripContextSnapshot, nowElapsedMs: Long): ManeuverObservation?
}

/** Only text already recognized as an explicit turn by NavigationObservationParser. */
internal object ObservedManeuverSource : ManeuverSource {
    override fun current(snapshot: TripContextSnapshot, nowElapsedMs: Long): ManeuverObservation? {
        val turn = snapshot.navigation.nextTurnInstruction?.takeIf {
            !it.isSimulated && it.freshness(nowElapsedMs) != Freshness.EXPIRED } ?: return null
        val distance = snapshot.navigation.distanceToNextTurnMeters?.takeIf {
            !it.isSimulated && it.source == turn.source &&
                it.capturedAtElapsedRealtimeMs == turn.capturedAtElapsedRealtimeMs &&
                it.freshness(nowElapsedMs) != Freshness.EXPIRED } ?: return null
        return ManeuverObservation("${turn.source}:${turn.value.trim().lowercase()}", turn.value,
            distance.value, turn.capturedAtElapsedRealtimeMs, minOf(turn.ttlMs, distance.ttlMs),
            ManeuverOrigin.OBSERVED)
    }
}

/** The own-route adapter is dormant until a real RoadRoutePlan and progress are available. */
internal class OwnRoadManeuverSource(private val candidate: () -> ManeuverObservation?) : ManeuverSource {
    override fun current(snapshot: TripContextSnapshot, nowElapsedMs: Long) = candidate()
        ?.takeIf { it.origin == ManeuverOrigin.APP_ROAD_ROUTE }
}

/** One instruction at each distance step; never repeats on GPS jitter or expired evidence. */
internal class NavigationSpeechDeduplicator {
    companion object {
        const val PREPARE_METERS = 450
        const val ADVANCE_METERS = 175
        const val IMMEDIATE_METERS = 50
        const val PASSED_GRACE_MS = 5_000L
    }
    private var id: String? = null
    private var lastStep = 0

    fun next(observation: ManeuverObservation?, nowElapsedMs: Long, speedMetersPerSec: Float?,
        conversationActive: Boolean): String? {
        if (conversationActive || observation == null || nowElapsedMs < observation.capturedAtElapsedMs ||
            nowElapsedMs - observation.capturedAtElapsedMs >= observation.ttlMs) return null
        val speed = speedMetersPerSec?.takeIf { it.isFinite() && it >= 0f } ?: return null
        if (speed > 0.5f && nowElapsedMs - observation.capturedAtElapsedMs >
            observation.distanceMeters * 1_000.0 / speed + PASSED_GRACE_MS) return null
        val step = when {
            observation.distanceMeters <= IMMEDIATE_METERS -> 3
            observation.distanceMeters <= ADVANCE_METERS -> 2
            observation.distanceMeters <= PREPARE_METERS -> 1
            else -> 0
        }
        if (observation.id != id) { id = observation.id; lastStep = 0 }
        if (step == 0 || step <= lastStep) return null
        lastStep = step
        return when (step) {
            1 -> "En unos 450 metros, ${observation.instruction}."
            2 -> "En unos ${observation.distanceMeters} metros, ${observation.instruction}."
            else -> "Ahora, ${observation.instruction}."
        }
    }

    fun reset() { id = null; lastStep = 0 }
}

internal object NavigationSpeechSettings {
    private const val KEY = "experimental_navigation_speech"
    fun enabled(context: android.content.Context): Boolean = !BuildConfig.IS_PLAYSTORE_BUILD &&
        context.getSharedPreferences("ui_settings", android.content.Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun setEnabled(context: android.content.Context, enabled: Boolean) {
        context.getSharedPreferences("ui_settings", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, enabled && !BuildConfig.IS_PLAYSTORE_BUILD).apply()
    }
}
