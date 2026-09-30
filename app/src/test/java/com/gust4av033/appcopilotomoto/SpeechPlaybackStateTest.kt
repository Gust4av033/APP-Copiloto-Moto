package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPlaybackStateTest {
    @Test fun callConfirmationKeepsTheOriginalPauseUntilAudioIsRestored() {
        val state = SpeechPlaybackState()
        state.rememberPause(true)
        assertFalse(state.finish(PlaybackResumePolicy.AFTER_ROUTE_RESTORED))
        // Reiniciar STT transfiere la pausa sin enviar PLAY entre orden y confirmación.
        val transferred = state.takeResume()
        state.rememberPause(transferred)
        state.rememberPause(false)
        assertFalse(state.finish(PlaybackResumePolicy.AFTER_ROUTE_RESTORED))
        assertTrue(state.takeResume())
        assertFalse(state.takeResume())
    }

    @Test fun userPauseCancelsAnEarlierAutomaticResume() {
        val state = SpeechPlaybackState()
        state.rememberPause(true)
        assertFalse(state.finish(PlaybackResumePolicy.KEEP_PAUSED))
        assertFalse(state.takeResume())
    }

    @Test fun alreadyPausedMusicDoesNotStartAfterAnError() {
        val state = SpeechPlaybackState()
        state.rememberPause(false)
        assertFalse(state.finish(PlaybackResumePolicy.AFTER_ROUTE_RESTORED))
        assertFalse(state.takeResume())
    }

    @Test fun immediateResumeIsConsumedOnlyOnce() {
        val state = SpeechPlaybackState()
        state.rememberPause(true)
        assertTrue(state.finish(PlaybackResumePolicy.NOW))
        assertFalse(state.takeResume())
    }
}
