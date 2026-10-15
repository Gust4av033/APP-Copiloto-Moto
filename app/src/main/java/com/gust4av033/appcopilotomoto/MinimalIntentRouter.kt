package com.gust4av033.appcopilotomoto

import java.text.Normalizer
import java.util.Locale

enum class TestIntent { MEDIA_NEXT, MEDIA_PREVIOUS, MEDIA_PLAY, MEDIA_PAUSE, MEDIA_SEARCH, VOLUME_UP, VOLUME_DOWN, VOLUME_SET, OPEN_APP, OPEN_CONTACT_CHAT, DRAFT_CONTACT_MESSAGE, CALL_SELECTED, CALL_NAMED, CONFIRM_CALL, STOP_TTS, CANCEL, STOP_SESSION, SWITCH_MODE, OPEN_ASSISTANT, NAVIGATE, SEARCH_PLACE, NEXT_TURN, NAVIGATION_REQUEST_STATUS, UNKNOWN }

data class VolumeRequest(val amount: Int, val percent: Boolean)
data class RoutedCommand(
    val intent: TestIntent,
    val volume: VolumeRequest? = null,
    val contactName: String? = null,
    val appName: String? = null,
    val musicQuery: String? = null,
    val chatApp: ChatApp? = null,
    val audioMode: WakeAudioMode? = null,
    val messageText: String? = null,
    val navigationRequest: NavigationRequest? = null,
    val placeQuery: String? = null,
    val musicTitleInEnglish: Boolean = false,
    val navigationProvider: NavigationApp? = null
)

