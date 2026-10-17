package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripGreetingTextTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun nameIsOnlyAddedToTheWelcomeAndWarningsRemain() {
        val ready = TripGreetingDecision(TripAnnouncement.VOICE, lowBatteryPercent = 19,
            gpsUnavailable = true)
        assertEquals("Copiloto listo, Bryan. Batería al 19 por ciento. Sin señal GPS para el contexto del viaje.",
            ready.spokenText(context, starting = true, name = "Bryan"))
        assertEquals("Copiloto listo. Batería al 19 por ciento. Sin señal GPS para el contexto del viaje.",
            ready.spokenText(context, starting = true))
        val finished = TripGreetingDecision(TripAnnouncement.VOICE)
        assertEquals("Viaje finalizado.", finished.spokenText(context, starting = false, name = "Bryan"))
    }
}
