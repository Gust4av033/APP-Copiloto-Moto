package com.gust4av033.appcopilotomoto

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors

/** Estado de diagnóstico; las escrituras se hacen en el hilo principal. */
object TripTestState {
    var running by mutableStateOf(false)
    var status by mutableStateOf("Iniciá la sesión de prueba con la app abierta.")
    var detections by mutableStateOf(0)
    var recognized by mutableStateOf(0)
    var actions by mutableStateOf(0)
    var failures by mutableStateOf(0)
    var lastWakeToSttMs by mutableStateOf(0L)
    var lastLatencyMs by mutableStateOf(0L)

    private const val PREFS = "fase_0_3_metricas"

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("session_active", false) && !running) {
            status = "La sesión anterior se interrumpió; iniciá una nueva prueba."
            prefs.edit().putBoolean("session_active", false).apply()
        }
        detections = prefs.getInt("detections", 0)
        recognized = prefs.getInt("recognized", 0)
        actions = prefs.getInt("actions", 0)
        failures = prefs.getInt("failures", 0)
        lastWakeToSttMs = prefs.getLong("ready_latency", 0L)
        lastLatencyMs = prefs.getLong("latency", 0L)
    }

    fun reset(context: Context) {
        detections = 0
        recognized = 0
        actions = 0
        failures = 0
        lastWakeToSttMs = 0L
        lastLatencyMs = 0L
        save(context)
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("detections", detections)
            .putInt("recognized", recognized)
            .putInt("actions", actions)
            .putInt("failures", failures)
            .putLong("ready_latency", lastWakeToSttMs)
            .putLong("latency", lastLatencyMs)
            .apply()
    }

    fun markRunning(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("session_active", active).apply()
    }
}

/** El servicio es dueño del micrófono y del ciclo de escucha cuando la pantalla se apaga. */
class TripVoiceService : Service() {
    companion object {
        const val ACTION_START = "com.gust4av033.appcopilotomoto.START_TRIP_TEST"
        const val ACTION_STOP = "com.gust4av033.appcopilotomoto.STOP_TRIP_TEST"
        const val EXTRA_PHRASE = "phrase"
        const val EXTRA_SENSITIVITY = "sensitivity"
        const val EXTRA_AUDIO_MODE = "audio_mode"
        private const val CHANNEL_ID = "trip_voice_test"
        private const val NOTIFICATION_ID = 103
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val speechLazy = lazy { AndroidSpeechToTextProvider(applicationContext) }
    private val speech by speechLazy
    private val media by lazy { MediaActionAdapter(applicationContext) }
    private var detector: WakeWordProvider? = null
    private var generation = 0
    private var pendingTask: Runnable? = null
    private var consecutiveFailures = 0
    private val recentDetections = ArrayDeque<Long>()
    private var active = false
    private var armed = false
    private var wakeAudioMode = WakeAudioMode.BLUETOOTH

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSession("Sesión detenida desde la app o la notificación.")
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (active) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            TripTestState.status = "Falta permiso de micrófono para iniciar la sesión."
            stopSelf(startId)
            return START_NOT_STICKY
        }
        createChannel()
        try {
            val notice = buildNotification("Preparando micrófono Bluetooth…")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else startForeground(NOTIFICATION_ID, notice)
        } catch (e: Exception) {
            TripTestState.status = "No se pudo iniciar la sesión: ${e.message ?: e.javaClass.simpleName}"
            stopSelf(startId)
            return START_NOT_STICKY
        }
        active = true
        TripTestState.running = true
        TripTestState.markRunning(this, true)
        TripTestState.reset(this)
        TripTestState.status = "Preparando detector; mantené el casco conectado…"

