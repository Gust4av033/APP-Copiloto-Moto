package com.gust4av033.appcopilotomoto.presentation.screens

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RideMapScreen(state: CopilotUiState, actions: CopilotActions, navigate: (CopilotScreen) -> Unit,
    modifier: Modifier = Modifier, phase: RideUiPhase = RideUiPhase.IDLE) {
    val riding = phase == RideUiPhase.RIDING
    val gps = state.tripLocationEnabled && state.tripLocationAvailable
    val fix = state.tripMap.lastFix?.value
    val showPosition = gps && fix != null && fix.latitude in -TripMapStyle.MAX_LATITUDE..TripMapStyle.MAX_LATITUDE
    val empty = remember { TripMapSession() }
    // Keep the last known position/camera at rest, without presenting old trip metrics or a live fix.
    val mappedSession = when {
        !showPosition -> empty
        state.tripActive -> state.tripMap
        else -> state.tripMap.copy(segments = emptyList(), distanceMeters = null,
            endedAtElapsedMs = state.tripMap.endedAtElapsedMs ?: fix?.elapsedRealtimeMs)
    }
    val now = state.observedRouteAtElapsedMs
    val positionLabel = when {
        mappedSession.currentFix(now) != null -> R.string.map_fix_short
        showPosition -> R.string.map_last_short
        !state.tripLocationEnabled -> R.string.map_gps_disabled
        !state.tripLocationAvailable -> R.string.trip_map_gps_unavailable
        fix != null && state.tripActive -> R.string.trip_map_projection_unavailable
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
                    LocalTripMapTopInset provides headerHeight, LocalTripMapRiding provides riding) {
                    key(gps) { LocalTripMapRenderer.current.Render(mappedSession, now, state.mapOnlineEnabled) }
                }
                Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 12.dp)
                    .heightIn(max = topMax).verticalScroll(rememberScrollState())) {
                    Column(Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() }.coerceAtMost(topMax) + Glass.cockpitGap }) {
                        if (!riding) CockpitHeader({ navigate(CopilotScreen.HOME) }, { options = true })
                        val audioWarning = state.sessionProblem || !state.audioDevices.permissionGranted ||
                            state.audioDevices.headsets.isEmpty() && state.audioDevices.bluetoothOutputs.isEmpty()
                        if (!riding) FlowRow(horizontalArrangement = Arrangement.spacedBy(Glass.cockpitGap),
                            verticalArrangement = Arrangement.spacedBy(Glass.rideSmallGap)) {
                            MapStatusPill(R.drawable.ic_center_fix, stringResource(positionLabel))
                            MapStatusPill(R.drawable.ic_audio, stringResource(when {
                                !state.audioDevices.permissionGranted -> R.string.map_audio_unknown
                                state.audioDevices.headsets.isNotEmpty() || state.audioDevices.bluetoothOutputs.isNotEmpty() -> R.string.map_audio_headset
                                else -> R.string.map_audio_none
                            }))
                        } else if (mappedSession.currentFix(now) == null || audioWarning) {
                            MapOverlayPanel(Modifier.fillMaxWidth(), riding = true) {
                                Column(Modifier.padding(Glass.cockpitGap)) {
                                    if (mappedSession.currentFix(now) == null) Text(stringResource(R.string.ride_hud_gps_warning),
                                        color = Glass.rideWarning, style = MaterialTheme.typography.labelMedium)
                                    if (audioWarning) Text(stringResource(R.string.ride_hud_audio_warning),
                                        color = Glass.rideWarning, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        if (state.listening || state.standaloneListening || state.tripActive &&
                            state.interactionPhase == com.gust4av033.appcopilotomoto.TripInteractionPhase.LISTENING) {
                            MapOverlayPanel(Modifier.fillMaxWidth().border(Glass.rideListeningBorder,
                                Glass.rideInk, RoundedCornerShape(Glass.cardRadius)), riding = true) {
                                Row(Modifier.padding(Glass.cockpitGap), horizontalArrangement = Arrangement.spacedBy(Glass.cockpitGap),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(R.drawable.ic_mic), null, Modifier.size(Glass.iconSize))
                                    Text(stringResource(R.string.ride_hud_listening), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        if (state.tripActive) ManeuverBanner(state.routeHud)
                    }
                    Spacer(Modifier.height(Glass.cockpitGap))
                    if (!riding) MapWeather(state, actions, Modifier.widthIn(max = Glass.mapWeatherWidth), compact = true)
                }
            }
        }
        val dock: @Composable (Modifier) -> Unit = { area ->
            RideDock(state, actions, mappedSession, now, navigate, area, riding)
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
    LaunchedEffect(riding) { if (riding) options = false }
    if (options && !riding) AlertDialog(onDismissRequest = { options = false },
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
                SupportingText(stringResource(R.string.map_road_coverage))
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
    Row(Modifier.fillMaxWidth().padding(vertical = Glass.cockpitGap), verticalAlignment = Alignment.CenterVertically) {
        val label = stringResource(R.string.nav_home)
        MapOverlayPanel(Modifier.clickable(role = Role.Button, onClick = home).semantics { contentDescription = label }) {
            Row(Modifier.heightIn(min = Glass.iconButtonSize).padding(horizontal = Glass.ridePanelPadding),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Glass.cockpitGap)) {
                Image(painterResource(R.drawable.ic_ignitor_vector), null, Modifier.size(Glass.mapBrandIconSize))
                Text(stringResource(R.string.map_brand), style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.weight(1f))
        MapOverlayPanel {
            GlassIconButton(R.drawable.ic_map_layers, stringResource(R.string.map_options), onClick = options)
        }
    }
}

@Composable
private fun MapStatusPill(icon: Int, value: String) {
    MapOverlayPanel {
        Row(Modifier.padding(horizontal = Glass.cockpitGap, vertical = Glass.rideSmallGap),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Glass.rideSmallGap)) {
            Icon(painterResource(icon), null, Modifier.size(Glass.mapStatusIconSize))
            Text(value, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MapDockShortcut(label: String, icon: Int, modifier: Modifier, action: () -> Unit) {
    Column(modifier.heightIn(min = Glass.cockpitButtonHeight)
        .clickable(role = Role.Button, onClick = action).semantics { contentDescription = label }
        .padding(Glass.cockpitGap), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Glass.rideSmallGap, Alignment.CenterVertically)) {
        Icon(painterResource(icon), null, Modifier.size(Glass.iconSize))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun CockpitStatus(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current)
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RideDock(state: CopilotUiState, actions: CopilotActions, mapped: TripMapSession, now: Long,
    navigate: (CopilotScreen) -> Unit, modifier: Modifier, riding: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val details = stringResource(R.string.map_trip_details)
        if (state.tripActive) MapOverlayPanel(Modifier.fillMaxWidth().clickable(enabled = !riding, role = Role.Button) {
            navigate(CopilotScreen.TRIP)
        }.semantics { contentDescription = details }, riding = true) {
            Column(Modifier.fillMaxWidth().padding(Glass.ridePanelPadding), verticalArrangement = Arrangement.spacedBy(Glass.rideSmallGap)) {
                EtaBar(state.routeHud)
                HudNumber(mapped.speedKmh(now)?.let { String.format(Locale.getDefault(), "%.0f", it) },
                    stringResource(R.string.ride_hud_kmh), large = state.routeHud == null)
                if (!riding) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Glass.cockpitGap)) {
                    CockpitStatus(stringResource(R.string.map_distance_short), mapped.distanceMeters?.let {
                        stringResource(R.string.trip_map_km, String.format(Locale.getDefault(), "%.1f", it / 1000))
                    } ?: stringResource(R.string.ride_hud_unavailable), Modifier.weight(1f))
                    CockpitStatus(stringResource(R.string.map_duration_short), state.tripMap.durationMs(now)?.let {
                        DateUtils.formatElapsedTime(it / 1000)
                    } ?: stringResource(R.string.ride_hud_unavailable), Modifier.weight(1f))
                }
            }
        }
        if (!riding) actions.onChooseMapDestination?.let { choose ->
            ActionButton(stringResource(R.string.ride_choose_destination), onClick = choose)
        }
        if (!riding && !state.tripLocationEnabled && !state.busy)
            ActionButton(stringResource(R.string.map_enable_location), onClick = { actions.onTripLocationChange(true) })
        if (riding) HoldToStopButton(Modifier.fillMaxWidth(), onStop = actions.onToggleTrip)
        else MapOverlayPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Glass.cockpitGap), verticalArrangement = Arrangement.spacedBy(Glass.rideSmallGap)) {
                if (state.tripActive) HoldToStopButton(Modifier.fillMaxWidth(), onStop = actions.onToggleTrip)
                else CockpitButton(stringResource(R.string.map_start_short), R.drawable.ic_play,
                    Modifier.fillMaxWidth(), primary = true, enabled = state.canStartTrip, inline = true,
                    onClick = actions.onToggleTrip)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    MapDockShortcut(stringResource(R.string.module_motorcycle), R.drawable.ic_motorcycle,
                        Modifier.weight(1f).fillMaxHeight()) { navigate(CopilotScreen.MOTORCYCLE) }
                    MapDockShortcut(stringResource(R.string.nav_trip), R.drawable.ic_trip,
                        Modifier.weight(1f).fillMaxHeight()) { navigate(CopilotScreen.TRIP) }
                    MapDockShortcut(stringResource(R.string.nav_settings), R.drawable.ic_settings,
                        Modifier.weight(1f).fillMaxHeight()) { navigate(CopilotScreen.SETTINGS) }
                }
            }
        }
        if (!riding && state.tripActive && state.buttonMode) ActionButton(stringResource(R.string.trip_manual_command),
            enabled = state.canRequestTripCommand, iconRes = R.drawable.ic_mic, onClick = actions.onManualTripCommand)
        if (!riding && state.pendingAppName != null) ActionButton(stringResource(R.string.prepared_app_title)) { navigate(CopilotScreen.TRIP) }
        if (!riding && state.standaloneListening) ActionButton(stringResource(R.string.listen_cancel), destructive = true,
            onClick = actions.onCancelStandaloneCommand)
    }
}
