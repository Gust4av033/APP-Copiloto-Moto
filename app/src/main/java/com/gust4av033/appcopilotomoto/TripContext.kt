package com.gust4av033.appcopilotomoto

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ContextSource { ANDROID_GPS, WAZE_NOTIFICATION, WAZE_ACCESSIBILITY,
    GOOGLE_MAPS_NOTIFICATION, GOOGLE_MAPS_ACCESSIBILITY, SAVED_DESTINATION_COORDS,
    APP_SESSION, APP_STRAIGHT_LINE, APP_ROAD_ROUTE, MEDIA_SESSION, DEVICE_STATUS, REPLAY_FIXTURE }
enum class Freshness { FRESH, STALE, EXPIRED }

/** All ages use the same monotonic clock domain, including replay fixtures. */
data class ContextValue<T>(val value: T, val source: ContextSource, val capturedAtEpochMs: Long,
    val capturedAtElapsedRealtimeMs: Long, val confidence: Float, val ttlMs: Long,
    val isSimulated: Boolean = false) {
    init { require(confidence in 0f..1f && ttlMs > 0) }
    fun ageMs(elapsedRealtimeMs: Long): Long = (elapsedRealtimeMs - capturedAtElapsedRealtimeMs).coerceAtLeast(0)
    fun freshness(elapsedRealtimeMs: Long): Freshness = when {
        elapsedRealtimeMs < capturedAtElapsedRealtimeMs || ageMs(elapsedRealtimeMs) >= ttlMs -> Freshness.EXPIRED
        ageMs(elapsedRealtimeMs) * 2 >= ttlMs -> Freshness.STALE
        else -> Freshness.FRESH
    }
}

data class TargetDestination(val label: String, val latitude: Double, val longitude: Double) {
    init { require(latitude in -90.0..90.0 && longitude in -180.0..180.0) }
}

data class LocationSample(val epochMs: Long, val elapsedRealtimeMs: Long, val latitude: Double,
    val longitude: Double, val altitude: Double?, val speedMetersPerSec: Float?,
    val speedAccuracyMetersPerSec: Float?, val bearing: Float?, val bearingAccuracyDegrees: Float?,
    val horizontalAccuracyMeters: Float?, val provider: String, val isSimulated: Boolean)

/** Quality gate shared by live context and local trip history. */
fun LocationSample.hasTripPositionQuality(): Boolean =
    !isSimulated && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        horizontalAccuracyMeters?.let { it.isFinite() && it in 0f..50f } == true

fun LocationSample.usableForTripContext(nowElapsedMs: Long): Boolean =
    hasTripPositionQuality() && nowElapsedMs - elapsedRealtimeMs in 0L until 10_000L

fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val lat1 = Math.toRadians(aLat); val lat2 = Math.toRadians(bLat)
    val dLat = lat2 - lat1; val dLon = Math.toRadians(bLon - aLon)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 6371000.0 * 2 * atan2(sqrt(h), sqrt((1 - h).coerceAtLeast(0.0)))
}

fun bearingDegrees(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val lat1 = Math.toRadians(aLat); val lat2 = Math.toRadians(bLat)
    val dLon = Math.toRadians(bLon - aLon)
    return (Math.toDegrees(atan2(sin(dLon) * cos(lat2), cos(lat1) * sin(lat2) -
        sin(lat1) * cos(lat2) * cos(dLon))) + 360) % 360
}

enum class EtaSource { WAZE, GOOGLE_MAPS, PROPIA_ESTIMADA }
data class EtaObservation(val source: EtaSource, val remainingMinutes: Int,
    val capturedAtEpochMs: Long, val capturedAtElapsedRealtimeMs: Long, val ttlMs: Long) {
    fun valid(nowElapsedMs: Long) = nowElapsedMs >= capturedAtElapsedRealtimeMs &&
        nowElapsedMs - capturedAtElapsedRealtimeMs < ttlMs
}

/** Previous navigator data is useful context, but never a current ETA. */
data class LastObservedEta(val source: EtaSource, val remainingMinutes: Int,
    val observedAtElapsedMs: Long) {
    companion object { const val MAX_AGE_MS = 120_000L }
    fun valid(nowElapsedMs: Long) = observedAtElapsedMs > 0 && nowElapsedMs >= observedAtElapsedMs &&
        nowElapsedMs - observedAtElapsedMs < MAX_AGE_MS
    fun ageMs(nowElapsedMs: Long) = (nowElapsedMs - observedAtElapsedMs).coerceAtLeast(0)
}

