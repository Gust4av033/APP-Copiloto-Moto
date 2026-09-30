package com.gust4av033.appcopilotomoto

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
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
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val speech by lazy { AndroidSpeechToTextProvider(applicationContext) }
    private val media by lazy { MediaActionAdapter(applicationContext) }
    private val mediaCommands by lazy { MediaCommandExecutor(media) }
    private val appLauncher by lazy { AppLaunchAdapter(this) }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private var audioDevices by mutableStateOf(AudioDevicesUi())
    private var audioDeviceMessage = ""
    private val refreshDevicesTask = object : Runnable {
        override fun run() {
            refreshAudioDevices()
            mainHandler.postDelayed(this, 1_000L)
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
    private var assistantMessage by mutableStateOf("Opcional: probá el botón del Y12 como acceso a una orden corta.")
    private var audioDiagnostic by mutableStateOf("Actualizá los niveles con la moto detenida y la sesión activa.")
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
        assistantMessage = if (isSelectedAssistant()) {
            "APP es el asistente elegido. Pulsá el Y12 durante la sesión o probá una orden corta sin sesión."
        } else "APP no quedó como asistente. Gemini sigue disponible; podés volver a probar cuando quieras."
    }

    private val callPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        tripCallMessage = if (granted) "Llamadas habilitadas para el contacto elegido."
        else "Sin permiso de teléfono, las órdenes de llamada no marcarán."
    }

    private val namedCallPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        tripCallMessage = if (listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
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
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                callPermissionRequest.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TripTestState.restore(this)
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
        setContent { AppUi() }
        handleAssistantInvocation(intent)
    }

    /** Único puente entre el runtime de fase 0 y las pantallas Compose. */
    @Composable
    private fun AppUi() {
        val state = CopilotUiState(
            tripActive = TripTestState.running,
            restoringAudio = TripTestState.restoringAudio,
            measuring = running,
            listening = listening,
            wakeEnabled = wakeEnabled,
            wakeTransition = wakeTransition,
            tripStatus = TripTestState.status,
            lastCommandResult = TripTestState.lastCommandResult,
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
            inputId = tripAudioMode.name,
            audioDevices = audioDevices,
            assistantSelected = isSelectedAssistant(),
            assistantMessage = assistantMessage,
            voiceMessage = voiceMessage,
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
        CopilotApp(
            state = state,
            selectedScreen = selectedScreen,
            onScreenChange = { selectedScreen = it },
            actions = CopilotActions(
                onToggleTrip = { if (TripTestState.running) stopTripTest() else requestTripPermissions() },
                onManualTripCommand = ::requestManualTripCommand,
                onRefreshAudio = {
                    audioDiagnostic = runCatching { media.audioRouteDiagnostic() }
                        .getOrDefault(getString(R.string.devices_read_error))
                    refreshAudioDevices()
                },
                onRefreshDevices = ::requestDeviceRefresh,
                onSelectHeadset = ::selectHeadset,
                onOpenAudioControls = ::openAudioControls,
                onSelectAssistant = ::requestAssistantRole,
                onToggleListening = { if (listening) cancelVoiceTest() else requestAudioPermissions(::startVoiceTest) },
                onMeasure = ::requestProbe,
                onToggleWake = { if (wakeEnabled) endWakeTest() else requestAudioPermissions(::startWakeTest) },
                onPhraseChange = { wakePhrase = WakePhrase.valueOf(it) },
                onSensitivityChange = { wakeSensitivity = it },
                onInputChange = {
                    tripAudioMode = WakeAudioMode.valueOf(it)
                    getSharedPreferences("ui_settings", MODE_PRIVATE).edit()
                        .putString("trip_audio_mode", tripAudioMode.name).apply()
                },
                onEnableNamedCalls = ::requestNamedCallPermissions,
                onChooseContact = ::chooseTripCallContact,
                onClearContact = {
                    TripCallContactStore.clear(this@MainActivity)
                    tripCallContact = null
                    tripCallMessage = getString(R.string.contact_removed)
                }
            )
        )
    }

    private fun audioBusy() = TripTestState.running || TripTestState.restoringAudio ||
        running || listening || wakeEnabled || wakeTransition

    private fun requestDeviceRefresh() {
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
            HeadsetSelection.select(device?.let(BluetoothAudioRouteSelector::identity))
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
        audioDevices = runCatching {
            val headsets = BluetoothAudioRouteSelector.availableHeadsets(audioManager)
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
            val outputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching {
                    audioManager.getAudioDevicesForAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                    ).map(::audioDeviceLabel).distinct().joinToString("\n")
                }.getOrDefault("")
            } else ""
            initial.copy(
                headsets = headsets.map { AudioDeviceUi(it.id, audioDeviceLabel(it)) },
                bluetoothOutputs = bluetoothOutputs,
                bluetoothInputs = bluetoothInputs,
                selectedLabel = preferred?.let { chosen ->
                    headsets.singleOrNull { BluetoothAudioRouteSelector.identity(it) == chosen }
                        ?.let(::audioDeviceLabel) ?: chosen.name
                }.orEmpty(),
                selectionMissing = preferred != null && resolved == null,
                automaticHeadset = if (preferred == null) resolved?.let(::audioDeviceLabel).orEmpty() else "",
                communicationRoute = audioManager.communicationDevice?.let(::audioDeviceLabel).orEmpty(),
                musicOutput = outputs,
                musicVolume = media.musicVolume(),
                communicationVolume = "${audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}/${audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}"
            )
        }.getOrElse { initial.copy(message = getString(R.string.devices_read_error)) }
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
        mainHandler.removeCallbacks(refreshDevicesTask)
        mainHandler.post(refreshDevicesTask)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(refreshDevicesTask)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("ui_screen", selectedScreen.name)
        outState.putString("ui_phrase", wakePhrase.name)
        outState.putString("ui_input", tripAudioMode.name)
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
        val source = when (incoming?.action) {
            Intent.ACTION_ASSIST -> "ASSIST"
            Intent.ACTION_VOICE_COMMAND -> "VOICE_COMMAND"
            else -> return
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
            selectedScreen = CopilotScreen.TESTS
            if (wakeEnabled || wakeTransition || running || listening) {
                assistantMessage = "Botón recibido ($source), pero ya hay una escucha activa. Detenela antes de repetir."
            } else {
                assistantMessage = "Botón recibido ($source). Preparando una orden corta por Bluetooth…"
                requestAudioPermissions(::startVoiceTest)
            }
        }
    }

    private fun requestProbe() {
        if (audioBusy()) return
        requestAudioPermissions(::startProbe)
    }

    private fun chooseTripCallContact() {
        runCatching {
            contactPicker.launch(Intent(Intent.ACTION_PICK).apply {
                type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
            })
        }.onFailure {
            tripCallMessage = "No se pudo abrir el selector de contactos: ${it.message ?: "sin app disponible"}."
        }
    }

    private fun requestNamedCallPermissions() {
        val needed = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
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

    private fun stopTripTest() {
        runCatching {
            startService(Intent(this, TripVoiceService::class.java).setAction(TripVoiceService.ACTION_STOP))
        }.onFailure {
            TripTestState.status = "No se pudo detener la sesión: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun startProbe() {
        if (audioBusy()) return
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

    private fun startVoiceTest() {
        if (audioBusy()) return
        startVoiceCommand(fromWake = false)
    }

    private fun startVoiceCommand(fromWake: Boolean) {
        if (listening || running) return
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
                }
            }, playbackResumePolicy = { heard ->
                if (heard?.let(MinimalIntentRouter::resolve) == TestIntent.MEDIA_PAUSE) {
                    PlaybackResumePolicy.KEEP_PAUSED
                } else PlaybackResumePolicy.NOW
            }) { outcome ->
                if (!listening) return@listenOnce
                listening = false
                val elapsed = SystemClock.elapsedRealtime() - start
                val routed = outcome.getOrNull()?.let(MinimalIntentRouter::route)
                val commandRecognized = routed?.intent != null && routed.intent != TestIntent.UNKNOWN
                voiceMessage = outcome.fold(
                    onSuccess = { heard ->
                        val command = checkNotNull(routed)
                        when (command.intent) {
                            TestIntent.MEDIA_NEXT, TestIntent.MEDIA_PREVIOUS,
                            TestIntent.MEDIA_PLAY, TestIntent.MEDIA_PAUSE,
                            TestIntent.VOLUME_UP, TestIntent.VOLUME_DOWN, TestIntent.VOLUME_SET -> {
                                val action = checkNotNull(mediaCommands.execute(command))
                                val label = mediaCommands.description(command.intent, action.getOrNull() as? VolumeAdjustment)
                                if (action.isSuccess) "Escuché: ‘$heard’. $label en $elapsed ms. Comprobá el resultado en la música."
                                else "Escuché: ‘$heard’, pero falló el envío de $label: ${action.exceptionOrNull()?.message}"
                            }
                            TestIntent.STOP_TTS -> "Escuché: ‘$heard’. Orden Silencio reconocida; aún no hay voz sintetizada para interrumpir."
                            TestIntent.CANCEL -> "Escuché: ‘$heard’. Orden Cancelar reconocida; no se ejecutó ninguna acción."
                            TestIntent.OPEN_APP -> appLauncher.find(checkNotNull(command.appName)).fold(
                                onSuccess = { target -> runCatching {
                                    startActivity(target.launchIntent)
                                    if (fromWake) endWakeTest("Prueba de activación detenida al abrir ${target.label}.")
                                }.fold(
                                    onSuccess = { "Abriendo ${target.label}." },
                                    onFailure = { "No se pudo abrir ${target.label}: ${it.message}." }
                                ) },
                                onFailure = { it.message ?: "No se encontró la aplicación." }
                            )
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
        pendingSpeechStart?.let(mainHandler::removeCallbacks)
        pendingSpeechStart = null
        speech.cancel()
        listening = false
        voiceMessage = "Escucha cancelada; no se ejecutó ninguna acción."
        if (wakeEnabled) scheduleWakeResume(2_000L)
    }

    private fun startWakeTest() {
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
