package com.gust4av033.appcopilotomoto

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A bounded diagnostic sample, not a parsed turn or a confirmed navigation session. */
data class WazeNotificationSample(
    val key: String,
    val category: String,
    val sourcePostTimeMs: Long,
    val observedAtMs: Long,
    val fields: Map<String, String>,
    val packageName: String = "com.waze",
    val observedAtElapsedMs: Long = 0
) {
    fun isRecent(nowMs: Long) = sourcePostTimeMs > 0 && nowMs >= sourcePostTimeMs && nowMs - sourcePostTimeMs <= 60_000
}

data class WazeObservationSnapshot(
    val sessionId: String? = null,
    val connected: Boolean = false,
    val sample: WazeNotificationSample? = null,
    val readFailed: Boolean = false,
    val samples: Map<String, WazeNotificationSample> = emptyMap()
) {
    fun record(session: String?, value: WazeNotificationSample): WazeObservationSnapshot =
        if (sessionId != null && session == sessionId) copy(sample = value, readFailed = false,
            samples = samples + (value.packageName to value)) else this

    fun report(nowMs: Long): String = buildString {
        appendLine("Fuentes: notificaciones de Waze y Google Maps (no confirman una ruta)")
        appendLine("Conectado=$connected; fallo de lectura=$readFailed; sesión activa=${sessionId != null}")
        val allSamples = samples.ifEmpty { sample?.let { mapOf(it.packageName to it) }.orEmpty() }
        if (allSamples.isEmpty()) appendLine("Sin muestras de navegación en esta sesión.")
        allSamples.toSortedMap().forEach { (packageName, value) ->
            appendLine("Paquete=$packageName; publicado=${value.sourcePostTimeMs}; observado=${value.observedAtMs}; reciente=${value.isRecent(nowMs)}; categoría=${value.category}")
            value.fields.forEach { (name, text) -> appendLine("$name: $text") }
        }
    }
}

internal object WazeObservationAccess {
    private const val PREFS = "waze_observation"
    private fun key() = if (BuildConfig.IS_PLAYSTORE_BUILD) "enabled_playstore" else "enabled"
    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(key(), BuildConfig.IS_PLAYSTORE_BUILD)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key(), enabled).apply()
        WazeNotificationObservation.clearSample()
    }
    private fun component(context: Context) = ComponentName(context, WazeNavigationObservationService::class.java)
    fun granted(context: Context) = Settings.Secure.getString(context.contentResolver,
        "enabled_notification_listeners").orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component(context) }
    fun settingsIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    fun allowed(context: Context) = (BuildConfig.IS_PLAYSTORE_BUILD ||
        (BuildConfig.DEBUG && UserSettingsStore(context).developerEnabled(BuildConfig.DEBUG))) &&
        enabled(context) && granted(context) && TripNavigationState.context.sessionId != null
}

internal object WazeNotificationExtractor {
    const val PACKAGE = "com.waze"
    const val MAPS_PACKAGE = "com.google.android.apps.maps"
    fun extract(sbn: StatusBarNotification, nowMs: Long): WazeNotificationSample? {
        // Filter before accessing extras, including when Android delivers another app's event.
        if (sbn.packageName !in setOf(PACKAGE, MAPS_PACKAGE)) return null
        val notification = sbn.notification
        val extras = notification.extras
        val fields = linkedMapOf<String, String>()
        for (name in listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT)) {
            extras.getCharSequence(name)?.toString()?.take(240)?.takeIf { it.isNotBlank() }?.let { fields[name] = it }
        }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.take(4)?.forEachIndexed { index, text ->
            text?.toString()?.take(240)?.takeIf { it.isNotBlank() }?.let { fields["line_$index"] = it }
        }
        return WazeNotificationSample(sbn.key, notification.category.orEmpty().take(80), sbn.postTime,
            nowMs, fields, sbn.packageName, SystemClock.elapsedRealtime())
    }
}

/** Main-thread state; the last sample is discarded on stop, disconnect, removal and opt-out. */
object WazeNotificationObservation {
    var snapshot by mutableStateOf(WazeObservationSnapshot())
        private set
    internal var listener: WazeNavigationObservationService? = null
    fun beginSession(sessionId: String?) {
        snapshot = WazeObservationSnapshot(sessionId, connected = snapshot.connected)
        listener?.readExisting()
    }
    fun endSession() { snapshot = WazeObservationSnapshot(connected = snapshot.connected) }
    fun clearSample() { snapshot = snapshot.copy(sample = null, samples = emptyMap(), readFailed = false) }
    internal fun connection(connected: Boolean) { snapshot = snapshot.copy(connected = connected, sample = null,
        samples = emptyMap(), readFailed = false) }
    internal fun record(sessionId: String?, value: WazeNotificationSample) { snapshot = snapshot.record(sessionId, value) }
    internal fun removed(key: String) {
        val remaining = snapshot.samples.filterValues { it.key != key }
        if (remaining.size != snapshot.samples.size) snapshot = snapshot.copy(samples = remaining,
            sample = remaining.values.maxByOrNull { it.observedAtElapsedMs })
    }
    internal fun readFailed() { snapshot = snapshot.copy(sample = null, readFailed = true) }
}

/** Separate permission from media controls; playstore uses this source without Accessibility. */
class WazeNavigationObservationService : NotificationListenerService() {
    override fun onListenerConnected() {
        WazeNotificationObservation.listener = this
        WazeNotificationObservation.connection(true)
        readExisting()
    }
    override fun onListenerDisconnected() {
        WazeNotificationObservation.listener = null
        WazeNotificationObservation.connection(false)
    }
    internal fun readExisting() {
        if (!WazeObservationAccess.allowed(this) || !WazeNotificationObservation.snapshot.connected) return
        runCatching {
            activeNotifications.filter { it.packageName in setOf(WazeNotificationExtractor.PACKAGE,
                WazeNotificationExtractor.MAPS_PACKAGE) }.groupBy { it.packageName }.values
                .mapNotNull { entries -> entries.maxByOrNull { it.postTime } }.forEach(::onNotificationPosted)
        }.onFailure { WazeNotificationObservation.readFailed() }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!WazeObservationAccess.allowed(this) || !WazeNotificationObservation.snapshot.connected) {
            WazeNotificationObservation.clearSample(); return
        }
        val session = TripNavigationState.context.sessionId
        runCatching { WazeNotificationExtractor.extract(sbn, System.currentTimeMillis()) }
            .onSuccess { sample -> if (sample != null) WazeNotificationObservation.record(session, sample) }
            .onFailure { WazeNotificationObservation.readFailed() }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in setOf(WazeNotificationExtractor.PACKAGE, WazeNotificationExtractor.MAPS_PACKAGE))
            WazeNotificationObservation.removed(sbn.key)
    }
    override fun onDestroy() {
        if (WazeNotificationObservation.listener === this) {
            WazeNotificationObservation.listener = null
            WazeNotificationObservation.connection(false)
        }
        super.onDestroy()
    }
}
