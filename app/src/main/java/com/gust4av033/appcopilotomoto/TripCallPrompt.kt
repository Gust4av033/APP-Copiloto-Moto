package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Anuncia una acción antes de abrir la escucha de confirmación. */
class TripCallPrompt(private val context: Context,
    private val audioUsage: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION) {
    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var generation = 0
    private var timeout: Runnable? = null
    private var announcing = false

    fun isAnnouncing(): Boolean = announcing

    fun announce(name: String, direct: Boolean = true, onFinished: (Boolean) -> Unit) = speak(
        if (direct) "Llamar a $name. Después del tono, di confirmar llamada o cancelar."
        else "Abrir marcador para $name. Deberás iniciar la llamada en Teléfono. Después del tono, di confirmar llamada o cancelar.",
        onFinished)

    fun announceApp(name: String, draft: String? = null, onFinished: (Boolean) -> Unit) = speak(
        VoiceInteractionTiming.appAnnouncement(name, draft), onFinished)

    fun askForDraft(name: String, onFinished: (Boolean) -> Unit) = speak(
        "Mensaje para $name. Después del tono, dicta un mensaje breve o di cancelar.", onFinished)

    fun askForEnglishSong(onFinished: (Boolean) -> Unit) = speak(
        "Después del tono, di el nombre de la canción en inglés. Para cancelar, di cancel.", onFinished)

    fun explain(message: String, onFinished: (Boolean) -> Unit) = speak(message.take(480), onFinished)

    private fun speak(text: String, onFinished: (Boolean) -> Unit) {
        cancel()
        val cycle = ++generation
        announcing = true
        val instance = TextToSpeech(context.applicationContext) { state ->
            handler.post {
                if (cycle != generation) return@post
                val speaker = engine ?: return@post
                if (state != TextToSpeech.SUCCESS || speaker.setLanguage(Locale.forLanguageTag("es-ES")) < 0) {
                    finish(cycle, false, onFinished)
                    return@post
                }
                speaker.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(audioUsage)
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
                armTimeout(cycle, VoiceInteractionTiming.playbackMs(text), onFinished)
                if (speaker.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "trip-prompt-$cycle") != TextToSpeech.SUCCESS) {
                    finish(cycle, false, onFinished)
                }
            }
        }
        engine = instance
        armTimeout(cycle, VoiceInteractionTiming.PREPARATION_MS, onFinished)
    }

    private fun armTimeout(cycle: Int, durationMs: Long, onFinished: (Boolean) -> Unit) {
        timeout?.let(handler::removeCallbacks)
        timeout = Runnable { finish(cycle, false, onFinished) }.also {
            handler.postDelayed(it, durationMs)
        }
    }

    private fun finish(cycle: Int, ok: Boolean, onFinished: (Boolean) -> Unit) {
        if (cycle != generation) return
        cancel()
        onFinished(ok)
    }

    fun cancel(): Boolean {
        val wasAnnouncing = announcing
        announcing = false
        generation++
        timeout?.let(handler::removeCallbacks)
        timeout = null
        engine?.stop()
        engine?.shutdown()
        engine = null
        return wasAnnouncing
    }
}


