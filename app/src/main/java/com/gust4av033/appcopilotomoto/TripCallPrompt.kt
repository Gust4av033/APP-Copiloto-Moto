package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Anuncia el destinatario antes de permitir la confirmación por voz. */
class TripCallPrompt(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var generation = 0
    private var timeout: Runnable? = null

    fun announce(name: String, onFinished: (Boolean) -> Unit) {
        cancel()
        val cycle = ++generation
        val instance = TextToSpeech(context.applicationContext) { state ->
            handler.post {
                if (cycle != generation) return@post
                val speaker = engine ?: return@post
                if (state != TextToSpeech.SUCCESS || speaker.setLanguage(Locale.forLanguageTag("es-ES")) < 0) {
                    finish(cycle, false, onFinished)
                    return@post
                }
                speaker.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                speaker.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) = Unit
                    override fun onDone(utteranceId: String) {
                        handler.post { finish(cycle, true, onFinished) }
                    }
                    override fun onError(utteranceId: String) {
                        handler.post { finish(cycle, false, onFinished) }
                    }
                })
                val text = "Llamar a $name. Después del tono, di confirmar llamada o cancelar."
                if (speaker.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "trip-call-$cycle") != TextToSpeech.SUCCESS) {
                    finish(cycle, false, onFinished)
                }
            }
        }
        engine = instance
        timeout = Runnable { finish(cycle, false, onFinished) }.also {
            handler.postDelayed(it, 10_000L)
        }
    }

    private fun finish(cycle: Int, ok: Boolean, onFinished: (Boolean) -> Unit) {
        if (cycle != generation) return
        cancel()
        onFinished(ok)
    }

    fun cancel() {
        generation++
        timeout?.let(handler::removeCallbacks)
        timeout = null
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
