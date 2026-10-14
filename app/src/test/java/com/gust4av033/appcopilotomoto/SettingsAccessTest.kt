package com.gust4av033.appcopilotomoto

import com.gust4av033.appcopilotomoto.presentation.*
import org.junit.Assert.*
import org.junit.Test

class SettingsAccessTest {
    @Test fun userSettingsHaveThreeGroupsWithoutDroppingDetailRoutes() {
        assertEquals(listOf(SettingsSection.HEADSET, SettingsSection.DESTINATIONS, SettingsSection.VOICE_PRIVACY), SettingsSection.userSections)
        assertTrue(SettingsSection.entries.containsAll(listOf(SettingsSection.ACTIVATION, SettingsSection.AUDIO,
            SettingsSection.APPEARANCE, SettingsSection.NAVIGATION, SettingsSection.INTEGRATIONS,
            SettingsSection.PRIVACY, SettingsSection.ABOUT, SettingsSection.CALLS)))
    }
    @Test fun oldTestsRouteCannotExposeToolsWhenLocked() { assertEquals(CopilotScreen.DIAGNOSTICS,CopilotScreen.TESTS.visibleFor(CopilotUiState())) }
    @Test fun oldToolsSectionCannotBypassOptIn() { assertEquals(SettingsSection.ABOUT,SettingsSection.TOOLS.visibleFor(CopilotUiState())) }
    @Test fun releaseDiagnosticsRemainAvailableWithOptIn() { val s=CopilotUiState(developerAccess=DeveloperAccess(true,false), developerDiagnosticsUnlocked=true);assertEquals(CopilotScreen.TESTS,CopilotScreen.TESTS.visibleFor(s));assertFalse(s.canListen);assertFalse(s.canToggleWake) }
    @Test fun debugOptInStillRespectsActiveTrip() { val s=CopilotUiState(developerAccess=DeveloperAccess(true,true),tripActive=true);assertFalse(s.canListen);assertFalse(s.canToggleWake) }
}
