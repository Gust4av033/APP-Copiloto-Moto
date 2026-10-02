package com.gust4av033.appcopilotomoto

data class ChatDraftCommand(val recipient: ContactChatCommand, val text: String? = null)

/** Destino y contenido se interpretan por separado: el mensaje nunca se ejecuta como orden. */
internal object ChatDraftParser {
    const val MAX_LENGTH = 240

    fun parse(transcript: String): ChatDraftCommand? {
        val spoken = transcript.trim().replace(Regex("^(?:por favor[,\\s]+|puedes |podrias |podrías )+", RegexOption.IGNORE_CASE), "")
        val match = Regex("^(?:escribe|escribir|dicta|dictar|prepara|preparar) (?:(?:un )?mensaje )?(?:a|para) (.+?) (?:en|por) (whatsapp|whats app|wasap|watsap)( business)?(?:[,\\s]+(?:mensaje|que diga|diciendo)[,:\\s]+(.+))?[.!?]*$",
            RegexOption.IGNORE_CASE).matchEntire(spoken) ?: return null
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
