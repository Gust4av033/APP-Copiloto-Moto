package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.*
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.*
import com.gust4av033.appcopilotomoto.presentation.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.platform.LocalUriHandler

/** Compact content only: the shared capsule owns interaction; weather keeps its existing owner. */
@Composable
internal fun MapWeather(state: CopilotUiState, modifier: Modifier = Modifier, showCondition: Boolean = false) {
    val observation = state.weather.usable
    val old = state.weather.old && observation != null
    val age = observation?.let { ((state.weather.nowEpochMs - it.currentEpochMs).coerceAtLeast(0) / 60_000) }
    val ageLabel = age?.let { if (it >= 60) stringResource(R.string.map_weather_age_hours, it / 60)
        else stringResource(R.string.map_weather_age_minutes, it) }
    Row(modifier.testTag("map_weather"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Glass.mapStatusIconGap)) {
        Icon(painterResource(R.drawable.ic_weather), stringResource(if (observation == null) weatherStatus(state)
            else conditionLabel(observation.weatherCode)), Modifier.size(Glass.iconSize))
        if (observation != null) {
            Text(temperature(observation.temperatureCelsius), maxLines = 1,
                color = if (old) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = Glass.mapTemperatureSize,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontFeatureSettings = "tnum"))
            if (old) Text(ageLabel.orEmpty(), maxLines = 1, style = MaterialTheme.typography.labelSmall)
            else if (showCondition) Text(stringResource(conditionLabel(observation.weatherCode)),
                maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun MapWeatherDetails(state: CopilotUiState, actions: CopilotActions) {
    val weather = state.weather
    val observation = weather.usable
    val status = weatherStatus(state)
    ToggleSettingRow(stringResource(R.string.weather_online), weather.enabled, enabled = weather.enabled || !state.busy,
        onCheckedChange = actions.onWeatherEnabled)
    if (!weather.enabled) {
        MapHint(stringResource(if (state.busy) R.string.weather_finish_session else R.string.weather_off_hint))
    } else {
        Row(
            Modifier.fillMaxWidth().selectable(
                selected = weather.area == WeatherArea.HERE,
                role = Role.RadioButton,
                onClick = { actions.onWeatherArea(WeatherArea.HERE) }
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(weather.area == WeatherArea.HERE, onClick = null)
            Text(stringResource(R.string.weather_here))
        }
        Row(
            Modifier.fillMaxWidth().selectable(
                selected = weather.area == WeatherArea.DESTINATION,
                role = Role.RadioButton,
                onClick = { actions.onWeatherArea(WeatherArea.DESTINATION) }
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(weather.area == WeatherArea.DESTINATION, onClick = null)
            Text(stringResource(R.string.weather_destination, weather.destinationLabel ?: stringResource(R.string.weather_not_selected)))
        }
        MapHint(stringResource(status))
        if (weather.failed && observation != null) MapHint(stringResource(R.string.weather_update_failed))
        observation?.let { current ->
            Text(temperature(current.temperatureCelsius), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(conditionLabel(current.weatherCode)))
            current.windKmh?.let { Text(stringResource(R.string.weather_wind, format(it))) }
            Text(stringResource(R.string.weather_at, time(current.currentEpochMs, current.timeZoneId), current.timeZoneId))
            MapHint(stringResource(R.string.weather_model_hint))
            HorizontalDivider()
            Text(stringResource(R.string.weather_hourly), style = MaterialTheme.typography.titleMedium, maxLines = 2)
            val hours = current.hourly.filter { it.epochMs >= weather.nowEpochMs }.take(12)
            if (hours.isEmpty()) MapHint(stringResource(R.string.weather_hours_unavailable))
            hours.forEach { hour ->
                Column {
                    Text("${time(hour.epochMs, current.timeZoneId)} · ${temperature(hour.temperatureCelsius)}",
                        style = MaterialTheme.typography.titleSmall)
                    MapHint(stringResource(R.string.weather_hour_details,
                        stringResource(conditionLabel(hour.weatherCode)), hour.rainProbability?.toString() ?: "—"))
                }
            }
        }
        ActionButton(stringResource(R.string.weather_refresh), enabled = weather.hasTarget && !weather.loading && weather.refreshAvailable,
            onClick = actions.onRefreshWeather)
        if (!weather.refreshAvailable && weather.hasTarget) MapHint(stringResource(R.string.weather_refresh_wait))
    }
    val uri = LocalUriHandler.current
    var linkError by remember { mutableStateOf(false) }
    ActionButton(stringResource(R.string.weather_attribution)) {
        runCatching { uri.openUri("https://open-meteo.com/") }.onFailure { linkError = true }
    }
    if (linkError) MapHint(stringResource(R.string.trip_map_link_error))
}

@Composable
internal fun MapWeatherTemperature(state: CopilotUiState): String? =
    state.weather.usable?.temperatureCelsius?.let(::weatherTemperature)

private fun weatherStatus(state: CopilotUiState): Int {
    val weather = state.weather
    val observation = weather.usable
    return when {
        !weather.enabled -> R.string.weather_enable
        !weather.hasTarget -> if (weather.area == WeatherArea.HERE) R.string.weather_waiting_gps else R.string.weather_no_destination
        weather.loading && observation == null -> R.string.weather_loading
        observation == null -> R.string.weather_unavailable
        weather.old -> R.string.weather_previous
        else -> conditionLabel(observation.weatherCode)
    }
}

private fun format(number: Double) = String.format(Locale.getDefault(), "%.0f", number)
private fun temperature(number: Double?) = if (number == null) "—" else "${format(number)} °C"
private fun weatherTemperature(number: Double) = "${format(number)} °C"
private fun time(epochMs: Long, zone: String): String = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault())
    .withZone(ZoneId.of(zone)).format(Instant.ofEpochMilli(epochMs))
internal fun conditionLabel(code: Int?): Int = when (code) {
    0 -> R.string.weather_clear
    1, 2 -> R.string.weather_partly_cloudy
    3 -> R.string.weather_cloudy
    45, 48 -> R.string.weather_fog
    51, 53, 55 -> R.string.weather_drizzle
    56, 57, 66, 67 -> R.string.weather_freezing_rain
    61, 63, 65, 80, 81, 82 -> R.string.weather_rain
    71, 73, 75, 77, 85, 86 -> R.string.weather_snow
    95, 96, 99 -> R.string.weather_storm
    else -> R.string.weather_unknown
}

@Composable
internal fun MapHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 2,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}
