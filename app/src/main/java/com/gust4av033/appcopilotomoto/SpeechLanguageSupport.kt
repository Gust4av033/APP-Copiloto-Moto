package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.os.SystemClock
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Queries/model preparation never open a mic, request audio focus, or run from composition. */
internal object SpeechLanguageSupport {
    var snapshot by mutableStateOf(SpeechLanguageSnapshot())
        private set
    private val handler = Handler(Looper.getMainLooper())
    private var sequence = 0L
    private var probe: SpeechRecognizer? = null
    private var timeout: Runnable? = null
    private var checkedAt = 0L
    private var automaticBlocked = false
    private val waiters = mutableListOf<(SpeechLanguageSnapshot) -> Unit>()

    fun refresh(context: Context, force: Boolean = false, onFinished: (SpeechLanguageSnapshot) -> Unit = {}) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (snapshot.downloading) { onFinished(snapshot); return }
        if (snapshot.phase == SpeechLanguagePhase.CHECKING) { waiters += onFinished; return }
        if (!force && automaticBlocked) { onFinished(snapshot); return }
        if (force) automaticBlocked = false
        if (!force && checkedAt != 0L && SystemClock.elapsedRealtime() - checkedAt < 600_000L) {
            onFinished(snapshot); return
        }
        waiters += onFinished
        val id = ++sequence
        val preferred = Locale.getDefault().takeIf { it.language == "es" }?.toLanguageTag() ?: "es-ES"
        snapshot = snapshot.copy(phase = SpeechLanguagePhase.CHECKING, spanishTag = preferred, detail = "")
        if (Build.VERSION.SDK_INT < 34) {
            complete(id, snapshot.copy(phase = SpeechLanguagePhase.UNSUPPORTED)); return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            complete(id, snapshot.copy(phase = SpeechLanguagePhase.UNAVAILABLE)); return
        }
        try {
            val current = createProbe(context)
            probe = current
            armTimeout(id, VoiceInteractionTiming.LANGUAGE_CHECK_MS) {
                complete(id, snapshot.copy(phase = SpeechLanguagePhase.FAILED, detail = "Consulta de idiomas sin respuesta."))
            }
            current.checkRecognitionSupport(intentFor(preferred), context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        if (id != sequence) return
                        complete(id, SpeechLanguagePolicy.fromInstalled(Build.VERSION.SDK_INT,
                            support.installedOnDeviceLanguages, Locale.getDefault().toLanguageTag()))
                    }
                    override fun onError(error: Int) {
                        complete(id, snapshot.copy(phase = SpeechLanguagePhase.FAILED, detail = "Consulta de idiomas: código $error."))
                    }
                })
        } catch (failure: Exception) {
            complete(id, snapshot.copy(phase = SpeechLanguagePhase.FAILED, detail = failure.message.orEmpty()))
        }
    }

    /** User initiated model preparation, outside a trip. Never changes to online recognition. */
    fun prepareEnglish(context: Context) {
        if (Build.VERSION.SDK_INT < 34 || snapshot.downloading || snapshot.phase == SpeechLanguagePhase.CHECKING) return
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return
        val id = ++sequence
        cleanup()
        snapshot = snapshot.copy(downloading = true, detail = "")
        try {
            val current = createProbe(context)
            probe = current
            armTimeout(id, 120_000L) {
                complete(id, snapshot.copy(downloading = false, detail = "La preparación sigue pendiente; volvé a comprobar idiomas."))
            }
            current.triggerModelDownload(intentFor("en-US"), context.mainExecutor, object : ModelDownloadListener {
                override fun onSuccess() {
                    if (id != sequence) return
                    complete(id, snapshot.copy(downloading = false))
                    refresh(context.applicationContext, force = true)
                }
                override fun onProgress(completedPercent: Int) = Unit
                override fun onScheduled() {
                    complete(id, snapshot.copy(downloading = false, detail = "Android programó la descarga de inglés; comprobá idiomas cuando termine."))
                }
                override fun onError(error: Int) {
                    complete(id, snapshot.copy(downloading = false, detail = "No se pudo preparar inglés: código $error."))
                }
            })
        } catch (failure: Exception) {
            complete(id, snapshot.copy(downloading = false, detail = failure.message.orEmpty()))
        }
    }

    fun observed(tag: String, switched: Boolean) {
        snapshot = snapshot.copy(lastDetectedTag = tag, switchObserved = snapshot.switchObserved || switched)
    }

    fun automaticRejected(reason: String) {
        automaticBlocked = true
        snapshot = SpeechLanguagePolicy.rejectedAutomatic(snapshot, reason)
        checkedAt = SystemClock.elapsedRealtime()
    }

    private fun intentFor(tag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun createProbe(context: Context): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext).apply {
            // Binder queries need a listener, but this instance never calls startListening.
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) = Unit
                override fun onResults(results: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

    private fun armTimeout(id: Long, duration: Long, action: () -> Unit) {
        timeout?.let(handler::removeCallbacks)
        timeout = Runnable { if (id == sequence) action() }.also { handler.postDelayed(it, duration) }
    }

    private fun complete(id: Long, result: SpeechLanguageSnapshot) {
        if (id != sequence) return
        sequence++
        cleanup()
        checkedAt = SystemClock.elapsedRealtime()
        snapshot = result
        val callbacks = waiters.toList()
        waiters.clear()
        callbacks.forEach { it(result) }
    }

    private fun cleanup() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        runCatching { probe?.destroy() }
        probe = null
    }
}
