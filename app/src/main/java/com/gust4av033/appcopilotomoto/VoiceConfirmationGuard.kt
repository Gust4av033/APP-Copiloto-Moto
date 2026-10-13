package com.gust4av033.appcopilotomoto

/** A confirmation survives the TTS→route→STT handoff, but can be consumed only once. */
internal class VoiceConfirmationGuard {
    private var current: Int? = null
    val pending: Boolean get() = current != null
    fun begin(cycle: Int) { current = cycle }
    fun isCurrent(cycle: Int) = current == cycle
    fun take(cycle: Int): Boolean {
        if (!isCurrent(cycle)) return false
        current = null
        return true
    }
    fun cancel() { current = null }
}
