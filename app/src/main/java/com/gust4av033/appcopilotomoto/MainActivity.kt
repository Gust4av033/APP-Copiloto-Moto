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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val speech by lazy { AndroidSpeechToTextProvider(applicationContext) }
    private val media by lazy { MediaActionAdapter(applicationContext) }
    private var running by mutableStateOf(false)
    private var listening by mutableStateOf(false)
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
                        Button(onClick = { requestProbe() }, enabled = !running && !listening, modifier = Modifier.padding(top = 20.dp)) {
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
                        Button(onClick = { if (listening) cancelVoiceTest() else requestAudioPermissions(::startVoiceTest) }, enabled = !running, modifier = Modifier.padding(top = 12.dp)) {
                            Text(if (listening) "Cancelar escucha" else "Escuchar orden")
                        }
                        Text(voiceMessage, modifier = Modifier.padding(top = 12.dp))
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
        }
    }

    private fun cancelVoiceTest() {
        speech.cancel()
        listening = false
        voiceMessage = "Escucha cancelada; no se ejecutó ninguna acción."
    }

    override fun onDestroy() {
        if (listening) speech.cancel()
        worker.shutdown()
        super.onDestroy()
    }
}
