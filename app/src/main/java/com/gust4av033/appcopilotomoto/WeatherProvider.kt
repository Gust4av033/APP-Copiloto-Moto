package com.gust4av033.appcopilotomoto

import android.content.Context
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class WeatherSnapshot(
    val temperatureC: Double,
    val rainMm: Double,
    val weatherCode: Int,
    val capturedAtElapsedMs: Long,
    val stale: Boolean = false,
    val source: String = "open-meteo"
) {
    val confidence: Double get() = if (stale) 0.25 else 0.9
}

class WeatherPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
    fun enabled(): Boolean = prefs.getBoolean("weather_consent_v1", false)
    fun setEnabled(value: Boolean) = prefs.edit().putBoolean("weather_consent_v1", value).apply()
}

object WeatherPolicy {
    const val MAX_LATITUDE = 90.0
    const val MAX_LONGITUDE = 180.0
    const val CACHE_KEY_PREFIX = "weather_cache_v1:"
    const val MAX_AGE_MS = 30 * 60 * 1000L
    const val STALE_AGE_MS = 2 * 60 * 60 * 1000L
}

class OpenMeteoWeatherProvider(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    fun cached(latitude: Double, longitude: Double): WeatherSnapshot? {
        val key = key(latitude, longitude)
        val json = prefs.getString(key, null) ?: return null
        return runCatching {
            val root = JSONObject(json)
            val temperature = root.getDouble("temperature")
            val rain = root.optDouble("rain", 0.0)
            val code = root.getInt("code")
            val captured = root.getLong("captured")
            WeatherSnapshot(temperature, rain, code, captured)
        }.getOrNull()
    }

    fun refresh(latitude: Double, longitude: Double, nowElapsedMs: Long): Result<WeatherSnapshot> {
        require(latitude in -WeatherPolicy.MAX_LATITUDE..WeatherPolicy.MAX_LATITUDE)
        require(longitude in -WeatherPolicy.MAX_LONGITUDE..WeatherPolicy.MAX_LONGITUDE)
        val url = HttpUrl.Builder()
            .scheme("https").host("api.open-meteo.com").addPathSegments("v1/forecast")
            .addQueryParameter("latitude", "%.5f".format(latitude))
            .addQueryParameter("longitude", "%.5f".format(longitude))
            .addQueryParameter("current", "temperature_2m,rain,weather_code")
            .addQueryParameter("timezone", "auto")
            .build()
        return runCatching {
            val response = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute()
            if (!response.isSuccessful) throw IOException("Weather HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty weather response")
            val current = JSONObject(body).optJSONObject("current") ?: throw IOException("Missing weather current")
            val snapshot = WeatherSnapshot(
                temperatureC = current.optDouble("temperature_2m"),
                rainMm = current.optDouble("rain", 0.0),
                weatherCode = current.optInt("weather_code", -1),
                capturedAtElapsedMs = nowElapsedMs
            )
            prefs.edit().putString(key(latitude, longitude), JSONObject()
                .put("temperature", snapshot.temperatureC)
                .put("rain", snapshot.rainMm)
                .put("code", snapshot.weatherCode)
                .put("captured", snapshot.capturedAtElapsedMs)
                .toString()).apply()
            snapshot
        }
    }

    private fun key(latitude: Double, longitude: Double) =
        "${WeatherPolicy.CACHE_KEY_PREFIX}${"%.3f".format(latitude)}_${"%.3f".format(longitude)}"
}
