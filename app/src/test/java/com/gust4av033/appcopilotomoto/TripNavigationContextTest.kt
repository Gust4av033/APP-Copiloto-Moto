package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.Intent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripNavigationContextTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()
    private fun favorite(destination: NavigationDestination) = AppTarget("Favorite",
        Intent(Intent.ACTION_VIEW).setPackage("com.waze"), navigationDestination = destination)

    @Before fun prepare() {
        TripNavigationState.endSession()
        NavigationRequestStore.forget(app)
        NavigationRequestStore.forget(app, NavigationApp.GOOGLE_MAPS)
    }
    @After fun clean() {
        TripNavigationState.endSession()
        NavigationRequestStore.forget(app)
        NavigationRequestStore.forget(app, NavigationApp.GOOGLE_MAPS)
    }

    @Test fun dispatchIsSessionEvidenceAndEndingDoesNotEraseRepeatFavorite() {
        TripNavigationState.beginSession()
        val session = TripNavigationState.context.sessionId
        NavigationRequestStore.recordDispatched(app, favorite(NavigationDestination.HOME), session)
        val dispatch = checkNotNull(TripNavigationState.context.lastDispatch)
        assertEquals("waze", dispatch.providerId)
        assertEquals(NavigationRequestKind.FAVORITE, dispatch.kind)
        assertEquals(NavigationDestination.HOME.label, dispatch.requestedDestination)
        assertTrue(dispatch.dispatchedAtEpochMs > 0)
        TripNavigationState.endSession()
        assertNull(TripNavigationState.context.lastDispatch)
        TripNavigationState.beginSession()
        assertNotEquals(session, TripNavigationState.context.sessionId)
        assertNull(TripNavigationState.context.lastDispatch)
        assertEquals(NavigationDestination.HOME, NavigationRequestStore.last(app))
    }

    @Test fun ownDestinationSurvivesMinimizationButNotTheEndOfTheTrip() {
        val home = TargetDestination("Casa", 13.7, -89.2)
        TripNavigationState.beginSession(home)
        val session = TripNavigationState.context.sessionId
        assertEquals(home, TripNavigationState.context.ownDestination)
        assertTrue(TripNavigationState.context.ownDestinationSetAtElapsedMs >= 0)
        TripNavigationState.chooseOwnDestination("another-session", TargetDestination("Trabajo", 13.8, -89.3))
        assertEquals(home, TripNavigationState.context.ownDestination)
        TripNavigationState.chooseOwnDestination(session, TargetDestination("Trabajo", 13.8, -89.3))
        assertEquals("Trabajo", TripNavigationState.context.ownDestination?.label)
        TripNavigationState.endSession()
        assertNull(TripNavigationState.context.ownDestination)
    }

    @Test fun externalDispatchNeverInventsAnAppDestinationAndClearsAConflictingChoice() {
        TripNavigationState.beginSession(TargetDestination("Casa", 13.7, -89.2))
        val work = TargetDestination("Trabajo", 13.8, -89.3)
        val session = TripNavigationState.context.sessionId
        TripNavigationState.record(session, NavigationDispatch("waze", NavigationRequestKind.FAVORITE,
            "Trabajo", 1_000, work, 1_000))
        assertNull(TripNavigationState.context.ownDestination)
        TripNavigationState.record(session, NavigationDispatch("google_maps", NavigationRequestKind.SEARCH,
            "gasolinera", 2_000, null, 2_000))
        assertNull(TripNavigationState.context.ownDestination)
        TripNavigationState.chooseOwnDestination(session, work)
        TripNavigationState.record(session, NavigationDispatch("waze", NavigationRequestKind.FAVORITE,
            "Trabajo", 2_500, work, 2_500))
        assertEquals(work, TripNavigationState.context.ownDestination)
        TripNavigationState.record(session, NavigationDispatch("waze", NavigationRequestKind.FAVORITE,
            "Casa", 3_000, null, 3_000))
        assertNull(TripNavigationState.context.ownDestination)
    }

    @Test fun oldSessionCannotChangeNewTripOrPersistentFavorite() {
        TripNavigationState.beginSession()
        val old = TripNavigationState.context.sessionId
        NavigationRequestStore.recordDispatched(app, favorite(NavigationDestination.HOME), old)
        TripNavigationState.endSession()
        TripNavigationState.beginSession()
        NavigationRequestStore.recordDispatched(app, favorite(NavigationDestination.WORK), old)
        assertNull(TripNavigationState.context.lastDispatch)
        assertEquals(NavigationDestination.HOME, NavigationRequestStore.last(app))
    }

    @Test fun searchRecordsQueryWithoutReplacingFavoriteAndOtherAppsDoNotChangeIt() {
        TripNavigationState.beginSession()
        NavigationRequestStore.recordDispatched(app, favorite(NavigationDestination.HOME))
        val search = AppTarget("Search", Intent(Intent.ACTION_VIEW).setPackage("com.waze"),
            navigationSearchQuery = "gasolinera")
        NavigationRequestStore.recordDispatched(app, search)
        val dispatch = TripNavigationState.context.lastDispatch
        assertEquals(NavigationRequestKind.SEARCH, dispatch?.kind)
        assertEquals("gasolinera", dispatch?.requestedDestination)
        assertEquals(NavigationDestination.HOME, NavigationRequestStore.last(app))
        NavigationRequestStore.recordDispatched(app, AppTarget("Other", Intent().setPackage("other")))
        NavigationRequestStore.recordDispatched(app, AppTarget("Waze", Intent().setPackage("com.waze")))
        assertEquals(dispatch, TripNavigationState.context.lastDispatch)
    }

    @Test fun preparationAndFailedOpeningDoNotRecordDispatch() {
        TripNavigationState.beginSession()
        val executor = ActionExecutor(app, MediaActionAdapter(app))
        executor.prepareNavigation(MinimalIntentRouter.route("vamos a casa"))
        assertNull(TripNavigationState.context.lastDispatch)
        assertTrue(executor.openAppFromActivity(favorite(NavigationDestination.HOME)).isFailure)
        assertNull(TripNavigationState.context.lastDispatch)
        assertNull(NavigationRequestStore.last(app))
    }

    @Test fun standaloneFavoriteDoesNotBecomeLiveTripContext() {
        NavigationRequestStore.recordDispatched(app, favorite(NavigationDestination.WORK))
        assertNull(TripNavigationState.context.sessionId)
        assertNull(TripNavigationState.context.lastDispatch)
        assertEquals(NavigationDestination.WORK, NavigationRequestStore.last(app))
    }

    @Test fun mapsDispatchKeepsItsOwnLastDestinationAndNeverInventsARoute() {
        TripNavigationState.beginSession()
        val maps = AppTarget("Casa en Maps", Intent(Intent.ACTION_VIEW)
            .setPackage(NavigationApp.GOOGLE_MAPS.packageName),
            navigationDestination = NavigationDestination.HOME)
        NavigationRequestStore.recordDispatched(app, maps, TripNavigationState.context.sessionId)
        assertEquals(NavigationApp.GOOGLE_MAPS.id, TripNavigationState.context.lastDispatch?.providerId)
        assertEquals(NavigationDestination.HOME, NavigationRequestStore.last(app, NavigationApp.GOOGLE_MAPS))
        assertNull(NavigationRequestStore.last(app))
        TripNavigationState.endSession()
        assertNull(TripNavigationState.context.lastDispatch)
        assertEquals(NavigationDestination.HOME, NavigationRequestStore.last(app, NavigationApp.GOOGLE_MAPS))
        NavigationRequestStore.forget(app, NavigationApp.GOOGLE_MAPS)
    }

    @Test fun spokenStatusReportsOnlyCurrentTripsLastDispatchAndItsActualProvider() {
        val actions = ActionExecutor(app, MediaActionAdapter(app))
        assertTrue(actions.navigationRequestAnswer().contains("No hay un viaje activo"))
        TripNavigationState.beginSession()
        assertTrue(actions.navigationRequestAnswer().contains("Todavía no enviaste"))

        NavigationRequestStore.recordDispatched(app, favorite(NavigationDestination.HOME))
        assertTrue(actions.navigationRequestAnswer().contains("ir a Casa en Waze"))
        assertTrue(actions.navigationRequestAnswer().contains("No puedo confirmar"))

        NavigationRequestStore.recordDispatched(app, AppTarget("Search",
            Intent(Intent.ACTION_VIEW).setPackage(NavigationApp.GOOGLE_MAPS.packageName),
            navigationSearchQuery = "gasolinera"))
        val answer = actions.navigationRequestAnswer()
        assertTrue(answer.contains("gasolinera en Google Maps"))
        assertTrue(answer.contains("No sé qué lugar elegiste"))
        TripNavigationState.endSession()
        TripNavigationState.beginSession()
        assertTrue(actions.navigationRequestAnswer().contains("Todavía no enviaste"))
    }
}
