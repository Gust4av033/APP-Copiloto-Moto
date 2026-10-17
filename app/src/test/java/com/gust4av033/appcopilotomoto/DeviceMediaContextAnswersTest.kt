package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class DeviceMediaContextAnswersTest {
    private fun <T> value(item: T, ttl: Long = 1_000L) =
        ContextValue(item, ContextSource.REPLAY_FIXTURE, 100L, 100L, 1f, ttl, isSimulated = true)

    @Test fun musicAnswerUsesSessionEvidenceAndHandlesMissingOrExpiredData() {
        val music = value(MusicContext("com.spotify.music", "Spotify", MusicPlayerOrigin.TRIP,
            true, "Counting Stars", "OneRepublic"))
        assertEquals(TestIntent.MUSIC_CONTEXT, MinimalIntentRouter.resolve("¿qué suena?"))
        assertEquals("Suena Counting Stars de OneRepublic en Spotify.",
            DeviceMediaContextAnswers.playing(TripContextSnapshot(music = music), 100L))
        assertEquals("No hay música sonando ahora.", DeviceMediaContextAnswers.playing(
            TripContextSnapshot(music = music.copy(value = music.value.copy(playing = false))), 100L))
        assertTrue(DeviceMediaContextAnswers.playing(TripContextSnapshot(music = music), 1_100L)
            .startsWith("No tengo"))
        assertTrue(DeviceMediaContextAnswers.playing(TripContextSnapshot(), 100L).startsWith("No tengo"))
    }

    @Test fun batteryAnswerReportsChargeOnlyWithCurrentEvidence() {
        assertEquals(TestIntent.BATTERY_CONTEXT, MinimalIntentRouter.resolve("¿cuánta batería me queda?"))
        val battery = value(BatteryContext(19, false))
        assertEquals("Te queda 19 por ciento de batería.",
            DeviceMediaContextAnswers.battery(TripContextSnapshot(battery = battery), 100L))
        assertTrue(DeviceMediaContextAnswers.battery(TripContextSnapshot(battery =
            battery.copy(value = BatteryContext(19, true))), 100L).contains("Está cargando"))
        assertEquals("No tengo una lectura reciente de batería.",
            DeviceMediaContextAnswers.battery(TripContextSnapshot(battery = battery), 1_100L))
    }
}
