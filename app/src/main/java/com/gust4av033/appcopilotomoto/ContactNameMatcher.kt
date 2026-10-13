package com.gust4av033.appcopilotomoto

internal data class PhoneContactCandidate(val id: Long, val contact: TripCallContact)

/** Las coincidencias exactas prevalecen; nunca elige entre personas o números ambiguos. */
internal object ContactNameMatcher {
    fun findUnique(spokenName: String, entries: List<PhoneContactCandidate>): TripCallContact {
        val sought = MinimalIntentRouter.normalize(spokenName)
        require(sought.length >= 2) { "Decí el nombre completo del contacto." }
        val exact = entries.filter { MinimalIntentRouter.normalize(it.contact.name) == sought }
        val matches = exact.ifEmpty {
            entries.filter { MinimalIntentRouter.normalize(it.contact.name).startsWith("$sought ") }
        }.distinctBy { it.id to it.contact.number }
        require(matches.isNotEmpty()) { "No encontré ‘$spokenName’ en tus contactos con teléfono." }
        require(matches.map { it.id }.distinct().size == 1) {
            val names = matches.map { it.contact.name }.distinct().sorted().take(3)
            if (names.size > 1) "Encontré ${names.joinToString(" y ")}. Decí el nombre completo del contacto."
            else "Hay varios contactos guardados como ‘$spokenName’. Poneles nombres distintos en la agenda para poder elegir por voz."
        }
        require(matches.map { it.contact.number }.distinct().size == 1) {
            "${matches.first().contact.name} tiene varios números. Elegí un número como contacto fijo para llamar, o guardalos con nombres distintos para abrir el chat."
        }
        return matches.first().contact
    }
}

