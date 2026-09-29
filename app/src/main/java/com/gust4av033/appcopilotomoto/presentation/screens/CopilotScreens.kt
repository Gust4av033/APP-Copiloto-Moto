package com.gust4av033.appcopilotomoto.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.components.ActionButton
import com.gust4av033.appcopilotomoto.presentation.components.ChoiceGroup
import com.gust4av033.appcopilotomoto.presentation.components.SectionCard
import com.gust4av033.appcopilotomoto.presentation.components.SessionStatus
import com.gust4av033.appcopilotomoto.presentation.components.SupportingText

@Composable
fun TripScreen(state: CopilotUiState, actions: CopilotActions, onOpenTests: () -> Unit) {
    SectionCard(stringResource(R.string.trip_session)) {
        SessionStatus(state.tripActive)
        Text(stringResource(R.string.trip_activation, state.phraseLabel))
        SupportingText(state.tripStatus)
        ActionButton(
            stringResource(if (state.tripActive) R.string.trip_stop else R.string.trip_start),
            enabled = state.tripActive || !state.busy,
            primary = true,
            onClick = actions.onToggleTrip
        )
        SupportingText(stringResource(R.string.trip_y12_hint, state.phraseLabel))
    }
    SectionCard(stringResource(R.string.trip_last_result)) {
        SupportingText(state.lastCommandResult.ifBlank { stringResource(R.string.no_command_result) })
    }
    SectionCard(stringResource(R.string.trip_audio_check)) {
        SupportingText(stringResource(R.string.trip_audio_check_hint))
        var comparisonVisible by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { comparisonVisible = !comparisonVisible }) {
            Text(stringResource(if (comparisonVisible) R.string.audio_comparison_hide else R.string.audio_comparison_show))
        }
        if (comparisonVisible) AudioComparison(state)
        ActionButton(stringResource(R.string.open_tests), onClick = onOpenTests)
    }
    SectionCard(stringResource(R.string.trip_commands)) {
        Text(stringResource(R.string.commands_music))
        Text(stringResource(R.string.commands_volume))
        Text(stringResource(R.string.commands_call))
        SupportingText(stringResource(R.string.commands_stop))
    }
    SectionCard(stringResource(R.string.trip_results)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric(state.metrics.detections, stringResource(R.string.metric_wake), Modifier.weight(1f))
            Metric(state.metrics.manualCommands, stringResource(R.string.metric_button), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric(state.metrics.actions, stringResource(R.string.metric_actions), Modifier.weight(1f))
            Metric(state.metrics.failures, stringResource(R.string.metric_failures), Modifier.weight(1f))
        }
        var detailsVisible by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { detailsVisible = !detailsVisible }) {
            Text(stringResource(if (detailsVisible) R.string.details_hide else R.string.details_show))
        }
        if (detailsVisible) {
            SupportingText(stringResource(R.string.metric_transcripts, state.metrics.recognized))
            SupportingText(stringResource(R.string.metric_ready_latency, state.metrics.readyLatencyMs))
            SupportingText(stringResource(R.string.metric_total_latency, state.metrics.totalLatencyMs))
            SupportingText(stringResource(R.string.metrics_hint))
        }
    }
}

@Composable
private fun Metric(value: Int, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        SupportingText(label)
    }
}

