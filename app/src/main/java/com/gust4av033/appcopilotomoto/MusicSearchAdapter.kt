package com.gust4av033.appcopilotomoto

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.media.session.PlaybackState

/** Intent estándar para reproductores compatibles; Poweramp permite envío directo sin Activity. */
class MusicSearchAdapter(private val context: Context) {
    companion object {
        private const val POWERAMP_RECEIVER = "com.maxmpz.audioplayer.player.PowerampAPIReceiver"
        private const val PREFERENCE = "music_search_player"
    }

    private val manager get() = context.packageManager
    private fun allowed(permission: String?): Boolean = permission.isNullOrEmpty() ||
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun availablePlayers(): Result<List<MusicPlayer>> = runCatching {
        val standard = manager.queryIntentActivities(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH),
            PackageManager.MATCH_DEFAULT_ONLY).mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (!activity.enabled || !activity.exported || !allowed(activity.permission) ||
                activity.packageName == context.packageName) return@mapNotNull null
            MusicPlayer(activity.packageName, info.loadLabel(manager).toString(), backgroundSearch = false)
        }.distinctBy(MusicPlayer::packageName)
        val receiver = runCatching {
            manager.getReceiverInfo(ComponentName(MusicPlayerSelection.POWERAMP_PACKAGE, POWERAMP_RECEIVER), 0)
        }.getOrNull()
        val direct = receiver?.takeIf { it.enabled && it.exported && allowed(it.permission) }?.let {
            MusicPlayer(it.packageName, it.applicationInfo.loadLabel(manager).toString(),
                backgroundSearch = true, intentSearch = false)
        }
        val sessionPlayers = runCatching { TripMediaAccess.sessions(context) }.getOrDefault(emptyList())
            .mapNotNull { controller -> runCatching {
                @Suppress("DEPRECATION")
                val label = manager.getApplicationLabel(manager.getApplicationInfo(controller.packageName, 0)).toString()
                MusicPlayer(controller.packageName, label, backgroundSearch = false,
                    sessionSearch = ((controller.playbackState?.actions ?: 0L) and
                        PlaybackState.ACTION_PLAY_FROM_SEARCH) != 0L, intentSearch = false)
            }.getOrNull() }
        val known = listOf("com.spotify.music", "com.google.android.apps.youtube.music",
            "com.apple.android.music", "org.videolan.vlc").mapNotNull { packageName -> runCatching {
            @Suppress("DEPRECATION")
            val label = manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
            MusicPlayer(packageName, label, backgroundSearch = false, intentSearch = false)
        }.getOrNull() }
        (standard + sessionPlayers + listOfNotNull(direct) + known).groupBy { it.packageName }
            .values.map { variants -> variants.first().copy(
                backgroundSearch = variants.any { it.backgroundSearch },
                sessionSearch = variants.any { it.sessionSearch },
                intentSearch = variants.any { it.intentSearch }) }
            .sortedBy { MinimalIntentRouter.normalize(it.label) }
    }

    fun preferredPackage(): String? = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
        .getString(PREFERENCE, null)

    fun selectPlayer(packageName: String?) {
        context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE).edit()
            .putString(PREFERENCE, packageName).apply()
    }

    fun currentPlayer(): Result<ResolvedMusicPlayer> = MusicPlayerSelection.resolve(
        availablePlayers().getOrElse { return Result.failure(it) }, null,
        TripMusicState.activePackage, playingPackages(), preferredPackage())

    private fun playingPackages(): List<String> = TripMediaAccess.playingPackages(context).ifEmpty {
        listOfNotNull(TripMusicState.cyclePlayingPackage)
    }

    fun playerPackage(name: String): Result<String> = MusicPlayerSelection.resolve(
        availablePlayers().getOrElse { return Result.failure(it) }, name, null,
        emptyList(), preferredPackage()).map { it.player.packageName }

    fun prepare(command: RoutedCommand): Result<MusicSearchRequest> = runCatching {
        check(command.intent == TestIntent.MEDIA_SEARCH) { "La orden no pide una canción por nombre." }
        val query = checkNotNull(command.musicQuery).trim()
        check(query.isNotBlank()) { "Falta el nombre de la canción." }
        val selected = MusicPlayerSelection.resolve(availablePlayers().getOrThrow(), command.musicPlayerName,
            TripMusicState.activePackage, playingPackages(), preferredPackage()).getOrThrow().player
        val route = MusicSearchRouting.choose(selected,
            TripMediaAccess.searchController(context, selected.packageName) != null)
        check(route != MusicSearchRoute.UNAVAILABLE) {
            "${selected.label} no anuncia búsqueda por nombre a APP. Podés abrirlo y usar pausa/siguiente si tiene sesión multimedia."
        }
        MusicSearchRequest(selected, query, route)
    }

    /** Resultado = solicitud enviada; el reproductor decide si encuentra la canción. */
    fun send(request: MusicSearchRequest): Result<Unit> = runCatching {
        val current = availablePlayers().getOrThrow().singleOrNull { it.packageName == request.player.packageName }
        check(current != null) { "El reproductor cambió o ya no está disponible. Pedí la canción otra vez." }
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, request.query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            .putExtra(MediaStore.EXTRA_MEDIA_TITLE, request.query)
        if (request.route == MusicSearchRoute.POWERAMP_BROADCAST) {
            check(current.backgroundSearch) { "Poweramp ya no admite el envío directo." }
            intent.component = ComponentName(current.packageName, POWERAMP_RECEIVER)
            context.sendBroadcast(intent)
        } else if (request.route == MusicSearchRoute.MEDIA_SESSION) {
            val controller = TripMediaAccess.searchController(context, current.packageName)
                ?: error("${current.label} ya no anuncia búsqueda desde su sesión multimedia. Pedí la canción otra vez.")
            controller.transportControls.playFromSearch(request.query, null)
        } else if (request.route == MusicSearchRoute.VISIBLE_INTENT) {
            check(current.intentSearch) { "${current.label} ya no ofrece búsqueda estándar." }
            check(context is Activity && !context.isFinishing) {
                "${current.label} requiere APP visible y el teléfono desbloqueado para buscar por nombre. Poweramp admite envío directo."
            }
            intent.setPackage(current.packageName)
            context.startActivity(intent)
        } else error("${current.label} no ofrece una ruta de búsqueda disponible.")
    }
}
