package com.gust4av033.appcopilotomoto

/** Una ráfaga aplaza la próxima captura; no termina el viaje. Usa tiempo monotónico. */
internal class WakeActivationGate {
    private val recent = ArrayDeque<Long>()
    private var quietUntilMs = 0L

    fun reset() { recent.clear(); quietUntilMs = 0L }

    fun waitBeforeCapture(nowMs: Long): Long {
        if (nowMs < quietUntilMs) return quietUntilMs - nowMs
        while (recent.isNotEmpty() && nowMs - recent.first() > WINDOW_MS) recent.removeFirst()
        if (recent.size >= MAX_CAPTURES) {
            recent.clear()
            quietUntilMs = nowMs + COOLDOWN_MS
            return COOLDOWN_MS
        }
        recent.addLast(nowMs)
        return 0L
    }

    companion object {
        const val WINDOW_MS = 12_000L
        const val MAX_CAPTURES = 3
        const val COOLDOWN_MS = 8_000L
    }
}
