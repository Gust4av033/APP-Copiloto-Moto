package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class DraftLinkRevisionTest {
    @Test fun replacingDraftPreservesRecipientAndRemovesOldContent() {
        val original = WhatsAppChatLink.fromInternationalNumber("+50371234567", "Mensaje viejo & Hola")
        val base = WhatsAppChatLink.withoutDraft(original)
        assertEquals("https://wa.me/50371234567", base)
        val replaced = WhatsAppChatLink.fromInternationalNumber("+" + base.substringAfterLast('/'), "Mensaje nuevo")
        assertTrue(replaced.endsWith("?text=Mensaje%20nuevo"))
        assertFalse(replaced.contains("viejo"))
        assertEquals(1, replaced.count { it == '?' })
    }

    @Test fun unsupportedLinksCannotBeReusedForRevision() {
        for (link in listOf("https://evil.example/50371234567?text=hola", "http://wa.me/50371234567?text=hola",
            "https://wa.me/50371234567?text=hola&other=1", "https://wa.me/50371234567#x",
            "https://wa.me/abc?text=hola")) assertTrue(runCatching { WhatsAppChatLink.withoutDraft(link) }.isFailure)
    }
}
