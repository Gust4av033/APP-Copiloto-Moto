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
        TripMapPreferences(context).setOnlineEnabled(true)
        assertTrue(TripMapPreferences(context).onlineEnabled())
        assertFalse(UserSettingsStore(context).tripLocationEnabled())
        TripMapPreferences(context).setOnlineEnabled(false)
        assertFalse(TripMapPreferences(context).onlineEnabled())
    }
    @Test fun onlyHttpsGetTilesInTheAllowedZoomRangeCanLeaveTheDevice() {
        val allowed = "https://tile.openstreetmap.org/16/16293/30082.png"
        assertTrue(TripTilePolicy.allows(allowed.toHttpUrl()))
        for (url in listOf("http://tile.openstreetmap.org/1/1/1.png", "https://example.com/1/1/1.png",
            "https://tile.openstreetmap.org/20/1/1.png", "https://tile.openstreetmap.org/1/4/1.png",
            "https://tile.openstreetmap.org/1/1/1.png?lat=14.6", "https://tile.openstreetmap.org/1/1/1.png#private",
            "https://tile.openstreetmap.org/style.json", "https://tile.openstreetmap.org:444/1/1/1.png")) {
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
        assertFalse(offline.getJSONObject("sources").has(TripMapStyle.OSM))
        val online = JSONObject(TripMapStyle.json(true, paint))
        assertEquals(TripTilePolicy.TEMPLATE, online.getJSONObject("sources").getJSONObject(TripMapStyle.OSM).getJSONArray("tiles").getString(0))
        assertFalse(online.has("glyphs")); assertFalse(online.has("sprite"))
        assertEquals(0, online.getJSONObject("transition").getInt("duration"))
    }
}
