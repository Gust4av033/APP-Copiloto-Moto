package com.gust4av033.appcopilotomoto

import android.app.Activity
import android.content.Context
import android.media.AudioManager

/** Ejecuta las acciones deterministas; la sesión decide cuándo restaurar audio o pedir confirmación. */
class ActionExecutor(private val context: Context, private val media: MediaActionAdapter) {
    private val apps = AppLaunchAdapter(context)
    private val music = MusicSearchAdapter(context)
    private val chats: List<ContactChatAdapter> = ChatApp.entries.map { WhatsAppChatAdapter(context, it) }

    fun executeMedia(command: RoutedCommand): Result<*>? = when (command.intent) {
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

    fun availableMusicPlayers(): Result<List<MusicPlayer>> = music.availablePlayers()
    fun preferredMusicPlayer(): String? = music.preferredPackage()
    fun selectMusicPlayer(packageName: String?) = music.selectPlayer(packageName)
    fun prepareMusicSearch(command: RoutedCommand): Result<MusicSearchRequest> = music.prepare(command)
    fun sendMusicSearch(request: MusicSearchRequest): Result<Unit> = music.send(request)

    fun contactChatCapabilities(): List<ContactChatCapability> = chats.map { ContactChatCapability(it.app, it.available()) }

    fun prepareContactChat(command: RoutedCommand): Result<AppTarget> = runCatching {
        check(command.intent == TestIntent.OPEN_CONTACT_CHAT) { "La orden no pide abrir un chat." }
        val app = checkNotNull(command.chatApp)
        val adapter = chats.single { it.app == app }
        check(adapter.available()) { "${app.label} no está disponible. Revisá Ajustes → Integraciones." }
        val contact = VoiceContactResolver(context).findUnique(checkNotNull(command.contactName), preferInternationalNumber = true).getOrThrow()
        adapter.prepare(contact).getOrThrow()
    }

    fun availableApps(): Result<List<LaunchableApp>> = apps.installedApps()

    fun prepareApp(command: RoutedCommand): Result<AppTarget> = runCatching {
        check(command.intent == TestIntent.OPEN_APP) { "La orden no pide abrir una aplicación." }
        apps.find(checkNotNull(command.appName)).getOrThrow()
    }

    /** Solo la prueba con Activity visible abre otra pantalla directamente. */
    fun openAppFromActivity(target: AppTarget): Result<Unit> = runCatching {
        check(context is Activity && !context.isFinishing) { "APP debe estar visible para abrir otra aplicación." }
        context.startActivity(target.launchIntent)
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

