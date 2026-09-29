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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors

private enum class AppScreen(val label: String) {
    TRIP("Viaje"), TESTS("Pruebas"), SETTINGS("Ajustes")
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

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
    private var selectedScreen by mutableStateOf(AppScreen.TRIP)
    private var detailsVisible by mutableStateOf(false)
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
        setContent { AppUi() }
        handleAssistantInvocation(intent)
    }

    @Composable
    private fun AppUi() {
        val busy = running || listening || wakeEnabled || wakeTransition || TripTestState.running
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Color(0xFF5C478F),
                background = Color(0xFFF7F5FA),
                surface = Color.White,
                onSurface = Color(0xFF25212B)
            )
        ) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
                        Text("APP · Copiloto Moto", style = MaterialTheme.typography.headlineSmall)
                        Text("Validación manos libres", color = MaterialTheme.colorScheme.primary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppScreen.entries.forEach { screen ->
                            if (selectedScreen == screen) {
                                Button(onClick = { selectedScreen = screen }, modifier = Modifier.weight(1f)) {
                                    Text(screen.label)
                                }
                            } else {
                                OutlinedButton(onClick = { selectedScreen = screen }, modifier = Modifier.weight(1f)) {
                                    Text(screen.label)
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        when (selectedScreen) {
                            AppScreen.TRIP -> {
                                SectionCard("Sesión de viaje") {
                                    Text(if (TripTestState.running) "Activa · esperando ‘${wakePhrase.label}’" else "Detenida · lista para iniciar")
                                    Text(TripTestState.status, style = MaterialTheme.typography.bodyMedium)
                                    Button(
                                        onClick = { if (TripTestState.running) stopTripTest() else requestTripPermissions() },
                                        enabled = TripTestState.running || !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (TripTestState.running) "Detener sesión" else "Iniciar sesión de viaje") }
                                    Text("Decí ‘${wakePhrase.label}’ o mantené pulsado el botón central del Y12. Esperá a que APP indique «Reconocedor listo» antes de decir la orden.", style = MaterialTheme.typography.bodySmall)
                                }
                                SectionCard("Órdenes disponibles") {
                                    Text("Música: siguiente, anterior, pausa y reanudar. También podés subir o bajar volumen.")
                                    Text("Llamadas: ‘llama a [nombre]’ o ‘llamar’ al contacto fijo. APP pedirá confirmación hablada antes de marcar.")
                                    Text("‘Detener sesión’ cierra el micrófono. Ajustá la frase, la sensibilidad y los contactos en Ajustes.", style = MaterialTheme.typography.bodySmall)
                                }
                                SectionCard("Resultados del viaje") {
                                    Text("Alexa: ${TripTestState.detections} · Y12: ${TripTestState.manualCommands} · ${TripTestState.actions} acciones · ${TripTestState.failures} fallos")
                                     Text("Último resultado: ${TripTestState.lastCommandResult}", style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { detailsVisible = !detailsVisible }) {
                                        Text(if (detailsVisible) "Ocultar detalles" else "Ver detalles")
                                    }
                                    if (detailsVisible) {
                                        Text("Transcripciones: ${TripTestState.recognized}")
                                        Text("Inicio de orden → STT listo: ${TripTestState.lastWakeToSttMs} ms")
                                        Text("Inicio de orden → resultado: ${TripTestState.lastLatencyMs} ms")
                                        Text("La puntuación del detector no mide la tasa de aciertos.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            AppScreen.TESTS -> {
                                SectionCard("Botón del casco · sin decir Alexa") {
                                    Text("Tu Y12 abre el asistente del teléfono al mantener pulsado el botón central. Elegí APP como asistente para probar si Android le entrega esa pulsación.")
                                    Text("Con la sesión detenida, el botón permite probar música y volumen. Durante una sesión activa, permite también ‘llama a [nombre]’, con confirmación hablada.", style = MaterialTheme.typography.bodySmall)
                                    Button(
                                        onClick = { requestAssistantRole() },
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (isSelectedAssistant()) "APP es el asistente elegido" else "Elegir APP como asistente") }
                                    Text(assistantMessage, style = MaterialTheme.typography.bodySmall)
                                    Text("Sin sesión de viaje, APP abre el micrófono solo al recibir el botón. Con sesión activa, el detector sigue esperando la frase.", style = MaterialTheme.typography.bodySmall)
                                }
                                SectionCard("Orden corta · prueba manual") {
                                    Text("Escuchá una sola orden por Bluetooth. La música se pausa mientras Android reconoce tu voz.")
                                    Button(
                                        onClick = { if (listening) cancelVoiceTest() else requestAudioPermissions(::startVoiceTest) },
                                        enabled = !running && !wakeEnabled && !wakeTransition && !TripTestState.running,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (listening) "Cancelar escucha" else "Escuchar orden") }
                                    Text(voiceMessage)
                                }
                                SectionCard("Micrófono Bluetooth · 5 segundos") {
                                    Button(
                                        onClick = { requestProbe() },
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (running) "Midiendo…" else "Medir micrófono") }
                                    Text(message)
                                    result?.let { measurement ->
                                        Text("Solicitado: ${measurement.requestedInput}")
                                        Text("Real: ${measurement.actualInput}")
                                        Text("Ruta de comunicación: ${measurement.selectedOutput}")
                                        Text("${measurement.durationMs} ms · ${measurement.sampleCount} muestras")
                                        Text("Medio: ${measurement.rmsDbfs} dBFS · pico: ${measurement.peakDbfs} dBFS")
                                        measurement.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                                SectionCard("Activación local · app visible") {
                                    Text("Prueba el detector sin iniciar la sesión de viaje. Decí ‘${wakePhrase.label}’ y luego una orden cuando el reconocedor esté listo.")
                                    Button(
                                        onClick = { if (wakeEnabled) endWakeTest() else requestAudioPermissions(::startWakeTest) },
                                        enabled = !TripTestState.running && !running && (wakeEnabled || (!listening && !wakeTransition)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (wakeEnabled) "Detener activación" else "Iniciar activación") }
                                    Text(wakeMessage)
                                    Text("Detecciones: $wakeDetections", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            AppScreen.SETTINGS -> {
                                SectionCard("Frase de activación") {
                                    Column(modifier = Modifier.selectableGroup()) {
                                        WakePhrase.entries.forEach { option ->
                                            val enabled = !busy
                                            Row(
                                                modifier = Modifier.fillMaxWidth().selectable(
                                                    selected = wakePhrase == option,
                                                    enabled = enabled,
                                                    role = Role.RadioButton,
                                                    onClick = { wakePhrase = option }
                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(selected = wakePhrase == option, onClick = null, enabled = enabled)
                                                Text(option.label, modifier = Modifier.padding(start = 8.dp))
                                            }
                                        }
                                    }
                                    Text("Una frase nueva como ‘Hola amigo’ necesita un modelo entrenado para reconocerla.", style = MaterialTheme.typography.bodySmall)
                                    Text("Sensibilidad: ${wakeSensitivity.toInt()} %")
                                    Slider(
                                        value = wakeSensitivity,
                                        onValueChange = { wakeSensitivity = it },
                                        valueRange = 40f..85f,
                                        steps = 8,
                                        enabled = !busy
                                    )
                                    Text("Más sensibilidad también puede causar activaciones por error.", style = MaterialTheme.typography.bodySmall)
                                }
                                SectionCard("Micrófono para la activación") {
                                    Column(modifier = Modifier.selectableGroup()) {
                                        WakeAudioMode.entries.forEach { option ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().selectable(
                                                    selected = tripAudioMode == option,
                                                    enabled = !busy,
                                                    role = Role.RadioButton,
                                                    onClick = { tripAudioMode = option }
                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(selected = tripAudioMode == option, onClick = null, enabled = !busy)
                                                Text(option.label, modifier = Modifier.padding(start = 8.dp))
                                            }
                                        }
                                    }
                                    Text("El teléfono no resultó fiable en tu ruta. Usá el casco para Alexa; la música puede sonar como llamada mientras el micrófono Bluetooth clásico está abierto.", style = MaterialTheme.typography.bodySmall)
                                }
                                SectionCard("Llamadas") {
                                    Text("Para ‘llama a [nombre]’, concedé acceso a contactos y llamadas antes del viaje. Si el nombre o número es ambiguo, APP no marca.")
                                    Button(onClick = { requestNamedCallPermissions() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                        Text("Habilitar llamadas por nombre")
                                    }
                                    Text("Opcional: elegí un contacto fijo para la orden ‘llamar’.", style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { chooseTripCallContact() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (tripCallContact == null) "Elegir contacto fijo" else "Cambiar contacto fijo")
                                    }
                                    tripCallContact?.let { contact ->
                                        Text("Contacto fijo: ${contact.display}")
                                        OutlinedButton(onClick = {
                                            TripCallContactStore.clear(this@MainActivity)
                                            tripCallContact = null
                                            tripCallMessage = "Contacto quitado."
                                        }, enabled = !busy) { Text("Quitar contacto") }
                                    }
                                    Text(tripCallMessage, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
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
            selectedScreen = AppScreen.TRIP
            assistantMessage = "Botón recibido ($source) durante el viaje."
            runCatching {
                startService(Intent(this, TripVoiceService::class.java)
                    .setAction(TripVoiceService.ACTION_MANUAL_COMMAND))
            }.onFailure {
                TripTestState.status = "No se pudo abrir la orden del botón: ${it.message ?: "error de Android"}."
            }
        } else {
            selectedScreen = AppScreen.TESTS
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
                                    TestIntent.VOLUME_UP -> "Subir volumen (${media.musicVolume()})"
                                    TestIntent.VOLUME_DOWN -> "Bajar volumen (${media.musicVolume()})"
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
