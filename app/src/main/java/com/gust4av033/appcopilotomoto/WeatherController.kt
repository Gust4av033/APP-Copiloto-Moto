package com.gust4av033.appcopilotomoto

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*

enum class WeatherArea { HERE, DESTINATION }
data class WeatherUiState(val enabled: Boolean = false, val area: WeatherArea = WeatherArea.HERE,
    val destinationLabel: String? = null, val hasTarget: Boolean = false, val loading: Boolean = false,
    val failed: Boolean = false, val refreshAvailable: Boolean = false, val data: ContextValue<WeatherObservation>? = null,
    val nowElapsedMs: Long = 0, val nowEpochMs: Long = 0) {
    val usable: WeatherObservation? get() = data?.takeIf { it.freshness(nowElapsedMs) != Freshness.EXPIRED }?.value
    val old: Boolean get() = data?.let { it.ageMs(nowElapsedMs) >= OpenMeteoWeatherProvider.REFRESH_MS } == true
}

internal class WeatherPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
    fun enabled() = prefs.getBoolean("weather_open_meteo_consent_v1", false)
    fun setEnabled(enabled: Boolean) { prefs.edit().putBoolean("weather_open_meteo_consent_v1", enabled).apply() }
}

/** Small in-memory cache survives Activity recreation. No weather coordinates go to disk. */
internal object WeatherMemory {
    private val values = linkedMapOf<String, ContextValue<WeatherObservation>>()
    private val attempts = linkedMapOf<String, Long>()
    fun key(target: TargetDestination) = "${OpenMeteoWeatherProvider.rounded(target.latitude)},${OpenMeteoWeatherProvider.rounded(target.longitude)}"
    @Synchronized fun get(key: String) = values[key]
    @Synchronized fun put(key: String, value: ContextValue<WeatherObservation>) {
        values[key] = value
        while (values.size > 8) values.remove(values.keys.first())
    }
    @Synchronized fun canTry(key: String, now: Long): Boolean {
        val last = attempts[key] ?: return true
        return now >= last && now - last >= 60_000
    }
    @Synchronized fun attempted(key: String, now: Long) {
        attempts[key] = now
        while (attempts.size > 8) attempts.remove(attempts.keys.first())
    }
    @Synchronized fun clear() { values.clear(); attempts.clear() }
}

/** UI-only consumer of the existing GPS state. Never starts a location/audio service. */
internal class WeatherController(context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val epoch: () -> Long = System::currentTimeMillis,
    providerFactory: (() -> Boolean) -> WeatherProvider = { OpenMeteoWeatherProvider(it) }) {
    private val preferences = WeatherPreferences(context)
    @Volatile private var active = false
    @Volatile private var foreground = false
    private var job: Job? = null
    private var current: TargetDestination? = null
    private var destination: TargetDestination? = null
    private var key: String? = null
    private var generation = 0
    private val provider = providerFactory({ preferences.enabled() && foreground && active })
    var state by mutableStateOf(WeatherUiState(enabled = preferences.enabled()))
        private set

    fun foreground(value: Boolean) {
        foreground = value
        if (!value) stopRequest()
        else update(active, current, destination)
    }
    fun enabled(value: Boolean) {
        preferences.setEnabled(value)
        stopRequest()
        if (!value) WeatherMemory.clear()
        state = state.copy(enabled = value, data = null, failed = false)
        update(active, current, destination)
    }
    fun select(area: WeatherArea) {
        if (area == state.area) return
        stopRequest(); key = null
        state = state.copy(area = area, data = null, failed = false)
        update(active, current, destination)
    }
    fun update(visible: Boolean, here: TargetDestination?, target: TargetDestination?) {
        active = visible; current = here; destination = target
        val selected = if (state.area == WeatherArea.HERE) here else target
        val nextKey = selected?.let(WeatherMemory::key)
        if (key != nextKey) {
            stopRequest(); key = nextKey
            state = state.copy(data = null, failed = false)
        }
        val now = elapsed()
        state = state.copy(enabled = preferences.enabled(), hasTarget = selected != null,
            destinationLabel = target?.label, refreshAvailable = nextKey != null && WeatherMemory.canTry(nextKey, now), nowElapsedMs = now, nowEpochMs = epoch())
        if (!state.enabled || !active || !foreground || selected == null || nextKey == null) {
            stopRequest()
            if (!state.enabled || selected == null) state = state.copy(data = null, failed = false)
            return
        }
        WeatherMemory.get(nextKey)?.let { state = state.copy(data = it) }
        if (job?.isActive == true) return
        if (state.data?.let { it.freshness(now) != Freshness.EXPIRED && it.ageMs(now) < OpenMeteoWeatherProvider.REFRESH_MS } == true) return
        if (!WeatherMemory.canTry(nextKey, now)) return
        request(selected, nextKey, now)
    }
    fun refresh() {
        val target = if (state.area == WeatherArea.HERE) current else destination
        val currentKey = key ?: return
        val now = elapsed()
        if (state.enabled && active && foreground && target != null && job?.isActive != true && WeatherMemory.canTry(currentKey, now))
            request(target, currentKey, now)
    }
    private fun request(target: TargetDestination, requestedKey: String, now: Long) {
        WeatherMemory.attempted(requestedKey, now)
        val requestGeneration = ++generation
        state = state.copy(loading = true, failed = false, refreshAvailable = false)
        job = scope.launch {
            val result = try { provider.at(target.latitude, target.longitude, epoch()) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null }
            if (generation != requestGeneration || !preferences.enabled() || !foreground || !active) return@launch
            result?.let { WeatherMemory.put(requestedKey, it) }
            state = state.copy(loading = false, failed = result == null, data = result ?: state.data,
                nowElapsedMs = elapsed(), nowEpochMs = epoch())
        }
    }
    private fun stopRequest() {
        generation++; job?.cancel(); job = null
        if (state.loading) state = state.copy(loading = false)
    }
    fun destroy() { stopRequest(); scope.cancel() }
}
