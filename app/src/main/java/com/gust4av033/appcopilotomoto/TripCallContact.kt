package com.gust4av033.appcopilotomoto

import android.content.Context

/** Número obtenido del selector o de una coincidencia única con la agenda local. */
data class TripCallContact(val name: String, val number: String) {
    val display: String get() = "$name (••••${number.takeLast(4)})"

    companion object {
        fun fromPhone(name: String, number: String): TripCallContact? {
            if (!number.matches(Regex("[+0-9() .-]+"))) return null
            val compact = number.filter { it.isDigit() }
            if (compact.length !in 7..15) return null
            val normalized = if (number.trim().startsWith("+")) "+$compact" else compact
            return TripCallContact(name.trim().take(60).ifBlank { "Contacto elegido" }, normalized)
        }
    }
}

object TripCallContactStore {
    private const val PREFS = "trip_call_contact"

    fun save(context: Context, name: String, number: String): TripCallContact? {
        val contact = TripCallContact.fromPhone(name, number) ?: return null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", contact.name)
            .putString("number", contact.number)
            .apply()
        return contact
    }

    fun load(context: Context): TripCallContact? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val number = prefs.getString("number", null) ?: return null
        if (!number.matches(Regex("\\+?[0-9]{7,15}"))) return null
        return TripCallContact(prefs.getString("name", "Contacto elegido").orEmpty(), number)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
