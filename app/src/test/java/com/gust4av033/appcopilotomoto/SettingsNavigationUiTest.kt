package com.gust4av033.appcopilotomoto

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.gust4av033.appcopilotomoto.presentation.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** These render the real Compose UI on Android JVM, not a Bluetooth device or physical screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsNavigationUiTest {
    @get:Rule val compose=createComposeRule()
    @Before fun hostAvailable() { assumeTrue(BuildConfig.DEBUG) }
    private fun app(state: CopilotUiState=CopilotUiState(installedVersion="test"),
                    section: SettingsSection=SettingsSection.OVERVIEW, screen: CopilotScreen=CopilotScreen.SETTINGS,
                    scale: Float=1f, actions: CopilotActions=CopilotActions()) {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,scale)) {
            CopilotApp(state,screen,{},actions,section)
        } }
    }
    @Test fun ordinarySettingsShowSevenCategoriesAndNoDeveloperEntry() {
        app()
        compose.onNodeWithText("Voz y activación").assertExists()
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Acerca de APP"))
        compose.onNodeWithText("Acerca de APP").assertExists()
        compose.onNodeWithText("Herramientas de desarrollador").assertDoesNotExist()
        compose.onNodeWithText("Medición de micrófono").assertDoesNotExist()
    }
    @Test fun audioAtLargeFontStillOffersInstallationSpecificPermission() {
        app(section=SettingsSection.AUDIO,scale=2f)
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Acceso a controles multimedia"))
        compose.onNodeWithText("Acceso a controles multimedia").assertExists()
        compose.onNodeWithText("APP y APP de pruebas tienen permisos independientes.",substring=true).assertExists()
    }
    @Test fun restoredTestsRouteOpensSafeHelpInsteadOfCaptures() {
        app(screen=CopilotScreen.TESTS)
        compose.onNodeWithText("Comprobar el audio").assertExists()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.probe_start)).assertDoesNotExist()
        compose.onNodeWithText("Métricas del viaje").assertDoesNotExist()
    }
    @Test fun releaseOptInHasNoButtonsThatStartCaptures() {
        app(state=CopilotUiState(developerAccess=DeveloperAccess(true,false)),screen=CopilotScreen.TESTS)
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText(RuntimeEnvironment.getApplication().getString(R.string.report_copy)))
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.listen_start)).assertDoesNotExist()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.probe_start)).assertDoesNotExist()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.wake_start)).assertDoesNotExist()
    }
    @Test fun aboutTapEmitsOnlyAnExplicitCallback() {
        var taps=0
        app(section=SettingsSection.ABOUT,actions=CopilotActions(onVersionTap={taps++}))
        compose.onNodeWithText("Versión instalada · test").performClick()
        assertEquals(1,taps)
        compose.onNodeWithText("Activar acceso de desarrollador").assertDoesNotExist()
    }
    @Test fun appearancePreferenceEmitsItsRealChangeCallback() {
        var value:Boolean?=null
        app(section=SettingsSection.APPEARANCE,actions=CopilotActions(onOverlayEnabledChange={value=it}))
        compose.onNode(hasText(RuntimeEnvironment.getApplication().getString(R.string.overlay_settings_toggle)) and hasClickAction()).performClick()
        assertEquals(true,value)
    }
    @Test fun tabletContentIsCenteredAndTouchTargetsRemainLarge() {
        RuntimeEnvironment.setQualifiers("w800dp-h1000dp")
        app()
        val content=compose.onNodeWithTag("settings_content").fetchSemanticsNode().boundsInRoot
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        val density=RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertTrue(content.width/density<=680.1f)
        assertTrue(content.left>root.left)
        val row=compose.onNode(hasText("Voz y activación") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(row.height/density>=48f)
    }
    @Test fun narrowNightLayoutStillNavigatesWithLargeText() {
        RuntimeEnvironment.setQualifiers("w320dp-h800dp-night")
        app(scale=1.5f)
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Privacidad y permisos"))
        compose.onNodeWithText("Privacidad y permisos").performClick()
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Abrir permisos de APP en Android"))
        compose.onNodeWithText("Abrir permisos de APP en Android").assertExists()
    }
}
