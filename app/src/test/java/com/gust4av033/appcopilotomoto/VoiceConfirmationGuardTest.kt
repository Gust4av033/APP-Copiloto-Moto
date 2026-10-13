package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class VoiceConfirmationGuardTest {
    @Test fun cancelAcrossAnnouncementRouteAndListeningRejectsTheLateAnswer() {
        listOf("anuncio", "preparación de Bluetooth", "escucha").forEach { stage ->
            val guard = VoiceConfirmationGuard()
            guard.begin(41)
            assertTrue(stage, guard.pending)
            guard.cancel()
            assertFalse(stage, guard.isCurrent(41))
            assertFalse(stage, guard.take(41))
        }
    }

    @Test fun aPreviousAnswerCannotConsumeANewCallConfirmation() {
        val guard = VoiceConfirmationGuard()
        guard.begin(41); guard.cancel(); guard.begin(42)
        assertFalse(guard.take(41))
        assertTrue(guard.pending)
        var dispatched = 0
        repeat(2) { if (guard.take(42)) dispatched++ }
        assertEquals(1, dispatched)
        assertFalse(guard.pending)
    }

    @Test fun cancelledRoutePreparationCannotOpenTheCallRecognizer() {
        val guard = VoiceConfirmationGuard()
        val tasks = mutableListOf<() -> Unit>()
        var opened = 0
        val gate = FollowUpCaptureGate({ it() }, { _, task -> tasks += task }, { 0L })
        guard.begin(7)
        gate.prepare({ guard.isCurrent(7) }, { Result.success(Unit) }, { true }, { opened++ }, { fail(it) })
        tasks.removeAt(0).invoke() // TTS drain and route selection.
        guard.cancel()
        while (tasks.isNotEmpty()) tasks.removeAt(0).invoke()
        assertEquals(0, opened)
    }
}
