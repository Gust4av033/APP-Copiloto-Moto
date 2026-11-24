package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import java.util.Locale

@Composable
fun TripMapCard(state: CopilotUiState, actions: CopilotActions) {
    val session = state.tripMap
    val now = state.observedRouteAtElapsedMs
    val fix = session.lastFix?.value
    val locationAllowed = state.tripLocationEnabled && state.tripLocationAvailable
    val uri = LocalUriHandler.current
    SectionCard(stringResource(R.string.trip_map_title)) {
        Text(stringResource(if (session.endedAtElapsedMs != null) R.string.trip_map_previous_session
            else if (state.tripActive) R.string.trip_map_current_session else R.string.trip_map_before_start),
            style = MaterialTheme.typography.labelLarge)
        val duration = session.durationMs(now)?.let { ms ->
            val seconds = ms / 1000
            String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        } ?: stringResource(R.string.trip_map_no_value)
        val distance = session.distanceMeters?.let {
            stringResource(R.string.trip_map_km, String.format(Locale.getDefault(), "%.2f", it / 1000))
        } ?: stringResource(R.string.trip_map_no_value)
        val speed = session.speedKmh(now)?.takeIf { locationAllowed }?.let {
            stringResource(R.string.trip_map_kmh, String.format(Locale.getDefault(), "%.0f", it))
        } ?: stringResource(R.string.trip_map_no_value)
        if (LocalDensity.current.fontScale < 1.5f) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TripMapMetric(stringResource(R.string.trip_map_duration), duration, Modifier.weight(1f))
                TripMapMetric(stringResource(R.string.trip_map_distance), distance, Modifier.weight(1f))
            }
        } else {
            TripMapMetric(stringResource(R.string.trip_map_duration), duration)
            TripMapMetric(stringResource(R.string.trip_map_distance), distance)
        }
        TripMapMetric(stringResource(R.string.trip_map_speed), speed)
        SupportingText(stringResource(R.string.trip_map_metrics_hint))
        if (fix != null && locationAllowed && fix.latitude in -TripMapStyle.MAX_LATITUDE..TripMapStyle.MAX_LATITUDE) {
            Text(stringResource(if (session.currentFix(now) != null) R.string.trip_map_fix_current else R.string.trip_map_fix_last),
                style = MaterialTheme.typography.labelLarge)
            SupportingText(stringResource(R.string.trip_map_accuracy,
                String.format(Locale.getDefault(), "%.0f", fix.horizontalAccuracyMeters ?: 0f)))
            LocalTripMapRenderer.current.Render(session, now, state.mapOnlineEnabled)
        } else SupportingText(stringResource(when {
            !state.tripLocationEnabled -> R.string.trip_map_gps_off
            !state.tripLocationAvailable -> R.string.trip_map_gps_unavailable
            fix != null -> R.string.trip_map_projection_unavailable
            state.tripActive -> R.string.trip_map_wait_fix
            else -> R.string.trip_map_start_to_locate
        }))
        if (session.simplified) SupportingText(stringResource(R.string.trip_map_simplified))
        if (session.endedAtElapsedMs != null) SupportingText(stringResource(R.string.trip_map_summary_memory))
        ToggleSettingRow(stringResource(R.string.trip_location_title), state.tripLocationEnabled,
            enabled = !state.busy, onCheckedChange = actions.onTripLocationChange)
        ToggleSettingRow(stringResource(R.string.trip_map_online_title), state.mapOnlineEnabled,
            enabled = state.mapOnlineEnabled || !state.busy, onCheckedChange = actions.onMapOnlineChange)
        SupportingText(stringResource(if (state.mapOnlineEnabled) R.string.trip_map_online_hint else R.string.trip_map_offline_hint))
        ActionButton(stringResource(R.string.trip_map_open_navigator, state.navigationProvider.label),
            enabled = state.navigationAvailable, iconRes = R.drawable.ic_trip, onClick = actions.onOpenTripNavigator)
        if (!state.navigationAvailable) SupportingText(stringResource(R.string.trip_map_navigator_unavailable))
        SupportingText(stringResource(R.string.trip_map_scope))
    }
}

@Composable
private fun TripMapMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}
