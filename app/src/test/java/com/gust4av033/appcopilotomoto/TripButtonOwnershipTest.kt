package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class TripButtonOwnershipTest {
    @Test fun musicAlreadyPlayingAndMusicResumedBothTriggerRecovery() {
        val owner = TripButtonOwnership()
        owner.beginWaiting()
        assertTrue(owner.shouldRecover(false, 0L))
        assertFalse(owner.shouldRecover(true, 350L))
        assertTrue(owner.shouldRecover(false, 1_500L))
        assertFalse(owner.shouldRecover(true, 1_850L))
    }

    @Test fun suspendedOrClosedCoordinatorCannotRecoverFromALateCallback() {
        val owner = TripButtonOwnership()
        owner.beginWaiting()
        owner.suspend()
        assertFalse(owner.shouldRecover(false, 5_000L))
        owner.beginWaiting()
        assertTrue(owner.shouldRecover(false, 5_001L))
        owner.suspend()
        assertFalse(owner.shouldRecover(false, 20_000L))
    }

    @Test fun playerThatKeepsTheButtonDoesNotCauseAnEndlessAudioRestart() {
        val owner = TripButtonOwnership()
        owner.beginWaiting()
        assertTrue(owner.shouldRecover(false, 0L))
        assertFalse(owner.shouldRecover(false, 100L))
        assertTrue(owner.shouldRecover(false, 1_000L))
        assertTrue(owner.shouldRecover(false, 2_000L))
        assertFalse(owner.shouldRecover(false, 3_000L))
        assertFalse(owner.shouldRecover(false, 30_000L))
    }

    @Test fun tenBluetoothCommandsWithZeroTimestampsReturnToWaitingAndKeepPreviousReserved() {
        val owner = TripButtonOwnership()
        val buttons = TripButtonPolicy()
        repeat(10) { i ->
            val time = i * 5_000L
            owner.beginWaiting()
            assertTrue(owner.shouldRecover(false, time))
            assertFalse(owner.shouldRecover(true, time + 350L))
            assertEquals(ButtonDecision.ACTIVATE, buttons.decide(TripButtonBinding.PREVIOUS,
                true, true, 88, true, 0, 0, -1, time))
            owner.suspend()
            assertFalse(owner.shouldRecover(false, time + 400L))
            assertEquals(ButtonDecision.CONSUME, buttons.decide(TripButtonBinding.PREVIOUS,
                true, false, 88, false, 0, 0, -1, time + 400L))
            assertEquals(ButtonDecision.PASS, buttons.decide(TripButtonBinding.PREVIOUS,
                true, false, 87, true, 0, time + 500L, 1))
        }
        owner.suspend()
        assertEquals(ButtonDecision.PASS, buttons.decide(TripButtonBinding.PREVIOUS,
            false, false, 88, true, 0, 60_000L, 1))
    }
}
