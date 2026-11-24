package com.gust4av033.appcopilotomoto

data class TripTrackPoint(val latitude: Double, val longitude: Double, val elapsedMs: Long)

/** The map observes the recorder. It never estimates a road or modifies the rider's odometer. */
data class TripMapSession(
    val sessionId: String? = null,
    val startedAtElapsedMs: Long = 0,
    val endedAtElapsedMs: Long? = null,
    val lastFix: ContextValue<LocationSample>? = null,
    val distanceMeters: Double? = null,
    val segments: List<List<TripTrackPoint>> = emptyList(),
    val simplified: Boolean = false
) {
    fun durationMs(now: Long): Long? = sessionId?.let {
        ((endedAtElapsedMs ?: now) - startedAtElapsedMs).coerceAtLeast(0)
    }
    fun currentFix(now: Long): LocationSample? = lastFix?.takeIf {
        endedAtElapsedMs == null && it.freshness(now) == Freshness.FRESH && !it.isSimulated
    }?.value
    fun speedKmh(now: Long): Float? = currentFix(now)?.let { fix ->
        fix.speedMetersPerSec?.takeIf { it.isFinite() && it in 0f..70f &&
            fix.speedAccuracyMetersPerSec?.let { accuracy -> accuracy.isFinite() && accuracy in 0f..3f } == true
        }?.times(3.6f)
    }
}

/** Receives only samples already accepted by TripRepository and its cumulative distance. */
class TripTrackAccumulator {
    companion object { const val MAX_POINTS = 4096; const val MAX_SEGMENTS = 128 }
    var snapshot = TripMapSession()
        private set
    private var previous: LocationSample? = null

    fun start(sessionId: String, elapsedMs: Long) {
        previous = null
        snapshot = TripMapSession(sessionId = sessionId, startedAtElapsedMs = elapsedMs)
    }
    fun acceptRecorded(sample: LocationSample, totalMeters: Double) {
        if (snapshot.sessionId == null || snapshot.endedAtElapsedMs != null || !sample.hasTripPositionQuality() ||
            sample.elapsedRealtimeMs < snapshot.startedAtElapsedMs ||
            previous?.let { sample.elapsedRealtimeMs <= it.elapsedRealtimeMs } == true ||
            !totalMeters.isFinite() || totalMeters < (snapshot.distanceMeters ?: 0.0)) return
        val prior = previous
        var segments = snapshot.segments
        var simplified = snapshot.simplified
        // A positive delta is the recorder's confirmation, not a second distance algorithm.
        if (prior != null && totalMeters > (snapshot.distanceMeters ?: 0.0) &&
            sample.elapsedRealtimeMs - prior.elapsedRealtimeMs <= 15_000 &&
            kotlin.math.abs(sample.longitude - prior.longitude) <= 180) {
            val point = sample.trackPoint()
            segments = if (segments.lastOrNull()?.lastOrNull()?.elapsedMs == prior.elapsedRealtimeMs)
                segments.dropLast(1) + listOf(segments.last() + point)
            else segments + listOf(listOf(prior.trackPoint(), point))
        }
        if (segments.size > MAX_SEGMENTS) { segments = segments.takeLast(MAX_SEGMENTS); simplified = true }
        if (segments.sumOf { it.size } > MAX_POINTS) {
            segments = segments.map { points -> points.filterIndexed { i, _ -> i % 2 == 0 || i == points.lastIndex } }
            simplified = true
        }
        snapshot = snapshot.copy(lastFix = ContextValue(sample, ContextSource.ANDROID_GPS, sample.epochMs,
            sample.elapsedRealtimeMs, if ((sample.horizontalAccuracyMeters ?: 50f) <= 10) .9f else .7f, 10_000),
            distanceMeters = totalMeters, segments = segments, simplified = simplified)
        previous = sample
    }
    fun finish(elapsedMs: Long) {
        if (snapshot.sessionId != null && snapshot.endedAtElapsedMs == null)
            snapshot = snapshot.copy(endedAtElapsedMs = elapsedMs.coerceAtLeast(snapshot.startedAtElapsedMs))
    }
    fun clear() { previous = null; snapshot = TripMapSession() }
    private fun LocationSample.trackPoint() = TripTrackPoint(latitude, longitude, elapsedRealtimeMs)
}
