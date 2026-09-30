package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent

data class VolumeAdjustment(
    val before: Int,
    val after: Int,
    val maximum: Int,
    val direction: Int,
    val fixed: Boolean,
    val stream: Int
)

class MediaActionAdapter(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Confirma envío del evento, no que otra app haya cambiado de pista. */
    fun nextTrack(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_NEXT) }

    fun previousTrack(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }

    fun play(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_PLAY) }

    fun pause(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_PAUSE) }

    /** En SCO el nivel multimedia puede cambiar sin modificar la intensidad audible. */
    fun volumeUp(requireBluetoothCommunication: Boolean = false): Result<VolumeAdjustment> =
        runCatching { adjustActiveRoute(AudioManager.ADJUST_RAISE, requireBluetoothCommunication) }

    fun volumeDown(requireBluetoothCommunication: Boolean = false): Result<VolumeAdjustment> =
        runCatching { adjustActiveRoute(AudioManager.ADJUST_LOWER, requireBluetoothCommunication) }

    private fun adjustActiveRoute(direction: Int, requireBluetoothCommunication: Boolean): VolumeAdjustment {
        val voiceHeadset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Samsung puede informar MODE_NORMAL durante la captura aunque el
            // micrófono y el dispositivo de comunicación SCO estén activos.
            val routeType = audioManager.communicationDevice?.type
            routeType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                routeType == AudioDeviceInfo.TYPE_BLE_HEADSET
        } else false
        check(!requireBluetoothCommunication || voiceHeadset) {
            "Ruta de voz Bluetooth no disponible al ajustar volumen; música quedó intacta."
        }
        val stream = if (voiceHeadset) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC
        val before = audioManager.getStreamVolume(stream)
        val maximum = audioManager.getStreamMaxVolume(stream)
        if (voiceHeadset) {
            // En el S22+ con Y12, adjustStreamVolume(VOICE_CALL) no movió 15/15.
            // Pedir el índice concreto permite distinguir un bloqueo de Android
            // de una variación que el casco no reproduce de forma audible.
            val minimum = audioManager.getStreamMinVolume(stream)
            val target = (before + direction).coerceIn(minimum, maximum)
            audioManager.setStreamVolume(stream, target, 0)
        } else {
            audioManager.adjustStreamVolume(stream, direction, 0)
        }
        return VolumeAdjustment(
            before = before,
            after = audioManager.getStreamVolume(stream),
            maximum = maximum,
            direction = direction,
            fixed = audioManager.isVolumeFixed,
            stream = stream
        )
    }

    fun commandVolumeStatus(change: VolumeAdjustment? = null): String {
        val music = musicVolume()
        val communication = "${audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}/${audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}"
        val adjustment = change?.let { changeValue ->
            val label = if (changeValue.stream == AudioManager.STREAM_VOICE_CALL) "comunicación" else "música"
            when {
                changeValue.fixed -> "$label: volumen fijo por Android"
                changeValue.after != changeValue.before ->
                    "$label ${changeValue.before}→${changeValue.after}/${changeValue.maximum}"
                changeValue.direction == AudioManager.ADJUST_RAISE && changeValue.after >= changeValue.maximum ->
                    "$label ${changeValue.after}/${changeValue.maximum}: ya está al máximo"
                changeValue.direction == AudioManager.ADJUST_LOWER && changeValue.after <= 0 ->
                    "$label ${changeValue.after}/${changeValue.maximum}: ya está al mínimo"
                else -> "$label ${changeValue.after}/${changeValue.maximum}: Android no cambió el nivel"
            }
        }
        return listOfNotNull(adjustment, "música $music", "comunicación $communication").joinToString("; ")
    }

    fun musicVolume(): String = "${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}/${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}"

    /** Lectura pasiva para distinguir volumen multimedia del nivel usado por el casco en comunicación. */
    fun audioRouteDiagnostic(): String {
        val route = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = audioManager.communicationDevice
            val kind = when (device?.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
                null -> "sin ruta de comunicación elegida"
                else -> "otra ruta de comunicación"
            }
            if (device == null) kind else "$kind (${device.productName}, ID ${device.id})"
        } else "ruta no informada"
        // Android 13+ informa la salida prevista para un audio con atributos de música.
        // No afirma qué salida decidió usar otra app o un dispositivo remoto.
        val mediaOutput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                audioManager.getAudioDevicesForAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                ).map { device ->
                    val kind = when (device.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "altavoz del teléfono"
                        else -> "tipo ${device.type}"
                    }
                    "$kind (${device.productName}, ID ${device.id})"
                }.distinct().joinToString().ifEmpty { "sin salida informada" }
            }.getOrDefault("no disponible")
        } else "no disponible en esta versión de Android"
        val availableHeadsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                audioManager.availableCommunicationDevices.mapNotNull { device ->
                    val kind = when (device.type) {
                        AudioDeviceInfo.TYPE_BLE_HEADSET -> "LE Audio"
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "SCO clásico"
                        else -> null
                    }
                    kind?.let { "$it (${device.productName}, ID ${device.id})" }
                }.joinToString().ifEmpty { "ninguno" }
            }.getOrDefault("no disponible")
        } else "no disponible"
        val mode = if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) "comunicación" else "normal"
        val music = musicVolume()
        val voice = "${audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}/${audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}"
        return "Modo: $mode · Ruta comunicación: $route · Salida música prevista: $mediaOutput · Rutas casco disponibles: $availableHeadsets · Música: $music · Comunicación: $voice"
    }

    /** Espera a que Android deje de enviar música por la ruta de llamada. */
    fun isMediaRouteReady(): Boolean {
        if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return runCatching {
                val outputs = audioManager.getAudioDevicesForAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                )
                outputs.isNotEmpty() && outputs.none { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            }.getOrDefault(false)
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            audioManager.communicationDevice?.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    }

    /** Solicita una pausa solo si Android informa reproducción activa. */
    fun pauseIfPlaying(): Boolean {
        if (!audioManager.isMusicActive) return false
        dispatch(KeyEvent.KEYCODE_MEDIA_PAUSE)
        return true
    }

    fun resumePlayback() = dispatch(KeyEvent.KEYCODE_MEDIA_PLAY)

    private fun dispatch(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(downTime, SystemClock.uptimeMillis(), action, keyCode, 0)
            )
        }
    }
}
