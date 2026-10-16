package com.gust4av033.appcopilotomoto

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WazeNotificationObservationTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()
    private fun notification(pkg: String = "com.waze", postTime: Long = 1000): StatusBarNotification {
        val value = Notification().apply {
            category = Notification.CATEGORY_NAVIGATION
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "Gira a la derecha")
                putCharSequence(Notification.EXTRA_TEXT, "En 300 m")
                putCharSequence(Notification.EXTRA_BIG_TEXT, "x".repeat(500))
                putCharSequenceArray(Notification.EXTRA_TEXT_LINES, Array(8) { "line $it" })
                putString("unrelated", "Do not retain")
            }
        }
        return StatusBarNotification(pkg, pkg, 1, null, 1000, 0, 0, value, android.os.Process.myUserHandle(), postTime)
    }
    private fun permission() = ComponentName(app, WazeNavigationObservationService::class.java).flattenToString()
    @Before fun prepare() {
        TripNavigationState.endSession()
        WazeNotificationObservation.connection(false)
        WazeObservationAccess.setEnabled(app, false)
        UserSettingsStore(app).setDeveloperEnabled(BuildConfig.DEBUG, false)
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", "")
    }
    @After fun clean() { prepare() }

    @Test fun onlyWazeTextIsExtractedAndSizeIsBounded() {
        assertNull(WazeNotificationExtractor.extract(notification("other.app"), 2000))
        val value = checkNotNull(WazeNotificationExtractor.extract(notification(), 2000))
        assertEquals("Gira a la derecha", value.fields[Notification.EXTRA_TITLE])
        assertEquals(240, value.fields[Notification.EXTRA_BIG_TEXT]?.length)
        assertEquals(7, value.fields.size)
        assertNull(value.fields["line_4"])
        assertNull(value.fields["unrelated"])
        assertEquals(1000, value.sourcePostTimeMs)
        assertEquals(2000, value.observedAtMs)
    }

    @Test fun freshnessUsesPublicationNotTheTimeWeOpenedAPP() {
        val value = checkNotNull(WazeNotificationExtractor.extract(notification(), 200_000))
        assertFalse(value.isRecent(200_000))
        assertTrue(value.isRecent(2000))
        assertFalse(value.isRecent(500))
    }

    @Test fun permissionIsInstallationSpecificAndDoesNotReuseMediaAccess() {
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners",
            ComponentName(app, TripMediaAccessService::class.java).flattenToString())
        assertFalse(WazeObservationAccess.granted(app))
        assertTrue(TripMediaAccess.granted(app))
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", permission())
        assertTrue(WazeObservationAccess.granted(app))
        assertEquals(permission(), WazeObservationAccess.settingsIntent(app)
            .getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME))
    }

    @Test fun developerOptInPermissionAndTripAreAllRequiredIncludingReleaseBoundary() {
        WazeObservationAccess.setEnabled(app, true)
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", permission())
        TripNavigationState.beginSession()
        assertEquals(BuildConfig.IS_PLAYSTORE_BUILD, WazeObservationAccess.allowed(app))
        UserSettingsStore(app).setDeveloperEnabled(BuildConfig.DEBUG, true)
        assertEquals(BuildConfig.IS_PLAYSTORE_BUILD || BuildConfig.DEBUG, WazeObservationAccess.allowed(app))
        TripNavigationState.endSession()
        assertFalse(WazeObservationAccess.allowed(app))
    }

    @Test fun delayedSampleCannotCrossSessionsAndRemovalDisconnectAndOptOutClearIt() {
        TripNavigationState.beginSession()
        val old = TripNavigationState.context.sessionId
        val value = checkNotNull(WazeNotificationExtractor.extract(notification(), 2000))
        WazeNotificationObservation.record(old, value)
        assertNotNull(WazeNotificationObservation.snapshot.sample)
        TripNavigationState.endSession()
        assertNull(WazeNotificationObservation.snapshot.sample)
        TripNavigationState.beginSession()
        WazeNotificationObservation.record(old, value)
        assertNull(WazeNotificationObservation.snapshot.sample)
        WazeNotificationObservation.record(TripNavigationState.context.sessionId, value)
        WazeNotificationObservation.removed("other")
        assertNotNull(WazeNotificationObservation.snapshot.sample)
        WazeNotificationObservation.removed(value.key)
        assertNull(WazeNotificationObservation.snapshot.sample)
        WazeNotificationObservation.record(TripNavigationState.context.sessionId, value)
        WazeNotificationObservation.connection(false)
        assertNull(WazeNotificationObservation.snapshot.sample)
        WazeNotificationObservation.record(TripNavigationState.context.sessionId, value)
        WazeObservationAccess.setEnabled(app, false)
        assertNull(WazeNotificationObservation.snapshot.sample)
    }

    @Test fun oldNotificationEtaIsLabeledLastSeenAndExpiresOrClearsOnRemoval() {
        TripNavigationState.beginSession()
        UserSettingsStore(app).setDeveloperEnabled(BuildConfig.DEBUG, true)
        WazeObservationAccess.setEnabled(app, true)
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", permission())
        val sample = WazeNotificationSample("maps-eta", Notification.CATEGORY_NAVIGATION, 1_700_000_001_000,
            1_700_000_001_000, mapOf(Notification.EXTRA_TEXT to "14 min para llegar"),
            WazeNotificationExtractor.MAPS_PACKAGE, 1000)
        WazeNotificationObservation.record(TripNavigationState.context.sessionId, sample)
        val observed = TripContextCollector.snapshot(app, 1_700_000_020_000, 20_000)
        assertNull(observed.navigation.remainingMinutes)
        assertEquals(14, observed.lastObservedEta?.remainingMinutes)
        assertTrue(BriefTripContextTtsFormatter.timeRemaining(observed, 20_000).contains("no tengo una ETA actual"))
        assertNull(TripContextCollector.snapshot(app, 1_700_000_121_000, 121_000).lastObservedEta)
        WazeNotificationObservation.removed(sample.key)
        assertNull(TripContextCollector.snapshot(app, 1_700_000_021_000, 21_000).lastObservedEta)
        WazeNotificationObservation.record(TripNavigationState.context.sessionId,
            sample.copy(key = "old-maps-eta", sourcePostTimeMs = 1_699_999_000_000,
                observedAtElapsedMs = 20_000))
        assertNull(TripContextCollector.snapshot(app, 1_700_000_021_000, 21_000).lastObservedEta)
    }

    @Test fun notificationCallbacksAreGatedAndUnrelatedAppsCannotReplaceSample() {
        val service = Robolectric.buildService(WazeNavigationObservationService::class.java).create().get()
        TripNavigationState.beginSession()
        WazeNotificationObservation.connection(true)
        service.onNotificationPosted(notification())
        assertNull(WazeNotificationObservation.snapshot.sample)
        UserSettingsStore(app).setDeveloperEnabled(BuildConfig.DEBUG, true)
        WazeObservationAccess.setEnabled(app, true)
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", permission())
        service.onNotificationPosted(notification())
        if (BuildConfig.IS_PLAYSTORE_BUILD || BuildConfig.DEBUG) {
            val sample = WazeNotificationObservation.snapshot.sample
            assertNotNull(sample)
            service.onNotificationPosted(notification("other.app"))
            assertEquals(sample, WazeNotificationObservation.snapshot.sample)
            Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", "")
            service.onNotificationPosted(notification())
            assertNull(WazeNotificationObservation.snapshot.sample)
        } else assertNull(WazeNotificationObservation.snapshot.sample)
        service.onDestroy()
    }
}
