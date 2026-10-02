package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

@Composable
fun ActivationSummary(state: CopilotUiState) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(when (state.inputId) {
                "BUTTON" -> R.string.mode_button_title
                "PHONE_WAKE" -> R.string.mode_phone_title
                else -> R.string.mode_voice_title
            }), style = MaterialTheme.typography.titleMedium)
            SupportingText(if (state.manualActivationOnly) stringResource(R.string.manual_activation_summary)
                else if (state.buttonMode) stringResource(R.string.mode_button_detail)
                else if (state.inputId == "PHONE_WAKE") stringResource(R.string.trip_phone_wake_trial)
                else stringResource(R.string.mode_voice_instruction, state.phraseLabel))
        }
    }
}

/** Política de las órdenes del viaje; las lecturas de índices no confirman un cambio acústico. */
@Composable
fun AudioLevels(state: CopilotUiState) {
    val devices = state.audioDevices
    if (devices.musicVolume.isBlank() && devices.communicationVolume.isBlank()) return
    listOf(R.string.volume_music to devices.musicVolume,
        R.string.volume_communication to devices.communicationVolume).forEach { (label, value) ->
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SupportingText(stringResource(label))
                Text(value.ifBlank { stringResource(R.string.devices_not_reported) },
                    style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
    SupportingText(stringResource(if (state.buttonMode) R.string.volume_button_hint
        else if (state.inputId == "BLUETOOTH") R.string.volume_voice_hint else R.string.volume_phone_hint))
    SupportingText(stringResource(R.string.volume_reading_scope))
}

