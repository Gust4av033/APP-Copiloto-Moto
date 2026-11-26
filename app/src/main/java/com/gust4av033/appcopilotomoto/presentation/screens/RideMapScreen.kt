package com.gust4av033.appcopilotomoto.presentation.screens

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.TripMapSession
import com.gust4av033.appcopilotomoto.presentation.*
import com.gust4av033.appcopilotomoto.presentation.components.*
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
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
        !state.tripLocationEnabled -> R.string.map_gps_disabled
        !state.tripLocationAvailable -> R.string.trip_map_gps_unavailable
        fix != null -> R.string.trip_map_projection_unavailable
        state.tripActive -> R.string.map_gps_pending
        else -> R.string.map_world
    }
    val density = LocalDensity.current
    var dockHeight by remember { mutableStateOf(0.dp) }
    var headerHeight by remember { mutableStateOf(0.dp) }
    var options by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight && maxWidth >= 520.dp
        val topMax = maxHeight * if (density.fontScale >= 1.5f) .43f else .36f
        val bottomMax = maxHeight * .44f
        val mapArea: @Composable (Modifier, Boolean) -> Unit = { area, withDockInset ->
            Box(area.testTag("ride_map_viewport")) {
                CompositionLocalProvider(LocalTripMapBottomInset provides if (withDockInset) dockHeight else 0.dp,
                    LocalTripMapTopInset provides headerHeight) {
                    key(gps) { LocalTripMapRenderer.current.Render(mappedSession, now, state.mapOnlineEnabled) }
                }
                Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 12.dp)
                    .heightIn(max = topMax).verticalScroll(rememberScrollState())) {
                    Column(Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } + 8.dp }) {
                        CockpitHeader({ navigate(CopilotScreen.HOME) }, { options = true })
                        MapOverlayPanel(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CockpitStatus(stringResource(R.string.map_gps_label), stringResource(positionLabel), Modifier.weight(1f))
                                CockpitStatus(stringResource(R.string.map_session_label), stringResource(if (state.tripActive)
                                    R.string.map_session_on else R.string.map_session_idle), Modifier.weight(1f))
                                val audio = if (!state.audioDevices.permissionGranted) R.string.map_audio_unknown else if (state.audioDevices.headsets.isNotEmpty() || state.audioDevices.bluetoothOutputs.isNotEmpty())
                                    R.string.map_audio_headset else R.string.map_audio_none
                                CockpitStatus(stringResource(R.string.map_audio_label), stringResource(audio), Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    MapWeather(state, actions, Modifier.widthIn(max = 188.dp))
                }
            }
        }
        val dock: @Composable (Modifier) -> Unit = { area ->
            RideDock(state, actions, mappedSession, now, navigate, area)
        }
        if (landscape) Row(Modifier.fillMaxSize()) {
            mapArea(Modifier.weight(1f).fillMaxHeight(), false)
            dock(Modifier.width(240.dp).fillMaxHeight().padding(8.dp).verticalScroll(rememberScrollState()))
        } else {
            mapArea(Modifier.fillMaxSize(), true)
            dock(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().heightIn(max = bottomMax)
                .onSizeChanged { dockHeight = with(density) { it.height.toDp() } + 24.dp }
                .verticalScroll(rememberScrollState()))
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
                SupportingText(stringResource(R.string.map_buildings_hint))
                SupportingText(stringResource(R.string.map_forest_hint))
                if (tripMapHeadingDegrees(state.tripMap.currentFix(now)) == null)
                    SupportingText(stringResource(R.string.trip_map_camera_waiting_heading))
                ActionButton(stringResource(R.string.trip_map_open_navigator, state.navigationProvider.label),
                    enabled = state.navigationAvailable) {
                    options = false
                    actions.onOpenTripNavigator()
                }
            }
        }, confirmButton = { TextButton(onClick = { options = false }) { Text(stringResource(R.string.ride_done)) } })
}

@Composable
private fun CockpitHeader(home: () -> Unit, options: () -> Unit) {
    MapOverlayPanel(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val label = stringResource(R.string.nav_home)
        Row(Modifier.weight(1f).clickable(role = Role.Button, onClick = home).semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.ic_ignitor_vector), null, Modifier.size(Glass.iconButtonSize))
            Column {
                Text(stringResource(R.string.map_brand), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.map_brand_subtitle), style = MaterialTheme.typography.labelMedium)
            }
        }
        GlassIconButton(R.drawable.ic_map_layers, stringResource(R.string.map_options), onClick = options)
    }
    }
}

@Composable
private fun CockpitStatus(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RideDock(state: CopilotUiState, actions: CopilotActions, mapped: TripMapSession, now: Long,
    navigate: (CopilotScreen) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val details = stringResource(R.string.map_trip_details)
        MapOverlayPanel(Modifier.fillMaxWidth().clickable(role = Role.Button) { navigate(CopilotScreen.TRIP) }
            .semantics { contentDescription = details }) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.tripActive || state.sessionProblem) SupportingText(stringResource(sessionProgress(state)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CockpitStatus(stringResource(R.string.map_distance_short), mapped.distanceMeters?.let {
                        stringResource(R.string.trip_map_km, String.format(Locale.getDefault(), "%.1f", it / 1000))
                    } ?: "—", Modifier.weight(1f))
                    CockpitStatus(stringResource(R.string.map_duration_short), state.tripMap.durationMs(now)?.let {
                        DateUtils.formatElapsedTime(it / 1000)
                    } ?: "—", Modifier.weight(1f))
                    CockpitStatus(stringResource(R.string.map_speed_short), mapped.speedKmh(now)?.let {
                        stringResource(R.string.trip_map_kmh, String.format(Locale.getDefault(), "%.0f", it))
                    } ?: "—", Modifier.weight(1f))
                }
            }
        }
        if (!state.tripLocationEnabled && !state.busy)
            ActionButton(stringResource(R.string.map_enable_location), onClick = { actions.onTripLocationChange(true) })
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CockpitButton(stringResource(R.string.module_motorcycle), R.drawable.ic_motorcycle,
                Modifier.weight(1f).fillMaxHeight()) { navigate(CopilotScreen.MOTORCYCLE) }
            CockpitButton(stringResource(if (state.tripActive) R.string.map_stop_short else R.string.map_start_short),
                if (state.tripActive) R.drawable.ic_stop else R.drawable.ic_play, Modifier.weight(if (LocalDensity.current.fontScale >= 1.5f) 1f else 1.5f).fillMaxHeight(),
                primary = !state.tripActive, destructive = state.tripActive, enabled = state.tripActive || state.canStartTrip,
                onClick = actions.onToggleTrip)
            CockpitButton(stringResource(R.string.nav_settings), R.drawable.ic_settings,
                Modifier.weight(1f).fillMaxHeight()) { navigate(CopilotScreen.SETTINGS) }
        }
        if (state.tripActive && state.buttonMode) ActionButton(stringResource(R.string.trip_manual_command),
            enabled = state.canRequestTripCommand, iconRes = R.drawable.ic_mic, onClick = actions.onManualTripCommand)
        if (state.pendingAppName != null) ActionButton(stringResource(R.string.prepared_app_title)) { navigate(CopilotScreen.TRIP) }
        if (state.standaloneListening) ActionButton(stringResource(R.string.listen_cancel), destructive = true,
            onClick = actions.onCancelStandaloneCommand)
    }
}
