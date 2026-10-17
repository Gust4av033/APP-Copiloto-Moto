package com.gust4av033.appcopilotomoto.presentation.screens

import androidx.compose.foundation.lazy.LazyListScope
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
import com.gust4av033.appcopilotomoto.presentation.SettingsSection
import com.gust4av033.appcopilotomoto.presentation.components.SettingsMenuRow
import com.gust4av033.appcopilotomoto.presentation.components.NavigationCard
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.VoiceRouteUi
import com.gust4av033.appcopilotomoto.presentation.components.JourneyHero
import com.gust4av033.appcopilotomoto.presentation.components.QuickLink
import com.gust4av033.appcopilotomoto.presentation.components.ActionButton
import com.gust4av033.appcopilotomoto.presentation.components.ActivationSummary
import com.gust4av033.appcopilotomoto.presentation.components.AudioLevels
import com.gust4av033.appcopilotomoto.presentation.components.DiagnosticsReportCard
import com.gust4av033.appcopilotomoto.presentation.components.CommandInterpretation
import com.gust4av033.appcopilotomoto.presentation.components.PendingAppCard
import com.gust4av033.appcopilotomoto.presentation.components.MusicPlayerCard
import com.gust4av033.appcopilotomoto.presentation.components.HeadsetButtonProbeCard
import com.gust4av033.appcopilotomoto.presentation.components.AudioDevicesCard
import com.gust4av033.appcopilotomoto.presentation.components.ChoiceGroup
import com.gust4av033.appcopilotomoto.presentation.components.IntegrationsCard
import com.gust4av033.appcopilotomoto.presentation.components.ExpandableSectionCard
import com.gust4av033.appcopilotomoto.presentation.components.SectionCard
import com.gust4av033.appcopilotomoto.presentation.components.SessionStatus
import com.gust4av033.appcopilotomoto.presentation.components.SupportingText
import com.gust4av033.appcopilotomoto.presentation.components.ToggleSettingRow