        val phrase = runCatching {
            WakePhrase.valueOf(intent.getStringExtra(EXTRA_PHRASE) ?: WakePhrase.ALEXA.name)
        }.getOrDefault(WakePhrase.ALEXA)
        val sensitivity = intent.getFloatExtra(EXTRA_SENSITIVITY, 80f).coerceIn(40f, 85f)
        wakeAudioMode = runCatching {
            WakeAudioMode.valueOf(intent.getStringExtra(EXTRA_AUDIO_MODE) ?: WakeAudioMode.BLUETOOTH.name)
        }.getOrDefault(WakeAudioMode.BLUETOOTH)
        val provider = runCatching {
            OpenWakeWordProvider(applicationContext, (100f - sensitivity) / 100f, phrase, wakeAudioMode)
        }.getOrElse {
            stopSession("No se pudo cargar el detector: ${it.message}")
            return START_NOT_STICKY
        }
        detector = provider
        startDetector(provider)
        return START_NOT_STICKY // No reiniciar el micrófono sin una nueva acción visible.
    }

    private fun startDetector(provider: WakeWordProvider) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !getSystemService(AudioManager::class.java).availableCommunicationDevices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }) {
            stopSession("No se detecta el micrófono Bluetooth del casco; sesión detenida.")
            return
        }
        armed = false
        val cycle = ++generation
        TripTestState.status = "Preparando escucha de ‘${provider.phrase}’…"
        worker.execute {
            val result = provider.start { score ->
                mainHandler.post { onDetected(provider, cycle, score) }
            }
            mainHandler.post {
                if (!active || detector !== provider || cycle != generation) return@post
                if (result.isFailure) {
                    stopSession("Falló la escucha: ${result.exceptionOrNull()?.message}")
                } else {
                    TripTestState.status = "Estabilizando audio; esperá un momento…"
                    schedule(1_500L) {
                        armDetector(provider, cycle, 0)
                    }
                }
            }
        }
    }

    private fun armDetector(provider: WakeWordProvider, cycle: Int, retry: Int) {
        if (!active || detector !== provider || cycle != generation) return
        val inputCheck = runCatching { (provider as OpenWakeWordProvider).wakeInputStatus() }
        if (inputCheck.isFailure) {
            val reason = inputCheck.exceptionOrNull()?.message.orEmpty()
            if (reason.startsWith("No se pudo verificar") && retry < 4) {
                schedule(1_000L) { armDetector(provider, cycle, retry + 1) }
            } else stopSession("Modo experimental: $reason")
            return
        }
        armed = true
        TripTestState.status = "Esperando ‘${provider.phrase}’ con pantalla apagada… ${inputCheck.getOrNull().orEmpty()}"
        updateNotification("Esperando ‘${provider.phrase}’")
    }

    private fun onDetected(provider: WakeWordProvider, cycle: Int, score: Float) {
        if (!active || !armed || detector !== provider || cycle != generation) return
        val now = SystemClock.elapsedRealtime()
        while (recentDetections.isNotEmpty() && now - recentDetections.first() > 12_000L) {
            recentDetections.removeFirst()
        }
        if (recentDetections.size >= 3) {
            stopSession("Varias activaciones seguidas; sesión detenida para evitar un bucle.")
            return
        }
        recentDetections.addLast(now)
        armed = false
        generation++ // Descarta callbacks anteriores al traspaso del micrófono.
        TripTestState.detections++
        TripTestState.save(this)
        TripTestState.status = "Activación #${TripTestState.detections} (${(score * 100).toInt()} %). Preparando orden…"
        updateNotification("Preparando orden de voz")
        worker.execute {
            val result = runCatching { provider.pause() }
            mainHandler.post {
                if (!active || detector !== provider) return@post
                if (result.isFailure) {
                    stopSession("No se pudo liberar el micrófono: ${result.exceptionOrNull()?.message}")
                } else {
                    val handoff = generation
                    schedule(350L) {
                        if (active && detector === provider && handoff == generation) {
                            // El sonido termina ANTES de abrir SpeechRecognizer para evitar autoactivación.
                            playHandoffTone()
                            schedule(400L) {
                                if (active && detector === provider && handoff == generation) {
                                    listenForCommand(provider, now)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun playHandoffTone() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 65)
            if (tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100)) {
                mainHandler.postDelayed({ tone.release() }, 200L)
            } else tone.release()
        }
    }

    private fun listenForCommand(provider: WakeWordProvider, wakeAt: Long) {
        TripTestState.status = "Abriendo reconocedor; decí la orden después del tono…"
        updateNotification("Escuchando orden")
        val cycle = generation
        speech.listenOnce(onState = { state ->
            if (active && cycle == generation && state == SpeechCaptureState.READY) {
                TripTestState.lastWakeToSttMs = SystemClock.elapsedRealtime() - wakeAt
                TripTestState.save(this)
                TripTestState.status = "Reconocedor listo; decí la orden de música o volumen."
            }
        }, shouldResumePlayback = { heard ->
            heard?.let(MinimalIntentRouter::resolve) != TestIntent.MEDIA_PAUSE
        }) { outcome ->
            if (!active || cycle != generation) return@listenOnce
            val heard = outcome.getOrNull()
            val intent = heard?.let(MinimalIntentRouter::resolve) ?: TestIntent.UNKNOWN
            val mediaAction = when (intent) {
                TestIntent.MEDIA_NEXT -> media.nextTrack()
                TestIntent.MEDIA_PREVIOUS -> media.previousTrack()
                TestIntent.MEDIA_PLAY -> media.play()
                TestIntent.MEDIA_PAUSE -> media.pause()
                TestIntent.VOLUME_UP -> media.volumeUp()
                TestIntent.VOLUME_DOWN -> media.volumeDown()
                else -> null
            }
            val elapsed = SystemClock.elapsedRealtime() - wakeAt
            TripTestState.lastLatencyMs = elapsed
            if (heard != null) TripTestState.recognized++
            if (mediaAction?.isSuccess == true) TripTestState.actions++
            if (outcome.isFailure || intent == TestIntent.UNKNOWN || mediaAction?.isFailure == true) {
                TripTestState.failures++
                consecutiveFailures++
            } else consecutiveFailures = 0
            TripTestState.save(this)
            if (intent == TestIntent.STOP_SESSION) {
                stopSession("Sesión detenida por voz: ‘$heard’ en $elapsed ms.")
                return@listenOnce
            }
            TripTestState.status = when {
                outcome.isFailure -> "No se entendió la orden: ${outcome.exceptionOrNull()?.message}."
                mediaAction != null -> {
                    val command = when (intent) {
                        TestIntent.MEDIA_NEXT -> "Siguiente canción"
                        TestIntent.MEDIA_PREVIOUS -> "Anterior canción"
                        TestIntent.MEDIA_PLAY -> "Reproducir"
                        TestIntent.MEDIA_PAUSE -> "Pausar"
                        TestIntent.VOLUME_UP -> "Subir volumen (${media.musicVolume()})"
                        TestIntent.VOLUME_DOWN -> "Bajar volumen (${media.musicVolume()})"
                        else -> "Orden"
                    }
                    if (mediaAction.isSuccess) "Escuché ‘$heard’; envié $command en $elapsed ms."
                    else "Escuché ‘$heard’, pero falló el envío de $command."
                }
                intent == TestIntent.CANCEL -> "Escuché ‘$heard’; orden cancelada."
                intent == TestIntent.STOP_TTS -> "Escuché ‘$heard’; Silencio reconocido (sin TTS activo)."
                else -> "Escuché ‘$heard’; no coincide con las órdenes de prueba."
            }
            updateNotification("Orden terminada; esperando activación")
            if (consecutiveFailures >= 2) {
                stopSession("Dos activaciones sin orden válida; sesión detenida. ${TripTestState.status}")
            } else {
                val delay = if (intent != TestIntent.UNKNOWN && outcome.isSuccess) 2_000L else 3_500L
                schedule(delay) { if (active && detector === provider && cycle == generation) startDetector(provider) }
            }
        }
    }

    private fun schedule(delayMs: Long, action: () -> Unit) {
        pendingTask?.let(mainHandler::removeCallbacks)
        val task = Runnable { pendingTask = null; action() }
        pendingTask = task
        mainHandler.postDelayed(task, delayMs)
    }

    private fun stopSession(reason: String) {
        active = false
        armed = false
        generation++
        pendingTask?.let(mainHandler::removeCallbacks)
        pendingTask = null
        if (speechLazy.isInitialized()) speech.cancel()
        val previous = detector
        detector = null
        if (previous != null) worker.execute { runCatching { previous.close() } }
        TripTestState.running = false
        TripTestState.markRunning(this, false)
        TripTestState.status = reason
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Sesión de voz de prueba", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(detail: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, TripVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("APP · prueba de voz activa")
            .setContentText(detail)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stop)
            .build()
    }

    private fun updateNotification(detail: String) {
        if (active) runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(detail))
        }
    }

    override fun onDestroy() {
        if (active) stopSession("Sesión interrumpida por Android; iniciá una nueva prueba.")
        worker.shutdown()
        super.onDestroy()
    }
}
