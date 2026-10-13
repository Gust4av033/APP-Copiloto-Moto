package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class ContactChatCommandTest {
    @Test fun aliasesResolveOneContactAndExplicitProvider() {
        listOf("abre el chat de Ana López en WhatsApp", "inicia un chat con Ana López",
            "abre WhatsApp con Ana López", "abrime wasap para Ana López",
            "abre chat en Whats App con Ana López").forEach { text ->
            assertEquals(text, RoutedCommand(TestIntent.OPEN_CONTACT_CHAT, contactName = "ana lopez", chatApp = ChatApp.WHATSAPP),
                MinimalIntentRouter.route(text))
        }
    }
    @Test fun businessIsNotSilentlyReplacedByRegularWhatsApp() {
        listOf("abre el chat de José en WhatsApp Business", "abre WhatsApp Business con José",
            "inicia chat por WhatsApp Business de José").forEach { text ->
            val command = MinimalIntentRouter.route(text)
            assertEquals(text, TestIntent.OPEN_CONTACT_CHAT, command.intent)
            assertEquals(text, ChatApp.WHATSAPP_BUSINESS, command.chatApp)
            assertEquals(text, "jose", command.contactName)
        }
    }
    @Test fun ordinaryAppCallsAndMusicKeepTheirMeaning() {
        assertEquals(RoutedCommand(TestIntent.OPEN_APP, appName = "whatsapp"), MinimalIntentRouter.route("abre WhatsApp"))
        assertEquals(TestIntent.CALL_NAMED, MinimalIntentRouter.resolve("llama a Ana"))
        assertEquals(TestIntent.MEDIA_SEARCH, MinimalIntentRouter.resolve("pon WhatsApp"))
        assertEquals(TestIntent.CANCEL, MinimalIntentRouter.resolve("cancela"))
    }
    @Test fun incompleteUnsupportedAndCompoundRequestsExecuteNothing() {
        listOf("abre el chat", "abre WhatsApp con", "abre el chat de 12345", "abre el chat de Ana en Telegram",
            "abre el chat de Ana y llama a José", "abre WhatsApp con Ana y envía hola",
            "manda un WhatsApp a", "abre el chat de a").forEach { text ->
            assertEquals(text, TestIntent.UNKNOWN, MinimalIntentRouter.resolve(text))
        }
    }
}