@Composable
fun TestsScreen(state: CopilotUiState, actions: CopilotActions) {
    SupportingText(stringResource(R.string.tests_intro))
    if (state.tripActive) SupportingText(stringResource(R.string.tests_trip_active))
    SectionCard(stringResource(R.string.tests_audio)) {
        AudioComparison(state)
        SupportingText(state.audioDiagnostic)
        ActionButton(stringResource(R.string.audio_refresh), onClick = actions.onRefreshAudio)
    }
    SectionCard(stringResource(R.string.tests_short_command)) {
        SupportingText(stringResource(R.string.tests_short_hint))
        ActionButton(
            stringResource(if (state.listening) R.string.listen_cancel else R.string.listen_start),
            enabled = state.canListen,
            primary = true,
            onClick = actions.onToggleListening
        )
        SupportingText(state.voiceMessage)
    }
    SectionCard(stringResource(R.string.tests_microphone)) {
        ActionButton(
            stringResource(if (state.measuring) R.string.probe_running else R.string.probe_start),
            enabled = !state.busy,
            onClick = actions.onMeasure
        )
        SupportingText(state.probeMessage)
        state.probe?.let { probe ->
            SupportingText(stringResource(R.string.probe_requested, probe.requestedInput))
            SupportingText(stringResource(R.string.probe_actual, probe.actualInput))
            SupportingText(stringResource(R.string.probe_output, probe.selectedOutput))
            SupportingText(stringResource(R.string.probe_samples, probe.durationMs, probe.sampleCount))
            SupportingText(stringResource(R.string.probe_levels, probe.rmsDbfs, probe.peakDbfs))
            probe.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    SectionCard(stringResource(R.string.tests_wake)) {
        SupportingText(stringResource(R.string.tests_wake_hint, state.phraseLabel))
        ActionButton(
            stringResource(if (state.wakeEnabled) R.string.wake_stop else R.string.wake_start),
            enabled = state.canToggleWake,
            onClick = actions.onToggleWake
        )
        SupportingText(state.wakeMessage)
        SupportingText(stringResource(R.string.wake_detections, state.wakeDetections))
    }
    SectionCard(stringResource(R.string.tests_y12)) {
        SupportingText(stringResource(R.string.tests_y12_hint))
        ActionButton(
            stringResource(if (state.assistantSelected) R.string.assistant_selected else R.string.assistant_select),
            enabled = !state.busy,
            onClick = actions.onSelectAssistant
        )
        SupportingText(state.assistantMessage)
    }
}

@Composable
fun SettingsScreen(state: CopilotUiState, actions: CopilotActions, onOpenTests: () -> Unit) {
    if (state.busy) SupportingText(stringResource(R.string.settings_busy))
    SectionCard(stringResource(R.string.settings_phrase)) {
        ChoiceGroup(state.phrases, state.phraseId, !state.busy, actions.onPhraseChange)
        SupportingText(stringResource(R.string.settings_phrase_hint))
        Text(stringResource(R.string.settings_sensitivity, state.sensitivity.toInt()))
        Slider(
            value = state.sensitivity,
            onValueChange = actions.onSensitivityChange,
            valueRange = 40f..85f,
            steps = 8,
            enabled = !state.busy
        )
        SupportingText(stringResource(R.string.settings_sensitivity_hint))
    }
    SectionCard(stringResource(R.string.settings_input)) {
        ChoiceGroup(state.inputs, state.inputId, !state.busy, actions.onInputChange)
        SupportingText(stringResource(R.string.settings_input_hint))
    }
    SectionCard(stringResource(R.string.settings_calls)) {
        SupportingText(stringResource(R.string.settings_calls_hint))
        ActionButton(stringResource(R.string.calls_enable), enabled = !state.busy, onClick = actions.onEnableNamedCalls)
        SupportingText(stringResource(R.string.calls_fixed_hint))
        ActionButton(
            stringResource(if (state.fixedContact == null) R.string.contact_choose else R.string.contact_change),
            enabled = !state.busy,
            onClick = actions.onChooseContact
        )
        state.fixedContact?.let { contact ->
            Text(stringResource(R.string.contact_fixed, contact))
            ActionButton(stringResource(R.string.contact_clear), enabled = !state.busy, onClick = actions.onClearContact)
        }
        SupportingText(state.callMessage)
    }
    SectionCard(stringResource(R.string.settings_test_tools)) {
        SupportingText(stringResource(R.string.settings_test_tools_hint))
        ActionButton(stringResource(R.string.open_tests), onClick = onOpenTests)
    }
}

@Composable
private fun AudioComparison(state: CopilotUiState) {
    SupportingText(stringResource(R.string.audio_before, state.audioBefore))
    SupportingText(stringResource(R.string.audio_during, state.audioDuring))
    SupportingText(stringResource(R.string.audio_after, state.audioAfter))
}
