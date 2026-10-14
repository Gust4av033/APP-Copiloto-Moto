package com.gust4av033.appcopilotomoto

import android.content.Context
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
    }

    @Test fun mapsNeedsAnExplicitLocalAddressForHomeAndWork() {
        NavigationPreferences.select(context, NavigationApp.GOOGLE_MAPS)
        assertEquals(NavigationApp.GOOGLE_MAPS, NavigationPreferences.selected(context))
        assertTrue(GoogleMapsNavigationProvider(context).prepare(NavigationDestination.HOME).isFailure)
        NavigationPreferences.saveAddress(context, NavigationDestination.HOME, "Avenida Norte 12, San Salvador")
        assertEquals("Avenida Norte 12, San Salvador",
            NavigationPreferences.address(context, NavigationDestination.HOME))
        assertEquals("", NavigationPreferences.address(context, NavigationDestination.WORK))
        assertThrows(IllegalArgumentException::class.java) {
            NavigationPreferences.saveAddress(context, NavigationDestination.WORK, "calle\nabre otra app")
        }
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
