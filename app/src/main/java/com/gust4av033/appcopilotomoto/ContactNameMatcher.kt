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
            "Hay varios contactos llamados ‘$spokenName’. Decí el nombre completo."
        }
        require(matches.map { it.contact.number }.distinct().size == 1) {
            "Ese contacto tiene varios números; no se eligió ninguno. Usá un contacto con un único número para esta orden."
        }
        return matches.first().contact
    }
}
