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
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripMapPolicyTest {
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
        val paint = TripMapPaint("#050C1D", "#2F6BFF", "#79DDFF", "#888888", "#050C1D", 4f, 7f, 2f, .82f)
        val offline = JSONObject(TripMapStyle.json(false, paint))
        assertFalse(offline.toString().contains("https://"))
        assertFalse(offline.getJSONObject("sources").has(TripMapStyle.OSM))
        val base = "{\"version\":8,\"sources\":{\"openmaptiles\":{\"type\":\"vector\",\"url\":\"https://tiles.openfreemap.org/planet\"}},\"layers\":[],\"glyphs\":\"https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf\",\"sprite\":\"https://tiles.openfreemap.org/sprites/ofm_f384/ofm\"}"
        val online = JSONObject(TripMapStyle.json(true, paint, base))
        assertEquals("https://tiles.openfreemap.org/planet",
            online.getJSONObject("sources").getJSONObject("openmaptiles").getString("url"))
        assertEquals("https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf", online.getString("glyphs"))
        assertEquals(0, online.getJSONObject("transition").getInt("duration"))
    }
}
