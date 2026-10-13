package com.gust4av033.appcopilotomoto

internal object EnglishSongRequest {
    fun matches(normalized: String): Boolean = normalized in setOf(
        "busca cancion en ingles", "busca una cancion en ingles", "buscar cancion en ingles",
        "pon cancion en ingles", "pon una cancion en ingles", "pon la cancion en ingles",
        "reproduce cancion en ingles", "reproduce una cancion en ingles")

    /** A title is data; it must not be routed as a Spanish command or normalized away. */
    fun title(transcript: String): String? {
        val title = transcript.trim().trim('"', '“', '”')
        if (MinimalIntentRouter.normalize(title) in setOf("cancel", "cancelar", "cancela", "silencio")) return null
        return title.takeIf { it.length in 2..200 && it.any(Char::isLetterOrDigit) }
    }
}
