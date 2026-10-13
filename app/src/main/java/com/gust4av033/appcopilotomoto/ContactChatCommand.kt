package com.gust4av033.appcopilotomoto

enum class ChatApp(val packageName: String, val label: String) {
    WHATSAPP("com.whatsapp", "WhatsApp"),
    WHATSAPP_BUSINESS("com.whatsapp.w4b", "WhatsApp Business")
}

data class ContactChatCommand(val contactName: String, val app: ChatApp)
data class ContactChatCapability(val app: ChatApp, val available: Boolean)

/** Abrir un chat y enviar un mensaje son acciones distintas. Solo resuelve la apertura. */
internal object ContactChatParser {
    private const val PROVIDER = "(?:whatsapp|whats app|wasap|watsap)"
    fun parse(text: String): ContactChatCommand? {
        val direct = Regex("^(?:abre|abrir|abrime) $PROVIDER( business)? (?:con|de|para) (.+)$").matchEntire(text)
        val chat = Regex("^(?:abre|abrir|abrime|inicia|iniciar) (?:(?:el|un) )?chat (?:con|de) (.+?)(?: (?:en|por) $PROVIDER( business)?)?$").matchEntire(text)
        val firstProvider = Regex("^(?:abre|abrir|abrime|inicia|iniciar) (?:(?:el|un) )?chat (?:en|por) $PROVIDER( business)? (?:con|de|para) (.+)$").matchEntire(text)
        val name: String
        val business: Boolean
        when {
            direct != null -> { name = direct.groupValues[2].trim(); business = direct.groupValues[1].isNotEmpty() }
            firstProvider != null -> { name = firstProvider.groupValues[2].trim(); business = firstProvider.groupValues[1].isNotEmpty() }
            chat != null -> { name = chat.groupValues[1].trim(); business = chat.groupValues[2].isNotEmpty() }
            else -> return null
        }
        if (name.length !in 2..80 || !name.any(Char::isLetter) ||
            Regex("\\by (?:abre|llama|envia|manda|cancela|deten)\\b").containsMatchIn(name) ||
            Regex("\\b(?:en|por) (?:telegram|messenger|signal|whatsapp|whats app|wasap|watsap)\\b").containsMatchIn(name)) return null
        return ContactChatCommand(name, if (business) ChatApp.WHATSAPP_BUSINESS else ChatApp.WHATSAPP)
    }
}
