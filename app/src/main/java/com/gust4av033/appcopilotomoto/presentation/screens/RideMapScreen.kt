package com.gust4av033.appcopilotomoto.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.TripMapSession
import com.gust4av033.appcopilotomoto.presentation.*
import com.gust4av033.appcopilotomoto.presentation.components.*
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import com.gust4av033.appcopilotomoto.presentation.theme.GlassMode
import com.gust4av033.appcopilotomoto.presentation.theme.GlassPanel
import java.util.Locale

@Composable
internal fun RideMapScreen(
    state: CopilotUiState,
    actions: CopilotActions,
    navigate: (CopilotScreen) -> Unit,
    modifier: Modifier = Modifier
) {
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
    val audioLabel = when {
        !state.audioDevices.permissionGranted -> R.string.map_audio_unknown
        state.audioDevices.headsets.isNotEmpty() || state.audioDevices.bluetoothOutputs.isNotEmpty() ->
            R.string.map_audio_headset
        else -> R.string.map_audio_none
    }
    val speed = mappedSession.speedKmh(now)
    val sample = mappedSession.currentFix(now)
    var phaseMemory by remember { mutableStateOf(RideUiPhaseMemory()) }
    LaunchedEffect(state.tripActive, sample, speed) {
        phaseMemory = rideUiPhase(phaseMemory, state.tripActive, speed, mappedSession.lastFix?.capturedAtElapsedRealtimeMs.takeIf { sample != null })
    }
    // Unknown/stale speed never unlocks controls during a session.
    val stopped = !state.tripActive || phaseMemory.phase == RideUiPhase.STOPPED_ON_ROUTE && speed != null && speed < 3f
    val density = LocalDensity.current
    var dockHeight by remember { mutableStateOf(0.dp) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(stopped) { if (!stopped) detailsOpen = false }

    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight && maxWidth >= 520.dp
        val bottomMax = maxHeight * .44f
        val statusMaxWidth = maxWidth - (if (landscape) Glass.mapLandscapeDockWidth else 0.dp) -
            Glass.mapScreenInset * 2 - Glass.mapCameraControlsWidth - Glass.mapControlGap
        val mapArea: @Composable (Modifier, Boolean) -> Unit = { area, withDockInset ->
            Box(area.testTag("ride_map_viewport")) {
                CompositionLocalProvider(
                    LocalTripMapBottomInset provides if (withDockInset) dockHeight + Glass.mapDockOuterInset else 0.dp,
                    LocalTripMapTopInset provides Glass.mapStatusIconGap,
                    LocalTripMapStopped provides (state.tripActive && stopped),
                    LocalTripMapNavigator provides if (state.navigationAvailable) actions.onOpenTripNavigator else null,
                    LocalTripMapNavigatorLabel provides stringResource(R.string.trip_map_open_navigator, state.navigationProvider.label),
                    LocalTripMapCameraInsets provides PaddingValues(start = Glass.mapScreenInset,
                        top = Glass.iconButtonSize + Glass.mapScreenInset * 2,
                        end = Glass.iconButtonSize + Glass.mapScreenInset * 2,
                        bottom = (if (withDockInset) dockHeight + Glass.mapDockOuterInset else 0.dp) + Glass.iconButtonSize + Glass.mapScreenInset),
                    LocalTripMapActive provides state.tripActive
                ) {
                    key(gps) {
                        LocalTripMapRenderer.current.Render(mappedSession, now, state.mapOnlineEnabled)
                    }
                }
                MapStatusPill(state, positionLabel, audioLabel, compact = state.tripActive && !stopped,
                    showCondition = statusMaxWidth >= Glass.mapCapsuleConditionWidth,
                    enabled = stopped, onClick = { detailsOpen = true },
                    modifier = Modifier.align(Alignment.TopStart).padding(Glass.mapScreenInset)
                        .widthIn(max = statusMaxWidth))
            }
        }
        val dock: @Composable (Modifier) -> Unit = { area ->
            RideDock(state, actions, mappedSession, now, navigate, stopped, area)
        }
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                mapArea(Modifier.weight(1f).fillMaxHeight(), false)
                dock(Modifier.width(Glass.mapLandscapeDockWidth).fillMaxHeight()
                    .padding(Glass.mapScreenInset).verticalScroll(rememberScrollState()))
            }
        } else {
            mapArea(Modifier.fillMaxSize(), true)
            dock(
                Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = Glass.mapScreenInset, vertical = Glass.mapDockOuterInset)
                    .fillMaxWidth()
                    .heightIn(max = bottomMax)
                    .onSizeChanged { dockHeight = with(density) { it.height.toDp() } }
                    .verticalScroll(rememberScrollState())
            )
        }
    }

    if (detailsOpen && stopped) {
        MapDetailsDialog(
            state = state,
            positionLabel = positionLabel,
            audioLabel = audioLabel,
            actions = actions,
            navigate = navigate,
            onDismiss = { detailsOpen = false }
        )
    }
}

