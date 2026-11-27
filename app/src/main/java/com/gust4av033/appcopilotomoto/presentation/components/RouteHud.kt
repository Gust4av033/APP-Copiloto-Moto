package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import java.util.Locale

/** Reserved for a real routing producer. Production deliberately supplies null today. */
data class RouteHudState(
    val distanceToManeuverM: Double?, val instruction: String?, val street: String?,
    val arrivalClock: String?, val remainingTime: String?, val remainingKm: Double?,
    val sourceLabel: String, val fresh: Boolean
)

@Composable
internal fun HudNumber(value: String?, unit: String, large: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(Glass.cockpitGap)) {
        Text(value ?: stringResource(R.string.ride_hud_unavailable),
            color = if (value == null) Glass.rideMuted else LocalContentColor.current,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = if (large) Glass.rideNumberSize else Glass.rideSecondaryNumberSize,
                lineHeight = if (large) Glass.rideNumberLineHeight else Glass.rideSecondaryNumberLineHeight,
                fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            modifier = Modifier.alignByBaseline())
        Text(unit, style = MaterialTheme.typography.labelMedium, modifier = Modifier.alignByBaseline())
    }
}

@Composable
fun ManeuverBanner(state: RouteHudState?) {
    if (state == null) return
    MapOverlayPanel(Modifier.fillMaxWidth(), riding = true) {
        Column(Modifier.padding(Glass.ridePanelPadding), verticalArrangement = Arrangement.spacedBy(Glass.rideSmallGap)) {
            val distance = state.distanceToManeuverM?.takeIf { state.fresh && it.isFinite() && it >= 0 }
            HudNumber(distance?.let { String.format(Locale.getDefault(), "%.0f", it) }, stringResource(R.string.ride_hud_meters))
            Text(if (state.fresh) state.instruction ?: stringResource(R.string.ride_hud_unavailable)
                else stringResource(R.string.ride_hud_expired), style = MaterialTheme.typography.titleMedium)
            if (state.fresh) state.street?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(state.sourceLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun EtaBar(state: RouteHudState?) {
    if (state == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Glass.rideSmallGap)) {
        HudNumber(state.arrivalClock?.takeIf { state.fresh }, stringResource(R.string.ride_hud_arrival))
        Text(if (state.fresh) stringResource(R.string.ride_hud_remaining,
            state.remainingTime ?: stringResource(R.string.ride_hud_unavailable),
            state.remainingKm?.takeIf { it.isFinite() && it >= 0 }?.let { String.format(Locale.getDefault(), "%.1f", it) }
                ?: stringResource(R.string.ride_hud_unavailable)) else stringResource(R.string.ride_hud_expired),
            style = MaterialTheme.typography.bodyMedium)
        Text(state.sourceLabel, style = MaterialTheme.typography.labelSmall)
    }
}
