package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Short, noninteractive voice output. It ducks music and never opens the microphone. */
internal class InformationalTripSpeaker(context: Context) {
    companion object { const val MAX_SPEECH_MS = 6_000L }
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val prompt = TripCallPrompt(context.applicationContext, AudioAttributes.USAGE_ASSISTANT)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).build()
    private var busy = false
    private var generation = 0
    private var timeout: Runnable? = null
    val isSpeaking get() = busy

    fun speak(text: String, onFinished: (Boolean) -> Unit = {}): Boolean {
        if (busy || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        busy = true
        val token = ++generation
        fun finish(success: Boolean) {
            if (!busy || token != generation) return
            busy = false
            timeout?.let(handler::removeCallbacks)
            timeout = null
            if (!success) prompt.cancel()
            audio.abandonAudioFocusRequest(focus)
            onFinished(success)
        }
        timeout = Runnable { finish(false) }.also { handler.postDelayed(it, MAX_SPEECH_MS) }
        runCatching { prompt.explain(text) { finish(it) } }.onFailure { finish(false) }
        return true
    }

    fun cancel() {
        if (!busy) return
        generation++
        busy = false
        timeout?.let(handler::removeCallbacks)
        timeout = null
        prompt.cancel()
        audio.abandonAudioFocusRequest(focus)
    }
}