@Composable
private fun MapStatusPill(state: CopilotUiState, positionLabel: Int, audioLabel: Int, compact: Boolean, showCondition: Boolean,
    enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    val gpsProblem = positionLabel != R.string.map_fix_short
    val audioProblem = audioLabel != R.string.map_audio_headset
    val weather = state.weather.usable
    val rain = weather?.hourly?.firstOrNull { it.epochMs in state.weather.nowEpochMs..(state.weather.nowEpochMs + 3_600_000) }
        ?.rainProbability?.let { it >= Glass.mapRainWarningPercent } == true
    val description = stringResource(R.string.map_capsule_description, stringResource(positionLabel), stringResource(audioLabel)) +
        if (rain) " · " + stringResource(R.string.map_rain_warning) else ""
    GlassPanel(modifier = modifier.height(Glass.iconButtonSize)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }.testTag("map_status_pill"),
        shape = CircleShape, mode = GlassMode.LITE) {
        Row(Modifier.height(Glass.iconButtonSize).padding(horizontal = Glass.mapStatusHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Glass.mapStatusIconGap)) {
            if (!compact && !largeFont) {
                Image(painterResource(R.drawable.ic_ignitor_vector), null, Modifier.size(Glass.mapStatusAvatarSize))
                Icon(painterResource(R.drawable.ic_center_fix), stringResource(positionLabel), Modifier.size(Glass.mapStatusIconSize))
                Icon(painterResource(R.drawable.ic_audio), stringResource(audioLabel), Modifier.size(Glass.mapStatusIconSize))
            }
            MapWeather(state, showCondition = showCondition && !largeFont && !compact)
            if (gpsProblem || audioProblem || rain) Text(stringResource(R.string.map_problem_badge),
                Modifier.semantics { contentDescription = description },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MapDetailsDialog(
    state: CopilotUiState,
    positionLabel: Int,
    audioLabel: Int,
    actions: CopilotActions,
    navigate: (CopilotScreen) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_details_title), maxLines = 2) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).testTag("map_details_content"),
                verticalArrangement = Arrangement.spacedBy(Glass.mapDetailsGap)
            ) {
                MapDetailLine(stringResource(R.string.map_gps_label), stringResource(positionLabel))
                MapDetailLine(
                    stringResource(R.string.map_session_label),
                    stringResource(if (state.tripActive) R.string.map_session_on else R.string.map_session_idle)
                )
                if (state.tripActive || state.sessionProblem) MapHint(stringResource(sessionProgress(state)))
                MapDetailLine(stringResource(R.string.map_audio_label), stringResource(audioLabel))
                HorizontalDivider()
                MapHint(stringResource(R.string.weather_title))
                MapWeatherDetails(state, actions)

                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(top = Glass.mapDetailsGap),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Glass.mapStatusGap)
                ) {
                    Icon(painterResource(R.drawable.ic_more_options), null,
                        Modifier.size(Glass.mapStatusIconSize), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.map_more_options), style = MaterialTheme.typography.titleSmall)
                }
                if (!state.tripLocationEnabled) {
                    ActionButton(
                        stringResource(R.string.map_enable_location),
                        enabled = !state.busy,
                        iconRes = R.drawable.ic_center_fix,
                        onClick = { onDismiss(); actions.onTripLocationChange(true) }
                    )
                } else {
                    ToggleSettingRow(
                        stringResource(R.string.trip_location_title),
                        checked = true,
                        enabled = !state.busy,
                        onCheckedChange = actions.onTripLocationChange
                    )
                }
                ToggleSettingRow(
                    stringResource(R.string.trip_map_online_title),
                    checked = state.mapOnlineEnabled,
                    enabled = state.mapOnlineEnabled || !state.busy,
                    onCheckedChange = actions.onMapOnlineChange
                )
                MapHint(stringResource(if (state.mapOnlineEnabled)
                    R.string.trip_map_online_hint else R.string.trip_map_offline_hint))
                MapHint(stringResource(R.string.map_road_coverage))
                MapHint(stringResource(R.string.map_buildings_hint))
                MapHint(stringResource(R.string.map_forest_hint))
                ActionButton(
                    stringResource(R.string.trip_map_open_navigator, state.navigationProvider.label),
                    enabled = state.navigationAvailable,
                    iconRes = R.drawable.ic_trip,
                    onClick = { onDismiss(); actions.onOpenTripNavigator() }
                )
                if (!state.navigationAvailable) MapHint(stringResource(R.string.trip_map_navigator_unavailable))
                ActionButton(
                    stringResource(R.string.map_trip_details),
                    iconRes = R.drawable.ic_arrow_forward,
                    onClick = { onDismiss(); navigate(CopilotScreen.TRIP) }
                )
                ActionButton(
                    stringResource(R.string.module_motorcycle),
                    iconRes = R.drawable.ic_motorcycle,
                    onClick = { onDismiss(); navigate(CopilotScreen.MOTORCYCLE) }
                )
                ActionButton(
                    stringResource(R.string.nav_settings),
                    iconRes = R.drawable.ic_settings,
                    onClick = { onDismiss(); navigate(CopilotScreen.SETTINGS) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ride_done)) }
        }
    )
}

