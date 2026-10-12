package com.gust4av033.appcopilotomoto

internal enum class AppChoice { OPEN, CANCEL, REDICTATE, REPEAT, UNKNOWN }

/** Used only for the follow-up to a live app request, never for general commands. */
internal object AppChoiceRouter {
    fun resolve(heard: String?): AppChoice = when (heard?.let(MinimalIntentRouter::normalize)) {
        "1", "uno", "opcion 1", "opcion uno", "el uno", "numero uno", "numero 1", "abrir", "abre", "abrir aplicacion" -> AppChoice.OPEN
        "2", "dos", "opcion 2", "opcion dos", "el dos", "numero dos", "numero 2", "cancelar", "cancela", "cancelar apertura" -> AppChoice.CANCEL
        else -> AppChoice.UNKNOWN
    }
}
