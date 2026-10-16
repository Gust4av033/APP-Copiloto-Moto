package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.SystemClock
import android.os.Build

/** Keep navigation fields from one observed app, preferring the app on screen. */
internal fun selectNavigationNotification(samples: Collection<WazeNotificationSample>, nowElapsed: Long,
    visiblePackage: String?, maxAgeMs: Long = 60_000): WazeNotificationSample? = samples.asSequence()
    .filter { it.observedAtElapsedMs > 0 && nowElapsed >= it.observedAtElapsedMs &&
        nowElapsed - it.observedAtElapsedMs <= maxAgeMs }
    .filter { visiblePackage == null || it.packageName == visiblePackage }
    .maxWithOrNull(compareBy<WazeNotificationSample> { it.sourcePostTimeMs }
        .thenBy { it.observedAtElapsedMs })

/** A generic persistent notification does not identify the navigator supplying route data. */
internal fun NavigationContext.hasRecentObservedRouteData(nowElapsed: Long): Boolean = listOfNotNull(
    nextTurnInstruction, remainingMinutes, remainingDistanceText, currentOrTargetStreet,
    routeAlertText).any { it.freshness(nowElapsed) != Freshness.EXPIRED }

/** Reads only the sources already granted and observed in this trip. */
internal object TripContextCollector {
    private const val CURRENT_NOTICE_MAX_AGE_MS = 15_000L
    @Volatile var latestLocation: LocationSample? = null
        private set
    fun recordLocation(sample: LocationSample) {
        if (!sample.hasTripPositionQuality()) return
        if (latestLocation?.elapsedRealtimeMs?.let { sample.elapsedRealtimeMs <= it } == true) return
        latestLocation = sample
    }
    fun clear() { latestLocation = null }

    /** Navigation source shared by voice and UI. */
    fun navigationSnapshot(context: Context, nowEpoch: Long = System.currentTimeMillis(),
        nowElapsed: Long = SystemClock.elapsedRealtime()): TripContextSnapshot {
        val screen = WazeScreenState.snapshot
        val screenVisible = screen.visible && screen.recent(nowElapsed) &&
            screen.sessionId == TripNavigationState.context.sessionId && screen.sessionId != null &&
            WazeScreenAccess.allowed(context)
        val notificationSamples = WazeNotificationObservation.snapshot.takeIf {
            it.sessionId != null && it.sessionId == TripNavigationState.context.sessionId &&
                WazeObservationAccess.allowed(context)
        }?.samples?.values.orEmpty()
        val visiblePackage = screen.packageName.takeIf { screenVisible }
        val parsedNotifications = notificationSamples.map { sample ->
            sample to NavigationObservationParser.parse(sample.fields.values.toList(),
                if (sample.packageName == WazeNotificationExtractor.MAPS_PACKAGE) ContextSource.GOOGLE_MAPS_NOTIFICATION
                else ContextSource.WAZE_NOTIFICATION, sample.observedAtMs, sample.observedAtElapsedMs)
        }
        val currentNotifications = parsedNotifications.filter { it.first.isRecent(nowEpoch) }
        val selectedNotification = selectNavigationNotification(currentNotifications.filter {
            it.second.hasRecentObservedRouteData(nowElapsed) }.map { it.first }, nowElapsed, visiblePackage)
        val activePackage = visiblePackage ?: selectedNotification?.packageName
        // Several active notices from one navigator can carry complementary fields. Keep
        // their provenance and TTL, and never mix them with the other navigator.
        val notification = currentNotifications.asSequence().filter { (sample, _) ->
            sample.packageName == activePackage && sample.observedAtElapsedMs > 0 &&
                nowElapsed - sample.observedAtElapsedMs in 0L until CURRENT_NOTICE_MAX_AGE_MS
        }.map { it.second }.fold(NavigationContext()) { merged, part ->
            fuseNavigation(merged, part, nowElapsed)
        }
        val lastNotificationSample = selectNavigationNotification(parsedNotifications.filter {
            it.second.remainingMinutes != null && it.first.sourcePostTimeMs > 0 &&
                nowEpoch >= it.first.sourcePostTimeMs &&
                nowEpoch - it.first.sourcePostTimeMs < LastObservedEta.MAX_AGE_MS
        }.map { it.first }, nowElapsed, activePackage, LastObservedEta.MAX_AGE_MS)
        val lastNotificationEta = lastNotificationSample?.let { sample ->
            parsedNotifications.first { it.first === sample }.second.remainingMinutes?.value
                ?.let { LastObservedEta(
                    if (sample.packageName == WazeNotificationExtractor.MAPS_PACKAGE) EtaSource.GOOGLE_MAPS else EtaSource.WAZE,
                    it, sample.observedAtElapsedMs) }
        }
        val accessible = if (screenVisible)
            NavigationObservationParser.parse(screen.texts.map(ScreenText::text),
                if (screen.packageName == WazeNotificationExtractor.MAPS_PACKAGE) ContextSource.GOOGLE_MAPS_ACCESSIBILITY
                else ContextSource.WAZE_ACCESSIBILITY,
                nowEpoch - (nowElapsed - screen.observedAtMs), screen.observedAtMs) else NavigationContext()
        val lastScreenEta = screen.takeIf { it.sessionId != null &&
            it.sessionId == TripNavigationState.context.sessionId && WazeScreenAccess.allowed(context) &&
            (activePackage == null || it.packageName == activePackage) &&
            it.observedAtMs > 0 && nowElapsed >= it.observedAtMs &&
            nowElapsed - it.observedAtMs < LastObservedEta.MAX_AGE_MS }?.let { observed ->
            NavigationObservationParser.parse(observed.texts.map(ScreenText::text),
                if (observed.packageName == WazeNotificationExtractor.MAPS_PACKAGE) ContextSource.GOOGLE_MAPS_ACCESSIBILITY
                else ContextSource.WAZE_ACCESSIBILITY,
                nowEpoch - (nowElapsed - observed.observedAtMs), observed.observedAtMs)
                .remainingMinutes?.value?.let { LastObservedEta(
                    if (observed.packageName == WazeNotificationExtractor.MAPS_PACKAGE) EtaSource.GOOGLE_MAPS else EtaSource.WAZE,
                    it, observed.observedAtMs) }
        }
        val lastObservedEta = listOfNotNull(lastNotificationEta, lastScreenEta)
            .maxByOrNull { it.observedAtElapsedMs }
        val source = fuseNavigation(notification, accessible, nowElapsed)
        return TripContextSnapshot(navigation = source, lastObservedEta = lastObservedEta)
    }

