package com.gust4av033.appcopilotomoto

data class ChatDraftCommand(val recipient: ContactChatCommand, val text: String? = null)

/** Destino y contenido se interpretan por separado: el mensaje nunca se ejecuta como orden. */
internal object ChatDraftParser {
    const val MAX_LENGTH = 240

    fun parse(transcript: String): ChatDraftCommand? {
        val spoken = transcript.trim().replace(Regex("^(?:por favor[,\\s]+|puedes |podrias |podrías )+", RegexOption.IGNORE_CASE), "")
        // 'Enviar/mandar' prepares the same reviewable draft; it never presses Send.
        val verbs = "(?:escribe|escribir|dicta|dictar|prepara|preparar|manda|mandar|envia|envía|enviar)"
        val app = "(whatsapp|whats app|wasap|watsap)( business)?"
        val content = "(?:[,\\s]+(?:mensaje|que diga|diciendo)[,:\\s]+(.+))?[.!?]*$"
        val appFirst = Regex("^$verbs (?:(?:un )?mensaje (?:en|por) |(?:un )?)$app (?:a|para) (.+?)$content",
            RegexOption.IGNORE_CASE).matchEntire(spoken)
        val canonical = if (appFirst != null) {
            "prepara mensaje a ${appFirst.groupValues[3]} en ${appFirst.groupValues[1]}${appFirst.groupValues[2]}" +
                appFirst.groupValues[4].takeIf { it.isNotBlank() }?.let { " que diga $it" }.orEmpty()
        } else spoken
        val match = Regex("^$verbs (?:(?:un )?mensaje )?(?:a|para) (.+?) (?:en|por) $app$content",
            RegexOption.IGNORE_CASE).matchEntire(canonical) ?: return null
        val destination = MinimalIntentRouter.normalize("abre el chat de ${match.groupValues[1]} en ${match.groupValues[2]}${match.groupValues[3]}")
        val recipient = ContactChatParser.parse(destination) ?: return null
        val raw = match.groupValues[4].takeIf { it.isNotEmpty() }
        val text = raw?.let { runCatching { validateText(it) }.getOrNull() ?: return null }
        return ChatDraftCommand(recipient, text)
    }

    fun validateText(text: String): String {
        val value = text.trim()
        require(value.length in 1..MAX_LENGTH && value.any(Char::isLetterOrDigit) &&
            value.none { it.isISOControl() && it != '\n' && it != '\t' }) {
            "Dictá un mensaje breve, de hasta $MAX_LENGTH caracteres."
        }
        return value
    }
}

