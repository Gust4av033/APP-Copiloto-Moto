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

        val bluetoothMedia = mediaOutputs.mapNotNull { output ->
            communicationType(output)?.let { type -> type to output.address }
        }
        // Android puede publicar varias salidas simultáneas: no tomar la primera al azar.
        if (bluetoothMedia.map { it.second }.distinct().size > 1) return null
        val matching = bluetoothMedia.flatMap { (type, address) ->
            if (address.isBlank()) emptyList() else choices.filter {
                it.type == type && it.address.isNotBlank() && it.address == address
            }
        }.distinctBy { it.id }
        if (matching.size == 1) return matching.single()
        if (matching.size > 1) return null

        // Con un solo casco se tolera que Android omita la dirección, pero no una
        // dirección conocida que apunte explícitamente a otro dispositivo.
        if (choices.size == 1) {
            val only = choices.single()
            if (bluetoothMedia.isEmpty()) return only
            if (bluetoothMedia.all { (type, address) ->
                    type == only.type && (address.isBlank() || only.address.isBlank() || address == only.address)
                }) return only
        }
        return null
    }
}
