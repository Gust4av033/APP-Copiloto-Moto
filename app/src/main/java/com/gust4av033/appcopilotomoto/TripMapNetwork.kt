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
    fun onlineEnabled() = prefs.getBoolean("trip_map_online_consent_v1", false)
    fun setOnlineEnabled(value: Boolean) { prefs.edit().putBoolean("trip_map_online_consent_v1", value).apply() }
}

/** Explicit, fixed provider. No arbitrary style URLs, tokens, coordinates, or redirects. */
internal object TripTilePolicy {
    const val TEMPLATE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    const val USER_AGENT = "APP-Copiloto-Moto/${BuildConfig.VERSION_NAME} (+https://github.com/Gust4av033/APP-Copiloto-Moto)"
    fun allows(url: HttpUrl): Boolean {
        if (url.scheme != "https" || url.host != "tile.openstreetmap.org" || url.port != 443 ||
            url.encodedQuery != null || url.encodedFragment != null || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        val match = Regex("/([0-9]{1,2})/([0-9]+)/([0-9]+)\\.png").matchEntire(url.encodedPath) ?: return false
        val z = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..19 } ?: return false
        val x = match.groupValues[2].toLongOrNull() ?: return false
        val y = match.groupValues[3].toLongOrNull() ?: return false
        return x in 0 until (1L shl z) && y in 0 until (1L shl z)
    }
}

/** Runs before cache lookup, so withdrawing consent also blocks SDK requests for cached tiles. */
internal class TripTileInterceptor(private val canRequest: () -> Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!canRequest() || chain.request().method != "GET" || !TripTilePolicy.allows(chain.request().url)) throw IOException("Map request unavailable")
        val request = chain.request().newBuilder().header("User-Agent", TripTilePolicy.USER_AGENT)
            .removeHeader("Pragma").removeHeader("Cache-Control").build()
        return chain.proceed(request)
    }
}

/** This client is exclusive to maps; cancelling it cannot cancel audio or another provider. */
internal object TripMapNetwork {
    private val visibleViews = ConcurrentHashMap.newKeySet<Any>()
    @Volatile private var preferences: TripMapPreferences? = null
    private var client: OkHttpClient? = null
    @Synchronized fun client(context: Context): OkHttpClient {
        client?.let { return it }
        preferences = TripMapPreferences(context)
        return OkHttpClient.Builder()
            .cache(Cache(File(context.applicationContext.cacheDir, "trip_map_tiles"), 64L * 1024 * 1024))
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
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
