package com.gust4av033.appcopilotomoto

enum class PipelineEvent {
    ACTIVATION_ATTEMPT_ANNOTATED, FALSE_POSITIVE_ANNOTATED, EXTERNAL_EFFECT_ANNOTATED,
    AUDIO_RESTORED_AUDIBLE_ANNOTATED, MANUAL_ACTIVATION, WAKE_DETECTION,
    BUTTON_DOWN_RECEIVED, BUTTON_ACTIVATION, STT_STARTED, STT_SUCCESS, STT_FAILURE, STT_CANCELLED,
    INTENT_RESOLVED, INTENT_UNKNOWN, ACTION_DISPATCHED, ACTION_CONFIRMED_BY_READBACK,
    AUDIO_ROUTE_RESTORE_SUCCESS, AUDIO_ROUTE_RESTORE_FAILURE, SESSION_UNEXPECTED_END
}

/** Contadores por etapa. Despacho y confirmación externa nunca son equivalentes. */
data class TripPipelineMetrics(
    val counts: Map<PipelineEvent, Int> = emptyMap(),
    val lastSttMs: Long = 0L,
    val lastCaptureContext: String = "Sin captura.",
    val lastEndReason: String = "Sin cierre inesperado.",
    val lastButtonStatus: String = "Sin botón dedicado."
) {
    fun count(event: PipelineEvent) = counts[event] ?: 0
    fun record(event: PipelineEvent) = copy(counts = counts + (event to count(event) + 1))
    fun report(): String = buildString {
        appendLine("Pipeline · despacho ≠ efecto externo confirmado; anotaciones = observación manual")
        PipelineEvent.entries.forEach { appendLine("${it.name.lowercase()}=${count(it)}") }
        appendLine("stt_latency_ms=$lastSttMs")
        appendLine("Captura: $lastCaptureContext")
        appendLine("Botón: $lastButtonStatus")
        append("Último cierre inesperado: $lastEndReason")
    }
}
