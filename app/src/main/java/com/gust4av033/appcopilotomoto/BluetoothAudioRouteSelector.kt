package com.gust4av033.appcopilotomoto

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi

/** Elige la entrada de comunicación asociada a la salida multimedia elegida por Android. */
internal object BluetoothAudioRouteSelector {
    @RequiresApi(Build.VERSION_CODES.S)
    fun communicationHeadset(audioManager: AudioManager): AudioDeviceInfo? {
        val choices = audioManager.availableCommunicationDevices.filter {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        if (choices.isEmpty()) return null

        val mediaOutputs: List<AudioDeviceInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                audioManager.getAudioDevicesForAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                )
            }.getOrDefault(emptyList())
        } else emptyList()

        fun communicationType(output: AudioDeviceInfo): Int? = when (output.type) {
            AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioDeviceInfo.TYPE_BLE_HEADSET
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            else -> null
        }

        // Una dirección coincidente evita elegir otro casco conectado al mismo tiempo.
        for (output in mediaOutputs) {
            val type = communicationType(output) ?: continue
            if (output.address.isNotBlank()) {
                choices.firstOrNull {
                    it.type == type && it.address.isNotBlank() && it.address == output.address
                }?.let { return it }
            }
        }
        // Algunos dispositivos no publican una dirección comparable en ambas rutas.
        for (output in mediaOutputs) {
            val type = communicationType(output) ?: continue
            choices.singleOrNull { it.type == type }?.let { return it }
        }
        audioManager.communicationDevice?.let { selected ->
            choices.firstOrNull { it.id == selected.id }?.let { return it }
        }
        // Con varios cascos ambiguos se pide elegir una salida; no se toma el primero.
        return choices.singleOrNull()
    }
}
