package com.gust4av033.appcopilotomoto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

internal fun labeledTripMapRenderer(): TripMapRenderer = TripMapRenderer { session, _, online ->
    var cameraMode by remember { mutableStateOf(TripMapCameraMode.NORTH_UP) }
    var previousMode by remember { mutableStateOf(TripMapCameraMode.NORTH_UP) }
    var inclined by remember { mutableStateOf(true) }
    var layers by remember { mutableStateOf(false) }
    var trees by remember { mutableStateOf(false) }
    val active = LocalTripMapActive.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        MapEdgeScrims()
        Text(
            "RENDERIZADOR DE PRUEBA\nMapa sustituido; sin cartografía real.",
            Modifier.align(Alignment.Center).padding(20.dp)
        )
        TripMapCameraControls(
            cameraMode = cameraMode, previousFollowMode = previousMode, inclined = inclined,
            onPerspectiveChanged = { inclined = it }, onCenter = { cameraMode = previousMode },
            onOptions = { layers = true }, riding = active && !LocalTripMapStopped.current,
            allowLayers = !active, onNavigator = LocalTripMapNavigator.current, navigatorLabel = LocalTripMapNavigatorLabel.current,
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = LocalTripMapTopInset.current + Glass.mapControlGap, end = Glass.mapScreenInset))
        if (layers && !active) MapLayersDialog(online, trees, null, false, cameraMode,
            { if (it != TripMapCameraMode.FREE) previousMode = it; cameraMode = it }, { trees = it }, {},
            if (session.segments.any { it.size >= 2 }) ({ cameraMode = TripMapCameraMode.FREE; layers = false }) else null,
            { layers = false })
        MapAttributionButton(
            online = online,
            onClick = {},
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = Glass.mapScreenInset, bottom = LocalTripMapBottomInset.current + Glass.mapControlGap)
        )
    }
}

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
        val track = TripTrackAccumulator()
        track.start("test-fixture", 1000)
        fun sample(t: Long, lon: Double) = LocationSample(t, t, 14.6, lon, null, 10f, .3f, null, null, 3f, "test-fixture", false)
        track.acceptRecorded(sample(1000, -90.5), 0.0)
        track.acceptRecorded(sample(2000, -90.4999), 12.5)
        return track.snapshot
    }

    private fun app(
        state: CopilotUiState,
        actions: CopilotActions = CopilotActions(),
        screen: CopilotScreen = CopilotScreen.MAP,
        scale: Float = 1f,
        navigate: (CopilotScreen) -> Unit = {}
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalTripMapRenderer provides labeledTripMapRenderer(),
                LocalDensity provides Density(density.density, scale)
            ) {
                CopilotApp(state, screen, navigate, actions)
            }
        }
    }

    private fun reveal(text: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithText(text)
        repeat(30) {
            if (!node.isDisplayed()) compose.onNodeWithTag("map_details_content").performTouchInput {
                swipeUp(startY = height * .8f, endY = height * .35f, durationMillis = 250)
            }
        }
        return node.assertIsDisplayed()
    }

    @Test fun openingStatusShowsGpsRequirementsWithoutStartingGpsOrAudio() {
        var audioActions = 0
        var enabledGps: Boolean? = null
        app(CopilotUiState(), CopilotActions(
            onToggleTrip = { audioActions++ },
            onManualTripCommand = { audioActions++ },
            onTripLocationChange = { enabledGps = it }
        ))
        compose.onNodeWithTag("map_status_pill").performClick()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.map_enable_location))
            .performClick()
        assertEquals(true, enabledGps)
        assertEquals(0, audioActions)
    }

    @Test fun onlyExplicitDockActionStartsASession() {
        var sessions = 0
        app(CopilotUiState(), CopilotActions(onToggleTrip = { sessions++ }))
        compose.onNodeWithTag("map_start").performClick()
        assertEquals(1, sessions)
    }

    @Test fun perspectiveAndLayerActionsAreSeparateAndDrivingKeepsOnlyContextualCenter() {
        val inclined = mutableStateOf(true)
        val driving = mutableStateOf(false)
        var layers = 0; var centers = 0
        compose.setContent {
            TripMapCameraControls(TripMapCameraMode.FREE, TripMapCameraMode.NORTH_UP, inclined.value,
                { inclined.value = it }, { centers++ }, { layers++ }, riding = driving.value)
        }
        compose.onNodeWithTag("map_perspective").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("map_layers").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("map_perspective").performClick()
        assertFalse(inclined.value)
        compose.onNodeWithTag("map_layers").performClick(); assertEquals(1, layers)
        compose.onNodeWithTag("map_trace").assertDoesNotExist()
        compose.onNodeWithTag("map_camera_mode").assertDoesNotExist()
        compose.runOnIdle { driving.value = true }
        compose.onNodeWithTag("map_layers").assertDoesNotExist()
        compose.onNodeWithTag("map_perspective").assertDoesNotExist()
        compose.onNodeWithTag("map_center").performClick(); assertEquals(1, centers)
    }
    @Test fun stoppedSessionRestoresDestinationsAndPerspectiveButNotLayers() {
        val track = TripTrackAccumulator()
        track.start("stopped-fixture", 1000)
        track.acceptRecorded(LocationSample(1000, 1000, 14.6, -90.5, null, 0f, .3f, null, null, 3f, "fixture", false), 0.0)
        app(CopilotUiState(tripActive = true, tripMap = track.snapshot, observedRouteAtElapsedMs = 1000,
            tripLocationEnabled = true, tripLocationAvailable = true))
        compose.onNodeWithTag("map_layers").assertDoesNotExist()
        compose.onNodeWithTag("map_perspective").assertIsDisplayed()
        compose.onNodeWithTag("map_motorcycle").assertIsDisplayed()
        compose.onNodeWithTag("map_settings").assertIsDisplayed()
        compose.onNodeWithTag("map_stop").assertIsDisplayed()
        compose.onNodeWithTag("map_status_pill").performClick()
        compose.onNodeWithTag("map_details_content").assertExists()
    }

    @Test fun layersOfferRealModesAndExplicitRefreshAndVegetationActions() {
        val mode = mutableStateOf(TripMapCameraMode.NORTH_UP)
        val trees = mutableStateOf(false)
        var refreshes = 0
        compose.setContent {
            MapLayersDialog(true, trees.value, null, false, mode.value, { mode.value = it },
                { trees.value = it }, { refreshes++ }, null, {})
        }
        val context = RuntimeEnvironment.getApplication()
        compose.onNodeWithText(context.getString(R.string.map_vegetation_title)).performClick()
        assertTrue(trees.value)
        compose.onNodeWithText(context.getString(R.string.trip_map_camera_free)).performScrollTo().performClick()
        assertEquals(TripMapCameraMode.FREE, mode.value)
        compose.onNodeWithText(context.getString(R.string.map_update)).performScrollTo().performClick()
        assertEquals(1, refreshes)
    }

    @Test fun activeTripKeepsOnlySpeedStopAndAttributionOnMap() {
        app(CopilotUiState(
            tripActive = true,
            tripMap = session(),
            observedRouteAtElapsedMs = 2500,
            tripLocationEnabled = true,
            tripLocationAvailable = true
        ))
        compose.onNodeWithTag("map_status_pill").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("map_camera_mode").assertDoesNotExist()
        compose.onNodeWithTag("map_perspective").assertDoesNotExist()
        compose.onNodeWithTag("map_speed").assertIsDisplayed()
        compose.onNodeWithTag("map_stop").assertIsDisplayed()
        compose.onNodeWithTag("map_attribution").assertIsDisplayed()
    }

    @Test fun movingTripKeepsConfigurationClosedAndOpensTheChosenNavigator() {
        var online: Boolean? = null
        var opens = 0
        val state = CopilotUiState(
            tripActive = true,
            tripMap = session(),
            observedRouteAtElapsedMs = 2500,
            tripLocationEnabled = true,
            tripLocationAvailable = true,
            mapOnlineEnabled = true,
            navigationAvailable = true
        )
        app(state, CopilotActions(onMapOnlineChange = { online = it }, onOpenTripNavigator = { opens++ }))
        compose.onNodeWithTag("map_speed").assertHasNoClickAction()
        compose.onNodeWithTag("map_status_pill").performClick()
        compose.onNodeWithTag("map_details_content").assertDoesNotExist()
        assertNull(online)
        compose.onNodeWithTag("map_navigator").performClick()
        assertEquals(1, opens)
    }

    @Test fun tripScreenShowsLabeledLastTripAndOpensTheMainMapWithoutEmbeddingIt() {
        var destination: CopilotScreen? = null
        app(
            CopilotUiState(tripMap = session().copy(endedAtElapsedMs = 2500, distanceMeters = 1250.0),
                observedRouteAtElapsedMs = 2500),
            screen = CopilotScreen.TRIP,
            navigate = { destination = it }
        )
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.trip_last_trip_title))
            .assertIsDisplayed()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.trip_map_duration))
            .assertExists()
        compose.onNodeWithTag("ride_map_viewport").assertDoesNotExist()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.trip_map_view_button))
            .performClick()
        assertEquals(CopilotScreen.MAP, destination)
    }

    @Test fun tripScreenLabelsTheEmptyLastTripStateInsteadOfShowingPreviousMetrics() {
        app(CopilotUiState(), screen = CopilotScreen.TRIP)
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.trip_last_trip_empty))
            .assertExists()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.trip_map_duration))
            .assertDoesNotExist()
        compose.onNodeWithTag("ride_map_viewport").assertDoesNotExist()
    }

    @Test fun mainMapRequestsConsentBeforeEnablingOnlineTiles() {
        var requested: Boolean? = null
        app(CopilotUiState(), CopilotActions(onMapOnlineChange = { requested = it }))
        compose.onNodeWithTag("map_status_pill").performClick()
        reveal(RuntimeEnvironment.getApplication().getString(R.string.trip_map_online_title)).performClick()
        assertEquals(true, requested)
    }

    @Test fun idleMapKeepsWeatherAndControlsSeparateWithoutStartingServices() {
        var destination: CopilotScreen? = null
        var starts = 0
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides labeledTripMapRenderer()) {
                CopilotApp(CopilotUiState(), CopilotScreen.MAP, { destination = it },
                    CopilotActions(onToggleTrip = { starts++ }))
            }
        }
        val viewport = compose.onNodeWithTag("ride_map_viewport").fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithTag("map_status_pill").fetchSemanticsNode().boundsInRoot
        val camera = compose.onNodeWithTag("map_layers").fetchSemanticsNode().boundsInRoot
        val perspective = compose.onNodeWithTag("map_perspective").fetchSemanticsNode().boundsInRoot
        val attribution = compose.onNodeWithTag("map_attribution").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("map_dock").fetchSemanticsNode().boundsInRoot
        assertTrue(status.right <= camera.left)
        assertTrue(camera.bottom <= attribution.top)
        assertTrue(perspective.bottom <= attribution.top)
        assertTrue(attribution.bottom <= dock.top)
        val weather = compose.onNodeWithTag("map_weather", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(weather.right <= camera.left || weather.top >= camera.bottom)
        assertTrue("Map remains the majority of the viewport: $weather $camera $attribution",
            attribution.top - maxOf(weather.bottom, camera.bottom, perspective.bottom) >= viewport.height * .5f)
        compose.onNodeWithTag("map_motorcycle").performClick()
        assertEquals(CopilotScreen.MOTORCYCLE, destination)
        assertEquals(0, starts)
    }

    @Test fun revokingGpsRemovesPreviousTraceAndMarkerFromMainMap() {
        val recording = session()
        val permitted = mutableStateOf(true)
        var presented: TripMapSession? = null
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides TripMapRenderer { track, _, _ ->
                SideEffect { presented = track }
            }) {
                CopilotApp(CopilotUiState(tripActive = true, tripMap = recording, tripLocationEnabled = true,
                    tripLocationAvailable = permitted.value), CopilotScreen.MAP, {}, CopilotActions())
            }
        }
        compose.runOnIdle { assertEquals(recording, presented); permitted.value = false }
        compose.runOnIdle { assertNull(presented!!.lastFix); assertTrue(presented!!.segments.isEmpty()) }
    }

    @Test fun mapReceivesCameraInsetsForCapsuleControlsAndDock() {
        var padding: androidx.compose.foundation.layout.PaddingValues? = null
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides TripMapRenderer { _, _, _ ->
                val insets = LocalTripMapCameraInsets.current
                androidx.compose.runtime.SideEffect { padding = insets }
            }) { CopilotApp(CopilotUiState(), CopilotScreen.MAP, {}, CopilotActions()) }
        }
        compose.runOnIdle {
            assertEquals(72.dp, padding!!.calculateTopPadding())
            assertEquals(72.dp, padding!!.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr))
            assertTrue(padding!!.calculateBottomPadding() > 120.dp)
        }
    }

    @Test fun safeStopRequiresSustainedPressAndCancelsOnReleaseOrDisposal() {
        var stops = 0
        var haptics = 0
        val feedback = object : androidx.compose.ui.hapticfeedback.HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) { haptics++ }
        }
        val visible = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            com.gust4av033.appcopilotomoto.presentation.theme.CopilotTheme {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalHapticFeedback provides feedback) {
                    if (visible.value) HoldToStopButton(onStop = { stops++ })
                }
            }
        }
        compose.mainClock.autoAdvance = false
        val button = compose.onNodeWithTag("ride_stop_hold")
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        button.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertEquals(0, stops); assertEquals(0, haptics) }
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        button.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, stops); assertEquals(1, haptics) }
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertEquals(1, stops); assertEquals(1, haptics) }
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
    private fun capture(theme: String, scale: Float, orientation: String = "portrait") {
        val size = if (orientation == "landscape") "w800dp-h360dp" else "w360dp-h800dp"
        RuntimeEnvironment.setQualifiers("$size-${if (theme == "night") "night" else "notnight"}")
        app(CopilotUiState(
            tripActive = true,
            navigationAvailable = true,
            weather = WeatherUiState(enabled = true, hasTarget = true, nowEpochMs = 1_800_000_000_000L, nowElapsedMs = 1000,
                data = ContextValue(WeatherObservation(24.0, "identified capture fixture", 61, 12.0, 1_800_000_000_000L,
                    "America/El_Salvador", emptyList()), ContextSource.REPLAY_FIXTURE, 1_800_000_000_000L, 1000, 1f, 60_000, true)),
            tripMap = session(),
            observedRouteAtElapsedMs = 2500,
            tripLocationEnabled = true,
            tripLocationAvailable = true
        ), scale = scale)
        compose.onNodeWithTag("map_speed").assertExists()
        compose.onNodeWithTag("map_stop").assertExists()
        compose.onNodeWithTag("map_attribution").assertExists()
        val viewport = compose.onNodeWithTag("ride_map_viewport").fetchSemanticsNode().boundsInRoot
        val attribution = compose.onNodeWithTag("map_attribution").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("map_dock").fetchSemanticsNode().boundsInRoot
        if (orientation == "landscape") {
            assertTrue(viewport.right <= dock.left)
            assertTrue(attribution.right <= viewport.right)
        } else {
            assertTrue(attribution.bottom <= dock.top)
            if (scale >= 1.5f) assertTrue(attribution.top - viewport.top >= viewport.height * .5f)
        }
        compose.runOnIdle {
            val view = (compose as AndroidComposeTestRule<*, *>).activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val file = File("build/reports/trip-map/active-$theme-${if (scale == 2f) "200" else "100"}-$orientation.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun activeNightNormalCapture() = capture("night", 1f)
    @Test fun activeDayLargeLandscapeCapture() = capture("day", 2f, "landscape")
}
