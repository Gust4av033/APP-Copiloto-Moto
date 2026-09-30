package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.ChoiceUi
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

@Composable
fun DiagnosticsReportCard(state: CopilotUiState, actions: CopilotActions) {
    val devices = state.audioDevices
    val roundKey = "${state.audioCaptureGeneration}:${state.inputId}:${devices.selectedId}:${devices.automaticHeadset}"
    var savedRoundKey by rememberSaveable { mutableStateOf(roundKey) }
    var audible by rememberSaveable { mutableStateOf("PENDING") }
    var restored by rememberSaveable { mutableStateOf("PENDING") }
    var observationsVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(roundKey) {
        if (savedRoundKey != roundKey) {
            audible = "PENDING"
            restored = "PENDING"
            savedRoundKey = roundKey
        }
    }
    val audibleChoices = listOf(ChoiceUi("PENDING", R.string.observation_pending),
        ChoiceUi("YES", R.string.observation_audible_yes), ChoiceUi("NO", R.string.observation_audible_no))
    val restoredChoices = listOf(ChoiceUi("PENDING", R.string.observation_pending),
        ChoiceUi("YES", R.string.observation_restored_yes), ChoiceUi("NO", R.string.observation_restored_no))
    val audibleLabel = stringResource(audibleChoices.first { it.id == audible }.titleRes)
    val restoredLabel = stringResource(restoredChoices.first { it.id == restored }.titleRes)
    val modeLabel = state.inputs.firstOrNull { it.id == state.inputId }?.let { stringResource(it.titleRes) }.orEmpty()
    val heading = stringResource(R.string.report_title)
    val revision = stringResource(R.string.ui_revision)
    val reportConfig = stringResource(R.string.report_configuration)
    val reportReadings = stringResource(R.string.report_readings)
    val reportMetrics = stringResource(R.string.report_metrics_scope)
    val audibleHeading = stringResource(R.string.observation_audible)
    val restoredHeading = stringResource(R.string.observation_restored)
    val scope = stringResource(R.string.report_scope)
    SectionCard(heading) {
        SupportingText(stringResource(R.string.report_hint))
        TextButton(onClick = { observationsVisible = !observationsVisible }) {
            Text(stringResource(if (observationsVisible) R.string.observations_hide else R.string.observations_show))
        }
        if (observationsVisible) {
            Text(audibleHeading)
            ChoiceGroup(audibleChoices, audible, true) { audible = it }
            Text(restoredHeading)
            ChoiceGroup(restoredChoices, restored, true) { restored = it }
        }
        ActionButton(stringResource(R.string.report_copy), onClick = {
            actions.onCopyDiagnostics(buildString {
                appendLine("$heading · $revision")
                appendLine(reportConfig)
                appendLine(modeLabel)
                if (!state.buttonMode) appendLine("${state.phraseLabel} · ${state.sensitivity.toInt()} %")
                appendLine("Casco: ${devices.selectedLabel.ifBlank { devices.automaticHeadset }}")
                appendLine("Estado: ${state.tripStatus}")
                appendLine(reportReadings)
                appendLine("Música: ${devices.musicVolume}; Comunicación: ${devices.communicationVolume}")
                appendLine("Salida musical prevista: ${devices.musicOutput}")
                appendLine("Ruta comunicación: ${devices.communicationRoute}")
                if (devices.message.isNotBlank()) appendLine(devices.message)
                appendLine("Diagnóstico consultado: ${state.audioDiagnostic}")
                appendLine("Antes: ${state.audioBefore}")
                appendLine("Durante (captura de espera): ${state.audioDuring}")
                appendLine("Después: ${state.audioAfter}")
                state.probe?.let {
                    appendLine("Última medición de 5 s — solicitada: ${it.requestedInput}; real: ${it.actualInput}")
                    appendLine("Ruta medición: ${it.selectedOutput}; duración: ${it.durationMs} ms")
                    appendLine("Muestras: ${it.sampleCount}; RMS: ${it.rmsDbfs} dBFS; pico: ${it.peakDbfs} dBFS")
                    it.warning?.let { warning -> appendLine(warning) }
                }
                appendLine("Última orden: ${state.lastCommandResult}")
                appendLine(reportMetrics)
                appendLine("Voz: ${state.metrics.detections}; manuales: ${state.metrics.manualCommands}; transcripciones: ${state.metrics.recognized}")
                appendLine("Solicitudes: ${state.metrics.actions}; fallos: ${state.metrics.failures}; latencia: ${state.metrics.totalLatencyMs} ms")
                appendLine("$audibleHeading: $audibleLabel")
                appendLine("$restoredHeading: $restoredLabel")
                appendLine(scope)
            })
        })
        SupportingText(scope)
    }
}
