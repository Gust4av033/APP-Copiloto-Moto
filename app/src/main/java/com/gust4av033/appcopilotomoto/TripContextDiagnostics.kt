package com.gust4av033.appcopilotomoto

object TripContextDiagnostics {
    fun report(snapshot: TripContextSnapshot, nowElapsed: Long, state: MovementState,
        samples: Int, events: Int): String = buildString {
        fun <T> line(label: String, value: ContextValue<T>?) {
            appendLine("$label: ${value?.let { "${it.value} · ${it.source} · ${it.ageMs(nowElapsed) / 1000}s · ${it.freshness(nowElapsed)}" } ?: "sin dato"}")
        }
        appendLine("Capa 1 · teléfono")
        line("GPS", snapshot.location?.let { ContextValue("${it.value.latitude},${it.value.longitude}; ${it.value.speedMetersPerSec} m/s; precisión ${it.value.horizontalAccuracyMeters} m",
            it.source, it.capturedAtEpochMs, it.capturedAtElapsedRealtimeMs, it.confidence, it.ttlMs) })
        appendLine("Movimiento: $state · $samples muestras · $events eventos")
        appendLine("Capa 3 · destino")
        line("Objetivo", snapshot.target)
        appendLine("Distancia lineal: ${snapshot.straightLineMeters(nowElapsed)?.toInt() ?: "sin dato"} m")
        appendLine("Rumbo al destino: ${snapshot.targetBearingDegrees(nowElapsed)?.toInt() ?: "sin dato"}°")
        appendLine("Capa 4 · navegador observado")
        line("Proveedor", snapshot.navigation.activeProvider)
        line("Giro", snapshot.navigation.nextTurnInstruction)
        line("Distancia al giro", snapshot.navigation.distanceToNextTurnText)
        line("Calle", snapshot.navigation.currentOrTargetStreet)
        line("ETA", snapshot.navigation.etaText)
        line("Alerta", snapshot.navigation.routeAlertText)
        appendLine("ETA observado: ${snapshot.navigation.etaObservation?.takeIf { it.valid(nowElapsed) } ?: "sin dato vigente"}")
        appendLine("Multimedia y dispositivo")
        line("Pista", snapshot.media?.track)
        line("Artista", snapshot.media?.artist)
        line("Reproductor", snapshot.media?.app)
        line("Reproducción", snapshot.media?.playback)
        line("Batería", snapshot.device?.batteryPercent)
        line("Carga", snapshot.device?.charging)
        line("Audio Bluetooth confirmado", snapshot.device?.bluetoothAudioConfirmed)
    }
}
