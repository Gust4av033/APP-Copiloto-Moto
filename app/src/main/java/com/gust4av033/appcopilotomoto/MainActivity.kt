package com.gust4av033.appcopilotomoto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
    private var running by mutableStateOf(false)
    private var message by mutableStateOf("Conectá el casco Bluetooth y pulsá Iniciar prueba.")
    private var result by mutableStateOf<AudioProbeResult?>(null)

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) startProbe()
        else message = "Se necesita permiso de micrófono y, en Android 12 o posterior, de Bluetooth para esta prueba."
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
                        Button(onClick = { requestProbe() }, enabled = !running, modifier = Modifier.padding(top = 20.dp)) {
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
                    }
                }
            }
        }
    }

    private fun requestProbe() {
        val required = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) required += Manifest.permission.BLUETOOTH_CONNECT
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startProbe() else permissionRequest.launch(missing.toTypedArray())
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

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
