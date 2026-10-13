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
import com.gust4av033.appcopilotomoto.presentation.theme.GlassPanel

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
    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, strong = true) {
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
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    border = BorderStroke(1.dp, Glass.outline())) {
                    Icon(painterResource(R.drawable.ic_motorcycle), null,
                        Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.home_copilot), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(status), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            Text(stringResource(if (state.tripActive) R.string.journey_active_hint else R.string.journey_ready_hint),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(if (state.buttonMode) R.drawable.ic_button else R.drawable.ic_mic), null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(stringResource(mode), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            val progress = when {
                state.sessionProblem -> R.string.settings_session_problem
                !state.tripActive -> R.string.journey_ready_hint
                state.callPhase != TripCallPhase.NONE -> R.string.session_call
                state.restoringAudio -> R.string.session_restoring
                state.switchingMode -> R.string.overlay_switching
                state.interactionPhase == com.gust4av033.appcopilotomoto.TripInteractionPhase.LISTENING -> R.string.settings_voice_listening
                state.interactionPhase == com.gust4av033.appcopilotomoto.TripInteractionPhase.PROCESSING -> R.string.settings_voice_processing
                state.interactionPhase == com.gust4av033.appcopilotomoto.TripInteractionPhase.CONFIRMING -> R.string.settings_voice_confirmation
                state.interactionPhase == com.gust4av033.appcopilotomoto.TripInteractionPhase.SPEAKING -> R.string.settings_voice_speaking
                state.commandReady || state.interactionPhase == com.gust4av033.appcopilotomoto.TripInteractionPhase.WAITING -> if (state.buttonMode) R.string.settings_button_ready else R.string.settings_voice_ready
                else -> R.string.settings_voice_preparing
            }
            Text(if (state.developerEnabled) state.tripStatus else stringResource(progress), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (state.tripActive && state.buttonMode && tripScreen) {
                ActionButton(stringResource(R.string.trip_manual_command),
                    enabled = state.canRequestTripCommand, iconRes = R.drawable.ic_mic, onClick = onCommand)
            }
            ActionButton(stringResource(if (!state.tripActive) R.string.trip_start else if (tripScreen)
                    R.string.trip_stop else R.string.home_open_trip),
                enabled = state.tripActive || state.canStartTrip, primary = true,
                iconRes = when { !state.tripActive -> R.drawable.ic_play
                    tripScreen -> R.drawable.ic_stop
                    else -> R.drawable.ic_trip }, onClick = onPrimary)
            if (state.manualActivationOnly) SupportingText(stringResource(R.string.manual_activation_summary))
            if (state.audioDevices.selectionMissing) Text(stringResource(R.string.devices_selection_lost),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun QuickLink(title: String, hint: String, iconRes: Int, onClick: () -> Unit) {
    GlassPanel(onClick = onClick, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 88.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .74f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
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