fun LazyListScope.TripScreen(state: CopilotUiState, actions: CopilotActions, onOpenCommands: () -> Unit, onOpenTests: () -> Unit) {
    item(key = "TripScreen_0") {
        JourneyHero(state, tripScreen = true, onPrimary = actions.onToggleTrip,
            onCommand = actions.onManualTripCommand)
    }
    if (state.tripActive && state.buttonMode && !state.canRequestTripCommand) item(key = "waiting") {
        SupportingText(stringResource(R.string.trip_wait_ready))
    }
    if (state.tripActive && state.greetingWarning.isNotBlank()) item(key = "greeting_warning") {
        SectionCard(stringResource(R.string.trip_greeting_title)) { SupportingText(state.greetingWarning) }
    }
    if (state.tripActive) item(key = "trip_music_player") {
        SectionCard(stringResource(R.string.trip_music_player_title)) {
            val selected = state.currentTripMusicPlayer
            if (selected == null) SupportingText(state.currentTripMusicMessage.ifBlank {
                stringResource(R.string.trip_music_player_unknown) })
            else SupportingText(stringResource(R.string.trip_music_player_value, selected.player.label,
                stringResource(when (selected.origin) {
                    com.gust4av033.appcopilotomoto.MusicPlayerOrigin.EXPLICIT -> R.string.trip_music_origin_explicit
                    com.gust4av033.appcopilotomoto.MusicPlayerOrigin.TRIP -> R.string.trip_music_origin_trip
                    com.gust4av033.appcopilotomoto.MusicPlayerOrigin.PLAYING_SESSION -> R.string.trip_music_origin_playing
                    com.gust4av033.appcopilotomoto.MusicPlayerOrigin.SETTINGS -> R.string.trip_music_origin_settings
                    com.gust4av033.appcopilotomoto.MusicPlayerOrigin.AUTOMATIC -> R.string.trip_music_origin_auto
                })))
        }
    }
    if (state.pendingAppName != null) item(key = "TripScreen_2") {
        PendingAppCard(state, actions)
    }
    if (state.standaloneListening) item("standalone_command") {
        SectionCard(stringResource(R.string.settings_short_command)) {
            SupportingText(stringResource(R.string.settings_short_command_hint))
            ActionButton(stringResource(R.string.listen_cancel), destructive = true,
                iconRes = R.drawable.ic_stop, onClick = actions.onCancelStandaloneCommand)
        }
    }
    if (!state.tripActive && state.lastTestCommand != null) item("standalone_result") {
        SectionCard(stringResource(R.string.trip_last_result)) { CommandInterpretation(state.lastTestCommand) }
    }
    if (state.buttonMode && state.tripButtonBinding != "NONE" && !state.mediaAccessGranted) item("trip_button_permission") { MediaAccessCard(state, actions) }
    item(key = "TripScreen_3") {
        SectionCard(stringResource(R.string.trip_last_result)) {
            state.lastTripCommand?.let {
                SupportingText(stringResource(R.string.command_interpreted))
                CommandInterpretation(it)
            }
            SupportingText(state.lastCommandResult.ifBlank { stringResource(R.string.no_command_result) })
        }
    }
    item(key = "TripScreen_5") {
        QuickLink(stringResource(R.string.home_view_commands), stringResource(R.string.home_commands_short),
            R.drawable.ic_commands, onOpenCommands)
    }

    if (state.tripActive) item(key = "trip_navigation_context") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.gust4av033.appcopilotomoto.presentation.components.TripDestinationCard(state, actions)
            com.gust4av033.appcopilotomoto.presentation.components.TripNavigationCard(
                state.navigationContext, state.observedRoute, state.observedRouteAtElapsedMs,
                state.navigationNoticesReady)
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


private fun LazyListScope.DeveloperTripDetails(state: CopilotUiState, onOpenTests: () -> Unit) {
    if (!state.developerEnabled) return
    item(key = "TripScreen_4") {
        ExpandableSectionCard(stringResource(R.string.trip_audio_check)) {
            SupportingText(stringResource(R.string.trip_audio_check_hint))
            AudioLevels(state)
            if (state.audioDevices.musicVolume.isNotBlank()) {
                SupportingText(stringResource(R.string.devices_communication_route,
                    state.audioDevices.communicationRoute.ifBlank { stringResource(R.string.devices_no_communication) }))
            }
            if (state.tripActive && state.audioDevices.voiceRoute == VoiceRouteUi.SCO) {
                SupportingText(stringResource(R.string.trip_sco_observed))
            } else if (state.tripActive && state.audioDevices.voiceRoute == VoiceRouteUi.LE) {
                SupportingText(stringResource(R.string.trip_le_observed))
            }
            if (state.inputId == "PHONE_WAKE") {
                SupportingText(stringResource(R.string.trip_phone_wake_trial))
            }
            var comparisonVisible by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { comparisonVisible = !comparisonVisible }) {
                Text(stringResource(if (comparisonVisible) R.string.audio_comparison_hide else R.string.audio_comparison_show))
            }
            if (comparisonVisible) AudioComparison(state)
            ActionButton(stringResource(R.string.open_tests), iconRes = R.drawable.ic_tests, onClick = onOpenTests)
        }
    }
    item(key = "TripScreen_6") {
        ExpandableSectionCard(stringResource(R.string.trip_results)) {
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
}

fun LazyListScope.TestsScreen(state: CopilotUiState, actions: CopilotActions) {
    if (!state.developerEnabled) { BasicDiagnosticsScreen(state, actions); return }
    DeveloperTripDetails(state, actions.onOpenBasicDiagnostics)
    item("trip_context_layers") { SectionCard(stringResource(R.string.trip_context_layers)) {
        SupportingText(state.tripContextReport.ifBlank { stringResource(R.string.trip_context_waiting) })
        ActionButton(stringResource(R.string.trip_export_gpx), enabled = !state.busy,
            onClick = { actions.onExportLastTrip(true) })
        ActionButton(stringResource(R.string.trip_export_json), enabled = !state.busy,
            onClick = { actions.onExportLastTrip(false) })
    } }
    if (!state.developerToolsAvailable) {
        item("release_diagnostics") { SupportingText(stringResource(R.string.settings_developer_read_only)) }
        item("release_devices") { AudioDevicesCard(state, actions) }
        item("release_report") { DiagnosticsReportCard(state, actions) }
        return
    }
    item(key = "TestsScreen_0") {
        SupportingText(stringResource(R.string.tests_intro))
    }
    if (state.tripActive) item(key = "TestsScreen_1") {
        SupportingText(stringResource(R.string.tests_trip_active))
    }
    item(key = "TestsScreen_2") {
        HeadsetButtonProbeCard(state, actions)
    }
    item(key = "TestsScreen_3") {
        AudioDevicesCard(state, actions)
    }
    item(key = "TestsScreen_4") {
        DiagnosticsReportCard(state, actions)
    }
    item(key = "TestsScreen_5") {
        SectionCard(stringResource(R.string.tests_volume_title)) {
            SupportingText(if (state.buttonMode) stringResource(R.string.tests_volume_button_protocol)
                else stringResource(R.string.tests_volume_voice_protocol, state.phraseLabel))
            SupportingText(stringResource(R.string.tests_volume_result_hint))
        }
    }
    item(key = "TestsScreen_6") {
        SectionCard(stringResource(R.string.tests_audio)) {
            AudioComparison(state)
            SupportingText(stringResource(R.string.tests_audio_profile_hint))
            SupportingText(state.audioDiagnostic)
            ActionButton(stringResource(R.string.audio_refresh), onClick = actions.onRefreshAudio)
        }
    }
    item(key = "TestsScreen_7") {
        SectionCard(stringResource(R.string.tests_short_command)) {
            SupportingText(stringResource(R.string.tests_short_hint))
            SupportingText(stringResource(R.string.devices_short_command_scope))
            ActionButton(
                stringResource(if (state.listening) R.string.listen_cancel else R.string.listen_start),
                enabled = state.canListen,
                primary = true,
                iconRes = R.drawable.ic_mic,
                onClick = actions.onToggleListening
            )
            state.lastTestCommand?.let {
                SupportingText(stringResource(R.string.command_interpreted))
                CommandInterpretation(it)
            }
            SupportingText(state.voiceMessage)
        }
    }
    item(key = "TestsScreen_8") {
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
    }
    item(key = "TestsScreen_9") {
        if (state.buttonMode) SectionCard(stringResource(R.string.tests_wake)) {
            SupportingText(stringResource(R.string.tests_wake_button_mode))
        } else SectionCard(stringResource(R.string.tests_wake)) {
            SupportingText(stringResource(R.string.tests_wake_hint, state.phraseLabel))
            ActionButton(
                stringResource(if (state.wakeEnabled) R.string.wake_stop else R.string.wake_start),
                enabled = state.canToggleWake,
                onClick = actions.onToggleWake
            )
            SupportingText(state.wakeMessage)
            SupportingText(stringResource(R.string.wake_detections, state.wakeDetections))
        }
    }
    item(key = "TestsScreen_10") {
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
}

@Composable
private fun AudioComparison(state: CopilotUiState) {
    SupportingText(stringResource(R.string.audio_snapshot_hint))
    SupportingText(stringResource(R.string.audio_before, state.audioBefore))
    SupportingText(stringResource(R.string.audio_during, state.audioDuring))
    SupportingText(stringResource(R.string.audio_after, state.audioAfter))
}




