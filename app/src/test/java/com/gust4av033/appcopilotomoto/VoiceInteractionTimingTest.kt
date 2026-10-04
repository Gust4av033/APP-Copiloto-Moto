package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class VoiceInteractionTimingTest {
    @Test fun slowPreparationStillLeavesAFullConfirmationOrDictationWindow() {
        val confirmation = SpeechCaptureDeadline(SpeechCaptureMode.CONFIRMATION, 100L)
        assertTrue(confirmation.ready(9_100L))
        assertEquals(21_100L, confirmation.expiresAtMs)
        assertFalse(confirmation.ready(10_100L))
        assertEquals(21_100L, confirmation.expiresAtMs)

        val dictation = SpeechCaptureDeadline(SpeechCaptureMode.DICTATION, 100L)
        assertTrue(dictation.ready(9_100L))
        assertEquals(49_100L, dictation.expiresAtMs)
    }

    @Test fun readyAfterPreparationExpiredCannotReopenTheMicWindow() {
        val deadline = SpeechCaptureDeadline(SpeechCaptureMode.DICTATION, 100L)
        assertFalse(deadline.ready(10_100L))
        assertEquals(10_100L, deadline.expiresAtMs)
    }

    @Test fun fullDraftHasEnoughBudgetForSlowReadbackAndResponse() {
        val draft = "a".repeat(240)
        val spoken = VoiceInteractionTiming.appAnnouncement("Ana", draft)
        val playback = VoiceInteractionTiming.playbackMs(spoken)
        assertTrue(playback > 28_000L)
        val total = VoiceInteractionTiming.pendingAppMs("Ana", draft)
        assertTrue(total >= VoiceInteractionTiming.PREPARATION_MS + playback +
            VoiceInteractionTiming.PREPARATION_MS + PendingAppLaunch.DRAFT_VALIDITY_MS)
        assertTrue(total <= PendingAppLaunch.MAX_VALIDITY_MS)
        assertTrue(VoiceInteractionTiming.pendingAppMs("a".repeat(5000), draft) <= PendingAppLaunch.MAX_VALIDITY_MS)
    }

    @Test fun backgroundTripCanOpenOnlyWithPermissionAndUnlockedPhone() {
        assertTrue(TripAppLaunchPolicy.canOpen(true, true, false, true))
        assertTrue(TripAppLaunchPolicy.canOpen(true, true, true, false))
        assertFalse(TripAppLaunchPolicy.canOpen(true, true, false, false))
        assertFalse(TripAppLaunchPolicy.canOpen(true, false, true, true))
        assertFalse(TripAppLaunchPolicy.canOpen(false, true, true, true))
    }

    @Test fun commonSpokenNumbersStillRequireAnUnambiguousChoice() {
        listOf("uno", "1", "el uno", "número uno", "número 1").forEach { assertEquals(AppChoice.OPEN, AppChoiceRouter.resolve(it)) }
        listOf("dos", "2", "el dos", "número dos", "número 2").forEach { assertEquals(AppChoice.CANCEL, AppChoiceRouter.resolve(it)) }
        listOf("uno o dos", "no abras", "uno dos").forEach { assertEquals(AppChoice.UNKNOWN, AppChoiceRouter.resolve(it)) }
    }
}
