package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

class MediaActionAdapter(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Confirma envío del evento, no que otra app haya cambiado de pista. */
    fun nextTrack(): Result<Unit> = runCatching {
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(downTime, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_MEDIA_NEXT, 0)
            )
        }
    }
}
