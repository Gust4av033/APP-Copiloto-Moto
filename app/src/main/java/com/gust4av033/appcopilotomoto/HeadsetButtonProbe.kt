package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import kotlin.math.sin

/** Prueba visible y acotada. No graba, no abre SCO y no ejecuta controles multimedia. */
class HeadsetButtonProbe(
    private val context: Context,
    private val onUpdate: (Boolean, List<String>, String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var tone: AudioTrack? = null
    private var startedAt = 0L
    private val lines = mutableListOf<String>()
    val active: Boolean get() = session != null
    private val timeout = Runnable { stop("Prueba terminada a los 60 segundos; controles multimedia liberados.") }
    private val releaseTone = Runnable { releaseTone() }

    fun start() {
        if (active) return
        lines.clear()
        startedAt = SystemClock.elapsedRealtime()
        val candidate = MediaSession(context, "APP: prueba de botones")
        try {
            candidate.setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    if (!active || mediaButtonIntent.action != Intent.ACTION_MEDIA_BUTTON) return false
                    val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    } ?: return false
                    recordKey("MediaSession", event)
                    return true // Sólo registrar; no pausar, llamar ni iniciar voz.
                }
            }, handler)
            // Android moderno prioriza sesiones que hayan reproducido audio localmente.
            // La sesión queda disponible tras un tono corto por la ruta musical normal.
            candidate.setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP)
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f).build())
            session = candidate
            candidate.isActive = true
            assistantGuardUntil = startedAt + 65_000L
            playProbeTone()
            handler.postDelayed(timeout, 60_000L)
            publish("Prueba activa: mantené APP visible y probá el botón pequeño del micrófono.")
        } catch (error: Exception) {
            releaseTone()
            candidate.release()
            session = null
            publish("No se pudo iniciar la prueba: ${error.message ?: error.javaClass.simpleName}.")
        }
    }

    fun recordActivityKey(event: KeyEvent): Boolean {
        if (!active || (event.keyCode !in observedKeys &&
                event.keyCode !in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16)) return false
        recordKey("Activity", event)
        // Los botones de volumen conservan su comportamiento normal.
        return event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
    }

    fun recordAssistant(source: String) {
        if (active) append("Asistente: $source (sin abrir micrófono)")
        else if (isAssistantGuarded()) {
            lines.add("Tras cerrar la prueba · Asistente: $source (sin abrir micrófono)")
            publish("Invocación de asistente registrada tras cerrar la prueba; micrófono cerrado.")
        }
    }

    fun stop(reason: String = "Prueba detenida; controles multimedia liberados.") {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(releaseTone)
        releaseTone()
        val previous = session ?: return
        session = null
        // Una invocación puede detener/recrear la Activity antes de entregar ASSIST.
        assistantGuardUntil = SystemClock.elapsedRealtime() + 5_000L
        previous.isActive = false
        previous.release()
        publish(reason)
    }

    private fun playProbeTone() {
        val samples = ShortArray(3_600) { index ->
            val fade = minOf(index / 360.0, (3_599 - index) / 360.0, 1.0)
            (sin(2 * Math.PI * 880 * index / 24_000) * 3_000 * fade).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(24_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
        tone = track
        check(track.write(samples, 0, samples.size) == samples.size) { "No se pudo preparar el tono de prueba" }
        track.play()
        handler.postDelayed(releaseTone, 500L)
        // No se solicita foco ni se pausa el reproductor; nunca se elige una ruta de comunicación.
    }

    private fun releaseTone() {
        val previous = tone ?: return
        tone = null
        runCatching { previous.stop() }
        previous.release()
    }

    private fun recordKey(source: String, event: KeyEvent) {
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            else -> event.action.toString()
        }
        append("$source · ${KeyEvent.keyCodeToString(event.keyCode)} · $action · " +
            "repeticiones=${event.repeatCount} · duración=${event.eventTime - event.downTime} ms · " +
            "device=${event.deviceId} · scan=${event.scanCode}")
    }

    private fun append(line: String) {
        lines.add("${SystemClock.elapsedRealtime() - startedAt} ms · $line")
        if (lines.size > 60) lines.removeAt(0)
        publish("Evento recibido. El registro no identifica por sí solo qué botón físico pulsaste.")
    }

    private fun publish(message: String) = onUpdate(active, lines.toList(), message)

    companion object {
        private var assistantGuardUntil = 0L
        fun isAssistantGuarded(): Boolean = SystemClock.elapsedRealtime() < assistantGuardUntil
        private val observedKeys = setOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_UNKNOWN,
            KeyEvent.KEYCODE_CALL, KeyEvent.KEYCODE_ENDCALL, KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)
    }
}
