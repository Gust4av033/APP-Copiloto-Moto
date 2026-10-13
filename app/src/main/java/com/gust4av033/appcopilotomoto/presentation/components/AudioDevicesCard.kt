package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

@Composable
fun AudioDevicesCard(state: CopilotUiState, actions: CopilotActions) {
    if (!state.developerEnabled) return
    val devices = state.audioDevices
    if (!state.developerToolsAvailable) {
        SectionCard(stringResource(R.string.devices_title)) {
            SupportingText(devices.headsets.joinToString("\n") { it.technicalLabel })
            SupportingText(devices.communicationRoute)
            AudioLevels(state)
            SupportingText(devices.bluetoothInputs.joinToString("\n"))
            SupportingText(devices.bluetoothOutputs.joinToString("\n"))
            HeadsetControlsSummary(state)
        }
        return
    }
    SectionCard(stringResource(R.string.devices_title)) {
        SupportingText(stringResource(R.string.devices_intro))
        if (state.restoringAudio) SupportingText(stringResource(R.string.devices_restoring))
        if (!devices.supported) {
            SupportingText(stringResource(R.string.devices_unsupported))
        } else if (!devices.permissionGranted) {
            SupportingText(stringResource(R.string.devices_permission_hint))
        } else {
            Text(stringResource(R.string.devices_headset_selected))
            SupportingText(if (devices.selectedId == null) {
                stringResource(R.string.devices_automatic_selected,
                    devices.automaticHeadset.ifBlank { stringResource(R.string.devices_auto_unresolved) })
            } else devices.selectedLabel)
            if (devices.selectionMissing) {
                Text(stringResource(R.string.devices_selection_lost), color = MaterialTheme.colorScheme.error)
            }
            if (devices.headsets.isEmpty()) SupportingText(stringResource(R.string.devices_none))
            if (state.busy) SupportingText(stringResource(R.string.devices_busy))
            var choicesVisible by rememberSaveable { mutableStateOf(false) }
            ActionButton(
                stringResource(if (choicesVisible) R.string.devices_hide_choices else R.string.devices_choose),
                enabled = !state.busy,
                onClick = { choicesVisible = !choicesVisible }
            )
            if (choicesVisible) {
                Column(Modifier.selectableGroup()) {
                    DeviceChoice(stringResource(R.string.devices_automatic), devices.selectedId == null,
                        !state.busy) { actions.onSelectHeadset(null) }
                    devices.headsets.forEach { device ->
                        DeviceChoice(device.technicalLabel, devices.selectedId == device.id && !devices.selectionMissing,
                            !state.busy) { actions.onSelectHeadset(device.id) }
                    }
                }
                SupportingText(stringResource(R.string.devices_choice_hint))
            }
            SupportingText(stringResource(R.string.devices_music_output,
                devices.musicOutput.ifBlank { stringResource(R.string.devices_not_reported) }))
            SupportingText(stringResource(R.string.devices_communication_route,
                devices.communicationRoute.ifBlank { stringResource(R.string.devices_no_communication) }))
            AudioLevels(state)
            if (state.inputId == "PHONE_WAKE") SupportingText(stringResource(R.string.trip_phone_wake_trial))
            state.probe?.let {
                SupportingText(stringResource(R.string.devices_last_probe, it.actualInput))
                SupportingText(stringResource(R.string.devices_probe_scope))
            }
            var profilesVisible by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { profilesVisible = !profilesVisible }) {
                Text(stringResource(if (profilesVisible) R.string.devices_profiles_hide else R.string.devices_profiles_show))
            }
            if (profilesVisible) {
                Text(stringResource(R.string.devices_connected_outputs))
                SupportingText(devices.bluetoothOutputs.joinToString("\n").ifBlank {
                    stringResource(R.string.devices_no_bluetooth_output)
                })
                Text(stringResource(R.string.devices_connected_inputs))
                SupportingText(devices.bluetoothInputs.joinToString("\n").ifBlank {
                    stringResource(R.string.devices_no_bluetooth_input)
                })
                SupportingText(stringResource(R.string.devices_profiles_hint))
            }
        }
        if (devices.message.isNotBlank()) Text(devices.message, color = MaterialTheme.colorScheme.error)
        ActionButton(
            stringResource(if (devices.supported && !devices.permissionGranted) R.string.devices_enable else R.string.devices_refresh),
            onClick = actions.onRefreshDevices
        )
        ActionButton(stringResource(R.string.devices_android_controls), enabled = !state.busy,
            onClick = actions.onOpenAudioControls)
        SupportingText(stringResource(R.string.devices_output_hint))
    }
}

@Composable
private fun DeviceChoice(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp).selectable(
            selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect
        ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = null)
        Text(label, Modifier.weight(1f).padding(start = 12.dp))
    }
}
