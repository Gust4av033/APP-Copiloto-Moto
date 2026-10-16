package com.gust4av033.appcopilotomoto

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.Density
import com.gust4av033.appcopilotomoto.presentation.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode

/** These render the real Compose UI on Android JVM, not a Bluetooth device or physical screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsNavigationUiTest {
    private val compose=createComposeRule()
    // The Compose test-host manifest exists only in debug. Skip before its Activity rule launches.
    @get:Rule val runtimeHost = org.junit.rules.TestRule { base, description ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                assumeTrue(BuildConfig.DEBUG)
                compose.apply(base, description).evaluate()
            }
        }
    }
    private fun saveRender(name: String) {
        // Draw the actual Android host view; PixelCopy's frame callback is unavailable on Android JVM.
        lateinit var bitmap: android.graphics.Bitmap
        compose.runOnIdle {
            val view = (compose as AndroidComposeTestRule<*, *>).activity.window.decorView
            bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
        }
        val file = java.io.File("build/reports/settings-ui/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun app(state: CopilotUiState=CopilotUiState(installedVersion="test"),
                    section: SettingsSection=SettingsSection.OVERVIEW, screen: CopilotScreen=CopilotScreen.SETTINGS,
                    scale: Float=1f, actions: CopilotActions=CopilotActions()) {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,scale)) {
            CopilotApp(state,screen,{},actions,section)
        } }
    }
    @Test fun ordinarySettingsShowThreeGroupsAndNoDeveloperEntry() {
        app()
        compose.onNodeWithText("Casco y Botón Bluetooth").assertExists()
        saveRender("settings-day")
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Voz, Permisos y Privacidad"))
        compose.onNodeWithText("Voz, Permisos y Privacidad").assertExists()
        compose.onNodeWithText("Herramientas de desarrollador").assertDoesNotExist()
        compose.onNodeWithText("Medición de micrófono").assertDoesNotExist()
    }
    @Test fun nestedSettingsReturnThroughTheirGroup() {
        app()
        compose.onNodeWithText("Navegación y Destinos").performClick()
        compose.onNodeWithText("Integraciones").performClick()
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Llamadas"))
        compose.onNodeWithText("Llamadas").performClick()
        compose.onNodeWithContentDescription("Volver a Integraciones").performClick()
        compose.onNodeWithContentDescription("Volver a Navegación y Destinos").performClick()
        compose.onNodeWithText("Navegación y Destinos").assertExists()
    }
    @Test fun tripShowsSearchDispatchWithoutClaimingAnActiveRoute() {
        app(state=CopilotUiState(tripActive=true, navigationContext=TripNavigationContext("session",
            NavigationDispatch("waze", NavigationRequestKind.SEARCH, "gasolinera", 1000))),
            screen=CopilotScreen.TRIP, scale=1.5f)
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Navegación de este viaje"))
        compose.onNodeWithText("Búsqueda enviada a Waze: gasolinera").assertExists()
        compose.onNodeWithText("El lugar elegido en el navegador no está confirmado en APP.").assertExists()
        compose.onNodeWithText("Estado de ruta no disponible.").assertExists()
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Sin datos recientes de navegación de Waze o Maps."))
        saveRender("phase2-trip-search-large-font")
    }
    @Test fun tripShowsObservedMapsFieldsWithoutTreatingThemAsAConfirmedRoute() {
        val source = ContextSource.GOOGLE_MAPS_NOTIFICATION
        fun <T> field(value: T) = ContextValue(value, source, 1_000, 1_000, .65f, 15_000)
        val observed = TripContextSnapshot(navigation = NavigationContext(
            activeProvider = field("Google Maps"), remainingMinutes = field(7),
            remainingDistanceText = field("3.2 km"), nextTurnInstruction = field("Gira a la derecha"),
            currentOrTargetStreet = field("Avenida Los Próceres")))
        app(state = CopilotUiState(tripActive = true, observedRoute = observed,
            observedRouteAtElapsedMs = 2_000), screen = CopilotScreen.TRIP, scale = 1.5f)
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Datos recientes de Google Maps"))
        compose.onNodeWithText("Tiempo restante observado: 7 min").assertExists()
        compose.onNodeWithText("Distancia restante observada: 3.2 km").assertExists()
        compose.onNodeWithText("Próximo giro observado: Gira a la derecha").assertExists()
        compose.onNodeWithText("Vía indicada: Avenida Los Próceres").assertExists()
        compose.onNodeWithText("La lectura no confirma el destino ni que la ruta siga activa.").assertExists()
        saveRender("phase2-trip-maps-observed-large-font")
    }
    @Test fun tripUsesOwnGpsOnlyAsClearlyLabeledFallback() {
        val location = LocationSample(1_000, 1_000, 13.7, -89.2, null, null,
            null, null, null, 5f, "gps", false)
        val own = TripContextSnapshot(
            location = ContextValue(location, ContextSource.ANDROID_GPS, 1_000, 1_000, .95f, 10_000),
            target = ContextValue(TargetDestination("Casa", 13.7, -89.19),
                ContextSource.SAVED_DESTINATION_COORDS, 1_000, 1_000, 1f, 86_400_000),
            sessionDurationMs = ContextValue(12 * 60_000L, ContextSource.APP_SESSION,
                2_000, 2_000, 1f, 5_000))
        app(state = CopilotUiState(tripActive = true, observedRoute = own,
            observedRouteAtElapsedMs = 2_000), screen = CopilotScreen.TRIP)
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Viaje en APP: 12 min"))
        compose.onNodeWithText("Viaje en APP: 12 min").assertExists()
        compose.onNodeWithText("Casa está a 1,1 km en línea recta, según GPS de APP.").assertExists()
        compose.onNodeWithText("Sin datos recientes de navegación de Waze o Maps.").assertExists()
    }
    @Test fun tripExplainsMissingNavigationNoticeAccess() {
        app(state = CopilotUiState(tripActive = true, navigationNoticesReady = false),
            screen = CopilotScreen.TRIP)
        compose.onNodeWithTag("settings_content").performScrollToNode(
            hasText("Sin acceso a avisos de Maps/Waze.", substring = true))
        compose.onNodeWithText("Sin acceso a avisos de Maps/Waze.", substring = true).assertExists()
    }
    @Test fun wazeObservationIsHiddenFromOrdinaryNavigation() {
        app(section=SettingsSection.NAVIGATION)
        compose.onNodeWithText("Notificaciones de Waze y Maps · experimental").assertDoesNotExist()
    }
    @Test fun wazeChoiceExplainsObservedCompatibilityAndRecommendsMaps() {
        app(section=SettingsSection.NAVIGATION)
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Recomendamos Google Maps", substring = true))
        compose.onNodeWithText("Recomendamos Google Maps", substring = true).assertExists()
        saveRender("phase2-waze-context-warning")
    }
    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp-night")
    fun wazeCompatibilityWarningIsReadableInNightMode() {
        app(section=SettingsSection.NAVIGATION, scale=1.5f)
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Recomendamos Google Maps", substring = true))
        compose.onNodeWithText("Recomendamos Google Maps", substring = true).assertExists()
        saveRender("phase2-waze-context-warning-night")
    }
    @Test fun observerModeIsSeparateFromTheBubbleAndRequiresAnExplicitToggle() {
        var chosen: Boolean? = null
        app(state=CopilotUiState(overlayEnabled=true, overlayPermissionGranted=true),
            section=SettingsSection.APPEARANCE, scale=2f,
            actions=CopilotActions(onOverlayObserverChange={ chosen=it }))
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Mostrar contexto observado junto a la burbuja"))
        compose.onNodeWithText("Mostrar contexto observado junto a la burbuja").performClick()
        assertEquals(true, chosen)
        saveRender("phase2-observer-setting-large-font")
    }
    @Test fun playstoreCanReenableWazeNoticesWithoutOpeningTheMicrophone() {
        assumeTrue(BuildConfig.IS_PLAYSTORE_BUILD)
        var enabled: Boolean? = null
        var microphoneRequests = 0
        app(state=CopilotUiState(wazeObservationEnabled=false), section=SettingsSection.NAVIGATION,
            actions=CopilotActions(onWazeObservationChange={enabled=it}, onManualTripCommand={microphoneRequests++}))
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Observar avisos de navegación durante el viaje"))
        compose.onNodeWithText("Observar avisos de navegación durante el viaje").performClick()
        assertEquals(true, enabled)
        assertEquals(0, microphoneRequests)
    }
    @Test fun screenConsentEmitsOnlyItsExplicitPreferenceCallback() {
        assumeTrue(BuildConfig.ALLOW_ACCESSIBILITY_WAZE)
        var enabled:Boolean?=null
        var microphoneRequests=0
        app(state=CopilotUiState(developerAccess=DeveloperAccess(true,true)),section=SettingsSection.NAVIGATION,
            actions=CopilotActions(onWazeScreenChange={enabled=it},onManualTripCommand={microphoneRequests++}))
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Observar pantalla de navegación"))
        compose.onNodeWithText("Observar pantalla de navegación").performClick()
        assertEquals(true,enabled)
        assertEquals(0,microphoneRequests)
    }
    @Test fun emptyScreenReportCanBeCopiedToDiagnoseMissingWazeText() {
        assumeTrue(BuildConfig.ALLOW_ACCESSIBILITY_WAZE)
        var copied: String? = null
        app(state=CopilotUiState(developerAccess=DeveloperAccess(true,true), tripActive=true,
            wazeScreenEnabled=true, wazeScreenGranted=true,
            wazeScreen=WazeScreenSnapshot(sessionId="session",connected=true)),
            section=SettingsSection.NAVIGATION,
            actions=CopilotActions(onCopyDiagnostics={copied=it}))
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Copiar estado de pantalla de navegación"))
        compose.onNodeWithText("Copiar estado de pantalla de navegación").assertIsEnabled().performClick()
        assertTrue(copied.orEmpty().contains("Lectura activada=true; permiso=true"))
        assertTrue(copied.orEmpty().contains("Sin textos accesibles de Waze"))
    }
    @Test fun mapsAddressesAreExplicitAndDoNotStartATrip() {
        var selected: String? = null
        var saved: Pair<NavigationDestination, String>? = null
        var trips = 0
        app(state=CopilotUiState(navigationProvider=NavigationApp.GOOGLE_MAPS),
            section=SettingsSection.NAVIGATION, scale=1.5f,
            actions=CopilotActions(onNavigationProviderChange={selected=it},
                onNavigationAddressSave={destination,address->saved=destination to address},
                onToggleTrip={trips++}))
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Dirección de Casa para Google Maps"))
        compose.onNodeWithText("Dirección de Casa para Google Maps").assertExists()
        saveRender("phase2-maps-settings-large-font")
        assertNull(selected)
        assertNull(saved)
        assertEquals(0,trips)
    }
    @Test fun recentWazeTurnIsShownAsObservedData() {
        val source = ContextSource.WAZE_ACCESSIBILITY
        val route = TripContextSnapshot(navigation = NavigationContext(
            activeProvider = ContextValue("Waze", source, 1_000, 1_000, .85f, 15_000),
            nextTurnInstruction = ContextValue("Gira a la izquierda", source, 1_000, 1_000, .85f, 15_000),
            distanceToNextTurnText = ContextValue("300 m", source, 1_000, 1_000, .85f, 15_000)))
        app(state=CopilotUiState(tripActive=true, observedRoute=route,
            observedRouteAtElapsedMs=2_000),screen=CopilotScreen.TRIP,scale=1.5f)
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Próximo giro observado: Gira a la izquierda"))
        compose.onNodeWithText("Próximo giro observado: Gira a la izquierda").assertExists()
        compose.onNodeWithText("Distancia al giro: 300 m").assertExists()
        compose.onNodeWithText("La lectura no confirma el destino ni que la ruta siga activa.").assertExists()
        saveRender("phase2-screen-turn-storm-150")
    }
    @Test fun wazeObservationShowsBoundedSampleAndCopyIsExplicit() {
        var copied: String? = null
        app(state=CopilotUiState(developerAccess=DeveloperAccess(true,true), tripActive=true,
            wazeObservationEnabled=true, wazeObservationGranted=true,
            wazeObservation=WazeObservationSnapshot("session",true,
                WazeNotificationSample("waze-key","navigation",1000,2000,mapOf("android.text" to "En 300 m")))),
            section=SettingsSection.NAVIGATION,scale=1.5f,
            actions=CopilotActions(onCopyDiagnostics={copied=it}))
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Copiar notificación de navegación"))
        compose.onNodeWithText("En 300 m").assertExists()
        assertNull(copied)
        saveRender("phase2-waze-observation-large-font")
        compose.onNodeWithText("Copiar notificación de navegación").performClick()
        assertTrue(copied.orEmpty().contains("android.text: En 300 m"))
        compose.onNodeWithText("Observar notificaciones de navegación").assertIsNotEnabled()
    }
    @Test fun audioAtLargeFontStillOffersInstallationSpecificPermission() {
        app(section=SettingsSection.AUDIO,scale=2f)
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Acceso a controles multimedia"))
        compose.onNodeWithText("Acceso a controles multimedia").assertExists()
        compose.onNodeWithText("APP y APP de pruebas tienen permisos independientes.",substring=true).assertExists()
        saveRender("audio-large-font")
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
        val row=compose.onNode(hasText("Casco y Botón Bluetooth") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(row.height/density>=48f)
    }
    @Test fun narrowNightLayoutStillNavigatesWithLargeText() {
        RuntimeEnvironment.setQualifiers("w320dp-h800dp-night")
        app(scale=1.5f)
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Voz, Permisos y Privacidad"))
        compose.onNodeWithText("Voz, Permisos y Privacidad").performClick()
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Privacidad y permisos"))
        compose.onNodeWithText("Privacidad y permisos").performClick()
        saveRender("permissions-night")
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Abrir permisos de APP en Android"))
        compose.onNodeWithText("Abrir permisos de APP en Android").assertExists()
    }
}
