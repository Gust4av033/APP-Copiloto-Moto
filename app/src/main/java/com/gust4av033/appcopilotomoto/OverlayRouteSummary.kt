package com.gust4av033.appcopilotomoto

import kotlin.math.roundToInt

/** A small, explicitly observed summary. No value here proves that a route is active. */
data class OverlayRouteSummary(val provider: String, val detail: String, val ownGps: Boolean = false)

internal fun overlayRouteSummary(snapshot: TripContextSnapshot, nowElapsed: Long): OverlayRouteSummary? {
    fun <T> ContextValue<T>?.current(): ContextValue<T>? = this?.takeIf {
        it.freshness(nowElapsed) == Freshness.FRESH && !it.isSimulated
    }
    val route = snapshot.navigation
    val turn = route.nextTurnInstruction.current()
    val eta = route.remainingMinutes.current()
    val remaining = route.remainingDistanceText.current()
    val arrival = route.arrivalClockText.current()
    val turnDistance = route.distanceToNextTurnText.current()?.takeIf {
        turn != null && it.source == turn.source &&
            it.capturedAtElapsedRealtimeMs == turn.capturedAtElapsedRealtimeMs
    }
    val lead = turn ?: eta ?: remaining ?: arrival
    if (lead != null) {
        val provider = when (lead.source) {
            ContextSource.GOOGLE_MAPS_NOTIFICATION, ContextSource.GOOGLE_MAPS_ACCESSIBILITY -> "Google Maps"
            ContextSource.WAZE_NOTIFICATION, ContextSource.WAZE_ACCESSIBILITY -> "Waze"
            else -> return null
        }
        val detail = if (turn != null) listOfNotNull(turnDistance?.value, turn.value).joinToString(" · ")
        else listOfNotNull(eta?.takeIf { it.source == lead.source }?.value?.let { "$it min" },
            remaining?.takeIf { it.source == lead.source }?.value,
            arrival?.takeIf { it.source == lead.source }?.value?.let { "Llegada $it" }).joinToString(" · ")
        if (detail.isNotBlank()) return OverlayRouteSummary(provider, detail)
    }
    if (snapshot.location.current() == null) return null
    val goal = snapshot.target.current()?.value ?: return null
    val meters = snapshot.straightLineMeters(nowElapsed) ?: return null
    val shown = if (meters < 1_000) "${((meters / 100).roundToInt().coerceAtLeast(1)) * 100} m"
        else "${((meters / 100).roundToInt() / 10.0).toString().replace('.', ',')} km"
    return OverlayRouteSummary("APP", "${goal.label} · $shown", ownGps = true)
}
