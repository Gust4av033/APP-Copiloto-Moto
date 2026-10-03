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
        ChoiceGroup(listOf(ChoiceUi("NONE", R.string.trip_button_none),
            ChoiceUi("PREVIOUS", R.string.trip_button_previous), ChoiceUi("NEXT", R.string.trip_button_next),
            ChoiceUi("PAUSE", R.string.trip_button_pause), ChoiceUi("PLAY_PAUSE", R.string.trip_button_toggle)),
            state.tripButtonBinding, !state.busy && !state.buttonProbeActive, actions.onTripButtonBindingChange)
        if (state.tripButtonBinding != "NONE") {
            SupportingText(stringResource(R.string.trip_button_manual_hint))
            SupportingText(stringResource(R.string.trip_button_access_hint))
            if (!state.mediaAccessGranted) {
                ActionButton(stringResource(R.string.trip_button_access), onClick = actions.onMediaAccessSettings)
            }
            SupportingText(state.pipelineMetrics.lastButtonStatus)
        }
    }
}
