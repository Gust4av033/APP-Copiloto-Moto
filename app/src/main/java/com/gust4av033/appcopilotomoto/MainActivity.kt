package com.gust4av033.appcopilotomoto

import android.Manifest
import android.app.KeyguardManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.Uri
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import android.view.KeyEvent
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.AudioDeviceUi
import com.gust4av033.appcopilotomoto.presentation.AudioDevicesUi
import com.gust4av033.appcopilotomoto.presentation.CopilotApp
import com.gust4av033.appcopilotomoto.presentation.CopilotScreen
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.ProbeUi
import com.gust4av033.appcopilotomoto.presentation.TripMetricsUi
import com.gust4av033.appcopilotomoto.presentation.VoiceRouteUi
import com.gust4av033.appcopilotomoto.presentation.CommandUi
import com.gust4av033.appcopilotomoto.presentation.toCommandUi
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_PENDING_APP = "com.gust4av033.appcopilotomoto.OPEN_PENDING_APP"
        const val ACTION_OPEN_ASSISTANT = "com.gust4av033.appcopilotomoto.OPEN_ASSISTANT"
    }
    private val settingsStore by lazy { UserSettingsStore(this) }
    private val developerChallenge = DeveloperAccessChallenge()
    private var developerEnabled by mutableStateOf(false)
    private var developerConfirmationRequested by mutableStateOf(false)
    private var standaloneCommand by mutableStateOf(false)
    private var configurationNotice by mutableStateOf("")
    private var microphoneGranted by mutableStateOf(false)
    private var bluetoothGranted by mutableStateOf(false)
    private var notificationsGranted by mutableStateOf(false)
    private var callsGranted by mutableStateOf(false)
    private var installedVersion = ""
    private fun canRunDeveloperTools() = developerEnabled && BuildConfig.DEBUG
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    // Las lecturas de presentación no esperan detrás de una medición de cinco segundos.
    private val presentationWorker = Executors.newSingleThreadExecutor()
    private var presentationGeneration = 0
    private var presentationSyncGeneration = 0
    private var deviceRefreshInFlight = false
    private var catalogRefreshInFlight = false
    private var catalogLoadedAt = 0L
    private var assistantSelected by mutableStateOf(false)
    private var appCatalogLoading by mutableStateOf(false)
    private val speech by lazy { AndroidSpeechToTextProvider(applicationContext) }
    private val testReadyCueLazy = lazy { TripReadyCue(applicationContext) }
    private val testReadyCue by testReadyCueLazy
    private val media by lazy { MediaActionAdapter(applicationContext) }
    private val actions by lazy { ActionExecutor(this, media) }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private var audioDevices by mutableStateOf(AudioDevicesUi())
    private var audioDeviceMessage = ""
    private val refreshDevicesTask = object : Runnable {
        override fun run() {
            refreshAudioDevices()
            // Los índices de volumen aún necesitan refresco; las consultas ocurren fuera de UI.
            mainHandler.postDelayed(this, 2_000L)
        }
    }
    private var wakeProvider: WakeWordProvider? = null
    private var wakeResumeTask: Runnable? = null
    private var pendingSpeechStart: Runnable? = null
    private var wakeCycle = 0
    private var consecutiveEmptyCommands = 0
    private val recentWakeDetections = ArrayDeque<Long>()
    private var running by mutableStateOf(false)
    private var listening by mutableStateOf(false)
    private var wakeEnabled by mutableStateOf(false)
    private var wakeTransition by mutableStateOf(false)
    private var wakeDetections by mutableStateOf(0)
    private var wakeSensitivity by mutableFloatStateOf(80f)
    private var wakePhrase by mutableStateOf(WakePhrase.ALEXA)
    private var tripAudioMode by mutableStateOf(WakeAudioMode.BLUETOOTH)
    private var tripCallContact by mutableStateOf<TripCallContact?>(null)
    private var tripCallMessage by mutableStateOf("Para llamar por nombre, habilitá los permisos antes del viaje.")
    private var wakeMessage by mutableStateOf("Elegí una frase e iniciá la sesión con la app abierta.")
    private var message by mutableStateOf("Conectá el casco Bluetooth y pulsá Iniciar prueba.")
    private var voiceMessage by mutableStateOf("Iniciá la reproducción en tu app de música antes de probar la orden.")
    private var appCatalogMessage by mutableStateOf("Buscando las aplicaciones que se pueden abrir…")
    private var appCommands by mutableStateOf<List<String>>(emptyList())
    private var musicPlayers by mutableStateOf<List<MusicPlayer>>(emptyList())
    private var selectedMusicPlayer by mutableStateOf<String?>(null)
    private var musicPlayersMessage by mutableStateOf("")
    private var navigationAvailable by mutableStateOf(false)
    private var navigationProvider by mutableStateOf(NavigationApp.WAZE)
    private var navigationHomeAddress by mutableStateOf("")
    private var navigationWorkAddress by mutableStateOf("")
    private var lastNavigationRequest by mutableStateOf<NavigationDestination?>(null)
    private var chatCapabilities by mutableStateOf<List<ContactChatCapability>>(emptyList())
    private var chatContactsGranted by mutableStateOf(false)
    private var chatIntegrationMessage by mutableStateOf("")
    private var lastTestCommand by mutableStateOf<CommandUi?>(null)
    private var assistantMessage by mutableStateOf("Opcional: probá el botón del Y12 como acceso a una orden corta.")
    private var audioDiagnostic by mutableStateOf("Actualizá los niveles con la moto detenida y la sesión activa.")
    private var overlayEnabled by mutableStateOf(false)
    private var overlayPermission by mutableStateOf(false)
    private var overlayMessage by mutableStateOf("")
    private var tripButtonBinding by mutableStateOf(TripButtonBinding.NONE)
    private var mediaAccessGranted by mutableStateOf(false)
    private var wazeObservationEnabled by mutableStateOf(false)
    private var wazeObservationGranted by mutableStateOf(false)
    private var wazeScreenEnabled by mutableStateOf(false)
    private var wazeScreenGranted by mutableStateOf(false)
    private var manualActivationOnly by mutableStateOf(false)
    private var buttonProbeActive by mutableStateOf(false)
    private var buttonProbeLog by mutableStateOf<List<String>>(emptyList())
    private var buttonProbeMessage by mutableStateOf("")
    private val buttonProbe by lazy { HeadsetButtonProbe(this) { active, log, status ->
        buttonProbeActive = active
        buttonProbeLog = log
        buttonProbeMessage = status
    } }
    private var selectedScreen by mutableStateOf(CopilotScreen.HOME)
    private var result by mutableStateOf<AudioProbeResult?>(null)
    private var pendingAction: (() -> Unit)? = null

    private val audioDevicePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        audioDeviceMessage = if (granted) "" else getString(R.string.devices_permission_denied)
        refreshAudioDevices()
    }

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val action = pendingAction
        pendingAction = null
        if (permissions.isNotEmpty() && permissions.values.all { it }) action?.invoke()
        else {
            message = "Se necesita permiso de micrófono y, en Android 12 o posterior, de Bluetooth para esta prueba."
            voiceMessage = message
            TripTestState.status = "Se necesitan los permisos solicitados para mostrar la notificación y usar el micrófono del casco."
        }
    }

    private val assistantRoleRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        assistantSelected = isSelectedAssistant()
        assistantMessage = if (assistantSelected) {
            "APP es el asistente elegido. Pulsá el Y12 durante la sesión o probá una orden corta sin sesión."
        } else "APP no quedó como asistente. Gemini sigue disponible; podés volver a probar cuando quieras."
    }

    private val callPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        tripCallMessage = if (listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
                .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) "Llamadas habilitadas para el contacto elegido."
        else "Sin permiso de teléfono, las órdenes de llamada no marcarán."
    }

    private val chatContactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        chatContactsGranted = granted
        chatIntegrationMessage = if (granted) "Contactos habilitados para abrir chats por nombre."
            else "Sin contactos no se pueden resolver chats por nombre. Podés habilitarlos en Ajustes de Android."
    }

    private val namedCallPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        tripCallMessage = if (listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
                .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            "Listo: podés decir ‘llama a [nombre]’ durante la sesión."
        } else "Faltan permisos de contactos o llamadas; podés seguir usando música y volumen."
    }

    private val contactPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val selected = runCatching {
            val columns = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(columns[0]))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(columns[1]))
                    TripCallContactStore.save(this, name.orEmpty(), number.orEmpty())
                }
            }
        }.getOrNull()
        if (selected == null) {
            tripCallMessage = "No se pudo guardar ese número. Elegí un contacto con teléfono válido."
        } else {
            tripCallContact = selected
            tripCallMessage = "Contacto de prueba: ${selected.display}."
            if (listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
                    .any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
                callPermissionRequest.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
            }
        }
    }

    private var keepScreenOnDuringTrip by mutableStateOf(true)
    // La apertura por voz requiere una Activity visible y el teléfono desbloqueado.
    private val voiceAppOpener: (String) -> Boolean = { token ->
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
            openPendingApp(token)
    }

    private val voiceMusicSearcher: (MusicSearchRequest) -> Result<Unit> = { request ->
        runCatching {
            check(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                !getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                "${request.player.label} requiere APP visible y el teléfono desbloqueado."
            }
            actions.sendMusicSearch(request).getOrThrow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TripTestState.restore(this)
        HeadsetSelection.restore(this)
        developerEnabled = settingsStore.developerEnabled(BuildConfig.DEBUG)
        installedVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() }.getOrDefault("")
        manualActivationOnly = getSharedPreferences("ui_settings", MODE_PRIVATE)
            .getBoolean(TripActivationPolicy.PREFERENCE, false)
        selectedMusicPlayer = actions.preferredMusicPlayer()
        keepScreenOnDuringTrip = getSharedPreferences("ui_settings", MODE_PRIVATE)
            .getBoolean("keep_screen_on_during_trip", true)
        tripCallContact = TripCallContactStore.load(this)
        tripAudioMode = runCatching {
            WakeAudioMode.valueOf(getSharedPreferences("ui_settings", MODE_PRIVATE)
                .getString("trip_audio_mode", WakeAudioMode.BLUETOOTH.name).orEmpty())
        }.getOrDefault(WakeAudioMode.BLUETOOTH)
        savedInstanceState?.let { saved ->
            selectedScreen = runCatching { CopilotScreen.valueOf(saved.getString("ui_screen").orEmpty()) }
                .getOrDefault(CopilotScreen.HOME)
            wakePhrase = runCatching { WakePhrase.valueOf(saved.getString("ui_phrase").orEmpty()) }
                .getOrDefault(WakePhrase.ALEXA)
            tripAudioMode = runCatching { WakeAudioMode.valueOf(saved.getString("ui_input").orEmpty()) }
                .getOrDefault(WakeAudioMode.BLUETOOTH)
            wakeSensitivity = saved.getFloat("ui_sensitivity", 80f).coerceIn(40f, 85f)
        }
        val voiceSettings = settingsStore.loadVoice(savedInstanceState?.getString("ui_phrase"),
            savedInstanceState?.takeIf { it.containsKey("ui_sensitivity") }?.getFloat("ui_sensitivity"))
        wakePhrase = WakePhrase.valueOf(voiceSettings.first)
        wakeSensitivity = voiceSettings.second
        if (!canRunDeveloperTools() && (tripAudioMode == WakeAudioMode.PHONE_WAKE || manualActivationOnly)) {
            tripAudioMode = if (manualActivationOnly) WakeAudioMode.BUTTON else WakeAudioMode.BLUETOOTH
            manualActivationOnly = false
            getSharedPreferences("ui_settings", MODE_PRIVATE).edit().putString("trip_audio_mode", tripAudioMode.name)
                .putBoolean(TripActivationPolicy.PREFERENCE, false).apply()
            configurationNotice = getString(R.string.settings_experimental_reset)
        }
        setContent { AppUi() }
        handleAssistantInvocation(intent)
    }

    /** Único puente entre el runtime de fase 1 y las pantallas Compose. */
    @Composable
    private fun AppUi() {
        val state = CopilotUiState(
            developerAccess = DeveloperAccess(developerEnabled, BuildConfig.DEBUG),
            developerConfirmationRequested = developerConfirmationRequested,
            installedVersion = installedVersion,
            configurationNotice = configurationNotice,
            microphoneGranted = microphoneGranted,
            bluetoothGranted = bluetoothGranted,
            notificationsGranted = notificationsGranted,
            callsGranted = callsGranted,
            standaloneListening = standaloneCommand && listening,
            interactionPhase = TripTestState.interactionPhase,
            sessionProblem = TripTestState.sessionProblem,
            tripActive = TripTestState.running,
            keepScreenOnDuringTrip = keepScreenOnDuringTrip,
            restoringAudio = TripTestState.restoringAudio,
            commandReady = TripTestState.commandReady,
            callPhase = TripTestState.callPhase,
            measuring = running,
            listening = listening,
            wakeEnabled = wakeEnabled,
            wakeTransition = wakeTransition,
            tripStatus = TripTestState.status,
            lastCommandResult = TripTestState.lastCommandResult,
            pendingAppName = TripTestState.pendingAppName,
            pendingDraftText = TripTestState.pendingDraftText,
            pendingAppToken = TripTestState.pendingAppToken,
            lastTripCommand = TripTestState.lastRoutedCommand?.toCommandUi(),
            lastTestCommand = lastTestCommand,
            metrics = TripMetricsUi(
                detections = TripTestState.detections,
                manualCommands = TripTestState.manualCommands,
                recognized = TripTestState.recognized,
                actions = TripTestState.actions,
                failures = TripTestState.failures,
                readyLatencyMs = TripTestState.lastWakeToSttMs,
                totalLatencyMs = TripTestState.lastLatencyMs
            ),
            phraseId = wakePhrase.name,
            phraseLabel = wakePhrase.label,
            sensitivity = wakeSensitivity,
            inputId = if (TripTestState.running) TripTestState.audioMode.name else tripAudioMode.name,
            switchingMode = TripTestState.switchingMode,
            manualActivationOnly = if (TripTestState.running) TripTestState.manualActivationOnly else manualActivationOnly,
            tripButtonBinding = tripButtonBinding.name,
            mediaAccessGranted = mediaAccessGranted,
            pipelineMetrics = TripTestState.pipeline,
            buttonProbeActive = buttonProbeActive,
            buttonProbeLog = buttonProbeLog,
            buttonProbeMessage = buttonProbeMessage,
            overlayEnabled = overlayEnabled,
            overlayPermissionGranted = overlayPermission,
            overlayHidden = TripTestState.overlayHidden,
            overlayMessage = overlayMessage.ifBlank { TripTestState.overlayMessage },
            audioCaptureGeneration = TripTestState.audioCaptureGeneration,
            audioDevices = audioDevices,
            assistantSelected = assistantSelected,
            assistantMessage = assistantMessage,
            voiceMessage = voiceMessage,
            speechLanguages = SpeechLanguageSupport.snapshot,
            appCatalogMessage = appCatalogMessage,
            appCatalogLoading = appCatalogLoading,
            appCommands = appCommands,
            musicPlayers = musicPlayers,
            selectedMusicPlayer = selectedMusicPlayer,
            musicPlayersMessage = musicPlayersMessage,
            navigationAvailable = navigationAvailable,
            navigationProvider = navigationProvider,
            navigationHomeAddress = navigationHomeAddress,
            navigationWorkAddress = navigationWorkAddress,
            wazeObservationEnabled = wazeObservationEnabled,
            wazeObservationGranted = wazeObservationGranted,
            wazeObservation = WazeNotificationObservation.snapshot,
            wazeScreenEnabled = wazeScreenEnabled,
            wazeScreenGranted = wazeScreenGranted,
            wazeScreen = WazeScreenState.snapshot,
            navigationContext = TripNavigationState.context,
            lastNavigationRequest = lastNavigationRequest?.label.orEmpty(),
            chatCapabilities = chatCapabilities,
            chatContactsGranted = chatContactsGranted,
            chatIntegrationMessage = chatIntegrationMessage,
            probeMessage = message,
            wakeMessage = wakeMessage,
            wakeDetections = wakeDetections,
            audioDiagnostic = audioDiagnostic,
            audioBefore = TripTestState.audioBefore,
            audioDuring = TripTestState.audioDuring,
            audioAfter = TripTestState.audioAfter,
            callMessage = tripCallMessage,
            fixedContact = tripCallContact?.display,
            probe = result?.let {
                ProbeUi(it.requestedInput, it.actualInput, it.selectedOutput, it.durationMs,
                    it.sampleCount, it.rmsDbfs, it.peakDbfs, it.warning)
            }
        )
        LaunchedEffect(state.tripActive, state.switchingMode) {
            if (!state.switchingMode) syncTripPresentation()
        }
        DisposableEffect(state.tripActive, state.keepScreenOnDuringTrip, state.overlayEnabled,
            state.overlayPermissionGranted, state.overlayHidden) {
            val bubbleActive = state.overlayEnabled && state.overlayPermissionGranted && !state.overlayHidden
            if (state.tripActive && (state.keepScreenOnDuringTrip || bubbleActive)) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        CopilotApp(
            state = state,
            selectedScreen = selectedScreen,
            onScreenChange = {
                if (it != CopilotScreen.TESTS && buttonProbeActive) buttonProbe.stop()
                selectedScreen = if (it == CopilotScreen.TESTS && !developerEnabled) CopilotScreen.DIAGNOSTICS else it
            },
            actions = CopilotActions(
                onVersionTap = {
                    if (!developerEnabled && !audioBusy() && developerChallenge.tap(SystemClock.elapsedRealtime()))
                        developerConfirmationRequested = true
                },
                onConfirmDeveloper = {
                    if (!audioBusy() && developerChallenge.confirm()) {
                        settingsStore.setDeveloperEnabled(BuildConfig.DEBUG, true)
                        developerEnabled = true
                    }
                    developerConfirmationRequested = false
                },
                onCancelDeveloper = { developerChallenge.reset(); developerConfirmationRequested = false },
                onDisableDeveloper = {
                    if (!audioBusy()) {
                        developerEnabled = false
                        settingsStore.setDeveloperEnabled(BuildConfig.DEBUG, false)
                        WazeObservationAccess.setEnabled(this@MainActivity, false)
                        wazeObservationEnabled = false
                        WazeScreenAccess.setEnabled(this@MainActivity, false)
                        wazeScreenEnabled = false
                        if (tripAudioMode == WakeAudioMode.PHONE_WAKE || manualActivationOnly) {
                            tripAudioMode = if (manualActivationOnly) WakeAudioMode.BUTTON else WakeAudioMode.BLUETOOTH
                            manualActivationOnly = false
                            getSharedPreferences("ui_settings", MODE_PRIVATE).edit().putString("trip_audio_mode", tripAudioMode.name)
                                .putBoolean(TripActivationPolicy.PREFERENCE, false).apply()
                            configurationNotice = getString(R.string.settings_experimental_reset)
                        }
                        developerChallenge.reset()
                        developerConfirmationRequested = false
                        selectedScreen = CopilotScreen.SETTINGS
                    }
                },
                onOpenBasicDiagnostics = { selectedScreen = CopilotScreen.DIAGNOSTICS },
                onOpenAppPermissions = {
                    if (!audioBusy()) runCatching {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }.onFailure { configurationNotice = getString(R.string.settings_android_unavailable) }
                },
                onCopyBasicDiagnostics = { report ->
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(
                        getString(R.string.settings_basic_help), report))
                },
                onCancelStandaloneCommand = ::cancelVoiceTest,
                onToggleTrip = { if (TripTestState.running) stopTripTest() else requestTripPermissions() },
                onManualTripCommand = ::requestManualTripCommand,
                onCancelPendingApp = ::cancelPendingApp,
                onOpenPendingApp = { openPendingApp(it) },
                onKeepScreenOnChange = { enabled ->
                    keepScreenOnDuringTrip = enabled
                    getSharedPreferences("ui_settings", MODE_PRIVATE).edit()
                        .putBoolean("keep_screen_on_during_trip", enabled).apply()
                },
                onCopyDiagnostics = { report ->
                    if (developerEnabled) {
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newPlainText(getString(R.string.report_title),
                            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n" +
                                "${java.time.Instant.now()}\n$report")
                    )
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(this@MainActivity, R.string.report_copied, Toast.LENGTH_SHORT).show()
                    }
                    }
                },
                onRefreshAudio = {
                    if (developerEnabled) {
                    audioDiagnostic = runCatching { media.audioRouteDiagnostic() }
                        .getOrDefault(getString(R.string.devices_read_error))
                    refreshAudioDevices()
                    }
                },
                onRefreshDevices = ::requestDeviceRefresh,
                onSelectHeadset = ::selectHeadset,
                onOpenAudioControls = ::openAudioControls,
                onSelectAssistant = ::requestAssistantRole,
                onRefreshSpeechLanguages = { if (!audioBusy()) SpeechLanguageSupport.refresh(applicationContext, force = true) },
                onPrepareEnglish = { if (!audioBusy()) SpeechLanguageSupport.prepareEnglish(applicationContext) },
                onToggleListening = { if (canRunDeveloperTools()) { if (listening) cancelVoiceTest() else requestAudioPermissions(::startVoiceTest) } },
                onRefreshApps = { refreshAppCatalog(force = true) },
                onEnableChatContacts = {
                    if (!TripTestState.running && !TripTestState.restoringAudio && !listening && !running && !wakeEnabled && !wakeTransition) {
                        chatContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }
                },
                onMusicPlayerChange = { selected ->
                    if (!TripTestState.running && !TripTestState.restoringAudio && !listening && !wakeEnabled && !wakeTransition && !running) {
                        actions.selectMusicPlayer(selected)
                        selectedMusicPlayer = selected
                    }
                },
                onMeasure = ::requestProbe,
                onToggleWake = { if (canRunDeveloperTools()) { if (wakeEnabled) endWakeTest() else requestAudioPermissions(::startWakeTest) } },
                onPhraseChange = { id ->
                    if (!audioBusy()) runCatching { WakePhrase.valueOf(id) }.getOrNull()?.let {
                        wakePhrase = it; settingsStore.saveVoice(it.name, wakeSensitivity)
                    }
                },
                onSensitivityChange = { value ->
                    if (!audioBusy() && value.isFinite()) {
                        wakeSensitivity = value.coerceIn(40f, 85f)
                        settingsStore.saveVoice(wakePhrase.name, wakeSensitivity)
                    }
                },
                onInputChange = ::selectTripMode,
                onManualActivationOnlyChange = ::selectManualActivationOnly,
                onTripButtonBindingChange = { id ->
                    if (!audioBusy() && !buttonProbeActive) {
                        tripButtonBinding = TripButtonBinding.parse(id)
                        getSharedPreferences("ui_settings", MODE_PRIVATE).edit()
                            .putString(TripButtonBinding.PREFERENCE, tripButtonBinding.name).apply()
                    }
                },
                onMediaAccessSettings = {
                    if (!audioBusy()) {
                    runCatching { startActivity(TripMediaAccess.settingsIntent(this)) }
                        .recoverCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        .onFailure { assistantMessage = "No se pudieron abrir los ajustes de acceso multimedia." }
                    }
                },
                onTestObservation = { event ->
                    if (canRunDeveloperTools() && event in listOf(PipelineEvent.ACTIVATION_ATTEMPT_ANNOTATED, PipelineEvent.FALSE_POSITIVE_ANNOTATED,
                            PipelineEvent.EXTERNAL_EFFECT_ANNOTATED, PipelineEvent.AUDIO_RESTORED_AUDIBLE_ANNOTATED))
                        TripTestState.record(this, event)
                },
                onToggleButtonProbe = {
                    if (buttonProbeActive) buttonProbe.stop()
                    else if (canRunDeveloperTools() && !audioBusy()) buttonProbe.start()
                },
                onOpenButtonProbe = { selectedScreen = if (developerEnabled) CopilotScreen.TESTS else CopilotScreen.DIAGNOSTICS },
                onForgetNavigationRequest = {
                    if (!audioBusy()) {
                        NavigationRequestStore.forget(this@MainActivity, navigationProvider)
                        lastNavigationRequest = null
                    }
                },
                onNavigationProviderChange = { id ->
                    if (!audioBusy() && !appCatalogLoading) {
                        val selected = NavigationApp.fromId(id)
                        NavigationPreferences.select(this@MainActivity, selected)
                        navigationProvider = selected
                        lastNavigationRequest = NavigationRequestStore.last(this@MainActivity, selected)
                        navigationAvailable = false
                        refreshAppCatalog(force = true)
                    }
                },
                onNavigationAddressSave = { destination, address ->
                    if (!audioBusy()) runCatching {
                        NavigationPreferences.saveAddress(this@MainActivity, destination, address)
                    }.onSuccess {
                        if (destination == NavigationDestination.HOME) navigationHomeAddress = NavigationPreferences.address(this@MainActivity, destination)
                        else navigationWorkAddress = NavigationPreferences.address(this@MainActivity, destination)
                    }.onFailure { configurationNotice = it.message.orEmpty() }
                },
                onWazeObservationChange = { enabled ->
                    if (canRunDeveloperTools() && !audioBusy()) {
                        WazeObservationAccess.setEnabled(this@MainActivity, enabled)
                        wazeObservationEnabled = enabled
                    }
                },
                onWazeScreenChange = { enabled ->
                    if (canRunDeveloperTools() && !audioBusy()) {
                        WazeScreenAccess.setEnabled(this@MainActivity, enabled)
                        wazeScreenEnabled = enabled
                    }
                },
                onWazeScreenPermission = {
                    if (canRunDeveloperTools() && wazeScreenEnabled && !audioBusy()) startActivity(WazeScreenAccess.settingsIntent())
                },
                onWazeObservationPermission = {
                    if (canRunDeveloperTools() && wazeObservationEnabled && !audioBusy())
                        startActivity(WazeObservationAccess.settingsIntent(this@MainActivity))
                },
                onOverlayEnabledChange = { enabled ->
                    TripOverlayPreferences.setEnabled(this@MainActivity, enabled)
                    overlayEnabled = enabled
                    overlayMessage = ""
                    if (enabled && !Settings.canDrawOverlays(this@MainActivity)) requestOverlayPermission()
                },
                onOverlayPermission = ::requestOverlayPermission,
                onShowOverlay = {
                    if (TripTestState.running) runCatching {
                        startService(Intent(this@MainActivity, TripVoiceService::class.java).setAction(TripVoiceService.ACTION_SHOW_OVERLAY))
                    }.onFailure { overlayMessage = getString(R.string.overlay_error) }
                },
                onEnableNamedCalls = ::requestNamedCallPermissions,
                onChooseContact = ::chooseTripCallContact,
                onClearContact = {
                    if (!audioBusy()) {
                    TripCallContactStore.clear(this@MainActivity)
                    tripCallContact = null
                    tripCallMessage = getString(R.string.contact_removed)
                    }
                }
            )
        )
    }

    private fun audioBusy() = TripTestState.running || TripTestState.restoringAudio ||
        running || listening || wakeEnabled || wakeTransition || buttonProbeActive

    private fun requestDeviceRefresh() {
        if (audioBusy()) return
        audioDeviceMessage = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            audioDevicePermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else refreshAudioDevices()
    }

    private fun selectHeadset(id: Int?) {
        if (audioBusy() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val outcome = runCatching {
            val device = id?.let { requested ->
                BluetoothAudioRouteSelector.availableHeadsets(audioManager).singleOrNull { it.id == requested }
                    ?: error(getString(R.string.devices_selection_lost))
            }
            // Solo configura la próxima captura; no abre micrófonos ni cambia música.
            HeadsetSelection.saveChoice(this, device?.let(BluetoothAudioRouteSelector::identity))
            result = null
            message = getString(R.string.devices_probe_again)
        }
        audioDeviceMessage = outcome.exceptionOrNull()?.message.orEmpty()
        refreshAudioDevices()
    }

    private fun openAudioControls() {
        if (audioBusy()) return
        val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_VOLUME
            else Settings.ACTION_SOUND_SETTINGS
        val outcome = runCatching { startActivity(Intent(panel)) }
        if (outcome.isFailure) {
            runCatching { startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }.onFailure {
                audioDeviceMessage = getString(R.string.devices_panel_error)
                refreshAudioDevices()
            }
        }
    }

    private fun refreshAudioDevices() {
        if (isDestroyed || presentationWorker.isShutdown || deviceRefreshInFlight) return
        val generation = presentationGeneration
        val preferred = HeadsetSelection.preferred
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val granted = !supported ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val initial = AudioDevicesUi(
            supported = supported, permissionGranted = granted,
            selectedId = preferred?.id, selectedLabel = preferred?.name.orEmpty(),
            message = audioDeviceMessage
        )
        if (!supported || !granted) {
            audioDevices = initial
            return
        }
        deviceRefreshInFlight = true
        presentationWorker.execute {
            val snapshot = runCatching {
                val headsets = BluetoothAudioRouteSelector.availableHeadsets(audioManager)
                HeadsetControlStore.forgetDisconnected(headsets.map(BluetoothAudioRouteSelector::identity).toSet())
                val bluetoothOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                    .map(::audioDeviceLabel).distinct()
                val bluetoothInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                    .map(::audioDeviceLabel).distinct()
                val resolved = BluetoothAudioRouteSelector.communicationHeadset(audioManager)
                val controlHeadset = resolved?.let(BluetoothAudioRouteSelector::identity)
                val controls = HeadsetControlStore.read(this, controlHeadset)
                val outputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching {
                        audioManager.getAudioDevicesForAttributes(
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                        ).map(::audioDeviceLabel).distinct().joinToString("\n")
                    }.getOrDefault("")
                } else ""
                val communicationDevice = audioManager.communicationDevice
                initial.copy(
                    headsets = headsets.map { AudioDeviceUi(it.id, it.productName.toString(), audioDeviceLabel(it)) },
                    selectedId = if (preferred == null) null else resolved?.id ?: preferred.id,
                    bluetoothOutputs = bluetoothOutputs,
                    bluetoothInputs = bluetoothInputs,
                    selectedLabel = preferred?.let { chosen ->
                        resolved?.productName?.toString() ?: chosen.name
                    }.orEmpty(),
                    selectionMissing = preferred != null && resolved == null,
                    automaticHeadset = if (preferred == null) resolved?.productName?.toString().orEmpty() else "",
                    controlHeadsetName = controlHeadset?.name?.ifBlank { "Casco Bluetooth" }.orEmpty(),
                    observedControlKeys = controls.mediaKeys,
                    assistantInvocationObserved = controls.assistantInvocation,
                    controlProfilePersistent = controlHeadset?.let { HeadsetControlEvidence.persistentId(it) } != null,
                    communicationRoute = communicationDevice?.let(::audioDeviceLabel).orEmpty(),
                    voiceRoute = when (communicationDevice?.type) {
                        null -> VoiceRouteUi.NONE
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> VoiceRouteUi.SCO
                        AudioDeviceInfo.TYPE_BLE_HEADSET -> VoiceRouteUi.LE
                        else -> VoiceRouteUi.OTHER
                    },
                    musicOutput = outputs,
                    musicVolume = media.musicVolume(),
                    communicationVolume = "${audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}/${audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}"
                )
            }.getOrElse { initial.copy(message = getString(R.string.devices_read_error)) }
            mainHandler.post {
                deviceRefreshInFlight = false
                if (!isDestroyed && generation == presentationGeneration) {
                    // Una selección cambiada mientras se leía el sistema exige otra lectura.
                    if (preferred == HeadsetSelection.preferred) audioDevices = snapshot
                    else refreshAudioDevices()
                }
            }
        }
    }

    private fun audioDeviceLabel(device: AudioDeviceInfo): String {
        val profile = when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> getString(R.string.devices_profile_sco)
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "A2DP"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> getString(R.string.devices_profile_le)
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> getString(R.string.devices_phone_speaker)
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> getString(R.string.devices_phone_earpiece)
            else -> getString(R.string.devices_profile_other, device.type)
        }
        return getString(R.string.devices_device_label, device.productName, profile, device.id)
    }

    override fun onResume() {
        super.onResume()
        microphoneGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        bluetoothGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        callsGranted = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        TripTestState.appVisible = true
        syncTripPresentation()
        TripTestState.openPendingAppFromVoice = voiceAppOpener
        TripTestState.playMusicFromVoice = voiceMusicSearcher
        chatContactsGranted = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (chatContactsGranted) chatIntegrationMessage = ""
        val languageRefreshGeneration = presentationGeneration
        mainHandler.postDelayed({
            if (!isDestroyed && languageRefreshGeneration == presentationGeneration && !audioBusy()) {
                SpeechLanguageSupport.refresh(applicationContext)
            }
        }, 500L)
        refreshAppCatalog()
        mainHandler.removeCallbacks(refreshDevicesTask)
        mainHandler.postDelayed(refreshDevicesTask, 250L)
    }

    private fun syncTripPresentation() {
        if (isDestroyed || presentationWorker.isShutdown) return
        val generation = ++presentationSyncGeneration
        val activityGeneration = presentationGeneration
        val prefs = getSharedPreferences("ui_settings", MODE_PRIVATE)
        navigationProvider = NavigationPreferences.selected(this)
        navigationHomeAddress = NavigationPreferences.address(this, NavigationDestination.HOME)
        navigationWorkAddress = NavigationPreferences.address(this, NavigationDestination.WORK)
        lastNavigationRequest = NavigationRequestStore.last(this, navigationProvider)
        wazeObservationEnabled = WazeObservationAccess.enabled(this)
        wazeObservationGranted = WazeObservationAccess.granted(this)
        wazeScreenEnabled = WazeScreenAccess.enabled(this)
        wazeScreenGranted = WazeScreenAccess.granted(this)
        if (!WazeScreenAccess.allowed(this)) WazeScreenState.clear()
        if (!WazeObservationAccess.allowed(this)) WazeNotificationObservation.clearSample()
        tripButtonBinding = TripButtonBinding.parse(prefs.getString(TripButtonBinding.PREFERENCE, null))
        manualActivationOnly = if (TripTestState.running) TripTestState.manualActivationOnly else
            prefs.getBoolean(TripActivationPolicy.PREFERENCE, false)
        overlayEnabled = TripOverlayPreferences.enabled(this)
        tripAudioMode = if (TripTestState.running) TripTestState.audioMode else runCatching {
            WakeAudioMode.valueOf(prefs.getString("trip_audio_mode", tripAudioMode.name).orEmpty())
        }.getOrDefault(tripAudioMode)
        // Secure Settings y RoleManager pueden hacer lecturas Binder: no retrasar la primera composición.
        presentationWorker.execute {
            val assistant = runCatching { isSelectedAssistant() }.getOrDefault(false)
            val mediaGranted = runCatching { TripMediaAccess.granted(this) }.getOrDefault(false)
            val canOverlay = Settings.canDrawOverlays(this)
            mainHandler.post {
                if (isDestroyed || activityGeneration != presentationGeneration || generation != presentationSyncGeneration) return@post
                assistantSelected = assistant
                mediaAccessGranted = mediaGranted
                overlayPermission = canOverlay
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) { syncTripPresentation(); return }
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }.recoverCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }.onFailure { overlayMessage = getString(R.string.overlay_permission_error) }
    }

    private fun selectTripMode(id: String) {
        val mode = runCatching { WakeAudioMode.valueOf(id) }.getOrNull() ?: return
        if (audioBusy() || (mode == WakeAudioMode.PHONE_WAKE && !canRunDeveloperTools())) return
        if (mode != WakeAudioMode.PHONE_WAKE) {
            manualActivationOnly = false
            getSharedPreferences("ui_settings", MODE_PRIVATE).edit().putBoolean(TripActivationPolicy.PREFERENCE, false).apply()
        }
        if (TripTestState.running) {
            if (!TripTestState.commandReady || TripTestState.switchingMode || TripTestState.restoringAudio) return
            runCatching {
                startService(Intent(this, TripVoiceService::class.java).setAction(TripVoiceService.ACTION_CHANGE_MODE)
                    .putExtra(TripVoiceService.EXTRA_AUDIO_MODE, mode.name))
            }.onFailure { TripTestState.lastCommandResult = "No se pudo cambiar de modo: ${it.message}." }
        } else if (!audioBusy()) {
            tripAudioMode = mode
            getSharedPreferences("ui_settings", MODE_PRIVATE).edit().putString("trip_audio_mode", mode.name).apply()
        }
    }

    private fun selectManualActivationOnly(enabled: Boolean) {
        if (!canRunDeveloperTools() || audioBusy()) return
        if (TripTestState.running) {
            if (!TripTestState.commandReady || TripTestState.switchingMode || TripTestState.restoringAudio ||
                TripTestState.callPhase != TripCallPhase.NONE) return
            runCatching {
                startService(Intent(this, TripVoiceService::class.java).setAction(TripVoiceService.ACTION_CHANGE_MODE)
                    .putExtra(TripVoiceService.EXTRA_AUDIO_MODE, TripTestState.audioMode.name)
                    .putExtra(TripVoiceService.EXTRA_MANUAL_ONLY, enabled))
            }.onFailure { TripTestState.lastCommandResult = "No se pudo cambiar la activación: ${it.message}." }
        } else if (!audioBusy()) {
            manualActivationOnly = enabled
            getSharedPreferences("ui_settings", MODE_PRIVATE).edit()
                .putBoolean(TripActivationPolicy.PREFERENCE, enabled).apply()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (buttonProbeActive && buttonProbe.recordActivityKey(event)) return true
        if (TripButtonRuntime.handler?.invoke(event) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (buttonProbeActive && buttonProbe.recordActivityKey(event)) return true
        if (TripButtonRuntime.handler?.invoke(event) == true) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onStop() {
        if (buttonProbeActive) buttonProbe.stop("Prueba detenida al salir de APP; controles multimedia liberados.")
        super.onStop()
    }

    private fun refreshAppCatalog(force: Boolean = false) {
        if (isDestroyed || presentationWorker.isShutdown || catalogRefreshInFlight) return
        // Reanudar tras una orden no debe volver a enumerar todas las aplicaciones.
        if (!force && catalogLoadedAt != 0L && SystemClock.elapsedRealtime() - catalogLoadedAt < 30_000L) return
        catalogRefreshInFlight = true
        appCatalogLoading = true
        val generation = presentationGeneration
        presentationWorker.execute {
            val available = actions.availableApps()
            val commands = available.mapCatching(AppNameMatcher::catalogCommands)
            val players = actions.availableMusicPlayers()
            val navigation = runCatching { actions.navigationAvailable() }.getOrDefault(false)
            val chats = runCatching { actions.contactChatCapabilities() }
            mainHandler.post {
                catalogRefreshInFlight = false
                if (isDestroyed || generation != presentationGeneration) return@post
                appCatalogLoading = false
                catalogLoadedAt = SystemClock.elapsedRealtime()
                navigationAvailable = navigation
                chatCapabilities = chats.getOrDefault(emptyList())
                chats.exceptionOrNull()?.let { chatIntegrationMessage = "No se pudieron consultar integraciones: ${it.message}." }
                musicPlayers = players.getOrDefault(emptyList())
                musicPlayersMessage = players.exceptionOrNull()?.let { "No se pudieron consultar reproductores: ${it.message}." }.orEmpty()
                appCommands = commands.getOrDefault(emptyList())
                appCatalogMessage = if (commands.isSuccess)
                    "${appCommands.size} accesos disponibles. Decí el comando como aparece aquí."
                else "No se pudo leer la lista de aplicaciones: ${commands.exceptionOrNull()?.message}."
            }
        }
    }

    private fun releaseVoiceAppOpener() {
        if (TripTestState.playMusicFromVoice === voiceMusicSearcher) TripTestState.playMusicFromVoice = null
        if (TripTestState.openPendingAppFromVoice === voiceAppOpener) {
            TripTestState.openPendingAppFromVoice = null
        }
    }

    override fun onPause() {
        developerChallenge.reset()
        developerConfirmationRequested = false
        TripTestState.appVisible = false
        releaseVoiceAppOpener()
        mainHandler.removeCallbacks(refreshDevicesTask)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("ui_screen", selectedScreen.name)
        outState.putString("ui_phrase", wakePhrase.name)
        outState.putString("ui_input", if (TripTestState.running) TripTestState.audioMode.name else tripAudioMode.name)
        outState.putFloat("ui_sensitivity", wakeSensitivity)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAssistantInvocation(intent)
    }

    private fun isSelectedAssistant(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roles = getSystemService(RoleManager::class.java)
        return roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
            roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }

    private fun requestAssistantRole() {
        if (audioBusy()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            assistantMessage = "Esta prueba necesita Android 10 o posterior."
            return
        }
        val roles = getSystemService(RoleManager::class.java)
        when {
            !roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT) ->
                assistantMessage = "Este teléfono no ofrece la selección de asistente a APP."
            roles.isRoleHeld(RoleManager.ROLE_ASSISTANT) ->
                assistantMessage = "APP ya es el asistente elegido. Probá la pulsación larga del Y12."
            else -> runCatching {
                assistantRoleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
            }.onFailure {
                assistantMessage = "No se pudo abrir la selección de asistente: ${it.message ?: "error de Android"}."
            }
        }
    }

    private fun handleAssistantInvocation(incoming: Intent?) {
        if (incoming?.action == ACTION_OPEN_ASSISTANT) {
            setIntent(Intent(this, MainActivity::class.java))
            selectedScreen = CopilotScreen.TRIP
            return
        }
        if (incoming?.action == ACTION_OPEN_PENDING_APP) {
            setIntent(Intent(this, MainActivity::class.java))
            selectedScreen = CopilotScreen.TRIP
            incoming.getStringExtra(TripVoiceService.EXTRA_APP_TOKEN)?.let(::openPendingApp)
            return
        }
        val source = when (incoming?.action) {
            Intent.ACTION_ASSIST -> "ASSIST"
            Intent.ACTION_VOICE_COMMAND -> "VOICE_COMMAND"
            else -> return
        }
        if (buttonProbeActive || HeadsetButtonProbe.isAssistantGuarded()) {
            setIntent(Intent(this, MainActivity::class.java))
            selectedScreen = CopilotScreen.TESTS
            buttonProbe.recordAssistant(source)
            return
        }
        // Consumir el intent: una recreación de la pantalla no debe iniciar otra escucha.
        setIntent(Intent(this, MainActivity::class.java))
        if (TripTestState.running) {
            selectedScreen = CopilotScreen.TRIP
            assistantMessage = "Botón recibido ($source) durante el viaje."
            runCatching {
                startService(Intent(this, TripVoiceService::class.java)
                    .setAction(TripVoiceService.ACTION_MANUAL_COMMAND))
            }.onFailure {
                TripTestState.status = "No se pudo abrir la orden del botón: ${it.message ?: "error de Android"}."
            }
        } else {
            selectedScreen = CopilotScreen.TRIP
            if (wakeEnabled || wakeTransition || running || listening) {
                assistantMessage = "Botón recibido ($source), pero ya hay una escucha activa. Detenela antes de repetir."
            } else {
                assistantMessage = "Botón recibido ($source). Preparando una orden corta por Bluetooth…"
                requestAudioPermissions(::startAssistantCommand)
            }
        }
    }

    private fun requestProbe() {
        if (!canRunDeveloperTools() || audioBusy()) return
        requestAudioPermissions(::startProbe)
    }

    private fun chooseTripCallContact() {
        if (audioBusy()) return
        runCatching {
            contactPicker.launch(Intent(Intent.ACTION_PICK).apply {
                type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
            })
        }.onFailure {
            tripCallMessage = "No se pudo abrir el selector de contactos: ${it.message ?: "sin app disponible"}."
        }
    }

    private fun requestNamedCallPermissions() {
        if (audioBusy()) return
        val needed = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) tripCallMessage = "Los permisos ya están concedidos: decí ‘llama a [nombre]’."
        else namedCallPermissions.launch(needed.toTypedArray())
    }

    private fun requestAudioPermissions(onGranted: () -> Unit) {
        val required = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) required += Manifest.permission.BLUETOOTH_CONNECT
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) onGranted() else {
            pendingAction = onGranted
            permissionRequest.launch(missing.toTypedArray())
        }
    }

    private fun requestTripPermissions() {
        if (audioBusy()) return
        val required = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) required += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) required += Manifest.permission.POST_NOTIFICATIONS
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startTripTest() else {
            pendingAction = ::startTripTest
            permissionRequest.launch(missing.toTypedArray())
        }
    }

    private fun startTripTest() {
        if (audioBusy()) return
        val intent = Intent(this, TripVoiceService::class.java).setAction(TripVoiceService.ACTION_START)
            .putExtra(TripVoiceService.EXTRA_PHRASE, wakePhrase.name)
            .putExtra(TripVoiceService.EXTRA_SENSITIVITY, wakeSensitivity)
            .putExtra(TripVoiceService.EXTRA_AUDIO_MODE, tripAudioMode.name)
            .putExtra(TripVoiceService.EXTRA_MANUAL_ONLY, manualActivationOnly)
        runCatching { startForegroundService(intent) }.onFailure {
            TripTestState.status = "No se pudo iniciar la sesión: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun requestManualTripCommand() {
        if (!TripTestState.running) return
        runCatching {
            startService(Intent(this, TripVoiceService::class.java)
                .setAction(TripVoiceService.ACTION_MANUAL_COMMAND))
        }.onFailure {
            TripTestState.status = "No se pudo abrir la orden: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun openPendingApp(token: String): Boolean {
        val target = TripTestState.pendingAppTarget
        if (!TripTestState.running || TripTestState.pendingAppToken != token || target == null ||
            SystemClock.elapsedRealtime() >= TripTestState.pendingAppExpiresAtMs) {
            return false
        }
        return actions.openAppFromActivity(target).fold(
            onSuccess = {
                // Impide otra apertura mientras el servicio recibe la confirmación.
                TripTestState.pendingAppExpiresAtMs = 0L
                runCatching {
                    startService(Intent(this, TripVoiceService::class.java)
                        .setAction(TripVoiceService.ACTION_APP_OPENED)
                        .putExtra(TripVoiceService.EXTRA_APP_TOKEN, token))
                }
                true
            },
            onFailure = {
                TripTestState.lastCommandResult = "No se pudo abrir ${target.label}: ${it.message}."
                false
            }
        )
    }

    private fun cancelPendingApp(token: String) {
        if (!TripTestState.running) return
        runCatching {
            startService(Intent(this, TripVoiceService::class.java)
                .setAction(TripVoiceService.ACTION_CANCEL_APP)
                .putExtra(TripVoiceService.EXTRA_APP_TOKEN, token))
        }.onFailure { TripTestState.lastCommandResult = "No se pudo cancelar la apertura: ${it.message}." }
    }

    private fun stopTripTest() {
        runCatching {
            startService(Intent(this, TripVoiceService::class.java).setAction(TripVoiceService.ACTION_STOP))
        }.onFailure {
            TripTestState.status = "No se pudo detener la sesión: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun startProbe() {
        if (!canRunDeveloperTools() || audioBusy()) return
        result = null
        running = true
        message = "Escuchando cinco segundos…"
        worker.execute {
            val outcome = runCatching { AudioRouteProbe(applicationContext).run() }
            runOnUiThread {
                result = outcome.getOrNull()
                message = if (outcome.isSuccess) "Prueba finalizada." else "Falló la prueba: ${outcome.exceptionOrNull()?.message ?: "Error desconocido"}"
                running = false
            }
        }
    }

    private fun startAssistantCommand() {
        if (audioBusy()) return
        standaloneCommand = true
        startVoiceCommand(fromWake = false)
    }

    private fun startVoiceTest() {
        if (!canRunDeveloperTools() || audioBusy()) return
        startVoiceCommand(fromWake = false)
    }

    private fun startVoiceCommand(fromWake: Boolean) {
        if ((!canRunDeveloperTools() && !standaloneCommand) || listening || running) return
        lastTestCommand = null
        listening = true
        voiceMessage = "Esperando un momento antes de abrir el micrófono…"
        val start = SystemClock.elapsedRealtime()
        val task = Runnable {
            pendingSpeechStart = null
            if (!listening || (fromWake && !wakeEnabled)) return@Runnable
            speech.listenOnce(onState = { state ->
                if (listening) when (state) {
                    SpeechCaptureState.READY -> {
                        voiceMessage = "Reconocedor listo. Decí una orden de música, volumen o abrir aplicación."
                        if (fromWake) wakeMessage = "Reconocedor listo: decí la orden ahora."
                    }
                    // El motor puede confundir música o ruido con voz: no es una orden reconocida.
                    SpeechCaptureState.SPEECH_ONSET -> Unit
                    SpeechCaptureState.RESTARTING -> {
                        voiceMessage = "Reabriendo confirmación tras silencio; esperá a que esté lista."
                    }
                }
            }, readySignal = { testReadyCue.play().map { Unit } }, playbackResumePolicy = { heard ->
                when (heard?.let(MinimalIntentRouter::resolve)) {
                    TestIntent.MEDIA_PAUSE -> PlaybackResumePolicy.KEEP_PAUSED
                    TestIntent.MEDIA_SEARCH -> PlaybackResumePolicy.AFTER_ROUTE_RESTORED
                    else -> PlaybackResumePolicy.NOW
                }
            }) { outcome ->
                if (!listening) return@listenOnce
                testReadyCue.cancel()
                listening = false
                standaloneCommand = false
                val elapsed = SystemClock.elapsedRealtime() - start
                val routed = outcome.getOrNull()?.let(MinimalIntentRouter::route)
                lastTestCommand = routed?.toCommandUi()
                val commandRecognized = routed?.intent != null && routed.intent != TestIntent.UNKNOWN
                voiceMessage = outcome.fold(
                    onSuccess = { heard ->
                        val command = checkNotNull(routed)
                        when (command.intent) {
                            TestIntent.MEDIA_NEXT, TestIntent.MEDIA_PREVIOUS,
                            TestIntent.MEDIA_PLAY, TestIntent.MEDIA_PAUSE,
                            TestIntent.VOLUME_UP, TestIntent.VOLUME_DOWN, TestIntent.VOLUME_SET -> {
                                val action = checkNotNull(actions.executeMedia(command))
                                val label = actions.description(command.intent, action.getOrNull() as? VolumeAdjustment)
                                if (action.isSuccess) "Escuché: ‘$heard’. $label en $elapsed ms. Comprobá el resultado en la música."
                                else "Escuché: ‘$heard’, pero falló el envío de $label: ${action.exceptionOrNull()?.message}"
                            }
                            TestIntent.MEDIA_SEARCH -> {
                                val pausedBySpeech = speech.cancel(resumePlayback = false)
                                if (command.musicTitleInEnglish) {
                                    if (pausedBySpeech) runCatching { media.resumePlayback() }
                                    return@fold "La escucha del título en inglés está disponible durante el viaje: decí ‘busca canción en inglés’."
                                }
                                val sent = actions.prepareMusicSearch(command).mapCatching { request ->
                                    val result = if (request.player.backgroundSearch) actions.sendMusicSearch(request)
                                        else voiceMusicSearcher(request)
                                    result.getOrThrow()
                                    if (fromWake && !request.player.backgroundSearch) endWakeTest("Búsqueda enviada a ${request.player.label}.")
                                    request
                                }
                                if (sent.isFailure && pausedBySpeech) runCatching { media.resumePlayback() }
                                sent.fold(
                                    onSuccess = { "Búsqueda ‘${it.query}’ enviada a ${it.player.label}. Comprobá la canción que reproduzca." },
                                    onFailure = { "No se envió la búsqueda: ${it.message}." }
                                )
                            }
                            TestIntent.SWITCH_MODE -> "Se reconoció modo ${command.audioMode?.modeNumber}; el cambio por voz se ejecuta durante una sesión de viaje."
                            TestIntent.OPEN_ASSISTANT -> { selectedScreen = CopilotScreen.TRIP; "APP abierta." }
                            TestIntent.STOP_TTS -> "Escuché: ‘$heard’. Orden Silencio reconocida; aún no hay voz sintetizada para interrumpir."
                            TestIntent.CANCEL -> "Escuché: ‘$heard’. Orden Cancelar reconocida; no se ejecutó ninguna acción."
                            TestIntent.OPEN_APP -> actions.prepareApp(command).fold(
                                onSuccess = { target -> runCatching {
                                    actions.openAppFromActivity(target).getOrThrow()
                                    if (fromWake) endWakeTest("Prueba de activación detenida al abrir ${target.label}.")
                                }.fold(
                                    onSuccess = { "Abriendo ${target.label}." },
                                    onFailure = { "No se pudo abrir ${target.label}: ${it.message}." }
                                ) },
                                onFailure = { it.message ?: "No se encontró la aplicación." }
                            )
                            TestIntent.SEARCH_PLACE -> "Búsqueda interpretada; se confirma en el viaje. No se abrió ningún navegador."
                            TestIntent.NEXT_TURN -> actions.nextTurnAnswer()
                            TestIntent.NAVIGATE -> "Destino interpretado; la ruta y su confirmación se prueban durante el viaje. No se abrió ningún navegador."
                            TestIntent.OPEN_CONTACT_CHAT -> "Escuché: ‘$heard’. Los chats con confirmación por voz se prueban en la sesión de viaje."
                            TestIntent.DRAFT_CONTACT_MESSAGE -> "Borrador interpretado; dictado y confirmación se prueban en el viaje. No se abrió ni envió un mensaje."
                            TestIntent.CALL_SELECTED, TestIntent.CALL_NAMED, TestIntent.CONFIRM_CALL -> "Escuché: ‘$heard’. Las llamadas por nombre se prueban en la sesión de viaje."
                            TestIntent.STOP_SESSION -> "Escuché: ‘$heard’. ${if (fromWake) "Deteniendo sesión de activación." else "No hay sesión de activación en esta prueba manual."}"
                            TestIntent.UNKNOWN -> "Escuché: ‘$heard’. No coincide con las órdenes de esta prueba; no se ejecutó nada."
                        }
                    },
                    onFailure = { "No se reconoció la orden: ${it.message ?: "error desconocido"}. Tiempo: $elapsed ms." }
                )
                if (fromWake && wakeEnabled) {
                    if (outcome.getOrNull()?.let(MinimalIntentRouter::resolve) == TestIntent.STOP_SESSION) {
                        endWakeTest("Sesión detenida por voz: ‘${outcome.getOrNull()}’.")
                        return@listenOnce
                    }
                    consecutiveEmptyCommands = if (commandRecognized) 0 else consecutiveEmptyCommands + 1
                    if (consecutiveEmptyCommands >= 2) {
                        endWakeTest("Sesión detenida: dos activaciones seguidas sin una orden reconocida. Revisá el resultado de voz y reiniciá la prueba; si se activó sola, bajá la sensibilidad.")
                    } else {
                        scheduleWakeResume(if (commandRecognized) 2_000L else 3_500L)
                    }
                }
            }
        }
        pendingSpeechStart = task
        mainHandler.postDelayed(task, 700L)
    }

    private fun cancelVoiceTest() {
        if (testReadyCueLazy.isInitialized()) testReadyCue.cancel()
        pendingSpeechStart?.let(mainHandler::removeCallbacks)
        pendingSpeechStart = null
        speech.cancel()
        listening = false
        standaloneCommand = false
        voiceMessage = "Escucha cancelada; no se ejecutó ninguna acción."
        if (wakeEnabled) scheduleWakeResume(2_000L)
    }

    private fun startWakeTest() {
        if (!canRunDeveloperTools() || audioBusy()) return
        if (audioBusy()) return
        wakeEnabled = true
        wakeTransition = true
        wakeDetections = 0
        consecutiveEmptyCommands = 0
        recentWakeDetections.clear()
        wakeMessage = "Preparando el detector local…"
        val provider = OpenWakeWordProvider(applicationContext, threshold = (100f - wakeSensitivity) / 100f, wakePhrase = wakePhrase)
        wakeProvider = provider
        val cycle = ++wakeCycle
        worker.execute {
            val outcome = provider.start { score -> onWakeDetected(score, cycle) }
            runOnUiThread {
                if (wakeEnabled && wakeProvider === provider) {
                    if (outcome.isSuccess) armWakeDetector(provider, cycle)
                    else {
                        wakeMessage = "Falló el detector: ${outcome.exceptionOrNull()?.message}"
                        endWakeTest()
                    }
                }
            }
        }
    }

    private fun onWakeDetected(score: Float, cycle: Int) {
        if (!wakeEnabled || wakeTransition || listening || cycle != wakeCycle) return
        val now = SystemClock.elapsedRealtime()
        while (recentWakeDetections.isNotEmpty() && now - recentWakeDetections.first() > 12_000L) {
            recentWakeDetections.removeFirst()
        }
        if (recentWakeDetections.size >= 3) {
            endWakeTest("Sesión detenida: varias activaciones en pocos segundos. Si ocurrieron sin decir la frase, bajá la sensibilidad antes de reiniciar.")
            return
        }
        recentWakeDetections.addLast(now)
        wakeCycle++ // Invalida detecciones del micrófono anterior que llegaron tarde.
        wakeTransition = true
        wakeDetections++
        wakeMessage = "Activación #$wakeDetections (${(score * 100).toInt()} %). Preparando reconocimiento…"
        val provider = wakeProvider ?: return
        worker.execute {
            val outcome = runCatching { provider.pause() }
            runOnUiThread {
                if (wakeEnabled && wakeProvider === provider) {
                    if (outcome.isSuccess) {
                        wakeMessage = "Micrófono de activación liberado. Preparando reconocimiento…"
                        val activeCycle = wakeCycle
                        mainHandler.postDelayed({
                            if (wakeEnabled && wakeProvider === provider && wakeCycle == activeCycle && !listening) {
                                wakeTransition = false
                                startVoiceCommand(fromWake = true)
                            }
                        }, 350L)
                    } else {
                        wakeMessage = "Falló la pausa del detector: ${outcome.exceptionOrNull()?.message}"
                        endWakeTest()
                    }
                }
            }
        }
    }

    private fun armWakeDetector(provider: WakeWordProvider, cycle: Int) {
        wakeTransition = true
        wakeMessage = "Estabilizando audio; esperá antes de decir ‘${provider.phrase}’…"
        mainHandler.postDelayed({
            if (wakeEnabled && wakeProvider === provider && wakeCycle == cycle && !listening) {
                wakeTransition = false
                wakeMessage = "Esperando ‘${provider.phrase}’…"
            }
        }, 1_500L)
    }

    private fun scheduleWakeResume(delayMs: Long) {
        val provider = wakeProvider ?: return
        wakeResumeTask?.let(mainHandler::removeCallbacks)
        wakeTransition = true
        val waitLabel = if (delayMs == 2_000L) "2" else "3,5"
        wakeMessage = "Esperando $waitLabel segundos antes de volver a escuchar ‘${provider.phrase}’…"
        val task = Runnable {
            wakeResumeTask = null
            if (wakeEnabled && wakeProvider === provider && !listening) resumeWakeTest()
        }
        wakeResumeTask = task
        mainHandler.postDelayed(task, delayMs)
    }

    private fun resumeWakeTest() {
        val provider = wakeProvider ?: return
        wakeTransition = true
        val cycle = ++wakeCycle
        worker.execute {
            val outcome = provider.start { score -> onWakeDetected(score, cycle) }
            runOnUiThread {
                if (wakeEnabled && wakeProvider === provider) {
                    if (outcome.isSuccess) armWakeDetector(provider, cycle)
                    else {
                        wakeMessage = "No se reanudó el detector: ${outcome.exceptionOrNull()?.message}"
                        endWakeTest()
                    }
                }
            }
        }
    }

    private fun endWakeTest(reason: String? = null) {
        wakeResumeTask?.let(mainHandler::removeCallbacks)
        wakeResumeTask = null
        wakeCycle++
        wakeEnabled = false
        wakeTransition = true
        pendingSpeechStart?.let(mainHandler::removeCallbacks)
        pendingSpeechStart = null
        if (listening) {
            speech.cancel()
            listening = false
        }
        val provider = wakeProvider
        wakeProvider = null
        wakeMessage = "Deteniendo sesión…"
        worker.execute {
            val outcome = runCatching { provider?.close() }
            runOnUiThread {
                wakeTransition = false
                wakeMessage = if (outcome.isSuccess) reason ?: "Sesión detenida. Activaciones: $wakeDetections."
                else "La sesión terminó con error: ${outcome.exceptionOrNull()?.message}"
            }
        }
    }

    override fun onDestroy() {
        if (testReadyCueLazy.isInitialized()) testReadyCue.cancel()
        presentationGeneration++
        presentationWorker.shutdown()
        if (buttonProbeActive) buttonProbe.stop()
        releaseVoiceAppOpener()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mainHandler.removeCallbacks(refreshDevicesTask)
        wakeResumeTask?.let(mainHandler::removeCallbacks)
        wakeResumeTask = null
        wakeCycle++
        pendingSpeechStart?.let(mainHandler::removeCallbacks)
        pendingSpeechStart = null
        if (listening) speech.cancel()
        wakeEnabled = false
        val provider = wakeProvider
        wakeProvider = null
        if (provider != null) worker.execute { runCatching { provider.close() } }
        worker.shutdown()
        super.onDestroy()
    }
}



