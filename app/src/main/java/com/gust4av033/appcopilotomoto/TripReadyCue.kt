package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin

/** Señal corta por la ruta de comunicación. No cambia volumen, foco ni selección de casco. */
internal class TripReadyCue(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private val release = Runnable { cancel() }

    fun play(): Result<String> = runCatching {
        cancel()
        // Dos pulsos con entrada/salida suave; el pequeño silencio inicial da margen a la salida.
        val rate = 16_000
        val samples = ShortArray(rate * 360 / 1_000) { frame ->
            val ms = frame * 1_000.0 / rate
            val start = when {
                ms in 40.0..160.0 -> 40.0
                ms in 200.0..320.0 -> 200.0
                else -> return@ShortArray 0
            }
            val position = ms - start
            val fade = minOf(position / 10.0, (120.0 - position) / 10.0, 1.0).coerceAtLeast(0.0)
            val frequency = if (start == 40.0) 880.0 else 1_100.0
            (sin(2 * PI * frequency * frame / rate) * 8_000 * fade).toInt().toShort()
        }
        val output = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
        track = output
        check(output.state != AudioTrack.STATE_UNINITIALIZED) { "Android no preparó la señal para hablar." }
        val preferred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.communicationDevice else null
        if (preferred != null) check(output.setPreferredDevice(preferred)) { "Android rechazó la salida del tono." }
        check(output.write(samples, 0, samples.size) == samples.size) { "No se pudo cargar el tono." }
        check(output.state == AudioTrack.STATE_INITIALIZED) { "Android no preparó la señal para hablar." }
        output.play()
        check(output.playState == AudioTrack.PLAYSTATE_PLAYING) { "Android no inició el tono." }
        handler.postDelayed(release, 800L) // Deja drenar el buffer de la ruta Bluetooth.
        "Tono iniciado; salida solicitada=${preferred?.productName ?: "ruta de comunicación"}; " +
            "volumen comunicación=${audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}/${audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}. Audición pendiente de comprobar."
    }.onFailure { cancel() }

    fun cancel() {
        handler.removeCallbacks(release)
        val old = track ?: return
        track = null
        runCatching { old.stop() }
        runCatching { old.release() }
    }
}
