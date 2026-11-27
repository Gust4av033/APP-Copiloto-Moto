package com.gust4av033.appcopilotomoto

import android.content.Context
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class TripMapPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
    fun vegetationEnabled() = prefs.getBoolean("map_vegetation_3d", false)
    fun setVegetationEnabled(value: Boolean) { prefs.edit().putBoolean("map_vegetation_3d", value).apply() }
    fun onlineEnabled() = prefs.getBoolean("trip_map_online_consent_v2", false)
    fun setOnlineEnabled(value: Boolean) {
        prefs.edit()
            .putBoolean("trip_map_online_consent_v2", value)
            .putBoolean("trip_map_online_consent_v1", false)
            .apply()
    }
}

data class TripTileProvider(
    val id: String,
    val hosts: Set<String>,
    val consentVersion: Int,
    val attributionLabel: String,
    val attributionUrl: String
)

/** Fixed vector provider adopted from Copilot c0b10ad. No fallback network hosts. */
internal object TripTilePolicy {
    val OPEN_FREE_MAP = TripTileProvider(
        id = "openfreemap",
        hosts = setOf("tiles.openfreemap.org"),
        consentVersion = 2,
        attributionLabel = "OpenFreeMap · © OpenMapTiles · datos © OSM",
        attributionUrl = "https://openfreemap.org/"
    )
    val ACTIVE_PROVIDER = OPEN_FREE_MAP
    const val USER_AGENT = "APP-Copiloto-Moto/${BuildConfig.VERSION_NAME} (+https://github.com/Gust4av033/APP-Copiloto-Moto)"
    fun allows(url: HttpUrl): Boolean {
        if (url.scheme != "https" || url.port != 443 ||
            url.host !in ACTIVE_PROVIDER.hosts || url.encodedQuery != null ||
            url.encodedFragment != null || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        val path = url.encodedPath
        if (path == "/planet" || path == "/planet/latest") return true
        val tilePbf = Regex("^/planet/[0-9]{8}_[0-9]{6}_pt/([0-9]{1,2})/([0-9]+)/([0-9]+)\\.pbf$")
            .matchEntire(path)?.let { match ->
                val z = match.groupValues[1].toIntOrNull() ?: -1
                val x = match.groupValues[2].toLongOrNull() ?: -1L
                val y = match.groupValues[3].toLongOrNull() ?: -1L
                z in 0..14 && x in 0 until (1L shl z) && y in 0 until (1L shl z)
            } == true
        if (tilePbf) return true
        val natural = Regex("^/natural_earth/ne2sr/([0-9]{1,2})/([0-9]+)/([0-9]+)\\.png$")
            .matchEntire(path)?.let { match ->
                val z = match.groupValues[1].toIntOrNull() ?: -1
                val x = match.groupValues[2].toLongOrNull() ?: -1L
                val y = match.groupValues[3].toLongOrNull() ?: -1L
                z in 0..6 && x in 0 until (1L shl z) && y in 0 until (1L shl z)
            } == true
        if (natural) return true
        val sprite = Regex("^/sprites/ofm_[^/]+/ofm(?:@2x)?\\.(json|png)$").matches(path)
        if (sprite) return true
        val glyph = Regex("^/fonts/[^/]+/[0-9]+-[0-9]+\\.pbf$").matches(path)
        return glyph
    }
}

/** Runs before cache lookup, so withdrawing consent also blocks SDK requests for cached tiles. */
internal class TripTileInterceptor(private val canRequest: () -> Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!canRequest() || chain.request().method != "GET" || !TripTilePolicy.allows(chain.request().url)) throw IOException("Map request unavailable")
        val request = chain.request().newBuilder().header("User-Agent", TripTilePolicy.USER_AGENT)
            .removeHeader("Pragma").removeHeader("Cache-Control").build()
        val controlled = if (request.url.toString() == MapCatalogPolicy.LATEST &&
            request.tag(RevalidateMapCatalog::class.java) != null)
            request.newBuilder().header("Cache-Control", "no-cache").build() else request
        return chain.proceed(controlled)
    }
}

/** This client is exclusive to maps; cancelling it cannot cancel audio or another provider. */
internal object TripMapNetwork {
    private val visibleViews = ConcurrentHashMap.newKeySet<Any>()
    @Volatile private var preferences: TripMapPreferences? = null
    private var client: OkHttpClient? = null
    private var catalog: TripMapCatalog? = null
    @Synchronized fun catalog(context: Context): TripMapCatalog = catalog ?: TripMapCatalog(context, client(context),
        { preferences?.onlineEnabled() == true && visibleViews.isNotEmpty() }).also { catalog = it }
    @Synchronized fun client(context: Context): OkHttpClient {
        client?.let { return it }
        preferences = TripMapPreferences(context)
        return OkHttpClient.Builder()
            .cache(Cache(File(context.applicationContext.cacheDir, "trip_map_tiles"), 64L * 1024 * 1024))
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .addInterceptor(TripTileInterceptor { preferences?.onlineEnabled() == true && visibleViews.isNotEmpty() })
            .build().also { client = it }
    }
    fun visible(token: Any, value: Boolean) {
        if (value) visibleViews.add(token) else visibleViews.remove(token)
        if (visibleViews.isEmpty()) cancelRequests()
    }
    @Synchronized fun cancelRequests() { client?.dispatcher?.cancelAll() }
}
