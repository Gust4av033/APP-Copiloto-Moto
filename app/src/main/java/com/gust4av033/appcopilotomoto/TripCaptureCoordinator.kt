package com.gust4av033.appcopilotomoto

import android.content.Context
import android.os.SystemClock

/** Cada captura termina una vez; un callback posterior a cancelar no puede ejecutar acciones. */
internal class TripCaptureCoordinator(
    private val context: Context,
    private val speech: () -> AndroidSpeechToTextProvider,
    private val captureContext: () -> String,
    private val readyValidation: () -> Result<Unit> = { Result.success(Unit) }
) {
    private var current = 0L
    private var sequence = 0L
    private val readyCue = TripReadyCue(context)

    fun listenOnce(mode: SpeechCaptureMode = SpeechCaptureMode.COMMAND, onState: (SpeechCaptureState) -> Unit = {},
        playbackResumePolicy: (String?) -> PlaybackResumePolicy = { PlaybackResumePolicy.NOW },
        onResult: (Result<String>) -> Unit) {
        if (current != 0L) TripTestState.record(context, PipelineEvent.STT_CANCELLED)
        readyCue.cancel()
        val id = ++sequence
        current = id
        val startedAt = SystemClock.elapsedRealtime()
        TripTestState.pipeline = TripTestState.pipeline.copy(lastCaptureContext = captureContext())
        TripTestState.record(context, PipelineEvent.STT_STARTED)
        speech().listenOnce(mode = mode, onState = { state ->
            if (current == id) {
                if (state == SpeechCaptureState.RESTARTING) {
                    TripTestState.record(context, PipelineEvent.STT_SILENCE_RESTART)
                    TripTestState.interactionPhase = TripInteractionPhase.PREPARING
                    TripTestState.status = "La escucha cerró por silencio; reabriendo. Esperá la señal para responder."
                }
                onState(state)
            }
        }, readySignal = {
            if (current != id) Result.failure(IllegalStateException("Captura cancelada antes del tono."))
            else {
                val result = readyValidation().mapCatching {
                    readyCue.play().getOrThrow()
                }
                TripTestState.pipeline = TripTestState.pipeline.copy(lastReadyCue =
                    result.getOrNull() ?: "Falló la señal: ${result.exceptionOrNull()?.message}")
                TripTestState.record(context, if (result.isSuccess) PipelineEvent.READY_CUE_STARTED else PipelineEvent.READY_CUE_FAILED)
                result.map { Unit }
            }
        }, playbackResumePolicy = playbackResumePolicy) onCaptureResult@ { result ->
            if (current != id) return@onCaptureResult
            readyCue.cancel()
            current = 0L
            TripTestState.pipeline = TripTestState.pipeline.copy(lastSttMs = SystemClock.elapsedRealtime() - startedAt)
            TripTestState.pipeline = TripTestState.pipeline.copy(lastLanguageStatus = speech().languageDescription())
            TripTestState.record(context, if (result.isSuccess) PipelineEvent.STT_SUCCESS else PipelineEvent.STT_FAILURE)
            onResult(result)
        }
    }

    fun cancel(resumePlayback: Boolean = true, signalCancellation: Boolean = false): Boolean {
        readyCue.cancel()
        if (current != 0L) {
            current = 0L
            TripTestState.record(context, PipelineEvent.STT_CANCELLED)
        }
        if (signalCancellation) cancellationCue()
        return speech().cancel(resumePlayback)
    }

    fun cancellationCue() {
        val result = readyValidation().mapCatching { readyCue.play(VoiceCue.CANCELLED).getOrThrow() }
        TripTestState.pipeline = TripTestState.pipeline.copy(lastCancellationCue =
            result.getOrNull() ?: "Falló el tono de cancelación: ${result.exceptionOrNull()?.message}")
        TripTestState.record(context, if (result.isSuccess) PipelineEvent.CANCELLATION_CUE_STARTED else PipelineEvent.CANCELLATION_CUE_FAILED)
    }

    fun releaseCue() = readyCue.cancel()
}


