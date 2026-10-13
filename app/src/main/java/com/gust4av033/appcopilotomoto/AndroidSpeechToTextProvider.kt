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
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

enum class SpeechCaptureState { READY, SPEECH_ONSET, RESTARTING }

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
    private val playbackState = SpeechPlaybackState()
    private var recognitionTimeout: Runnable? = null
    private var requestedLanguages = "Español; disponibilidad pendiente de comprobar."

    fun languageDescription(): String = "$requestedLanguages; último idioma informado=${SpeechLanguageSupport.snapshot.lastDetectedTag.ifBlank { "ninguno" }}; " +
        "cambio informado=${SpeechLanguageSupport.snapshot.switchObserved}."

    fun listenOnce(
        mode: SpeechCaptureMode = SpeechCaptureMode.COMMAND,
        onState: (SpeechCaptureState) -> Unit = {},
        readySignal: () -> Result<Unit> = { Result.success(Unit) },
        playbackResumePolicy: (String?) -> PlaybackResumePolicy = { PlaybackResumePolicy.NOW },
        onResult: (Result<String>) -> Unit
    ) {
        val pausedBeforeRestart = cancel(resumePlayback = false)
        playbackState.rememberPause(pausedBeforeRestart)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            finish(Result.failure(IllegalStateException("No hay reconocimiento de voz local disponible en este teléfono.")),
                generation, playbackResumePolicy, onResult)
            return
        }
        val session = generation
        val deadline = SpeechCaptureDeadline(mode, SystemClock.elapsedRealtime())
        fun begin(languages: SpeechLanguageSnapshot) {
            if (session != generation) return
            try {
                var ready = false
                val speechEvidence = SpeechStartEvidence()
                var activeInputs = "no informadas"
                var attempt = 0
                val recovery = ConfirmationSilenceRecovery()
                val pauseRequestedForSession = runCatching { media.pauseIfPlaying() }.getOrDefault(false)
                playbackState.rememberPause(pauseRequestedForSession)
                audioFocusGranted = runCatching {
                    audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                }.getOrDefault(false)
                val focusGrantedForSession = audioFocusGranted
                val locale = if (mode == SpeechCaptureMode.SONG_TITLE) Locale.US
                    else Locale.forLanguageTag(languages.spanishTag)
                val automatic = Build.VERSION.SDK_INT >= 34 && SpeechLanguagePolicy.automaticFor(mode, languages)
                requestedLanguages = if (automatic) "Automático solicitado: ${locale.toLanguageTag()} + ${languages.englishTag}"
                    else "Idioma solicitado: ${locale.toLanguageTag()}; disponibilidad=${languages.phase.name}"
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    if (automatic) {
                        val tags = arrayListOf(locale.toLanguageTag(), checkNotNull(languages.englishTag))
                        putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                        putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, tags)
                        putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, tags)
                    }
                    if (mode == SpeechCaptureMode.DICTATION) {
                        // Sugerencias: el motor puede aplicar su propia detección de silencio.
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
                    }
                }
                fun startAttempt() {
                    if (session != generation) return
                    val attemptId = ++attempt // Invalida callbacks del motor anterior antes de destruirlo.
                    runCatching { recognizer?.cancel() }
                    runCatching { recognizer?.destroy() }
                    val current = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    recognizer = current
                    var attemptReady = false
                    fun valid() = session == generation && attemptId == attempt
                    current.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            val now = SystemClock.elapsedRealtime()
                            if (valid() && !attemptReady && (deadline.ready(now) || deadline.listeningAt(now))) {
                                attemptReady = true
                                ready = true
                                speechEvidence.ready(now)
                                activeInputs = activeInputDevices()
                                armRecognitionTimeout(session, (deadline.expiresAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(1L),
                                    "La escucha terminó sin respuesta.", playbackResumePolicy, onResult)
                                onState(SpeechCaptureState.READY)
                                if (valid()) {
                                    val signal = runCatching { readySignal() }.getOrElse { Result.failure(it) }
                                    signal.exceptionOrNull()?.let {
                                        finish(Result.failure(IllegalStateException("No se pudo emitir la señal para hablar: ${it.message}")),
                                            session, playbackResumePolicy, onResult)
                                    }
                                }
                            }
                        }
                        override fun onBeginningOfSpeech() {
                            if (valid()) {
                                // The 360 ms ready cue can itself trigger the engine's speech callback.
                                // Partial text still counts as voice even if the user starts early.
                                speechEvidence.onset(SystemClock.elapsedRealtime())
                                activeInputs = activeInputDevices().takeUnless { it == "no informadas" } ?: activeInputs
                                onState(SpeechCaptureState.SPEECH_ONSET)
                            }
                        }
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onPartialResults(partialResults: Bundle?) {
                            if (valid() && partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.any { it.isNotBlank() } == true) speechEvidence.partial(true)
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                        override fun onLanguageDetection(results: Bundle) {
                            if (valid() && Build.VERSION.SDK_INT >= 34 && automatic) {
                                SpeechLanguageSupport.observed(results.getString(SpeechRecognizer.DETECTED_LANGUAGE).orEmpty(),
                                    results.getInt(SpeechRecognizer.LANGUAGE_SWITCH_RESULT, SpeechRecognizer.LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED) ==
                                        SpeechRecognizer.LANGUAGE_SWITCH_RESULT_SUCCEEDED)
                            }
                        }
                        override fun onError(error: Int) {
                            if (!valid()) return
                            val rejectedAutomatic = automatic && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
                            if (rejectedAutomatic) SpeechLanguageSupport.automaticRejected(
                                "El reconocedor rechazó la mezcla de idiomas. La próxima orden usará español; comprobá idiomas antes de reintentar inglés.")
                            if (recovery.shouldRestart(mode, ready, speechEvidence.detected,
                                    error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                                    deadline.expiresAtMs - SystemClock.elapsedRealtime())) {
                                // Mantiene pausa/foco y el mismo plazo. Nunca reenvía una acción externa.
                                onState(SpeechCaptureState.RESTARTING)
                                if (valid()) {
                                    try { startAttempt() } catch (failure: Exception) {
                                        finish(Result.failure(failure), session, playbackResumePolicy, onResult)
                                    }
                                }
                                return
                            }
                            val detail = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "No se entendieron palabras reconocibles"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz durante la escucha"
                                SpeechRecognizer.ERROR_AUDIO -> "Falló la captura de audio"
                                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "El motor local no admite ${locale.toLanguageTag()}"
                                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Falta descargar el modelo de voz ${locale.toLanguageTag()} en el teléfono"
                                else -> "Error de reconocimiento local"
                            }
                            val languageDetail = if (rejectedAutomatic) "El motor rechazó el reconocimiento español/inglés; se conserva español para la siguiente orden" else detail
                            val diagnostic = "$languageDetail (código $error). Idioma: ${locale.toLanguageTag()}; STT listo: ${if (ready) "sí" else "no"}; inicio de voz marcado: ${if (speechEvidence.detected) "sí" else "no"}; entradas activas: $activeInputs; pausa multimedia: ${if (pauseRequestedForSession) "solicitada" else "no solicitada"}; foco: ${if (focusGrantedForSession) "concedido" else "no concedido"}."
                            finish(Result.failure(IllegalStateException(diagnostic)), session, playbackResumePolicy, onResult)
                        }
                        override fun onResults(results: Bundle?) {
                            if (!valid()) return
                            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()?.trim().orEmpty()
                            val value = if (heard.isBlank()) {
                                Result.failure(IllegalStateException("No se entendió la orden."))
                            } else Result.success(heard)
                            finish(value, session, playbackResumePolicy, onResult)
                        }
                    })
                    // Da margen a la pérdida de foco y, en reintentos, al cierre del motor anterior.
                    mainHandler.postDelayed({
                        if (valid()) {
                            try {
                                current.startListening(intent)
                            } catch (failure: Exception) {
                                finish(Result.failure(failure), session, playbackResumePolicy, onResult)
                            }
                        }
                    }, VoiceInteractionTiming.SPEECH_START_MS)
                }
                armRecognitionTimeout(session, (deadline.expiresAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(1L),
                    "El reconocedor no llegó a estar listo; captura cancelada.", playbackResumePolicy, onResult)
                startAttempt()
            } catch (failure: Exception) {
                finish(Result.failure(failure), session, playbackResumePolicy, onResult)
            }
        }
        SpeechLanguageSupport.refresh(context.applicationContext) { begin(it) }
    }

    /** Devuelve si APP pausó música; el servicio puede reanudarla tras restaurar la ruta. */
    fun cancel(resumePlayback: Boolean = true): Boolean {
        generation++
        clearRecognitionTimeout()
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        releaseAudioFocus()
        val pausedByApp = playbackState.takeResume()
        if (resumePlayback && pausedByApp) resumePlayback()
        return pausedByApp
    }

    /** Lectura diagnóstica; Android puede ocultar la entrada de un reconocedor externo. */
    fun activeInputDescription(): String = activeInputDevices()

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

    private fun resumePlayback() {
        // PLAY es idempotente; se evita si otra reproducción sigue activa.
        runCatching { if (!audioManager.isMusicActive) media.resumePlayback() }
    }

    private fun armRecognitionTimeout(session: Int, durationMs: Long, message: String,
        policy: (String?) -> PlaybackResumePolicy, callback: (Result<String>) -> Unit) {
        clearRecognitionTimeout()
        recognitionTimeout = Runnable {
            finish(Result.failure(IllegalStateException(message)), session, policy, callback)
        }.also { mainHandler.postDelayed(it, durationMs) }
    }

    private fun clearRecognitionTimeout() {
        recognitionTimeout?.let(mainHandler::removeCallbacks)
        recognitionTimeout = null
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
        playbackResumePolicy: (String?) -> PlaybackResumePolicy,
        callback: (Result<String>) -> Unit
    ) {
        if (session != generation) return
        generation++
        clearRecognitionTimeout()
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        releaseAudioFocus()
        val policy = runCatching { playbackResumePolicy(result.getOrNull()) }
            .getOrDefault(PlaybackResumePolicy.NOW)
        if (playbackState.finish(policy)) resumePlayback()
        callback(result)
    }
}


