package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Reconoce una sola orden, en el dispositivo, tras pulsar el botón de prueba. */
class AndroidSpeechToTextProvider(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0

    fun listenOnce(onResult: (Result<String>) -> Unit) {
        cancel()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onResult(Result.failure(IllegalStateException("No hay reconocimiento de voz local disponible en este teléfono.")))
            return
        }
        val session = generation
        try {
            val current = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = current
            current.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onError(error: Int) {
                    finish(Result.failure(IllegalStateException("Error de reconocimiento local: $error")), session, onResult)
                }
                override fun onResults(results: Bundle?) {
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    val value = if (heard.isBlank()) {
                        Result.failure(IllegalStateException("No se entendió la orden."))
                    } else Result.success(heard)
                    finish(value, session, onResult)
                }
            })
            val locale = Locale.getDefault().takeIf { it.language == "es" } ?: Locale.forLanguageTag("es-ES")
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            current.startListening(intent)
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
    }

    private fun finish(result: Result<String>, session: Int, callback: (Result<String>) -> Unit) {
        if (session != generation) return
        generation++
        recognizer?.destroy()
        recognizer = null
        callback(result)
    }
}
