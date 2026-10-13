package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class NaturalCommandRegressionTest {
    @Test fun mixedLanguageSongTitlesRemainDataWithTheirOriginalSpelling() {
        mapOf("pon Sweet Child O' Mine" to "Sweet Child O' Mine",
            "please play Don't Stop Me Now" to "Don't Stop Me Now",
            "busca la canción Nothing Else Matters" to "Nothing Else Matters",
            "search for the song The Sound of Silence" to "The Sound of Silence").forEach { (phrase, title) ->
            val command = MinimalIntentRouter.route(phrase)
            assertEquals(TestIntent.MEDIA_SEARCH, command.intent)
            assertEquals(title, command.musicQuery)
            assertFalse(command.musicTitleInEnglish)
        }
    }

    @Test fun commonEnglishControlsAndSpanishVariantsKeepTheirSpecificActions() {
        mapOf("next track" to TestIntent.MEDIA_NEXT, "previous song" to TestIntent.MEDIA_PREVIOUS,
            "pause music" to TestIntent.MEDIA_PAUSE, "resume music" to TestIntent.MEDIA_PLAY,
            "terminar viaje" to TestIntent.STOP_SESSION, "stop trip" to TestIntent.STOP_SESSION,
            "quiet" to TestIntent.STOP_TTS, "cancel" to TestIntent.CANCEL,
            "confirmo la llamada" to TestIntent.CONFIRM_CALL).forEach { (phrase, intent) ->
            assertEquals(phrase, intent, MinimalIntentRouter.resolve(phrase))
        }
        assertEquals("ana", MinimalIntentRouter.route("marcale a Ana").contactName)
        assertEquals("john smith", MinimalIntentRouter.route("please call John Smith").contactName)
        assertEquals("spotify", MinimalIntentRouter.route("could you open Spotify").appName)
        assertEquals(TestIntent.MEDIA_NEXT, MinimalIntentRouter.resolve("play next song"))
        assertEquals(TestIntent.MEDIA_PLAY, MinimalIntentRouter.resolve("play music"))
        assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.resolve("play Hello and open Waze"))
        assertEquals(VolumeRequest(40, true), MinimalIntentRouter.route("please set volume to forty percent").volume)
        assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.resolve("set volume to forty"))
    }

    @Test fun sensitiveCallConfirmationDoesNotAcceptAGenericYes() {
        listOf("sí", "one", "adelante", "send", "yes").forEach {
            assertNotEquals(TestIntent.CONFIRM_CALL, MinimalIntentRouter.resolve(it))
        }
        assertEquals(AppChoice.OPEN, AppChoiceRouter.resolve("sí abre"))
        assertEquals(AppChoice.CANCEL, AppChoiceRouter.resolve("mejor no"))
        assertEquals(AppChoice.UNKNOWN, DraftChoiceRouter.resolve("envía"))
        assertEquals(AppChoice.UNKNOWN, DraftChoiceRouter.resolve("send"))
        assertEquals(AppChoice.UNKNOWN, AppChoiceRouter.resolve("uno o dos"))
    }

    @Test fun aMessageContainingActionWordsIsNotExecutedAsThoseActions() {
        val command = MinimalIntentRouter.route("envía un mensaje a Ana en WhatsApp que diga Please open Waze and call John.")
        assertEquals(TestIntent.DRAFT_CONTACT_MESSAGE, command.intent)
        assertEquals("Please open Waze and call John.", command.messageText)
        assertNull(command.appName)
    }
}
