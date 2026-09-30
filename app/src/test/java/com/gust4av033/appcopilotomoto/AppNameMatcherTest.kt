package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNameMatcherTest {
    private val catalog = listOf(
        LaunchableApp("Música Azul", "example.music", "example.music.Main"),
        LaunchableApp("Mapas", "example.maps", "example.maps.Main"),
        LaunchableApp("Mapas", "example.othermaps", "example.othermaps.Main"),
        LaunchableApp("Cámara", "example.camera", "example.camera.Main")
    )

    @Test fun installedAppNameMatchesWithoutAHardcodedList() {
        assertEquals(AppMatch.Found(catalog[0]), AppNameMatcher.find("musica azul", catalog))
        assertEquals(AppMatch.Found(catalog[3]), AppNameMatcher.find("CÁMARA", catalog))
        assertEquals(TestIntent.OPEN_APP, MinimalIntentRouter.route("abre la app de Música Azul").intent)
    }

    @Test fun duplicatedNamesRequireAnExplicitNumber() {
        val ambiguous = AppNameMatcher.find("Mapas", catalog)
        assertTrue(ambiguous is AppMatch.Ambiguous)
        assertEquals(AppMatch.Found(catalog[1]), AppNameMatcher.find("Mapas número uno", catalog))
        assertEquals(AppMatch.Found(catalog[2]), AppNameMatcher.find("Mapas número dos", catalog))
        assertEquals(AppMatch.Missing, AppNameMatcher.find("Mapas número tres", catalog))
    }

    @Test fun partialOrCombinedNamesNeverLaunchAnotherApp() {
        assertEquals(AppMatch.Missing, AppNameMatcher.find("Música", catalog))
        assertEquals(AppMatch.Missing, AppNameMatcher.find("Mapas y Cámara", catalog))
    }
}
