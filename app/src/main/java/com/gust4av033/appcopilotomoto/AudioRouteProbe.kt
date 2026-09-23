package com.gust4av033.appcopilotomoto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioProbeResult(
    val requestedInput: String,
    val actualInput: String,
    val selectedOutput: String,
    val durationMs: Long,
    val rmsDbfs: Int,
    val peakDbfs: Int,
    val sampleCount: Long,
    val warning: String?
)

/** Graba cinco segundos para medir la ruta; no guarda audio ni transcripciones. */
class AudioRouteProbe(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun run(): AudioProbeResult {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val sampleRate = 16_000
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minimum = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        require(minimum > 0) { "No se pudo configurar la captura de audio." }

        val bluetoothInput = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        val requested = bluetoothInput?.label() ?: "Micrófono predeterminado"
        val oldMode = audioManager.mode
        var switched = false
        var recorder: AudioRecord? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && bluetoothInput != null) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                val communicationDevice = audioManager.availableCommunicationDevices
                    .firstOrNull { it.id == bluetoothInput.id }
                    ?: audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == bluetoothInput.type
                    }
                if (communicationDevice != null) {
                    switched = audioManager.setCommunicationDevice(communicationDevice)
                }
            }

            val bufferSize = maxOf(minimum * 2, 4096)
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channel,
                encoding,
                bufferSize
            )
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "El micrófono no se inicializó." }
            if (bluetoothInput != null) recorder.setPreferredDevice(bluetoothInput)
            val samples = ShortArray(bufferSize / 2)
            val target = sampleRate * 5L
            var count = 0L
            var sumSquares = 0.0
            var peak = 0
            recorder.startRecording()
            val start = SystemClock.elapsedRealtime()
            while (count < target && SystemClock.elapsedRealtime() - start < 7_000) {
                val read = recorder.read(samples, 0, minOf(samples.size.toLong(), target - count).toInt(), AudioRecord.READ_BLOCKING)
                if (read < 0) error("Error de captura: $read")
                if (read == 0) continue
                for (i in 0 until read) {
                    val value = samples[i].toInt()
                    sumSquares += value.toDouble() * value
                    peak = maxOf(peak, kotlin.math.abs(value))
                }
                count += read
            }
            val duration = SystemClock.elapsedRealtime() - start
            check(count > 0) { "No llegaron muestras del micrófono." }
            val routedDevice = recorder.routedDevice
            val actual = routedDevice?.label() ?: "Ruta no informada"
            val output = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.communicationDevice?.label() ?: "Salida predeterminada"
            } else "Salida predeterminada"
            val rms = sqrt(sumSquares / count) / 32768.0
            val warning = when {
                bluetoothInput == null -> "No se detectó entrada Bluetooth. La prueba utilizó la ruta disponible."
                routedDevice?.id != bluetoothInput.id -> "Android no confirmó el micrófono Bluetooth solicitado."
                rms < 0.001 -> "La señal fue muy baja: revisá el micrófono o repetí la prueba hablando."
                else -> null
            }
            return AudioProbeResult(requested, actual, output, duration, dbfs(rms), dbfs(peak / 32768.0), count, warning)
        } finally {
            try { recorder?.stop() } catch (_: IllegalStateException) { }
            recorder?.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && switched) audioManager.clearCommunicationDevice()
            audioManager.mode = oldMode
        }
    }

    private fun dbfs(amplitude: Double) = if (amplitude <= 0.0) -120 else (20 * log10(amplitude)).toInt()

    private fun AudioDeviceInfo.label(): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (casco, SCO; ID $id)"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth (casco, LE; ID $id)"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Micrófono del teléfono (ID $id)"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Auricular interno del teléfono (ID $id)"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altavoz del teléfono (ID $id)"
        else -> "Dispositivo tipo $type (ID $id)"
    }
}