@Composable
private fun MapDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Glass.mapStatusGap)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RideDock(
    state: CopilotUiState,
    actions: CopilotActions,
    mapped: TripMapSession,
    now: Long,
    navigate: (CopilotScreen) -> Unit,
    stopped: Boolean,
    modifier: Modifier
) {
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    if (state.tripActive && !stopped) {
        Box(modifier.testTag("map_dock").clip(RoundedCornerShape(Glass.mapDockRadius))
            .background(Brush.verticalGradient(listOf(Glass.rideSurface.copy(alpha = Glass.rideSurfaceAlpha), Glass.rideSurface)))) { ActiveDockContent(actions, mapped, now, largeFont) }
        return
    }
    GlassPanel(
        modifier = modifier.testTag("map_dock"),
        shape = RoundedCornerShape(Glass.mapDockRadius),
        mode = GlassMode.LITE
    ) {
        if (largeFont) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Glass.mapDockPadding, vertical = Glass.mapDockVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(Glass.mapDockGap)
            ) {
                if (state.tripActive) HoldToStopButton(Modifier.fillMaxWidth(), "map_stop", actions.onToggleTrip)
                else RideMapDockButton(
                    label = stringResource(R.string.map_start_short),
                    icon = R.drawable.ic_play,
                    enabled = state.canStartTrip,
                    primary = true,
                    onClick = actions.onToggleTrip,
                    modifier = Modifier.fillMaxWidth().testTag("map_start")
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Glass.mapDockGap)
                ) {
                    DockDestination(
                        label = stringResource(R.string.module_motorcycle_short),
                        icon = R.drawable.ic_motorcycle,
                        description = stringResource(R.string.module_motorcycle),
                        onClick = { navigate(CopilotScreen.MOTORCYCLE) },
                        modifier = Modifier.weight(1f).testTag("map_motorcycle")
                    )
                    DockDestination(
                        label = stringResource(R.string.nav_settings),
                        icon = R.drawable.ic_settings,
                        description = stringResource(R.string.nav_settings),
                        onClick = { navigate(CopilotScreen.SETTINGS) },
                        modifier = Modifier.weight(1f).testTag("map_settings")
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Glass.mapDockHeight)
                    .padding(horizontal = Glass.mapDockPadding, vertical = Glass.mapDockVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Glass.mapDockGap)
            ) {
                DockDestination(
                    label = stringResource(R.string.module_motorcycle_short),
                    icon = R.drawable.ic_motorcycle,
                    description = stringResource(R.string.module_motorcycle),
                    onClick = { navigate(CopilotScreen.MOTORCYCLE) },
                    modifier = Modifier.weight(1f).testTag("map_motorcycle")
                )
                if (state.tripActive) HoldToStopButton(Modifier.weight(2f), "map_stop", actions.onToggleTrip)
                else RideMapDockButton(
                    label = stringResource(R.string.map_start_short),
                    icon = R.drawable.ic_play,
                    enabled = state.canStartTrip,
                    primary = true,
                    onClick = actions.onToggleTrip,
                    modifier = Modifier.weight(2f).testTag("map_start")
                )
                DockDestination(
                    label = stringResource(R.string.nav_settings),
                    icon = R.drawable.ic_settings,
                    description = stringResource(R.string.nav_settings),
                    onClick = { navigate(CopilotScreen.SETTINGS) },
                    modifier = Modifier.weight(1f).testTag("map_settings")
                )
            }
        }
    }
}

