package com.gust4av033.appcopilotomoto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
        Box(Modifier.fillMaxWidth().height(Glass.mapHeight).background(MaterialTheme.colorScheme.surfaceVariant)) {
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
    @Test fun cameraModeSelectorShowsSelectionAndChangesModeExplicitly() {
        val selected = mutableStateOf(TripMapCameraMode.FOLLOW_HEADING)
        compose.setContent {
            TripMapCameraControls(selected.value, headingAvailable = false, onSelected = { selected.value = it })
        }
        compose.onNodeWithTag("trip_map_camera_FOLLOW_HEADING").assertIsSelected()
        compose.onNodeWithTag("trip_map_camera_NORTH_UP").performClick()
        compose.onNodeWithTag("trip_map_camera_NORTH_UP").assertIsSelected()
        assertEquals(TripMapCameraMode.NORTH_UP, selected.value)
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
    private fun capture(theme: String, scale: Float) {
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-${if (theme == "night") "night" else "notnight"}")
        app(CopilotUiState(tripActive = true, tripMap = session(), observedRouteAtElapsedMs = 2500,
            tripLocationEnabled = true, tripLocationAvailable = true), scale = scale)
        reveal("Velocidad GPS")
        compose.onNodeWithText("36 km/h").assertExists()
        compose.runOnIdle {
            val view = (compose as AndroidComposeTestRule<*, *>).activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val file = File("build/reports/trip-map/trip-$theme-${if (scale == 2f) "200" else "100"}.png")
            file.parentFile?.mkdirs(); file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun nightNormal() = capture("night", 1f)
    @Test fun dayNormal() = capture("day", 1f)
    @Test fun nightLarge() = capture("night", 2f)
    @Test fun dayLarge() = capture("day", 2f)
}
