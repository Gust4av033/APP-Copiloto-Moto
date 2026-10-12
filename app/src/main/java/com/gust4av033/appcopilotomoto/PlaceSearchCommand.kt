package com.gust4av033.appcopilotomoto

import java.net.URLEncoder

/** Busca texto en un proveedor explícito; no adivina qué resultado sería el destino. */
internal object PlaceSearchParser {
    const val MAX_LENGTH = 160

    fun parse(transcript: String): String? {
        val spoken = transcript.trim()
            .replace(Regex("^(?:por favor[,\\s]+|puedes |podrias |podrías )+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[,\\s]+(?:por favor|gracias)[.!?]*$", RegexOption.IGNORE_CASE), "")
        val match = Regex("^(?:busca|buscar|buscame|búscame|encuentra|encontrar)\\s+(?:en\\s+waze\\s+(.+)|(.+)\\s+en\\s+waze)[.!?]*$",
            RegexOption.IGNORE_CASE).matchEntire(spoken) ?: return null
        val query = match.groupValues[1].ifBlank { match.groupValues[2] }.trim().trimEnd('.', '!', '?')
        if (!valid(query) || Regex("\\by (?:llama|abre|sube|baja|cancela|deten|envia)\\b")
                .containsMatchIn(MinimalIntentRouter.normalize(query))) return null
        return query
    }

    fun valid(query: String): Boolean = query.length in 2..MAX_LENGTH &&
        query.any(Char::isLetterOrDigit) && query.none(Char::isISOControl)
}

internal fun wazePlaceSearchLink(query: String): String {
    require(PlaceSearchParser.valid(query)) { "Pedí un lugar breve, de hasta 160 caracteres." }
    return "https://waze.com/ul?q=" + URLEncoder.encode(query, "UTF-8").replace("+", "%20") +
        "&utm_source=app_copiloto_moto"
}
