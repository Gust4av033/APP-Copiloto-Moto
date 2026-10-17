package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class PointOfInterestTest {
    @Test fun fakeProvidersPreserveProvenanceAndDoNotClaimRoute() {
        val point = PoiPoint(13.7, -89.2)
        val query = PoiQuery(PointOfInterestCategory.FARMACIA, near = point)
        val fake = object : PlacesProvider {
            override suspend fun search(query: PoiQuery) = ContextValue(
                listOf(PoiResult("Farmacia de prueba", point)), ContextSource.REPLAY_FIXTURE,
                100L, 100L, 1f, 1_000L, isSimulated = true)
        }
        val result = complete { fake.search(query) }
        assertEquals(ContextSource.REPLAY_FIXTURE, result?.source)
        assertEquals(Freshness.FRESH, result?.freshness(100L))
        assertEquals("Farmacia de prueba", result?.value?.single()?.name)
        val weather = object : WeatherProvider {
            override suspend fun at(latitude: Double, longitude: Double, epochMs: Long) =
                ContextValue(WeatherObservation(24.0, "despejado"), ContextSource.REPLAY_FIXTURE,
                    epochMs, 100L, 1f, 1_000L, isSimulated = true)
        }
        assertEquals("despejado", complete { weather.at(point.latitude, point.longitude, 100L) }?.value?.condition)
        assertEquals(Freshness.EXPIRED, result?.freshness(1_100L))
        assertEquals(2, PoiQuery(PointOfInterestCategory.CAJERO,
            route = listOf(point, PoiPoint(13.8, -89.3))).route.size)
        assertEquals(PointOfInterestCategory.FARMACIA, query.category)
        assertEquals(NavigationRequestKind.SEARCH, NavigationDispatch("waze", NavigationRequestKind.SEARCH,
            "farmacia", 100L, category = query.category).kind)
        assertNull(NavigationDispatch("waze", NavigationRequestKind.SEARCH,
            "farmacia", 100L, category = query.category).target)
    }

    @Test(expected = IllegalArgumentException::class) fun poiQueryNeedsOneGeometry() {
        PoiQuery(PointOfInterestCategory.CAJERO)
    }

    private fun <T> complete(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return checkNotNull(result).getOrThrow()
    }
}
