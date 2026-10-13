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

    @Before fun prepare() { TripNavigationState.endSession(); NavigationRequestStore.forget(app) }
    @After fun clean() { TripNavigationState.endSession(); NavigationRequestStore.forget(app) }

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
}
