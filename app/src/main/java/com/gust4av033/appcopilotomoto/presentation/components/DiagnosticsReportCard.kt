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
    if (!state.developerEnabled) return
    val devices = state.audioDevices
    val roundKey = "${state.audioCaptureGeneration}:${state.inputId}:${state.manualActivationOnly}:${devices.selectedId}:${devices.automaticHeadset}"
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
        if (state.developerToolsAvailable) TextButton(onClick = { observationsVisible = !observationsVisible }) {
            Text(stringResource(if (observationsVisible) R.string.observations_hide else R.string.observations_show))
        }
        if (state.developerToolsAvailable && observationsVisible) {
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
                if (state.manualActivationOnly) appendLine("Activación manual: micrófono cerrado entre órdenes")
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
                appendLine("Ciclo de orden: ${state.conversationAudioStatus}")
                appendLine("Después: ${state.audioAfter}")
                state.probe?.let {
                    appendLine("Última medición de 5 s — solicitada: ${it.requestedInput}; real: ${it.actualInput}")
                    appendLine("Ruta medición: ${it.selectedOutput}; duración: ${it.durationMs} ms")
                    appendLine("Muestras: ${it.sampleCount}; RMS: ${it.rmsDbfs} dBFS; pico: ${it.peakDbfs} dBFS")
                    it.warning?.let { warning -> appendLine(warning) }
                }
                appendLine("Última orden: ${state.lastCommandResult}")
                state.pendingAppName?.let { appendLine("Aplicación pendiente: $it") }
                appendLine("Botón dedicado: ${state.tripButtonBinding}; acceso multimedia=${state.mediaAccessGranted}")
                appendLine(state.pipelineMetrics.report())
                appendLine(reportMetrics)
                appendLine("Voz: ${state.metrics.detections}; manuales: ${state.metrics.manualCommands}; transcripciones: ${state.metrics.recognized}")
                appendLine("Solicitudes: ${state.metrics.actions}; fallos: ${state.metrics.failures}; latencia: ${state.metrics.totalLatencyMs} ms")
                appendLine("$audibleHeading: $audibleLabel")
                appendLine("$restoredHeading: $restoredLabel")
                appendLine(scope)
            })
        })
        if (state.developerToolsAvailable) {
        SupportingText(stringResource(R.string.pipeline_observation_hint))
        ActionButton(stringResource(R.string.pipeline_attempt), onClick = {
            actions.onTestObservation(com.gust4av033.appcopilotomoto.PipelineEvent.ACTIVATION_ATTEMPT_ANNOTATED)
        })
        ActionButton(stringResource(R.string.pipeline_false_positive), onClick = {
            actions.onTestObservation(com.gust4av033.appcopilotomoto.PipelineEvent.FALSE_POSITIVE_ANNOTATED)
        })
        ActionButton(stringResource(R.string.pipeline_external_effect), onClick = {
            actions.onTestObservation(com.gust4av033.appcopilotomoto.PipelineEvent.EXTERNAL_EFFECT_ANNOTATED)
        })
        ActionButton(stringResource(R.string.pipeline_audible_restore), onClick = {
            actions.onTestObservation(com.gust4av033.appcopilotomoto.PipelineEvent.AUDIO_RESTORED_AUDIBLE_ANNOTATED)
        })
        }
        SupportingText("STT: ${state.pipelineMetrics.count(com.gust4av033.appcopilotomoto.PipelineEvent.STT_SUCCESS)} correctos / " +
            "${state.pipelineMetrics.count(com.gust4av033.appcopilotomoto.PipelineEvent.STT_FAILURE)} fallidos. " +
            "Despachos: ${state.pipelineMetrics.count(com.gust4av033.appcopilotomoto.PipelineEvent.ACTION_DISPATCHED)}")
        SupportingText(scope)
    }
}


