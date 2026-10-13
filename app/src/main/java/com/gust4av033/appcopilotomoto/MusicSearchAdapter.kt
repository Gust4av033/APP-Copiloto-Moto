package com.gust4av033.appcopilotomoto

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

/** Intent estándar para reproductores compatibles; Poweramp permite envío directo sin Activity. */
class MusicSearchAdapter(private val context: Context) {
    companion object {
        private const val POWERAMP_RECEIVER = "com.maxmpz.audioplayer.player.PowerampAPIReceiver"
        private const val PREFERENCE = "music_search_player"
    }

    private val manager get() = context.packageManager
    private fun allowed(permission: String?): Boolean = permission.isNullOrEmpty() ||
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun availablePlayers(): Result<List<MusicPlayer>> = runCatching {
        val standard = manager.queryIntentActivities(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH),
            PackageManager.MATCH_DEFAULT_ONLY).mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (!activity.enabled || !activity.exported || !allowed(activity.permission) ||
                activity.packageName == context.packageName) return@mapNotNull null
            MusicPlayer(activity.packageName, info.loadLabel(manager).toString(), backgroundSearch = false)
        }.distinctBy(MusicPlayer::packageName)
        val receiver = runCatching {
            manager.getReceiverInfo(ComponentName(MusicPlayerSelection.POWERAMP_PACKAGE, POWERAMP_RECEIVER), 0)
        }.getOrNull()
        val direct = receiver?.takeIf { it.enabled && it.exported && allowed(it.permission) }?.let {
            MusicPlayer(it.packageName, it.applicationInfo.loadLabel(manager).toString(), backgroundSearch = true)
        }
        (standard.filterNot { it.packageName == direct?.packageName } + listOfNotNull(direct))
            .sortedBy { MinimalIntentRouter.normalize(it.label) }
    }

    fun preferredPackage(): String? = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
        .getString(PREFERENCE, null)

    fun selectPlayer(packageName: String?) {
        context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE).edit()
            .putString(PREFERENCE, packageName).apply()
    }

    fun prepare(command: RoutedCommand): Result<MusicSearchRequest> = runCatching {
        check(command.intent == TestIntent.MEDIA_SEARCH) { "La orden no pide una canción por nombre." }
        val query = checkNotNull(command.musicQuery).trim()
        check(query.isNotBlank()) { "Falta el nombre de la canción." }
        MusicSearchRequest(MusicPlayerSelection.choose(availablePlayers().getOrThrow(), preferredPackage()).getOrThrow(), query)
    }

    /** Resultado = solicitud enviada; el reproductor decide si encuentra la canción. */
    fun send(request: MusicSearchRequest): Result<Unit> = runCatching {
        val current = availablePlayers().getOrThrow().singleOrNull { it.packageName == request.player.packageName }
        check(current == request.player) { "El reproductor cambió o ya no está disponible. Pedí la canción otra vez." }
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, request.query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            .putExtra(MediaStore.EXTRA_MEDIA_TITLE, request.query)
        if (current.backgroundSearch) {
            intent.component = ComponentName(current.packageName, POWERAMP_RECEIVER)
            context.sendBroadcast(intent)
        } else {
            check(context is Activity && !context.isFinishing) {
                "${current.label} requiere APP visible y el teléfono desbloqueado para buscar por nombre. Poweramp admite envío directo."
            }
            intent.setPackage(current.packageName)
            context.startActivity(intent)
        }
    }
}
