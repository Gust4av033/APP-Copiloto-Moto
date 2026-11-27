package com.gust4av033.appcopilotomoto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.gust4av033.appcopilotomoto.presentation.*
import com.gust4av033.appcopilotomoto.presentation.components.*
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TripMapUiTest {
    private val compose = createComposeRule()
    @get:Rule val host = org.junit.rules.TestRule { base, description -> object : org.junit.runners.model.Statement() {
        override fun evaluate() { assumeTrue(BuildConfig.DEBUG); compose.apply(base, description).evaluate() }
    } }
    private fun session(): TripMapSession {
        val track = TripTrackAccumulator(); track.start("test-fixture", 1000)
        fun sample(t: Long, lon: Double) = LocationSample(t, t, 14.6, lon, null, 10f, .3f, null, null, 3f, "test-fixture", false)
        track.acceptRecorded(sample(1000, -90.5), 0.0)
        track.acceptRecorded(sample(2000, -90.4999), 12.5)
        return track.snapshot
    }
    private val fixtureRenderer = TripMapRenderer { _, _, _ ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("RENDERIZADOR DE PRUEBA\nSin cartografía real; solo prueba de controles Compose.")
        }
    }
    private fun app(state: CopilotUiState, actions: CopilotActions = CopilotActions(), scale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides fixtureRenderer,
                LocalDensity provides Density(LocalDensity.current.density, scale)) {
                CopilotApp(state, CopilotScreen.TRIP, {}, actions)
            }
        }
    }
    private fun reveal(text: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) compose.onNodeWithTag("settings_content").performScrollToIndex(0)
        repeat(30) {
            if (!node.isDisplayed()) compose.onNodeWithTag("settings_content").performTouchInput {
                swipeUp(startY = height * .8f, endY = height * .35f, durationMillis = 250)
            }
        }
        return node.assertIsDisplayed()
    }
    @Test fun openingTripWithoutGpsShowsRealRequirementsAndDoesNotStartAudio() {
        var audio = 0; var gps = false
        app(CopilotUiState(), CopilotActions(onToggleTrip = { audio++ }, onManualTripCommand = { audio++ },
            onTripLocationChange = { gps = it }))
        reveal(RuntimeEnvironment.getApplication().getString(R.string.trip_map_gps_off))
        reveal(RuntimeEnvironment.getApplication().getString(R.string.trip_location_title)).performClick()
        assertTrue(gps); assertEquals(0, audio)
    }
    @Test fun onlyAnExplicitSessionActionDispatchesStartOrStop() {
        var sessions = 0
        app(CopilotUiState(), CopilotActions(onToggleTrip = { sessions++ }))
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.trip_start)).performClick()
        assertEquals(1, sessions)
    }
    @Test fun standaloneCancelRemainsVisibleBeforeTheMap() {
        var cancels = 0
        app(CopilotUiState(standaloneListening = true), CopilotActions(onCancelStandaloneCommand = { cancels++ }))
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.listen_cancel))
            .assertIsDisplayed().performClick()
        assertEquals(1, cancels)
    }
    @Test fun activeTripAllowsRevokingOnlineMapsButCannotEnableThemOrGps() {
        var online: Boolean? = null
        app(CopilotUiState(tripActive = true, mapOnlineEnabled = true), CopilotActions(onMapOnlineChange = { online = it }))
        reveal(RuntimeEnvironment.getApplication().getString(R.string.trip_location_title)).assertIsNotEnabled()
        reveal(RuntimeEnvironment.getApplication().getString(R.string.trip_map_online_title)).performClick()
        assertEquals(false, online)
    }
    @Test fun staleSpeedIsUnavailableAndOpeningWazeIsASeparateAction() {
        var opens = 0; var sessions = 0
        app(CopilotUiState(tripActive = true, tripMap = session(), observedRouteAtElapsedMs = 15_000,
            tripLocationEnabled = true, tripLocationAvailable = true, navigationAvailable = true),
            CopilotActions(onOpenTripNavigator = { opens++ }, onToggleTrip = { sessions++ }))
        reveal(RuntimeEnvironment.getApplication().getString(R.string.trip_map_fix_last))
        compose.onNodeWithText("36 km/h").assertDoesNotExist()
        reveal("Abrir Waze").performClick()
        assertEquals(1, opens); assertEquals(0, sessions)
    }
    @Test fun mapHasPrimaryViewportAndMotorcycleShortcutWithoutStartingServices() {
        var destination: CopilotScreen? = null
        var starts = 0
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides fixtureRenderer) {
                CopilotApp(CopilotUiState(), CopilotScreen.MAP, { destination = it },
                    CopilotActions(onToggleTrip = { starts++ }))
            }
        }
        val viewport = compose.onNodeWithTag("ride_map_viewport").fetchSemanticsNode().boundsInRoot
        assertTrue(viewport.height > compose.onRoot().fetchSemanticsNode().boundsInRoot.height / 2)
        compose.onNodeWithContentDescription("Mi moto", ignoreCase = true).performClick()
        assertEquals(CopilotScreen.MOTORCYCLE, destination)
        assertEquals(0, starts)
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.map_start_short)).performClick()
        assertEquals(1, starts)
    }
    @Test fun revokingGpsRemovesPreviousTraceAndMarkerFromMainMap() {
        val recording = session()
        val permitted = androidx.compose.runtime.mutableStateOf(true)
        var presented: TripMapSession? = null
        compose.setContent {
            val renderer = TripMapRenderer { track, _, _ ->
                androidx.compose.runtime.SideEffect { presented = track }
            }
            CompositionLocalProvider(LocalTripMapRenderer provides renderer) {
                CopilotApp(CopilotUiState(tripActive = true, tripMap = recording, tripLocationEnabled = true,
                    tripLocationAvailable = permitted.value), CopilotScreen.MAP, {}, CopilotActions())
            }
        }
        compose.runOnIdle { assertEquals(recording, presented); permitted.value = false }
        compose.runOnIdle { assertNull(presented!!.lastFix); assertTrue(presented!!.segments.isEmpty()) }
    }
    @Test fun movingMapHidesSecondaryActionsAndDoesNotUnlockOnStaleGps() {
        var picks = 0
        val current = androidx.compose.runtime.mutableStateOf(CopilotUiState(tripActive = true,
            tripMap = session(), observedRouteAtElapsedMs = 2000, tripLocationEnabled = true,
            tripLocationAvailable = true, audioDevices = AudioDevicesUi(permissionGranted = true,
                bluetoothOutputs = listOf("Fixture headset"))))
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides fixtureRenderer) {
                CopilotApp(current.value, CopilotScreen.MAP, {}, CopilotActions(onChooseMapDestination = { picks++ }))
            }
        }
        compose.onNodeWithContentDescription("Mi moto", ignoreCase = true).assertExists()
        compose.runOnIdle {
            val fix = current.value.tripMap.lastFix!!
            current.value = current.value.copy(observedRouteAtElapsedMs = 5000,
                tripMap = current.value.tripMap.copy(lastFix = fix.copy(value = fix.value.copy(elapsedRealtimeMs = 5000))))
        }
        compose.onNodeWithContentDescription("Mi moto", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("36").assertExists()
        compose.runOnIdle { current.value = current.value.copy(observedRouteAtElapsedMs = 20000) }
        compose.onNodeWithText("36").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mi moto", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Elegir destino").assertDoesNotExist()
        for (time in listOf(21000L, 26000L)) compose.runOnIdle {
            val fix = current.value.tripMap.lastFix!!.value.copy(elapsedRealtimeMs = time, speedMetersPerSec = 0f)
            current.value = current.value.copy(observedRouteAtElapsedMs = time,
                tripMap = current.value.tripMap.copy(lastFix = ContextValue(fix, ContextSource.ANDROID_GPS, time, time, 1f, 10000)))
        }
        compose.onNodeWithText("Elegir destino").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, picks) }
    }
    @Test fun idleMapDoesNotReusePreviousTripMetricsOrInventSearch() {
        var presented: TripMapSession? = null
        val renderer = TripMapRenderer { track, _, _ ->
            androidx.compose.runtime.SideEffect { presented = track }
        }
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides renderer) {
                CopilotApp(CopilotUiState(tripMap = session(), tripLocationEnabled = true,
                    tripLocationAvailable = true, observedRouteAtElapsedMs = 2000), CopilotScreen.MAP, {}, CopilotActions())
            }
        }
        compose.onNodeWithText("36").assertDoesNotExist()
        compose.onNodeWithText("Recorrido").assertDoesNotExist()
        compose.onNodeWithText("Elegir destino").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(session().lastFix, presented!!.lastFix)
            assertNull(presented!!.currentFix(2000))
            assertNull(presented!!.distanceMeters)
            assertTrue(presented!!.segments.isEmpty())
        }
    }
    @Test fun listeningIndicatorReflectsVoiceStateWithoutDispatchingCommands() {
        var commands = 0
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides fixtureRenderer) {
                CopilotApp(CopilotUiState(tripActive = true, interactionPhase = TripInteractionPhase.LISTENING),
                    CopilotScreen.MAP, {}, CopilotActions(onManualTripCommand = { commands++ }))
            }
        }
        compose.onNodeWithText("Escuchando").assertIsDisplayed()
        assertEquals(0, commands)
    }
    @Test fun routeHudOnlyShowsSuppliedFreshData() {
        val route = androidx.compose.runtime.mutableStateOf<RouteHudState?>(null)
        compose.setContent {
            com.gust4av033.appcopilotomoto.presentation.theme.CopilotTheme {
                Column { ManeuverBanner(route.value); EtaBar(route.value) }
            }
        }
        compose.onNodeWithText("Llegada").assertDoesNotExist()
        compose.runOnIdle { route.value = RouteHudState(120.0, "Girá a la derecha", "Calle fixture", "14:35", "12 min", 4.2, "Ruta de APP", true) }
        compose.onNodeWithText("120").assertExists()
        compose.onNodeWithText("14:35").assertExists()
        compose.runOnIdle { route.value = route.value!!.copy(fresh = false) }
        compose.onNodeWithText("120").assertDoesNotExist()
        compose.onNodeWithText("14:35").assertDoesNotExist()
        compose.onNodeWithText("Girá a la derecha").assertDoesNotExist()
        compose.onNodeWithText("Calle fixture").assertDoesNotExist()
    }
    @Test fun safeStopRequiresSustainedPressAndCancelsOnReleaseOrDisposal() {
        var stops = 0
        val visible = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            com.gust4av033.appcopilotomoto.presentation.theme.CopilotTheme {
                if (visible.value) HoldToStopButton(onStop = { stops++ })
            }
        }
        compose.mainClock.autoAdvance = false
        val button = compose.onNodeWithTag("ride_stop_hold")
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        button.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertEquals(0, stops) }
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        button.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, stops) }
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertEquals(1, stops) }
    }
    @Test fun accessibleStopAsksForExplicitConfirmation() {
        var stops = 0
        compose.setContent { HoldToStopButton(onStop = { stops++ }) }
        compose.onNodeWithTag("ride_stop_hold").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(0, stops) }
        compose.onNodeWithText("Seguir viaje").performClick()
        compose.runOnIdle { assertEquals(0, stops) }
        compose.onNodeWithTag("ride_stop_hold").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Detener").performClick()
        compose.runOnIdle { assertEquals(1, stops) }
    }
    private fun capture(theme: String, scale: Float, riding: Boolean) {
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-${if (theme == "night") "night" else "notnight"}")
        val current = androidx.compose.runtime.mutableStateOf(CopilotUiState(tripActive = riding,
            tripMap = session(), observedRouteAtElapsedMs = 2000,
            tripLocationEnabled = true, tripLocationAvailable = true,
            audioDevices = AudioDevicesUi(permissionGranted = true, bluetoothOutputs = listOf("Fixture headset"))))
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides fixtureRenderer,
                LocalDensity provides Density(LocalDensity.current.density, scale)) {
                CopilotApp(current.value, CopilotScreen.MAP, {}, CopilotActions())
            }
        }
        if (riding) {
            compose.runOnIdle {
                val fix = current.value.tripMap.lastFix!!
                current.value = current.value.copy(observedRouteAtElapsedMs = 5000,
                    tripMap = current.value.tripMap.copy(lastFix = fix.copy(value = fix.value.copy(elapsedRealtimeMs = 5000))))
            }
            compose.onNodeWithText("36").assertIsDisplayed()
            compose.onNodeWithTag("ride_stop_hold").assertIsDisplayed()
            compose.onNodeWithContentDescription("Mi moto", ignoreCase = true).assertDoesNotExist()
        } else compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.map_start_short)).assertIsDisplayed()
        compose.runOnIdle {
            val view = (compose as AndroidComposeTestRule<*, *>).activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val file = File("build/reports/ui-vistazo/${if (riding) "riding" else "idle"}-$theme-${if (scale == 2f) "200" else "100"}.png")
            file.parentFile?.mkdirs(); file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun idleDayNormal() = capture("day", 1f, false)
    @Test fun idleNightLarge() = capture("night", 2f, false)
    @Test fun ridingDayNormal() = capture("day", 1f, true)
    @Test fun ridingNightLarge() = capture("night", 2f, true)
}
