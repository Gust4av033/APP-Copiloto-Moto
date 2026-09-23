package com.gust4av033.appcopilotomoto

import java.text.Normalizer
import java.util.Locale

enum class TestIntent { MEDIA_NEXT, STOP_TTS, CANCEL, UNKNOWN }

/** Las órdenes claras pasan por reglas locales; una frase desconocida no ejecuta nada. */
object MinimalIntentRouter {
    fun resolve(transcript: String): TestIntent {
        val normalized = Normalizer.normalize(transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        return when {
            normalized in setOf("callate", "silencio", "deja de hablar", "para de hablar") -> TestIntent.STOP_TTS
            normalized in setOf("cancelar", "cancela", "olvidalo") -> TestIntent.CANCEL
            normalized in setOf("siguiente cancion", "siguiente pista", "pon la siguiente", "pasa a la siguiente", "salta la cancion", "next") -> TestIntent.MEDIA_NEXT
            else -> TestIntent.UNKNOWN
        }
    }
}
