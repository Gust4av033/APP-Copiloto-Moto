package com.gust4av033.appcopilotomoto

/** Developer opt-in is separate from the build type; release tools are read-only. */
data class DeveloperAccess(val enabled: Boolean = false, val debugBuild: Boolean = false) {
    val canViewDiagnostics: Boolean get() = enabled
    val canRunExperiments: Boolean get() = enabled && debugBuild
}

/** Seven consecutive taps followed by a separate confirmation; never saved in a Bundle. */
class DeveloperAccessChallenge {
    private var count = 0
    private var startedAt = 0L
    private var lastTapAt = 0L
    private var awaitingConfirmation = false

    fun tap(now: Long): Boolean {
        if (awaitingConfirmation) return false
        if (count == 0 || now < lastTapAt || now - lastTapAt > 2_000 || now - startedAt > 15_000) {
            count = 0
            startedAt = now
        }
        lastTapAt = now
        count++
        awaitingConfirmation = count == 7
        return awaitingConfirmation
    }

    fun confirm(): Boolean {
        val allowed = awaitingConfirmation
        reset()
        return allowed
    }

    fun reset() { count = 0; awaitingConfirmation = false; startedAt = 0; lastTapAt = 0 }
}

enum class SensitivityPreset(val value: Float) {
    LOW(60f), BALANCED(80f), HIGH(85f);
    companion object {
        fun closest(value: Float): SensitivityPreset = entries.minBy { kotlin.math.abs(it.value - value) }
    }
}
