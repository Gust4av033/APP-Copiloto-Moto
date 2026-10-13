package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

@Composable
fun WazeObservationCard(state: CopilotUiState, actions: CopilotActions) {
    if (!state.developerEnabled) return
    SectionCard(stringResource(R.string.waze_observation_title)) {
        SupportingText(stringResource(R.string.waze_observation_scope))
        ToggleSettingRow(stringResource(R.string.waze_observation_toggle), state.wazeObservationEnabled,
            enabled = state.developerToolsAvailable && !state.busy, onCheckedChange = actions.onWazeObservationChange)
        if (!state.developerToolsAvailable) SupportingText(stringResource(R.string.settings_developer_read_only))
        SupportingText(stringResource(if (state.wazeObservationGranted) R.string.waze_observation_granted else R.string.waze_observation_missing))
        ActionButton(stringResource(R.string.waze_observation_permission),
            enabled = state.developerToolsAvailable && state.wazeObservationEnabled && !state.busy,
            onClick = actions.onWazeObservationPermission)
        val sample = state.wazeObservation.sample
        SupportingText(stringResource(when {
            !state.wazeObservationEnabled -> R.string.waze_observation_disabled
            !state.tripActive -> R.string.waze_observation_start_trip
            !state.wazeObservation.connected -> R.string.waze_observation_disconnected
            state.wazeObservation.readFailed -> R.string.waze_observation_failed
            sample == null -> R.string.waze_observation_empty
            else -> R.string.waze_observation_received
        }))
        if (sample != null) {
            sample.fields.forEach { (_, text) -> SupportingText(text) }
            SupportingText(stringResource(R.string.waze_observation_sample_scope))
        }
        ActionButton(stringResource(R.string.waze_observation_copy), enabled = sample != null,
            onClick = { actions.onCopyDiagnostics(state.wazeObservation.report(System.currentTimeMillis())) })
    }
}