    /** Own trip progress plus navigation, without polling media or battery for the route card. */
    fun routeCardSnapshot(context: Context, nowEpoch: Long = System.currentTimeMillis(),
        nowElapsed: Long = SystemClock.elapsedRealtime()): TripContextSnapshot {
        val observedRoute = navigationSnapshot(context, nowEpoch, nowElapsed)
        val gps = latestLocation?.takeIf { TripLocationAccess.available(context) && it.usableForTripContext(nowElapsed) }
            ?.let { fix -> ContextValue(fix, ContextSource.ANDROID_GPS, fix.epochMs, fix.elapsedRealtimeMs,
                when (fix.horizontalAccuracyMeters ?: Float.POSITIVE_INFINITY) {
                    in 0f..10f -> .95f
                    in 10f..25f -> .85f
                    else -> .6f
                }, 10_000) }
        val target = TripNavigationState.context.lastDispatch?.takeIf {
            it.dispatchedAtElapsedRealtimeMs > 0
        }?.let { dispatch -> dispatch.target?.let {
            ContextValue(it, ContextSource.SAVED_DESTINATION_COORDS, dispatch.dispatchedAtEpochMs,
                dispatch.dispatchedAtElapsedRealtimeMs, 1f, 86_400_000)
        }
        }
        val session = TripNavigationState.context.takeIf { it.sessionId != null &&
            nowElapsed >= it.startedAtElapsedRealtimeMs }?.let {
            ContextValue(nowElapsed - it.startedAtElapsedRealtimeMs, ContextSource.APP_SESSION,
                nowEpoch, nowElapsed, 1f, 5_000)
        }
        return observedRoute.copy(location = gps, target = target, sessionDurationMs = session)
    }

    fun snapshot(context: Context, nowEpoch: Long = System.currentTimeMillis(),
        nowElapsed: Long = SystemClock.elapsedRealtime()): TripContextSnapshot {
        val route = routeCardSnapshot(context, nowEpoch, nowElapsed)
        fun <T> media(value: T?) = value?.let { ContextValue(it, ContextSource.MEDIA_SESSION,
            nowEpoch, nowElapsed, .9f, 5_000) }
        val mediaContext = runCatching {
            val controller = TripMediaAccess.controller(context)
            MediaTripContext(media(controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)),
                media(controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)),
                media(controller.packageName), media(when (controller.playbackState?.state) {
                    PlaybackState.STATE_PLAYING -> "PLAYING"
                    PlaybackState.STATE_PAUSED -> "PAUSED"
                    else -> null
                }))
        }.getOrNull()
        fun <T> device(value: T?) = value?.let { ContextValue(it, ContextSource.DEVICE_STATUS,
            nowEpoch, nowElapsed, 1f, 5_000) }
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val audio = context.getSystemService(AudioManager::class.java)
        val bluetoothTypes = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
        val bluetoothAudio = audio.isBluetoothA2dpOn || audio.isBluetoothScoOn ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audio.communicationDevice?.type in bluetoothTypes)
        return route.copy(media = mediaContext,
            device = DeviceTripContext(device(if (level >= 0 && scale > 0) level * 100 / scale else null),
                device(status in setOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)),
                device(bluetoothAudio)))
    }
}
