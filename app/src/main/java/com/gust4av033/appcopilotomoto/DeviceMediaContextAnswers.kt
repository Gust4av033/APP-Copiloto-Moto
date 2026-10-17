package com.gust4av033.appcopilotomoto

/** Deterministic answers from current evidence; never infers a song or battery level. */
internal object DeviceMediaContextAnswers {
    fun playing(snapshot: TripContextSnapshot, nowElapsedMs: Long): String {
        val item = snapshot.music?.takeIf { it.freshness(nowElapsedMs) != Freshness.EXPIRED }
            ?.value ?: return "No tengo una sesión multimedia reciente para saber qué suena."
        return when {
            item.playing == false -> "No hay música sonando ahora."
            item.playing == null -> "No puedo confirmar qué suena en ${item.playerLabel} ahora."
            item.title.isNullOrBlank() -> "Suena música en ${item.playerLabel}, sin nombre de pista disponible."
            item.artist.isNullOrBlank() -> "Suena ${item.title} en ${item.playerLabel}."
            else -> "Suena ${item.title} de ${item.artist} en ${item.playerLabel}."
        }
    }

    fun battery(snapshot: TripContextSnapshot, nowElapsedMs: Long): String {
        val item = snapshot.battery?.takeIf { it.freshness(nowElapsedMs) != Freshness.EXPIRED }
            ?.value ?: return "No tengo una lectura reciente de batería."
        return "Te queda ${item.percent} por ciento de batería." +
            if (item.charging) " Está cargando." else ""
    }
}
