package com.gust4av033.appcopilotomoto

/** Conserva el modo elegido, pero no crea un detector mientras la activación sea manual. */
object TripActivationPolicy {
    const val PREFERENCE = "manual_activation_only"

    fun effectiveMode(selected: WakeAudioMode, manualOnly: Boolean): WakeAudioMode =
        if (manualOnly) WakeAudioMode.BUTTON else selected
}
