package com.gust4av033.appcopilotomoto

import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.round

/** Independent from the map/audio clients. No redirects, credentials, logs or background polling. */
internal class OpenMeteoWeatherProvider(private val allowed: () -> Boolean,
    private val client: OkHttpClient = weatherClient(allowed),
    private val elapsed: () -> Long = SystemClock::elapsedRealtime) : WeatherProvider {
    override suspend fun at(latitude: Double, longitude: Double, epochMs: Long): ContextValue<WeatherObservation>? {
        if (!allowed()) return null
        val request = Request.Builder().url(url(latitude, longitude)).build()
        val body = suspendCancellableCoroutine<String> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            check(it.isSuccessful) { "Weather unavailable" }
                            val source = checkNotNull(it.body).source()
                            check(!source.request(256 * 1024 + 1L)) { "Weather response too large" }
                            source.readUtf8()
                        }
                    }
                    if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
        if (!allowed()) return null
        val observation = parse(body, epochMs) ?: return null
        return ContextValue(observation, ContextSource.OPEN_METEO, epochMs, elapsed(), .7f, TTL_MS)
    }

    companion object {
        const val TTL_MS = 30 * 60_000L
        const val REFRESH_MS = 15 * 60_000L
        fun rounded(value: Double) = round(value * 100) / 100
        fun url(latitude: Double, longitude: Double): HttpUrl {
            require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
            return HttpUrl.Builder().scheme("https").host("api.open-meteo.com").addPathSegments("v1/forecast")
                .addQueryParameter("latitude", rounded(latitude).toString())
                .addQueryParameter("longitude", rounded(longitude).toString())
                .addQueryParameter("current", "temperature_2m,weather_code,wind_speed_10m")
                .addQueryParameter("hourly", "temperature_2m,precipitation_probability,weather_code")
                .addQueryParameter("temperature_unit", "celsius").addQueryParameter("wind_speed_unit", "kmh")
                .addQueryParameter("forecast_hours", "24").addQueryParameter("timeformat", "unixtime")
                .addQueryParameter("timezone", "auto").build()
        }
        fun parse(body: String, nowEpochMs: Long): WeatherObservation? = runCatching {
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val units = json.getJSONObject("current_units")
            require(units.getString("temperature_2m") == "°C" && units.getString("wind_speed_10m") == "km/h")
            val time = current.getLong("time") * 1000
            require(nowEpochMs - time in -5 * 60_000L..60 * 60_000L)
            fun number(value: Any?, range: ClosedFloatingPointRange<Double>): Double? =
                (value as? Number)?.toDouble()?.takeIf { it.isFinite() && it in range }
            fun code(value: Any?) = number(value, 0.0..99.0)?.takeIf { it % 1.0 == 0.0 }?.toInt()
            val temperature = number(current.opt("temperature_2m"), -100.0..70.0)
            val weatherCode = code(current.opt("weather_code"))
            require(temperature != null || weatherCode != null)
            val hours = json.optJSONObject("hourly")
            val times = hours?.optJSONArray("time") ?: JSONArray()
            val hourlyUnits = json.optJSONObject("hourly_units")
            val hourly = if (hourlyUnits?.optString("temperature_2m") != "°C" ||
                hourlyUnits.optString("precipitation_probability") != "%") emptyList() else buildList {
                for (i in 0 until times.length().coerceAtMost(48)) {
                    val epoch = (times.opt(i) as? Number)?.toLong()?.times(1000) ?: continue
                    if (epoch < nowEpochMs || epoch > nowEpochMs + 24 * 60 * 60_000L) continue
                    add(WeatherHour(epoch, number(hours?.optJSONArray("temperature_2m")?.opt(i), -100.0..70.0),
                        number(hours?.optJSONArray("precipitation_probability")?.opt(i), 0.0..100.0)?.toInt(),
                        code(hours?.optJSONArray("weather_code")?.opt(i))))
                }
            }
            WeatherObservation(temperature, "WMO:${weatherCode ?: "unknown"}", weatherCode,
                number(current.opt("wind_speed_10m"), 0.0..500.0), time,
                runCatching { ZoneId.of(json.getString("timezone")).id }.getOrDefault("UTC"), hourly)
        }.getOrNull()

        fun weatherClient(allowed: () -> Boolean): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url
                if (!allowed() || request.method != "GET" || url.scheme != "https" || url.port != 443 ||
                    url.host != "api.open-meteo.com" || url.encodedPath != "/v1/forecast") throw IOException("Weather disabled")
                chain.proceed(request.newBuilder().header("User-Agent", TripTilePolicy.USER_AGENT).build())
            }.build()
    }
}
