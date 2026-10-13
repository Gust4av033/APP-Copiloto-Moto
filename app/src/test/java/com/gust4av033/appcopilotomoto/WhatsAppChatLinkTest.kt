package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class WhatsAppChatLinkTest {
    @Test fun draftIsEncodedAsOneTextValueWithoutSendAction() {
        val text = "¡Hola! José + Ana & tú? #1 🙂"
        val link = WhatsAppChatLink.fromInternationalNumber("+50370000001", text)
        val uri = java.net.URI(link)
        assertEquals("wa.me", uri.host)
        assertEquals("/50370000001", uri.path)
        assertNull(uri.fragment)
        assertEquals(text, java.net.URLDecoder.decode(uri.rawQuery.removePrefix("text="), "UTF-8"))
        assertFalse(uri.rawQuery.contains("&"))
        assertTrue(link.contains("%20"))
    }
    @Test fun draftPayloadCannotAddQueryParametersOrAnotherRecipient() {
        val link = WhatsAppChatLink.fromInternationalNumber("+50370000001", "hola&phone=999&send=true")
        assertEquals(1, java.net.URI(link).rawQuery.split("&").size)
        assertEquals("/50370000001", java.net.URI(link).path)
        assertThrows(IllegalArgumentException::class.java) { WhatsAppChatLink.fromInternationalNumber("+50370000001", " ") }
    }
    @Test fun internationalNumbersProduceOnlyANumberWithoutMessageOrSendAction() {
        assertEquals("https://wa.me/50370000001", WhatsAppChatLink.fromInternationalNumber("+50370000001"))
        assertEquals("https://wa.me/34600111222", WhatsAppChatLink.fromInternationalNumber("+34600111222"))
        assertEquals("https://wa.me/123456789012345", WhatsAppChatLink.fromInternationalNumber("+123456789012345"))
    }
    @Test fun localInvalidAndInjectedNumbersAreRejected() {
        listOf("70000001", "+070000001", "+123", "+1234567890123456", "+503 70000001",
            "+50370000001?text=hola", "+50370000001/", "++50370000001", "+٥٠٣٧٠٠٠٠٠٠١").forEach { number ->
            assertThrows(IllegalArgumentException::class.java) { WhatsAppChatLink.fromInternationalNumber(number) }
        }
    }
}
