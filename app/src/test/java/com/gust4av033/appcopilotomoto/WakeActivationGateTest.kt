package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Test

class WakeActivationGateTest {
    @Test fun aBurstPausesFurtherCapturesAndThenRecovers() {
        val gate = WakeActivationGate()
        listOf(0L, 1_000L, 2_000L).forEach { assertEquals(0L, gate.waitBeforeCapture(it)) }
        assertEquals(8_000L, gate.waitBeforeCapture(3_000L))
        assertEquals(4_000L, gate.waitBeforeCapture(7_000L))
        assertEquals(0L, gate.waitBeforeCapture(11_000L))
        assertEquals(0L, gate.waitBeforeCapture(12_000L))
    }

    @Test fun isolatedNoiseAndCommandsNeverExhaustAnAttemptCount() {
        val gate = WakeActivationGate()
        repeat(100) { assertEquals(0L, gate.waitBeforeCapture(it * 13_000L)) }
    }

    @Test fun aNewTripStartsWithoutThePreviousCooldown() {
        val gate = WakeActivationGate()
        listOf(0L, 1L, 2L, 3L).forEach { gate.waitBeforeCapture(it) }
        gate.reset()
        assertEquals(0L, gate.waitBeforeCapture(4L))
    }

    @Test fun recentWindowHasAnExactBoundary() {
        val gate = WakeActivationGate()
        listOf(0L, 1L, 2L).forEach { gate.waitBeforeCapture(it) }
        assertEquals(0L, gate.waitBeforeCapture(12_001L))
    }
}
