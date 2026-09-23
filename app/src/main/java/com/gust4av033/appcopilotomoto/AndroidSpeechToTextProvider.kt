package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

enum class SpeechCaptureState { READY, SPEECH_ONSET }

/** Reconoce una sola orden, en el dispositivo, tras pulsar el botón de prueba. */
class AndroidSpeechToTextProvider(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val media = MediaActionAdapter(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build())
        .build()
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var audioFocusGranted = false
    private var playbackPauseRequested = false

    fun listenOnce(
        onState: (SpeechCaptureState) -> Unit = {},
        shouldResumePlayback: (String?) -> Boolean = { true },
        onResult: (Result<String>) -> Unit
    ) {
        cancel()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onResult(Result.failure(IllegalStateException("No hay reconocimiento de voz local disponible en este teléfono.")))
            return
        }
        val session = generation
        try {
            var ready = false
            var speechOnset = false
            var activeInputs = "no informadas"
            playbackPauseRequested = runCatching { media.pauseIfPlaying() }.getOrDefault(false)
            val pauseRequestedForSession = playbackPauseRequested
            audioFocusGranted = runCatching {
                audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }.getOrDefault(false)
            val focusGrantedForSession = audioFocusGranted
            val current = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = current
            current.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (session == generation) {
                        ready = true
                        activeInputs = activeInputDevices()
                        onState(SpeechCaptureState.READY)
                    }
                }
                override fun onBeginningOfSpeech() {
                    if (session == generation) {
                        speechOnset = true
                        activeInputs = activeInputDevices().takeUnless { it == "no informadas" } ?: activeInputs
                        onState(SpeechCaptureState.SPEECH_ONSET)
                    }
                }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onError(error: Int) {
                    val detail = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No se entendieron palabras reconocibles"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz durante la escucha"
                        SpeechRecognizer.ERROR_AUDIO -> "Falló la captura de audio"
                        else -> "Error de reconocimiento local"
                    }
                    val diagnostic = "$detail (código $error). STT listo: ${if (ready) "sí" else "no"}; inicio de voz marcado: ${if (speechOnset) "sí" else "no"}; entradas activas: $activeInputs; pausa multimedia: ${if (pauseRequestedForSession) "solicitada" else "no solicitada"}; foco: ${if (focusGrantedForSession) "concedido" else "no concedido"}."
                    finish(Result.failure(IllegalStateException(diagnostic)), session, shouldResumePlayback, onResult)
                }
                override fun onResults(results: Bundle?) {
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    val value = if (heard.isBlank()) {
                        Result.failure(IllegalStateException("No se entendió la orden."))
                    } else Result.success(heard)
                    finish(value, session, shouldResumePlayback, onResult)
                }
            })
            val locale = Locale.getDefault().takeIf { it.language == "es" } ?: Locale.forLanguageTag("es-ES")
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            // Da un momento al reproductor para responder a la pérdida temporal de foco.
            mainHandler.postDelayed({
                if (session == generation) {
                    try {
                        current.startListening(intent)
                    } catch (failure: Exception) {
                        cancel()
                        onResult(Result.failure(failure))
                    }
                }
            }, 250L)
        } catch (failure: Exception) {
            cancel()
            onResult(Result.failure(failure))
        }
    }

    fun cancel() {
        generation++
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        releaseAudioFocus()
        resumePlaybackIfNeeded()
    }

    private fun activeInputDevices(): String = runCatching {
        audioManager.activeRecordingConfigurations.mapNotNull { configuration ->
            configuration.audioDevice?.let { device ->
                val label = when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "micrófono del teléfono"
                    else -> "entrada tipo ${device.type}"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && configuration.isClientSilenced) "$label (silenciada)" else label
            }
        }.distinct().joinToString().ifBlank { "no informadas" }
    }.getOrDefault("no informadas")

    private fun resumePlaybackIfNeeded() {
        if (playbackPauseRequested) {
            playbackPauseRequested = false
            // PLAY es idempotente; se evita si otra reproducción sigue activa.
            runCatching { if (!audioManager.isMusicActive) media.resumePlayback() }
        }
    }

    private fun releaseAudioFocus() {
        if (audioFocusGranted) {
            audioFocusGranted = false
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        }
    }

    private fun finish(
        result: Result<String>,
        session: Int,
        shouldResumePlayback: (String?) -> Boolean,
        callback: (Result<String>) -> Unit
    ) {
        if (session != generation) return
        generation++
        recognizer?.destroy()
        recognizer = null
        releaseAudioFocus()
        if (runCatching { shouldResumePlayback(result.getOrNull()) }.getOrDefault(true)) {
            resumePlaybackIfNeeded()
        } else {
            // La orden Pausar es la intención final del usuario, aunque esta captura haya pausado música.
            playbackPauseRequested = false
        }
        callback(result)
    }
}
