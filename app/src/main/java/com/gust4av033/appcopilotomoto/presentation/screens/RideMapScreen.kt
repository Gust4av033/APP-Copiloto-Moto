package com.gust4av033.appcopilotomoto.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.TripMapSession
import com.gust4av033.appcopilotomoto.presentation.*
import com.gust4av033.appcopilotomoto.presentation.components.*
import com.gust4av033.appcopilotomoto.presentation.theme.GlassMode
import com.gust4av033.appcopilotomoto.presentation.theme.GlassPanel
import java.util.Locale

@Composable
internal fun RideMapScreen(state: CopilotUiState, actions: CopilotActions, navigate: (CopilotScreen) -> Unit,
    modifier: Modifier = Modifier) {
    val gps = state.tripLocationEnabled && state.tripLocationAvailable
    val fix = state.tripMap.lastFix?.value
    val showPosition = gps && fix != null && fix.latitude in -TripMapStyle.MAX_LATITUDE..TripMapStyle.MAX_LATITUDE
    val empty = remember { TripMapSession() }
    val mappedSession = if (showPosition) state.tripMap else empty
    val now = state.observedRouteAtElapsedMs
    val positionLabel = when {
        mappedSession.currentFix(now) != null -> R.string.map_fix_short
        showPosition -> R.string.map_last_short
        !state.tripLocationEnabled -> R.string.map_gps_off_short
        !state.tripLocationAvailable -> R.string.trip_map_gps_unavailable
        fix != null -> R.string.trip_map_projection_unavailable
        state.tripActive -> R.string.map_locating
        else -> R.string.map_world
    }
    var options by rememberSaveable { mutableStateOf(false) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.ic_ignitor_vector), null, Modifier.size(44.dp))
            Text(stringResource(R.string.nav_map), modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge)
            GlassIconButton(R.drawable.ic_home, stringResource(R.string.nav_home)) { navigate(CopilotScreen.HOME) }
            GlassIconButton(R.drawable.ic_motorcycle, stringResource(R.string.module_motorcycle)) { navigate(CopilotScreen.MOTORCYCLE) }
            GlassIconButton(R.drawable.ic_map_layers, stringResource(R.string.map_options)) { options = true }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxDockHeight = maxHeight * .44f
            val viewport: @Composable (Modifier) -> Unit = { area ->
                Box(area.testTag("ride_map_viewport")) {
                    key(gps) { LocalTripMapRenderer.current.Render(mappedSession, now, state.mapOnlineEnabled) }
                }
            }
            val controls: @Composable (Modifier) -> Unit = { area ->
                RideControls(state, actions, stringResource(positionLabel), mappedSession.speedKmh(now),
                    { navigate(CopilotScreen.TRIP) }, area)
            }
            if (maxWidth > maxHeight && maxWidth >= 520.dp) {
                Row(Modifier.fillMaxSize()) {
                    viewport(Modifier.weight(1f).fillMaxHeight())
                    controls(Modifier.widthIn(max = 240.dp).verticalScroll(rememberScrollState()))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    viewport(Modifier.weight(1f).fillMaxWidth())
                    controls(Modifier.fillMaxWidth().heightIn(max = maxDockHeight).verticalScroll(rememberScrollState()))
                }
            }
        }
    }
    if (options) AlertDialog(onDismissRequest = { options = false },
        title = { Text(stringResource(R.string.map_options)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleSettingRow(stringResource(R.string.trip_location_title), state.tripLocationEnabled, enabled = !state.busy) {
                    options = false
                    actions.onTripLocationChange(it)
                }
                ToggleSettingRow(stringResource(R.string.trip_map_online_title), state.mapOnlineEnabled,
                    enabled = state.mapOnlineEnabled || !state.busy) {
                    options = false
                    actions.onMapOnlineChange(it)
                }
                SupportingText(stringResource(if (state.mapOnlineEnabled) R.string.trip_map_online_hint else R.string.ride_offline_hint))
                ToggleSettingRow(stringResource(R.string.weather_title), state.weatherEnabled,
                    enabled = state.weatherEnabled || !state.busy) {
                    options = false
                    actions.onWeatherChange(it)
                }
                WeatherCompactPanel(state, actions)
                ActionButton(stringResource(R.string.trip_map_open_navigator, state.navigationProvider.label),
                    enabled = state.navigationAvailable) {
                    options = false
                    actions.onOpenTripNavigator()
                }
            }
        }, confirmButton = { TextButton(onClick = { options = false }) { Text(stringResource(R.string.ride_done)) } })
}

@Composable
private fun WeatherCompactPanel(state: CopilotUiState, actions: CopilotActions) {
    val weatherSnapshot = state.weatherSnapshot
    GlassPanel(mode = GlassMode.LITE) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.weather_title), style = MaterialTheme.typography.labelLarge)
            when {
                state.weatherLoading -> SupportingText(stringResource(R.string.weather_loading))
                weatherSnapshot != null -> {
                    val weather = weatherSnapshot
                    Text(stringResource(R.string.weather_value,
                        String.format(Locale.getDefault(), "%.1f", weather.temperatureC),
                        String.format(Locale.getDefault(), "%.1f", weather.rainMm)))
                    SupportingText(stringResource(if (weather.stale) R.string.weather_stale else R.string.weather_updated))
                }
                state.weatherError -> SupportingText(stringResource(R.string.weather_error))
                !state.weatherEnabled -> SupportingText(stringResource(R.string.weather_disabled))
                else -> SupportingText(stringResource(R.string.weather_waiting))
            }
            if (state.weatherEnabled) ActionButton(stringResource(R.string.weather_refresh),
                enabled = !state.weatherLoading, onClick = actions.onRefreshWeather)
        }
    }
}

@Composable
private fun RideControls(state: CopilotUiState, actions: CopilotActions, position: String, speed: Float?,
    openTrip: () -> Unit, modifier: Modifier) {
    GlassPanel(modifier = modifier, mode = GlassMode.LITE, strong = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(position, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                if (!state.tripLocationEnabled && !state.busy) {
                    TextButton(onClick = { actions.onTripLocationChange(true) }) {
                        Text(stringResource(R.string.map_enable_location))
                    }
                }
                if (speed != null) Text(stringResource(R.string.trip_map_kmh, String.format(Locale.getDefault(), "%.0f", speed)),
                    style = MaterialTheme.typography.titleLarge)
            }
            if (state.tripActive || state.sessionProblem) SupportingText(stringResource(sessionProgress(state)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    ActionButton(stringResource(if (state.tripActive) R.string.trip_stop else R.string.trip_start),
                        primary = !state.tripActive, destructive = state.tripActive, enabled = state.tripActive || state.canStartTrip,
                        iconRes = if (state.tripActive) R.drawable.ic_stop else R.drawable.ic_play, onClick = actions.onToggleTrip)
                }
                if (state.tripActive && state.buttonMode)
                    GlassIconButton(R.drawable.ic_mic, stringResource(R.string.trip_manual_command),
                        enabled = state.canRequestTripCommand, onClick = actions.onManualTripCommand)
            }
            if (state.pendingAppName != null) ActionButton(stringResource(R.string.prepared_app_title), onClick = openTrip)
            if (state.standaloneListening) ActionButton(stringResource(R.string.listen_cancel), destructive = true,
                onClick = actions.onCancelStandaloneCommand)
        }
    }
}
