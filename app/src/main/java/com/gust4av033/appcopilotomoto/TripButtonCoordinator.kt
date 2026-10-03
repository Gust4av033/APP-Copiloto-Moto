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
import android.view.KeyEvent

/** Captura opcional, sin micrófono ni SCO. Android puede enrutar botones a otra sesión. */
internal class TripButtonCoordinator(
    private val context: Context,
    private val binding: TripButtonBinding,
    private val canActivate: () -> Boolean,
    private val onActivate: () -> Unit,
    private val onStatus: (String) -> Unit,
    private val onReceived: (Boolean) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val policy = TripButtonPolicy()
    private var session: MediaSession? = null
    private var pulse: AudioTrack? = null
    private val releasePulse = Runnable { releasePulse() }
    private val reclaimLater = Runnable { if (session != null && canActivate()) reclaim() }
    private val handler: (KeyEvent) -> Boolean = ::handle
    private val forwardedDown = mutableMapOf<Int, android.media.session.MediaController>()

    fun start() {
        if (session != null || binding == TripButtonBinding.NONE) return
        if (!TripMediaAccess.granted(context)) {
            onStatus("Botón dedicado suspendido: falta acceso multimedia; usá el asistente largo o la burbuja.")
            return
        }
        val candidate = MediaSession(context, "APP: botón de viaje")
        try {
            candidate.setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    if (mediaButtonIntent.action != Intent.ACTION_MEDIA_BUTTON) return false
                    val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    } ?: return false
                    return handle(key)
                }
            }, main)
            // No permitir que un botón reviva una sesión terminada.
            @Suppress("DEPRECATION")
            candidate.setMediaButtonReceiver(null)
            candidate.setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP)
                .setState(PlaybackState.STATE_PAUSED, 0L, 0f).build())
            session = candidate
            candidate.isActive = true
            TripButtonRuntime.handler = handler
        } catch (error: Exception) {
            candidate.release()
            session = null
            onStatus("No se pudo capturar el botón: ${error.message}.")
        }
    }

    /** Pulso musical silencioso de 100 ms, solo al volver a espera. Sin reproducción continua. */
    fun reclaim() {
        start()
        if (session == null) return
        if (!TripMediaAccess.granted(context)) { close(); onStatus("Acceso multimedia revocado; botón liberado."); return }
        main.removeCallbacks(releasePulse)
        releasePulse()
        runCatching {
            val samples = ShortArray(2_400)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
            pulse = track
            check(track.write(samples, 0, samples.size) == samples.size)
            track.play()
            main.postDelayed(releasePulse, 300L)
            onStatus("Botón dedicado ${binding.name}: captura solicitada; confirmá recepción con el casco.")
        }.onFailure { releasePulse(); onStatus("Captura sin pulso multimedia: ${it.message}.") }
    }

    private fun handle(key: KeyEvent): Boolean {
        if (session == null) return false
        if (key.keyCode !in setOf(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND)) return false
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        if (audio.mode == android.media.AudioManager.MODE_IN_CALL || audio.mode == android.media.AudioManager.MODE_RINGTONE) {
            close()
            return false
        }
        if (!TripMediaAccess.granted(context)) { close(); return false }
        val decision = policy.decide(binding, true, canActivate(), key.keyCode,
            key.action == KeyEvent.ACTION_DOWN, key.repeatCount, key.downTime, key.deviceId)
        if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) onReceived(decision == ButtonDecision.ACTIVATE)
        when (decision) {
            ButtonDecision.ACTIVATE -> onActivate()
            ButtonDecision.CONSUME -> Unit
            ButtonDecision.PASS -> {
                runCatching {
                    val target = if (key.action == KeyEvent.ACTION_DOWN) {
                        forwardedDown[key.keyCode] ?: TripMediaAccess.controller(context).also { forwardedDown[key.keyCode] = it }
                    } else forwardedDown.remove(key.keyCode) ?: TripMediaAccess.controller(context)
                    check(target.dispatchMediaButtonEvent(key)) { "El reproductor rechazó el control multimedia." }
                }
                    .onFailure { onStatus("Control no enviado al reproductor: ${it.message}.") }
                if (key.action == KeyEvent.ACTION_UP) {
                    main.removeCallbacks(reclaimLater)
                    main.postDelayed(reclaimLater, 500L)
                }
            }
        }
        return true
    }

    private fun releasePulse() {
        val previous = pulse ?: return
        pulse = null
        runCatching { previous.stop() }
        previous.release()
    }

    fun beforeCapture() {
        main.removeCallbacks(reclaimLater)
        main.removeCallbacks(releasePulse)
        releasePulse()
    }

    fun close() {
        beforeCapture()
        forwardedDown.clear()
        if (TripButtonRuntime.handler === handler) TripButtonRuntime.handler = null
        val previous = session ?: return
        session = null
        previous.isActive = false
        previous.release()
    }
}
