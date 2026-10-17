package com.gust4av033.appcopilotomoto

data class MusicPlayer(val packageName: String, val label: String, val backgroundSearch: Boolean,
    val sessionSearch: Boolean = false, val intentSearch: Boolean = true)
enum class MusicPlayerOrigin { EXPLICIT, TRIP, PLAYING_SESSION, SETTINGS, AUTOMATIC }
data class ResolvedMusicPlayer(val player: MusicPlayer, val origin: MusicPlayerOrigin)
data class MusicSearchRequest(val player: MusicPlayer, val query: String,
    val route: MusicSearchRoute = MusicSearchRoute.VISIBLE_INTENT)
enum class MusicSearchRoute { POWERAMP_BROADCAST, MEDIA_SESSION, VISIBLE_INTENT, UNAVAILABLE }

internal object MusicSearchRouting {
    fun choose(player: MusicPlayer, activeSessionCanSearch: Boolean): MusicSearchRoute = when {
        player.backgroundSearch -> MusicSearchRoute.POWERAMP_BROADCAST
        activeSessionCanSearch -> MusicSearchRoute.MEDIA_SESSION
        player.intentSearch -> MusicSearchRoute.VISIBLE_INTENT
        else -> MusicSearchRoute.UNAVAILABLE
    }
}

/** Un reproductor elegido que desaparece nunca se sustituye silenciosamente. */
internal object MusicPlayerSelection {
    const val POWERAMP_PACKAGE = "com.maxmpz.audioplayer"
    fun resolve(players: List<MusicPlayer>, explicitName: String?, tripPackage: String?,
        playingPackages: List<String>, preferredPackage: String?): Result<ResolvedMusicPlayer> = runCatching {
        fun byPackage(value: String, origin: MusicPlayerOrigin) = players.singleOrNull { it.packageName == value }
            ?.let { ResolvedMusicPlayer(it, origin) }
        if (explicitName != null) {
            val normalized = MinimalIntentRouter.normalize(explicitName)
            val known = mapOf("spotify" to "com.spotify.music", "poweramp" to POWERAMP_PACKAGE,
                "youtube music" to "com.google.android.apps.youtube.music",
                "apple music" to "com.apple.android.music", "vlc" to "org.videolan.vlc")
            return@runCatching players.singleOrNull { MinimalIntentRouter.normalize(it.label) == normalized ||
                it.packageName == known[normalized] ||
                MinimalIntentRouter.normalize(it.packageName.substringAfterLast('.')) == normalized }
                ?.let { ResolvedMusicPlayer(it, MusicPlayerOrigin.EXPLICIT) }
                ?: error("No encontré ‘$explicitName’ entre los reproductores compatibles.")
        }
        if (tripPackage != null) return@runCatching byPackage(tripPackage, MusicPlayerOrigin.TRIP)
            ?: error("El reproductor de este viaje dejó de estar disponible. Abrilo otra vez o decí ‘vuelve a mi reproductor’.")
        val playing = players.filter { it.packageName in playingPackages }
        if (playing.size == 1) return@runCatching ResolvedMusicPlayer(playing.single(), MusicPlayerOrigin.PLAYING_SESSION)
        if (playing.size > 1) {
            val preferredPlaying = preferredPackage?.let { byPackage(it, MusicPlayerOrigin.PLAYING_SESSION) }
                ?.takeIf { it.player in playing }
            if (preferredPlaying != null) return@runCatching preferredPlaying
            error("Hay varios reproductores sonando; decí el nombre en la orden o elegí uno en Ajustes → Música.")
        }
        if (preferredPackage != null) return@runCatching byPackage(preferredPackage, MusicPlayerOrigin.SETTINGS)
            ?: error("El reproductor elegido ya no ofrece búsqueda. Revisá Ajustes → Música.")
        val automatic = choose(players, null).getOrThrow()
        ResolvedMusicPlayer(automatic, MusicPlayerOrigin.AUTOMATIC)
    }
    fun choose(players: List<MusicPlayer>, preferredPackage: String?): Result<MusicPlayer> = runCatching {
        if (preferredPackage != null) {
            return@runCatching players.singleOrNull { it.packageName == preferredPackage }
                ?: error("El reproductor elegido ya no ofrece búsqueda. Revisá Ajustes → Música.")
        }
        players.firstOrNull { it.packageName == POWERAMP_PACKAGE } ?: players.singleOrNull()
            ?: error(if (players.isEmpty()) "No hay reproductores que anuncien búsqueda por nombre. Revisá Ajustes → Música."
                else "Hay varios reproductores compatibles. Elegí uno en Ajustes → Música.")
    }
}
