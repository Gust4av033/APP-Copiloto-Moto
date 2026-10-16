package com.gust4av033.appcopilotomoto

/** Domain contract for a future local or self-hosted road engine; no network is used here. */
data class RoutePoint(val latitude: Double, val longitude: Double) {
    init { require(latitude in -90.0..90.0 && longitude in -180.0..180.0) }
}

enum class RouteVehicleProfile { MOTORCYCLE }

data class RoadRouteRequest(val sessionId: String, val origin: RoutePoint,
    val destination: TargetDestination, val departureEpochMs: Long,
    val requestedAtElapsedMs: Long, val profile: RouteVehicleProfile = RouteVehicleProfile.MOTORCYCLE)

data class RoadRouteManeuver(val instruction: String, val point: RoutePoint,
    val distanceFromStartMeters: Int) {
    init { require(instruction.isNotBlank() && distanceFromStartMeters >= 0) }
}

/** A route is real only after an engine returns road geometry and its own provenance. */
data class RoadRoutePlan(val sessionId: String, val destination: TargetDestination,
    val engineId: String, val mapVersion: String, val shape: List<RoutePoint>,
    val distanceMeters: Int, val durationSeconds: Int, val maneuvers: List<RoadRouteManeuver>,
    val calculatedAtEpochMs: Long, val calculatedAtElapsedMs: Long,
    val confidence: Float, val ttlMs: Long, val isSimulated: Boolean = false) {
    init {
        require(sessionId.isNotBlank() && engineId.isNotBlank() && mapVersion.isNotBlank())
        require(shape.size >= 2 && distanceMeters > 0 && durationSeconds > 0)
        require(maneuvers.all { it.distanceFromStartMeters <= distanceMeters })
        require(calculatedAtEpochMs > 0 && calculatedAtElapsedMs > 0)
        require(confidence in 0f..1f && ttlMs > 0)
    }

    fun observation(): ContextValue<RoadRoutePlan> = ContextValue(this,
        ContextSource.APP_ROAD_ROUTE, calculatedAtEpochMs, calculatedAtElapsedMs,
        confidence, ttlMs, isSimulated)
}

/** Valhalla on-device or a self-hosted engine can implement this without changing callers. */
interface RoadRouteEngine {
    val id: String
    suspend fun plan(request: RoadRouteRequest): Result<RoadRoutePlan>
}

/** Never infer APP's road destination from a Maps/Waze notification or dispatch. */
fun roadRouteRequest(trip: TripNavigationContext, snapshot: TripContextSnapshot,
    nowElapsedMs: Long, nowEpochMs: Long): RoadRouteRequest? {
    val session = trip.sessionId ?: return null
    val destination = trip.ownDestination ?: return null
    val fix = snapshot.location?.takeIf { it.source == ContextSource.ANDROID_GPS && !it.isSimulated &&
        it.freshness(nowElapsedMs) != Freshness.EXPIRED }?.value ?: return null
    if (!fix.usableForTripContext(nowElapsedMs) ||
        (fix.horizontalAccuracyMeters ?: Float.POSITIVE_INFINITY) > 25f) return null
    return RoadRouteRequest(session, RoutePoint(fix.latitude, fix.longitude), destination,
        nowEpochMs, nowElapsedMs)
}
