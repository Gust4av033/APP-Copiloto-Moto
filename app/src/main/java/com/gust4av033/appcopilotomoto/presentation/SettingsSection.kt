package com.gust4av033.appcopilotomoto.presentation

import com.gust4av033.appcopilotomoto.R

/** Rutas de presentación: abrir una categoría no modifica preferencias ni audio. */
enum class SettingsSection(val titleRes: Int, val hintRes: Int, val iconRes: Int) {
    OVERVIEW(R.string.nav_settings, R.string.settings_subtitle, R.drawable.ic_settings),
    ACTIVATION(R.string.settings_category_activation, R.string.settings_category_activation_hint, R.drawable.ic_mic),
    AUDIO(R.string.settings_category_audio, R.string.settings_category_audio_hint, R.drawable.ic_audio),
    APPEARANCE(R.string.settings_category_appearance, R.string.settings_category_appearance_hint, R.drawable.ic_display),
    INTEGRATIONS(R.string.settings_category_integrations, R.string.settings_category_integrations_hint, R.drawable.ic_link),
    NAVIGATION(R.string.settings_category_navigation, R.string.settings_category_navigation_hint, R.drawable.ic_trip),
    CALLS(R.string.settings_category_calls, R.string.settings_category_calls_hint, R.drawable.ic_phone),
    PRIVACY(R.string.settings_category_privacy, R.string.settings_category_privacy_hint, R.drawable.ic_shield),
    ABOUT(R.string.settings_category_about, R.string.settings_category_about_hint, R.drawable.ic_info),
    DEVELOPER(R.string.settings_developer, R.string.settings_developer_hint, R.drawable.ic_tests),
    TOOLS(R.string.settings_category_tools, R.string.settings_category_tools_hint, R.drawable.ic_tests);

    fun visibleFor(state: CopilotUiState): SettingsSection = if (this == TOOLS || this == DEVELOPER)
        if (state.developerEnabled) DEVELOPER else ABOUT else this

    companion object {
        val userSections = listOf(ACTIVATION, AUDIO, APPEARANCE, NAVIGATION, INTEGRATIONS, PRIVACY, ABOUT)
    }
}

