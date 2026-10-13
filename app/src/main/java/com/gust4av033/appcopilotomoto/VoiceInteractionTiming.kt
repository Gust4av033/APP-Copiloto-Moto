package com.gust4av033.appcopilotomoto

/** Cada plazo pertenece a una etapa: preparar, hablar o escuchar. */
internal object VoiceInteractionTiming {
    const val PREPARATION_MS = 10_000L

    fun playbackMs(text: String): Long = (5_000L + text.length * 150L).coerceIn(15_000L, 90_000L)

    fun appAnnouncement(name: String, draft: String?): String =
        if (draft == null) "Abrir $name. Después del tono, di uno para abrir o dos para cancelar."
        else "Preparar un borrador para $name. El mensaje dice: $draft. No se enviará. Después del tono, di uno para abrir el borrador, dos para descartarlo, tres para dictar de nuevo o cuatro para repetir la lectura."

    fun pendingAppMs(name: String, draft: String?): Long = PREPARATION_MS +
        playbackMs(appAnnouncement(name, draft)) + PREPARATION_MS + 1_000L +
        if (draft == null) PendingAppLaunch.VALIDITY_MS else PendingAppLaunch.DRAFT_VALIDITY_MS
}

enum class SpeechCaptureMode(val listeningMs: Long) {
    COMMAND(15_000L), CONFIRMATION(18_000L), DICTATION(40_000L), SONG_TITLE(20_000L)
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

    fun listeningAt(nowMs: Long): Boolean = listening && nowMs < expiresAtMs
}

/** Un motor puede cerrar por silencio antes del plazo de APP. No alarga el plazo global. */
internal class ConfirmationSilenceRecovery {
    private var restarts = 0

    fun shouldRestart(mode: SpeechCaptureMode, ready: Boolean, voiceStarted: Boolean,
                      silenceError: Boolean, remainingMs: Long): Boolean {
        if (mode == SpeechCaptureMode.COMMAND || !ready || voiceStarted || !silenceError ||
            restarts >= 2 || remainingMs < 2_000L) return false
        restarts++
        return true
    }
}

/** The ready cue may trigger onset without any spoken words. */
internal class SpeechStartEvidence {
    var detected = false
        private set
    private var cueEndsAt: Long? = null
    fun ready(nowMs: Long) { cueEndsAt = nowMs + 400L }
    fun onset(nowMs: Long) {
        if (cueEndsAt?.let { nowMs >= it } == true) detected = true
    }
    fun partial(hasWords: Boolean) { if (hasWords) detected = true }
}

/** Permiso ya concedido, sin desbloquear ni abrir automáticamente desde la pantalla bloqueada. */
internal object TripAppLaunchPolicy {
    fun canOpen(active: Boolean, unlocked: Boolean, activityVisible: Boolean, overlayPermission: Boolean) =
        active && unlocked && (activityVisible || overlayPermission)
}

