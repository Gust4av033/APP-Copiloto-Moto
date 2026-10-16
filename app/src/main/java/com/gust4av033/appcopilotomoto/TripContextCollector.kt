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

/** Reads only the sources already granted and observed in this trip. */
internal object TripContextCollector {
    @Volatile var latestLocation: LocationSample? = null
        private set
    fun recordLocation(sample: LocationSample) { latestLocation = sample }
    fun clear() { latestLocation = null }

    fun snapshot(context: Context, nowEpoch: Long = System.currentTimeMillis(),
        nowElapsed: Long = SystemClock.elapsedRealtime()): TripContextSnapshot {
        val session = TripNavigationState.context.sessionId
        val observedNotifications = WazeNotificationObservation.snapshot.takeIf {
            session != null && it.sessionId == session && WazeObservationAccess.allowed(context)
        }?.samples?.values.orEmpty().filter {
            it.sourcePostTimeMs > 0 && nowEpoch >= it.sourcePostTimeMs &&
                nowEpoch - it.sourcePostTimeMs < LastObservedEta.MAX_AGE_MS &&
                it.observedAtElapsedMs > 0 && nowElapsed >= it.observedAtElapsedMs &&
                nowElapsed - it.observedAtElapsedMs < LastObservedEta.MAX_AGE_MS
        }.map { sample ->
            val source = if (sample.packageName == WazeNotificationExtractor.MAPS_PACKAGE)
                ContextSource.GOOGLE_MAPS_NOTIFICATION else ContextSource.WAZE_NOTIFICATION
            sample to NavigationObservationParser.parse(sample.fields.values.toList(), source,
                sample.observedAtMs, sample.observedAtElapsedMs)
        }
        val notification = observedNotifications.filter {
            nowElapsed - it.first.observedAtElapsedMs <= 60_000
        }.fold(NavigationContext()) { previous, (_, next) -> fuseNavigation(previous, next, nowElapsed) }
        val lastNotificationEta = observedNotifications.mapNotNull { (sample, parsed) ->
            parsed.remainingMinutes?.value?.let { LastObservedEta(
                if (sample.packageName == WazeNotificationExtractor.MAPS_PACKAGE) EtaSource.GOOGLE_MAPS else EtaSource.WAZE,
                it, sample.observedAtElapsedMs) }
        }.maxByOrNull { it.observedAtElapsedMs }
        val screen = WazeScreenState.snapshot
        val sameScreenSession = screen.sessionId != null &&
            screen.sessionId == TripNavigationState.context.sessionId && WazeScreenAccess.allowed(context)
        val screenSource = if (screen.packageName == WazeNotificationExtractor.MAPS_PACKAGE)
            ContextSource.GOOGLE_MAPS_ACCESSIBILITY else ContextSource.WAZE_ACCESSIBILITY
        val accessible = if (sameScreenSession && screen.visible && screen.recent(nowElapsed))
            NavigationObservationParser.parse(screen.texts.map(ScreenText::text),
                screenSource,
                nowEpoch - (nowElapsed - screen.observedAtMs), screen.observedAtMs) else NavigationContext()
        val lastScreenEta = if (sameScreenSession && screen.observedAtMs > 0 &&
            nowElapsed >= screen.observedAtMs && nowElapsed - screen.observedAtMs < LastObservedEta.MAX_AGE_MS)
            NavigationObservationParser.parse(screen.texts.map(ScreenText::text), screenSource,
                nowEpoch - (nowElapsed - screen.observedAtMs), screen.observedAtMs)
                .remainingMinutes?.value?.let { LastObservedEta(
                    if (screenSource == ContextSource.GOOGLE_MAPS_ACCESSIBILITY) EtaSource.GOOGLE_MAPS else EtaSource.WAZE,
                    it, screen.observedAtMs) } else null
        val lastObservedEta = listOfNotNull(lastNotificationEta, lastScreenEta)
            .maxByOrNull { it.observedAtElapsedMs }
        val source = fuseNavigation(notification, accessible, nowElapsed)
        val gps = latestLocation?.takeIf { TripLocationAccess.available(context) && !it.isSimulated }
            ?.let { ContextValue(it, ContextSource.ANDROID_GPS, it.epochMs, it.elapsedRealtimeMs, .9f, 10_000) }
        val target = TripNavigationState.context.lastDispatch?.takeIf {
            it.dispatchedAtElapsedRealtimeMs > 0
        }?.let { dispatch -> dispatch.target?.let {
            ContextValue(it, ContextSource.SAVED_DESTINATION_COORDS, dispatch.dispatchedAtEpochMs,
                dispatch.dispatchedAtElapsedRealtimeMs, 1f, 86_400_000)
        }
        }
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
        return TripContextSnapshot(source, gps, target, mediaContext,
            DeviceTripContext(device(if (level >= 0 && scale > 0) level * 100 / scale else null),
                device(status in setOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)),
                device(bluetoothAudio)), lastObservedEta)
    }
}
