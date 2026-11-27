package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable
internal fun MapWeather(state: CopilotUiState, actions: CopilotActions, modifier: Modifier = Modifier, compact: Boolean = false) {
    val weather = state.weather
    val observation = weather.usable
    var open by rememberSaveable { mutableStateOf(false) }
    val status = when {
        !weather.enabled -> R.string.weather_enable
        !weather.hasTarget -> if (weather.area == WeatherArea.HERE) R.string.weather_waiting_gps else R.string.weather_no_destination
        weather.loading && observation == null -> R.string.weather_loading
        observation == null -> R.string.weather_unavailable
        weather.old -> R.string.weather_previous
        else -> conditionLabel(observation.weatherCode)
    }
    MapOverlayPanel(modifier.clickable(role = Role.Button) { open = true }) {
        Row(Modifier.heightIn(min = Glass.iconButtonSize).padding(if (compact) Glass.cockpitGap else Glass.ridePanelPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_weather), null, Modifier.size(Glass.iconSize), tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(if (observation?.temperatureCelsius != null) temperature(observation.temperatureCelsius)
                    else stringResource(R.string.weather_title), style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                Text(stringResource(status), style = MaterialTheme.typography.labelSmall, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (weather.area == WeatherArea.DESTINATION && weather.destinationLabel != null)
                    Text(weather.destinationLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.weather_title)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleSettingRow(stringResource(R.string.weather_online), weather.enabled, enabled = weather.enabled || !state.busy,
                    onCheckedChange = { open = false; actions.onWeatherEnabled(it) })
                if (!weather.enabled) {
                    SupportingText(stringResource(if (state.busy) R.string.weather_finish_session else R.string.weather_off_hint))
                } else {
                    // Stacked choices remain accessible with large fonts and narrow screens.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(weather.area == WeatherArea.HERE, onClick = { actions.onWeatherArea(WeatherArea.HERE) })
                        Text(stringResource(R.string.weather_here))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(weather.area == WeatherArea.DESTINATION, onClick = { actions.onWeatherArea(WeatherArea.DESTINATION) })
                        Text(stringResource(R.string.weather_destination, weather.destinationLabel ?: stringResource(R.string.weather_not_selected)))
                    }
                    SupportingText(stringResource(status))
                    if (weather.failed && observation != null) SupportingText(stringResource(R.string.weather_update_failed))
                    observation?.let { current ->
                        Text(temperature(current.temperatureCelsius), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(conditionLabel(current.weatherCode)))
                        current.windKmh?.let { Text(stringResource(R.string.weather_wind, format(it))) }
                        Text(stringResource(R.string.weather_at, time(current.currentEpochMs, current.timeZoneId), current.timeZoneId))
                        SupportingText(stringResource(R.string.weather_model_hint))
                        Text(stringResource(R.string.weather_hourly), style = MaterialTheme.typography.titleMedium)
                        val hours = current.hourly.filter { it.epochMs >= weather.nowEpochMs }.take(12)
                        if (hours.isEmpty()) SupportingText(stringResource(R.string.weather_hours_unavailable))
                        hours.forEach { hour ->
                            Column {
                                Text("${time(hour.epochMs, current.timeZoneId)} · ${temperature(hour.temperatureCelsius)}",
                                    style = MaterialTheme.typography.titleSmall)
                                SupportingText(stringResource(R.string.weather_hour_details,
                                    stringResource(conditionLabel(hour.weatherCode)), hour.rainProbability?.toString() ?: "—"))
                            }
                        }
                    }
                    ActionButton(stringResource(R.string.weather_refresh), enabled = weather.hasTarget && !weather.loading && weather.refreshAvailable,
                        onClick = actions.onRefreshWeather)
                    if (!weather.refreshAvailable && weather.hasTarget) SupportingText(stringResource(R.string.weather_refresh_wait))
                }
                val uri = LocalUriHandler.current
                var linkError by remember { mutableStateOf(false) }
                ActionButton(stringResource(R.string.weather_attribution)) {
                    runCatching { uri.openUri("https://open-meteo.com/") }.onFailure { linkError = true }
                }
                if (linkError) SupportingText(stringResource(R.string.trip_map_link_error))
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ride_done)) } })
}

private fun format(number: Double) = String.format(Locale.getDefault(), "%.0f", number)
private fun temperature(number: Double?) = if (number == null) "—" else "${format(number)} °C"
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
