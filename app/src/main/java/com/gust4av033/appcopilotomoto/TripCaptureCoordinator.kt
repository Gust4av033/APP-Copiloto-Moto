package com.gust4av033.appcopilotomoto

import android.content.Context
import android.os.SystemClock

/** Cada captura termina una vez; un callback posterior a cancelar no puede ejecutar acciones. */
internal class TripCaptureCoordinator(
    private val context: Context,
    private val speech: () -> AndroidSpeechToTextProvider,
    private val captureContext: () -> String
) {
    private var current = 0L
    private var sequence = 0L

    fun listenOnce(mode: SpeechCaptureMode = SpeechCaptureMode.COMMAND, onState: (SpeechCaptureState) -> Unit = {},
        playbackResumePolicy: (String?) -> PlaybackResumePolicy = { PlaybackResumePolicy.NOW },
        onResult: (Result<String>) -> Unit) {
        if (current != 0L) TripTestState.record(context, PipelineEvent.STT_CANCELLED)
        val id = ++sequence
        current = id
        val startedAt = SystemClock.elapsedRealtime()
        TripTestState.pipeline = TripTestState.pipeline.copy(lastCaptureContext = captureContext())
        TripTestState.record(context, PipelineEvent.STT_STARTED)
        speech().listenOnce(mode = mode, onState = { if (current == id) onState(it) }, playbackResumePolicy = playbackResumePolicy) onCaptureResult@ { result ->
            if (current != id) return@onCaptureResult
            current = 0L
            TripTestState.pipeline = TripTestState.pipeline.copy(lastSttMs = SystemClock.elapsedRealtime() - startedAt)
            TripTestState.record(context, if (result.isSuccess) PipelineEvent.STT_SUCCESS else PipelineEvent.STT_FAILURE)
            onResult(result)
        }
    }

    fun cancel(resumePlayback: Boolean = true): Boolean {
        if (current != 0L) {
            current = 0L
            TripTestState.record(context, PipelineEvent.STT_CANCELLED)
        }
        return speech().cancel(resumePlayback)
    }
}
