package com.gust4av033.appcopilotomoto

import com.gust4av033.appcopilotomoto.presentation.*
import org.junit.Assert.*
import org.junit.Test

class RideUiPhaseTest {
    @Test fun requiresContinuousEvidenceAcrossBothThresholds() {
        var s = RideUiPhaseMemory()
        fun step(speed: Float?, time: Long) { s = rideUiPhase(s, true, speed, time) }
        step(8f, 0); step(9f, 1000); step(9f, 3999)
        assertEquals(RideUiPhase.STOPPED_ON_ROUTE, s.phase)
        step(9f, 4000); assertEquals(RideUiPhase.RIDING, s.phase)
        step(2f, 5000); step(3f, 9000); step(2f, 10000); step(2f, 14999)
        assertEquals(RideUiPhase.RIDING, s.phase)
        step(2f, 15000); assertEquals(RideUiPhase.STOPPED_ON_ROUTE, s.phase)
        assertEquals(RideUiPhaseMemory(), rideUiPhase(s, false, 40f, 16000))
    }
    @Test fun gapsStaleSamplesAndClockRegressionCannotUnlockMovingUi() {
        val moving = RideUiPhaseMemory(RideUiPhase.RIDING)
        val slow = rideUiPhase(moving, true, 0f, 1000)
        assertEquals(slow, rideUiPhase(slow, true, 0f, 1000))
        assertEquals(slow, rideUiPhase(slow, true, 0f, 500))
        val gap = rideUiPhase(slow, true, 0f, 12000)
        assertEquals(RideUiPhase.RIDING, gap.phase)
        val stale = rideUiPhase(gap, true, null, null)
        assertNull(stale.candidateSinceMs)
        assertEquals(RideUiPhase.RIDING, rideUiPhase(stale, true, 0f, 18000).phase)
        assertEquals(RideUiPhase.RIDING, rideUiPhase(stale, true, Float.NaN, 18000).phase)
    }
}
