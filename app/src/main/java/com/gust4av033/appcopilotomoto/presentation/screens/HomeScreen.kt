package com.gust4av033.appcopilotomoto.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotScreen
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.components.ActionButton
import com.gust4av033.appcopilotomoto.presentation.components.ActivationSummary
import com.gust4av033.appcopilotomoto.presentation.components.PendingAppCard
import com.gust4av033.appcopilotomoto.presentation.components.SectionCard
import com.gust4av033.appcopilotomoto.presentation.components.SessionStatus
import com.gust4av033.appcopilotomoto.presentation.components.SupportingText
import com.gust4av033.appcopilotomoto.presentation.upcomingModules

@Composable
fun HomeScreen(state: CopilotUiState, actions: CopilotActions, onNavigate: (CopilotScreen) -> Unit) {
    SectionCard(stringResource(R.string.home_copilot)) {
        SessionStatus(state.tripActive, state.restoringAudio, state.callPhase)
        ActivationSummary(state)
        SupportingText(state.tripStatus)
        ActionButton(
            stringResource(if (state.tripActive) R.string.home_open_trip else R.string.trip_start),
            enabled = state.tripActive || state.canStartTrip,
            primary = true,
            onClick = {
                if (!state.tripActive) actions.onToggleTrip()
                onNavigate(CopilotScreen.TRIP)
            }
        )
        if (state.restoringAudio) SupportingText(stringResource(R.string.devices_restoring))
        else if (state.busy && !state.tripActive) SupportingText(stringResource(R.string.home_test_active))
    }
    PendingAppCard(state, actions)
    SectionCard(stringResource(R.string.home_setup)) {
        if (!state.buttonMode) Text(stringResource(R.string.trip_activation, state.phraseLabel))
        val inputTitle = state.inputs.firstOrNull { it.id == state.inputId }?.titleRes
        if (inputTitle != null) SupportingText(stringResource(inputTitle))
        if (state.audioDevices.supported && state.audioDevices.permissionGranted) {
            SupportingText(stringResource(R.string.home_headset,
                state.audioDevices.selectedLabel.ifBlank {
                    state.audioDevices.automaticHeadset.ifBlank { stringResource(R.string.devices_auto_unresolved) }
                }))
            if (state.audioDevices.selectionMissing) {
                Text(stringResource(R.string.devices_selection_lost), color = MaterialTheme.colorScheme.error)
            }
        }
        SupportingText(stringResource(if (state.assistantSelected) R.string.home_assistant_ready
            else if (state.buttonMode) R.string.home_assistant_required else R.string.home_assistant_optional))
        if (state.buttonMode && !state.assistantSelected) {
            ActionButton(stringResource(R.string.assistant_select), enabled = !state.busy, onClick = actions.onSelectAssistant)
        }
        ActionButton(stringResource(R.string.home_configure), onClick = { onNavigate(CopilotScreen.SETTINGS) })
        ActionButton(
            stringResource(if (state.busy && !state.tripActive) R.string.home_open_test else R.string.home_check_headset),
            onClick = { onNavigate(CopilotScreen.TESTS) }
        )
    }
    SectionCard(stringResource(R.string.home_command_core)) {
        SupportingText(stringResource(R.string.home_command_core_hint))
        ActionButton(stringResource(R.string.home_view_commands), onClick = { onNavigate(CopilotScreen.COMMANDS) })
    }
    SectionCard(stringResource(R.string.home_next_modules)) {
        SupportingText(stringResource(R.string.home_next_hint))
        upcomingModules.forEach { module ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(module.iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(module.titleRes), style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.module_upcoming), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        SupportingText(stringResource(module.descriptionRes))
                    }
                }
            }
        }
    }
}
