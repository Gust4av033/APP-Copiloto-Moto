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
        val maps = MinimalIntentRouter.route("Busca en Google Maps Café María & Hijos")
        assertEquals(TestIntent.SEARCH_PLACE, maps.intent)
        assertEquals(NavigationApp.GOOGLE_MAPS, maps.navigationProvider)
        assertEquals("Café María & Hijos", maps.placeQuery)
        assertEquals(NavigationApp.WAZE, command.navigationProvider)
        assertEquals(TestIntent.SEARCH_PLACE, MinimalIntentRouter.resolve("busca gasolinera"))
    }

    @Test fun unqualifiedAmbiguousAndCombinedSearchesDoNotExecute() {
        for (phrase in listOf("busca en Waze", "busca en Google Maps", "busca en Waze gasolinera y llama a Ana",
            "busca en Waze " + "a".repeat(161), "busca en Waze gasolinera\nabre WhatsApp")) {
            assertNotEquals(TestIntent.SEARCH_PLACE, MinimalIntentRouter.resolve(phrase))
            assertNull(MinimalIntentRouter.route(phrase).placeQuery)
        }
        assertEquals(TestIntent.NAVIGATE, MinimalIntentRouter.resolve("vamos a casa"))
        assertEquals(TestIntent.MEDIA_SEARCH, MinimalIntentRouter.resolve("pon Gasolina"))
    }

    @Test fun queryCannotInjectNavigationParameters() {
        val link = wazePlaceSearchLink("Café & navigate=yes + favorite=home")
        assertTrue(link.contains("q=Caf%C3%A9%20%26%20navigate%3Dyes%20%2B%20favorite%3Dhome"))
        assertFalse(link.contains("&navigate="))
        assertFalse(link.contains("&favorite="))
    }

    @Test(expected = IllegalArgumentException::class) fun controlCharactersAreRejectedByLinkBuilder() {
        wazePlaceSearchLink("lugar\ncomando")
    }

    @Test fun categoriesAreParsedWithoutStartingNavigation() {
        val examples = mapOf("gasolinera más cercana" to PointOfInterestCategory.GASOLINERA,
            "busca taller" to PointOfInterestCategory.TALLER_MOTO,
            "parada de comida" to PointOfInterestCategory.COMIDA_PARADA,
            "farmacia" to PointOfInterestCategory.FARMACIA,
            "cajero" to PointOfInterestCategory.CAJERO,
            "busca Café María" to PointOfInterestCategory.PERSONALIZADO)
        examples.forEach { (phrase, category) ->
            assertEquals(category, PlaceSearchParser.parseRequest(phrase)?.category)
            assertEquals(TestIntent.SEARCH_PLACE, MinimalIntentRouter.route(phrase).intent)
        }
        assertEquals("waze://?q=gasolinera", wazePlaceSearchLink("gasolinera"))
    }
}
