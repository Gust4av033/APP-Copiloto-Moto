package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class ChatDraftCommandTest {
    @Test fun destinationOnlyStartsADictationRequest() {
        val routed = MinimalIntentRouter.route("Escribe a Ana López en WhatsApp")
        assertEquals(TestIntent.DRAFT_CONTACT_MESSAGE, routed.intent)
        assertEquals("ana lopez", routed.contactName)
        assertEquals(ChatApp.WHATSAPP, routed.chatApp)
        assertNull(routed.messageText)
        assertEquals(TestIntent.DRAFT_CONTACT_MESSAGE, MinimalIntentRouter.resolve("prepara un mensaje para Ana en WhatsApp."))
    }

    @Test fun inlineTextKeepsCaseAccentsAndPunctuationAndNeverExecutesIt() {
        val text = "¡Voy llegando! No llames a José, sube el volumen & abre Waze."
        val routed = MinimalIntentRouter.route("dicta un mensaje para Ana en WhatsApp Business, mensaje: $text")
        assertEquals(TestIntent.DRAFT_CONTACT_MESSAGE, routed.intent)
        assertEquals(ChatApp.WHATSAPP_BUSINESS, routed.chatApp)
        assertEquals(text, routed.messageText)
        assertNull(routed.volume)
        assertNull(routed.appName)
    }

    @Test fun malformedOrUnsupportedDestinationsDoNothing() {
        listOf("escribe a Ana", "escribe a Ana en Telegram", "escribe a 12345 en WhatsApp",
            "escribe a Ana y llama a José en WhatsApp", "escribe a Ana en WhatsApp mensaje",
            "escribe a Ana en WhatsApp mensaje " + "x".repeat(241))
            .forEach { assertEquals(it, TestIntent.UNKNOWN, MinimalIntentRouter.resolve(it)) }
    }

    @Test fun emptyControlAndOversizedDraftsAreRejectedWithoutTruncating() {
        listOf(" ", "!!!", "hola\u0000", "x".repeat(241)).forEach {
            assertThrows(IllegalArgumentException::class.java) { ChatDraftParser.validateText(it) }
        }
        assertEquals("Ánimo 🙂", ChatDraftParser.validateText("  Ánimo 🙂  "))
        assertEquals(240, ChatDraftParser.validateText("x".repeat(240)).length)
    }

    @Test fun sendingPhrasesPrepareADraftForReviewRatherThanSending() {
        listOf("envía un mensaje a Ana en WhatsApp", "manda mensaje a Ana por WhatsApp",
            "envia un mensaje por WhatsApp a Ana", "manda un WhatsApp a Ana").forEach {
            val command = MinimalIntentRouter.route(it)
            assertEquals(it, TestIntent.DRAFT_CONTACT_MESSAGE, command.intent)
            assertEquals("ana", command.contactName)
            assertEquals(ChatApp.WHATSAPP, command.chatApp)
            assertNull(command.messageText)
        }
        val command = MinimalIntentRouter.route("envía un mensaje por WhatsApp Business a Ana que diga ¡Ya voy!")
        assertEquals(ChatApp.WHATSAPP_BUSINESS, command.chatApp)
        assertEquals("¡Ya voy!", command.messageText)
    }
}

