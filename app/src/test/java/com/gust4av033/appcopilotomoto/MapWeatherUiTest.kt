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
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.presentation.*
import com.gust4av033.appcopilotomoto.presentation.components.*
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
    private val renderer = TripMapRenderer { _, _, _ ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            Text("PRUEBA DE INTERFAZ\nMapa sustituido; clima sintético.", Modifier.align(androidx.compose.ui.Alignment.Center).padding(20.dp))
        }
    }
    @Test fun expiredWeatherIsNotDisplayedAsCurrentAndOpeningPanelDoesNotStartAudio() {
        val data = mutableStateOf(weather)
        var audio = 0
        var area: WeatherArea? = null
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides renderer) {
                CopilotApp(CopilotUiState(weather = data.value), CopilotScreen.MAP, {},
                    CopilotActions(onToggleTrip = { audio++ }, onManualTripCommand = { audio++ }, onWeatherArea = { area = it }))
            }
        }
        compose.onNodeWithText("24 °C").assertIsDisplayed().performClick()
        compose.onNodeWithText("Próximas horas").assertExists()
        compose.onNodeWithText("Mi ubicación").assertExists()
        compose.onNodeWithText("Listo").performClick()
        compose.runOnIdle { data.value = weather.copy(nowElapsedMs = 61_000) }
        compose.onNodeWithText("24 °C").assertDoesNotExist()
        compose.onNodeWithText("Clima no disponible").assertIsDisplayed()
        assertEquals(0, audio)
    }
    @Test @Config(sdk = [35], qualifiers = "w320dp-h480dp-night")
    fun weatherDisclosureCanBeReadToTheEndAtTwoHundredPercentFont() {
        RuntimeEnvironment.setFontScale(2f)
        var accepted = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                com.gust4av033.appcopilotomoto.presentation.theme.CopilotTheme {
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
    private fun capture(theme: String, scale: Float) {
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-${if (theme == "night") "night" else "notnight"}")
        compose.setContent {
            CompositionLocalProvider(LocalTripMapRenderer provides renderer,
                LocalDensity provides Density(LocalDensity.current.density, scale)) {
                CopilotApp(CopilotUiState(weather = weather), CopilotScreen.MAP, {}, CopilotActions())
            }
        }
        compose.onNodeWithText("24 °C").assertIsDisplayed()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.map_start_short)).assertIsDisplayed()
        compose.runOnIdle {
            val view = (compose as AndroidComposeTestRule<*, *>).activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = File("build/reports/map-weather/map-$theme-${if (scale == 2f) "200" else "100"}.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun nightNormal() = capture("night", 1f)
    @Test fun dayNormal() = capture("day", 1f)
    @Test fun nightLarge() = capture("night", 2f)
    @Test fun dayLarge() = capture("day", 2f)
}
