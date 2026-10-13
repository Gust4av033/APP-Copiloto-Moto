package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class FollowUpCaptureGateTest {
    private class Harness {
        var time = 0L
        var current = true
        var route = false
        var selections = 0
        var openings = 0
        var failure: String? = null
        val tasks = mutableListOf<Pair<Long, () -> Unit>>()
        val workers = mutableListOf<() -> Unit>()
        val gate = FollowUpCaptureGate({ workers.add(it) }, { delay, task -> tasks.add(time + delay to task) }, { time })
        fun request(selection: () -> Result<Unit> = { selections++; Result.success(Unit) }) {
            gate.prepare({ current }, selection, { route }, { openings++ }, { failure = it })
        }
        fun advance(to: Long) {
            while (true) {
                val task = tasks.filter { it.first <= to }.minByOrNull { it.first } ?: break
                tasks.remove(task)
                time = task.first
                task.second()
            }
            time = to
        }
        fun select() { workers.removeAt(0).invoke(); advance(time) }
    }

    @Test fun waitsForTtsDrainSelectionAndAStableHeadsetBeforeOpening() {
        val h = Harness()
        h.request()
        h.advance(299)
        assertEquals(0, h.workers.size)
        h.advance(300)
        assertEquals(0, h.openings)
        h.select()
        assertEquals(1, h.selections)
        h.advance(1_000)
        assertEquals(0, h.openings)
        h.route = true
        h.advance(1_050)
        assertEquals(0, h.openings)
        h.advance(1_300)
        assertEquals(1, h.openings)
        h.advance(10_000)
        assertEquals(1, h.openings)
    }

    @Test fun aTransientRouteDoesNotOpenThePhoneMic() {
        val h = Harness()
        h.request(); h.advance(300); h.select()
        h.route = true; h.advance(550)
        h.route = false; h.advance(800)
        assertEquals(0, h.openings)
        h.route = true; h.advance(1_050)
        assertEquals(0, h.openings)
        h.advance(1_300)
        assertEquals(1, h.openings)
    }

    @Test fun unavailableRouteEndsTheTurnOnceWithoutOpening() {
        val h = Harness()
        h.request(); h.advance(300); h.select(); h.advance(8_000)
        assertEquals(0, h.openings)
        assertTrue(h.failure!!.contains("Bluetooth"))
        assertTrue(h.tasks.isEmpty())
    }

    @Test fun routeSelectionFailureIsReportedWithoutPolling() {
        val h = Harness()
        h.request { Result.failure(IllegalStateException("casco desconectado")) }
        h.advance(300); h.select()
        assertTrue(h.failure!!.contains("casco desconectado"))
        assertEquals(0, h.openings)
        assertTrue(h.tasks.isEmpty())
    }

    @Test fun cancellingBeforeOrDuringPollingCannotReopenAConversation() {
        val h = Harness()
        h.request(); h.gate.cancel(); h.advance(1_000)
        assertTrue(h.workers.isEmpty())
        h.request(); h.advance(1_300); h.select()
        h.gate.cancel(); h.route = true; h.advance(10_000)
        assertEquals(0, h.openings)
        assertNull(h.failure)
    }

    @Test fun lateSelectionAndExpiredTokensCannotOpenAReplacementRequest() {
        val h = Harness()
        h.request(); h.advance(300)
        h.gate.cancel(); h.select()
        assertEquals(0, h.openings)
        assertEquals(0, h.selections)
        h.request(); h.advance(600); h.select()
        h.current = false; h.route = true; h.advance(10_000)
        assertEquals(0, h.openings)
        assertNull(h.failure)
    }

    @Test fun repeatingReadbackCreatesOnlyOneCurrentPreparation() {
        val h = Harness()
        h.request(); h.advance(300); h.select()
        h.request(); h.route = true; h.advance(600); h.select(); h.advance(1_000)
        assertEquals(1, h.openings)
    }
}
