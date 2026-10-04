package com.gust4av033.appcopilotomoto

/** Limita la recuperación del dueño real del botón, incluso si otro reproductor lo disputa. */
internal class TripButtonOwnership {
    private var waiting = false
    private var attempts = 0
    private var nextAttemptAtMs = 0L

    fun beginWaiting() { waiting = true; attempts = 0; nextAttemptAtMs = 0L }
    fun suspend() { waiting = false; attempts = 0; nextAttemptAtMs = 0L }

    fun shouldRecover(owned: Boolean, nowMs: Long): Boolean {
        if (!waiting) return false
        if (owned) { attempts = 0; return false }
        if (attempts >= 3 || nowMs < nextAttemptAtMs) return false
        attempts++
        nextAttemptAtMs = nowMs + 1_000L
        return true
    }
}
