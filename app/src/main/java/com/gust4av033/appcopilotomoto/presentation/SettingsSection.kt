package com.gust4av033.appcopilotomoto.presentation

import com.gust4av033.appcopilotomoto.R

/** Rutas de presentación: abrir una categoría no modifica preferencias ni audio. */
enum class SettingsSection(val titleRes: Int, val hintRes: Int, val iconRes: Int) {
    OVERVIEW(R.string.nav_settings, R.string.settings_subtitle, R.drawable.ic_settings),
    ACTIVATION(R.string.settings_category_activation, R.string.settings_category_activation_hint, R.drawable.ic_commands),
    AUDIO(R.string.settings_category_audio, R.string.settings_category_audio_hint, R.drawable.ic_audio),
    APPEARANCE(R.string.settings_category_appearance, R.string.settings_category_appearance_hint, R.drawable.ic_display),
    INTEGRATIONS(R.string.settings_category_integrations, R.string.settings_category_integrations_hint, R.drawable.ic_commands),
    NAVIGATION(R.string.settings_category_navigation, R.string.settings_category_navigation_hint, R.drawable.ic_trip),
    CALLS(R.string.settings_category_calls, R.string.settings_category_calls_hint, R.drawable.ic_phone),
    TOOLS(R.string.settings_category_tools, R.string.settings_category_tools_hint, R.drawable.ic_tests)
}
