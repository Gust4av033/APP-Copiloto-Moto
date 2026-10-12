package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.TripCallPhase
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

/** Resume el estado real sin abrir audio ni anticipar una ruta de navegación. */
@Composable
fun JourneyHero(state: CopilotUiState, tripScreen: Boolean, onPrimary: () -> Unit, onCommand: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = Color(0xFF112C40), contentColor = Color(0xFFEAF4FC)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF112C40), Color(0xFF144A50))))
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_motorcycle), null, Modifier.size(32.dp), tint = Color(0xFF9CE8D9))
                Text(stringResource(when {
                    state.switchingMode -> R.string.overlay_switching
                    state.restoringAudio -> R.string.session_restoring
                    state.callPhase == TripCallPhase.CALL -> R.string.session_call
                    state.callPhase == TripCallPhase.RECOVERING -> R.string.session_call_recovering
                    state.tripActive -> R.string.trip_active
                    else -> R.string.journey_ready_title
                }), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            }
            Text(stringResource(if (state.tripActive) R.string.journey_active_hint else R.string.journey_ready_hint),
                style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD2E8EE))
            Text(stringResource(when (state.inputId) {
                "BUTTON" -> R.string.mode_button_title
                "PHONE_WAKE" -> R.string.mode_phone_title
                else -> R.string.mode_voice_title
            }), style = MaterialTheme.typography.labelLarge, color = Color(0xFFA8EEE1))
            if (state.tripStatus.isNotBlank()) Text(state.tripStatus, style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD2E8EE))
            if (state.tripActive && state.buttonMode && tripScreen) {
                Button(onClick = onCommand, enabled = state.canRequestTripCommand, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAEEDDE), contentColor = Color(0xFF092D32))) {
                    Text(stringResource(R.string.trip_manual_command), Modifier.padding(vertical = 6.dp))
                }
            }
            Button(onClick = onPrimary, enabled = state.tripActive || state.canStartTrip,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAEEDDE), contentColor = Color(0xFF092D32))) {
                Text(stringResource(if (!state.tripActive) R.string.trip_start else if (tripScreen)
                    R.string.trip_stop else R.string.home_open_trip), Modifier.padding(vertical = 6.dp))
            }
            if (state.manualActivationOnly) Text(stringResource(R.string.manual_activation_summary),
                style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD2E8EE))
            if (state.audioDevices.selectionMissing) Text(stringResource(R.string.devices_selection_lost),
                color = Color(0xFFFFD5C8), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun QuickLink(title: String, hint: String, iconRes: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(painterResource(iconRes), null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                SupportingText(hint)
            }
        }
    }
}
