package com.gust4av033.appcopilotomoto

/** The port only exposes ducking, speech and release. No pause or capture operation exists. */
internal interface InformationalAudioPort {
    fun requestDucking(): Boolean
    fun speak(text: String, onFinished: (Boolean) -> Unit)
    fun stop()
    fun releaseDucking()
}

internal fun interface InformationalSpeechTimer {
    fun schedule(delayMs: Long, action: () -> Unit): () -> Unit
}

internal class InformationalSpeechCycle(private val port: InformationalAudioPort,
    private val timer: InformationalSpeechTimer) {
    companion object { const val MAX_SPEECH_MS = 6_000L }
    private var generation = 0
    private var pendingCancel: (() -> Unit)? = null
    var isSpeaking = false
        private set

    fun speak(text: String, onFinished: (Boolean) -> Unit = {}): Boolean {
        if (isSpeaking || !port.requestDucking()) return false
        isSpeaking = true
        val token = ++generation
        fun finish(success: Boolean) {
            if (!isSpeaking || generation != token) return
            isSpeaking = false
            generation++
            pendingCancel?.invoke()
            pendingCancel = null
            if (!success) port.stop()
            port.releaseDucking()
            onFinished(success)
        }
        pendingCancel = timer.schedule(MAX_SPEECH_MS) { finish(false) }
        runCatching { port.speak(text) { finish(it) } }.onFailure { finish(false) }
        return true
    }

    fun cancel() {
        if (!isSpeaking) return
        isSpeaking = false
        generation++
        pendingCancel?.invoke()
        pendingCancel = null
        port.stop()
        port.releaseDucking()
    }
}
