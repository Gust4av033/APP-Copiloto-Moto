package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class MusicSearchCommandTest {
    @Test fun explicitPlayerIsKeptSeparateFromTheSongTitle() {
        val command = MinimalIntentRouter.route("pon Counting Stars en Spotify")
        assertEquals(TestIntent.MEDIA_SEARCH, command.intent)
        assertEquals("Counting Stars", command.musicQuery)
        assertEquals("Spotify", command.musicPlayerName)
        assertEquals(TestIntent.RESET_MUSIC_PLAYER,
            MinimalIntentRouter.route("vuelve a mi reproductor").intent)
        assertEquals("spotify", MinimalIntentRouter.route("usa Spotify").appName)
        val control = MinimalIntentRouter.route("siguiente en Spotify")
        assertEquals(TestIntent.MEDIA_NEXT, control.intent)
        assertEquals("spotify", control.musicPlayerName)
        assertEquals(TestIntent.MEDIA_PAUSE, MinimalIntentRouter.route("pausa en Spotify").intent)
    }
    @Test fun songNamesRetainAccentsAndPunctuation() {
        assertEquals(RoutedCommand(TestIntent.MEDIA_SEARCH, musicQuery = "Él & Ella"),
            MinimalIntentRouter.route("Por favor, pon la canción Él & Ella, gracias"))
        assertEquals("Svegliarsi la mattina", MinimalIntentRouter.route("reproduce Svegliarsi la mattina").musicQuery)
        assertEquals("Counting Stars", MinimalIntentRouter.route("Poneme Counting Stars").musicQuery)
        assertEquals("Qué Más Da", MinimalIntentRouter.route("poné Qué Más Da").musicQuery)
    }

    @Test fun musicControlsAndVolumeKeepPriority() {
        val controls = mapOf("pon la siguiente" to TestIntent.MEDIA_NEXT,
            "pon la siguiente canción" to TestIntent.MEDIA_NEXT,
            "pon la canción anterior" to TestIntent.MEDIA_PREVIOUS,
            "pon la música en pausa" to TestIntent.MEDIA_PAUSE,
            "pon la música" to TestIntent.MEDIA_PLAY,
            "pon volumen al 40 por ciento" to TestIntent.VOLUME_SET)
        controls.forEach { (text, intent) ->
            val routed = MinimalIntentRouter.route(text)
            assertEquals(text, intent, routed.intent)
            assertNull(text, routed.musicQuery)
        }
    }

    @Test fun emptyInvalidAndCompoundOrdersDoNotBecomeSongSearches() {
        listOf("pon", "reproduce", "pon la canción", "pon volumen al 80", "pon volumen al 110 por ciento",
            "pon una canción y llama a Ana", "pon canción y abre Spotify", "no pongas música").forEach {
            assertNotEquals(it, TestIntent.MEDIA_SEARCH, MinimalIntentRouter.resolve(it))
        }
    }
}
