package com.gust4av033.appcopilotomoto

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeatherProviderTest {
    private val epoch = 1_800_000_000_000L
    private fun fixture() = JSONObject().put("timezone", "America/El_Salvador")
        .put("current_units", JSONObject().put("temperature_2m", "°C").put("wind_speed_10m", "km/h"))
        .put("current", JSONObject().put("time", epoch / 1000).put("temperature_2m", 24.5).put("weather_code", 61).put("wind_speed_10m", 12))
        .put("hourly_units", JSONObject().put("temperature_2m", "°C").put("precipitation_probability", "%"))
        .put("hourly", JSONObject().put("time", JSONArray().put(epoch / 1000 - 3600).put(epoch / 1000 + 3600).put(epoch / 1000 + 7200))
            .put("temperature_2m", JSONArray().put(23).put(25).put(JSONObject.NULL))
            .put("precipitation_probability", JSONArray().put(20).put(80).put(JSONObject.NULL))
            .put("weather_code", JSONArray().put(0).put(61).put(JSONObject.NULL)))
    @Before fun reset() {
        RuntimeEnvironment.getApplication().getSharedPreferences("ui_settings", 0).edit().clear().commit()
        WeatherMemory.clear()
    }
    @Test fun parsesUnitsTimesAndNullsWithoutInventingZeroes() {
        val result = OpenMeteoWeatherProvider.parse(fixture().toString(), epoch)!!
        assertEquals(24.5, result.temperatureCelsius!!, 0.0)
        assertEquals(61, result.weatherCode)
        assertEquals(2, result.hourly.size)
        assertEquals(80, result.hourly.first().rainProbability)
        assertNull(result.hourly.last().temperatureCelsius)
        assertNull(result.hourly.last().rainProbability)
        assertNull(OpenMeteoWeatherProvider.parse(fixture().toString(), epoch + 61 * 60_000))
        assertNull(OpenMeteoWeatherProvider.parse(fixture().toString(), epoch - 6 * 60_000))
        val wrongUnit = fixture().apply { getJSONObject("current_units").put("temperature_2m", "°F") }
        assertNull(OpenMeteoWeatherProvider.parse(wrongUnit.toString(), epoch))
        assertNull(OpenMeteoWeatherProvider.parse("not json", epoch))
    }
    @Test fun providerUsesRoundedCoordinatesAndProvenanceAndChecksRevocationAfterResponse() = runBlocking {
        var allowed = true
        var revokeInTransport = false
        var requests = 0
        val client = OpenMeteoWeatherProvider.weatherClient { allowed }.newBuilder().addInterceptor { chain ->
            requests++
            assertEquals("13.69", chain.request().url.queryParameter("latitude"))
            assertEquals("-89.22", chain.request().url.queryParameter("longitude"))
            assertFalse(chain.request().url.toString().contains("label"))
            if (revokeInTransport) allowed = false
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("identified fixture")
                .body(fixture().toString().toResponseBody()).build()
        }.build()
        val provider = OpenMeteoWeatherProvider({ allowed }, client) { 5000 }
        val result = provider.at(13.69123, -89.22123, epoch)!!
        assertEquals(ContextSource.OPEN_METEO, result.source)
        assertEquals(5000L, result.capturedAtElapsedRealtimeMs)
        assertEquals(Freshness.EXPIRED, result.freshness(5000 + OpenMeteoWeatherProvider.TTL_MS))
        allowed = false
        assertNull(provider.at(13.69123, -89.22123, epoch)); assertEquals(1, requests)
        allowed = true; revokeInTransport = true
        assertNull(provider.at(13.69123, -89.22123, epoch)); assertEquals(2, requests)
    }
    @Test fun controllerSeparatesTargetsCachesAndNeverRequestsWhileHiddenOrDisabled() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        var now = 100_000L
        var calls = 0
        val controller = WeatherController(context, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), { now }, { epoch }) { gate ->
            object : WeatherProvider {
                override suspend fun at(latitude: Double, longitude: Double, epochMs: Long): ContextValue<WeatherObservation>? {
                    assertTrue(gate()); calls++
                    return ContextValue(WeatherObservation(latitude, "fixture"), ContextSource.OPEN_METEO, epochMs, now, .7f, OpenMeteoWeatherProvider.TTL_MS)
                }
            }
        }
        val here = TargetDestination("", 13.69, -89.22)
        val goal = TargetDestination("Destino de prueba", 14.0, -89.0)
        controller.foreground(true); controller.update(true, here, goal)
        assertEquals(0, calls)
        controller.enabled(true); assertEquals(1, calls)
        controller.update(true, here, goal); assertEquals(1, calls)
        controller.select(WeatherArea.DESTINATION); assertEquals(2, calls)
        assertEquals(14.0, controller.state.usable!!.temperatureCelsius!!, 0.0)
        controller.select(WeatherArea.HERE); assertEquals(2, calls)
        controller.update(true, null, goal); assertNull(controller.state.usable)
        controller.foreground(false)
        now += OpenMeteoWeatherProvider.TTL_MS
        controller.update(true, here, goal); assertEquals(2, calls)
        assertNull(controller.state.usable)
        controller.foreground(true); assertEquals(3, calls)
        controller.enabled(false); assertNull(controller.state.data)
        assertNull(WeatherMemory.get(WeatherMemory.key(here)))
        controller.destroy()
    }
    @Test fun cancellationCannotRestoreAResponseAfterConsentWasWithdrawn() = runBlocking {
        val response = CompletableDeferred<Unit>()
        val controller = WeatherController(RuntimeEnvironment.getApplication(),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), { 100_000 }, { epoch }) {
            object : WeatherProvider {
                override suspend fun at(latitude: Double, longitude: Double, epochMs: Long): ContextValue<WeatherObservation> {
                    withContext(NonCancellable) { response.await() }
                    return ContextValue(WeatherObservation(25.0, "late fixture"), ContextSource.OPEN_METEO, epochMs, 100_000, .7f, 60_000)
                }
            }
        }
        controller.foreground(true); controller.enabled(true)
        controller.update(true, TargetDestination("", 13.0, -89.0), null)
        assertTrue(controller.state.loading)
        controller.enabled(false); response.complete(Unit); yield()
        assertFalse(controller.state.loading); assertNull(controller.state.data)
        controller.destroy()
    }
}
