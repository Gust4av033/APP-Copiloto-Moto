package com.gust4av033.appcopilotomoto

import android.Manifest
import android.app.Notification
import android.app.KeyguardManager
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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import java.util.concurrent.Executors
import java.util.UUID

/** El servicio es dueño del micrófono y del ciclo de escucha cuando la pantalla se apaga. */
class TripVoiceService : Service() {
    companion object {
        const val ACTION_START = "com.gust4av033.appcopilotomoto.START_TRIP_TEST"
        const val ACTION_STOP = "com.gust4av033.appcopilotomoto.STOP_TRIP_TEST"
        const val ACTION_CHANGE_MODE = "com.gust4av033.appcopilotomoto.CHANGE_TRIP_MODE"
        const val ACTION_SHOW_OVERLAY = "com.gust4av033.appcopilotomoto.SHOW_TRIP_OVERLAY"
        const val ACTION_MANUAL_COMMAND = "com.gust4av033.appcopilotomoto.MANUAL_TRIP_COMMAND"
        const val ACTION_SILENCE = "com.gust4av033.appcopilotomoto.SILENCE_TRIP_SPEECH"
        const val ACTION_APP_OPENED = "com.gust4av033.appcopilotomoto.PENDING_APP_OPENED"
        const val ACTION_CANCEL_APP = "com.gust4av033.appcopilotomoto.CANCEL_PENDING_APP"
        const val EXTRA_APP_TOKEN = "app_token"
        const val EXTRA_PHRASE = "phrase"
        const val EXTRA_SENSITIVITY = "sensitivity"
        const val EXTRA_AUDIO_MODE = "audio_mode"
        const val EXTRA_MANUAL_ONLY = "manual_activation_only"
        private const val CHANNEL_ID = "trip_voice_test"
        private const val NOTIFICATION_ID = 103
        private const val APP_CHANNEL_ID = "trip_app_selection_v1"
        private const val APP_NOTIFICATION_ID = 104
        private const val DIAL_NOTIFICATION_ID = 105
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val followUpGate = FollowUpCaptureGate(
        background = { task -> worker.execute { task() } },
        schedule = { delay, task -> mainHandler.postDelayed({ task() }, delay) },
        now = SystemClock::elapsedRealtime)
    private val speechLazy = lazy { AndroidSpeechToTextProvider(applicationContext) }
    private val speech by speechLazy
    private val capture by lazy { TripCaptureCoordinator(this, { speech }, {
        "modo=${wakeAudioMode.name}; manual=$manualActivationOnly; bloqueada=${getSystemService(KeyguardManager::class.java).isKeyguardLocked}; visible=${TripTestState.appVisible}; ${media.audioRouteDiagnostic()}"
    }, readyValidation = {
        if (!headsetRouteAvailable()) {
            Result.failure(IllegalStateException("La ruta del casco no está lista; se canceló esta escucha."))
        } else Result.success(Unit)
    }) }
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
    private val activationGate = WakeActivationGate()
    private var active = false
    private var closing = false
    private var callPending = false
        set(value) {
            field = value
            TripTestState.callPhase = if (value) TripCallPhase.CALL
                else TripCallPhase.NONE
        }
    private var pendingDialContact: TripCallContact? = null
    private var armed = false
        set(value) {
            field = value
            TripTestState.commandReady = value
            if (value) {
                TripTestState.callPhase = TripCallPhase.NONE
                TripTestState.interactionPhase = TripInteractionPhase.WAITING
                if (TripTestState.switchingMode) {
                    TripTestState.switchingMode = false
                    getSharedPreferences("ui_settings", MODE_PRIVATE).edit()
                        .putString("trip_audio_mode", wakeAudioMode.name)
                        .putBoolean(TripActivationPolicy.PREFERENCE, manualActivationOnly).apply()
                    TripTestState.actions++
                    TripTestState.lastLatencyMs = SystemClock.elapsedRealtime() - modeChangeStartedAt
                    TripTestState.save(this)
                    TripTestState.lastCommandResult = "Modo ${wakeAudioMode.modeNumber} activado${if (manualActivationOnly) " con activación manual" else ""}; el viaje sigue activo."
                }
            } else if (!TripTestState.switchingMode) TripTestState.interactionPhase = TripInteractionPhase.PREPARING
        }
    private var wakeAudioMode = WakeAudioMode.BLUETOOTH
    private var buttonCoordinator: TripButtonCoordinator? = null
    private var manualActivationOnly = false
    private val effectiveAudioMode get() = TripActivationPolicy.effectiveMode(wakeAudioMode, manualActivationOnly)
    private var modeChangeStartedAt = 0L
    private data class PendingRoutedCommand(
        val command: RoutedCommand,
        val heard: String,
        val source: String,
        val wakeAt: Long
    )
    private var pendingVolumeCommand: PendingRoutedCommand? = null
    private var pendingMusicCommand: PendingRoutedCommand? = null
    private val appRequests = PendingAppLaunch<AppTarget>()
    private val pendingApp: AppTarget? get() = appRequests.current?.target
    private var appChoiceToken: String? = null
    private var draftCaptureToken: String? = null
    private var songCaptureToken: String? = null
    private val callConfirmation = VoiceConfirmationGuard()
    private var appExpirationTask: Runnable? = null
    private var appLaunchPendingIntent: PendingIntent? = null
    private var resumeAfterButtonRoute = false
    private var providerFactory: (() -> WakeWordProvider)? = null
    private var phoneListener: PhoneStateListener? = null
    private var callWasActive = false
    private val overlay by lazy {
        TripOverlay(this,
            onRequestCommand = { detector?.let { if (active && armed && !callPending) startManualCommand(it) } },
            onOpenApp = {
                if (!openAssistant()) android.widget.Toast.makeText(this, R.string.overlay_open_error, android.widget.Toast.LENGTH_SHORT).show()
            },
            onHide = { TripTestState.overlayHidden = true },
            onFailure = { TripTestState.overlayMessage = getString(R.string.overlay_error) })
    }
    private val overlayTick = object : Runnable {
        override fun run() {
            if (!active) return
            val visible = TripOverlayPreferences.enabled(this@TripVoiceService) &&
                !TripTestState.overlayHidden && !TripTestState.appVisible &&
                !getSystemService(KeyguardManager::class.java).isKeyguardLocked
            overlay.render(visible, TripOverlaySnapshot(wakeAudioMode, TripTestState.interactionPhase,
                TripTestState.callPhase, armed && !TripTestState.switchingMode && !callPending, manualActivationOnly))
            mainHandler.postDelayed(this, 400L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_OVERLAY) {
            if (active) { TripTestState.overlayHidden = false; TripTestState.overlayMessage = "" }
            else stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CHANGE_MODE) {
            val target = runCatching { WakeAudioMode.valueOf(intent.getStringExtra(EXTRA_AUDIO_MODE).orEmpty()) }.getOrNull()
            val provider = detector
            if (active && armed && !callPending && provider != null && target != null) switchMode(provider, target,
                intent.getBooleanExtra(EXTRA_MANUAL_ONLY, manualActivationOnly))
            else if (!active) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_APP_OPENED) {
            val token = intent.getStringExtra(EXTRA_APP_TOKEN)
            val openedTarget = pendingApp
            val label = openedTarget?.label
            val hadDraft = pendingApp?.draftText != null
            if (active && token != null && clearPendingApp(token)) {
                TripTestState.record(this, PipelineEvent.ACTION_DISPATCHED)
                TripTestState.actions++
                TripTestState.save(this)
                TripTestState.lastCommandResult = if (openedTarget != null && (openedTarget.navigationDestination != null || openedTarget.navigationSearchQuery != null)) navigationDispatchMessage(openedTarget)
                    else if (hadDraft) "Borrador de $label abierto; mensaje sin enviar. El viaje sigue activo."
                    else "Solicitud de apertura de $label enviada; el viaje sigue activo."
                updateNotification("Aplicación abierta · viaje activo")
            } else if (!active) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CANCEL_APP) {
            val token = intent.getStringExtra(EXTRA_APP_TOKEN)
            if (active && token != null && appRequests.current?.token == token) cancelAppChoice(signalCancellation = true)
            if (active && token != null && clearPendingApp(token)) {
                TripTestState.lastCommandResult = "Apertura de aplicación cancelada; el viaje sigue activo."
                updateNotification("Apertura cancelada · viaje activo")
            } else if (!active) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SILENCE) {
            if (callConfirmation.pending) {
                callConfirmation.cancel()
                val cycle = ++generation
                followUpGate.cancel()
                callPrompt.cancel()
                resumeAfterButtonRoute = capture.cancel(resumePlayback = false, signalCancellation = true) || resumeAfterButtonRoute
                TripTestState.status = "Llamada cancelada; no se marcó."
                TripTestState.lastCommandResult = TripTestState.status
                updateNotification("Llamada cancelada; recuperando viaje")
                detector?.let { provider ->
                    schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider && generation == cycle) startDetector(provider) }
                }
                return START_NOT_STICKY
            }
            if (songCaptureToken != null) {
                finishSongCapture("Búsqueda de canción cancelada; viaje activo.", cancelled = true)
                return START_NOT_STICKY
            }
            if (draftCaptureToken != null) {
                finishDraftCapture("Dictado cancelado; no se abrió ni envió el mensaje.", cancelled = true)
                return START_NOT_STICKY
            }
            appChoiceToken?.let { token ->
                cancelAppChoice(signalCancellation = true)
                clearPendingApp(token)
                TripTestState.status = "Apertura de aplicación cancelada; esperando activación."
                TripTestState.lastCommandResult = TripTestState.status
                updateNotification("Apertura cancelada · viaje activo")
                return START_NOT_STICKY
            }
            val silenced = callPromptLazy.isInitialized() && callPrompt.cancel()
            if (active && silenced) {
                capture.cancellationCue()
                pendingTask?.let(mainHandler::removeCallbacks)
                pendingTask = null
                TripTestState.status = "Respuesta hablada detenida; esperando otra orden."
                TripTestState.lastCommandResult = TripTestState.status
                updateNotification("Respuesta detenida; esperando activación")
                detector?.let { provider ->
                    schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider) startDetector(provider) }
                }
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSession("Sesión detenida desde la app o la notificación.", expected = true)
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
        HeadsetSelection.restore(this)
        TripTestState.sessionProblem = false
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            TripTestState.sessionProblem = true
            TripTestState.status = "Falta permiso de micrófono para iniciar la sesión."
            stopSelf(startId)
            return START_NOT_STICKY
        }
        wakeAudioMode = runCatching {
            WakeAudioMode.valueOf(intent.getStringExtra(EXTRA_AUDIO_MODE) ?: WakeAudioMode.BLUETOOTH.name)
        }.getOrDefault(WakeAudioMode.BLUETOOTH)
        manualActivationOnly = intent.getBooleanExtra(EXTRA_MANUAL_ONLY,
            getSharedPreferences("ui_settings", MODE_PRIVATE).getBoolean(TripActivationPolicy.PREFERENCE, false))
        if (!(BuildConfig.DEBUG && UserSettingsStore(this).developerEnabled(BuildConfig.DEBUG))) {
            if (wakeAudioMode == WakeAudioMode.PHONE_WAKE) wakeAudioMode = WakeAudioMode.BLUETOOTH
            if (manualActivationOnly) wakeAudioMode = WakeAudioMode.BUTTON
            manualActivationOnly = false
        }
        TripTestState.manualActivationOnly = manualActivationOnly
        TripTestState.audioMode = wakeAudioMode
        TripTestState.switchingMode = false
        clearPendingApp()
        createChannel()
        val binding = TripButtonBinding.parse(getSharedPreferences("ui_settings", MODE_PRIVATE)
            .getString(TripButtonBinding.PREFERENCE, null))
        try {
            val notice = buildNotification(if (effectiveAudioMode == WakeAudioMode.BUTTON) {
                "Preparando modo botón · música normal"
            } else "Preparando micrófono Bluetooth…")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    if (binding != TripButtonBinding.NONE && TripMediaAccess.granted(this))
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
                startForeground(NOTIFICATION_ID, notice, serviceTypes)
            } else startForeground(NOTIFICATION_ID, notice)
        } catch (e: Exception) {
            TripTestState.sessionProblem = true
            TripTestState.status = "No se pudo iniciar la sesión: ${e.message ?: e.javaClass.simpleName}"
            stopSelf(startId)
            return START_NOT_STICKY
        }
        activationGate.reset()
        active = true
        val hardwareMonitor = runCatching { monitorHardware() }
        if (hardwareMonitor.isFailure) {
            stopSession("No se pudo observar el casco Bluetooth: ${hardwareMonitor.exceptionOrNull()?.message}.")
            return START_NOT_STICKY
        }
        TripTestState.overlayHidden = false
        TripTestState.overlayMessage = ""
        mainHandler.removeCallbacks(overlayTick)
        mainHandler.post(overlayTick)
        TripTestState.running = true
        TripNavigationState.beginSession()
        TripTestState.markRunning(this, true)
        TripTestState.reset(this)
        buttonCoordinator = TripButtonCoordinator(this, binding,
            canActivate = { active && armed && !callPending && !closing && !TripTestState.switchingMode &&
                audioManager.mode != AudioManager.MODE_IN_CALL && audioManager.mode != AudioManager.MODE_RINGTONE },
            onActivate = { detector?.let { startManualCommand(it) } },
            onStatus = { TripTestState.pipeline = TripTestState.pipeline.copy(lastButtonStatus = it); TripTestState.save(this) },
            onReceived = { key, decision, ready ->
                val reason = when (decision) {
                    ButtonDecision.ACTIVATE -> "escuchar orden"
                    ButtonDecision.CONSUME -> if (ready) "pulsación repetida; descartada" else "APP ocupada; descartada"
                    ButtonDecision.PASS -> "control del reproductor"
                }
                var metrics = TripTestState.pipeline.record(PipelineEvent.BUTTON_DOWN_RECEIVED)
                if (decision == ButtonDecision.ACTIVATE) metrics = metrics.record(PipelineEvent.BUTTON_ACTIVATION)
                TripTestState.pipeline = metrics.copy(lastButtonEvent =
                    "$reason; key=${key.keyCode}; downTime=${key.downTime}; eventTime=${key.eventTime}; device=${key.deviceId}; listo=$ready")
                TripTestState.save(this)
            })
        TripTestState.audioCaptureGeneration++
        TripTestState.audioBefore = media.audioRouteDiagnostic()
        TripTestState.audioDuring = "Preparando la ruta Bluetooth…"
        TripTestState.audioAfter = "Sesión activa; falta detenerla."
        TripTestState.status = "Preparando detector; mantené el casco conectado…"

        val phrase = runCatching {
            WakePhrase.valueOf(intent.getStringExtra(EXTRA_PHRASE) ?: WakePhrase.ALEXA.name)
        }.getOrDefault(WakePhrase.ALEXA)
        val sensitivity = intent.getFloatExtra(EXTRA_SENSITIVITY, 80f).coerceIn(40f, 85f)
        providerFactory = {
            if (effectiveAudioMode == WakeAudioMode.BUTTON) ButtonWakeWordProvider(applicationContext)
            else OpenWakeWordProvider(applicationContext, (100f - sensitivity) / 100f, phrase, effectiveAudioMode)
        }
        val provider: WakeWordProvider = runCatching { checkNotNull(providerFactory).invoke() }.getOrElse {
            stopSession("No se pudo preparar la activación: ${it.message}")
            return START_NOT_STICKY
        }
        detector = provider
        startDetector(provider)
        return START_NOT_STICKY // No reiniciar el micrófono sin una nueva acción visible.
    }

    /** El modo cambia dentro de la sesión; no reinicia métricas ni abre dos proveedores. */
    private fun switchMode(provider: WakeWordProvider, target: WakeAudioMode, manualOnly: Boolean = manualActivationOnly) {
        if ((target == WakeAudioMode.PHONE_WAKE || manualOnly) &&
            !(BuildConfig.DEBUG && UserSettingsStore(this).developerEnabled(BuildConfig.DEBUG))) {
            TripTestState.lastCommandResult = getString(R.string.settings_experimental_reset)
            if (!armed && active && !closing && detector === provider) startDetector(provider)
            return
        }
        if (!active || callPending || closing || TripTestState.switchingMode || detector !== provider) return
        TripTestState.lastRoutedCommand = RoutedCommand(TestIntent.SWITCH_MODE, audioMode = target)
        if (target == wakeAudioMode && manualOnly == manualActivationOnly) {
            TripTestState.lastCommandResult = "El modo ${target.modeNumber} ya está seleccionado."
            if (!armed) startDetector(provider)
            return
        }
        val supported = runCatching {
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                BluetoothAudioRouteSelector.communicationHeadset(audioManager) != null
        }.getOrDefault(false)
        if (!supported) {
            TripTestState.lastCommandResult = "No se cambió el modo: revisá permisos y el casco seleccionado en Ajustes."
            if (!armed) startDetector(provider)
            return
        }
        modeChangeStartedAt = SystemClock.elapsedRealtime()
        TripTestState.switchingMode = true
        armed = false
        TripTestState.interactionPhase = TripInteractionPhase.SWITCHING
        val cycle = ++generation
        pendingTask?.let(mainHandler::removeCallbacks)
        pendingTask = null
        clearPendingApp()
        if (speechLazy.isInitialized()) resumeAfterButtonRoute = capture.cancel(resumePlayback = false) || resumeAfterButtonRoute
        if (callPromptLazy.isInitialized()) callPrompt.cancel()
        TripTestState.status = "Cambiando al modo ${target.modeNumber}; liberando audio…"
        updateNotification(TripTestState.status)
        // Se conserva hasta cerrar: Detener también debe poder limpiar el proveedor anterior.
        worker.execute {
            val released = runCatching { provider.close() }
            mainHandler.post {
                if (!active || generation != cycle || detector !== provider) return@post
                if (released.isFailure) {
                    stopSession("No se pudo liberar el modo anterior: ${released.exceptionOrNull()?.message}.")
                } else {
                    detector = null
                    completeModeSwitch(target, manualOnly, cycle, 0)
                }
            }
        }
    }

    private fun completeModeSwitch(target: WakeAudioMode, manualOnly: Boolean, cycle: Int, attempt: Int) {
        if (!active || generation != cycle || !TripTestState.switchingMode) return
        if (!media.isMediaRouteReady()) {
            if (attempt < 12) schedule(500L) { completeModeSwitch(target, manualOnly, cycle, attempt + 1) }
            else stopSession("Android no liberó la ruta anterior; reiniciá el viaje cuando el audio vuelva a la normalidad.")
            return
        }
        wakeAudioMode = target
        manualActivationOnly = manualOnly
        TripTestState.manualActivationOnly = manualOnly
        TripTestState.audioMode = target
        activationGate.reset()
        runCatching { checkNotNull(providerFactory).invoke() }.fold(
            onSuccess = { detector = it; startDetector(it) },
            onFailure = { stopSession("No se pudo activar el modo ${target.modeNumber}: ${it.message}.") }
        )
    }

    private fun openAssistant(): Boolean = runCatching {
        check(!getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        check(TripTestState.appVisible || Settings.canDrawOverlays(this))
        startActivity(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_ASSISTANT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        true
    }.getOrDefault(false)

    private fun startDetector(provider: WakeWordProvider) {
        buttonCoordinator?.beforeCapture()
        if (effectiveAudioMode != WakeAudioMode.BUTTON && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !getSystemService(AudioManager::class.java).availableCommunicationDevices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }) {
            stopSession("No se detecta el micrófono Bluetooth del casco; sesión detenida.")
            return
        }
        if (!active || callPending || detector !== provider) return
        armed = false
        val cycle = ++generation
        TripTestState.status = if (effectiveAudioMode == WakeAudioMode.BUTTON) {
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
                    TripTestState.status = if (effectiveAudioMode == WakeAudioMode.BUTTON) {
                        "Comprobando que la música volvió a su salida normal…"
                    } else "Estabilizando audio; esperá un momento…"
                    schedule(if (effectiveAudioMode == WakeAudioMode.BUTTON) 500L else 1_500L) {
                        armDetector(provider, cycle, 0)
                    }
                }
            }
        }
    }

    private fun armDetector(provider: WakeWordProvider, cycle: Int, retry: Int) {
        if (!active || detector !== provider || cycle != generation) return
        if (effectiveAudioMode == WakeAudioMode.BUTTON) {
            if (!media.isMediaRouteReady()) {
                if (retry < 12) schedule(500L) { armDetector(provider, cycle, retry + 1) }
                else {
                    TripTestState.record(this, PipelineEvent.AUDIO_ROUTE_RESTORE_FAILURE)
                    stopSession("Android no devolvió la música a su salida normal tras la orden.")
                }
                return
            }
            TripTestState.record(this, PipelineEvent.AUDIO_ROUTE_RESTORE_SUCCESS)
            finishPendingVolume(bluetoothVoice = false)
            val pausedBySpeech = if (speechLazy.isInitialized()) capture.cancel(resumePlayback = false) else false
            val resume = pausedBySpeech || resumeAfterButtonRoute
            resumeAfterButtonRoute = false
            val searchSent = finishPendingMusic()
            if (resume && !searchSent) runCatching { media.resumePlayback() }
            armed = true
            buttonCoordinator?.reclaim()
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
            } else stopSession("${if (effectiveAudioMode == WakeAudioMode.PHONE_WAKE) "Modo experimental" else "Micrófono del casco"}: $reason")
            return
        }
        if (effectiveAudioMode == WakeAudioMode.BLUETOOTH && !headsetRouteAvailable()) {
            stopSession("El casco de voz cambió o se desconectó. Elegí el casco de música y reiniciá la sesión.")
            return
        }
        finishPendingVolume(bluetoothVoice = effectiveAudioMode == WakeAudioMode.BLUETOOTH)
        val pausedBySpeech = if (speechLazy.isInitialized()) capture.cancel(resumePlayback = false) else false
        val resume = pausedBySpeech || resumeAfterButtonRoute
        resumeAfterButtonRoute = false
        val searchSent = finishPendingMusic()
        if (resume && !searchSent) runCatching { media.resumePlayback() }
        armed = true
        buttonCoordinator?.reclaim()
        TripTestState.audioDuring = media.audioRouteDiagnostic()
        TripTestState.status = "Esperando ‘${provider.phrase}’; viaje activo… ${inputCheck.getOrNull().orEmpty()}"
        updateNotification("Esperando ‘${provider.phrase}’")
    }

    private fun finishPendingMusic(): Boolean {
        val pending = pendingMusicCommand ?: return false
        pendingMusicCommand = null
        val sent = actions.prepareMusicSearch(pending.command).mapCatching { request ->
            if (request.player.backgroundSearch) actions.sendMusicSearch(request).getOrThrow()
            else (TripTestState.playMusicFromVoice?.invoke(request)
                ?: Result.failure(IllegalStateException("${request.player.label} requiere APP visible y el teléfono desbloqueado. Revisá Ajustes → Música."))).getOrThrow()
            request
        }
        if (sent.isSuccess) {
            TripTestState.record(this, PipelineEvent.ACTION_DISPATCHED)
            TripTestState.actions++
        } else TripTestState.failures++
        TripTestState.lastLatencyMs = SystemClock.elapsedRealtime() - pending.wakeAt
        TripTestState.lastCommandResult = sent.fold(
            onSuccess = { "Búsqueda ‘${it.query}’ enviada a ${it.player.label}; comprobá la canción que reproduzca. Viaje activo." },
            onFailure = { "No se envió la búsqueda de canción: ${it.message}. Viaje activo." }
        )
        TripTestState.save(this)
        // La búsqueda enviada inicia su propia reproducción; no reenviar PLAY a la pista anterior.
        return sent.isSuccess
    }

    private fun finishPendingVolume(bluetoothVoice: Boolean) {
        val pending = pendingVolumeCommand ?: return
        pendingVolumeCommand = null
        val action = actions.executeRestoredVolume(pending.command, bluetoothVoice) ?: return
        val change = action.getOrNull()
        val atLimit = change != null && when (pending.command.intent) {
            TestIntent.VOLUME_UP -> change.before >= change.maximum
            TestIntent.VOLUME_DOWN -> change.before <= media.minimumVolume(change.stream)
            TestIntent.VOLUME_SET -> change.after == change.before && !change.fixed
            else -> false
        }
        if (action.isSuccess) TripTestState.record(this, PipelineEvent.ACTION_DISPATCHED)
        if (change != null && change.after != change.before) TripTestState.record(this, PipelineEvent.ACTION_CONFIRMED_BY_READBACK)
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
            "Escuché ‘${pending.heard}’; $command (${media.commandVolumeStatus(change)})."
        } else {
            "${pending.source}: Escuché ‘${pending.heard}’, pero falló $command: ${action.exceptionOrNull()?.message ?: "error de Android"}."
        }
    }

    private fun onDetected(provider: WakeWordProvider, cycle: Int, score: Float) {
        if (!active || !armed || detector !== provider || cycle != generation) return
        val now = SystemClock.elapsedRealtime()
        val waitMs = activationGate.waitBeforeCapture(now)
        if (waitMs > 0L) {
            armed = false
            val quietCycle = ++generation
            TripTestState.status = "Activaciones muy seguidas; pausa de ${waitMs / 1_000} segundos, el viaje sigue activo."
            TripTestState.lastCommandResult = TripTestState.status
            updateNotification("Pausa breve de activación · viaje activo")
            worker.execute {
                val paused = runCatching { provider.pause() }
                mainHandler.post {
                    if (!active || detector !== provider || generation != quietCycle) return@post
                    if (paused.isFailure) stopSession("No se pudo liberar el micrófono: ${paused.exceptionOrNull()?.message}")
                    else schedule(waitMs) {
                        if (active && detector === provider && generation == quietCycle) startDetector(provider)
                    }
                }
            }
            return
        }
        armed = false
        generation++ // Descarta callbacks anteriores al traspaso del micrófono.
        TripTestState.record(this, PipelineEvent.WAKE_DETECTION)
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
                    schedule(VoiceInteractionTiming.HANDOFF_MS) {
                        if (active && detector === provider && handoff == generation) {
                            listenForCommand(provider, now, provider.phrase)
                        }
                    }
                }
            }
        }
    }

    /** Usa la escucha y las confirmaciones de la sesión existente sin contar una detección de wake word. */
    private fun startManualCommand(provider: WakeWordProvider) {
        buttonCoordinator?.beforeCapture()
        armed = false
        val cycle = ++generation // Invalida detecciones atrasadas de Alexa.
        val startedAt = SystemClock.elapsedRealtime()
        TripTestState.record(this, PipelineEvent.MANUAL_ACTIVATION)
        TripTestState.manualCommands++
        TripTestState.save(this)
        TripTestState.status = "Botón del casco recibido; preparando orden…"
        updateNotification("Preparando orden del botón")
        worker.execute {
            val paused = runCatching { provider.pause() }
            mainHandler.post {
                if (!active || detector !== provider || cycle != generation) return@post
                if (paused.isFailure) {
                    if (effectiveAudioMode == WakeAudioMode.BUTTON) {
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
        if (effectiveAudioMode == WakeAudioMode.BUTTON && !headsetRouteAvailable()) {
            if (attempt < 12) {
                schedule(250L) { awaitManualRoute(provider, cycle, startedAt, attempt + 1) }
            } else {
                recoverButtonCommand(provider, cycle,
                    "Android no confirmó el micrófono Bluetooth; se canceló esta orden.")
            }
            return
        }
        schedule(VoiceInteractionTiming.HANDOFF_MS) {
            if (active && detector === provider && cycle == generation) {
                listenForCommand(provider, startedAt, "Casco")
            }
        }
    }

    /** Una orden fallida no termina el viaje; suelta SCO antes de aceptar otra pulsación. */
    private fun recoverButtonCommand(provider: WakeWordProvider, cycle: Int, reason: String) {
        if (!active || detector !== provider || cycle != generation) return
        TripTestState.failures++
        TripTestState.save(this)
        if (speechLazy.isInitialized()) {
            resumeAfterButtonRoute = capture.cancel(resumePlayback = false) || resumeAfterButtonRoute
        }
        TripTestState.status = "$reason Restaurando música; probá de nuevo cuando indique que está listo."
        TripTestState.lastCommandResult = TripTestState.status
        updateNotification("Orden cancelada · restaurando música")
        schedule(100L) {
            if (active && detector === provider && cycle == generation) startDetector(provider)
        }
    }

    private fun listenForCommand(provider: WakeWordProvider, wakeAt: Long, source: String) {
        buttonCoordinator?.beforeCapture()
        TripTestState.status = "$source: abriendo reconocedor; decí la orden cuando esté listo…"
        updateNotification("$source · abriendo reconocedor")
        val cycle = generation
        TripTestState.interactionPhase = TripInteractionPhase.PREPARING
        capture.listenOnce(onState = { state ->
            if (active && cycle == generation && state == SpeechCaptureState.READY) {
                val input = speech.activeInputDescription()
                if (effectiveAudioMode == WakeAudioMode.BUTTON &&
                    input.contains("micrófono del teléfono") && !input.contains("Bluetooth")) {
                    TripTestState.failures++
                    TripTestState.save(this)
                    resumeAfterButtonRoute = capture.cancel(resumePlayback = false)
                    TripTestState.status = "Android abrió el micrófono del teléfono; orden cancelada. Restaurando música."
                    TripTestState.lastCommandResult = TripTestState.status
                    schedule(100L) { if (active && detector === provider && cycle == generation) startDetector(provider) }
                } else {
                    TripTestState.interactionPhase = TripInteractionPhase.LISTENING
                    TripTestState.lastWakeToSttMs = SystemClock.elapsedRealtime() - wakeAt
                    TripTestState.save(this)
                    TripTestState.status = "$source: después de la señal, decí música, volumen, ‘abre [aplicación]’ o ‘llama a [nombre]’. Entrada: $input."
                    updateNotification("$source · reconocedor listo; decí la orden")
                }
            }
        }, playbackResumePolicy = { heard ->
            when (heard?.let(MinimalIntentRouter::resolve)) {
                TestIntent.MEDIA_PAUSE -> PlaybackResumePolicy.KEEP_PAUSED
                TestIntent.CALL_SELECTED, TestIntent.CALL_NAMED, TestIntent.OPEN_APP, TestIntent.NAVIGATE, TestIntent.SEARCH_PLACE, TestIntent.OPEN_CONTACT_CHAT,
                TestIntent.DRAFT_CONTACT_MESSAGE, TestIntent.MEDIA_SEARCH -> PlaybackResumePolicy.AFTER_ROUTE_RESTORED
                TestIntent.STOP_SESSION, TestIntent.SWITCH_MODE -> PlaybackResumePolicy.AFTER_ROUTE_RESTORED
                else -> if (effectiveAudioMode == WakeAudioMode.BUTTON) {
                    PlaybackResumePolicy.AFTER_ROUTE_RESTORED
                } else PlaybackResumePolicy.NOW
            }
        }) { outcome ->
            if (!active || cycle != generation) return@listenOnce
            TripTestState.interactionPhase = TripInteractionPhase.PROCESSING
            val heard = outcome.getOrNull()
            val command = heard?.let(MinimalIntentRouter::route) ?: RoutedCommand(TestIntent.UNKNOWN)
            TripTestState.lastRoutedCommand = if (heard != null) command else null
            val intent = command.intent
            if (heard != null) TripTestState.record(this,
                if (intent == TestIntent.UNKNOWN) PipelineEvent.INTENT_UNKNOWN else PipelineEvent.INTENT_RESOLVED)
            val cancelledApp = intent == TestIntent.CANCEL && pendingApp != null
            // Una orden nueva reemplaza cualquier apertura que aún estaba pendiente.
            if (intent != TestIntent.OPEN_APP && intent != TestIntent.NAVIGATE && intent != TestIntent.SEARCH_PLACE && intent != TestIntent.OPEN_CONTACT_CHAT && intent != TestIntent.DRAFT_CONTACT_MESSAGE) {
                clearPendingApp()
            }
            if (intent == TestIntent.SWITCH_MODE && command.audioMode != null) {
                TripTestState.recognized++
                TripTestState.save(this)
                switchMode(provider, command.audioMode)
                return@listenOnce
            }
            if (intent == TestIntent.OPEN_ASSISTANT) {
                // Opening APP remains separate from reading the navigation source.
                TripTestState.recognized++
                val opened = openAssistant()
                if (opened) {
                    TripTestState.record(this, PipelineEvent.ACTION_DISPATCHED)
                    TripTestState.actions++
                } else TripTestState.failures++
                TripTestState.lastCommandResult = if (opened) "APP abierta; el viaje sigue activo."
                    else "No se pudo abrir APP. Desbloqueá el teléfono y usá la burbuja o la notificación."
                TripTestState.save(this)
                schedule(100L) { if (active && detector === provider && cycle == generation) startDetector(provider) }
                return@listenOnce
            }
            if (intent == TestIntent.NEXT_TURN) {
                TripTestState.recognized++
                TripTestState.save(this)
                explainResolutionProblem(provider, cycle, actions.nextTurnAnswer())
                return@listenOnce
            }
            if (intent == TestIntent.VOLUME_UP || intent == TestIntent.VOLUME_DOWN ||
                intent == TestIntent.VOLUME_SET) {
                TripTestState.recognized++
                TripTestState.save(this)
                pendingVolumeCommand = PendingRoutedCommand(command, heard.orEmpty(), source, wakeAt)
                TripTestState.status = "$source: Escuché ‘$heard’; restaurando la ruta para ajustar volumen…"
                TripTestState.lastCommandResult = TripTestState.status
                updateNotification("$source · restaurando ruta de voz")
                schedule(VoiceInteractionTiming.RECOVERY_MS) {
                    if (active && detector === provider && cycle == generation) startDetector(provider)
                }
                return@listenOnce
            }
            if (intent == TestIntent.MEDIA_SEARCH) {
                TripTestState.recognized++
                TripTestState.save(this)
                if (command.musicTitleInEnglish) {
                    requestEnglishSongCapture(provider, cycle, source, wakeAt)
                    return@listenOnce
                }
                pendingMusicCommand = PendingRoutedCommand(command, heard.orEmpty(), source, wakeAt)
                TripTestState.status = "Restaurando audio antes de buscar ‘${command.musicQuery}’…"
                updateNotification("Preparando búsqueda de canción")
                schedule(VoiceInteractionTiming.RECOVERY_MS) {
                    if (active && detector === provider && cycle == generation) startDetector(provider)
                }
                return@listenOnce
            }
            val mediaAction = actions.executeMedia(command)
            val elapsed = SystemClock.elapsedRealtime() - wakeAt
            TripTestState.lastLatencyMs = elapsed
            if (heard != null) TripTestState.recognized++
            if (mediaAction?.isSuccess == true) TripTestState.record(this, PipelineEvent.ACTION_DISPATCHED)
            val volumeChange = mediaAction?.getOrNull() as? VolumeAdjustment
            if (volumeChange != null && volumeChange.after != volumeChange.before)
                TripTestState.record(this, PipelineEvent.ACTION_CONFIRMED_BY_READBACK)
            if (mediaAction?.isSuccess == true && (volumeChange == null || volumeChange.after != volumeChange.before)) TripTestState.actions++
            if (outcome.isFailure || intent == TestIntent.UNKNOWN || mediaAction?.isFailure == true) {
                TripTestState.failures++
            }
            TripTestState.save(this)
            if (intent == TestIntent.STOP_SESSION) {
                stopSession("Sesión detenida por voz: ‘$heard’ en $elapsed ms.", expected = true)
                return@listenOnce
            }
            if (intent == TestIntent.OPEN_APP || intent == TestIntent.NAVIGATE || intent == TestIntent.SEARCH_PLACE || intent == TestIntent.OPEN_CONTACT_CHAT || intent == TestIntent.DRAFT_CONTACT_MESSAGE) {
                clearPendingApp()
                val navigator = (command.navigationProvider ?: NavigationPreferences.selected(this)).label
                TripTestState.status = when (intent) {
                    TestIntent.NAVIGATE -> "Preparando destino en $navigator…"
                    TestIntent.SEARCH_PLACE -> "Preparando búsqueda en $navigator…"
                    TestIntent.OPEN_APP -> "Buscando aplicación…"
                    else -> "Buscando contacto para el chat…"
                }
                updateNotification(TripTestState.status)
                worker.execute {
                    val found = when (intent) {
                        TestIntent.NAVIGATE -> actions.prepareNavigation(command)
                        TestIntent.SEARCH_PLACE -> actions.preparePlaceSearch(command)
                        TestIntent.OPEN_APP -> actions.prepareApp(command)
                        else -> actions.prepareContactChat(command)
                    }
                    mainHandler.post {
                        if (!active || detector !== provider || cycle != generation) return@post
                        if (intent == TestIntent.DRAFT_CONTACT_MESSAGE && found.isSuccess) {
                            val target = found.getOrThrow()
                            if (command.messageText == null) requestDraftCapture(provider, cycle, target)
                            else prepareDraftChoice(provider, cycle, target, command.messageText)
                            return@post
                        }
                        found.getOrNull()?.let(::preparePendingApp)
                        TripTestState.status = if (pendingApp != null) {
                            "$source: Encontré ${pendingApp?.label}. Escuchá la confirmación: uno para abrir o dos para cancelar."
                        } else {
                            TripTestState.failures++
                            "$source: ${found.exceptionOrNull()?.message ?: "No se pudo preparar la apertura."}"
                        }
                        TripTestState.lastCommandResult = TripTestState.status
                        TripTestState.save(this)
                        updateNotification(if (pendingApp != null) "Acción lista: ${pendingApp?.label}" else "No se pudo preparar la acción")
                        val request = appRequests.current
                        if (request != null) requestAppChoice(provider, cycle, request)
                        else if (found.isFailure) explainResolutionProblem(provider, cycle,
                            found.exceptionOrNull()?.message ?: "No se pudo preparar la acción.")
                        else schedule(if (effectiveAudioMode == WakeAudioMode.BUTTON) 100L else 2_000L) {
                            if (active && detector === provider && cycle == generation) startDetector(provider)
                        }
                    }
                }
                return@listenOnce
            }
            if (intent == TestIntent.CALL_SELECTED) {
                val contact = TripCallContactStore.load(this)
                if (contact == null || (BuildConfig.ALLOW_DIRECT_CALL &&
                        checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)) {
                    TripTestState.failures++
                    TripTestState.save(this)
                    TripTestState.status = if (BuildConfig.ALLOW_DIRECT_CALL)
                        "Para llamar, elegí un contacto y concedé el permiso de teléfono desde APP antes del viaje."
                        else "Para abrir el marcador, elegí un contacto en APP antes del viaje."
                    updateNotification("Llamada sin configurar")
                    schedule(if (effectiveAudioMode == WakeAudioMode.BUTTON) 100L else 3_500L) {
                        if (active && detector === provider && cycle == generation) startDetector(provider)
                    }
                } else requestCallConfirmation(provider, cycle, contact)
                return@listenOnce
            }
            if (intent == TestIntent.CALL_NAMED) {
                val name = command.contactName ?: return@listenOnce
                if ((BuildConfig.ALLOW_DIRECT_CALL && checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) ||
                    checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    TripTestState.failures++
                    TripTestState.save(this)
                    TripTestState.status = if (BuildConfig.ALLOW_DIRECT_CALL)
                        "Habilitá llamadas por nombre desde APP: requiere contactos y teléfono."
                        else "Habilitá contactos desde APP para abrir el marcador por nombre."
                    updateNotification("Llamada por nombre sin permisos")
                    schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider && cycle == generation) startDetector(provider) }
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
                                explainResolutionProblem(provider, cycle, TripTestState.status)
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
                    if (mediaAction.isSuccess) "Escuché ‘$heard’; $label."
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
            TripTestState.lastCommandResult = TripTestState.status
            if (intent == TestIntent.CANCEL || intent == TestIntent.STOP_TTS) capture.cancellationCue()
            updateNotification("$source · orden terminada; esperando activación")
            // Ruido, silencio y órdenes desconocidas no terminan una sesión de viaje.
            val delay = VoiceInteractionTiming.RECOVERY_MS
            schedule(delay) { if (active && detector === provider && cycle == generation) startDetector(provider) }
        }
    }

    private fun isCurrentAppChoice(provider: WakeWordProvider, cycle: Int, token: String): Boolean =
        active && detector === provider && generation == cycle && appChoiceToken == token &&
            appRequests.current?.let { it.token == token && SystemClock.elapsedRealtime() < it.expiresAtMs } == true

    private fun explainResolutionProblem(provider: WakeWordProvider, cycle: Int, message: String) {
        if (!active || detector !== provider || generation != cycle) return
        TripTestState.interactionPhase = TripInteractionPhase.SPEAKING
        TripTestState.status = message
        TripTestState.lastCommandResult = message
        fun recover() {
            if (!active || detector !== provider || generation != cycle) return
            schedule(VoiceInteractionTiming.RECOVERY_MS) {
                if (active && detector === provider && generation == cycle) startDetector(provider)
            }
        }
        prepareFollowUp(provider, { active && detector === provider && generation == cycle },
            onFailure = { reason ->
                TripTestState.lastCommandResult = "$message No se pudo leer la aclaración: $reason"
                recover()
            }) {
            TripTestState.interactionPhase = TripInteractionPhase.SPEAKING
            TripTestState.status = message
            runCatching { callPrompt.explain(message) { recover() } }
                .onSuccess { updateNotification("Escuchá la aclaración · Silencio disponible") }
                .onFailure { recover() }
        }
    }

    private fun prepareFollowUp(provider: WakeWordProvider, isCurrent: () -> Boolean,
        onFailure: (String) -> Unit, onReady: () -> Unit) {
        buttonCoordinator?.beforeCapture()
        TripTestState.interactionPhase = TripInteractionPhase.PREPARING
        TripTestState.status = "Preparando la siguiente escucha en el casco; esperá el tono."
        updateNotification(TripTestState.status)
        followUpGate.prepare(isCurrent,
            selectRoute = { runCatching { provider.pause() } },
            routeReady = { headsetRouteAvailable() },
            onReady = onReady, onFailure = onFailure)
    }

    private fun requestEnglishSongCapture(provider: WakeWordProvider, cycle: Int, source: String, wakeAt: Long) {
        val token = UUID.randomUUID().toString()
        songCaptureToken = token
        fun current() = active && detector === provider && generation == cycle && songCaptureToken == token
        TripTestState.interactionPhase = TripInteractionPhase.SPEAKING
        TripTestState.status = "Preparando escucha del título en inglés."
        updateNotification(TripTestState.status)
        runCatching {
            callPrompt.askForEnglishSong { announced ->
                if (!current()) return@askForEnglishSong
                if (!announced) {
                    finishSongCapture("No se pudo anunciar la búsqueda de canción.")
                    return@askForEnglishSong
                }
                prepareFollowUp(provider, ::current,
                    onFailure = { finishSongCapture(it) }) {
                    capture.listenOnce(mode = SpeechCaptureMode.SONG_TITLE, onState = { state ->
                        if (current() && state == SpeechCaptureState.READY) {
                            val input = speech.activeInputDescription()
                            if (effectiveAudioMode == WakeAudioMode.BUTTON && input.contains("micrófono del teléfono") && !input.contains("Bluetooth")) {
                                finishSongCapture("Android abrió el micrófono del teléfono; búsqueda cancelada.")
                            } else {
                                TripTestState.interactionPhase = TripInteractionPhase.LISTENING
                                TripTestState.status = "Título listo: después del tono decí el nombre en inglés o cancel."
                                updateNotification(TripTestState.status)
                            }
                        }
                    }, playbackResumePolicy = { PlaybackResumePolicy.AFTER_ROUTE_RESTORED }) { result ->
                        if (!current()) return@listenOnce
                        val title = result.getOrNull()?.let(EnglishSongRequest::title)
                        if (title == null) {
                            finishSongCapture(result.exceptionOrNull()?.message ?: "Búsqueda de canción cancelada.", cancelled = result.isSuccess)
                        } else {
                            songCaptureToken = null
                            val command = RoutedCommand(TestIntent.MEDIA_SEARCH, musicQuery = title)
                            TripTestState.lastRoutedCommand = command
                            TripTestState.recognized++
                            pendingMusicCommand = PendingRoutedCommand(command, title, source, wakeAt)
                            TripTestState.lastCommandResult = "Título reconocido: ‘$title’. Restaurando audio antes de buscar."
                            TripTestState.save(this)
                            schedule(100L) { if (active && detector === provider && generation == cycle) startDetector(provider) }
                        }
                    }
                }
            }
        }.onFailure { if (current()) finishSongCapture("No se pudo preparar el título: ${it.message}") }
    }

    private fun finishSongCapture(message: String, cancelled: Boolean = false) {
        if (songCaptureToken == null) return
        songCaptureToken = null
        followUpGate.cancel()
        val cycle = ++generation
        callPrompt.cancel()
        resumeAfterButtonRoute = capture.cancel(resumePlayback = false, signalCancellation = cancelled) || resumeAfterButtonRoute
        TripTestState.status = message
        TripTestState.lastCommandResult = message
        updateNotification(message)
        detector?.let { provider ->
            schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider && generation == cycle) startDetector(provider) }
        }
    }

    private fun requestDraftCapture(provider: WakeWordProvider, cycle: Int, target: AppTarget) {
        val token = UUID.randomUUID().toString()
        draftCaptureToken = token
        fun current() = active && detector === provider && generation == cycle && draftCaptureToken == token
        TripTestState.interactionPhase = TripInteractionPhase.SPEAKING
        TripTestState.status = "Preparando dictado para ${target.label}; Silencio cancela."
        updateNotification("Preparando dictado · Silencio cancela")
        runCatching {
            callPrompt.askForDraft(target.spokenLabel) { announced ->
                if (!current()) return@askForDraft
                if (!announced) {
                    finishDraftCapture("No se pudo anunciar el destinatario; dictado cancelado.")
                    return@askForDraft
                }
                prepareFollowUp(provider, ::current, onFailure = { finishDraftCapture(it) }) {
                    TripTestState.interactionPhase = TripInteractionPhase.PREPARING
                    capture.listenOnce(mode = SpeechCaptureMode.DICTATION, onState = { state ->
                        if (current() && state == SpeechCaptureState.READY) {
                            val input = speech.activeInputDescription()
                            if (effectiveAudioMode == WakeAudioMode.BUTTON && input.contains("micrófono del teléfono") && !input.contains("Bluetooth")) {
                                finishDraftCapture("Android abrió el micrófono del teléfono; dictado cancelado.")
                            } else {
                                TripTestState.interactionPhase = TripInteractionPhase.LISTENING
                                TripTestState.status = "Dictado listo: decí el mensaje breve o cancelar."
                                updateNotification("Dictando mensaje · Silencio cancela")
                            }
                        }
                    }, playbackResumePolicy = { PlaybackResumePolicy.AFTER_ROUTE_RESTORED }) { outcome ->
                        if (!current()) return@listenOnce
                        pendingTask?.let(mainHandler::removeCallbacks)
                        pendingTask = null
                        val text = outcome.getOrNull()
                        val control = text?.let(MinimalIntentRouter::resolve)
                        if (control == TestIntent.STOP_SESSION) {
                            stopSession("Viaje detenido durante dictado; mensaje descartado.", expected = true)
                        } else if (text == null || control == TestIntent.CANCEL || control == TestIntent.STOP_TTS) {
                            finishDraftCapture("No se preparó el mensaje: ${outcome.exceptionOrNull()?.message ?: "dictado cancelado"}.", cancelled = outcome.isSuccess)
                        } else {
                            TripTestState.recognized++
                            TripTestState.save(this)
                            draftCaptureToken = null
                            prepareDraftChoice(provider, cycle, target, text)
                        }
                    }
                }
            }
        }.onFailure { if (current()) finishDraftCapture("No se pudo iniciar el dictado; no se preparó el mensaje.") }
    }

    private fun finishDraftCapture(message: String, cancelled: Boolean = false) {
        if (draftCaptureToken == null) return
        draftCaptureToken = null
        followUpGate.cancel()
        val cycle = ++generation
        pendingTask?.let(mainHandler::removeCallbacks)
        pendingTask = null
        if (callPromptLazy.isInitialized()) callPrompt.cancel()
        if (speechLazy.isInitialized()) resumeAfterButtonRoute = capture.cancel(resumePlayback = false, signalCancellation = cancelled) || resumeAfterButtonRoute
        TripTestState.status = message
        TripTestState.lastCommandResult = message
        updateNotification("Dictado cancelado · viaje activo")
        detector?.let { provider ->
            schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider && generation == cycle) startDetector(provider) }
        }
    }

    private fun prepareDraftChoice(provider: WakeWordProvider, cycle: Int, target: AppTarget, text: String) {
        if (!active || detector !== provider || generation != cycle) return
        val draft = runCatching { WhatsAppChatAdapter.withDraft(target, text) }
        draft.fold(onSuccess = {
            preparePendingApp(it)
            TripTestState.lastCommandResult = "Borrador preparado. Uno abre, dos descarta, tres vuelve a dictar, cuatro repite la lectura. No se envió."
            requestAppChoice(provider, cycle, checkNotNull(appRequests.current))
        }, onFailure = {
            TripTestState.failures++
            TripTestState.save(this)
            TripTestState.status = "No se preparó el borrador: ${it.message}"
            TripTestState.lastCommandResult = TripTestState.status
            schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider && generation == cycle) startDetector(provider) }
        })
    }

    private fun requestAppChoice(provider: WakeWordProvider, cycle: Int, request: PendingAppLaunch.Request<AppTarget>) {
        val token = request.token
        appChoiceToken = token
        TripTestState.interactionPhase = TripInteractionPhase.SPEAKING
        TripTestState.status = "Anunciando ${request.target.label}: uno para abrir o dos para cancelar…"
        runCatching {
            callPrompt.announceApp(request.target.spokenLabel, request.target.draftText) { announced ->
                if (!isCurrentAppChoice(provider, cycle, token)) return@announceApp
                if (!announced) {
                    finishAppChoice(provider, cycle, token, "No se pudo anunciar la app; la apertura quedó pendiente en el aviso.")
                    return@announceApp
                }
                renewAppChoiceWindow(token)
                prepareFollowUp(provider, { isCurrentAppChoice(provider, cycle, token) },
                    onFailure = { finishAppChoice(provider, cycle, token, it) }) {
                    TripTestState.status = if (request.target.draftText != null) "Esperá el tono: uno abre, dos descarta, tres vuelve a dictar, cuatro repite la lectura."
                        else "Abriendo confirmación: esperá el tono y decí uno o dos."
                    updateNotification(if (request.target.draftText != null) "Borrador: 1 abrir · 2 descartar · 3 dictar · 4 repetir" else "Confirmación: 1 abrir · 2 cancelar")
                    capture.listenOnce(mode = SpeechCaptureMode.CONFIRMATION, onState = { state ->
                        if (isCurrentAppChoice(provider, cycle, token) && state == SpeechCaptureState.READY) {
                            val input = speech.activeInputDescription()
                            if (effectiveAudioMode == WakeAudioMode.BUTTON && input.contains("micrófono del teléfono") &&
                                !input.contains("Bluetooth")) {
                                finishAppChoice(provider, cycle, token, "Android abrió el micrófono del teléfono; no se confirmó la apertura.")
                            } else {
                                renewAppChoiceWindow(token)
                                TripTestState.interactionPhase = TripInteractionPhase.CONFIRMING
                                TripTestState.status = if (request.target.draftText != null) "Borrador listo: uno abre, dos descarta, tres vuelve a dictar, cuatro repite la lectura."
                                    else "Confirmación lista: uno para abrir ${request.target.label}; dos para cancelar."
                            }
                        }
                    }, playbackResumePolicy = { PlaybackResumePolicy.AFTER_ROUTE_RESTORED }) { answer ->
                        if (!isCurrentAppChoice(provider, cycle, token)) return@listenOnce
                        val heard = answer.getOrNull()
                        if (heard != null) TripTestState.recognized++
                        if (heard?.let(MinimalIntentRouter::resolve) == TestIntent.STOP_SESSION) {
                            stopSession("Sesión detenida durante la confirmación de aplicación.", expected = true)
                            return@listenOnce
                        }
                        when (if (request.target.draftText != null) DraftChoiceRouter.resolve(heard) else AppChoiceRouter.resolve(heard)) {
                            AppChoice.OPEN -> {
                                val opened = openConfirmedApp(token, request.target)
                                val result = if (opened && (request.target.navigationDestination != null || request.target.navigationSearchQuery != null)) navigationDispatchMessage(request.target)
                                    else if (opened && request.target.draftText != null) "Borrador abierto en WhatsApp; mensaje sin enviar. Viaje activo."
                                    else if (opened) "Solicitud de apertura de ${request.target.label} enviada por voz; viaje activo."
                                    else "No se pudo abrir ${request.target.label}. Desbloqueá el teléfono y habilitá Mostrar sobre otras apps, o usá el aviso de apertura."
                                // Terminar primero: limpiar la solicitud no debe cancelar este mismo resultado.
                                finishAppChoice(provider, cycle, token, result)
                                if (opened && clearPendingApp(token)) {
                                    TripTestState.record(this, PipelineEvent.ACTION_DISPATCHED)
                                    TripTestState.actions++
                                }
                            }
                            AppChoice.CANCEL -> finishAppChoice(provider, cycle, token,
                                "Apertura de ${request.target.label} cancelada por voz; viaje activo.", cancelRequest = true)
                            AppChoice.REDICTATE -> redictateDraft(provider, cycle, request)
                            AppChoice.REPEAT -> repeatDraftReadback(provider, cycle, request)
                            AppChoice.UNKNOWN -> finishAppChoice(provider, cycle, token,
                                if (answer.isFailure) "Falló la escucha de confirmación: ${answer.exceptionOrNull()?.message}. La solicitud sigue en el aviso."
                                else if (request.target.draftText != null) "No se reconoció uno, dos, tres o cuatro; el borrador sigue en el aviso, sin enviar."
                                else "No se reconoció uno o dos; la app no se abrió. La solicitud sigue en el aviso.")
                        }
                        TripTestState.save(this)
                    }
                }
            }
            updateNotification("Anunciando ${request.target.label} · Silencio disponible")
        }.onFailure {
            if (isCurrentAppChoice(provider, cycle, token)) {
                finishAppChoice(provider, cycle, token, "No se pudo iniciar la confirmación; la apertura sigue en el aviso.")
            }
        }
    }

    private fun redictateDraft(provider: WakeWordProvider, cycle: Int, request: PendingAppLaunch.Request<AppTarget>) {
        if (!isCurrentAppChoice(provider, cycle, request.token) || request.target.draftText == null) return
        val base = runCatching { WhatsAppChatAdapter.withoutDraft(request.target) }
        if (base.isFailure) {
            finishAppChoice(provider, cycle, request.token, "No se pudo volver a dictar; borrador descartado.", cancelRequest = true)
            return
        }
        // Cierra la confirmación antes de invalidar el aviso; conserva contacto y edición, sin texto viejo.
        finishAppChoice(provider, cycle, request.token, "Borrador anterior descartado; preparando nuevo dictado.")
        clearPendingApp(request.token)
        pendingTask?.let(mainHandler::removeCallbacks)
        pendingTask = null
        requestDraftCapture(provider, cycle, base.getOrThrow())
    }

    private fun repeatDraftReadback(provider: WakeWordProvider, cycle: Int, request: PendingAppLaunch.Request<AppTarget>) {
        if (!isCurrentAppChoice(provider, cycle, request.token) || request.target.draftText == null) return
        // Reserva también la locución completa: no dejar vencer un mensaje largo mientras se relee.
        val renewed = appRequests.renew(request.token, SystemClock.elapsedRealtime(),
            VoiceInteractionTiming.pendingAppMs(request.target.spokenLabel, request.target.draftText)) ?: return
        TripTestState.pendingAppExpiresAtMs = renewed.expiresAtMs
        scheduleAppExpiration(renewed)
        showAppPrompt(renewed)
        requestAppChoice(provider, cycle, renewed)
    }

    /** Solo después de recibir UNO para la solicitud actual. Nunca desbloquea el teléfono. */
    private fun openConfirmedApp(token: String, target: AppTarget): Boolean {
        val unlocked = !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!TripAppLaunchPolicy.canOpen(active, unlocked, TripTestState.appVisible, Settings.canDrawOverlays(this))) return false
        val request = appRequests.current ?: return false
        if (request.token != token || SystemClock.elapsedRealtime() >= request.expiresAtMs) return false
        val activityOpener = TripTestState.openPendingAppFromVoice
        val navigationSession = TripNavigationState.context.sessionId
        val opensFromActivity = TripTestState.appVisible && activityOpener != null
        return runCatching {
            if (opensFromActivity) checkNotNull(activityOpener)(token)
            else {
                // SYSTEM_ALERT_WINDOW concedido es una excepción documentada de apertura en segundo plano.
                startActivity(Intent(target.launchIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        }.getOrDefault(false).also { opened ->
            // The Activity path already records the dispatch after its successful startActivity.
            if (opened && !opensFromActivity) NavigationRequestStore.recordDispatched(this, target, navigationSession)
        }
    }

    private fun renewAppChoiceWindow(token: String) {
        val target = appRequests.current?.target ?: return
        val validity = if (target.draftText == null) PendingAppLaunch.VALIDITY_MS else PendingAppLaunch.DRAFT_VALIDITY_MS
        val request = appRequests.renew(token, SystemClock.elapsedRealtime(), validity) ?: return
        TripTestState.pendingAppExpiresAtMs = request.expiresAtMs
        scheduleAppExpiration(request)
        showAppPrompt(request)
    }

    private fun finishAppChoice(provider: WakeWordProvider, cycle: Int, token: String,
        message: String, cancelRequest: Boolean = false) {
        if (!isCurrentAppChoice(provider, cycle, token)) return
        appChoiceToken = null
        followUpGate.cancel()
        pendingTask?.let(mainHandler::removeCallbacks)
        pendingTask = null
        callPrompt.cancel()
        resumeAfterButtonRoute = capture.cancel(resumePlayback = false, signalCancellation = cancelRequest) || resumeAfterButtonRoute
        if (cancelRequest) clearPendingApp(token)
        TripTestState.status = message
        TripTestState.lastCommandResult = message
        updateNotification(if (cancelRequest) "Apertura cancelada · viaje activo" else "Confirmación terminada · viaje activo")
        schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider && generation == cycle) startDetector(provider) }
    }

    /** Abrir, descartar, vencer o detener cancela también su confirmación por voz. */
    private fun cancelAppChoice(signalCancellation: Boolean = false) {
        if (appChoiceToken == null) return
        appChoiceToken = null
        followUpGate.cancel()
        val cycle = ++generation
        pendingTask?.let(mainHandler::removeCallbacks)
        pendingTask = null
        if (callPromptLazy.isInitialized()) callPrompt.cancel()
        if (speechLazy.isInitialized()) {
            resumeAfterButtonRoute = capture.cancel(resumePlayback = false, signalCancellation = signalCancellation) || resumeAfterButtonRoute
        }
        detector?.let { provider ->
            if (active) schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider && generation == cycle) startDetector(provider) }
        }
    }

    private fun requestCallConfirmation(provider: WakeWordProvider, cycle: Int, contact: TripCallContact) {
        if (!active || detector !== provider || generation != cycle) return
        callConfirmation.begin(cycle)
        TripTestState.interactionPhase = TripInteractionPhase.SPEAKING
        TripTestState.status = "Anunciando destinatario: ${contact.display}…"
        updateNotification("Anunciando a ${contact.name}")
        runCatching { callPrompt.announce(contact.name, direct = BuildConfig.ALLOW_DIRECT_CALL) { announced ->
            if (!active || detector !== provider || generation != cycle) return@announce
            if (!announced) {
                callConfirmation.cancel()
                TripTestState.failures++
                TripTestState.save(this)
                TripTestState.status = "No se pudo anunciar a ${contact.name}; no se hará la llamada."
                updateNotification("Llamada cancelada: fallo de voz")
                schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider && generation == cycle) startDetector(provider) }
                return@announce
            }
            TripTestState.status = "Llamar a ${contact.display}: después de la señal decí ‘confirmar llamada’ o ‘cancelar’."
            updateNotification("Confirmar llamada a ${contact.name}")
            prepareFollowUp(provider, { active && detector === provider && generation == cycle && callConfirmation.isCurrent(cycle) },
                onFailure = { message ->
                    callConfirmation.cancel()
                    TripTestState.status = "Llamada cancelada: $message"
                    TripTestState.lastCommandResult = TripTestState.status
                    schedule(VoiceInteractionTiming.RECOVERY_MS) {
                        if (active && detector === provider && generation == cycle) startDetector(provider)
                    }
                }) {
                capture.listenOnce(mode = SpeechCaptureMode.CONFIRMATION, onState = { state ->
                    if (active && generation == cycle && state == SpeechCaptureState.READY) {
                        TripTestState.interactionPhase = TripInteractionPhase.CONFIRMING
                        TripTestState.status = "Confirmación lista: decí ‘confirmar llamada’ o ‘cancelar’."
                    }
                }, playbackResumePolicy = { _ ->
                    PlaybackResumePolicy.AFTER_ROUTE_RESTORED
                }) { answer ->
                    if (!active || detector !== provider || generation != cycle) return@listenOnce
                    if (!callConfirmation.take(cycle)) return@listenOnce
                    pendingTask?.let(mainHandler::removeCallbacks)
                    pendingTask = null
                    if (answer.getOrNull()?.let(MinimalIntentRouter::resolve) == TestIntent.CONFIRM_CALL) {
                        finishAndPlaceCall(provider, contact)
                    } else {
                        TripTestState.status = "Llamada cancelada; no se marcó a ${contact.name}."
                        TripTestState.lastCommandResult = TripTestState.status
                        resumeAfterButtonRoute = capture.cancel(resumePlayback = false, signalCancellation = true) || resumeAfterButtonRoute
                        updateNotification("Llamada cancelada; esperando activación")
                        schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider && generation == cycle) startDetector(provider) }
                    }
                }
            }
        } }.onSuccess {
            updateNotification("Anunciando a ${contact.name} · Silencio disponible")
        }.onFailure {
            TripTestState.failures++
            TripTestState.save(this)
            TripTestState.status = "No se pudo anunciar el contacto; no se hará la llamada."
            callConfirmation.cancel()
            schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider && generation == cycle) startDetector(provider) }
        }
    }

    @Suppress("DEPRECATION")
    private fun finishAndPlaceCall(provider: WakeWordProvider, contact: TripCallContact) {
        if (!BuildConfig.ALLOW_DIRECT_CALL) {
            buttonCoordinator?.close()
            armed = false
            val callCycle = ++generation
            resumeAfterButtonRoute = capture.cancel(resumePlayback = false) || resumeAfterButtonRoute
            detector = null
            TripTestState.status = "Preparando el marcador para ${contact.display}…"
            worker.execute {
                val released = runCatching { provider.close() }
                mainHandler.post {
                    if (!active || generation != callCycle) return@post
                    pendingDialContact = if (released.isSuccess) contact else null
                    if (released.isFailure) TripTestState.lastCommandResult =
                        "No se pudo liberar el micrófono; marcador sin abrir."
                    // stopSession finishes SCO restoration before dispatching the dialer.
                    stopSession("Viaje en pausa para abrir el marcador; iniciá la llamada en Teléfono.", expected = true)
                }
            }
            return
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            TripTestState.status = "Habilitá el permiso de estado del teléfono en Ajustes → llamadas; no se marcó."
            TripTestState.lastCommandResult = TripTestState.status
            resumeAfterButtonRoute = capture.cancel(resumePlayback = false) || resumeAfterButtonRoute
            schedule(VoiceInteractionTiming.RECOVERY_MS) { if (active && detector === provider) startDetector(provider) }
            return
        }
        buttonCoordinator?.close()
        callPending = true
        callWasActive = false
        armed = false
        val callCycle = ++generation
        resumeAfterButtonRoute = capture.cancel(resumePlayback = false) || resumeAfterButtonRoute
        detector = null
        TripTestState.status = "Viaje en espera; preparando llamada a ${contact.display}…"
        updateNotification("Viaje en espera; solicitud de llamada")
        worker.execute {
            val released = runCatching { provider.close() }
            mainHandler.post {
                if (!active || !callPending || generation != callCycle) return@post
                val placed = runCatching {
                    released.getOrThrow()
                    val listener = object : PhoneStateListener() {
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            if (!active || !callPending || generation != callCycle) return
                            if (state != TelephonyManager.CALL_STATE_IDLE) {
                                callWasActive = true
                                TripTestState.status = "Android informa llamada en curso; viaje en espera."
                            }
                            else if (callWasActive) resumeAfterCall(callCycle)
                        }
                    }
                    phoneListener = listener
                    getSystemService(TelephonyManager::class.java).listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                    if (TripTestState.appVisible) startActivity(CallDispatch.intent(contact.number, direct = true))
                    else getSystemService(TelecomManager::class.java)
                        .placeCall(Uri.fromParts("tel", contact.number, null), Bundle.EMPTY)
                }
                if (placed.isSuccess) {
                    TripTestState.record(this, PipelineEvent.ACTION_DISPATCHED)
                    TripTestState.actions++
                    TripTestState.status = "Solicitud de llamada despachada a ${contact.display}; esperando estado del teléfono."
                    schedule(30_000L) {
                        if (active && callPending && generation == callCycle && !callWasActive) {
                            val idle = runCatching {
                                getSystemService(TelephonyManager::class.java).callState == TelephonyManager.CALL_STATE_IDLE
                            }.getOrDefault(false)
                            if (idle) resumeAfterCall(callCycle)
                        }
                    }
                } else {
                    TripTestState.failures++
                    TripTestState.status = "No se inició la llamada: ${placed.exceptionOrNull()?.message}."
                    resumeAfterCall(callCycle)
                }
                TripTestState.lastCommandResult = TripTestState.status
                TripTestState.save(this)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun stopPhoneMonitoring() {
        phoneListener?.let {
            runCatching { getSystemService(TelephonyManager::class.java).listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        phoneListener = null
    }

    private fun resumeAfterCall(cycle: Int) {
        if (!active || !callPending || generation != cycle) return
        callPending = false
        TripTestState.callPhase = TripCallPhase.RECOVERING
        stopPhoneMonitoring()
        activationGate.reset()
        pendingTask?.let(mainHandler::removeCallbacks)
        pendingTask = null
        TripTestState.status = "Llamada terminada; recuperando modo viaje…"
        updateNotification("Recuperando modo viaje")
        schedule(1_500L) { restoreAfterCall(cycle, 0) }
    }

    private fun restoreAfterCall(cycle: Int, attempt: Int) {
        if (!active || generation != cycle) return
        // IDLE puede llegar antes de que Android libere MODE_IN_CALL y la ruta del teléfono.
        if (audioManager.mode != AudioManager.MODE_NORMAL || !media.isMediaRouteReady()) {
            if (attempt < 15) schedule(1_000L) { restoreAfterCall(cycle, attempt + 1) }
            else stopSession("Android no liberó el audio de la llamada; reiniciá el viaje cuando termine.")
            return
        }
        runCatching { checkNotNull(providerFactory).invoke() }.fold(
            onSuccess = { detector = it; startDetector(it) },
            onFailure = { stopSession("No se pudo recuperar el viaje: ${it.message}.") }
        )
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
            if (active && armed && effectiveAudioMode == WakeAudioMode.BLUETOOTH && !headsetRouteAvailable()) {
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

    private fun stopSession(reason: String, expected: Boolean = false) {
        if (closing) return
        callConfirmation.cancel()
        followUpGate.cancel()
        songCaptureToken = null
        if (active && !expected) {
            TripTestState.sessionProblem = true
            TripTestState.pipeline = TripTestState.pipeline.copy(lastEndReason = reason)
            TripTestState.record(this, PipelineEvent.SESSION_UNEXPECTED_END)
        }
        buttonCoordinator?.close()
        buttonCoordinator = null
        active = false
        draftCaptureToken = null
        mainHandler.removeCallbacks(overlayTick)
        overlay.close()
        TripTestState.switchingMode = false
        stopPhoneMonitoring()
        stopMonitoringHardware()
        closing = true
        TripTestState.restoringAudio = true
        callPending = false
        armed = false
        pendingVolumeCommand = null
        pendingMusicCommand = null
        clearPendingApp()
        generation++
        val stoppedGeneration = generation
        if (callPromptLazy.isInitialized()) callPrompt.cancel()
        pendingTask?.let(mainHandler::removeCallbacks)
        pendingTask = null
        // Conserva la intención de reproducción mientras se libera SCO. Una pausa pedida por
        // el usuario no aparece aquí y permanece pausada.
        val pausedBySpeech = (if (speechLazy.isInitialized()) capture.cancel(resumePlayback = false) else false) ||
            resumeAfterButtonRoute
        resumeAfterButtonRoute = false
        val previous = detector
        detector = null
        val pausedForRoute = if (previous != null &&
            (effectiveAudioMode != WakeAudioMode.BUTTON || !media.isMediaRouteReady())) {
            runCatching { media.pauseIfPlaying() }.getOrDefault(false)
        } else false
        val resumeAfterRoute = pausedBySpeech || pausedForRoute
        val audioCaptureGeneration = TripTestState.audioCaptureGeneration
        TripTestState.running = false
        TripNavigationState.endSession()
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
        TripTestState.record(this, if (routeReady && closeError == null) PipelineEvent.AUDIO_ROUTE_RESTORE_SUCCESS
            else PipelineEvent.AUDIO_ROUTE_RESTORE_FAILURE)
        if (resumeAfterRoute && routeReady) runCatching { media.resumePlayback() }
        TripTestState.status = when {
            closeError != null -> "$reason No se pudo liberar completamente el audio: ${closeError.message}."
            !routeReady -> "$reason La salida de música aún no volvió a la ruta normal; revisá Audio del casco."
            else -> reason
        }
        pendingDialContact?.let { contact ->
            pendingDialContact = null
            if (routeReady && closeError == null) {
                showDialPrompt(contact)
                runCatching { startActivity(CallDispatch.intent(contact.number, direct = false)) }
                    .onSuccess {
                        TripTestState.record(this, PipelineEvent.ACTION_DISPATCHED)
                        TripTestState.actions++
                        TripTestState.lastCommandResult = "Solicitud despachada: abrir marcador para ${contact.display}; llamada sin confirmar. También podés abrirlo desde la notificación."
                    }.onFailure {
                        TripTestState.failures++
                        TripTestState.lastCommandResult = "No se pudo abrir el marcador: ${it.message}."
                    }
            } else TripTestState.lastCommandResult = "Audio sin restaurar; marcador sin abrir."
        }
        TripTestState.save(this)
        closing = false
        TripTestState.restoringAudio = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showDialPrompt(contact: TripCallContact) {
        val launch = PendingIntent.getActivity(this, 7,
            CallDispatch.intent(contact.number, direct = false),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = Notification.Builder(this, APP_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Abrir marcador para ${contact.display}")
            .setContentText("Tocá para iniciar la llamada en Teléfono; APP aún no llamó.")
            .setContentIntent(launch)
            .setAutoCancel(true)
            .setTimeoutAfter(300_000L)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(DIAL_NOTIFICATION_ID, notice) }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Sesión de voz de prueba", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(APP_CHANNEL_ID, "Abrir aplicaciones", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun clearPendingApp(token: String? = null): Boolean {
        if (!appRequests.clear(token)) return false
        cleanAppPrompt()
        return true
    }

    private fun cleanAppPrompt() {
        cancelAppChoice()
        appExpirationTask?.let(mainHandler::removeCallbacks)
        appExpirationTask = null
        appLaunchPendingIntent?.cancel()
        appLaunchPendingIntent = null
        TripTestState.pendingAppTarget = null
        TripTestState.pendingAppExpiresAtMs = 0L
        TripTestState.pendingAppName = null
        TripTestState.pendingDraftText = null
        TripTestState.pendingAppToken = null
        getSystemService(NotificationManager::class.java).cancel(APP_NOTIFICATION_ID)
    }

    private fun preparePendingApp(target: AppTarget) {
        val validity = VoiceInteractionTiming.pendingAppMs(target.spokenLabel, target.draftText)
        val request = appRequests.prepare(target, UUID.randomUUID().toString(), SystemClock.elapsedRealtime(), validity)
        TripTestState.pendingAppTarget = target
        TripTestState.pendingAppExpiresAtMs = request.expiresAtMs
        TripTestState.pendingAppName = target.label
        TripTestState.pendingDraftText = target.draftText
        TripTestState.pendingAppToken = request.token
        scheduleAppExpiration(request)
        showAppPrompt(request)
    }

    private fun scheduleAppExpiration(request: PendingAppLaunch.Request<AppTarget>) {
        appExpirationTask?.let(mainHandler::removeCallbacks)
        val expiration = Runnable {
            if (appRequests.expire(request.token, SystemClock.elapsedRealtime())) {
                cleanAppPrompt()
                TripTestState.lastCommandResult = "La apertura de ${request.target.label} venció; podés pedirla otra vez."
                updateNotification("Apertura vencida · viaje activo")
            }
        }
        appExpirationTask = expiration
        mainHandler.postDelayed(expiration, (request.expiresAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(1L))
    }

    private fun showAppPrompt(request: PendingAppLaunch.Request<AppTarget>) {
        val target = request.target
        val launch = PendingIntent.getActivity(this, 5,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PENDING_APP)
                .setData(Uri.parse("appcopiloto://launch/${request.token}"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_APP_TOKEN, request.token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        appLaunchPendingIntent = launch
        val cancel = PendingIntent.getService(this, 6,
            Intent(this, TripVoiceService::class.java).setAction(ACTION_CANCEL_APP)
                .setData(Uri.parse("appcopiloto://pending/${request.token}"))
                .putExtra(EXTRA_APP_TOKEN, request.token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = Notification.Builder(this, APP_CHANNEL_ID)
            .setOnlyAlertOnce(true)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Abrir ${target.label}")
            .setContentText(if (target.draftText == null) "Tras el anuncio y el tono: 1 abrir · 2 cancelar."
                else "Tras la lectura y el tono: 1 abrir · 2 descartar · 3 dictar de nuevo · 4 repetir. Sin enviar.")
            .setContentIntent(launch)
            .setDeleteIntent(cancel)
            .setAutoCancel(true)
            .setTimeoutAfter((request.expiresAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(1L))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(android.R.drawable.ic_menu_view, "Abrir ${target.label}", launch)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancel)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(APP_NOTIFICATION_ID, notice) }
    }

    private fun buildNotification(detail: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, TripVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(when (wakeAudioMode) {
                WakeAudioMode.BUTTON -> "APP · modo 2, botón"
                WakeAudioMode.PHONE_WAKE -> "APP · modo 3, experimental"
                WakeAudioMode.BLUETOOTH -> "APP · modo 1, voz"
            } + if (manualActivationOnly) " · solo activación manual" else "")
            .setContentText(detail)
            .setContentIntent(open)
            .setOngoing(true)
        if (effectiveAudioMode == WakeAudioMode.BUTTON) {
            val listen = PendingIntent.getService(this, 2,
                Intent(this, TripVoiceService::class.java).setAction(ACTION_MANUAL_COMMAND),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notice.addAction(android.R.drawable.ic_btn_speak_now, "Escuchar orden", listen)
        }
        pendingApp?.let { target ->
            appLaunchPendingIntent?.let { launch ->
                notice.addAction(android.R.drawable.ic_menu_view, "Abrir ${target.label}", launch)
            }
        }
        if (draftCaptureToken != null || appChoiceToken != null || callConfirmation.pending || (callPromptLazy.isInitialized() && callPrompt.isAnnouncing())) {
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
        followUpGate.cancel()
        capture.releaseCue()
        mainHandler.removeCallbacks(overlayTick)
        overlay.close()
        clearPendingApp()
        stopMonitoringHardware()
        if (active) stopSession("Sesión interrumpida por Android; iniciá una nueva prueba.")
        worker.shutdown()
        super.onDestroy()
    }
}



