package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class PlaceSearchCommandTest {
    @Test fun explicitSearchPreservesPlaceNamesAndDoesNotChooseADestination() {
        val command = MinimalIntentRouter.route("Por favor, busca en Waze Café María & Hijos")
        assertEquals(TestIntent.SEARCH_PLACE, command.intent)
        assertEquals("Café María & Hijos", command.placeQuery)
        assertNull(command.navigationRequest)
        assertEquals("gasolinera", MinimalIntentRouter.route("busca gasolinera en Waze, por favor").placeQuery)
    }

    @Test fun unqualifiedAmbiguousAndCombinedSearchesDoNotExecute() {
        for (phrase in listOf("busca gasolinera", "busca en Waze", "busca en Waze gasolinera y llama a Ana",
            "busca en Waze " + "a".repeat(161), "busca en Waze gasolinera\nabre WhatsApp")) {
            assertNotEquals(TestIntent.SEARCH_PLACE, MinimalIntentRouter.resolve(phrase))
            assertNull(MinimalIntentRouter.route(phrase).placeQuery)
        }
        assertEquals(TestIntent.NAVIGATE, MinimalIntentRouter.resolve("vamos a casa"))
        assertEquals(TestIntent.MEDIA_SEARCH, MinimalIntentRouter.resolve("pon Gasolina"))
    }

    @Test fun queryCannotInjectNavigationParameters() {
        val link = wazePlaceSearchLink("Café & navigate=yes + favorite=home")
        assertTrue(link.contains("q=Caf%C3%A9%20%26%20navigate%3Dyes%20%2B%20favorite%3Dhome&"))
        assertFalse(link.contains("&navigate="))
        assertFalse(link.contains("&favorite="))
    }

    @Test(expected = IllegalArgumentException::class) fun controlCharactersAreRejectedByLinkBuilder() {
        wazePlaceSearchLink("lugar\ncomando")
    }
}
