package com.gust4av033.appcopilotomoto

/** Solo admite una elección completa; no interpreta frases compuestas ni números fuera de 1–3. */
object TripModeCommands {
    fun parse(normalized: String): WakeAudioMode? {
        val text = normalized.removePrefix("alexa ").removePrefix("hey jarvis ")
        val name = Regex("^(?:activa|activar|cambia a|cambia al|cambiar a|cambiar al|pon|pone|usa|usar) (?:el )?modo (.+)$")
            .matchEntire(text)?.groupValues?.get(1) ?: return null
        return when (name) {
            "1", "uno", "voz", "voz continua", "continuo", "voz continuo" -> WakeAudioMode.BLUETOOTH
            "2", "dos", "boton", "por boton" -> WakeAudioMode.BUTTON
            "3", "tres", "experimental", "telefono" -> WakeAudioMode.PHONE_WAKE
            else -> null
        }
    }
}

val WakeAudioMode.modeNumber: Int get() = when (this) {
    WakeAudioMode.BLUETOOTH -> 1
    WakeAudioMode.BUTTON -> 2
    WakeAudioMode.PHONE_WAKE -> 3
}

enum class TripInteractionPhase { PREPARING, WAITING, LISTENING, PROCESSING, SPEAKING, CONFIRMING, SWITCHING }
