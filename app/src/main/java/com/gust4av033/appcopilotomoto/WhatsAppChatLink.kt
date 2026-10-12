package com.gust4av033.appcopilotomoto

import java.net.URLEncoder
import java.net.URI

/** Enlace público con borrador opcional. Nunca incluye una acción de envío. */
internal object WhatsAppChatLink {
    fun withoutDraft(link: String): String {
        val uri = URI(link)
        require(uri.scheme == "https" && uri.host == "wa.me" && uri.userInfo == null && uri.port == -1 && uri.fragment == null &&
            (uri.rawQuery == null || uri.rawQuery.matches(Regex("text=[^&]*")))) { "No es un enlace de borrador compatible." }
        return fromInternationalNumber("+${uri.path.orEmpty().removePrefix("/")}")
    }

    fun fromInternationalNumber(number: String, draft: String? = null): String {
        require(number.matches(Regex("\\+[1-9][0-9]{6,14}"))) {
            "Guardá el contacto con código de país, por ejemplo +503 seguido del número."
        }
        val base = "https://wa.me/${number.removePrefix("+")}"
        return if (draft == null) base else base + "?text=" +
            URLEncoder.encode(ChatDraftParser.validateText(draft), "UTF-8").replace("+", "%20")
    }
}
