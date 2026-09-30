package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Test

class AppChoiceRouterTest {
    @Test fun acceptsSpokenAndNumericChoices() {
        listOf("1", "uno", "OPCIÓN UNO", "opcion 1", "abrir").forEach {
            assertEquals(it, AppChoice.OPEN, AppChoiceRouter.resolve(it))
        }
        listOf("2", "dos", "opción dos", "opcion 2", "cancelar").forEach {
            assertEquals(it, AppChoice.CANCEL, AppChoiceRouter.resolve(it))
        }
    }

    @Test fun ambiguityAndUnrelatedSpeechNeverOpenAnApp() {
        listOf(null, "", "uno dos", "uno o dos", "no abrir", "no uno", "veintiuno",
            "abre Spotify", "volumen uno", "1 2", "sí", "3").forEach {
            assertEquals(it, AppChoice.UNKNOWN, AppChoiceRouter.resolve(it))
        }
    }

    @Test fun numberedChoicesDoNotChangeGeneralVoiceRouting() {
        assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.resolve("uno"))
        assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.resolve("dos"))
        assertEquals(TestIntent.VOLUME_SET, MinimalIntentRouter.resolve("volumen al 1"))
    }
}
