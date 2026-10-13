package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class DraftChoiceRouterTest {
    @Test fun revisionChoicesAreScopedToDraftReview() {
        for (phrase in listOf("tres", "3", "corregir mensaje", "dictar de nuevo")) {
            assertEquals(AppChoice.REDICTATE, DraftChoiceRouter.resolve(phrase))
            assertEquals(AppChoice.UNKNOWN, AppChoiceRouter.resolve(phrase))
            assertEquals(TestIntent.UNKNOWN, MinimalIntentRouter.resolve(phrase))
        }
        for (phrase in listOf("cuatro", "4", "repetir mensaje", "lee de nuevo")) {
            assertEquals(AppChoice.REPEAT, DraftChoiceRouter.resolve(phrase))
            assertEquals(AppChoice.UNKNOWN, AppChoiceRouter.resolve(phrase))
        }
    }

    @Test fun openCancelAndAmbiguityRemainExplicit() {
        assertEquals(AppChoice.OPEN, DraftChoiceRouter.resolve("uno"))
        assertEquals(AppChoice.CANCEL, DraftChoiceRouter.resolve("dos"))
        for (phrase in listOf(null, "", "uno o tres", "no corrijas", "tres cuatro", "repite y abre"))
            assertEquals(AppChoice.UNKNOWN, DraftChoiceRouter.resolve(phrase))
    }

    @Test fun revisionWordsInsideMessageRemainText() {
        val message = "Tres personas llegarán. Repite el mensaje a Juan."
        assertEquals(message, ChatDraftParser.validateText(message))
        assertEquals(message, ChatDraftParser.parse("escribe a Ana en WhatsApp mensaje $message")?.text)
    }
}
