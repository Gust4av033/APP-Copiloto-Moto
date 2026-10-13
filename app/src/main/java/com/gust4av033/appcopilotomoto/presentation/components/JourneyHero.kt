package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.TripCallPhase
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.theme.Glass

/** Resume el estado compartido del servicio sin ejecutar efectos al dibujarse. */
@Composable
fun JourneyHero(state: CopilotUiState, tripScreen: Boolean, onPrimary: () -> Unit, onCommand: () -> Unit) {
    val status = when {
        state.switchingMode -> R.string.overlay_switching
        state.restoringAudio -> R.string.session_restoring
        state.callPhase == TripCallPhase.CALL -> R.string.session_call
        state.callPhase == TripCallPhase.RECOVERING -> R.string.session_call_recovering
        state.tripActive -> R.string.trip_active
        else -> R.string.journey_ready_title
    }
    val mode = when (state.inputId) {
        "BUTTON" -> R.string.mode_button_title
        "PHONE_WAKE" -> R.string.mode_phone_title
        else -> R.string.mode_voice_title
    }
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = Glass.surface(strong = true),
        border = BorderStroke(1.dp, Glass.outline()), shadowElevation = 8.dp
    ) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = .13f),
                MaterialTheme.colorScheme.secondary.copy(alpha = .07f),
                MaterialTheme.colorScheme.tertiary.copy(alpha = .05f)
            ))).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .85f),
                    border = BorderStroke(1.dp, Glass.outline())) {
                    Icon(painterResource(R.drawable.ic_motorcycle), null,
                        Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.home_copilot), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(status), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            Text(stringResource(if (state.tripActive) R.string.journey_active_hint else R.string.journey_ready_hint),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .8f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_mic), null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(stringResource(mode), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            if (state.tripStatus.isNotBlank()) Text(state.tripStatus, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (state.tripActive && state.buttonMode && tripScreen) {
                ActionButton(stringResource(R.string.trip_manual_command),
                    enabled = state.canRequestTripCommand, onClick = onCommand)
            }
            Button(onClick = onPrimary, enabled = state.tripActive || state.canStartTrip,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Text(stringResource(if (!state.tripActive) R.string.trip_start else if (tripScreen)
                    R.string.trip_stop else R.string.home_open_trip), Modifier.padding(vertical = 6.dp))
            }
            if (state.manualActivationOnly) SupportingText(stringResource(R.string.manual_activation_summary))
            if (state.audioDevices.selectionMissing) Text(stringResource(R.string.devices_selection_lost),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun QuickLink(title: String, hint: String, iconRes: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 88.dp),
        shape = MaterialTheme.shapes.medium, color = Glass.surface(),
        border = BorderStroke(1.dp, Glass.outline())) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .8f)) {
                Icon(painterResource(iconRes), null, Modifier.padding(12.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                SupportingText(hint)
            }
        }
    }
}
