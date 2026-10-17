package com.gust4av033.appcopilotomoto

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent

/** Android exige acceso a notificaciones para consultar sesiones. No inspecciona su contenido. */
class TripMediaAccessService : NotificationListenerService()

internal object TripMediaAccess {
    private fun component(context: Context) = ComponentName(context, TripMediaAccessService::class.java)

    /** Abre el permiso de esta instalación, también con un applicationIdSuffix de prueba. */
    fun settingsIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
    } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun granted(context: Context): Boolean = Settings.Secure.getString(context.contentResolver,
        "enabled_notification_listeners").orEmpty().split(':').any {
        ComponentName.unflattenFromString(it) == component(context)
    }

    fun sessions(context: Context): List<MediaController> {
        check(granted(context)) { "Habilitá acceso multimedia en Ajustes de APP para usar el botón reasignado." }
        return context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(component(context)).filter { it.packageName != context.packageName }
    }

    fun playingPackages(context: Context): List<String> = runCatching { sessions(context)
        .filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        .map { it.packageName }.distinct() }.getOrDefault(emptyList())

    fun searchController(context: Context, packageName: String): MediaController? = runCatching {
        sessions(context).firstOrNull { it.packageName == packageName &&
            ((it.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_SEARCH) != 0L }
    }.getOrNull()

    fun controller(context: Context, explicitPackage: String? = null): MediaController {
        val sessions = sessions(context)
        if (explicitPackage != null) return sessions.firstOrNull { it.packageName == explicitPackage }
            ?: error("El reproductor pedido no tiene una sesión multimedia activa; abrilo y reproducí música.")
        val tripPackage = TripMusicState.activePackage
        if (tripPackage != null) return sessions.firstOrNull { it.packageName == tripPackage }
            ?: error("El reproductor de este viaje no tiene una sesión activa; abrilo y reproducí música.")
        val playing = sessions.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (playing.isEmpty()) TripMusicState.cyclePlayingPackage?.let { captured ->
            sessions.firstOrNull { it.packageName == captured }?.let { return it }
        }
        val preferred = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
            .getString("music_search_player", null)
        if (playing.size == 1) return playing.single()
        if (playing.size > 1) return playing.firstOrNull { it.packageName == preferred }
            ?: error("Hay varios reproductores sonando; elegí uno en Ajustes → Música.")
        if (preferred != null) return sessions.firstOrNull { it.packageName == preferred }
            ?: error("El reproductor elegido no tiene una sesión activa; abrilo y reproducí música.")
        return sessions.firstOrNull { it.packageName == MusicPlayerSelection.POWERAMP_PACKAGE }
            ?: sessions.singleOrNull() ?: error("No hay un reproductor único; elegilo en Ajustes → Música.")
    }

    fun isPlaying(context: Context): Boolean = runCatching {
        controller(context).playbackState?.state in setOf(PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING)
    }.getOrDefault(false)

    fun dispatchPair(context: Context, keyCode: Int, explicitPackage: String? = null) {
        val controller = controller(context, explicitPackage)
        val time = android.os.SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            check(controller.dispatchMediaButtonEvent(KeyEvent(time, android.os.SystemClock.uptimeMillis(), action, keyCode, 0))) {
                "El reproductor rechazó el botón multimedia."
            }
        }
    }
}
