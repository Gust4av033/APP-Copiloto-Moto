package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class MusicPlayerSelectionTest {
    private val poweramp = MusicPlayer(MusicPlayerSelection.POWERAMP_PACKAGE, "Poweramp", true)
    private val other = MusicPlayer("other.player", "Otro", false)

    @Test fun tripAndPlayingSessionsOverrideSavedDefaultWithoutChangingIt() {
        val spotify = MusicPlayer("com.spotify.music", "Spotify", false, sessionSearch = true)
        val players = listOf(poweramp, spotify)
        assertEquals(MusicPlayerOrigin.EXPLICIT, MusicPlayerSelection.resolve(players, "Spotify",
            spotify.packageName, listOf(poweramp.packageName), poweramp.packageName).getOrThrow().origin)
        assertEquals(spotify, MusicPlayerSelection.resolve(players, null,
            spotify.packageName, listOf(poweramp.packageName), poweramp.packageName).getOrThrow().player)
        assertEquals(MusicPlayerOrigin.PLAYING_SESSION, MusicPlayerSelection.resolve(players, null,
            null, listOf(spotify.packageName), poweramp.packageName).getOrThrow().origin)
        assertEquals(poweramp, MusicPlayerSelection.resolve(players, null,
            null, emptyList(), poweramp.packageName).getOrThrow().player)
    }

    @Test fun missingTripPlayerDoesNotSilentlyFallBackToTheDefault() {
        assertTrue(MusicPlayerSelection.resolve(listOf(poweramp), null, "com.spotify.music",
            emptyList(), poweramp.packageName).isFailure)
    }

    @Test fun searchRouteDependsOnAdvertisedCapabilityRatherThanBrand() {
        val spotify = MusicPlayer("com.spotify.music", "Spotify", false)
        assertEquals(MusicSearchRoute.MEDIA_SESSION, MusicSearchRouting.choose(spotify, true))
        assertEquals(MusicSearchRoute.VISIBLE_INTENT, MusicSearchRouting.choose(spotify, false))
        assertEquals(MusicSearchRoute.POWERAMP_BROADCAST, MusicSearchRouting.choose(poweramp, false))
    }

    @Test fun automaticPrefersPowerampAndDoesNotDependOnOrder() {
        assertEquals(poweramp, MusicPlayerSelection.choose(listOf(other, poweramp), null).getOrThrow())
    }

    @Test fun explicitChoiceOverridesAutomaticAndIsNotSilentlyReplaced() {
        assertEquals(other, MusicPlayerSelection.choose(listOf(other, poweramp), other.packageName).getOrThrow())
        assertTrue(MusicPlayerSelection.choose(listOf(poweramp), other.packageName).isFailure)
    }

    @Test fun automaticNeedsOnePlayerWhenPowerampIsAbsent() {
        assertEquals(other, MusicPlayerSelection.choose(listOf(other), null).getOrThrow())
        assertTrue(MusicPlayerSelection.choose(emptyList(), null).isFailure)
        assertTrue(MusicPlayerSelection.choose(listOf(other, other.copy(packageName = "another.player")), null).isFailure)
    }
}
