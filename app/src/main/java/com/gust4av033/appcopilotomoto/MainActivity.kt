package com.gust4av033.appcopilotomoto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
    private val worker = Executors.newSingleThreadExecutor()
    private val speech by lazy { AndroidSpeechToTextProvider(applicationContext) }
    private val media by lazy { MediaActionAdapter(applicationContext) }
    private var wakeProvider: WakeWordProvider? = null
    private var running by mutableStateOf(false)
    private var listening by mutableStateOf(false)
    private var wakeEnabled by mutableStateOf(false)
    private var wakeTransition by mutableStateOf(false)
    private var wakeDetections by mutableStateOf(0)
    private var wakeSensitivity by mutableFloatStateOf(80f)
    private var wakePhrase by mutableStateOf(WakePhrase.ALEXA)
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        Button(onClick = { requestProbe() }, enabled = !running && !listening && !wakeEnabled && !wakeTransition, modifier = Modifier.padding(top = 20.dp)) {
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
                        Text("Pulsá Escuchar y decí ‘siguiente canción’. También podés probar ‘cancelar’ o ‘cállate’.", modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = { if (listening) cancelVoiceTest() else requestAudioPermissions(::startVoiceTest) }, enabled = !running && !wakeEnabled && !wakeTransition, modifier = Modifier.padding(top = 12.dp)) {
                            Text(if (listening) "Cancelar escucha" else "Escuchar orden")
                        }
                        Text(voiceMessage, modifier = Modifier.padding(top = 12.dp))
                        Text("Fase 0.2 · Activación local", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 28.dp))
                        Text("Iniciá la sesión, decí ‘${wakePhrase.label}’, esperá a que se active y luego decí ‘siguiente canción’. El detector se pausa mientras escucha la orden.", modifier = Modifier.padding(top = 8.dp))
                        Text("Frase de activación de prueba", modifier = Modifier.padding(top = 12.dp))
                        Column(modifier = Modifier.selectableGroup()) {
                            WakePhrase.entries.forEach { option ->
                                val canChange = !wakeEnabled && !wakeTransition && !listening && !running
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
                            enabled = !wakeEnabled && !wakeTransition && !listening && !running
                        )
                        Text("Más sensibilidad puede detectar más veces la frase elegida, pero también activarse por error. Ajustala con la sesión detenida.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { if (wakeEnabled) endWakeTest() else requestAudioPermissions(::startWakeTest) },
                            enabled = !running && (wakeEnabled || (!listening && !wakeTransition)),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(if (wakeEnabled) "Detener sesión" else "Iniciar activación")
                        }
                        Text(wakeMessage, modifier = Modifier.padding(top = 12.dp))
                        Text("Activaciones detectadas: $wakeDetections")
                        Text("Prueba con la app visible. La escucha con pantalla apagada llegará con el servicio de viaje.", style = MaterialTheme.typography.bodySmall)
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
        voiceMessage = "Escuchando una orden corta…"
        val start = SystemClock.elapsedRealtime()
        speech.listenOnce { outcome ->
            if (!listening) return@listenOnce
            listening = false
            val elapsed = SystemClock.elapsedRealtime() - start
            voiceMessage = outcome.fold(
                onSuccess = { heard ->
                    when (MinimalIntentRouter.resolve(heard)) {
                        TestIntent.MEDIA_NEXT -> {
                            val dispatched = media.nextTrack()
                            if (dispatched.isSuccess) "Escuché: ‘$heard’. Envié Siguiente canción en $elapsed ms. Comprobá si cambió la pista."
                            else "Escuché: ‘$heard’, pero falló el envío: ${dispatched.exceptionOrNull()?.message}"
                        }
                        TestIntent.STOP_TTS -> "Escuché: ‘$heard’. Orden Silencio reconocida; aún no hay voz sintetizada para interrumpir."
                        TestIntent.CANCEL -> "Escuché: ‘$heard’. Orden Cancelar reconocida; no se ejecutó ninguna acción."
                        TestIntent.UNKNOWN -> "Escuché: ‘$heard’. No coincide con las órdenes de esta prueba; no se ejecutó nada."
                    }
                },
                onFailure = { "No se reconoció la orden: ${it.message ?: "error desconocido"}. Tiempo: $elapsed ms." }
            )
            if (fromWake && wakeEnabled) resumeWakeTest()
        }
    }

    private fun cancelVoiceTest() {
        speech.cancel()
        listening = false
        voiceMessage = "Escucha cancelada; no se ejecutó ninguna acción."
        if (wakeEnabled) resumeWakeTest()
    }

    private fun startWakeTest() {
        if (wakeEnabled || wakeTransition || listening || running) return
        wakeEnabled = true
        wakeTransition = true
        wakeDetections = 0
        wakeMessage = "Preparando el detector local…"
        val provider = OpenWakeWordProvider(applicationContext, threshold = (100f - wakeSensitivity) / 100f, wakePhrase = wakePhrase)
        wakeProvider = provider
        worker.execute {
            val outcome = provider.start(::onWakeDetected)
            runOnUiThread {
                if (wakeEnabled && wakeProvider === provider) {
                    wakeTransition = false
                    wakeMessage = if (outcome.isSuccess) "Esperando ‘${provider.phrase}’…" else "Falló el detector: ${outcome.exceptionOrNull()?.message}"
                    if (outcome.isFailure) endWakeTest()
                }
            }
        }
    }

    private fun onWakeDetected(score: Float) {
        if (!wakeEnabled || wakeTransition || listening) return
        wakeTransition = true
        wakeDetections++
        wakeMessage = "Activación #$wakeDetections (${(score * 100).toInt()} %). Preparando reconocimiento…"
        val provider = wakeProvider ?: return
        worker.execute {
            val outcome = runCatching { provider.pause() }
            runOnUiThread {
                if (wakeEnabled && wakeProvider === provider) {
                    wakeTransition = false
                    if (outcome.isSuccess) startVoiceCommand(fromWake = true)
                    else {
                        wakeMessage = "Falló la pausa del detector: ${outcome.exceptionOrNull()?.message}"
                        endWakeTest()
                    }
                }
            }
        }
    }

    private fun resumeWakeTest() {
        val provider = wakeProvider ?: return
        wakeTransition = true
        worker.execute {
            val outcome = provider.start(::onWakeDetected)
            runOnUiThread {
                if (wakeEnabled && wakeProvider === provider) {
                    wakeTransition = false
                    wakeMessage = if (outcome.isSuccess) "Esperando ‘${provider.phrase}’ de nuevo…" else "No se reanudó el detector: ${outcome.exceptionOrNull()?.message}"
                    if (outcome.isFailure) endWakeTest()
                }
            }
        }
    }

    private fun endWakeTest() {
        wakeEnabled = false
        wakeTransition = true
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
                wakeMessage = if (outcome.isSuccess) "Sesión detenida. Activaciones: $wakeDetections."
                else "La sesión terminó con error: ${outcome.exceptionOrNull()?.message}"
            }
        }
    }

    override fun onDestroy() {
        if (listening) speech.cancel()
        wakeEnabled = false
        val provider = wakeProvider
        wakeProvider = null
        if (provider != null) worker.execute { runCatching { provider.close() } }
        worker.shutdown()
        super.onDestroy()
    }
}
