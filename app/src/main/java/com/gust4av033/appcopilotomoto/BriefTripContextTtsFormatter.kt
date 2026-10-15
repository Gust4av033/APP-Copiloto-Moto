package com.gust4av033.appcopilotomoto

import kotlin.math.roundToInt

object BriefTripContextTtsFormatter {
    fun route(snapshot: TripContextSnapshot, nowElapsed: Long): String {
        val nav = snapshot.navigation
        val turn = nav.nextTurnInstruction?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }
        val distance = nav.distanceToNextTurnText?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }
        val eta = nav.remainingMinutes?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }
        if (turn == null && eta == null) return "No tengo una ruta externa reciente. Revisá el navegador al detenerte."
        return buildString {
            if (turn != null) append("Próximo giro: ${turn.value}.")
            if (distance != null) append(" A ${distance.value}.")
            if (eta != null) append(" Faltan ${eta.value} minutos según el navegador.")
            snapshot.straightLineMeters(nowElapsed)?.let {
                append(" Destino a unos ${(it / 1000).roundToInt()} kilómetros en línea recta.")
            }
        }.trim()
    }
    fun status(snapshot: TripContextSnapshot, nowElapsed: Long, movingMs: Long): String {
        val items = mutableListOf<String>()
        snapshot.location?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }
            ?.value?.speedMetersPerSec?.let { items += "${(it * 3.6).roundToInt()} kilómetros por hora" }
        if (movingMs > 0) items += "${movingMs / 60_000} minutos de viaje"
        snapshot.media?.track?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }
            ?.let { items += "suena ${it.value}" }
        snapshot.device?.batteryPercent?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }
            ?.let { items += "batería ${it.value} por ciento" }
        return if (items.isEmpty()) "No hay contexto reciente del viaje disponible."
            else items.joinToString("; ", postfix = ".")
    }
}
