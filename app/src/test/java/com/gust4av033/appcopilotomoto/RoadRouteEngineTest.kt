package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RoadRouteEngineTest {
    private val home = TargetDestination("Casa", 13.70, -89.20)
    private val work = TargetDestination("Trabajo", 13.71, -89.19)

    private fun snapshot(elapsed: Long = 1_000, accuracy: Float = 5f,
        simulated: Boolean = false, source: ContextSource = ContextSource.ANDROID_GPS,
        simulatedObservation: Boolean = false): TripContextSnapshot {
        val fix = LocationSample(1_700_000_000_000, elapsed, 13.69, -89.21,
            null, null, null, null, null, accuracy, "gps", simulated)
        return TripContextSnapshot(location = ContextValue(fix, source, fix.epochMs,
            elapsed, .95f, 10_000, simulatedObservation))
    }

    @Test fun externalDispatchCannotCreateAnOwnRoadRouteRequest() {
        val dispatched = NavigationDispatch("google_maps", NavigationRequestKind.FAVORITE,
            "Casa", 1_700_000_000_000, home, 1_000)
        val trip = TripNavigationContext(sessionId = "trip-1", lastDispatch = dispatched)
        assertNull(roadRouteRequest(trip, snapshot(), 2_000, 1_700_000_001_000))

        val chosen = trip.chooseOwnDestination("trip-1", work)
        val request = roadRouteRequest(chosen, snapshot(), 2_000, 1_700_000_001_000)
        assertNotNull(request)
        assertEquals(work, request?.destination)
        assertEquals(RoutePoint(13.69, -89.21), request?.origin)
        assertEquals(RouteVehicleProfile.MOTORCYCLE, request?.profile)
        assertEquals(home, chosen.lastDispatch?.target)
        assertEquals(work, chosen.chooseOwnDestination("trip-old", home).ownDestination)
        assertNull(roadRouteRequest(TripNavigationContext(), snapshot(), 2_000, 1_700_000_001_000))
    }

    @Test fun requestRequiresRecentPreciseRealGps() {
        val trip = TripNavigationContext(sessionId = "trip-1", ownDestination = home)
        assertNull(roadRouteRequest(trip, snapshot(elapsed = 1_000), 12_000, 1_700_000_012_000))
        assertNull(roadRouteRequest(trip, snapshot(accuracy = 40f), 2_000, 1_700_000_001_000))
        assertNull(roadRouteRequest(trip, snapshot(simulated = true), 2_000, 1_700_000_001_000))
        assertNull(roadRouteRequest(trip, snapshot(simulatedObservation = true),
            2_000, 1_700_000_001_000))
        assertNull(roadRouteRequest(trip, snapshot(source = ContextSource.REPLAY_FIXTURE),
            2_000, 1_700_000_001_000))
    }

    @Test fun roadPlanKeepsItsOwnSourceAndExpiry() {
        val plan = RoadRoutePlan("trip-1", home, "local-osm", "map-2026-10",
            listOf(RoutePoint(13.69, -89.21), RoutePoint(13.70, -89.20)),
            1_800, 300, emptyList(), 1_700_000_001_000, 2_000, .8f, 30_000)
        val observed = plan.observation()
        assertEquals(ContextSource.APP_ROAD_ROUTE, observed.source)
        assertEquals("local-osm", observed.value.engineId)
        assertEquals(Freshness.EXPIRED, observed.freshness(32_000))
    }
}
