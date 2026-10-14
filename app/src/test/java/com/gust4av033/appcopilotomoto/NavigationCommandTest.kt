package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class NavigationCommandTest {
    @Test fun familiarPhrasesResolveWithoutInterferingWithAppLaunches() {
        for (phrase in listOf("Vamos a casa", "Llévame a casa en Waze", "Por favor, vamos para la casa")) {
            val command = MinimalIntentRouter.route(phrase)
            assertEquals(TestIntent.NAVIGATE, command.intent)
            assertEquals(NavigationRequest.HOME, command.navigationRequest)
            assertNull(command.appName)
        }
        assertEquals(NavigationRequest.WORK, MinimalIntentRouter.route("Vamos al trabajo, por favor").navigationRequest)
        assertEquals(TestIntent.OPEN_APP, MinimalIntentRouter.route("abre Waze").intent)
        assertEquals(NavigationApp.WAZE,
            MinimalIntentRouter.route("Llévame a casa en Waze").navigationProvider)
        val maps = MinimalIntentRouter.route("Vamos a la chamba en Google Maps")
        assertEquals(TestIntent.NAVIGATE, maps.intent)
        assertEquals(NavigationApp.GOOGLE_MAPS, maps.navigationProvider)
    }

    @Test fun lastDestinationUsesLocalRequestAndNeverInventsAFallback() {
        assertEquals(NavigationRequest.LAST, MinimalIntentRouter.route("Vamos a la última ubicación puesta").navigationRequest)
        assertNull(NavigationRequest.LAST.resolve(null))
        assertEquals(NavigationDestination.WORK, NavigationRequest.LAST.resolve(NavigationDestination.WORK))
        assertEquals(NavigationDestination.HOME, NavigationRequest.HOME.resolve(NavigationDestination.WORK))
    }

    @Test fun unknownPlacesAndCombinedOrdersDoNotNavigate() {
        for (phrase in listOf("vamos a casa y llama a Ana", "no vamos a casa", "vamos a casa de Ana",
            "vamos a San Salvador", "última canción", "abre la última ubicación")) {
            assertNotEquals(TestIntent.NAVIGATE, MinimalIntentRouter.resolve(phrase))
            assertNull(MinimalIntentRouter.route(phrase).navigationRequest)
        }
        assertEquals(TestIntent.CANCEL, MinimalIntentRouter.resolve("cancelar"))
        assertEquals(TestIntent.MEDIA_PREVIOUS, MinimalIntentRouter.resolve("anterior canción"))
    }

    @Test fun linksAddressOnlyDocumentedWazeFavorites() {
        assertEquals("https://waze.com/ul?favorite=home&navigate=yes&utm_source=app_copiloto_moto",
            WazeNavigationLink.forDestination(NavigationDestination.HOME))
        assertTrue(WazeNavigationLink.forDestination(NavigationDestination.WORK).contains("favorite=work&"))
        assertFalse(WazeNavigationLink.forDestination(NavigationDestination.WORK).contains("ll="))
    }

}
