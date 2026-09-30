package com.gust4av033.appcopilotomoto

import android.media.AudioManager

/** Único punto de ejecución de órdenes de música y volumen. */
class MediaCommandExecutor(private val media: MediaActionAdapter) {
    fun execute(command: RoutedCommand): Result<*>? = when (command.intent) {
        TestIntent.MEDIA_NEXT -> media.nextTrack()
        TestIntent.MEDIA_PREVIOUS -> media.previousTrack()
        TestIntent.MEDIA_PLAY -> media.play()
        TestIntent.MEDIA_PAUSE -> media.pause()
        TestIntent.VOLUME_UP -> media.volumeUp()
        TestIntent.VOLUME_DOWN -> media.volumeDown()
        TestIntent.VOLUME_SET -> command.volume?.let { media.setRequestedVolume(it, bluetoothVoice = false) }
        else -> null
    }

    /** El viaje ajusta volumen después de restaurar la ruta de audio. */
    fun executeRestoredVolume(command: RoutedCommand, bluetoothVoice: Boolean): Result<VolumeAdjustment>? =
        when (command.intent) {
            TestIntent.VOLUME_UP -> if (bluetoothVoice) media.volumeUp(requireBluetoothCommunication = true)
                else media.adjustMusicAfterRoute(AudioManager.ADJUST_RAISE)
            TestIntent.VOLUME_DOWN -> if (bluetoothVoice) media.volumeDown(requireBluetoothCommunication = true)
                else media.adjustMusicAfterRoute(AudioManager.ADJUST_LOWER)
            TestIntent.VOLUME_SET -> command.volume?.let { media.setRequestedVolume(it, bluetoothVoice) }
            else -> null
        }

    fun description(intent: TestIntent, change: VolumeAdjustment? = null): String = when (intent) {
        TestIntent.MEDIA_NEXT -> "Siguiente canción"
        TestIntent.MEDIA_PREVIOUS -> "Anterior canción"
        TestIntent.MEDIA_PLAY -> "Reproducir"
        TestIntent.MEDIA_PAUSE -> "Pausar"
        TestIntent.VOLUME_UP -> "Subir volumen (${media.commandVolumeStatus(change)})"
        TestIntent.VOLUME_DOWN -> "Bajar volumen (${media.commandVolumeStatus(change)})"
        TestIntent.VOLUME_SET -> "Fijar volumen (${media.commandVolumeStatus(change)})"
        else -> "Orden"
    }
}