@Composable
private fun ActiveDockContent(
    actions: CopilotActions,
    mapped: TripMapSession,
    now: Long,
    largeFont: Boolean
) {
    val speed = mapped.speedKmh(now)
    val speedText = speed?.let { String.format(Locale.getDefault(), "%.0f", it) }
        ?: stringResource(R.string.trip_map_no_value)
    if (largeFont) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Glass.mapDockPadding, vertical = Glass.mapDockVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(Glass.mapDockGap)
        ) {
            TripSpeedAction(speedText, Modifier.fillMaxWidth())
            HoldToStopButton(modifier = Modifier.fillMaxWidth().testTag("map_stop"), testTagName = "map_stop", onStop = actions.onToggleTrip)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Glass.mapDockHeight)
                .padding(horizontal = Glass.mapDockPadding, vertical = Glass.mapDockVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Glass.mapDockGap)
        ) {
            TripSpeedAction(speedText, Modifier.weight(1f))
            HoldToStopButton(modifier = Modifier.weight(1f).testTag("map_stop"), testTagName = "map_stop", onStop = actions.onToggleTrip)
        }
    }
}

@Composable
private fun TripSpeedAction(
    speed: String,
    modifier: Modifier
) {
    Row(
        modifier.heightIn(min = Glass.mapDockButtonHeight)
            .testTag("map_speed"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Glass.mapDockGap)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Glass.mapStatusIconGap)) {
            Text(stringResource(R.string.map_speed_label), style = MaterialTheme.typography.labelMedium, color = Glass.rideMuted, maxLines = 1)
            Text(
                speed,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = Glass.rideInk,
                maxLines = 1
            )
        }
        Text(stringResource(R.string.map_speed_short), style = MaterialTheme.typography.labelSmall, color = Glass.rideMuted)
    }
}

@Composable
private fun DockDestination(
    label: String,
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.heightIn(min = Glass.mapDockButtonHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(icon), null, Modifier.size(Glass.iconSize), tint = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RideMapDockButton(
    label: String,
    icon: Int,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val fill = when {
        !enabled -> Brush.verticalGradient(listOf(colors.surfaceVariant, colors.surfaceVariant))
        destructive -> Brush.verticalGradient(listOf(Glass.destructiveTop, Glass.destructiveBottom))
        primary -> Brush.verticalGradient(listOf(Glass.primaryTop, Glass.primaryBottom))
        else -> Brush.verticalGradient(listOf(Glass.surface(), colors.secondaryContainer))
    }
    val foreground = when {
        !enabled -> colors.onSurfaceVariant
        primary || destructive -> androidx.compose.ui.graphics.Color.White
        else -> colors.onSurface
    }
    Row(
        modifier.heightIn(min = Glass.mapDockButtonHeight)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = Glass.mapDockButtonHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(icon), null, Modifier.size(Glass.mapDockIconSize), tint = foreground)
        Spacer(Modifier.width(Glass.mapStatusIconGap))
        Text(label, color = foreground, style = MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
