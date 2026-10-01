package com.gust4av033.appcopilotomoto

/** Solo admite un número internacional validado. No agrega texto ni una acción de envío. */
internal object WhatsAppChatLink {
    fun fromInternationalNumber(number: String): String {
        require(number.matches(Regex("\\+[1-9][0-9]{6,14}"))) {
            "Guardá el contacto con código de país, por ejemplo +503 seguido del número."
        }
        return "https://wa.me/${number.removePrefix("+")}"
    }
}
