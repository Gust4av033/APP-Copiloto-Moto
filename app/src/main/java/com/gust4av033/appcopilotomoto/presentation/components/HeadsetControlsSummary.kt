package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.TripButtonBinding
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

@Composable
internal fun HeadsetControlsSummary(state: CopilotUiState) {
    val devices = state.audioDevices
    if (devices.controlHeadsetName.isBlank()) {
        SupportingText(stringResource(R.string.headset_controls_choose))
        return
    }
    SupportingText(stringResource(R.string.headset_controls_name, devices.controlHeadsetName))
    val labels = devices.observedControlKeys.sorted().mapNotNull { key ->
        when (key) {
            79 -> R.string.headset_control_hook
            85 -> R.string.headset_control_toggle
            86 -> R.string.headset_control_stop
            87 -> R.string.headset_control_next
            88 -> R.string.headset_control_previous
            89 -> R.string.headset_control_rewind
            90 -> R.string.headset_control_forward
            126 -> R.string.headset_control_play
            127 -> R.string.headset_control_pause
            else -> null
        }?.let { stringResource(it) }
    } + if (devices.assistantInvocationObserved) listOf(stringResource(R.string.headset_control_assistant)) else emptyList()
    SupportingText(if (labels.isEmpty()) stringResource(R.string.headset_controls_unknown)
        else stringResource(R.string.headset_controls_observed, labels.joinToString(", ")))
    if (!devices.controlProfilePersistent) SupportingText(stringResource(R.string.headset_controls_temporary))
    TripButtonBinding.parse(state.tripButtonBinding).keyCode?.let { chosen ->
        SupportingText(stringResource(if (chosen in devices.observedControlKeys)
            R.string.headset_controls_selected_observed else R.string.headset_controls_selected_unknown))
    }
    SupportingText(stringResource(R.string.headset_controls_scope))
}
