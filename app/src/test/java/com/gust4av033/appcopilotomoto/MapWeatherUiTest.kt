package com.gust4av033.appcopilotomoto

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.presentation.*
import com.gust4av033.appcopilotomoto.presentation.components.*
import com.gust4av033.appcopilotomoto.presentation.theme.CopilotTheme
import org.junit.Assert.*
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
class MapWeatherUiTest {
    @get:Rule val compose = createComposeRule()
    private val epoch = 1_800_000_000_000L
    private val weather = WeatherUiState(enabled = true, hasTarget = true, refreshAvailable = true,
        destinationLabel = "Destino de prueba", nowElapsedMs = 1000, nowEpochMs = epoch,
        data = ContextValue(WeatherObservation(24.0, "identified fixture", 61, 12.0, epoch, "America/El_Salvador",
            listOf(WeatherHour(epoch + 3600_000, 23.0, 80, 61))), ContextSource.REPLAY_FIXTURE, epoch, 1000, 1f, 60_000, true))

    @Test fun compactWeatherUsesExistingDataAndDetailsWithoutStartingAudio() {
        val data = mutableStateOf(weather)
        var audioActions = 0
        var area: WeatherArea? = null
        var weatherEnabled: Boolean? = null
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides labeledTripMapRenderer()) {
                CopilotApp(CopilotUiState(weather = data.value), CopilotScreen.MAP, {},
                    CopilotActions(
                        onToggleTrip = { audioActions++ },
                        onManualTripCommand = { audioActions++ },
                        onWeatherArea = { area = it },
                        onWeatherEnabled = { weatherEnabled = it }
                    ))
            }
        }
        compose.onNodeWithText("24 °C").assertIsDisplayed()
        compose.onNodeWithTag("map_status_pill").performClick()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.weather_hourly)).assertExists()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.weather_here))
            .performClick()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.weather_online))
            .performClick()
        assertEquals(WeatherArea.HERE, area)
        assertEquals(false, weatherEnabled)
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.weather_attribution)).assertExists()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.ride_done)).performClick()
        compose.runOnIdle { data.value = weather.copy(nowElapsedMs = 61_000) }
        compose.onNodeWithText("24 °C").assertDoesNotExist()
        compose.onNodeWithTag("map_status_pill").performClick()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.weather_unavailable)).assertExists()
        assertEquals(0, audioActions)
    }

    @Test fun tripKeepsWeatherVisibleAndUnknownMotionDoesNotOpenDetails() {
        var calls = 0
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides labeledTripMapRenderer()) {
                CopilotApp(CopilotUiState(tripActive = true, weather = weather), CopilotScreen.MAP, {},
                    CopilotActions(onToggleTrip = { calls++ }, onRefreshWeather = { calls++ }))
            }
        }
        compose.onNodeWithText("24 °C").assertIsDisplayed()
        compose.onNodeWithTag("map_status_pill").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("map_details_content").assertDoesNotExist()
        assertEquals(0, calls)
    }

    @Test fun oldObservationShowsItsAgeInsteadOfClaimingCurrentWeather() {
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides labeledTripMapRenderer(),
                LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                CopilotApp(CopilotUiState(weather = weather.copy(nowEpochMs = epoch + 2 * 3_600_000, nowElapsedMs = 1000 + 2 * 3_600_000,
                    data = weather.data!!.copy(ttlMs = 3 * 3_600_000))),
                    CopilotScreen.MAP, {}, CopilotActions())
            }
        }
        compose.onNodeWithText("24 °C").assertIsDisplayed()
        compose.onNodeWithText("hace 2 h").assertIsDisplayed()
        compose.onNodeWithText("Vigente").assertDoesNotExist()
        compose.onNodeWithTag("map_status_pill").assertHeightIsEqualTo(48.dp)
    }

    @Test fun activatingWeatherIsAnExplicitActionFromTheMapDetails() {
        var enabled: Boolean? = null
        var audioActions = 0
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides labeledTripMapRenderer()) {
                CopilotApp(CopilotUiState(), CopilotScreen.MAP, {},
                    CopilotActions(onWeatherEnabled = { enabled = it }, onToggleTrip = { audioActions++ }))
            }
        }
        compose.onNodeWithTag("map_status_pill").performClick()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.weather_online))
            .performClick()
        assertEquals(true, enabled)
        assertEquals(0, audioActions)
    }

    @Test fun onlineMapDisclosureExplainsTileDataAndRequiresAnExplicitChoice() {
        var accepted = 0
        var dismissed = 0
        val visible = mutableStateOf(true)
        compose.setContent {
            if (visible.value) CopilotTheme {
                ProminentDisclosureDialog(
                    DisclosureKind.ONLINE_MAP,
                    onContinue = { accepted++; visible.value = false },
                    onDismiss = { dismissed++; visible.value = false }
                )
            }
        }
        compose.onNodeWithText("APP solicita recursos de OpenFreeMap", substring = true).assertExists()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.disclosure_cancel))
            .performClick()
        assertEquals(0, accepted)
        assertEquals(1, dismissed)
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.disclosure_continue))
            .performClick()
        assertEquals(1, accepted)
    }

    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-night")
    fun weatherDisclosureCanBeReadToTheEndAtTwoHundredPercentFont() {
        RuntimeEnvironment.setFontScale(2f)
        var accepted = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                CopilotTheme {
                    ProminentDisclosureDialog(DisclosureKind.WEATHER, { accepted++ }, {})
                }
            }
        }
        val text = compose.onNodeWithTag("disclosure_body")
        val range = text.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        text.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2f, layouts.single().layoutInput.density.fontScale, .01f)
        assertTrue(range.maxValue() > 0f)
        text.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        compose.runOnIdle { assertEquals(range.maxValue(), range.value(), 1f); assertEquals(0, accepted) }
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.disclosure_continue)).performClick()
        assertEquals(1, accepted)
    }

    private fun capture(theme: String, scale: Float, orientation: String = "portrait") {
        val size = if (orientation == "landscape") "w800dp-h360dp" else "w360dp-h800dp"
        RuntimeEnvironment.setQualifiers("$size-${if (theme == "night") "night" else "notnight"}")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalTripMapRenderer provides labeledTripMapRenderer(),
                LocalDensity provides Density(density.density, scale)) {
                CopilotApp(CopilotUiState(weather = weather), CopilotScreen.MAP, {}, CopilotActions())
            }
        }
        compose.onNodeWithText("24 °C").assertIsDisplayed()
        compose.onNodeWithText("Vigente").assertDoesNotExist()
        compose.onNodeWithTag("map_status_pill").assertIsDisplayed()
        compose.onNodeWithTag("map_layers").assertIsDisplayed()
        compose.onNodeWithTag("map_perspective").assertIsDisplayed()
        compose.onNodeWithTag("map_attribution").assertIsDisplayed()
        compose.onNodeWithTag("map_dock").assertIsDisplayed()
        val viewport = compose.onNodeWithTag("ride_map_viewport").fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithTag("map_status_pill").fetchSemanticsNode().boundsInRoot
        val camera = compose.onNodeWithTag("map_layers").fetchSemanticsNode().boundsInRoot
        val perspective = compose.onNodeWithTag("map_perspective").fetchSemanticsNode().boundsInRoot
        val attribution = compose.onNodeWithTag("map_attribution").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("map_dock").fetchSemanticsNode().boundsInRoot
        if (orientation == "landscape") {
            assertTrue(viewport.right <= dock.left)
            assertTrue(camera.right <= viewport.right)
            assertTrue(attribution.right <= viewport.right)
        } else {
            assertTrue(!status.overlaps(camera))
            assertTrue(attribution.bottom <= dock.top)
            if (scale >= 1.5f) {
                assertTrue(attribution.top - maxOf(status.bottom, camera.bottom, perspective.bottom) >= viewport.height * .5f)
            }
        }
        compose.runOnIdle {
            val view = (compose as AndroidComposeTestRule<*, *>).activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = File("build/reports/trip-map/idle-$theme-${if (scale == 2f) "200" else "100"}-$orientation.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun idleNightNormalCapture() = capture("night", 1f)
    @Test fun idleNightLargeCapture() = capture("night", 2f)
}
