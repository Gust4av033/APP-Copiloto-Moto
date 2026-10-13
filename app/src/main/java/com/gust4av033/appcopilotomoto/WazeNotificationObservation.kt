package com.gust4av033.appcopilotomoto

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
    val fields: Map<String, String>
) {
    fun isRecent(nowMs: Long) = sourcePostTimeMs > 0 && nowMs >= sourcePostTimeMs && nowMs - sourcePostTimeMs <= 60_000
}

data class WazeObservationSnapshot(
    val sessionId: String? = null,
    val connected: Boolean = false,
    val sample: WazeNotificationSample? = null,
    val readFailed: Boolean = false
) {
    fun record(session: String?, value: WazeNotificationSample): WazeObservationSnapshot =
        if (sessionId != null && session == sessionId) copy(sample = value, readFailed = false) else this

    fun report(nowMs: Long): String = buildString {
        appendLine("Fuente: notificación de com.waze (experimental; no confirma una ruta)")
        appendLine("Conectado=$connected; fallo de lectura=$readFailed; sesión activa=${sessionId != null}")
        val value = sample
        if (value == null) appendLine("Sin muestra de Waze en esta sesión.")
        else {
            appendLine("Publicado=${value.sourcePostTimeMs}; observado=${value.observedAtMs}; reciente=${value.isRecent(nowMs)}; categoría=${value.category}")
            value.fields.forEach { (name, text) -> appendLine("$name: $text") }
        }
    }
}

internal object WazeObservationAccess {
    private const val PREFS = "waze_observation"
    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
        WazeNotificationObservation.clearSample()
    }
    private fun component(context: Context) = ComponentName(context, WazeNavigationObservationService::class.java)
    fun granted(context: Context) = Settings.Secure.getString(context.contentResolver,
        "enabled_notification_listeners").orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component(context) }
    fun settingsIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    fun allowed(context: Context) = BuildConfig.DEBUG && UserSettingsStore(context).developerEnabled(BuildConfig.DEBUG) &&
        enabled(context) && granted(context) && TripNavigationState.context.sessionId != null
}

internal object WazeNotificationExtractor {
    const val PACKAGE = "com.waze"
    fun extract(sbn: StatusBarNotification, nowMs: Long): WazeNotificationSample? {
        // Filter before accessing extras, including when Android delivers another app's event.
        if (sbn.packageName != PACKAGE) return null
        val notification = sbn.notification
        val extras = notification.extras
        val fields = linkedMapOf<String, String>()
        for (name in listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT)) {
            extras.getCharSequence(name)?.toString()?.take(240)?.takeIf { it.isNotBlank() }?.let { fields[name] = it }
        }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.take(4)?.forEachIndexed { index, text ->
            text?.toString()?.take(240)?.takeIf { it.isNotBlank() }?.let { fields["line_$index"] = it }
        }
        return WazeNotificationSample(sbn.key, notification.category.orEmpty().take(80), sbn.postTime, nowMs, fields)
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
    fun clearSample() { snapshot = snapshot.copy(sample = null, readFailed = false) }
    internal fun connection(connected: Boolean) { snapshot = snapshot.copy(connected = connected, sample = null, readFailed = false) }
    internal fun record(sessionId: String?, value: WazeNotificationSample) { snapshot = snapshot.record(sessionId, value) }
    internal fun removed(key: String) { if (snapshot.sample?.key == key) clearSample() }
    internal fun readFailed() { snapshot = snapshot.copy(sample = null, readFailed = true) }
}

/** Separate permission from media controls. Passive inspection is restricted to developer opt-in. */
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
            activeNotifications.filter { it.packageName == WazeNotificationExtractor.PACKAGE }
                .maxByOrNull { it.postTime }?.let { onNotificationPosted(it) }
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
        if (sbn.packageName == WazeNotificationExtractor.PACKAGE) WazeNotificationObservation.removed(sbn.key)
    }
    override fun onDestroy() {
        if (WazeNotificationObservation.listener === this) {
            WazeNotificationObservation.listener = null
            WazeNotificationObservation.connection(false)
        }
        super.onDestroy()
    }
}
