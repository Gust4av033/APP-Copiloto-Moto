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
                    appendLine("Y12 Pro · prueba de eventos del botón · límite 60 s")
                    appendLine("${state.buttonProbeMessage} · activa=${state.buttonProbeActive}")
                    appendLine("Orden propuesta: micrófono corto ×3; micrófono largo ×1; M corto ×1")
                    appendLine("Anotá si seguiste esa secuencia y qué botón pulsaste en cada momento.")
                    if (state.buttonProbeLog.isEmpty()) appendLine("Sin eventos observados en APP.")
                    state.buttonProbeLog.forEach { appendLine(it) }
                })
            })
        SupportingText(stringResource(R.string.button_probe_scope))
    }
}
