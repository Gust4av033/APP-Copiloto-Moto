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
    val active = LocalTripMapActive.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Text(
            "RENDERIZADOR DE PRUEBA\nMapa sustituido; sin cartografía real.",
            Modifier.align(Alignment.Center).padding(20.dp)
        )
        if (!active) TripMapCameraControls(
            cameraMode = cameraMode,
            previousFollowMode = previousMode,
            inclined = inclined,
            hasTrace = session.segments.any { it.size >= 2 },
            onModeSelected = {
                if (it != TripMapCameraMode.FREE) previousMode = it
                cameraMode = it
            },
            onPerspectiveChanged = { inclined = it },
            onCenter = { cameraMode = previousMode },
            onFitTrace = { cameraMode = TripMapCameraMode.FREE },
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = LocalTripMapTopInset.current + Glass.mapControlGap, end = Glass.mapScreenInset)
        )
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

    @Test fun cameraButtonAlternatesModesAndUsesDifferentPerspectiveAction() {
        val selected = mutableStateOf(TripMapCameraMode.NORTH_UP)
        val perspective = mutableStateOf(true)
        compose.setContent {
            TripMapCameraControls(
                cameraMode = selected.value,
                previousFollowMode = TripMapCameraMode.NORTH_UP,
                inclined = perspective.value,
                hasTrace = false,
                onModeSelected = { selected.value = it },
                onPerspectiveChanged = { perspective.value = it },
                onCenter = {},
                onFitTrace = {}
            )
        }
        val northDescription = compose.onNodeWithTag("map_camera_mode").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        assertTrue(northDescription.contains("Norte arriba"))
        compose.onNodeWithTag("map_camera_mode").performClick()
        assertEquals(TripMapCameraMode.FOLLOW_HEADING, selected.value)
        val headingDescription = compose.onNodeWithTag("map_camera_mode").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        assertTrue(headingDescription.contains("Cambiar a Norte arriba"))
        val perspectiveDescription = compose.onNodeWithTag("map_perspective").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        assertTrue(perspectiveDescription.contains("Vista inclinada"))
        compose.onNodeWithTag("map_perspective").performClick()
        assertFalse(perspective.value)
        val flatDescription = compose.onNodeWithTag("map_perspective").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        assertTrue(flatDescription.contains("Cambiar a 3D"))
    }

    @Test fun freeCameraShowsOnlyCenterAndAvailableTraceActions() {
        val selected = mutableStateOf(TripMapCameraMode.FREE)
        compose.setContent {
            TripMapCameraControls(
                cameraMode = selected.value,
                previousFollowMode = TripMapCameraMode.NORTH_UP,
                inclined = true,
                hasTrace = true,
                onModeSelected = { selected.value = it },
                onPerspectiveChanged = {},
                onCenter = { selected.value = TripMapCameraMode.NORTH_UP },
                onFitTrace = {}
            )
        }
        compose.onNodeWithTag("map_camera_mode").assertDoesNotExist()
        compose.onNodeWithTag("map_center").assertIsDisplayed()
        compose.onNodeWithTag("map_trace").assertIsDisplayed()
        compose.onNodeWithTag("map_center").performClick()
        assertEquals(TripMapCameraMode.NORTH_UP, selected.value)
        compose.onNodeWithTag("map_center").assertDoesNotExist()
    }

    @Test fun activeTripKeepsOnlySpeedStopAndAttributionOnMap() {
        app(CopilotUiState(
            tripActive = true,
            tripMap = session(),
            observedRouteAtElapsedMs = 2500,
            tripLocationEnabled = true,
            tripLocationAvailable = true
        ))
        compose.onNodeWithTag("map_status_pill").assertDoesNotExist()
        compose.onNodeWithTag("map_camera_mode").assertDoesNotExist()
        compose.onNodeWithTag("map_perspective").assertDoesNotExist()
        compose.onNodeWithTag("map_speed").assertIsDisplayed()
        compose.onNodeWithTag("map_stop").assertIsDisplayed()
        compose.onNodeWithTag("map_attribution").assertIsDisplayed()
    }

    @Test fun activeTripCanRevokeOnlineMapsAndOpenTheChosenNavigatorSeparately() {
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
        compose.onNodeWithTag("map_speed").performClick()
        reveal(RuntimeEnvironment.getApplication().getString(R.string.trip_map_online_title)).performClick()
        assertEquals(false, online)
        reveal(RuntimeEnvironment.getApplication().getString(
            R.string.trip_map_open_navigator, state.navigationProvider.label
        )).performClick()
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

    @Test fun mainMapHasFiveIdleItemsAndMotorcycleShortcutDoesNotStartServices() {
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
        val camera = compose.onNodeWithTag("map_camera_mode").fetchSemanticsNode().boundsInRoot
        val perspective = compose.onNodeWithTag("map_perspective").fetchSemanticsNode().boundsInRoot
        val attribution = compose.onNodeWithTag("map_attribution").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("map_dock").fetchSemanticsNode().boundsInRoot
        assertTrue(status.right <= camera.left)
        assertTrue(camera.bottom <= attribution.top)
        assertTrue(perspective.bottom <= attribution.top)
        assertTrue(attribution.bottom <= dock.top)
        assertTrue(attribution.top - maxOf(status.bottom, camera.bottom, perspective.bottom) >= viewport.height * .70f)
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

    private fun capture(theme: String, scale: Float, orientation: String = "portrait") {
        val size = if (orientation == "landscape") "w800dp-h360dp" else "w360dp-h800dp"
        RuntimeEnvironment.setQualifiers("$size-${if (theme == "night") "night" else "notnight"}")
        app(CopilotUiState(
            tripActive = true,
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
    @Test fun activeDayNormalCapture() = capture("day", 1f)
    @Test fun activeNightLargeCapture() = capture("night", 2f)
    @Test fun activeDayLargeCapture() = capture("day", 2f)
    @Test fun activeNightNormalLandscapeCapture() = capture("night", 1f, "landscape")
    @Test fun activeDayNormalLandscapeCapture() = capture("day", 1f, "landscape")
    @Test fun activeNightLargeLandscapeCapture() = capture("night", 2f, "landscape")
    @Test fun activeDayLargeLandscapeCapture() = capture("day", 2f, "landscape")
}
