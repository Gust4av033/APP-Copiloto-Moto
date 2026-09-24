package com.gust4av033.appcopilotomoto

import java.text.Normalizer
import java.util.Locale

enum class TestIntent { MEDIA_NEXT, MEDIA_PREVIOUS, MEDIA_PLAY, MEDIA_PAUSE, VOLUME_UP, VOLUME_DOWN, STOP_TTS, CANCEL, STOP_SESSION, UNKNOWN }

/** Las órdenes claras pasan por reglas locales; una frase desconocida no ejecuta nada. */
object MinimalIntentRouter {
    fun resolve(transcript: String): TestIntent {
        val normalized = Normalizer.normalize(transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        return when {
            normalized in setOf("detener sesion", "terminar sesion", "finalizar sesion") -> TestIntent.STOP_SESSION
            normalized in setOf("callate", "silencio", "deja de hablar", "para de hablar") -> TestIntent.STOP_TTS
            normalized in setOf("cancelar", "cancela", "olvidalo") -> TestIntent.CANCEL
            normalized in setOf("subir volumen", "sube volumen", "sube el volumen", "subir el volumen", "mas volumen", "volumen mas alto") -> TestIntent.VOLUME_UP
            normalized in setOf("bajar volumen", "baja volumen", "baja el volumen", "bajar el volumen", "menos volumen", "volumen mas bajo") -> TestIntent.VOLUME_DOWN
            normalized in setOf("siguiente cancion", "siguiente pista", "pon la siguiente", "pasa a la siguiente", "salta la cancion", "siguiente", "next") -> TestIntent.MEDIA_NEXT
            normalized in setOf("anterior cancion", "cancion anterior", "pista anterior", "anterior pista", "pon la anterior", "pasa a la anterior", "anterior", "previous") -> TestIntent.MEDIA_PREVIOUS
            normalized in setOf("reanudar", "reanuda", "reproducir", "reproduce", "play", "continuar musica", "pon la musica") -> TestIntent.MEDIA_PLAY
            normalized in setOf("pausa", "pausar", "pon pausa", "detener", "detener musica", "para la musica") -> TestIntent.MEDIA_PAUSE
            else -> TestIntent.UNKNOWN
        }
    }
}
