package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Short, noninteractive voice output. The pure cycle enforces ducking and a six-second cap. */
internal class InformationalTripSpeaker(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val prompt = TripCallPrompt(context.applicationContext, AudioAttributes.USAGE_ASSISTANT)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).build()
    private val cycle = InformationalSpeechCycle(object : InformationalAudioPort {
        override fun requestDucking() = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        override fun speak(text: String, onFinished: (Boolean) -> Unit) = prompt.explain(text, onFinished)
        override fun stop() { prompt.cancel() }
        override fun releaseDucking() { audio.abandonAudioFocusRequest(focus) }
    }, InformationalSpeechTimer { delay, action ->
        val task = Runnable(action)
        handler.postDelayed(task, delay)
        ({ handler.removeCallbacks(task) })
    })
    val isSpeaking get() = cycle.isSpeaking
    fun speak(text: String, onFinished: (Boolean) -> Unit = {}) = cycle.speak(text, onFinished)
    fun cancel() = cycle.cancel()
}
