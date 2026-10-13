package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Test

class AppCatalogCommandsTest {
    @Test fun `catalog preserves order and the router's numbering for repeated labels`() {
        val catalog = listOf(
            LaunchableApp("Música", "z.player", "Z"),
            LaunchableApp("Waze", "com.waze", "Main"),
            LaunchableApp("Musica", "a.player", "B"),
            LaunchableApp("Música", "a.player", "A")
        )
        val commands = AppNameMatcher.catalogCommands(catalog)
        assertEquals(catalog.map { app ->
            AppNameMatcher.commandFor(app, catalog) +
                if (app.packageName == "com.waze") "" else " · ${app.packageName}"
        }, commands)
        catalog.zip(commands).forEach { (app, command) ->
            val spokenName = command.substringBefore(" · ").removePrefix("abre ")
            assertEquals(AppMatch.Found(app), AppNameMatcher.find(spokenName, catalog))
        }
    }

    @Test fun `numbered duplicate labels beyond ten still resolve to the same activity`() {
        val catalog = (1..12).map { LaunchableApp("Player", "package.${it.toString().padStart(2, '0')}", "Main") }
        val commands = AppNameMatcher.catalogCommands(catalog)
        assertEquals("abre Player número 12 · package.12", commands.last())
        catalog.zip(commands).forEach { (app, command) ->
            assertEquals(AppMatch.Found(app), AppNameMatcher.find(command.substringBefore(" · ").removePrefix("abre "), catalog))
        }
    }

    @Test fun `empty catalog has no commands`() { assertEquals(emptyList<String>(), AppNameMatcher.catalogCommands(emptyList())) }
}
