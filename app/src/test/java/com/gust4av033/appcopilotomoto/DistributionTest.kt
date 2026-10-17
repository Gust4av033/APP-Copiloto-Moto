package com.gust4av033.appcopilotomoto

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DistributionTest {
    @Test fun mergedFlavorManifestMatchesPermissionMatrix() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
        assertEquals(BuildConfig.MIN_SUPPORTED_SDK, info.applicationInfo!!.minSdkVersion)
        val common = setOf("SYSTEM_ALERT_WINDOW", "RECORD_AUDIO", "BLUETOOTH_CONNECT",
            "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MICROPHONE",
            "FOREGROUND_SERVICE_LOCATION",
            "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS", "READ_CONTACTS")
            .map { "android.permission.$it" }.toSet() +
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val expected = common + if (BuildConfig.IS_PLAYSTORE_BUILD) emptySet() else
            setOf("android.permission.CALL_PHONE", "android.permission.READ_PHONE_STATE")
        assertEquals(expected, info.requestedPermissions.orEmpty().toSet())
        val services = info.services.orEmpty().map { it.name.substringAfterLast('.') }.toSet()
        assertEquals(setOf("TripMediaAccessService", "WazeNavigationObservationService", "TripVoiceService") +
            if (BuildConfig.IS_PLAYSTORE_BUILD) emptySet() else setOf("WazeScreenService"), services)
        assertEquals(BuildConfig.IS_PLAYSTORE_BUILD, !BuildConfig.ALLOW_ACCESSIBILITY_WAZE)
        assertEquals(BuildConfig.IS_PLAYSTORE_BUILD, !BuildConfig.ALLOW_DIRECT_CALL)
    }
    @Test fun dialFallbackUsesTelAndNewTask() {
        val intent = CallDispatch.intent("+15551234567", direct = false)
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel", intent.data?.scheme)
        assertEquals("+15551234567", android.net.Uri.decode(intent.data?.encodedSchemeSpecificPart))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun fullDirectCallKeepsExplicitDispatch() {
        val intent = CallDispatch.intent("+15551234567", direct = true)
        assertEquals(Intent.ACTION_CALL, intent.action)
    }

    @Test fun playstoreCannotUseAccessibilityEvenIfOptedIn() {
        if (!BuildConfig.IS_PLAYSTORE_BUILD) return
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WazeScreenAccess.setEnabled(context, true)
        assertFalse(BuildConfig.ALLOW_ACCESSIBILITY_WAZE)
        assertFalse(WazeScreenAccess.granted(context))
        assertFalse(WazeScreenAccess.allowed(context))
    }

    @Test fun playstoreNotificationSourceSurvivesAnOldFullOptOut() {
        if (!BuildConfig.IS_PLAYSTORE_BUILD) return
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("waze_observation", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", false).remove("enabled_playstore").commit()
        assertTrue(WazeObservationAccess.enabled(context))
    }

    @Test fun diagnosticsPreferenceUsesBuildDefault() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("ui_settings", android.content.Context.MODE_PRIVATE).edit()
            .remove("developer_diagnostics_unlocked").commit()
        assertEquals(BuildConfig.SHOW_DIAGNOSTICS_BY_DEFAULT,
            UserSettingsStore(context).developerDiagnosticsUnlocked())
    }
}
