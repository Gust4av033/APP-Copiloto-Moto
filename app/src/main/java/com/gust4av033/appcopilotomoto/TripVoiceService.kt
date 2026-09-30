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
import android.media.AudioDeviceCallback
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.telecom.TelecomManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors

/** Estado de diagnóstico; las escrituras se hacen en el hilo principal. */
object TripTestState {
    var running by mutableStateOf(false)
    var restoringAudio by mutableStateOf(false)
    var status by mutableStateOf("Iniciá la sesión de prueba con la app abierta.")
    var lastCommandResult by mutableStateOf("Aún no hay órdenes en esta sesión.")
    var detections by mutableStateOf(0)
    var manualCommands by mutableStateOf(0)
    var recognized by mutableStateOf(0)
    var actions by mutableStateOf(0)
    var failures by mutableStateOf(0)
    var lastWakeToSttMs by mutableStateOf(0L)
    var lastLatencyMs by mutableStateOf(0L)
    var audioBefore by mutableStateOf("Sin lectura previa.")
    var audioDuring by mutableStateOf("Iniciá una sesión para medir la ruta activa.")
    var audioAfter by mutableStateOf("Detené la sesión para medir la ruta restaurada.")
    var audioCaptureGeneration = 0L

    private const val PREFS = "fase_0_3_metricas"

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("session_active", false) && !running) {
            status = "La sesión anterior se interrumpió; iniciá una nueva prueba."
            prefs.edit().putBoolean("session_active", false).apply()
        }
        detections = prefs.getInt("detections", 0)
        manualCommands = prefs.getInt("manual_commands", 0)
        recognized = prefs.getInt("recognized", 0)
        actions = prefs.getInt("actions", 0)
        failures = prefs.getInt("failures", 0)
        lastWakeToSttMs = prefs.getLong("ready_latency", 0L)
        lastLatencyMs = prefs.getLong("latency", 0L)
    }

    fun reset(context: Context) {
        lastCommandResult = "Aún no hay órdenes en esta sesión."
        detections = 0
        manualCommands = 0
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
            .putInt("manual_commands", manualCommands)
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
        const val ACTION_MANUAL_COMMAND = "com.gust4av033.appcopilotomoto.MANUAL_TRIP_COMMAND"
        const val ACTION_SILENCE = "com.gust4av033.appcopilotomoto.SILENCE_TRIP_SPEECH"
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
    private val callPromptLazy = lazy { TripCallPrompt(applicationContext) }
    private val callPrompt by callPromptLazy
    private val media by lazy { MediaActionAdapter(applicationContext) }
    private val actions by lazy { ActionExecutor(this, media) }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private var deviceCallback: AudioDeviceCallback? = null
    private var communicationListener: AudioManager.OnCommunicationDeviceChangedListener? = null
    private var routeCheck: Runnable? = null
    private var detector: WakeWordProvider? = null
    private var generation = 0
    private var pendingTask: Runnable? = null
    private var consecutiveFailures = 0
    private val recentDetections = ArrayDeque<Long>()
    private var active = false
    private var closing = false
    private var callPending = false
    private var armed = false
    private var wakeAudioMode = WakeAudioMode.BLUETOOTH
    private data class PendingVolumeCommand(
        val command: RoutedCommand,
        val heard: String,
        val source: String,
        val wakeAt: Long
    )
    private var pendingVolumeCommand: PendingVolumeCommand? = null
    private var pendingApp: AppTarget? = null
    private var resumeAfterButtonRoute = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SILENCE) {
            val silenced = callPromptLazy.isInitialized() && callPrompt.cancel()
            if (active && silenced) {
                pendingTask?.let(mainHandler::removeCallbacks)
                pendingTask = null
                TripTestState.status = "Respuesta hablada detenida; llamada cancelada."
                TripTestState.lastCommandResult = TripTestState.status
                updateNotification("Llamada cancelada; esperando activación")
                detector?.let { provider ->
                    schedule(300L) { if (active && detector === provider) startDetector(provider) }
                }
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSession("Sesión detenida desde la app o la notificación.")
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_MANUAL_COMMAND) {
            val provider = detector
            if (active && armed && provider != null) {
                startManualCommand(provider)
            } else if (active) {
                TripTestState.status = "La sesión está procesando otra orden; esperá antes de pulsar de nuevo."
            } else if (!callPending) {
                stopSelf(startId)
            }
            // Una pulsación sin sesión activa nunca inicia un micrófono en segundo plano.
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (active || callPending || closing) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            TripTestState.status = "Falta permiso de micrófono para iniciar la sesión."
            stopSelf(startId)
            return START_NOT_STICKY
        }
        wakeAudioMode = runCatching {
            WakeAudioMode.valueOf(intent.getStringExtra(EXTRA_AUDIO_MODE) ?: WakeAudioMode.BLUETOOTH.name)
        }.getOrDefault(WakeAudioMode.BLUETOOTH)
        pendingApp = null
        createChannel()
        try {
            val notice = buildNotification(if (wakeAudioMode == WakeAudioMode.BUTTON) {
                "Preparando modo botón · música normal"
            } else "Preparando micrófono Bluetooth…")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else startForeground(NOTIFICATION_ID, notice)
        } catch (e: Exception) {
            TripTestState.status = "No se pudo iniciar la sesión: ${e.message ?: e.javaClass.simpleName}"
            stopSelf(startId)
            return START_NOT_STICKY
        }
        active = true
        val hardwareMonitor = runCatching { monitorHardware() }
        if (hardwareMonitor.isFailure) {
            stopSession("No se pudo observar el casco Bluetooth: ${hardwareMonitor.exceptionOrNull()?.message}.")
            return START_NOT_STICKY
        }
        TripTestState.running = true
        TripTestState.markRunning(this, true)
        TripTestState.reset(this)
        TripTestState.audioCaptureGeneration++
        TripTestState.audioBefore = media.audioRouteDiagnostic()
        TripTestState.audioDuring = "Preparando la ruta Bluetooth…"
        TripTestState.audioAfter = "Sesión activa; falta detenerla."
        TripTestState.status = "Preparando detector; mantené el casco conectado…"

        val phrase = runCatching {
            WakePhrase.valueOf(intent.getStringExtra(EXTRA_PHRASE) ?: WakePhrase.ALEXA.name)
        }.getOrDefault(WakePhrase.ALEXA)
        val sensitivity = intent.getFloatExtra(EXTRA_SENSITIVITY, 80f).coerceIn(40f, 85f)
        val provider: WakeWordProvider = runCatching {
            if (wakeAudioMode == WakeAudioMode.BUTTON) ButtonWakeWordProvider(applicationContext)
            else OpenWakeWordProvider(applicationContext, (100f - sensitivity) / 100f, phrase, wakeAudioMode)
        }.getOrElse {
            stopSession("No se pudo preparar la activación: ${it.message}")
            return START_NOT_STICKY
        }
        detector = provider
        startDetector(provider)
        return START_NOT_STICKY // No reiniciar el micrófono sin una nueva acción visible.
    }

    private fun startDetector(provider: WakeWordProvider) {
        if (wakeAudioMode != WakeAudioMode.BUTTON && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !getSystemService(AudioManager::class.java).availableCommunicationDevices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }) {
            stopSession("No se detecta el micrófono Bluetooth del casco; sesión detenida.")
            return
        }
        armed = false
        val cycle = ++generation
        TripTestState.status = if (wakeAudioMode == WakeAudioMode.BUTTON) {
            "Restaurando música; esperando el botón del casco…"
        } else "Preparando escucha de ‘${provider.phrase}’…"
        worker.execute {
            val result = provider.start { score ->
                mainHandler.post { onDetected(provider, cycle, score) }
            }
            mainHandler.post {
                if (!active || detector !== provider || cycle != generation) return@post
                if (result.isFailure) {
                    stopSession("Falló la escucha: ${result.exceptionOrNull()?.message}")
                } else {
                    TripTestState.status = if (wakeAudioMode == WakeAudioMode.BUTTON) {
                        "Comprobando que la música volvió a su salida normal…"
                    } else "Estabilizando audio; esperá un momento…"
                    schedule(if (wakeAudioMode == WakeAudioMode.BUTTON) 500L else 1_500L) {
                        armDetector(provider, cycle, 0)
                    }
                }
            }
        }
    }

    private fun armDetector(provider: WakeWordProvider, cycle: Int, retry: Int) {
        if (!active || detector !== provider || cycle != generation) return
        if (wakeAudioMode == WakeAudioMode.BUTTON) {
            if (!media.isMediaRouteReady()) {
                if (retry < 12) schedule(500L) { armDetector(provider, cycle, retry + 1) }
                else stopSession("Android no devolvió la música a su salida normal tras la orden.")
                return
            }
            finishPendingVolume(bluetoothVoice = false)
            val pausedBySpeech = if (speechLazy.isInitialized()) speech.cancel(resumePlayback = false) else false
            val resume = pausedBySpeech || resumeAfterButtonRoute
            resumeAfterButtonRoute = false
            if (resume) runCatching { media.resumePlayback() }
            armed = true
            TripTestState.audioDuring = media.audioRouteDiagnostic()
            TripTestState.status = "Esperando el botón del casco. Música por la salida normal; micrófono cerrado."
            updateNotification("Esperando botón · música normal")
            return
        }
        val inputCheck = runCatching { (provider as OpenWakeWordProvider).wakeInputStatus() }
        if (inputCheck.isFailure) {
            val reason = inputCheck.exceptionOrNull()?.message.orEmpty()
            if ((reason.startsWith("No se pudo verificar") || reason.contains("usa otro micrófono")) && retry < 4) {
                schedule(1_000L) { armDetector(provider, cycle, retry + 1) }
            } else stopSession("${if (wakeAudioMode == WakeAudioMode.PHONE_WAKE) "Modo experimental" else "Micrófono del casco"}: $reason")
            return
        }
        if (wakeAudioMode == WakeAudioMode.BLUETOOTH && !headsetRouteAvailable()) {
            stopSession("El casco de voz cambió o se desconectó. Elegí el casco de música y reiniciá la sesión.")
            return
        }
        finishPendingVolume(bluetoothVoice = wakeAudioMode == WakeAudioMode.BLUETOOTH)
        armed = true
        TripTestState.audioDuring = media.audioRouteDiagnostic()
        TripTestState.status = "Esperando ‘${provider.phrase}’ con pantalla apagada… ${inputCheck.getOrNull().orEmpty()}"
        updateNotification("Esperando ‘${provider.phrase}’")
    }

    private fun finishPendingVolume(bluetoothVoice: Boolean) {
        val pending = pendingVolumeCommand ?: return
        pendingVolumeCommand = null
        val action = actions.executeRestoredVolume(pending.command, bluetoothVoice) ?: return
        val change = action.getOrNull()
        val atLimit = change != null && when (pending.command.intent) {
            TestIntent.VOLUME_UP -> change.before >= change.maximum
            TestIntent.VOLUME_DOWN -> change.before <= audioManager.getStreamMinVolume(change.stream)
            TestIntent.VOLUME_SET -> change.after == change.before && !change.fixed
            else -> false
        }
        if (change != null && (change.after != change.before || atLimit)) TripTestState.actions++
        else TripTestState.failures++
        val elapsed = SystemClock.elapsedRealtime() - pending.wakeAt
        TripTestState.lastLatencyMs = elapsed
        TripTestState.save(this)
        val command = when (pending.command.intent) {
            TestIntent.VOLUME_UP -> "Subir volumen"
            TestIntent.VOLUME_DOWN -> "Bajar volumen"
            else -> "Fijar volumen"
        }
        TripTestState.lastCommandResult = if (action.isSuccess) {
            "${pending.source}: Escuché ‘${pending.heard}’; $command (${media.commandVolumeStatus(change)}) en $elapsed ms."
        } else {
            "${pending.source}: Escuché ‘${pending.heard}’, pero falló $command: ${action.exceptionOrNull()?.message ?: "error de Android"}."
        }
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
                                    listenForCommand(provider, now, provider.phrase)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Usa la escucha y las confirmaciones de la sesión existente sin contar una detección de wake word. */
    private fun startManualCommand(provider: WakeWordProvider) {
        armed = false
        val cycle = ++generation // Invalida detecciones atrasadas de Alexa.
        val startedAt = SystemClock.elapsedRealtime()
        TripTestState.manualCommands++
        TripTestState.save(this)
        TripTestState.status = "Botón del casco recibido; preparando orden…"
        updateNotification("Preparando orden del botón")
        worker.execute {
            val paused = runCatching { provider.pause() }
            mainHandler.post {
                if (!active || detector !== provider || cycle != generation) return@post
                if (paused.isFailure) {
                    if (wakeAudioMode == WakeAudioMode.BUTTON) {
                        recoverButtonCommand(provider, cycle,
                            "No se pudo abrir el casco Bluetooth: ${paused.exceptionOrNull()?.message}")
                    } else stopSession("No se pudo liberar el micrófono: ${paused.exceptionOrNull()?.message}")
                } else {
                    awaitManualRoute(provider, cycle, startedAt, 0)
                }
            }
        }
    }

    /** La elección de SCO puede tardar después de setCommunicationDevice(), sobre todo con pantalla apagada. */
    private fun awaitManualRoute(provider: WakeWordProvider, cycle: Int, startedAt: Long, attempt: Int) {
        if (!active || detector !== provider || cycle != generation) return
        if (wakeAudioMode == WakeAudioMode.BUTTON && !headsetRouteAvailable()) {
            if (attempt < 12) {
                schedule(250L) { awaitManualRoute(provider, cycle, startedAt, attempt + 1) }
            } else {
                recoverButtonCommand(provider, cycle,
                    "Android no confirmó el micrófono Bluetooth; se canceló esta orden.")
            }
            return
        }
        schedule(350L) {
            if (active && detector === provider && cycle == generation) {
                playHandoffTone()
                schedule(400L) {
                    if (active && detector === provider && cycle == generation) {
                        listenForCommand(provider, startedAt, "Casco")
                    }
                }
            }
        }
    }

    /** Una orden fallida no termina el viaje; suelta SCO antes de aceptar otra pulsación. */
    private fun recoverButtonCommand(provider: WakeWordProvider, cycle: Int, reason: String) {
        if (!active || detector !== provider || cycle != generation) return
        TripTestState.failures++
        TripTestState.save(this)
        if (speechLazy.isInitialized()) {
            resumeAfterButtonRoute = speech.cancel(resumePlayback = false) || resumeAfterButtonRoute
        }
        TripTestState.status = "$reason Restaurando música; probá de nuevo cuando indique que está listo."
        TripTestState.lastCommandResult = TripTestState.status
        updateNotification("Orden cancelada · restaurando música")
        schedule(100L) {
            if (active && detector === provider && cycle == generation) startDetector(provider)
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

    private fun listenForCommand(provider: WakeWordProvider, wakeAt: Long, source: String) {
        TripTestState.status = "$source: abriendo reconocedor; decí la orden cuando esté listo…"
        updateNotification("$source · abriendo reconocedor")
        val cycle = generation
        speech.listenOnce(onState = { state ->
            if (active && cycle == generation && state == SpeechCaptureState.READY) {
                val input = speech.activeInputDescription()
                if (wakeAudioMode == WakeAudioMode.BUTTON &&
                    input.contains("micrófono del teléfono") && !input.contains("Bluetooth")) {
                    TripTestState.failures++
                    TripTestState.save(this)
                    resumeAfterButtonRoute = speech.cancel(resumePlayback = false)
                    TripTestState.status = "Android abrió el micrófono del teléfono; orden cancelada. Restaurando música."
                    TripTestState.lastCommandResult = TripTestState.status
                    schedule(100L) { if (active && detector === provider && cycle == generation) startDetector(provider) }
                } else {
                    TripTestState.lastWakeToSttMs = SystemClock.elapsedRealtime() - wakeAt
                    TripTestState.save(this)
                    TripTestState.status = "$source: reconocedor listo; entrada: $input. Decí música, volumen, ‘abre [aplicación]’ o ‘llama a [nombre]’."
                    updateNotification("$source · reconocedor listo; decí la orden")
                }
            }
        }, playbackResumePolicy = { heard ->
            when (heard?.let(MinimalIntentRouter::resolve)) {
                TestIntent.MEDIA_PAUSE -> PlaybackResumePolicy.KEEP_PAUSED
                TestIntent.STOP_SESSION -> PlaybackResumePolicy.AFTER_ROUTE_RESTORED
                else -> if (wakeAudioMode == WakeAudioMode.BUTTON) {
                    PlaybackResumePolicy.AFTER_ROUTE_RESTORED
                } else PlaybackResumePolicy.NOW
            }
        }) { outcome ->
            if (!active || cycle != generation) return@listenOnce
            val heard = outcome.getOrNull()
            val command = heard?.let(MinimalIntentRouter::route) ?: RoutedCommand(TestIntent.UNKNOWN)
            val intent = command.intent
            val cancelledApp = intent == TestIntent.CANCEL && pendingApp != null
            // Una orden nueva reemplaza cualquier apertura que aún estaba pendiente.
            if (intent != TestIntent.OPEN_APP) pendingApp = null
            if (intent == TestIntent.VOLUME_UP || intent == TestIntent.VOLUME_DOWN ||
                intent == TestIntent.VOLUME_SET) {
                TripTestState.recognized++
                TripTestState.save(this)
                pendingVolumeCommand = PendingVolumeCommand(command, heard.orEmpty(), source, wakeAt)
                TripTestState.status = "$source: Escuché ‘$heard’; restaurando la ruta para ajustar volumen…"
                TripTestState.lastCommandResult = TripTestState.status
                updateNotification("$source · restaurando ruta de voz")
                schedule(if (wakeAudioMode == WakeAudioMode.BUTTON) 100L else 350L) {
                    if (active && detector === provider && cycle == generation) startDetector(provider)
                }
                return@listenOnce
            }
            val mediaAction = actions.executeMedia(command)
            val elapsed = SystemClock.elapsedRealtime() - wakeAt
            TripTestState.lastLatencyMs = elapsed
            if (heard != null) TripTestState.recognized++
            val volumeChange = mediaAction?.getOrNull() as? VolumeAdjustment
            if (mediaAction?.isSuccess == true && (volumeChange == null || volumeChange.after != volumeChange.before)) TripTestState.actions++
            if (outcome.isFailure || intent == TestIntent.UNKNOWN || mediaAction?.isFailure == true) {
                TripTestState.failures++
                consecutiveFailures++
            } else consecutiveFailures = 0
            TripTestState.save(this)
            if (intent == TestIntent.STOP_SESSION) {
                stopSession("Sesión detenida por voz: ‘$heard’ en $elapsed ms.")
                return@listenOnce
            }
            if (intent == TestIntent.OPEN_APP) {
                val found = actions.prepareApp(command)
                pendingApp = found.getOrNull()
                TripTestState.status = if (pendingApp != null) {
                    "$source: Encontré ${pendingApp?.label}. Tocá ‘Abrir aplicación’ en la notificación para abrirla."
                } else {
                    TripTestState.failures++
                    "$source: ${found.exceptionOrNull()?.message ?: "No se encontró la aplicación."}"
                }
                TripTestState.lastCommandResult = TripTestState.status
                TripTestState.save(this)
                updateNotification(if (pendingApp != null) "Aplicación lista: ${pendingApp?.label}" else "Aplicación no encontrada")
                schedule(if (wakeAudioMode == WakeAudioMode.BUTTON) 100L else 2_000L) {
                    if (active && detector === provider && cycle == generation) startDetector(provider)
                }
                return@listenOnce
            }
            if (intent == TestIntent.CALL_SELECTED) {
                val contact = TripCallContactStore.load(this)
                if (contact == null || checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    TripTestState.failures++
                    TripTestState.save(this)
                    TripTestState.status = "Para llamar, elegí un contacto y concedé el permiso de teléfono desde APP antes del viaje."
                    updateNotification("Llamada sin configurar")
                    schedule(if (wakeAudioMode == WakeAudioMode.BUTTON) 100L else 3_500L) {
                        if (active && detector === provider && cycle == generation) startDetector(provider)
                    }
                } else requestCallConfirmation(provider, cycle, contact)
                return@listenOnce
            }
            if (intent == TestIntent.CALL_NAMED) {
                val name = command.contactName ?: return@listenOnce
                if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    TripTestState.failures++
                    TripTestState.save(this)
                    TripTestState.status = "Habilitá llamadas por nombre desde APP: requiere contactos y teléfono."
                    updateNotification("Llamada por nombre sin permisos")
                    schedule(3_500L) { if (active && detector === provider && cycle == generation) startDetector(provider) }
                } else {
                    TripTestState.status = "Buscando ‘$name’ en contactos…"
                    worker.execute {
                        val found = VoiceContactResolver(this).findUnique(name)
                        mainHandler.post {
                            if (!active || detector !== provider || generation != cycle) return@post
                            if (found.isSuccess) {
                                requestCallConfirmation(provider, cycle, found.getOrThrow())
                            } else {
                                TripTestState.failures++
                                TripTestState.save(this)
                                TripTestState.status = found.exceptionOrNull()?.message ?: "No se encontró el contacto."
                                updateNotification("No se pudo resolver el contacto")
                                schedule(3_500L) { if (active && detector === provider && cycle == generation) startDetector(provider) }
                            }
                        }
                    }
                }
                return@listenOnce
            }
            TripTestState.status = when {
                outcome.isFailure -> "No se entendió la orden: ${outcome.exceptionOrNull()?.message}."
                mediaAction != null -> {
                    val label = actions.description(intent, mediaAction.getOrNull() as? VolumeAdjustment)
                    if (mediaAction.isSuccess) "Escuché ‘$heard’; $label en $elapsed ms."
                    else "Escuché ‘$heard’, pero falló $label: ${mediaAction.exceptionOrNull()?.message ?: "error de Android"}."
                }
                intent == TestIntent.CANCEL -> if (cancelledApp) "Escuché ‘$heard’; apertura de aplicación cancelada."
                    else "Escuché ‘$heard’; orden cancelada."
                intent == TestIntent.STOP_TTS -> {
                    val silenced = callPromptLazy.isInitialized() && callPrompt.cancel()
                    if (silenced) "Escuché ‘$heard’; respuesta hablada detenida."
                    else "Escuché ‘$heard’; Silencio reconocido (sin TTS activo)."
                }
                else -> "Escuché ‘$heard’; no coincide con las órdenes de prueba."
            }
            TripTestState.status = "$source: ${TripTestState.status}"
            TripTestState.lastCommandResult = TripTestState.status
            updateNotification("$source · orden terminada; esperando activación")
            if (consecutiveFailures >= 2 && wakeAudioMode != WakeAudioMode.BUTTON) {
                stopSession("Dos activaciones sin orden válida; sesión detenida. ${TripTestState.status}")
            } else {
                val delay = if (wakeAudioMode == WakeAudioMode.BUTTON) 100L
                    else if (intent != TestIntent.UNKNOWN && outcome.isSuccess) 2_000L else 3_500L
                schedule(delay) { if (active && detector === provider && cycle == generation) startDetector(provider) }
            }
        }
    }

    private fun requestCallConfirmation(provider: WakeWordProvider, cycle: Int, contact: TripCallContact) {
        if (!active || detector !== provider || generation != cycle) return
        TripTestState.status = "Anunciando destinatario: ${contact.display}…"
        updateNotification("Anunciando a ${contact.name}")
        runCatching { callPrompt.announce(contact.name) { announced ->
            if (!active || detector !== provider || generation != cycle) return@announce
            if (!announced) {
                TripTestState.failures++
                TripTestState.save(this)
                TripTestState.status = "No se pudo anunciar a ${contact.name}; no se hará la llamada."
                updateNotification("Llamada cancelada: fallo de voz")
                schedule(2_000L) { if (active && detector === provider && generation == cycle) startDetector(provider) }
                return@announce
            }
            TripTestState.status = "Llamar a ${contact.display}: después del segundo tono decí ‘confirmar llamada’ o ‘cancelar’."
            updateNotification("Confirmar llamada a ${contact.name}")
            playHandoffTone()
            schedule(400L) {
                if (!active || detector !== provider || generation != cycle) return@schedule
                schedule(12_000L) {
                    if (active && detector === provider && generation == cycle) {
                        speech.cancel()
                        TripTestState.status = "No se confirmó la llamada; no se marcó."
                        startDetector(provider)
                    }
                }
                speech.listenOnce(onState = { state ->
                    if (active && generation == cycle && state == SpeechCaptureState.READY) {
                        TripTestState.status = "Confirmación lista: decí ‘confirmar llamada’ o ‘cancelar’."
                    }
                }, playbackResumePolicy = { heard ->
                    if (MinimalIntentRouter.resolve(heard.orEmpty()) == TestIntent.CONFIRM_CALL) {
                        PlaybackResumePolicy.KEEP_PAUSED
                    } else PlaybackResumePolicy.NOW
                }) { answer ->
                    if (!active || detector !== provider || generation != cycle) return@listenOnce
                    pendingTask?.let(mainHandler::removeCallbacks)
                    pendingTask = null
                    if (answer.getOrNull()?.let(MinimalIntentRouter::resolve) == TestIntent.CONFIRM_CALL) {
                        finishAndPlaceCall(provider, contact)
                    } else {
                        TripTestState.status = "Llamada cancelada; no se marcó a ${contact.name}."
                        updateNotification("Llamada cancelada; esperando Alexa")
                        schedule(2_000L) { if (active && detector === provider && generation == cycle) startDetector(provider) }
                    }
                }
            }
        } }.onSuccess {
            updateNotification("Anunciando a ${contact.name} · Silencio disponible")
        }.onFailure {
            TripTestState.failures++
            TripTestState.save(this)
            TripTestState.status = "No se pudo anunciar el contacto; no se hará la llamada."
            schedule(2_000L) { if (active && detector === provider && generation == cycle) startDetector(provider) }
        }
    }

    private fun finishAndPlaceCall(provider: WakeWordProvider, contact: TripCallContact) {
        active = false
        callPending = true
        armed = false
        val callCycle = ++generation
        speech.cancel()
        detector = null
        TripTestState.status = "Cerrando el micrófono antes de llamar a ${contact.display}…"
        worker.execute {
            val released = runCatching { provider.close() }
            mainHandler.post {
                if (!callPending || generation != callCycle) return@post
                callPending = false
                val placed = runCatching {
                    released.getOrThrow()
                    check(checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        "Falta permiso para hacer llamadas."
                    }
                    getSystemService(TelecomManager::class.java)
                        .placeCall(Uri.fromParts("tel", contact.number, null), Bundle.EMPTY)
                }
                if (placed.isSuccess) TripTestState.actions++ else TripTestState.failures++
                TripTestState.save(this)
                TripTestState.markRunning(this, false)
                TripTestState.running = false
                TripTestState.status = if (placed.isSuccess) {
                    "Solicitud de llamada a ${contact.display} enviada; comprobá si Android marcó. Sesión de viaje cerrada."
                } else "No se inició la llamada: ${placed.exceptionOrNull()?.message}. Sesión detenida."
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun monitorHardware() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = queueRouteCheck()
            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = queueRouteCheck()
        }
        deviceCallback = callback
        audioManager.registerAudioDeviceCallback(callback, mainHandler)
        val listener = AudioManager.OnCommunicationDeviceChangedListener { queueRouteCheck() }
        communicationListener = listener
        audioManager.addOnCommunicationDeviceChangedListener(mainExecutor, listener)
    }

    private fun queueRouteCheck() {
        routeCheck?.let(mainHandler::removeCallbacks)
        val task = Runnable {
            routeCheck = null
            if (active && armed && wakeAudioMode == WakeAudioMode.BLUETOOTH && !headsetRouteAvailable()) {
                stopSession("Se perdió la ruta del casco. Sesión detenida para no escuchar con otro micrófono; seleccioná el casco y reiniciá.")
            }
        }
        routeCheck = task
        mainHandler.postDelayed(task, 1_200L)
    }

    private fun headsetRouteAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return runCatching {
            val selected = audioManager.communicationDevice ?: return@runCatching false
            if (selected.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO &&
                selected.type != AudioDeviceInfo.TYPE_BLE_HEADSET) return@runCatching false
            val connected = audioManager.availableCommunicationDevices.any {
                it.id == selected.id && it.type == selected.type
            }
            val expected = BluetoothAudioRouteSelector.communicationHeadset(audioManager)
            connected && if (HeadsetSelection.preferred != null) {
                expected?.id == selected.id
            } else (expected == null || expected.id == selected.id)
        }.getOrDefault(false)
    }

    private fun stopMonitoringHardware() {
        routeCheck?.let(mainHandler::removeCallbacks)
        routeCheck = null
        deviceCallback?.let(audioManager::unregisterAudioDeviceCallback)
        deviceCallback = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            communicationListener?.let(audioManager::removeOnCommunicationDeviceChangedListener)
        }
        communicationListener = null
    }

    private fun schedule(delayMs: Long, action: () -> Unit) {
        pendingTask?.let(mainHandler::removeCallbacks)
        val task = Runnable { pendingTask = null; action() }
        pendingTask = task
        mainHandler.postDelayed(task, delayMs)
    }

    private fun stopSession(reason: String) {
        if (closing) return
        active = false
        stopMonitoringHardware()
        closing = true
        TripTestState.restoringAudio = true
        callPending = false
        armed = false
        pendingVolumeCommand = null
        pendingApp = null
        generation++
        val stoppedGeneration = generation
        if (callPromptLazy.isInitialized()) callPrompt.cancel()
        pendingTask?.let(mainHandler::removeCallbacks)
        pendingTask = null
        // Conserva la intención de reproducción mientras se libera SCO. Una pausa pedida por
        // el usuario no aparece aquí y permanece pausada.
        val pausedBySpeech = (if (speechLazy.isInitialized()) speech.cancel(resumePlayback = false) else false) ||
            resumeAfterButtonRoute
        resumeAfterButtonRoute = false
        val previous = detector
        detector = null
        val pausedForRoute = if (previous != null &&
            (wakeAudioMode != WakeAudioMode.BUTTON || !media.isMediaRouteReady())) {
            runCatching { media.pauseIfPlaying() }.getOrDefault(false)
        } else false
        val resumeAfterRoute = pausedBySpeech || pausedForRoute
        val audioCaptureGeneration = TripTestState.audioCaptureGeneration
        TripTestState.running = false
        TripTestState.markRunning(this, false)
        TripTestState.status = "$reason Restaurando audio del casco…"
        // El servicio conserva su ruta y su notificación hasta terminar close() y verificar
        // la salida de música. No cerrar el servicio antes de limpiar setCommunicationDevice().
        if (previous != null) {
            worker.execute {
                val released = runCatching { previous.close() }
                mainHandler.post {
                    if (generation == stoppedGeneration && closing) {
                        captureRestoredAudio(audioCaptureGeneration, stoppedGeneration, 0,
                            resumeAfterRoute, reason, released.exceptionOrNull())
                    }
                }
            }
        } else {
            captureRestoredAudio(audioCaptureGeneration, stoppedGeneration, 0,
                resumeAfterRoute, reason, null)
        }
    }

    private fun captureRestoredAudio(
        captureGeneration: Long,
        stoppedGeneration: Int,
        attempt: Int,
        resumeAfterRoute: Boolean,
        reason: String,
        closeError: Throwable?
    ) {
        if (!closing || generation != stoppedGeneration ||
            TripTestState.audioCaptureGeneration != captureGeneration) return
        TripTestState.audioAfter = media.audioRouteDiagnostic()
        val routeReady = media.isMediaRouteReady()
        if (attempt < 8 && (!routeReady || attempt == 0)) {
            mainHandler.postDelayed({
                captureRestoredAudio(captureGeneration, stoppedGeneration, attempt + 1,
                    resumeAfterRoute, reason, closeError)
            }, 1_000L)
            return
        }
        if (resumeAfterRoute) runCatching { media.resumePlayback() }
        TripTestState.status = when {
            closeError != null -> "$reason No se pudo liberar completamente el audio: ${closeError.message}."
            !routeReady -> "$reason La salida de música aún no volvió a la ruta normal; revisá Audio del casco."
            else -> reason
        }
        closing = false
        TripTestState.restoringAudio = false
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
        val notice = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (wakeAudioMode == WakeAudioMode.BUTTON) "APP · viaje por botón" else "APP · prueba de voz activa")
            .setContentText(detail)
            .setContentIntent(open)
            .setOngoing(true)
        if (wakeAudioMode == WakeAudioMode.BUTTON) {
            val listen = PendingIntent.getService(this, 2,
                Intent(this, TripVoiceService::class.java).setAction(ACTION_MANUAL_COMMAND),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notice.addAction(android.R.drawable.ic_btn_speak_now, "Escuchar orden", listen)
        }
        pendingApp?.let { target ->
            val launch = PendingIntent.getActivity(this, 3, target.launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notice.addAction(android.R.drawable.ic_menu_view, "Abrir ${target.label}", launch)
        }
        if (callPromptLazy.isInitialized() && callPrompt.isAnnouncing()) {
            val silence = PendingIntent.getService(this, 4,
                Intent(this, TripVoiceService::class.java).setAction(ACTION_SILENCE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notice.addAction(android.R.drawable.ic_lock_silent_mode, "Silencio", silence)
        }
        return notice.addAction(android.R.drawable.ic_media_pause, "Detener", stop).build()
    }

    private fun updateNotification(detail: String) {
        if (active) runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(detail))
        }
    }

    override fun onDestroy() {
        stopMonitoringHardware()
        if (active) stopSession("Sesión interrumpida por Android; iniciá una nueva prueba.")
        worker.shutdown()
        super.onDestroy()
    }
}
