package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class ConversationAudioCycleTest {
    private var time = 1_000L
    private val cycle = ConversationAudioCycle { time }

    @Test fun aSimpleOrderResumesOnceOnlyIfAppPausedIt() {
        val id = cycle.begin("orden", true, true).id
        time += 3_000
        assertTrue(cycle.end(id, ConversationAudioCycle.End.COMPLETED)!!.shouldResume)
        assertNull(cycle.end(id, ConversationAudioCycle.End.COMPLETED))
        val next = cycle.begin("orden", false, false)
        assertFalse(cycle.end(next.id, ConversationAudioCycle.End.ERROR)!!.shouldResume)
    }

    @Test fun dictationAndConfirmationAreOneCycleWithAResettingDeadline() {
        val id = cycle.begin("mensaje", true, true).id
        time += 80_000
        assertFalse(cycle.timedOut())
        cycle.touch(id)
        time += 80_000
        assertFalse(cycle.timedOut())
        time += 10_000
        assertTrue(cycle.timedOut())
        assertTrue(cycle.end(id, ConversationAudioCycle.End.TIMED_OUT)!!.shouldResume)
    }

    @Test fun aCallOrMusicSearchHandsPlaybackToTheOtherApp() {
        val call = cycle.begin("llamada", true, true)
        assertFalse(cycle.end(call.id, ConversationAudioCycle.End.CALL_DISPATCHED)!!.shouldResume)
        val music = cycle.begin("canción", true, true)
        assertFalse(cycle.end(music.id, ConversationAudioCycle.End.MUSIC_REPLACED)!!.shouldResume)
    }

    @Test fun cancelAndErrorsResumeButManualChangesAndIncomingCallDoNot() {
        for (cause in listOf(ConversationAudioCycle.End.CANCELLED,
            ConversationAudioCycle.End.ERROR, ConversationAudioCycle.End.INTERRUPTED)) {
            val id = cycle.begin("confirmación", true, true).id
            assertTrue(cycle.end(id, cause)!!.shouldResume)
        }
        val manualPause = cycle.begin("orden", true, true)
        assertFalse(cycle.end(manualPause.id, ConversationAudioCycle.End.USER_PAUSED)!!.shouldResume)
        val anotherPlayer = cycle.begin("orden", true, true)
        assertFalse(cycle.end(anotherPlayer.id, ConversationAudioCycle.End.COMPLETED,
            otherPlaybackActive = true)!!.shouldResume)
        val incomingCall = cycle.begin("orden", true, true)
        assertFalse(cycle.end(incomingCall.id, ConversationAudioCycle.End.INTERRUPTED,
            callActive = true)!!.shouldResume)
    }

    @Test fun lateCallbackCannotFinishTheNextCycle() {
        val old = cycle.begin("vieja", true, true)
        cycle.end(old.id, ConversationAudioCycle.End.CANCELLED)
        val next = cycle.begin("nueva", false, false)
        assertNull(cycle.end(old.id, ConversationAudioCycle.End.ERROR))
        assertEquals(next.id, cycle.current?.id)
    }
}
