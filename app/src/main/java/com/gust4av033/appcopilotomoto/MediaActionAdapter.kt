package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

class MediaActionAdapter(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Confirma envío del evento, no que otra app haya cambiado de pista. */
    fun nextTrack(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_NEXT) }

    fun previousTrack(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }

    fun play(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_PLAY) }

    fun pause(): Result<Unit> = runCatching { dispatch(KeyEvent.KEYCODE_MEDIA_PAUSE) }

    /** Solicita una pausa solo si Android informa reproducción activa. */
    fun pauseIfPlaying(): Boolean {
        if (!audioManager.isMusicActive) return false
        dispatch(KeyEvent.KEYCODE_MEDIA_PAUSE)
        return true
    }

    fun resumePlayback() = dispatch(KeyEvent.KEYCODE_MEDIA_PLAY)

    private fun dispatch(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(downTime, SystemClock.uptimeMillis(), action, keyCode, 0)
            )
        }
    }
}
