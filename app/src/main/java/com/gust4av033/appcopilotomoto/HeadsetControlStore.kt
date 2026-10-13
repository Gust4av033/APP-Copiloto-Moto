package com.gust4av033.appcopilotomoto

import android.content.Context
import android.media.AudioManager
import android.os.Build

/** Perfil local por identidad Bluetooth. El nombre es una etiqueta, nunca una prueba de controles. */
internal object HeadsetControlStore {
    private const val PREFS = "headset_controls_observed"
    private val temporary = mutableMapOf<HeadsetIdentity, HeadsetControlEvidence>()

    fun connected(context: Context): HeadsetIdentity? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            BluetoothAudioRouteSelector.communicationHeadset(context.getSystemService(AudioManager::class.java))
                ?.let(BluetoothAudioRouteSelector::identity)
        }.getOrNull()
    } else null

    @Synchronized
    fun read(context: Context, headset: HeadsetIdentity?): HeadsetControlEvidence {
        if (headset == null) return HeadsetControlEvidence()
        val id = HeadsetControlEvidence.persistentId(headset) ?: return temporary[headset] ?: HeadsetControlEvidence()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val keys = prefs.getStringSet("keys_$id", emptySet()).orEmpty().mapNotNull(String::toIntOrNull)
            .filter { it in HeadsetControlEvidence.MEDIA_KEYS }.toSet()
        return HeadsetControlEvidence(keys, prefs.getBoolean("assistant_$id", false))
    }

    @Synchronized
    fun observeKey(context: Context, headset: HeadsetIdentity?, keyCode: Int) {
        if (headset == null) return
        val old = read(context, headset)
        save(context, headset, old, old.observeKey(keyCode))
    }

    @Synchronized
    fun observeAssistant(context: Context, headset: HeadsetIdentity?) {
        if (headset == null) return
        val old = read(context, headset)
        save(context, headset, old, old.copy(assistantInvocation = true))
    }

    @Synchronized
    fun forgetDisconnected(connected: Set<HeadsetIdentity>) {
        temporary.keys.retainAll(connected)
    }

    private fun save(context: Context, headset: HeadsetIdentity, old: HeadsetControlEvidence, updated: HeadsetControlEvidence) {
        if (old == updated) return
        val id = HeadsetControlEvidence.persistentId(headset)
        if (id == null) temporary[headset] = updated
        else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("keys_$id", updated.mediaKeys.map(Int::toString).toSet())
            .putBoolean("assistant_$id", updated.assistantInvocation).apply()
    }
}
