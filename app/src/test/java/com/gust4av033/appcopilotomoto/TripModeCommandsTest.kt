package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripModeCommandsTest {
    @Test fun numbersAndNamesResolveTheSameMode() {
        mapOf(WakeAudioMode.BLUETOOTH to listOf("activa modo 1", "activa modo uno", "cambia al modo voz continua"),
            WakeAudioMode.BUTTON to listOf("activa modo 2", "activa modo dos", "activa modo botón"),
            WakeAudioMode.PHONE_WAKE to listOf("activa modo 3", "activa modo tres", "cambia a modo experimental"))
            .forEach { (mode, phrases) -> phrases.forEach {
                assertEquals(RoutedCommand(TestIntent.SWITCH_MODE, audioMode = mode), MinimalIntentRouter.route(it))
            } }
    }

    @Test fun wakeNameMayBeIncludedInTheCapturedModeOrder() {
        assertEquals(WakeAudioMode.BLUETOOTH, MinimalIntentRouter.route("Alexa, activa modo 1").audioMode)
        assertEquals(WakeAudioMode.BUTTON, MinimalIntentRouter.route("Hey Jarvis, activa modo botón").audioMode)
        assertEquals(WakeAudioMode.PHONE_WAKE, MinimalIntentRouter.route("Por favor activa modo tres gracias").audioMode)
    }

    @Test fun invalidOrCompoundModeOrdersNeverSwitchOrSearchMusic() {
        listOf("activa modo 4", "activa modo cero", "activa modo 1 y modo 2", "pon modo cuatro",
            "activa modo 2 y llama a Ana", "activa modo", "alexa modo 1").forEach {
            assertEquals(it, TestIntent.UNKNOWN, MinimalIntentRouter.route(it).intent)
            assertNull(MinimalIntentRouter.route(it).audioMode)
        }
    }

    @Test fun assistantAliasIsSeparatedFromInstalledAppsAndChat() {
        assertEquals(TestIntent.OPEN_ASSISTANT, MinimalIntentRouter.route("abre el asistente").intent)
        assertEquals(TestIntent.OPEN_APP, MinimalIntentRouter.route("abre Waze").intent)
        assertEquals(TestIntent.MEDIA_SEARCH, MinimalIntentRouter.route("pon la canción Hola").intent)
        assertEquals(TestIntent.MEDIA_SEARCH, MinimalIntentRouter.route("pon la canción Modo avión").intent)
        assertNull(MinimalIntentRouter.route("sube el volumen").audioMode)
    }
}
