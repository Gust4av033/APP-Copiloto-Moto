package com.gust4av033.appcopilotomoto

/** Cada plazo pertenece a una etapa: preparar, hablar o escuchar. */
internal object VoiceInteractionTiming {
    const val PREPARATION_MS = 10_000L

    fun playbackMs(text: String): Long = (5_000L + text.length * 150L).coerceIn(15_000L, 90_000L)

    fun appAnnouncement(name: String, draft: String?): String =
        if (draft == null) "Abrir $name. Después del tono, di uno para abrir o dos para cancelar."
        else "Preparar un borrador para $name. El mensaje dice: $draft. No se enviará. Después del tono, di uno para abrir el borrador o dos para descartarlo."

    fun pendingAppMs(name: String, draft: String?): Long = PREPARATION_MS +
        playbackMs(appAnnouncement(name, draft)) + PREPARATION_MS + 1_000L +
        if (draft == null) PendingAppLaunch.VALIDITY_MS else PendingAppLaunch.DRAFT_VALIDITY_MS
}

enum class SpeechCaptureMode(val listeningMs: Long) {
    COMMAND(15_000L), CONFIRMATION(12_000L), DICTATION(40_000L)
}

/** READY abre una sola ventana nueva; la preparación no resta tiempo al dictado. */
internal class SpeechCaptureDeadline(private val mode: SpeechCaptureMode, startedAtMs: Long) {
    var expiresAtMs = startedAtMs + VoiceInteractionTiming.PREPARATION_MS
        private set
    private var listening = false

    fun ready(nowMs: Long): Boolean {
        if (listening || nowMs >= expiresAtMs) return false
        listening = true
        expiresAtMs = nowMs + mode.listeningMs
        return true
    }
}

/** Permiso ya concedido, sin desbloquear ni abrir automáticamente desde la pantalla bloqueada. */
internal object TripAppLaunchPolicy {
    fun canOpen(active: Boolean, unlocked: Boolean, activityVisible: Boolean, overlayPermission: Boolean) =
        active && unlocked && (activityVisible || overlayPermission)
}
