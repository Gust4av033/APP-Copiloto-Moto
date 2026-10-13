package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class EnglishSongRequestTest {
    @Test fun explicitEnglishRequestsAskForTheTitleWithoutSearchingTheInstruction() {
        listOf("busca canción en inglés", "pon una canción en inglés", "reproduce una canción en inglés",
            "por favor busca canción en inglés gracias").forEach {
            val command = MinimalIntentRouter.route(it)
            assertEquals(it, TestIntent.MEDIA_SEARCH, command.intent)
            assertTrue(command.musicTitleInEnglish)
            assertNull(command.musicQuery)
        }
    }

    @Test fun titlesKeepEnglishSpellingAndDoNotBecomeCommands() {
        listOf("Don't Stop Me Now", "Sweet Child O' Mine", "Next", "Play", "Silence", "Canción en inglés").forEach {
            assertEquals(it, EnglishSongRequest.title(it))
        }
        assertNull(EnglishSongRequest.title("cancel"))
        assertNull(EnglishSongRequest.title("cancelar"))
        assertNull(EnglishSongRequest.title("!"))
        assertNull(EnglishSongRequest.title("x".repeat(201)))
    }

    @Test fun normalMusicAndCallsStayOnTheirExistingRoutes() {
        val music = MinimalIntentRouter.route("pon Don't Stop Me Now")
        assertEquals("Don't Stop Me Now", music.musicQuery)
        assertFalse(music.musicTitleInEnglish)
        assertEquals(TestIntent.CALL_NAMED, MinimalIntentRouter.resolve("llama a Ana"))
        assertEquals(TestIntent.MEDIA_PREVIOUS, MinimalIntentRouter.resolve("anterior canción"))
    }
}
