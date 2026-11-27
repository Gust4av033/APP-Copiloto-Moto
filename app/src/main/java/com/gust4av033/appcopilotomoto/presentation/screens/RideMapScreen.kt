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
    val compactPositionLabel = when (positionLabel) {
        R.string.map_fix_short -> R.string.map_gps_short_live
        R.string.map_last_short -> R.string.map_gps_short_last
        R.string.map_gps_disabled -> R.string.map_gps_short_disabled
        R.string.trip_map_gps_unavailable -> R.string.map_gps_short_unavailable
        R.string.trip_map_projection_unavailable -> R.string.map_gps_short_projection
        R.string.map_gps_pending -> R.string.map_gps_short_waiting
        else -> R.string.map_gps_short_world
    }
    val compactAudioLabel = when (audioLabel) {
        R.string.map_audio_unknown -> R.string.map_audio_short_unknown
        R.string.map_audio_headset -> R.string.map_audio_short_headset
        else -> R.string.map_audio_short_none
    }
    val density = LocalDensity.current
    var dockHeight by remember { mutableStateOf(0.dp) }
    var statusHeight by remember { mutableStateOf(0.dp) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight && maxWidth >= 520.dp
        val bottomMax = maxHeight * .44f
        val largeFont = density.fontScale >= 1.5f
        val statusMaxWidth = maxWidth - Glass.mapScreenInset * 2 -
            if (largeFont) 0.dp else Glass.mapCameraControlsWidth + Glass.mapControlGap
        val mapArea: @Composable (Modifier, Boolean) -> Unit = { area, withDockInset ->
            Box(area.testTag("ride_map_viewport")) {
                CompositionLocalProvider(
                    LocalTripMapBottomInset provides if (withDockInset) dockHeight + Glass.mapDockOuterInset else 0.dp,
                    LocalTripMapTopInset provides if (largeFont && !state.tripActive)
                        statusHeight + Glass.mapControlGap else 0.dp,
                    LocalTripMapActive provides state.tripActive
                ) {
                    key(gps) {
                        LocalTripMapRenderer.current.Render(mappedSession, now, state.mapOnlineEnabled)
                    }
                }
                if (!state.tripActive) {
                    MapStatusPill(
                        state = state,
                        positionLabel = positionLabel,
                        audioLabel = audioLabel,
                        compactPositionLabel = compactPositionLabel,
                        compactAudioLabel = compactAudioLabel,
                        onClick = { detailsOpen = true },
                        modifier = Modifier.align(Alignment.TopStart)
                            .widthIn(max = statusMaxWidth)
                            .padding(Glass.mapScreenInset)
                            .onSizeChanged { statusHeight = with(density) { it.height.toDp() } }
                    )
                }
            }
        }
        val dock: @Composable (Modifier) -> Unit = { area ->
            RideDock(state, actions, mappedSession, now, navigate, { detailsOpen = true }, area)
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

    if (detailsOpen) {
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
private fun MapStatusPill(
    state: CopilotUiState,
    positionLabel: Int,
    audioLabel: Int,
    compactPositionLabel: Int,
    compactAudioLabel: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fullDescription = stringResource(
        R.string.map_status_description,
        stringResource(positionLabel),
        stringResource(audioLabel),
        stringResource(if (state.tripActive) R.string.map_session_on else R.string.map_session_idle)
    )
    GlassPanel(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = fullDescription
            }
            .testTag("map_status_pill"),
        shape = CircleShape,
        mode = GlassMode.LITE
    ) {
        Row(
            Modifier.heightIn(min = Glass.mapStatusHeight)
                .padding(horizontal = Glass.mapStatusHorizontalPadding, vertical = Glass.mapStatusVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Glass.mapStatusGap)
        ) {
            Image(painterResource(R.drawable.ic_ignitor_vector), null, Modifier.size(Glass.mapStatusAvatarSize))
            MapStatusPart(R.drawable.ic_center_fix, stringResource(compactPositionLabel))
            MapStatusPart(R.drawable.ic_audio, stringResource(compactAudioLabel))
            MapWeatherTemperature(state)?.let { temperature ->
                Text(
                    temperature,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MapStatusPart(icon: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Glass.mapStatusIconGap)) {
        Icon(painterResource(icon), null, Modifier.size(Glass.mapStatusIconSize),
            tint = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        title = { Text(stringResource(R.string.map_details_title)) },
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
                if (state.tripActive || state.sessionProblem) SupportingText(stringResource(sessionProgress(state)))
                MapDetailLine(stringResource(R.string.map_audio_label), stringResource(audioLabel))
                SupportingText(stringResource(R.string.weather_title))
                MapWeatherDetails(state, actions)

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
                SupportingText(stringResource(if (state.mapOnlineEnabled)
                    R.string.trip_map_online_hint else R.string.trip_map_offline_hint))
                SupportingText(stringResource(R.string.map_road_coverage))
                SupportingText(stringResource(R.string.map_buildings_hint))
                SupportingText(stringResource(R.string.map_forest_hint))
                ActionButton(
                    stringResource(R.string.trip_map_open_navigator, state.navigationProvider.label),
                    enabled = state.navigationAvailable,
                    iconRes = R.drawable.ic_trip,
                    onClick = { onDismiss(); actions.onOpenTripNavigator() }
                )
                if (!state.navigationAvailable) SupportingText(stringResource(R.string.trip_map_navigator_unavailable))
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
    openDetails: () -> Unit,
    modifier: Modifier
) {
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    GlassPanel(
        modifier = modifier.testTag("map_dock"),
        shape = RoundedCornerShape(Glass.mapDockRadius),
        mode = GlassMode.LITE
    ) {
        if (state.tripActive) {
            ActiveDockContent(actions, mapped, now, openDetails, largeFont)
        } else if (largeFont) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Glass.mapDockPadding, vertical = Glass.mapDockVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(Glass.mapDockGap)
            ) {
                RideMapDockButton(
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
                RideMapDockButton(
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
    openDetails: () -> Unit,
    largeFont: Boolean
) {
    val speed = mapped.speedKmh(now)
    val detailDescription = stringResource(R.string.map_trip_details)
    val speedText = speed?.let { String.format(Locale.getDefault(), "%.0f", it) }
        ?: stringResource(R.string.trip_map_no_value)
    if (largeFont) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Glass.mapDockPadding, vertical = Glass.mapDockVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(Glass.mapDockGap)
        ) {
            TripSpeedAction(speedText, detailDescription, openDetails, Modifier.fillMaxWidth())
            RideMapDockButton(
                label = stringResource(R.string.map_stop_short),
                icon = R.drawable.ic_stop,
                destructive = true,
                onClick = actions.onToggleTrip,
                modifier = Modifier.fillMaxWidth().testTag("map_stop")
            )
        }
    } else {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Glass.mapDockHeight)
                .padding(horizontal = Glass.mapDockPadding, vertical = Glass.mapDockVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Glass.mapDockGap)
        ) {
            TripSpeedAction(speedText, detailDescription, openDetails, Modifier.weight(1f))
            RideMapDockButton(
                label = stringResource(R.string.map_stop_short),
                icon = R.drawable.ic_stop,
                destructive = true,
                onClick = actions.onToggleTrip,
                modifier = Modifier.weight(1f).testTag("map_stop")
            )
        }
    }
}

@Composable
private fun TripSpeedAction(
    speed: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier.heightIn(min = Glass.mapDockButtonHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .testTag("map_speed"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Glass.mapDockGap)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Glass.mapStatusIconGap)) {
            Text(stringResource(R.string.map_speed_label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(
                speed,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
        Icon(painterResource(R.drawable.ic_arrow_forward), null, Modifier.size(Glass.mapStatusIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