data class NavigationContext(
    val activeProvider: ContextValue<String>? = null,
    val nextTurnInstruction: ContextValue<String>? = null,
    val distanceToNextTurnText: ContextValue<String>? = null,
    val distanceToNextTurnMeters: ContextValue<Int>? = null,
    val currentOrTargetStreet: ContextValue<String>? = null,
    val etaText: ContextValue<String>? = null,
    val remainingMinutes: ContextValue<Int>? = null,
    val remainingDistanceText: ContextValue<String>? = null,
    val routeAlertText: ContextValue<String>? = null,
    val etaObservation: EtaObservation? = null
)

private fun <T> best(a: ContextValue<T>?, b: ContextValue<T>?, now: Long): ContextValue<T>? =
    listOfNotNull(a, b).filter { it.freshness(now) != Freshness.EXPIRED }
        .maxWithOrNull(compareBy<ContextValue<T>> { if (it.freshness(now) == Freshness.FRESH) 1 else 0 }
            .thenBy { it.confidence }.thenBy { it.capturedAtElapsedRealtimeMs })

fun fuseNavigation(a: NavigationContext, b: NavigationContext, now: Long): NavigationContext = NavigationContext(
    best(a.activeProvider,b.activeProvider,now), best(a.nextTurnInstruction,b.nextTurnInstruction,now),
    best(a.distanceToNextTurnText,b.distanceToNextTurnText,now), best(a.distanceToNextTurnMeters,b.distanceToNextTurnMeters,now),
    best(a.currentOrTargetStreet,b.currentOrTargetStreet,now), best(a.etaText,b.etaText,now),
    best(a.remainingMinutes,b.remainingMinutes,now), best(a.remainingDistanceText,b.remainingDistanceText,now),
    best(a.routeAlertText,b.routeAlertText,now),
    listOfNotNull(a.etaObservation,b.etaObservation).filter { it.valid(now) }.maxByOrNull { it.capturedAtElapsedRealtimeMs })

data class MediaTripContext(val track: ContextValue<String>?, val artist: ContextValue<String>?,
    val app: ContextValue<String>?, val playback: ContextValue<String>?)
data class DeviceTripContext(val batteryPercent: ContextValue<Int>?, val charging: ContextValue<Boolean>?,
    val bluetoothAudioConfirmed: ContextValue<Boolean>?)
/** Geometry to an explicit coordinate, independent of roads and external navigation. */
data class OwnTargetGeometry(val distanceMeters: Double, val bearingDegrees: Double)
data class TripContextSnapshot(val navigation: NavigationContext = NavigationContext(),
    val location: ContextValue<LocationSample>? = null, val target: ContextValue<TargetDestination>? = null,
    val media: MediaTripContext? = null, val device: DeviceTripContext? = null,
    val sessionDurationMs: ContextValue<Long>? = null,
    val lastObservedEta: LastObservedEta? = null) {
    fun ownTargetGeometry(now: Long): ContextValue<OwnTargetGeometry>? {
        val gps = location?.takeIf { it.source == ContextSource.ANDROID_GPS && !it.isSimulated &&
            it.freshness(now) != Freshness.EXPIRED && it.value.usableForTripContext(now) } ?: return null
        val destination = target?.takeIf { !it.isSimulated &&
            it.freshness(now) != Freshness.EXPIRED } ?: return null
        val remainingMs = minOf(gps.ttlMs - gps.ageMs(now), destination.ttlMs - destination.ageMs(now))
        if (remainingMs <= 0) return null
        val point = gps.value
        val goal = destination.value
        return ContextValue(OwnTargetGeometry(
            distanceMeters(point.latitude, point.longitude, goal.latitude, goal.longitude),
            bearingDegrees(point.latitude, point.longitude, goal.latitude, goal.longitude)),
            ContextSource.APP_STRAIGHT_LINE, point.epochMs + (now - point.elapsedRealtimeMs),
            now, minOf(gps.confidence, destination.confidence), remainingMs)
    }
    fun straightLineMeters(now: Long): Double? {
        return ownTargetGeometry(now)?.value?.distanceMeters
    }
    fun targetBearingDegrees(now: Long): Double? {
        return ownTargetGeometry(now)?.value?.bearingDegrees
    }
}
