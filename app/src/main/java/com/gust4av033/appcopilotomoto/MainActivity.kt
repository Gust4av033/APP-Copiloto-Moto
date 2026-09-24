package com.gust4av033.appcopilotomoto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
    private var wakeMessage by mutableStateOf("Elegí una frase e iniciá la sesión con la app abierta.")
    private var message by mutableStateOf("Conectá el casco Bluetooth y pulsá Iniciar prueba.")
    private var voiceMessage by mutableStateOf("Iniciá la reproducción en tu app de música antes de probar la orden.")
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TripTestState.restore(this)
        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("APP · Fase 0.0", style = MaterialTheme.typography.headlineMedium)
                        Text("Diagnóstico de micrófono Bluetooth", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                        Text("La prueba escucha cinco segundos. Hablá por el casco; el audio se descarta y solo se muestran medidas.", modifier = Modifier.padding(top = 12.dp))
                        Button(onClick = { requestProbe() }, enabled = !running && !listening && !wakeEnabled && !wakeTransition && !TripTestState.running, modifier = Modifier.padding(top = 20.dp)) {
                            Text(if (running) "Midiendo…" else "Iniciar prueba")
                        }
                        Text(message, modifier = Modifier.padding(top = 16.dp))
                        result?.let { measurement ->
                            Text("Entrada solicitada: ${measurement.requestedInput}", modifier = Modifier.padding(top = 16.dp))
                            Text("Entrada real: ${measurement.actualInput}")
                            Text("Salida de comunicación seleccionada: ${measurement.selectedOutput}")
                            Text("La salida de comunicación no verifica dónde se reproduce la música.", style = MaterialTheme.typography.bodySmall)
                            Text("Duración: ${measurement.durationMs} ms · Muestras: ${measurement.sampleCount}")
                            Text("Nivel medio: ${measurement.rmsDbfs} dBFS · Pico: ${measurement.peakDbfs} dBFS")
                            measurement.warning?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                        }
                        Text("Fase 0.1 · Orden corta", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 28.dp))
                        Text("Pulsá Escuchar, esperá a que diga ‘Reconocedor listo’ y probá ‘siguiente canción’, ‘anterior canción’, ‘reanudar’, ‘pausa’ o ‘detener’.", modifier = Modifier.padding(top = 8.dp))
                        Text("Durante la orden APP pide pausar la música; al terminar solicita reanudarla.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { if (listening) cancelVoiceTest() else requestAudioPermissions(::startVoiceTest) }, enabled = !running && !wakeEnabled && !wakeTransition && !TripTestState.running, modifier = Modifier.padding(top = 12.dp)) {
                            Text(if (listening) "Cancelar escucha" else "Escuchar orden")
                        }
                        Text(voiceMessage, modifier = Modifier.padding(top = 12.dp))
                        Text("Fase 0.2 · Activación local", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 28.dp))
                        Text("Iniciá la sesión, esperá a que diga ‘Esperando’ y decí ‘${wakePhrase.label}’. Cuando aparezca ‘Reconocedor listo’, decí una orden de música; ‘detener sesión’ cierra esta prueba.", modifier = Modifier.padding(top = 8.dp))
                        Text("Frase de activación de prueba", modifier = Modifier.padding(top = 12.dp))
                        Column(modifier = Modifier.selectableGroup()) {
                            WakePhrase.entries.forEach { option ->
                                val canChange = !wakeEnabled && !wakeTransition && !listening && !running && !TripTestState.running
                                Row(
                                    modifier = Modifier.selectable(
                                        selected = wakePhrase == option,
                                        enabled = canChange,
                                        role = Role.RadioButton,
                                        onClick = { wakePhrase = option }
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = wakePhrase == option, onClick = null, enabled = canChange)
                                    Text(option.label, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        Text("«Hola amigo» requiere entrenar un modelo propio; elegir un nombre aquí no cambia lo que escucha el detector.", style = MaterialTheme.typography.bodySmall)
                        Text("Sensibilidad de activación: ${wakeSensitivity.toInt()} %", modifier = Modifier.padding(top = 12.dp))
                        Slider(
                            value = wakeSensitivity,
                            onValueChange = { wakeSensitivity = it },
                            valueRange = 40f..85f,
                            steps = 8,
                            enabled = !wakeEnabled && !wakeTransition && !listening && !running && !TripTestState.running
                        )
                        Text("Más sensibilidad puede detectar más veces la frase elegida, pero también activarse por error. Ajustala con la sesión detenida.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { if (wakeEnabled) endWakeTest() else requestAudioPermissions(::startWakeTest) },
                            enabled = !TripTestState.running && !running && (wakeEnabled || (!listening && !wakeTransition)),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(if (wakeEnabled) "Detener sesión" else "Iniciar activación")
                        }
                        Text(wakeMessage, modifier = Modifier.padding(top = 12.dp))
                        Text("Activaciones detectadas: $wakeDetections")
                        Text("El porcentaje entre paréntesis es la puntuación de una detección, no la tasa de aciertos.", style = MaterialTheme.typography.bodySmall)
                        Text("Esta prueba 0.2 usa la app visible; la sesión 0.3 se controla abajo.", style = MaterialTheme.typography.bodySmall)
                        Text("Fase 0.3 · Sesión con pantalla apagada", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 28.dp))
                        Text("Iniciá la sesión con el casco conectado. Para cada orden decí ‘${wakePhrase.label}’, esperá el tono y luego pedí ‘reanudar’, ‘pausa’, ‘detener’, ‘anterior canción’ o ‘siguiente canción’. Solo ‘detener sesión’ termina la escucha.", modifier = Modifier.padding(top = 8.dp))
                        Text("Micrófono mientras espera Alexa", modifier = Modifier.padding(top = 12.dp))
                        Column(modifier = Modifier.selectableGroup()) {
                            WakeAudioMode.entries.forEach { option ->
                                val canChange = !TripTestState.running && !running && !listening && !wakeEnabled && !wakeTransition
                                Row(
                                    modifier = Modifier.selectable(
                                        selected = tripAudioMode == option,
                                        enabled = canChange,
                                        role = Role.RadioButton,
                                        onClick = { tripAudioMode = option }
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = tripAudioMode == option, onClick = null, enabled = canChange)
                                    Text(option.label, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        Text("En la prueba con teléfono, Alexa debe llegar al micrófono del celular aunque hablés desde el casco. Si va guardado o hay viento, puede no oírte. Solo la orden usa el casco; probalo con la moto detenida.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { if (TripTestState.running) stopTripTest() else requestTripPermissions() },
                            enabled = TripTestState.running || (!running && !listening && !wakeEnabled && !wakeTransition),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(if (TripTestState.running) "Detener sesión de viaje" else "Iniciar sesión de viaje")
                        }
                        Text(TripTestState.status, modifier = Modifier.padding(top = 12.dp))
                        Text("Detecciones: ${TripTestState.detections} · Transcripciones: ${TripTestState.recognized} · Acciones: ${TripTestState.actions} · Fallos: ${TripTestState.failures}")
                        Text("Último tiempo activación → STT listo: ${TripTestState.lastWakeToSttMs} ms · activación → resultado: ${TripTestState.lastLatencyMs} ms", style = MaterialTheme.typography.bodySmall)
                        Text("Mientras espera ‘${wakePhrase.label}’, el detector escucha la frase. Cada orden abre el reconocedor solo unos segundos. El modo casco puede cambiar el sonido de la música durante toda la sesión; el experimental puede hacerlo durante la orden. Podés cerrarla por voz, desde la app o desde la notificación.", style = MaterialTheme.typography.bodySmall)
                        Text("Para medir aciertos y falsas alarmas, anotá por separado cuántas veces dijiste la frase y si hubo alguna activación sin hablar.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    private fun requestProbe() {
        requestAudioPermissions(::startProbe)
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
                            TestIntent.MEDIA_PLAY, TestIntent.MEDIA_PAUSE -> {
                                val action = when (MinimalIntentRouter.resolve(heard)) {
                                    TestIntent.MEDIA_NEXT -> media.nextTrack()
                                    TestIntent.MEDIA_PREVIOUS -> media.previousTrack()
                                    TestIntent.MEDIA_PLAY -> media.play()
                                    else -> media.pause()
                                }
                                val command = when (MinimalIntentRouter.resolve(heard)) {
                                    TestIntent.MEDIA_NEXT -> "Siguiente canción"
                                    TestIntent.MEDIA_PREVIOUS -> "Anterior canción"
                                    TestIntent.MEDIA_PLAY -> "Reproducir"
                                    else -> "Pausar"
                                }
                                if (action.isSuccess) "Escuché: ‘$heard’. Envié $command en $elapsed ms. Comprobá el resultado en la música."
                                else "Escuché: ‘$heard’, pero falló el envío de $command: ${action.exceptionOrNull()?.message}"
                            }
                            TestIntent.STOP_TTS -> "Escuché: ‘$heard’. Orden Silencio reconocida; aún no hay voz sintetizada para interrumpir."
                            TestIntent.CANCEL -> "Escuché: ‘$heard’. Orden Cancelar reconocida; no se ejecutó ninguna acción."
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
