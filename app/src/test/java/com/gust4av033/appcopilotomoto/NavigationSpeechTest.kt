package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class NavigationSpeechTest {
    private fun turn(distance: Int, time: Long = 1_000L, id: String = "left") =
        ManeuverObservation(id, "girá a la izquierda", distance, time, 15_000L, ManeuverOrigin.OBSERVED)

    @Test fun announcesEachStepOnceAndResetsForNextManeuver() {
        val policy = NavigationSpeechDeduplicator()
        assertNull(policy.next(turn(600), 1_000, 10f, false))
        assertNotNull(policy.next(turn(430), 1_000, 10f, false))
        assertNull(policy.next(turn(400), 1_000, 10f, false))
        assertNotNull(policy.next(turn(170), 1_000, 10f, false))
        assertNotNull(policy.next(turn(40), 1_000, 10f, false))
        assertNull(policy.next(turn(45), 1_000, 10f, false))
        assertNotNull(policy.next(turn(400, id = "right"), 1_000, 10f, false))
    }

    @Test fun stalePassedAndBusyManeuversStaySilent() {
        val policy = NavigationSpeechDeduplicator()
        assertNull(policy.next(turn(400), 20_000, 10f, false))
        assertNull(policy.next(turn(40), 10_000, 20f, false))
        assertNull(policy.next(turn(400), 1_000, 10f, true))
        assertNull(policy.next(turn(400), 1_000, null, false))
        assertNotNull(policy.next(turn(400), 1_000, 10f, false))
    }

    @Test fun observedSourceNeedsMatchingCurrentDistance() {
        val turn = ContextValue("Gira a la izquierda", ContextSource.WAZE_ACCESSIBILITY,
            100L, 100L, .8f, 15_000L)
        val meters = ContextValue(400, ContextSource.GOOGLE_MAPS_NOTIFICATION,
            100L, 100L, .8f, 15_000L)
        assertNull(ObservedManeuverSource.current(TripContextSnapshot(navigation =
            NavigationContext(nextTurnInstruction = turn, distanceToNextTurnMeters = meters)), 100L))
        assertNotNull(ObservedManeuverSource.current(TripContextSnapshot(navigation =
            NavigationContext(nextTurnInstruction = turn, distanceToNextTurnMeters = meters.copy(source = turn.source))), 100L))
        assertNull(ObservedManeuverSource.current(TripContextSnapshot(navigation =
            NavigationContext(nextTurnInstruction = turn, distanceToNextTurnMeters = meters.copy(source = turn.source))), 15_100L))
    }
}
