package com.gust4av033.appcopilotomoto

import android.content.ComponentName
import android.content.ContextWrapper
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripMediaAccessCompatibilityTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Test fun observingPreviousKeyDoesNotGrantMultimediaAccess() {
        Settings.Secure.putString(context.contentResolver,"enabled_notification_listeners","")
        HeadsetControlStore.observeKey(context,HeadsetIdentity(1,7,"AA:BB","EVO"),android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        assertFalse(TripMediaAccess.granted(context))
    }
    @Test fun settingPointsToThisInstallationIncludingFieldTestSuffix() {
        val testContext=object:ContextWrapper(context) { override fun getPackageName()="com.gust4av033.appcopilotomoto.pruebas" }
        val intent=TripMediaAccess.settingsIntent(testContext)
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS,intent.action)
        assertTrue(intent.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME)!!.startsWith(testContext.packageName+"/"))
    }
    @Test fun accessOfOtherInstallationDoesNotGrantThisOne() {
        val component=ComponentName("com.gust4av033.appcopilotomoto.pruebas",TripMediaAccessService::class.java.name)
        Settings.Secure.putString(context.contentResolver,"enabled_notification_listeners",component.flattenToString())
        assertFalse(TripMediaAccess.granted(context))
    }
}
