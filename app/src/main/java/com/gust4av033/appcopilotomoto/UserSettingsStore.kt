package com.gust4av033.appcopilotomoto

import android.content.Context

/** Uses the existing preference file without changing keys belonging to other features. */
internal class UserSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    fun tripLocationEnabled(): Boolean = preferences.getBoolean("trip_location_enabled", false)
    fun setTripLocationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("trip_location_enabled", enabled).apply()
    }

    fun loadVoice(legacyPhrase: String? = null, legacySensitivity: Float? = null): Pair<String, Float> {
        val phrase = validPhrase(preferences.getString("wake_phrase", legacyPhrase))
        val sensitivity = validSensitivity(preferences.getFloat("wake_sensitivity", legacySensitivity ?: 80f))
        saveVoice(phrase, sensitivity)
        return phrase to sensitivity
    }

    fun saveVoice(phrase: String, sensitivity: Float) {
        preferences.edit().putString("wake_phrase", validPhrase(phrase))
            .putFloat("wake_sensitivity", validSensitivity(sensitivity)).apply()
    }

    fun developerEnabled(debugBuild: Boolean): Boolean = preferences.getBoolean(developerKey(debugBuild), false)
    fun setDeveloperEnabled(debugBuild: Boolean, enabled: Boolean) {
        preferences.edit().putBoolean(developerKey(debugBuild), enabled).apply()
    }

    fun developerDiagnosticsUnlocked(defaultValue: Boolean = BuildConfig.SHOW_DIAGNOSTICS_BY_DEFAULT): Boolean =
        preferences.getBoolean("developer_diagnostics_unlocked", defaultValue)

    fun setDeveloperDiagnosticsUnlocked(enabled: Boolean) {
        preferences.edit().putBoolean("developer_diagnostics_unlocked", enabled).apply()
    }

    private fun developerKey(debugBuild: Boolean) = if (debugBuild) "developer_opt_in_debug" else "developer_opt_in_public"
    private fun validPhrase(value: String?) = value?.takeIf { it in setOf("ALEXA", "HEY_JARVIS") } ?: "ALEXA"
    private fun validSensitivity(value: Float) = if (value.isFinite()) value.coerceIn(40f, 85f) else 80f
}
