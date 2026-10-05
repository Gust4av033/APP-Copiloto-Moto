package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.ChoiceUi
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

@Composable
fun TripButtonSettingsCard(state: CopilotUiState, actions: CopilotActions) {
    SectionCard(stringResource(R.string.trip_button_title)) {
        SupportingText(stringResource(R.string.trip_button_hint_mapping))
        HeadsetControlsSummary(state)
        ActionButton(stringResource(R.string.headset_controls_check), enabled = !state.busy,
            onClick = actions.onOpenButtonProbe)
        ChoiceGroup(listOf(ChoiceUi("NONE", R.string.trip_button_none),
            ChoiceUi("PREVIOUS", R.string.trip_button_previous), ChoiceUi("NEXT", R.string.trip_button_next),
            ChoiceUi("PAUSE", R.string.trip_button_pause), ChoiceUi("PLAY_PAUSE", R.string.trip_button_toggle)),
            state.tripButtonBinding, !state.busy && !state.buttonProbeActive, actions.onTripButtonBindingChange)
        if (state.tripButtonBinding != "NONE") {
            SupportingText(stringResource(R.string.trip_button_manual_hint))
            SupportingText(stringResource(R.string.trip_button_access_hint))
            SupportingText(stringResource(if (state.mediaAccessGranted) R.string.trip_button_access_ready
                else R.string.trip_button_access_missing, stringResource(R.string.app_name)))
            ActionButton(stringResource(if (state.mediaAccessGranted) R.string.trip_button_access_review
                else R.string.trip_button_access), onClick = actions.onMediaAccessSettings)
            SupportingText(if (state.tripActive) state.pipelineMetrics.lastButtonStatus
                else stringResource(R.string.trip_button_configured_before_trip))
        }
    }
}
