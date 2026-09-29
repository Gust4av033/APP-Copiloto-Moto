package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent

class MediaActionAdapter(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Confirma envío del evento, no que otra app haya cambiado de pista. */
    fun nextTrack(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_NEXT) }

    fun previousTrack(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }

    fun play(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_PLAY) }

    fun pause(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_PAUSE) }

    /** Durante la ruta SCO, Android puede usar el nivel de comunicación para el casco. */
    private fun commandVolumeStream(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION &&
            audioManager.communicationDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            AudioManager.STREAM_VOICE_CALL
        } else AudioManager.STREAM_MUSIC

    /** Solo cambia un paso cuando el usuario pronuncia la orden de volumen. */
    fun volumeUp(): Result<Unit> = runCatching {
        audioManager.adjustStreamVolume(commandVolumeStream(), AudioManager.ADJUST_RAISE, 0)
    }

    fun volumeDown(): Result<Unit> = runCatching {
        audioManager.adjustStreamVolume(commandVolumeStream(), AudioManager.ADJUST_LOWER, 0)
    }

    fun commandVolumeStatus(): String {
        val stream = commandVolumeStream()
        val label = if (stream == AudioManager.STREAM_VOICE_CALL) "casco/comunicación" else "música"
        return "$label ${audioManager.getStreamVolume(stream)}/${audioManager.getStreamMaxVolume(stream)}"
    }

    fun musicVolume(): String = "${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}/${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}"

    /** Lectura pasiva para distinguir volumen multimedia del nivel usado por el casco en comunicación. */
    fun audioRouteDiagnostic(): String {
        val route = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (audioManager.communicationDevice?.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
                null -> "sin ruta de comunicación elegida"
                else -> "otra ruta de comunicación"
            }
        } else "ruta no informada"
        // Android 13+ informa la salida prevista para un audio con atributos de música.
        // No afirma qué salida decidió usar otra app o un dispositivo remoto.
        val mediaOutput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                audioManager.getAudioDevicesForAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                ).map { device ->
                    when (device.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "altavoz del teléfono"
                        else -> "tipo ${device.type}"
                    }
                }.distinct().joinToString().ifEmpty { "sin salida informada" }
            }.getOrDefault("no disponible")
        } else "no disponible en esta versión de Android"
        val mode = if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) "comunicación" else "normal"
        val music = musicVolume()
        val voice = "${audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}/${audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}"
        return "Modo: $mode · Ruta comunicación: $route · Salida música prevista: $mediaOutput · Música: $music · Comunicación: $voice"
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
