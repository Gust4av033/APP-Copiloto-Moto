package com.gust4av033.appcopilotomoto

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotApp
import com.gust4av033.appcopilotomoto.presentation.CopilotScreen
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Captures Compose itself on Android JVM; these are not device photographs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-night")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualGlassCaptureTest {
    private val compose = createComposeRule()
    @get:Rule val host = org.junit.rules.TestRule { base, description ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                assumeTrue(BuildConfig.DEBUG)
                compose.apply(base, description).evaluate()
            }
        }
    }

    private fun capture(theme: String, scale: Float) {
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-${if (theme == "night") "night" else "notnight"}")
        val screen = mutableStateOf(CopilotScreen.HOME)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                CopilotApp(CopilotUiState(installedVersion = "test"), screen.value,
                    { screen.value = it }, CopilotActions())
            }
        }
        for (target in listOf(CopilotScreen.HOME, CopilotScreen.TRIP, CopilotScreen.SETTINGS)) {
            compose.runOnIdle { screen.value = target }
            compose.runOnIdle {
                val view = (compose as AndroidComposeTestRule<*, *>).activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height,
                    android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                val file = java.io.File("build/reports/glass-v2/${target.name.lowercase()}-$theme-${if (scale == 2f) "200" else "100"}.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue(file.length() > 1000)
            }
        }
    }

    @Test fun dayNormal() = capture("day", 1f)
    @Test fun nightNormal() = capture("night", 1f)
    @Test fun dayLarge() = capture("day", 2f)
    @Test fun nightLarge() = capture("night", 2f)
}
