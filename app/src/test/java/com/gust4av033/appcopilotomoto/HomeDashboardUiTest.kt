package com.gust4av033.appcopilotomoto

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.gust4av033.appcopilotomoto.presentation.CopilotApp
import com.gust4av033.appcopilotomoto.presentation.CopilotScreen
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], qualifiers="w360dp-h800dp-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeDashboardUiTest {
    @get:Rule val compose = createComposeRule()

    private fun app(
        state: CopilotUiState = CopilotUiState(),
        actions: CopilotActions = CopilotActions(),
        scale: Float = 1f,
        navigate: (CopilotScreen) -> Unit = {}
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                CopilotApp(state, CopilotScreen.HOME, navigate, actions)
            }
        }
    }

    @Test fun gpsOffShowsHonestEmptyStateAndNoRuntimeCallbacks() {
        var callbacks = 0
        app(actions = CopilotActions(
            onToggleTrip = { callbacks++ },
            onTripLocationChange = { callbacks++ },
            onManualTripCommand = { callbacks++ }
        ))
        compose.onNodeWithTag("home_map_status").assertExists()
        compose.onNodeWithText(
            RuntimeEnvironment.getApplication().getString(R.string.home_map_empty_gps_off),
            substring = true
        ).assertExists()
        assert(callbacks == 0)
    }

    @Test fun explicitDockStartStartsTripAndNavigates() {
        var starts = 0
        var screen: CopilotScreen? = null
        app(actions = CopilotActions(onToggleTrip = { starts++ }), navigate = { screen = it })
        compose.onNodeWithTag("home_map_dock_trip").performClick()
        assert(starts == 1)
        assert(screen == CopilotScreen.TRIP)
    }

    @Test fun activeDockStopsTripWithoutNavigating() {
        var stops = 0
        var screen: CopilotScreen? = null
        app(
            state = CopilotUiState(tripActive = true, tripLocationEnabled = true),
            actions = CopilotActions(onToggleTrip = { stops++ }),
            navigate = { screen = it }
        )
        compose.onNodeWithTag("home_map_dock_trip").performClick()
        assert(stops == 1)
        assert(screen == null)
    }

    @Test fun motorcycleAndSettingsDockNavigateWithoutTripCallbacks() {
        var callbacks = 0
        var screens = mutableListOf<CopilotScreen>()
        app(actions = CopilotActions(onToggleTrip = { callbacks++ }), navigate = { screens += it })
        compose.onNodeWithTag("home_map_dock_motorcycle").performClick()
        compose.onNodeWithTag("home_map_dock_settings").performClick()
        assert(callbacks == 0)
        assert(screens == listOf(CopilotScreen.MOTORCYCLE, CopilotScreen.SETTINGS))
    }

    @Test fun statusSheetOpensWithoutStartingGpsMicrophoneOrTrip() {
        var callbacks = 0
        app(actions = CopilotActions(
            onToggleTrip = { callbacks++ },
            onToggleListening = { callbacks++ },
            onTripLocationChange = { callbacks++ }
        ))
        compose.onNodeWithText(
            RuntimeEnvironment.getApplication().getString(R.string.home_map_sheet_open)
        ).assertDoesNotExist()
        compose.onNodeWithContentDescription(
            RuntimeEnvironment.getApplication().getString(R.string.home_map_sheet_open)
        ).performClick()
        compose.onNodeWithText(
            RuntimeEnvironment.getApplication().getString(R.string.home_map_sheet_title)
        ).assertExists()
        assert(callbacks == 0)
    }

    @Test fun dockRemainsTouchableAtTwoHundredPercentText() {
        RuntimeEnvironment.setQualifiers("w320dp-h800dp-night")
        app(scale = 2f)
        listOf("motorcycle", "trip", "settings").forEach { dock ->
            val node = compose.onNodeWithTag("home_map_dock_$dock")
            node.assertExists()
            node.assertIsEnabled()
        }
    }
}
