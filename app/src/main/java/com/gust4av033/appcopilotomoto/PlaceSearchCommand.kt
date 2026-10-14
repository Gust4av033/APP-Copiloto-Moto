package com.gust4av033.appcopilotomoto

import java.net.URLEncoder

/** Busca texto en el proveedor elegido o indicado; no adivina qué resultado sería el destino. */
internal object PlaceSearchParser {
    const val MAX_LENGTH = 160

    data class Request(val query: String, val provider: NavigationApp?)

    fun parse(transcript: String): String? = parseRequest(transcript)?.query

    fun parseRequest(transcript: String): Request? {
        val spoken = transcript.trim()
            .replace(Regex("^(?:por favor[,\\s]+|puedes |podrias |podrías )+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[,\\s]+(?:por favor|gracias)[.!?]*$", RegexOption.IGNORE_CASE), "")
        val prefix = Regex("^(?:busca|buscar|buscame|búscame|encuentra|encontrar)\\s+(.+)$",
            RegexOption.IGNORE_CASE).matchEntire(spoken)?.groupValues?.get(1) ?: return null
        val phrase = prefix.trim().trimEnd('.', '!', '?').trim()
        val leading = Regex("^en\\s+(waze|google maps)\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(phrase)
        val trailing = Regex("^(.+)\\s+en\\s+(waze|google maps)$", RegexOption.IGNORE_CASE).matchEntire(phrase)
        val providerName = leading?.groupValues?.get(1) ?: trailing?.groupValues?.get(2)
        val provider = when (providerName?.lowercase()) {
            "waze" -> NavigationApp.WAZE
            "google maps" -> NavigationApp.GOOGLE_MAPS
            else -> null
        }
        val query = (leading?.groupValues?.get(2) ?: trailing?.groupValues?.get(1) ?: phrase)
            .trim().trimEnd('.', '!', '?')
        if (!valid(query) || Regex("\\by (?:llama|abre|sube|baja|cancela|deten|envia)\\b")
                .containsMatchIn(MinimalIntentRouter.normalize(query))) return null
        if (provider == null && (query.startsWith("en ", ignoreCase = true) ||
                Regex("^(?:la )?(?:canción|cancion|song|pista|track)\\b", RegexOption.IGNORE_CASE).containsMatchIn(query))) return null
        return Request(query, provider)
    }

    fun valid(query: String): Boolean = query.length in 2..MAX_LENGTH &&
        query.any(Char::isLetterOrDigit) && query.none(Char::isISOControl)
}

internal fun wazePlaceSearchLink(query: String): String {
    require(PlaceSearchParser.valid(query)) { "Pedí un lugar breve, de hasta 160 caracteres." }
    return "https://waze.com/ul?q=" + URLEncoder.encode(query, "UTF-8").replace("+", "%20") +
        "&utm_source=app_copiloto_moto"
}
