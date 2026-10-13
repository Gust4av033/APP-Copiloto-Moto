package com.gust4av033.appcopilotomoto.presentation.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotScreen
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.components.*
import com.gust4av033.appcopilotomoto.presentation.upcomingModules

fun LazyListScope.HomeScreen(state: CopilotUiState, actions: CopilotActions, onNavigate: (CopilotScreen) -> Unit) {
    item(key = "journey") {
        JourneyHero(state, tripScreen = false, onPrimary = {
            if (!state.tripActive) actions.onToggleTrip()
            onNavigate(CopilotScreen.TRIP)
        }, onCommand = actions.onManualTripCommand)
    }
    if (state.pendingAppName != null) item(key = "pending") { PendingAppCard(state, actions) }
    item(key = "setup") {
        SectionCard(stringResource(R.string.home_setup)) {
            Text(stringResource(R.string.home_headset, state.audioDevices.selectedLabel.ifBlank {
                state.audioDevices.automaticHeadset.ifBlank { stringResource(R.string.devices_auto_unresolved) }
            }), style = MaterialTheme.typography.titleSmall)
            SupportingText(if (state.buttonMode) stringResource(R.string.home_button_short)
                else stringResource(R.string.mode_voice_instruction, state.phraseLabel))
            if (state.buttonMode && state.tripButtonBinding == "NONE" && !state.assistantSelected) {
                SupportingText(stringResource(R.string.home_assistant_required))
                ActionButton(stringResource(R.string.assistant_select), enabled = !state.busy,
                    iconRes = R.drawable.ic_button, onClick = actions.onSelectAssistant)
            }
            if (state.busy && !state.tripActive) SupportingText(stringResource(R.string.home_test_active))
            ActionButton(stringResource(R.string.home_configure), iconRes = R.drawable.ic_settings,
                onClick = { onNavigate(CopilotScreen.SETTINGS) })
        }
    }
    item(key = "commands") {
        QuickLink(stringResource(R.string.home_view_commands), stringResource(R.string.home_commands_short),
            R.drawable.ic_commands) { onNavigate(CopilotScreen.COMMANDS) }
    }
    if (state.buttonMode && state.tripButtonBinding != "NONE" && !state.mediaAccessGranted) item("media_access_help") { MediaAccessCard(state, actions) }
    item(key = "tests") {
        QuickLink(stringResource(R.string.home_check_headset), stringResource(R.string.home_tests_short),
            R.drawable.ic_help) { onNavigate(CopilotScreen.DIAGNOSTICS) }
    }
    item(key = "upcoming") {
        ExpandableSectionCard(stringResource(R.string.home_next_modules)) {
            SupportingText(stringResource(R.string.home_next_hint))
            upcomingModules.forEach { module ->
                Text(stringResource(module.titleRes) + " · " + stringResource(R.string.module_upcoming),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

