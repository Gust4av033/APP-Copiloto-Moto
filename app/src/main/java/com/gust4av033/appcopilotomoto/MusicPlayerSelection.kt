package com.gust4av033.appcopilotomoto

data class MusicPlayer(val packageName: String, val label: String, val backgroundSearch: Boolean)
data class MusicSearchRequest(val player: MusicPlayer, val query: String)

/** Un reproductor elegido que desaparece nunca se sustituye silenciosamente. */
internal object MusicPlayerSelection {
    const val POWERAMP_PACKAGE = "com.maxmpz.audioplayer"
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
