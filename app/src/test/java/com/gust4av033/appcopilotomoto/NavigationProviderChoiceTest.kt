package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationProviderChoiceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun prepare() = clean()
    @After fun clean() {
        NavigationPreferences.select(context, NavigationApp.WAZE)
        NavigationPreferences.saveAddress(context, NavigationDestination.HOME, "")
        NavigationPreferences.saveAddress(context, NavigationDestination.WORK, "")
        NavigationRequestStore.forget(context, NavigationApp.GOOGLE_MAPS)
        TripNavigationState.endSession()
    }

    @Test fun mapsLocalAddressesRemainOptionalAndValidated() {
        NavigationPreferences.select(context, NavigationApp.GOOGLE_MAPS)
        assertEquals(NavigationApp.GOOGLE_MAPS, NavigationPreferences.selected(context))
        NavigationPreferences.saveAddress(context, NavigationDestination.HOME, "Avenida Norte 12, San Salvador")
        assertEquals("Avenida Norte 12, San Salvador",
            NavigationPreferences.address(context, NavigationDestination.HOME))
        assertEquals("", NavigationPreferences.address(context, NavigationDestination.WORK))
        assertThrows(IllegalArgumentException::class.java) {
            NavigationPreferences.saveAddress(context, NavigationDestination.WORK, "calle\nabre otra app")
        }
    }

    @Test fun mapsWithoutAddressOpensSearchAndDoesNotRecordAnUnverifiedFavorite() {
        val launch = googleMapsSearchIntent(NavigationDestination.HOME.label)
        val resolved = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = NavigationApp.GOOGLE_MAPS.packageName
                name = "com.google.android.maps.MapsActivity"
            }
        }
        Shadows.shadowOf(context.packageManager).addResolveInfoForIntent(launch, resolved)
        val provider = GoogleMapsNavigationProvider(context)
        assertFalse(provider.capabilities.opensSavedDestinations)
        val target = provider.prepare(NavigationDestination.HOME).getOrThrow()
        assertEquals(launch.data, target.launchIntent.data)
        assertEquals(NavigationApp.GOOGLE_MAPS.packageName, target.launchIntent.`package`)
        assertNull(target.navigationDestination)
        assertEquals("Casa", target.navigationSearchQuery)
        assertTrue(navigationDispatchMessage(target).contains("Elegí el resultado"))

        TripNavigationState.beginSession()
        NavigationRequestStore.recordDispatched(context, target)
        assertEquals(NavigationRequestKind.SEARCH, TripNavigationState.context.lastDispatch?.kind)
        assertNull(NavigationRequestStore.last(context, NavigationApp.GOOGLE_MAPS))
    }

    @Test fun configuredAddressStillPreparesDirectNavigation() {
        val address = "Avenida Norte 12, San Salvador"
        NavigationPreferences.saveAddress(context, NavigationDestination.HOME, address)
        val launch = googleMapsNavigationIntent(address)
        val resolved = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = NavigationApp.GOOGLE_MAPS.packageName
                name = "com.google.android.maps.MapsActivity"
            }
        }
        Shadows.shadowOf(context.packageManager).addResolveInfoForIntent(launch, resolved)
        val target = GoogleMapsNavigationProvider(context).prepare(NavigationDestination.HOME).getOrThrow()
        assertEquals(launch.data, target.launchIntent.data)
        assertEquals(NavigationDestination.HOME, target.navigationDestination)
        assertNull(target.navigationSearchQuery)
    }

    @Test fun mapsUrisEncodeQueriesAndStayWithinTheMapsPackageAtDispatch() {
        val address = googleMapsNavigationUri("Calle 1 & Avenida 2")
        assertEquals("google.navigation:q=Calle%201%20%26%20Avenida%202", address.toString())
        assertEquals("geo:0,0?q=Caf%C3%A9%20%26%20T%C3%A9",
            googleMapsSearchUri("Café & Té").toString())
        assertEquals(NavigationApp.GOOGLE_MAPS.packageName,
            googleMapsNavigationIntent("Casa").`package`)
        assertEquals(NavigationApp.GOOGLE_MAPS.packageName,
            googleMapsSearchIntent("gasolinera").`package`)
        assertEquals(NavigationApp.GOOGLE_MAPS,
            NavigationApp.fromPackage("com.google.android.apps.maps"))
        assertNull(NavigationApp.fromPackage("com.android.browser"))
    }
}
