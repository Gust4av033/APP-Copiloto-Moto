package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentRouterTest {
    @Test fun naturalMusicPhrasesKeepSpecificActions() {
        assertEquals(TestIntent.MEDIA_NEXT, MinimalIntentRouter.route("Por favor, pasa de canción").intent)
        assertEquals(TestIntent.MEDIA_PREVIOUS, MinimalIntentRouter.route("Regresa a la canción anterior").intent)
        assertEquals(TestIntent.MEDIA_PAUSE, MinimalIntentRouter.route("Pon la música en pausa").intent)
        assertEquals(TestIntent.STOP_SESSION, MinimalIntentRouter.route("Detener sesión").intent)
        assertEquals(TestIntent.MEDIA_PAUSE, MinimalIntentRouter.route("Detener").intent)
    }

    @Test fun parametersBelongToTheResolvedCommand() {
        assertEquals(RoutedCommand(TestIntent.VOLUME_SET, VolumeRequest(65, true)),
            MinimalIntentRouter.route("Poné el volumen al sesenta y cinco por ciento, por favor"))
        assertEquals(RoutedCommand(TestIntent.CALL_NAMED, contactName = "ana maria"),
            MinimalIntentRouter.route("Llama a Ana María, por favor"))
        assertNull(MinimalIntentRouter.route("sube el volumen").volume)
        assertEquals(RoutedCommand(TestIntent.OPEN_APP, appName = "spotify"),
            MinimalIntentRouter.route("Por favor, abre Spotify"))
    }

    @Test fun ambiguousOrOutOfRangeSpeechNeverBecomesAnAction() {
        assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.route("volumen al 80").intent)
        assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.route("siguiente canción y llamar a Ana").intent)
        assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.route("volumen al 110 por ciento").intent)
        assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.route("abre").intent)
    }
}
