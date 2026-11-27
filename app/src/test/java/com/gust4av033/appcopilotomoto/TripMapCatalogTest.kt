package com.gust4av033.appcopilotomoto

import com.gust4av033.appcopilotomoto.presentation.components.TripMapPaint
import com.gust4av033.appcopilotomoto.presentation.components.TripMapStyle
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripMapCatalogTest {
    private fun metadata(version: String = "20261122_124456_pt") = """{"tilejson":"3.0.0","minzoom":0,"maxzoom":14,
        "tiles":["https://tiles.openfreemap.org/planet/$version/{z}/{x}/{y}.pbf"],
        "vector_layers":[{"id":"building"},{"id":"water"},{"id":"transportation"},{"id":"landcover"}]}"""
    @Test fun parserRejectsForeignUrlsIncompleteDataAndUnboundedMetadata() {
        assertEquals("20261122_124456_pt", MapCatalogPolicy.parse(metadata()).version)
        for (bad in listOf(metadata().replace("https:","http:"), metadata().replace("tiles.openfreemap.org", "evil.example"),
            metadata().replace(".pbf", ".pbf?token=123"), metadata().replace("building", "unknown"),
            metadata("20269999_124456_pt"), metadata().replace("\"maxzoom\":14", "\"maxzoom\":15"),
            metadata().replace("\"minzoom\":0", "\"minzoom\":0.5"),
            metadata().replace("\"maxzoom\":14", "\"maxzoom\":13.5"), " ".repeat(300000) + metadata()))
            assertTrue(runCatching { MapCatalogPolicy.parse(bad) }.isFailure)
    }
    @Test fun validatedVersionReplacesAliasWithoutRemovingRealBuildingsOrGreenAreas() {
        val context = RuntimeEnvironment.getApplication()
        val base = context.assets.open("map/liberty.json").bufferedReader().use { it.readText() }
        val paint = TripMapPaint("#050C1D", "#2F6BFF", "#79DDFF", "#888888", "#050C1D", 4f, 7f, 2f, 1f, "#334455", "#668899")
        val version = MapCatalogPolicy.parse(metadata())
        val style = JSONObject(TripMapStyle.json(true, paint, base, version))
        val source = style.getJSONObject("sources").getJSONObject(TripMapStyle.VECTOR)
        assertFalse(source.has("url"))
        assertEquals(version.tileTemplate, source.getJSONArray("tiles").getString(0))
        val layers = style.getJSONArray("layers")
        val indexed = (0 until layers.length()).map { layers.getJSONObject(it) }.associateBy { it.getString("id") }
        assertEquals("none", indexed.getValue(TripMapStyle.TREES).getJSONObject("layout").getString("visibility"))
        val originals = JSONObject(base).getJSONArray("layers")
        for (i in 0 until originals.length()) {
            val layer = originals.getJSONObject(i)
            if (layer.optString("source-layer") in listOf("building", "landcover", "park"))
                assertEquals(layer.toString(), indexed.getValue(layer.getString("id")).toString())
        }
    }
    @Test fun updatesAreBoundedAndFailuresNeverReplaceTheSavedVersion() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("map_catalog",0).edit().clear().commit()
        var allowed = true; var requests = 0; var time = 100000L; var body = metadata(); var code = 200
        val client = OkHttpClient.Builder().addInterceptor(TripTileInterceptor { allowed })
            .addInterceptor { chain ->
                requests++
                assertEquals(MapCatalogPolicy.LATEST, chain.request().url.toString())
                assertEquals("no-cache", chain.request().header("Cache-Control"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                    .body(body.toResponseBody()).build()
            }.build()
        val store = TripMapCatalog(context, client, { allowed }, { time })
        assertEquals(MapCatalogStatus.UPDATED, store.resolve(true).status)
        assertEquals(MapCatalogStatus.WAIT, store.resolve(true).status)
        assertEquals(1, requests)
        time += 61000; body = metadata("20261001_120000_pt")
        assertEquals(MapCatalogStatus.PREVIOUS, store.resolve(true).status)
        assertEquals("20261122_124456_pt", store.saved()!!.version)
        time += 61000; body = "{}"
        assertEquals(MapCatalogStatus.FAILED, store.resolve(true).status)
        time += 61000; code = 503
        assertEquals(MapCatalogStatus.FAILED, store.resolve(true).status)
        assertEquals("20261122_124456_pt", TripMapCatalog(context, client, { allowed }).saved()!!.version)
        allowed = false; time += 61000
        val before = requests
        assertEquals(MapCatalogStatus.FAILED, store.resolve(true).status)
        assertEquals(before, requests)
    }
    @Test fun metadataRevalidationNeverForcesAllTilesToDownload() {
        assertTrue(TripTilePolicy.allows(MapCatalogPolicy.LATEST.toHttpUrl()))
        assertFalse(TripTilePolicy.allows("https://tiles.openfreemap.org/planet/latest/14/1/1.pbf".toHttpUrl()))
        val client = OkHttpClient.Builder().addInterceptor(TripTileInterceptor { true }).addInterceptor { chain ->
            assertNull(chain.request().header("Cache-Control"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("tile".toResponseBody()).build()
        }.build()
        client.newCall(Request.Builder().url("https://tiles.openfreemap.org/planet/20261122_124456_pt/14/1/1.pbf")
            .tag(RevalidateMapCatalog::class.java, RevalidateMapCatalog).header("Cache-Control","no-cache").build()).execute().close()
    }
    @Test fun vegetationPreferenceIsOptInAndIndependentOfNetworkConsent() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ui_settings",0).edit().clear().commit()
        val preferences = TripMapPreferences(context)
        assertFalse(preferences.vegetationEnabled())
        preferences.setVegetationEnabled(true)
        assertTrue(TripMapPreferences(context).vegetationEnabled())
        assertFalse(preferences.onlineEnabled())
    }
}
