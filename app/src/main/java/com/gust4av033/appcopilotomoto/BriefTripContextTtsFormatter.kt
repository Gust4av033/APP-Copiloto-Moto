package com.gust4av033.appcopilotomoto

import kotlin.math.roundToInt

/** Short answers from observed data. A straight line is never described as a road route. */
object BriefTripContextTtsFormatter {
    private fun navigator(source: ContextSource) = when (source) {
        ContextSource.WAZE_NOTIFICATION, ContextSource.WAZE_ACCESSIBILITY -> "Waze"
        ContextSource.GOOGLE_MAPS_NOTIFICATION, ContextSource.GOOGLE_MAPS_ACCESSIBILITY -> "Google Maps"
        else -> "el navegador"
    }

    fun nextTurn(snapshot: TripContextSnapshot, nowElapsed: Long): String {
        val turn = snapshot.navigation.nextTurnInstruction
            ?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }
            ?: return "No tengo un próximo giro verificable. Seguí la indicación del navegador."
        val distance = snapshot.navigation.distanceToNextTurnText
            ?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED && it.source == turn.source &&
                it.capturedAtElapsedRealtimeMs == turn.capturedAtElapsedRealtimeMs }
        return "Según ${navigator(turn.source)}, próximo giro: ${turn.value}." +
            (distance?.let { " A ${it.value}." } ?: "")
    }

    fun remaining(snapshot: TripContextSnapshot, nowElapsed: Long): String {
        val eta = snapshot.navigation.remainingMinutes
            ?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }
        if (eta != null) return "Según ${navigator(eta.source)}, faltan ${eta.value} minutos."
        val ownDistance = ownDistance(snapshot, nowElapsed)
        snapshot.lastObservedEta?.takeIf { it.valid(nowElapsed) }?.let { last ->
            val minutesAgo = last.ageMs(nowElapsed) / 60_000
            val age = if (minutesAgo == 0L) "menos de un minuto" else
                "$minutesAgo ${if (minutesAgo == 1L) "minuto" else "minutos"}"
            val provider = if (last.source == EtaSource.GOOGLE_MAPS) "Google Maps" else "Waze"
            return "El último dato de $provider, hace $age, indicaba ${last.remainingMinutes} minutos. " +
                "Ese tiempo puede haber cambiado; no tengo una ETA actual." +
                (ownDistance?.let { " $it" } ?: "")
        }
        if (ownDistance != null) return "No tengo tiempo de ruta. $ownDistance"
        val target = snapshot.target?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }?.value
        return if (target != null)
            "No tengo un tiempo de ruta reciente. Falta una posición GPS precisa para calcular la distancia a ${target.label}."
        else "No tengo un tiempo de ruta reciente ni coordenadas del destino de este viaje."
    }

    private fun ownDistance(snapshot: TripContextSnapshot, nowElapsed: Long): String? {
        val meters = snapshot.straightLineMeters(nowElapsed) ?: return null
        val target = snapshot.target?.value ?: return null
        val distance = when {
            meters < 100 -> "menos de 100 metros"
            meters < 2_000 -> "unos ${(meters / 100).roundToInt() * 100} metros"
            else -> "unos ${(meters / 1000).roundToInt()} kilómetros"
        }
        return "${target.label} está a $distance en línea recta."
    }

    fun status(snapshot: TripContextSnapshot, nowElapsed: Long, movingMs: Long,
        movement: MovementState? = null): String {
        val items = mutableListOf<String>()
        snapshot.sessionDurationMs?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }
            ?.value?.let { items += if (it < 60_000) "viaje recién iniciado"
                else "viaje activo hace ${it / 60_000} minutos" }
        val fix = snapshot.location?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }?.value
        if (fix != null && fix.horizontalAccuracyMeters?.let { it <= 25f } == true) {
            val confirmedMovement = movement?.takeUnless {
                it == MovementState.EN_MARCHA &&
                    (fix.speedMetersPerSec ?: 0f) < MovementStateDetector.MOVING_FROM_MPS
            }
            if (confirmedMovement != null) items += when (confirmedMovement) {
                MovementState.DETENIDO -> "moto detenida"
                MovementState.LENTO -> "avance lento"
                MovementState.EN_MARCHA -> "en marcha"
            }
            fix.speedMetersPerSec?.takeIf { it.isFinite() && it >= 0f &&
                (fix.speedAccuracyMetersPerSec == null || fix.speedAccuracyMetersPerSec <= 2.5f) }
                ?.let { items += "${(it * 3.6).roundToInt()} kilómetros por hora" }
        }
        if (movingMs >= 60_000) items += "${movingMs / 60_000} minutos en movimiento"
        snapshot.media?.track?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }
            ?.let { items += "suena ${it.value}" }
        snapshot.device?.batteryPercent?.takeIf { it.freshness(nowElapsed) != Freshness.EXPIRED }
            ?.let { items += "batería ${it.value} por ciento" }
        return if (items.isEmpty()) "No hay contexto reciente del viaje disponible."
            else items.joinToString("; ", postfix = ".")
    }
}
