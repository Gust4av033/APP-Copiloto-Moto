package com.gust4av033.appcopilotomoto

enum class PlaybackResumePolicy { NOW, AFTER_ROUTE_RESTORED, KEEP_PAUSED }

/** Conserva la pausa de APP entre una orden y su confirmación. */
internal class SpeechPlaybackState {
    private var pendingResume = false

    fun rememberPause(pausedByApp: Boolean) {
        pendingResume = pendingResume || pausedByApp
    }

    fun finish(policy: PlaybackResumePolicy): Boolean = when (policy) {
        PlaybackResumePolicy.NOW -> takeResume()
        PlaybackResumePolicy.AFTER_ROUTE_RESTORED -> false
        PlaybackResumePolicy.KEEP_PAUSED -> { pendingResume = false; false }
    }

    fun takeResume(): Boolean {
        val resume = pendingResume
        pendingResume = false
        return resume
    }
}
