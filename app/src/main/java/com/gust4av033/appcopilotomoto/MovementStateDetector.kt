package com.gust4av033.appcopilotomoto

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

enum class MovementState { DETENIDO, LENTO, EN_MARCHA }
enum class MovementEventKind { STOP_START, STOP_END, SLOW_START, SLOW_END }
data class MovementEvent(val kind: MovementEventKind, val epochMs: Long, val durationMs: Long,
    val distanceMeters: Double, val dayOfWeek: DayOfWeek, val averageGpsAccuracyMeters: Float)

/** Pure shadow classifier. It has no path to speech or prompting. */
class MovementStateDetector(private val zone: ZoneId = ZoneId.systemDefault()) {
    companion object {
        const val MAX_ACCURACY_METERS = 25f
        const val STOP_BELOW_MPS = 1.2f
        const val SLOW_BELOW_MPS = 4.2f
        const val MOVING_FROM_MPS = 4.5f
        const val STOP_HOLD_MS = 5_000L
        const val SLOW_HOLD_MS = 20_000L
    }
    var state = MovementState.EN_MARCHA
        private set
    private var candidate: MovementState? = null
    private var candidateSince = 0L
    private var stateSince = 0L
    private var distanceInState = 0.0
    private var accuracySum = 0.0
    private var accuracyCount = 0
    private var last: LocationSample? = null

    fun accept(sample: LocationSample): List<MovementEvent> {
        val accuracy = sample.horizontalAccuracyMeters ?: return emptyList()
        if (accuracy > MAX_ACCURACY_METERS || sample.speedMetersPerSec == null) return emptyList()
        val prior = last
        if (prior != null && sample.elapsedRealtimeMs <= prior.elapsedRealtimeMs) return emptyList()
        if (prior != null && stateSince > 0) distanceInState += distanceMeters(prior.latitude, prior.longitude,
            sample.latitude, sample.longitude).coerceAtMost(100.0)
        last = sample
        accuracySum += accuracy
        accuracyCount++
        val speed = sample.speedMetersPerSec
        val observed = when {
            speed < STOP_BELOW_MPS -> MovementState.DETENIDO
            speed in STOP_BELOW_MPS..SLOW_BELOW_MPS -> MovementState.LENTO
            speed >= MOVING_FROM_MPS -> MovementState.EN_MARCHA
            else -> state // hysteresis band 4.2–4.5 m/s
        }
        if (observed == state) { candidate = null; return emptyList() }
        if (candidate != observed) { candidate = observed; candidateSince = sample.elapsedRealtimeMs; return emptyList() }
        val hold = when (observed) { MovementState.DETENIDO -> STOP_HOLD_MS
            MovementState.LENTO -> SLOW_HOLD_MS; MovementState.EN_MARCHA -> 0L }
        if (sample.elapsedRealtimeMs - candidateSince < hold) return emptyList()
        val duration = if (stateSince == 0L) 0L else sample.elapsedRealtimeMs - stateSince
        val day = Instant.ofEpochMilli(sample.epochMs).atZone(zone).dayOfWeek
        val quality = if (accuracyCount == 0) accuracy else (accuracySum / accuracyCount).toFloat()
        val events = buildList {
            if (state == MovementState.DETENIDO) add(MovementEvent(MovementEventKind.STOP_END,
                sample.epochMs, duration, distanceInState, day, quality))
            if (state == MovementState.LENTO) add(MovementEvent(MovementEventKind.SLOW_END,
                sample.epochMs, duration, distanceInState, day, quality))
            if (observed == MovementState.DETENIDO) add(MovementEvent(MovementEventKind.STOP_START,
                sample.epochMs, 0, 0.0, day, quality))
            if (observed == MovementState.LENTO) add(MovementEvent(MovementEventKind.SLOW_START,
                sample.epochMs, 0, 0.0, day, quality))
        }
        state = observed
        stateSince = sample.elapsedRealtimeMs
        candidate = null
        distanceInState = 0.0; accuracySum = 0.0; accuracyCount = 0
        return events
    }
}
