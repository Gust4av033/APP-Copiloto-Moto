package com.gust4av033.appcopilotomoto

/** One rider request, including prompts, dictation and confirmation. No Android dependency. */
internal class ConversationAudioCycle(private val nowMs: () -> Long) {
    companion object { const val INACTIVITY_TIMEOUT_MS = 90_000L }

    enum class End { COMPLETED, CANCELLED, TIMED_OUT, ERROR, CALL_DISPATCHED, MUSIC_REPLACED, USER_PAUSED, INTERRUPTED }

    data class Session(val id: Long, val reason: String, val startedAtMs: Long,
        val lastActivityAtMs: Long, val wasPlayingBeforeCycle: Boolean,
        val pausedByApp: Boolean)
    data class Ended(val session: Session, val cause: End, val durationMs: Long,
        val shouldResume: Boolean)

    private var nextId = 0L
    var current: Session? = null
        private set

    fun begin(reason: String, wasPlaying: Boolean, pausedByApp: Boolean): Session {
        check(current == null) { "A conversation is already active" }
        val time = nowMs()
        return Session(++nextId, reason, time, time, wasPlaying, pausedByApp).also { current = it }
    }

    fun touch(id: Long) {
        current?.takeIf { it.id == id }?.let { current = it.copy(lastActivityAtMs = nowMs()) }
    }

    fun rememberPause(id: Long, pausedByApp: Boolean) {
        if (pausedByApp) current?.takeIf { it.id == id }?.let { current = it.copy(pausedByApp = true) }
    }

    fun timedOut(): Boolean = current?.let { nowMs() - it.lastActivityAtMs >= INACTIVITY_TIMEOUT_MS } == true

    fun end(id: Long, cause: End, otherPlaybackActive: Boolean = false,
        callActive: Boolean = false): Ended? {
        val session = current?.takeIf { it.id == id } ?: return null
        current = null
        val resume = session.pausedByApp && !otherPlaybackActive && !callActive && cause !in setOf(
            End.CALL_DISPATCHED, End.MUSIC_REPLACED, End.USER_PAUSED)
        return Ended(session, cause, (nowMs() - session.startedAtMs).coerceAtLeast(0L), resume)
    }
}
