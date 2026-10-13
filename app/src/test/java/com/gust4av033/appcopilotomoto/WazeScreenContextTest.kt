package com.gust4av033.appcopilotomoto

import android.content.ComponentName
import android.provider.Settings
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WazeScreenContextTest {
    @After fun clean() {
        val app = RuntimeEnvironment.getApplication()
        TripNavigationState.endSession()
        WazeScreenAccess.setEnabled(app,false)
        UserSettingsStore(app).setDeveloperEnabled(BuildConfig.DEBUG,false)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
    }
    @Test fun explicitSingleManeuverUsesOnlyNearbyUnambiguousDistance() {
        val left=ScreenText("Gira a la izquierda",100,130)
        assertEquals(WazeTurn(left.text,"300 m"), WazeTurnParser.parse(listOf(left,ScreenText("300 m",150,170),ScreenText("20 km",800,830))))
        assertEquals("1,2 km",WazeTurnParser.parse(listOf(left,ScreenText("1,2 km",150,170)))?.distance)
        assertNull(WazeTurnParser.parse(listOf(ScreenText("Waze",10,30),ScreenText("En ejecución. Toca para abrir.",40,80))))
        assertNull(WazeTurnParser.parse(listOf(left,ScreenText("Gira a la derecha",200,230))))
        assertNull(WazeTurnParser.parse(listOf(ScreenText("Avenida Izquierda",100,130))))
        assertNull(WazeTurnParser.parse(listOf(left,ScreenText("300 m",150,170),ScreenText("500 m",200,220)))?.distance)
    }
    @Test fun voiceQueriesDoNotBecomeNavigationOrMultipleActions() {
        for(text in listOf("¿Cuál es el próximo giro?", "Qué indica Waze", "next turn"))
            assertEquals(TestIntent.NEXT_TURN, MinimalIntentRouter.resolve(text))
        assertEquals(TestIntent.UNKNOWN,MinimalIntentRouter.resolve("próximo giro y llama a Ana"))
        assertEquals(TestIntent.NAVIGATE,MinimalIntentRouter.resolve("vamos a casa"))
    }
    @Test fun oldHiddenOrUnknownScreenCannotProduceADirection() {
        val sample=WazeScreenSnapshot("s",true,true,1000,turn=WazeTurn("Gira a la izquierda","300 m"))
        assertTrue(sample.answer(2000).contains("Según la pantalla de Waze"))
        for(value in listOf(sample.copy(visible=false),sample.copy(turn=null)))
            assertTrue(value.answer(2000).startsWith("No tengo"))
        assertTrue(sample.answer(16001).startsWith("No tengo"))
        assertTrue(sample.answer(500).startsWith("No tengo"))
    }
    @Test fun stoppedSessionAndDelayedReadsDoNotLeakToNewTrip() {
        TripNavigationState.beginSession()
        val old=TripNavigationState.context.sessionId
        WazeScreenState.update(old,listOf(ScreenText("Gira a la izquierda",10,30)),1000)
        assertNotNull(WazeScreenState.snapshot.turn)
        TripNavigationState.endSession()
        assertTrue(WazeScreenState.snapshot.texts.isEmpty())
        TripNavigationState.beginSession()
        WazeScreenState.update(old,listOf(ScreenText("Gira a la derecha",10,30)),2000)
        assertNull(WazeScreenState.snapshot.turn)
    }
    @Test fun accessNeedsSeparateConsentAndReleaseCannotRead() {
        val app=RuntimeEnvironment.getApplication()
        clean()
        TripNavigationState.beginSession()
        WazeScreenAccess.setEnabled(app,true)
        UserSettingsStore(app).setDeveloperEnabled(BuildConfig.DEBUG,true)
        assertFalse(WazeScreenAccess.allowed(app))
        Settings.Secure.putString(app.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(app,WazeScreenService::class.java).flattenToString())
        assertEquals(BuildConfig.DEBUG,WazeScreenAccess.allowed(app))
        val keyguard=org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.KeyguardManager::class.java))
        keyguard.setKeyguardLocked(true)
        assertFalse(WazeScreenAccess.allowed(app))
        keyguard.setKeyguardLocked(false)
        UserSettingsStore(app).setDeveloperEnabled(BuildConfig.DEBUG,false)
        assertFalse(WazeScreenAccess.allowed(app))
        assertTrue(WazeNavigationProvider(app).nextTurnAnswer().contains("no está habilitada"))
    }
}
