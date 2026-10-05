package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

@Composable
fun HeadsetButtonProbeCard(state: CopilotUiState, actions: CopilotActions) {
    SectionCard(stringResource(R.string.button_probe_title)) {
        SupportingText(stringResource(R.string.button_probe_hint))
        SupportingText(stringResource(R.string.button_probe_controls))
        SupportingText(stringResource(R.string.button_probe_steps))
        HeadsetControlsSummary(state)
        ActionButton(stringResource(if (state.buttonProbeActive) R.string.button_probe_stop else R.string.button_probe_start),
            enabled = state.buttonProbeActive || !state.busy,
            primary = true,
            onClick = actions.onToggleButtonProbe)
        if (state.buttonProbeMessage.isNotBlank()) SupportingText(state.buttonProbeMessage)
        if (state.buttonProbeLog.isEmpty()) SupportingText(stringResource(R.string.button_probe_empty))
        else state.buttonProbeLog.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        ActionButton(stringResource(R.string.button_probe_copy),
            enabled = state.buttonProbeMessage.isNotBlank(),
            onClick = {
                actions.onCopyDiagnostics(buildString {
                    appendLine("Prueba de controles del casco · límite 60 s")
                    appendLine("Casco seleccionado: ${state.audioDevices.controlHeadsetName.ifBlank { "sin identificar" }}")
                    appendLine("${state.buttonProbeMessage} · activa=${state.buttonProbeActive}")
                    appendLine("Orden propuesta: anterior ×3; siguiente ×3; pausa/reproducir; gesto de asistente")
                    appendLine("Anotá qué control físico usaste; una señal sin origen informado no identifica por sí sola el botón.")
                    if (state.buttonProbeLog.isEmpty()) appendLine("Sin eventos observados en APP.")
                    state.buttonProbeLog.forEach { appendLine(it) }
                })
            })
        SupportingText(stringResource(R.string.button_probe_scope))
    }
}
