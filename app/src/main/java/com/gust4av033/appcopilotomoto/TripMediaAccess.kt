package com.gust4av033.appcopilotomoto

import android.content.ComponentName
import android.content.Context
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

    fun granted(context: Context): Boolean = Settings.Secure.getString(context.contentResolver,
        "enabled_notification_listeners").orEmpty().split(':').any {
        ComponentName.unflattenFromString(it) == component(context)
    }

    fun controller(context: Context): MediaController {
        check(granted(context)) { "Habilitá acceso multimedia en Ajustes de APP para usar el botón reasignado." }
        val sessions = context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(component(context)).filter { it.packageName != context.packageName }
        val preferred = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
            .getString("music_search_player", null)
        if (preferred != null) return sessions.firstOrNull { it.packageName == preferred }
            ?: error("El reproductor elegido no tiene una sesión activa; abrilo y reproducí música.")
        return sessions.firstOrNull { it.packageName == MusicPlayerSelection.POWERAMP_PACKAGE }
            ?: sessions.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }.singleOrNull()
            ?: sessions.singleOrNull() ?: error("No hay un reproductor único; elegilo en Ajustes → Música.")
    }

    fun dispatchPair(context: Context, keyCode: Int) {
        val controller = controller(context)
        val time = android.os.SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            check(controller.dispatchMediaButtonEvent(KeyEvent(time, android.os.SystemClock.uptimeMillis(), action, keyCode, 0))) {
                "El reproductor rechazó el botón multimedia."
            }
        }
    }
}
