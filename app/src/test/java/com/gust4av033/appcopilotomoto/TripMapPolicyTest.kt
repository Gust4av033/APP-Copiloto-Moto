package com.gust4av033.appcopilotomoto

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.gust4av033.appcopilotomoto.presentation.components.TripMapPaint
import com.gust4av033.appcopilotomoto.presentation.components.TripMapStyle
import com.gust4av033.appcopilotomoto.presentation.components.tripMapHeadingDegrees
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripMapPolicyTest {
    @Test fun plannedRouteIsEmptyUntilSuppliedAndDistinctFromRecordedTrace() {
        val paint = TripMapPaint("#050C1D", "#2F6BFF", "#79DDFF", "#888888", "#050C1D", 4f, 7f, 2f, .82f, "#334455", "#668899")
        val style = JSONObject(TripMapStyle.json(false, paint))
        assertEquals(0, style.getJSONObject("sources").getJSONObject(TripMapStyle.GUIDANCE)
            .getJSONObject("data").getJSONArray("features").length())
        val array = style.getJSONArray("layers")
        val layers = (0 until array.length()).map { array.getJSONObject(it) }
        val trace = layers.single { it.getString("id") == TripMapStyle.ROUTE }.getJSONObject("paint")
        val route = layers.single { it.getString("id") == TripMapStyle.GUIDANCE }.getJSONObject("paint")
        assertTrue(trace.has("line-dasharray")); assertFalse(route.has("line-dasharray"))
        assertNotEquals(trace.getString("line-color"), route.getString("line-color"))
        assertTrue(route.getDouble("line-width") > trace.getDouble("line-width"))
        assertTrue(layers.indexOfFirst { it.getString("id") == TripMapStyle.GUIDANCE_CASING } <
            layers.indexOfFirst { it.getString("id") == TripMapStyle.GUIDANCE })
        assertTrue(layers.indexOfFirst { it.getString("id") == TripMapStyle.GUIDANCE } <
            layers.indexOfFirst { it.getString("id") == TripMapStyle.MOTO })
        val points = listOf(com.gust4av033.appcopilotomoto.presentation.components.MapRoutePoint(14.6, -90.5),
            com.gust4av033.appcopilotomoto.presentation.components.MapRoutePoint(14.7, -90.4))
        assertEquals("LineString", JSONObject(TripMapStyle.plannedRoute(points)).getJSONObject("geometry").getString("type"))
        assertTrue(JSONObject(TripMapStyle.plannedRoute(emptyList())).has("features"))
        assertTrue(JSONObject(TripMapStyle.plannedRoute(points + points[0].copy(latitude = Double.NaN))).has("features"))
    }
    @Test fun roadDirectionDoesNotGuessUnknownOrBidirectionalRoads() {
        assertEquals(0, TripMapStyle.roadDirection(1))
        assertEquals(180, TripMapStyle.roadDirection(-1))
        assertNull(TripMapStyle.roadDirection(0))
        assertNull(TripMapStyle.roadDirection(2))
        val base = RuntimeEnvironment.getApplication().assets.open("map/liberty.json").bufferedReader().use { it.readText() }
        val paint = TripMapPaint("#050C1D", "#2F6BFF", "#79DDFF", "#888888", "#050C1D", 4f, 7f, 2f, .82f, "#334455", "#668899")
        val layers = JSONObject(TripMapStyle.json(true, paint, base)).getJSONArray("layers")
        val arrows = (0 until layers.length()).map { layers.getJSONObject(it) }.filter { it.getString("id").startsWith("app-road-direction-") }
        assertEquals(2, arrows.size)
        for (layer in arrows) {
            assertEquals("transportation", layer.getString("source-layer"))
            assertFalse(layer.getJSONObject("layout").getBoolean("icon-keep-upright"))
            assertFalse(layer.getJSONObject("layout").getBoolean("icon-allow-overlap"))
            assertTrue(layer.getJSONArray("filter").toString().contains("oneway"))
        }
    }
    @Test fun cameraHeadingRequiresFreshUsableMotionAndAccurateBearing() {
        fun fix(speed: Float?, accuracy: Float?, bearing: Float?) = LocationSample(
            1_000, 1_000, 14.6, -90.5, null, speed, null, bearing, accuracy, 3f, "test-fixture", false
        )
        assertEquals(45.0, tripMapHeadingDegrees(fix(8f, 20f, 45f))!!, 0.0)
        assertNull(tripMapHeadingDegrees(fix(1f, 10f, 45f)))
        assertNull(tripMapHeadingDegrees(fix(8f, 31f, 45f)))
        assertNull(tripMapHeadingDegrees(fix(8f, 10f, null)))
    }

    @Test fun consentIsOffByDefaultAndSurvivesRecreationWithoutEnablingGps() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ui_settings", 0).edit().clear().commit()
        assertFalse(TripMapPreferences(context).onlineEnabled())
        context.getSharedPreferences("ui_settings", 0).edit()
            .putBoolean("trip_map_online_consent_v1", true).commit()
        assertFalse(TripMapPreferences(context).onlineEnabled())
        TripMapPreferences(context).setOnlineEnabled(true)
        assertTrue(TripMapPreferences(context).onlineEnabled())
        assertFalse(UserSettingsStore(context).tripLocationEnabled())
        TripMapPreferences(context).setOnlineEnabled(false)
        assertFalse(TripMapPreferences(context).onlineEnabled())
    }
    @Test fun onlyHttpsGetTilesInTheAllowedZoomRangeCanLeaveTheDevice() {
        val allowed = "https://tiles.openfreemap.org/planet/20261122_124456_pt/14/123/456.pbf"
        for (url in listOf(
            allowed,
            "https://tiles.openfreemap.org/planet",
            "https://tiles.openfreemap.org/natural_earth/ne2sr/6/12/20.png",
            "https://tiles.openfreemap.org/sprites/ofm_f384/ofm.json",
            "https://tiles.openfreemap.org/fonts/NotoSans/0-255.pbf"
        )) assertTrue(url, TripTilePolicy.allows(url.toHttpUrl()))
        for (url in listOf(
            "http://tiles.openfreemap.org/planet",
            "https://example.com/planet",
            "https://tiles.openfreemap.org/planet/20261122_124456_pt/15/123/456.pbf",
            "https://tile.openstreetmap.org/16/16293/30082.png",
            "https://tiles.openfreemap.org/planet/20261122_124456_pt/14/123/456.pbf?secret=1",
            "https://tiles.openfreemap.org/planet/20261122_124456_pt/14/123/456.pbf#private",
            "https://tiles.openfreemap.org/../admin",
            "https://tiles.openfreemap.org:444/planet"
        )) {
            assertFalse(url, TripTilePolicy.allows(url.toHttpUrl()))
        }
        var canRequest = false
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor(TripTileInterceptor { canRequest })
            .addInterceptor { chain ->
                requests++
                assertEquals(TripTilePolicy.USER_AGENT, chain.request().header("User-Agent"))
                assertNull(chain.request().header("Cache-Control")); assertNull(chain.request().header("Pragma"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .header("Cache-Control", "max-age=604800").body("fixture tile".toResponseBody()).build()
            }.build()
        val request = Request.Builder().url(allowed).header("Cache-Control", "no-cache").header("Pragma", "no-cache").build()
        assertTrue(runCatching { client.newCall(request).execute().close() }.exceptionOrNull() is IOException)
        assertEquals(0, requests)
        canRequest = true
        client.newCall(request).execute().use { assertEquals("max-age=604800", it.header("Cache-Control")) }
        assertEquals(1, requests)
        val post = request.newBuilder().post("no upload".toRequestBody()).build()
        assertTrue(runCatching { client.newCall(post).execute().close() }.exceptionOrNull() is IOException)
        canRequest = false
        assertTrue(runCatching { client.newCall(request).execute().close() }.exceptionOrNull() is IOException)
        assertEquals(1, requests)
    }
    @Test fun offlineStyleHasNoNetworkSourcesAndOnlineStyleOnlyHasTheApprovedProvider() {
        val paint = TripMapPaint("#050C1D", "#2F6BFF", "#79DDFF", "#888888", "#050C1D", 4f, 7f, 2f, .82f, "#334455", "#668899")
        val offline = JSONObject(TripMapStyle.json(false, paint))
        assertFalse(offline.toString().contains("https://"))
        assertFalse(offline.getJSONObject("sources").has(TripMapStyle.VECTOR))
        val base = "{\"version\":8,\"sources\":{\"openmaptiles\":{\"type\":\"vector\",\"url\":\"https://tiles.openfreemap.org/planet\"}},\"layers\":[],\"glyphs\":\"https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf\",\"sprite\":\"https://tiles.openfreemap.org/sprites/ofm_f384/ofm\"}"
        val online = JSONObject(TripMapStyle.json(true, paint, base))
        assertEquals("https://tiles.openfreemap.org/planet",
            online.getJSONObject("sources").getJSONObject("openmaptiles").getString("url"))
        assertEquals("https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf", online.getString("glyphs"))
        assertEquals(0, online.getJSONObject("transition").getInt("duration"))
    }
}
