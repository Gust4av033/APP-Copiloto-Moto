package com.gust4av033.appcopilotomoto

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DistributionTest {
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
