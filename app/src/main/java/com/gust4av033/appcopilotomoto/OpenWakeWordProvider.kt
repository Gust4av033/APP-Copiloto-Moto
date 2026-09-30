package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import com.openwakeword.OpenWakeWord

/** Frases con modelos incluidos; una frase nueva requiere un modelo entrenado. */
enum class WakePhrase(val label: String) {
    ALEXA("Alexa"),
    HEY_JARVIS("Hey Jarvis")
}

enum class WakeAudioMode(val label: String) {
    BLUETOOTH("Casco: activación y orden"),
    PHONE_WAKE("Prueba: teléfono para Alexa, casco para la orden")
}

class OpenWakeWordProvider(
    context: Context,
    threshold: Float,
    wakePhrase: WakePhrase,
    private val audioMode: WakeAudioMode = WakeAudioMode.BLUETOOTH
) : WakeWordProvider {
    override val phrase = wakePhrase.label
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val detector = OpenWakeWord.Builder(context.applicationContext)
        .setModel(when (wakePhrase) {
            WakePhrase.HEY_JARVIS -> OpenWakeWord.BuiltInModel.HEY_JARVIS
            WakePhrase.ALEXA -> OpenWakeWord.BuiltInModel.ALEXA
        })
        .setThreshold(threshold)
        .setDebounceMs(2_000)
        .build()
    private var oldMode: Int? = null
    private var ownsRoute = false
    private var closed = false

    override fun start(onDetected: (Float) -> Unit): Result<Unit> = runCatching {
        check(!closed) { "La sesión de wake word ya terminó." }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (oldMode == null) oldMode = audioManager.mode
            if (audioMode == WakeAudioMode.PHONE_WAKE) {
                // Sin ruta de llamada: el detector usa la entrada normal de Android.
                if (ownsRoute) audioManager.clearCommunicationDevice()
                ownsRoute = false
                audioManager.mode = oldMode ?: AudioManager.MODE_NORMAL
            } else {
                selectHeadset()
            }
        }
        detector.start { score -> onDetected(score) }
    }

    /** Libera la grabación wake antes de abrir STT; la orden usa el casco en ambos modos. */
    override fun pause() {
        detector.stop()
        if (audioMode == WakeAudioMode.PHONE_WAKE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            selectHeadset()
        }
    }

    /** Comprueba la entrada activa del detector; elegir una salida no garantiza el micrófono. */
    fun wakeInputStatus(): String? {
        val inputs = audioManager.activeRecordingConfigurations
            .filter { it.clientAudioSource == MediaRecorder.AudioSource.VOICE_RECOGNITION }
            .mapNotNull { it.audioDevice }
        if (audioMode == WakeAudioMode.BLUETOOTH) {
            if (inputs.isEmpty()) return "Alexa: Android no informó el micrófono activo."
            val selected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.communicationDevice else null
            check(selected != null && inputs.all { input ->
                input.type == selected.type &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                        input.address.isBlank() || selected.address.isBlank() || input.address == selected.address)
            }) { "Alexa usa otro micrófono distinto al casco seleccionado." }
            return "Alexa: micrófono Bluetooth ${if (selected.type == AudioDeviceInfo.TYPE_BLE_HEADSET) "LE" else "SCO"} verificado."
        }
        check(inputs.isNotEmpty()) { "No se pudo verificar la entrada de Alexa; probá el modo casco." }
        check(inputs.all { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }) {
            "Alexa está usando Bluetooth u otra entrada; probá el modo casco."
        }
        return "Alexa: micrófono del teléfono; órdenes: casco Bluetooth."
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun selectHeadset() {
        val headset = BluetoothAudioRouteSelector.communicationHeadset(audioManager)
            ?: error("No se pudo elegir el micrófono del casco. Seleccioná su salida multimedia o desconectá el otro casco.")
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (!audioManager.setCommunicationDevice(headset)) {
            audioManager.mode = oldMode ?: AudioManager.MODE_NORMAL
            error("Android no pudo seleccionar el casco para la orden.")
        }
        ownsRoute = true
    }

    override fun close() {
        if (closed) return
        closed = true
        try { detector.release() } finally {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        if (ownsRoute) audioManager.clearCommunicationDevice()
                    } finally {
                        // Restaurar el modo aunque falle la limpieza de la ruta SCO.
                        oldMode?.let { audioManager.mode = it }
                    }
                }
            } finally {
                ownsRoute = false
                oldMode = null
            }
        }
    }
}
