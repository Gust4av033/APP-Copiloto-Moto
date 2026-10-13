package com.gust4av033.appcopilotomoto

/** Sólo se consulta al revisar un borrador vivo, nunca al dictar su contenido. */
internal object DraftChoiceRouter {
    fun resolve(heard: String?): AppChoice = when (heard?.let(MinimalIntentRouter::normalize)) {
        "3", "tres", "opcion tres", "opcion 3", "numero tres", "el tres",
        "dictar de nuevo", "volver a dictar", "corregir mensaje", "cambiar mensaje" -> AppChoice.REDICTATE
        "4", "cuatro", "opcion cuatro", "opcion 4", "numero cuatro", "el cuatro",
        "repetir", "repite", "repetir mensaje", "repite mensaje", "repite el mensaje",
        "repetir lectura", "lee de nuevo", "leer de nuevo" -> AppChoice.REPEAT
        else -> AppChoiceRouter.resolve(heard)
    }
}
