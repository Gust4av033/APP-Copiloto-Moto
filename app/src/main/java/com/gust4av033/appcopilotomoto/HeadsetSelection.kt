package com.gust4av033.appcopilotomoto

import android.content.Context

/** IDs temporales: nunca reutilizar una selección para un dispositivo diferente. */
internal data class HeadsetIdentity(
    val id: Int,
    val type: Int,
    val address: String,
    val name: String
) {
    fun matches(other: HeadsetIdentity): Boolean = this == other
}

/** A saved choice uses stable identity; a transient Android ID is never persisted. */
internal object HeadsetSelection {
    @Volatile
    var preferred: HeadsetIdentity? = null
        private set

    @Synchronized fun select(device: HeadsetIdentity?) { preferred = device }

    @Synchronized fun saveChoice(context: Context, device: HeadsetIdentity?) {
        select(device)
        val prefs = context.getSharedPreferences("headset_selection", Context.MODE_PRIVATE)
        val edit = prefs.edit().clear()
        if (device != null) edit.putString("address", device.address).putString("name", device.name)
            .putInt("type", device.type).putBoolean("manual", true)
        edit.apply()
    }

    @Synchronized fun restore(context: Context) {
        if (preferred != null) return
        val prefs = context.getSharedPreferences("headset_selection", Context.MODE_PRIVATE)
        if (prefs.getBoolean("manual", false)) preferred = HeadsetIdentity(-1,
            prefs.getInt("type", 0), prefs.getString("address", "").orEmpty(), prefs.getString("name", "").orEmpty())
    }

    @Synchronized fun resolve(available: List<HeadsetIdentity>): HeadsetIdentity? {
        val selected = preferred ?: return null
        return if (selected.id >= 0) available.singleOrNull(selected::matches)
        else if (selected.address.isNotBlank()) available.singleOrNull {
            it.address == selected.address && it.type == selected.type && it.name == selected.name
        } else null // Anonymous devices require an explicit choice after a process restart.
    }
}
