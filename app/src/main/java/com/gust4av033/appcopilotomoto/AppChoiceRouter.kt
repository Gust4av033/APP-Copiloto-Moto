package com.gust4av033.appcopilotomoto

internal enum class AppChoice { OPEN, CANCEL, UNKNOWN }

/** Used only for the follow-up to a live app request, never for general commands. */
internal object AppChoiceRouter {
    fun resolve(heard: String?): AppChoice = when (heard?.let(MinimalIntentRouter::normalize)) {
        "1", "uno", "opcion 1", "opcion uno", "abrir", "abre", "abrir aplicacion" -> AppChoice.OPEN
        "2", "dos", "opcion 2", "opcion dos", "cancelar", "cancela", "cancelar apertura" -> AppChoice.CANCEL
        else -> AppChoice.UNKNOWN
    }
}
