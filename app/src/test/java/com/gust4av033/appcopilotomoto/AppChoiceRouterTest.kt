package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Test

class AppChoiceRouterTest {
    @Test fun acceptsSpokenAndNumericChoices() {
        listOf("1", "uno", "OPCIÓN UNO", "opcion 1", "abrir", "sí", "sí abre", "one").forEach {
            assertEquals(it, AppChoice.OPEN, AppChoiceRouter.resolve(it))
        }
        listOf("2", "dos", "opción dos", "opcion 2", "cancelar", "no", "mejor no", "two").forEach {
            assertEquals(it, AppChoice.CANCEL, AppChoiceRouter.resolve(it))
        }
    }

    @Test fun ambiguityAndUnrelatedSpeechNeverOpenAnApp() {
        listOf(null, "", "uno dos", "uno o dos", "no abrir", "no uno", "veintiuno",
            "abre Spotify", "volumen uno", "1 2", "sí no", "3").forEach {
            assertEquals(it, AppChoice.UNKNOWN, AppChoiceRouter.resolve(it))
        }
    }

    @Test fun numberedChoicesDoNotChangeGeneralVoiceRouting() {
        assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.resolve("uno"))
        assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.resolve("dos"))
        assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.resolve("sí"))
        assertEquals(TestIntent.VOLUME_SET, MinimalIntentRouter.resolve("volumen al 1"))
    }
}

