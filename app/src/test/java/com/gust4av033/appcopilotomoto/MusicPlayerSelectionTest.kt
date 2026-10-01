package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class MusicPlayerSelectionTest {
    private val poweramp = MusicPlayer(MusicPlayerSelection.POWERAMP_PACKAGE, "Poweramp", true)
    private val other = MusicPlayer("other.player", "Otro", false)

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
