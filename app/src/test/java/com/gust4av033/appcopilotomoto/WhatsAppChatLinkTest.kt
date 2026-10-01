package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class WhatsAppChatLinkTest {
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
