package com.gust4av033.appcopilotomoto

import android.app.Activity
import android.content.Context
import android.media.AudioManager

/** Ejecuta las acciones deterministas; la sesión decide cuándo restaurar audio o pedir confirmación. */
class ActionExecutor(private val context: Context, private val media: MediaActionAdapter) {
    private val apps = AppLaunchAdapter(context)
    private fun navigation(app: NavigationApp): NavigationProvider = when (app) {
        NavigationApp.WAZE -> WazeNavigationProvider(context)
        NavigationApp.GOOGLE_MAPS -> GoogleMapsNavigationProvider(context)
    }
    private val selectedNavigation: NavigationProvider get() = navigation(NavigationPreferences.selected(context))
    private val music = MusicSearchAdapter(context)
    private val chats: List<ContactChatAdapter> = ChatApp.entries.map { WhatsAppChatAdapter(context, it) }

    fun mediaPlayerPackage(name: String): Result<String> = music.playerPackage(name)

    fun executeMedia(command: RoutedCommand): Result<*>? = when (command.intent) {
        TestIntent.MEDIA_NEXT -> mediaPackage(command).mapCatching { media.nextTrack(it).getOrThrow() }
        TestIntent.MEDIA_PREVIOUS -> mediaPackage(command).mapCatching { media.previousTrack(it).getOrThrow() }
        TestIntent.MEDIA_PLAY -> mediaPackage(command).mapCatching { media.play(it).getOrThrow() }
        TestIntent.MEDIA_PAUSE -> mediaPackage(command).mapCatching { media.pause(it).getOrThrow() }
        TestIntent.VOLUME_UP -> media.volumeUp()
        TestIntent.VOLUME_DOWN -> media.volumeDown()
        TestIntent.VOLUME_SET -> command.volume?.let { media.setRequestedVolume(it, bluetoothVoice = false) }
        else -> null
    }

    private fun mediaPackage(command: RoutedCommand): Result<String?> = command.musicPlayerName?.let {
        music.playerPackage(it).map { packageName -> packageName }
    } ?: Result.success(null)

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
    fun currentMusicPlayer(): Result<ResolvedMusicPlayer> = music.currentPlayer()
    fun selectMusicPlayer(packageName: String?) = music.selectPlayer(packageName)
    fun prepareMusicSearch(command: RoutedCommand): Result<MusicSearchRequest> = music.prepare(command)
    fun sendMusicSearch(request: MusicSearchRequest): Result<Unit> = music.send(request)

    fun contactChatCapabilities(): List<ContactChatCapability> = chats.map { ContactChatCapability(it.app, it.available()) }

    fun prepareContactChat(command: RoutedCommand): Result<AppTarget> = runCatching {
        check(command.intent == TestIntent.OPEN_CONTACT_CHAT || command.intent == TestIntent.DRAFT_CONTACT_MESSAGE) { "La orden no pide un chat o borrador." }
        val app = checkNotNull(command.chatApp)
        val adapter = chats.single { it.app == app }
        check(adapter.available()) { "${app.label} no está disponible. Revisá Ajustes → Integraciones." }
        val contact = VoiceContactResolver(context).findUnique(checkNotNull(command.contactName), preferInternationalNumber = true).getOrThrow()
        adapter.prepare(contact).getOrThrow()
    }

    fun navigationAvailable(): Boolean = selectedNavigation.available()
    fun nextTurnAnswer(): String {
        val current = TripNavigationState.context.lastDispatch?.providerId
            ?.let(NavigationApp::fromId) ?: NavigationPreferences.selected(context)
        return navigation(current).nextTurnAnswer()
    }

    /** Only reports what APP dispatched in this trip; the navigator's actual route is unknown. */
    fun navigationRequestAnswer(): String {
        val trip = TripNavigationState.context
        if (trip.sessionId == null) return context.getString(R.string.navigation_request_no_trip)
        val dispatch = trip.lastDispatch ?: return context.getString(R.string.navigation_request_empty)
        val provider = NavigationApp.entries.firstOrNull { it.id == dispatch.providerId }?.label
            ?: context.getString(R.string.navigation_request_unknown_provider)
        return context.getString(if (dispatch.kind == NavigationRequestKind.SEARCH)
            R.string.navigation_request_search_answer else R.string.navigation_request_favorite_answer,
            dispatch.requestedDestination, provider)
    }

    fun preparePlaceSearch(command: RoutedCommand): Result<AppTarget> = runCatching {
        check(command.intent == TestIntent.SEARCH_PLACE) { "La orden no pide buscar un lugar." }
        navigation(command.navigationProvider ?: NavigationPreferences.selected(context))
            .search(checkNotNull(command.placeQuery)).getOrThrow()
    }

    fun prepareNavigation(command: RoutedCommand): Result<AppTarget> = runCatching {
        check(command.intent == TestIntent.NAVIGATE) { "La orden no pide navegación." }
        val request = checkNotNull(command.navigationRequest)
        val app = command.navigationProvider ?: NavigationPreferences.selected(context)
        val destination = request.resolve(NavigationRequestStore.last(context, app))
            ?: error("Todavía no hay un destino solicitado desde APP en ${app.label}. Pedí ir a Casa o Trabajo primero; APP no lee el historial externo.")
        navigation(app).prepare(destination).getOrThrow()
    }

    fun availableApps(): Result<List<LaunchableApp>> = apps.installedApps()

    fun prepareApp(command: RoutedCommand): Result<AppTarget> = runCatching {
        check(command.intent == TestIntent.OPEN_APP) { "La orden no pide abrir una aplicación." }
        apps.find(checkNotNull(command.appName)).getOrThrow()
    }

    /** Solo la prueba con Activity visible abre otra pantalla directamente. */
    fun openAppFromActivity(target: AppTarget): Result<Unit> = runCatching {
        check(context is Activity && !context.isFinishing) { "APP debe estar visible para abrir otra aplicación." }
        val navigationSession = TripNavigationState.context.sessionId
        context.startActivity(target.launchIntent)
        NavigationRequestStore.recordDispatched(context, target, navigationSession)
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
