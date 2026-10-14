package com.gust4av033.appcopilotomoto

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.gust4av033.appcopilotomoto.presentation.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], qualifiers="w360dp-h800dp-night")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeDashboardUiTest {
    private val compose=createComposeRule()
    @get:Rule val debugHost=org.junit.rules.TestRule { base,description ->
        object:org.junit.runners.model.Statement() {
            override fun evaluate() { assumeTrue(BuildConfig.DEBUG);compose.apply(base,description).evaluate() }
        }
    }
    private val devices=AudioDevicesUi(supported=true,permissionGranted=true,
        selectedId=12,selectedLabel="Dime Evo",headsets=listOf(AudioDeviceUi(12,"Dime Evo")))
    private fun render(name:String) {
        compose.runOnIdle {
            val view=(compose as AndroidComposeTestRule<*,*>).activity.window.decorView
            val image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val file=java.io.File("build/reports/storm-ui/$name.png");file.parentFile?.mkdirs()
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    private fun app(state:CopilotUiState=CopilotUiState(audioDevices=devices),actions:CopilotActions=CopilotActions(),scale:Float=1f,navigate:(CopilotScreen)->Unit={}) {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,scale)) {
            CopilotApp(state,CopilotScreen.HOME,navigate,actions)
        } }
    }
    @Test fun horizontalSwipesShowActualEquipmentAndRouteWithoutInvokingAudio() {
        var captures=0
        app(actions=CopilotActions(onToggleTrip={captures++},onManualTripCommand={captures++},onToggleListening={captures++},onToggleWake={captures++}))
        render("home-storm-night")
        compose.onNodeWithTag("home_dashboard_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("home_tab_1").assertIsSelected()
        compose.onNodeWithText("Dime Evo",substring=true).assertExists()
        render("equipment-storm-night")
        compose.onNodeWithTag("home_dashboard_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("home_tab_2").assertIsSelected()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.trip_navigation_empty)).assertExists()
        assertEquals(0,captures)
    }
    @Test fun onlyExplicitStartButtonStartsTripAndNavigates() {
        var starts=0;var screen:CopilotScreen?=null
        app(actions=CopilotActions(onToggleTrip={starts++}),navigate={screen=it})
        compose.onNodeWithTag("home_tab_1").performClick()
        assertEquals(0,starts)
        compose.onNodeWithTag("home_tab_0").performClick()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.trip_start)).performClick()
        assertEquals(1,starts);assertEquals(CopilotScreen.TRIP,screen)
    }
    @Test fun swipingAcrossStartButtonNeverStartsTrip() {
        var starts=0
        app(actions=CopilotActions(onToggleTrip={starts++}))
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.trip_start))
            .performTouchInput { swipeLeft() }
        compose.onNodeWithTag("home_tab_1").assertIsSelected()
        assertEquals(0,starts)
    }
    @Test fun activeTripSurvivesChangingCardsWithoutAnyRuntimeCallback() {
        var actions=0
        app(state=CopilotUiState(tripActive=true,commandReady=true,inputId="BUTTON",audioDevices=devices),
            actions=CopilotActions(onToggleTrip={actions++},onManualTripCommand={actions++},onInputChange={actions++}))
        compose.onNodeWithTag("home_dashboard_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("home_tab_1").assertIsSelected()
        assertEquals(0,actions)
    }
    @Test fun tabsRemainTouchableWithTwoHundredPercentTextOnNarrowScreen() {
        RuntimeEnvironment.setQualifiers("w320dp-h800dp-night")
        app(scale=2f)
        val density=RuntimeEnvironment.getApplication().resources.displayMetrics.density
        for (i in 0..2) {
            val node=compose.onNodeWithTag("home_tab_$i")
            assertTrue(node.fetchSemanticsNode().boundsInRoot.height/density>=48f)
            node.performClick();node.assertIsSelected()
        }
        render("route-storm-large-font")
        for (label in listOf("Copiloto", "Mi equipo", "Mi ruta")) {
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1,layouts.single().lineCount)
            val layout=layouts.single()
            // Intrinsic width may round upward by a subpixel; assert the actual painted line bounds.
            assertTrue("$label must fit its label bounds",layout.getLineRight(0)<=layout.size.width+1f)
            assertTrue(layout.getLineLeft(0)>=-1f)
            assertFalse(layout.didOverflowHeight)
        }
        render("route-storm-large-font")
    }
    @Test fun largeTextCanScrollToStartTripWithoutAccidentallyStartingIt() {
        RuntimeEnvironment.setQualifiers("w320dp-h800dp-night")
        var starts=0
        var screen:CopilotScreen?=null
        app(scale=2f,actions=CopilotActions(onToggleTrip={starts++}),navigate={screen=it})
        val start=compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.trip_start))
        // The button lives inside a horizontal pager. Use the user's vertical
        // gesture on the outer list rather than its nearest scroll semantics.
        repeat(12) {
            if (!start.isDisplayed()) compose.onNodeWithTag("settings_content").performTouchInput {
                swipeUp(startY=height*.75f,endY=height*.5f,durationMillis=600)
            }
        }
        render("home-large-font-after-scroll")
        start.assertIsDisplayed()
        assertEquals(0,starts)
        render("home-large-font-start-visible")
        start.performClick()
        assertEquals(1,starts)
        assertEquals(CopilotScreen.TRIP,screen)
    }
    @Test fun dayThemeUsesTheSameThreeRealPanels() {
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-notnight")
        app()
        compose.onNodeWithTag("home_tab_0").assertIsSelected()
        render("home-storm-day")
        compose.onNodeWithText("Mi moto").assertDoesNotExist()
        compose.onNodeWithText("Historial").assertDoesNotExist()
    }
}
