package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.openwakeword.OpenWakeWord

/** Prototipo con frase conocida; personalización acústica llegará después. */
class OpenWakeWordProvider(context: Context, threshold: Float) : WakeWordProvider {
    override val phrase = "Hey Jarvis"
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val detector = OpenWakeWord.Builder(context.applicationContext)
        .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
        .setThreshold(threshold)
        .setDebounceMs(2_000)
        .build()
    private var oldMode: Int? = null
    private var ownsRoute = false
    private var closed = false

    override fun start(onDetected: (Float) -> Unit): Result<Unit> = runCatching {
        check(!closed) { "La sesión de wake word ya terminó." }
        if (oldMode == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            oldMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            val headset = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (headset != null) ownsRoute = audioManager.setCommunicationDevice(headset)
        }
        detector.start { score -> onDetected(score) }
    }

    /** Detiene el micrófono del detector antes de cederlo al STT. Conserva la ruta Bluetooth. */
    override fun pause() {
        detector.stop()
    }

    override fun close() {
        if (closed) return
        closed = true
        try { detector.release() } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ownsRoute) audioManager.clearCommunicationDevice()
                oldMode?.let { audioManager.mode = it }
            }
            ownsRoute = false
            oldMode = null
        }
    }
}
