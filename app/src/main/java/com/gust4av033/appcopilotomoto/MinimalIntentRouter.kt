package com.gust4av033.appcopilotomoto

import java.text.Normalizer
import java.util.Locale

enum class TestIntent { MEDIA_NEXT, MEDIA_PREVIOUS, MEDIA_PLAY, MEDIA_PAUSE, VOLUME_UP, VOLUME_DOWN, VOLUME_SET, OPEN_APP, CALL_SELECTED, CALL_NAMED, CONFIRM_CALL, STOP_TTS, CANCEL, STOP_SESSION, UNKNOWN }

data class VolumeRequest(val amount: Int, val percent: Boolean)
data class RoutedCommand(
    val intent: TestIntent,
    val volume: VolumeRequest? = null,
    val contactName: String? = null,
    val appName: String? = null
)

/** Las órdenes claras pasan por reglas locales; una frase desconocida no ejecuta nada. */
object MinimalIntentRouter {
    fun normalize(transcript: String): String =
        Normalizer.normalize(transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    private fun commandText(transcript: String): String = normalize(transcript)
        .replace(Regex("^(?:por favor |puedes |podrias |quiero que )+"), "")
        .replace(Regex("(?: por favor| gracias)+$"), "")
        .trim()

    /** Porcentaje explícito (0-100) o índice del control de Android (0-15). */
    fun requestedVolume(transcript: String): VolumeRequest? {
        val text = commandText(transcript)
        val percent = transcript.contains('%') || text.endsWith(" por ciento") || text.endsWith(" porciento")
        val withoutPercent = text.removeSuffix(" por ciento").removeSuffix(" porciento")
        val number = Regex("^(?:(?:pon|pone|poner|ajusta|ajustar|fija|establece) )?(?:el )?volumen(?: (?:a|al|en))? ([a-z0-9 ]+)$")
            .matchEntire(withoutPercent)?.groupValues?.get(1)?.trim() ?: return null
        val words = mapOf(
            "cero" to 0, "uno" to 1, "un" to 1, "dos" to 2, "tres" to 3,
            "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7,
            "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11,
            "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
            "veinte" to 20, "treinta" to 30, "cuarenta" to 40,
            "cincuenta" to 50, "sesenta" to 60, "setenta" to 70,
            "ochenta" to 80, "noventa" to 90, "cien" to 100, "ciento" to 100
        )
        val value = number.toIntOrNull() ?: words[number] ?: run {
            val parts = number.split(" y ")
            val tens = words[parts.first()]
            val ones = words[parts.last()]
            if (parts.size == 2 && tens != null && tens in 20..90 && tens % 10 == 0 &&
                ones != null && ones in 1..9) {
                tens + ones
            } else if (number.startsWith("veinti")) {
                words[number.removePrefix("veinti")]?.plus(20)
            } else null
        } ?: return null
        if (value !in 0..(if (percent) 100 else 15)) return null
        return VolumeRequest(value, percent)
    }

    fun requestedContact(transcript: String): String? {
        val spoken = commandText(transcript)
        val prefix = listOf("llama a ", "llamar a ", "haz una llamada a ", "hacer una llamada a ")
            .firstOrNull { spoken.startsWith(it) } ?: return null
        return spoken.removePrefix(prefix).takeIf { it.length >= 2 && it.any(Char::isLetter) }
    }

    fun requestedApp(transcript: String): String? {
        val spoken = commandText(transcript)
        val prefix = listOf("abre la aplicacion de ", "abre la aplicacion ",
            "abre la app de ", "abre la app ", "abrir la aplicacion ",
            "abre ", "abrir ", "abrime ", "inicia ", "iniciar ", "lanza ", "lanzar ")
            .firstOrNull { spoken.startsWith(it) } ?: return null
        return spoken.removePrefix(prefix).takeIf { it.length >= 2 && it.any(Char::isLetter) }
    }

    fun route(transcript: String): RoutedCommand {
        val normalized = commandText(transcript)
        val volume = requestedVolume(transcript)
        val contact = requestedContact(transcript)
        val app = requestedApp(transcript)
        val intent = when {
            normalized in setOf("detener sesion", "terminar sesion", "finalizar sesion") -> TestIntent.STOP_SESSION
            normalized in setOf("callate", "silencio", "deja de hablar", "para de hablar") -> TestIntent.STOP_TTS
            normalized in setOf("cancelar", "cancela", "olvidalo",
                "cancelar cancelar", "cancela cancela", "cancelar cancela", "cancela cancelar") -> TestIntent.CANCEL
            normalized in setOf("confirmar llamada", "confirma llamada", "llamar ahora") -> TestIntent.CONFIRM_CALL
            normalized in setOf("llamar", "hacer llamada", "llamar al contacto", "llama al contacto") -> TestIntent.CALL_SELECTED
            contact != null -> TestIntent.CALL_NAMED
            volume != null -> TestIntent.VOLUME_SET
            app != null -> TestIntent.OPEN_APP
            normalized in setOf("subir volumen", "sube volumen", "sube el volumen", "subir el volumen", "mas volumen", "volumen mas alto", "aumenta el volumen", "aumentar volumen") -> TestIntent.VOLUME_UP
            normalized in setOf("bajar volumen", "baja volumen", "baja el volumen", "bajar el volumen", "menos volumen", "volumen mas bajo", "reduce el volumen", "disminuye el volumen") -> TestIntent.VOLUME_DOWN
            normalized in setOf("siguiente cancion", "siguiente pista", "pon la siguiente", "pasa a la siguiente", "salta la cancion", "siguiente", "next", "cambia a la siguiente cancion", "pasa de cancion") -> TestIntent.MEDIA_NEXT
            normalized in setOf("anterior cancion", "cancion anterior", "pista anterior", "anterior pista", "pon la anterior", "pasa a la anterior", "anterior", "previous", "regresa a la cancion anterior") -> TestIntent.MEDIA_PREVIOUS
            normalized in setOf("reanudar", "reanuda", "reproducir", "reproduce", "play", "continuar musica", "pon la musica", "sigue la musica", "continua la musica") -> TestIntent.MEDIA_PLAY
            normalized in setOf("pausa", "pausar", "pon pausa", "detener", "detener musica", "para la musica", "pausa la musica", "pon la musica en pausa") -> TestIntent.MEDIA_PAUSE
            else -> TestIntent.UNKNOWN
        }
        return RoutedCommand(intent, if (intent == TestIntent.VOLUME_SET) volume else null,
            if (intent == TestIntent.CALL_NAMED) contact else null,
            if (intent == TestIntent.OPEN_APP) app else null)
    }

    fun resolve(transcript: String): TestIntent = route(transcript).intent
}