/** Las órdenes claras pasan por reglas locales; una frase desconocida no ejecuta nada. */
object MinimalIntentRouter {
    fun normalize(transcript: String): String =
        Normalizer.normalize(transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    private fun commandText(transcript: String): String = normalize(transcript)
        .replace(Regex("^(?:por favor |puedes |podrias |quiero que |please |can you |could you )+"), "")
        .replace(Regex("(?: por favor| gracias| please| thanks)+$"), "")
        .trim()

    /** Porcentaje explícito (0-100) o índice del control de Android (0-15). */
    fun requestedVolume(transcript: String): VolumeRequest? {
        val text = commandText(transcript)
        val percent = transcript.contains('%') || text.endsWith(" por ciento") || text.endsWith(" porciento") || text.endsWith(" percent")
        val withoutPercent = text.removeSuffix(" por ciento").removeSuffix(" porciento").removeSuffix(" percent")
        val number = Regex("^(?:(?:pon|pone|poner|ajusta|ajustar|fija|establece|set|adjust) )?(?:(?:el|the) )?(?:volumen|volume)(?: (?:a|al|en|to))? ([a-z0-9 ]+)$")
            .matchEntire(withoutPercent)?.groupValues?.get(1)?.trim() ?: return null
        val words = mapOf(
            "cero" to 0, "uno" to 1, "un" to 1, "dos" to 2, "tres" to 3,
            "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7,
            "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11,
            "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
            "veinte" to 20, "treinta" to 30, "cuarenta" to 40,
            "cincuenta" to 50, "sesenta" to 60, "setenta" to 70,
            "ochenta" to 80, "noventa" to 90, "cien" to 100, "ciento" to 100,
            "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
            "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
            "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
            "seventy" to 70, "eighty" to 80, "ninety" to 90, "one hundred" to 100
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
        val prefix = listOf("llama a ", "llamar a ", "llamale a ", "marcale a ", "haz una llamada a ", "hacer una llamada a ", "call ")
            .firstOrNull { spoken.startsWith(it) } ?: return null
        return spoken.removePrefix(prefix).takeIf { it.length >= 2 && it.any(Char::isLetter) }
    }

    fun requestedApp(transcript: String): String? {
        val spoken = commandText(transcript)
        if (Regex("^(?:abre|abrir|abrime|inicia|iniciar) (?:(?:el|un) )?chat(?: |$)").containsMatchIn(spoken) ||
            Regex("^(?:abre|abrir|abrime) (?:whatsapp|whats app|wasap|watsap)(?: business)? (?:con|de|para)(?: |$)").containsMatchIn(spoken)) return null
        val prefix = listOf("abre la aplicacion de ", "abre la aplicacion ",
            "abre la app de ", "abre la app ", "abrir la aplicacion ",
            "abre ", "abrir ", "abrime ", "inicia ", "iniciar ", "lanza ", "lanzar ", "open ", "launch ")
            .firstOrNull { spoken.startsWith(it) } ?: return null
        return spoken.removePrefix(prefix).takeIf { it.length >= 2 && it.any(Char::isLetter) }
    }

    /** Conserva el título original; las reglas de control tienen prioridad sobre la búsqueda. */
    fun requestedSong(transcript: String): String? {
        val spoken = transcript.trim()
            .replace(Regex("^(?:por favor[,\\s]+|puedes |podrias |podrías |quiero que |please |can you |could you )+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[,\\s]+(?:por favor|gracias|please|thanks)[.!?]*$", RegexOption.IGNORE_CASE), "")
        val match = Regex("^(?:pon|pone|poné|poneme|ponme|reproduce|reproducir|reproduci|reproducí|play|busca (?:la )?canci[oó]n|buscar (?:la )?canci[oó]n|search (?:for )?(?:the )?song)\\s+(.+)$",
            RegexOption.IGNORE_CASE).matchEntire(spoken) ?: return null
        val title = match.groupValues[1]
            .replace(Regex("^(?:(?:la|the) )?(?:cancion|canción|pista|song|track)\\s+", RegexOption.IGNORE_CASE), "")
            .trim().trim('"', '“', '”')
        val normalized = normalize(title)
        val reserved = setOf("musica", "la musica", "pausa", "la pausa", "cancion", "la cancion", "pista", "la pista",
            "siguiente", "la siguiente", "siguiente cancion", "anterior", "la anterior", "anterior cancion",
            "cancelar", "silencio", "detener sesion")
        if (normalize(match.groupValues[1]).startsWith("modo ") || normalized in reserved || normalized.startsWith("volumen") || normalized.startsWith("el volumen") ||
            normalized.startsWith("volume") || normalized.startsWith("the volume") || normalized.startsWith("la musica ") ||
            Regex("\\b(?:y|and) (?:llama|abre|sube|baja|cancela|deten|call|open|play|pause|stop)\\b").containsMatchIn(normalized)) return null
        return title.takeIf { it.length in 2..200 && it.any(Char::isLetterOrDigit) }
    }

    fun route(transcript: String): RoutedCommand {
        val normalized = commandText(transcript)
        val englishTitle = EnglishSongRequest.matches(normalized)
        val placeSearch = PlaceSearchParser.parseRequest(transcript)
        val placeQuery = placeSearch?.query
        val navigation = NavigationCommandParser.parse(normalized)
        val mode = TripModeCommands.parse(normalized)
        val volume = requestedVolume(transcript)
        val contact = requestedContact(transcript)
        val app = requestedApp(transcript)
        val song = requestedSong(transcript)
        val chat = ContactChatParser.parse(normalized)
        val draft = ChatDraftParser.parse(transcript)
        val intent = when {
            normalized in setOf("cual es el proximo giro", "proximo giro", "que indica waze", "que dice waze", "next turn", "what is the next turn") -> TestIntent.NEXT_TURN
            normalized in setOf("que destino pedi", "que destino solicite", "que ruta solicite",
                "que busqueda hice", "cual fue mi ultima solicitud de ruta", "a donde voy") -> TestIntent.NAVIGATION_REQUEST_STATUS
            mode != null -> TestIntent.SWITCH_MODE
            navigation != null -> TestIntent.NAVIGATE
            placeQuery != null -> TestIntent.SEARCH_PLACE
            normalized in setOf("abre asistente", "abrir asistente", "abre el asistente", "abrir el asistente",
                "abre copiloto", "abrir copiloto", "abre app copiloto") -> TestIntent.OPEN_ASSISTANT
            normalized in setOf("detener sesion", "terminar sesion", "finalizar sesion", "terminar viaje", "detener viaje", "stop trip", "end trip") -> TestIntent.STOP_SESSION
            normalized in setOf("callate", "silencio", "deja de hablar", "para de hablar", "quiet", "stop talking") -> TestIntent.STOP_TTS
            normalized in setOf("cancelar", "cancela", "olvidalo",
                "cancelar cancelar", "cancela cancela", "cancelar cancela", "cancela cancelar", "cancel", "never mind") -> TestIntent.CANCEL
            normalized in setOf("confirmar llamada", "confirma llamada", "confirmo la llamada", "confirmar la llamada", "llamar ahora", "confirm call") -> TestIntent.CONFIRM_CALL
            normalized in setOf("llamar", "hacer llamada", "llamar al contacto", "llama al contacto") -> TestIntent.CALL_SELECTED
            contact != null -> TestIntent.CALL_NAMED
            volume != null -> TestIntent.VOLUME_SET
            draft != null -> TestIntent.DRAFT_CONTACT_MESSAGE
            chat != null -> TestIntent.OPEN_CONTACT_CHAT
            app != null -> TestIntent.OPEN_APP
            normalized in setOf("subir volumen", "sube volumen", "sube el volumen", "subir el volumen", "mas volumen", "volumen mas alto", "aumenta el volumen", "aumentar volumen", "volume up", "turn up the volume", "increase volume") -> TestIntent.VOLUME_UP
            normalized in setOf("bajar volumen", "baja volumen", "baja el volumen", "bajar el volumen", "menos volumen", "volumen mas bajo", "reduce el volumen", "disminuye el volumen", "volume down", "turn down the volume", "decrease volume") -> TestIntent.VOLUME_DOWN
            normalized in setOf("siguiente cancion", "siguiente pista", "pon la siguiente", "pasa a la siguiente", "salta la cancion", "siguiente", "next", "next song", "next track", "play next song", "cambia a la siguiente cancion", "pasa de cancion", "pon siguiente cancion", "pon la siguiente cancion") -> TestIntent.MEDIA_NEXT
            normalized in setOf("anterior cancion", "cancion anterior", "pista anterior", "anterior pista", "pon la anterior", "pasa a la anterior", "anterior", "previous", "previous song", "previous track", "play previous song", "regresa a la cancion anterior", "pon anterior cancion", "pon la cancion anterior") -> TestIntent.MEDIA_PREVIOUS
            normalized in setOf("reanudar", "reanuda", "reproducir", "reproduce", "play", "play music", "continue music", "resume music", "continuar musica", "pon la musica", "sigue la musica", "continua la musica") -> TestIntent.MEDIA_PLAY
            normalized in setOf("pausa", "pausar", "pause", "pause music", "pause the music", "stop music", "pon pausa", "detener", "detener musica", "para la musica", "pausa la musica", "pon la musica en pausa") -> TestIntent.MEDIA_PAUSE
            englishTitle || song != null -> TestIntent.MEDIA_SEARCH
            else -> TestIntent.UNKNOWN
        }
        return RoutedCommand(intent, if (intent == TestIntent.VOLUME_SET) volume else null,
            if (intent == TestIntent.CALL_NAMED) contact else if (intent == TestIntent.OPEN_CONTACT_CHAT) chat?.contactName
                else if (intent == TestIntent.DRAFT_CONTACT_MESSAGE) draft?.recipient?.contactName else null,
            if (intent == TestIntent.OPEN_APP) app else null,
            if (intent == TestIntent.MEDIA_SEARCH && !englishTitle) song else null,
            if (intent == TestIntent.OPEN_CONTACT_CHAT) chat?.app else if (intent == TestIntent.DRAFT_CONTACT_MESSAGE) draft?.recipient?.app else null,
            if (intent == TestIntent.SWITCH_MODE) mode else null,
            if (intent == TestIntent.DRAFT_CONTACT_MESSAGE) draft?.text else null,
            if (intent == TestIntent.NAVIGATE) navigation else null,
            if (intent == TestIntent.SEARCH_PLACE) placeQuery else null,
            intent == TestIntent.MEDIA_SEARCH && englishTitle,
            if (intent == TestIntent.SEARCH_PLACE) placeSearch?.provider
                else if (intent == TestIntent.NAVIGATE) NavigationCommandParser.explicitProvider(normalized)
                else null)
    }

    fun resolve(transcript: String): TestIntent = route(transcript).intent
}


