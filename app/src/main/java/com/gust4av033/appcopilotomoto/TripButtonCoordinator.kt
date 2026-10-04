package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.SystemClock
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/** Captura opcional durante espera. Mantiene una salida PCM de ceros, sin foco ni micrófono. */
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
    private var anchor: AudioTrack? = null
    private val ownership = TripButtonOwnership()
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private var ownerListener: MediaSessionManager.OnMediaKeyEventSessionChangedListener? = null
    private var waiting = false
    private var outputSuspended = false
    private var lastStatus = ""
    private val verifyOwner = Runnable { verifyOwnership() }
    private val reclaimLater = Runnable { if (session != null && waiting && canActivate()) requestRecovery() }
    private val handler: (KeyEvent) -> Boolean = ::handle
    private val forwardedDown = mutableMapOf<Int, android.media.session.MediaController>()

    private fun reportStatus(message: String) {
        if (lastStatus == message) return
        lastStatus = message
        onStatus(message)
    }

    fun start() {
        if (session != null || binding == TripButtonBinding.NONE) return
        if (!TripMediaAccess.granted(context)) {
            reportStatus("Botón dedicado suspendido: falta acceso multimedia; usá el asistente largo o la burbuja.")
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
            setPlaybackState(candidate, false)
            session = candidate
            candidate.isActive = true
            TripButtonRuntime.handler = handler
            observeOwner()
        } catch (error: Exception) {
            candidate.release()
            session = null
            reportStatus("No se pudo capturar el botón: ${error.message}.")
        }
    }

    /** Conserva la prioridad mientras espera, también si ya había música al iniciar el viaje. */
    fun reclaim() {
        start()
        if (session == null || !canActivate()) return
        if (!TripMediaAccess.granted(context)) { close(); reportStatus("Acceso multimedia revocado; botón liberado."); return }
        waiting = true
        outputSuspended = false
        ownership.beginWaiting()
        holdSilentOutput()
        main.removeCallbacks(verifyOwner)
        main.postDelayed(verifyOwner, 350L)
    }

    private fun setPlaybackState(target: MediaSession, playing: Boolean) {
        target.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                0L, if (playing) 1f else 0f).build())
    }

    private fun holdSilentOutput() {
        val current = session ?: return
        releaseAnchor()
        runCatching {
            // Bucle estático de ceros: no escribe audio continuamente desde un hilo ni solicita foco.
            val samples = ShortArray(2_400)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
            anchor = track
            check(track.write(samples, 0, samples.size) == samples.size)
            check(track.setLoopPoints(0, samples.size, -1) == AudioTrack.SUCCESS)
            setPlaybackState(current, true)
            track.play()
        }.onFailure {
            releaseAnchor()
            setPlaybackState(current, false)
            reportStatus("No se pudo mantener el botón dedicado: ${it.message}. Usá el asistente largo o la burbuja.")
        }
    }

    private fun observeOwner() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val observedSession = session ?: return
        val listener = MediaSessionManager.OnMediaKeyEventSessionChangedListener { _, _ ->
            // Una notificación encolada de la sesión anterior no revive su salida de audio.
            if (session === observedSession && waiting) {
                main.removeCallbacks(verifyOwner)
                main.postDelayed(verifyOwner, 350L)
            }
        }
        runCatching {
            manager.addOnMediaKeyEventSessionChangedListener(context.mainExecutor, listener)
            ownerListener = listener
        }.onFailure { reportStatus("No se pudo observar el dueño del botón: ${it.message}.") }
    }

    private fun verifyOwnership() {
        val current = session ?: return
        if (!waiting) return
        if (!TripMediaAccess.granted(context)) { close(); reportStatus("Acceso multimedia revocado; botón liberado."); return }
        if (!canActivate()) {
            releaseAnchor()
            setPlaybackState(current, false)
            ownership.suspend()
            outputSuspended = true
            main.postDelayed(verifyOwner, 2_000L)
            return
        }
        if (outputSuspended) {
            outputSuspended = false
            ownership.beginWaiting()
            holdSilentOutput()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { manager.mediaKeyEventSession }.fold(onSuccess = { token ->
                val owned = token == current.sessionToken
                if (owned) reportStatus("Botón dedicado ${binding.name}: Android confirmó que APP recibe las teclas.")
                else {
                    if (ownership.shouldRecover(false, SystemClock.uptimeMillis())) {
                        reportStatus("Otro reproductor tiene el botón; recuperando ${binding.name}…")
                        holdSilentOutput()
                    } else reportStatus("Android aún entrega el botón a otra app; usá el asistente largo o la burbuja.")
                }
                if (owned) ownership.shouldRecover(true, SystemClock.uptimeMillis())
            }, onFailure = { reportStatus("No se pudo verificar el dueño del botón: ${it.message}.") })
        } else reportStatus("Botón dedicado ${binding.name}: salida mantenida; verificá recepción con el casco.")
        // Comprueba también una revocación del permiso sin callbacks del reproductor.
        main.removeCallbacks(verifyOwner)
        main.postDelayed(verifyOwner, 2_000L)
    }

    private fun requestRecovery() {
        // Reenviar PLAY/NEXT puede cambiar el destinatario. Verificarlo sin rearmar un bucle de reintentos.
        main.removeCallbacks(verifyOwner)
        main.postDelayed(verifyOwner, 350L)
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
                    .onFailure { reportStatus("Control no enviado al reproductor: ${it.message}.") }
                if (key.action == KeyEvent.ACTION_UP) {
                    main.removeCallbacks(reclaimLater)
                    main.postDelayed(reclaimLater, 500L)
                }
            }
        }
        return true
    }

    private fun releaseAnchor() {
        val previous = anchor ?: return
        anchor = null
        runCatching { previous.stop() }
        runCatching { previous.release() }
    }

    fun beforeCapture() {
        waiting = false
        outputSuspended = false
        ownership.suspend()
        main.removeCallbacks(reclaimLater)
        main.removeCallbacks(verifyOwner)
        releaseAnchor()
        session?.let { setPlaybackState(it, false) }
    }

    fun close() {
        beforeCapture()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ownerListener?.let { runCatching { manager.removeOnMediaKeyEventSessionChangedListener(it) } }
        }
        ownerListener = null
        forwardedDown.clear()
        if (TripButtonRuntime.handler === handler) TripButtonRuntime.handler = null
        val previous = session ?: return
        session = null
        previous.isActive = false
        previous.release()
    }
}
