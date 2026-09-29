package com.gust4av033.appcopilotomoto

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
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
    private var selectedScreen by mutableStateOf(CopilotScreen.TRIP)
    private var result by mutableStateOf<AudioProbeResult?>(null)
    private var pendingAction: (() -> Unit)? = null

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
        savedInstanceState?.let { saved ->
            selectedScreen = runCatching { CopilotScreen.valueOf(saved.getString("ui_screen").orEmpty()) }
                .getOrDefault(CopilotScreen.TRIP)
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
                onRefreshAudio = { audioDiagnostic = media.audioRouteDiagnostic() },
                onSelectAssistant = ::requestAssistantRole,
                onToggleListening = { if (listening) cancelVoiceTest() else requestAudioPermissions(::startVoiceTest) },
                onMeasure = ::requestProbe,
                onToggleWake = { if (wakeEnabled) endWakeTest() else requestAudioPermissions(::startWakeTest) },
                onPhraseChange = { wakePhrase = WakePhrase.valueOf(it) },
                onSensitivityChange = { wakeSensitivity = it },
                onInputChange = { tripAudioMode = WakeAudioMode.valueOf(it) },
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
        val intent = Intent(this, TripVoiceService::class.java).setAction(TripVoiceService.ACTION_START)
            .putExtra(TripVoiceService.EXTRA_PHRASE, wakePhrase.name)
            .putExtra(TripVoiceService.EXTRA_SENSITIVITY, wakeSensitivity)
            .putExtra(TripVoiceService.EXTRA_AUDIO_MODE, tripAudioMode.name)
        runCatching { startForegroundService(intent) }.onFailure {
            TripTestState.status = "No se pudo iniciar la sesión: ${it.message ?: it.javaClass.simpleName}"
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
        if (running) return
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
                        voiceMessage = "Reconocedor listo. Decí una orden de música ahora."
                        if (fromWake) wakeMessage = "Reconocedor listo: decí la orden ahora."
                    }
                    // El motor puede confundir música o ruido con voz: no es una orden reconocida.
                    SpeechCaptureState.SPEECH_ONSET -> Unit
                }
            }, shouldResumePlayback = { heard ->
                heard?.let(MinimalIntentRouter::resolve) != TestIntent.MEDIA_PAUSE
            }) { outcome ->
                if (!listening) return@listenOnce
                listening = false
                val elapsed = SystemClock.elapsedRealtime() - start
                val commandRecognized = outcome.getOrNull()?.let {
                    MinimalIntentRouter.resolve(it) != TestIntent.UNKNOWN
                } ?: false
                voiceMessage = outcome.fold(
                    onSuccess = { heard ->
                        when (MinimalIntentRouter.resolve(heard)) {
                            TestIntent.MEDIA_NEXT, TestIntent.MEDIA_PREVIOUS,
                            TestIntent.MEDIA_PLAY, TestIntent.MEDIA_PAUSE,
                            TestIntent.VOLUME_UP, TestIntent.VOLUME_DOWN -> {
                                val action = when (MinimalIntentRouter.resolve(heard)) {
                                    TestIntent.MEDIA_NEXT -> media.nextTrack()
                                    TestIntent.MEDIA_PREVIOUS -> media.previousTrack()
                                    TestIntent.MEDIA_PLAY -> media.play()
                                    TestIntent.MEDIA_PAUSE -> media.pause()
                                    TestIntent.VOLUME_UP -> media.volumeUp()
                                    TestIntent.VOLUME_DOWN -> media.volumeDown()
                                    else -> error("Orden multimedia no soportada")
                                }
                                val command = when (MinimalIntentRouter.resolve(heard)) {
                                    TestIntent.MEDIA_NEXT -> "Siguiente canción"
                                    TestIntent.MEDIA_PREVIOUS -> "Anterior canción"
                                    TestIntent.MEDIA_PLAY -> "Reproducir"
                                    TestIntent.MEDIA_PAUSE -> "Pausar"
                                    TestIntent.VOLUME_UP -> "Subir volumen (${media.commandVolumeStatus()})"
                                    TestIntent.VOLUME_DOWN -> "Bajar volumen (${media.commandVolumeStatus()})"
                                    else -> "Orden"
                                }
                                if (action.isSuccess) "Escuché: ‘$heard’. Envié $command en $elapsed ms. Comprobá el resultado en la música."
                                else "Escuché: ‘$heard’, pero falló el envío de $command: ${action.exceptionOrNull()?.message}"
                            }
                            TestIntent.STOP_TTS -> "Escuché: ‘$heard’. Orden Silencio reconocida; aún no hay voz sintetizada para interrumpir."
                            TestIntent.CANCEL -> "Escuché: ‘$heard’. Orden Cancelar reconocida; no se ejecutó ninguna acción."
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
        if (wakeEnabled || wakeTransition || listening || running) return
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

