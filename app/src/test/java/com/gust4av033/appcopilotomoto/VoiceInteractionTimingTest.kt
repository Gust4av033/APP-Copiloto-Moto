package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class VoiceInteractionTimingTest {
    @Test fun cancellationFeedbackFitsBeforeRecoveryAndEveryConfirmationHasTheSameBudget() {
        assertTrue(VoiceInteractionTiming.CANCEL_CUE_MS < VoiceInteractionTiming.RECOVERY_MS)
        assertTrue(VoiceInteractionTiming.READY_CUE_MS < VoiceInteractionTiming.CUE_SPEECH_GUARD_MS)
        listOf("app", "llamada", "borrador").forEach {
            val window = SpeechCaptureDeadline(SpeechCaptureMode.CONFIRMATION, 0)
            assertTrue(window.ready(VoiceInteractionTiming.TTS_DRAIN_MS + VoiceInteractionTiming.ROUTE_POLL_MS))
            assertEquals(18_000L, window.expiresAtMs - VoiceInteractionTiming.TTS_DRAIN_MS - VoiceInteractionTiming.ROUTE_POLL_MS)
        }
    }
    @Test fun slowPreparationStillLeavesAFullConfirmationOrDictationWindow() {
        val confirmation = SpeechCaptureDeadline(SpeechCaptureMode.CONFIRMATION, 100L)
        assertTrue(confirmation.ready(9_100L))
        assertEquals(27_100L, confirmation.expiresAtMs)
        assertFalse(confirmation.ready(10_100L))
        assertEquals(27_100L, confirmation.expiresAtMs)

        val dictation = SpeechCaptureDeadline(SpeechCaptureMode.DICTATION, 100L)
        assertTrue(dictation.ready(9_100L))
        assertEquals(49_100L, dictation.expiresAtMs)
    }

    @Test fun readyAfterPreparationExpiredCannotReopenTheMicWindow() {
        val deadline = SpeechCaptureDeadline(SpeechCaptureMode.DICTATION, 100L)
        assertFalse(deadline.ready(10_100L))
        assertEquals(10_100L, deadline.expiresAtMs)
    }

    @Test fun confirmationRestartsUseTheOriginalDeadlineAndStopBeforeItExpires() {
        val deadline = SpeechCaptureDeadline(SpeechCaptureMode.CONFIRMATION, 0)
        assertFalse(deadline.listeningAt(100))
        assertTrue(deadline.ready(500))
        val end = deadline.expiresAtMs
        assertTrue(deadline.listeningAt(8_000))
        assertFalse(deadline.ready(8_000))
        assertEquals(end, deadline.expiresAtMs)
        assertFalse(deadline.listeningAt(end))
        assertFalse(deadline.ready(end + 500))
    }

    @Test fun earlySilenceHasAtMostTwoRestartsWithoutRestartingRecognizedSpeech() {
        val recovery = ConfirmationSilenceRecovery()
        assertFalse(recovery.shouldRestart(SpeechCaptureMode.COMMAND, true, false, true, 10_000))
        assertFalse(recovery.shouldRestart(SpeechCaptureMode.CONFIRMATION, false, false, true, 10_000))
        assertFalse(recovery.shouldRestart(SpeechCaptureMode.CONFIRMATION, true, true, true, 10_000))
        assertFalse(recovery.shouldRestart(SpeechCaptureMode.CONFIRMATION, true, false, false, 10_000))
        assertFalse(recovery.shouldRestart(SpeechCaptureMode.CONFIRMATION, true, false, true, 1_999))
        assertTrue(recovery.shouldRestart(SpeechCaptureMode.CONFIRMATION, true, false, true, 10_000))
        assertTrue(recovery.shouldRestart(SpeechCaptureMode.CONFIRMATION, true, false, true, 5_000))
        assertFalse(recovery.shouldRestart(SpeechCaptureMode.CONFIRMATION, true, false, true, 3_000))
    }

    @Test fun dictationAndEnglishTitlesRecoverEarlySilenceWithoutRepeatingSpeech() {
        listOf(SpeechCaptureMode.DICTATION, SpeechCaptureMode.SONG_TITLE).forEach { mode ->
            val recovery = ConfirmationSilenceRecovery()
            assertTrue(recovery.shouldRestart(mode, true, false, true, 10_000))
            assertFalse(recovery.shouldRestart(mode, true, true, true, 9_000))
            assertFalse(recovery.shouldRestart(mode, true, false, false, 9_000))
            assertTrue(recovery.shouldRestart(mode, true, false, true, 8_000))
            assertFalse(recovery.shouldRestart(mode, true, false, true, 6_000))
        }
    }

    @Test fun readyCueOnsetDoesNotDisableSilenceRecovery() {
        val evidence = SpeechStartEvidence()
        evidence.onset(0)
        assertFalse(evidence.detected)
        evidence.ready(100)
        evidence.onset(300)
        assertFalse(evidence.detected)
        assertTrue(ConfirmationSilenceRecovery().shouldRestart(SpeechCaptureMode.DICTATION,
            true, evidence.detected, true, 10_000))
        evidence.onset(500)
        assertTrue(evidence.detected)
    }

    @Test fun realPartialWordsDuringTheCueStillCountAsSpeech() {
        val evidence = SpeechStartEvidence()
        evidence.ready(100)
        evidence.partial(false)
        assertFalse(evidence.detected)
        evidence.partial(true)
        assertTrue(evidence.detected)
        evidence.ready(1_000)
        assertTrue(evidence.detected)
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


