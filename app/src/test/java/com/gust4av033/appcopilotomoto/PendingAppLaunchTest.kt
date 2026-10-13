package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class PendingAppLaunchTest {
    @Test fun slowReadbackDoesNotConsumeConfirmationWindow() {
        val pending = PendingAppLaunch<String>()
        val text = "mensaje largo ".repeat(18)
        val initial = VoiceInteractionTiming.pendingAppMs("Ana", text)
        pending.prepare("WhatsApp", "draft", 100, initial)
        assertFalse(pending.expire("draft", 60_100))
        val ready = pending.renew("draft", 65_100, PendingAppLaunch.DRAFT_VALIDITY_MS)
        assertEquals(125_100L, ready?.expiresAtMs)
        // Un callback del plazo anterior no puede vencer el plazo renovado.
        assertFalse(pending.expire("draft", 80_100))
        assertTrue(pending.expire("draft", 125_100))
    }

    @Test fun renewalRejectsReplacedCancelledAndExpiredRequests() {
        val pending = PendingAppLaunch<String>()
        pending.prepare("Mapas", "old", 0)
        pending.prepare("Música", "new", 100)
        assertNull(pending.renew("old", 200, 30_000))
        assertNull(pending.renew("new", 30_100, 30_000))
        assertTrue(pending.clear("new"))
        assertNull(pending.renew("new", 200, 30_000))
    }

    @Test fun draftHasTimeForReadbackButStillExpiresAndRejectsOldToken() {
        val pending = PendingAppLaunch<String>()
        pending.prepare("Borrador", "draft", 100, PendingAppLaunch.DRAFT_VALIDITY_MS)
        assertFalse(pending.expire("draft", 30_100))
        assertFalse(pending.expire("draft", 60_099))
        assertTrue(pending.expire("draft", 60_100))
        assertNull(pending.current)
        assertFalse(pending.clear("draft"))
    }
    @Test fun replacingAnAppIgnoresItsOldCancelAndDeadline() {
        val pending = PendingAppLaunch<String>()
        pending.prepare("Música", "first", 0)
        pending.prepare("Mapas", "second", 10_000)
        assertFalse(pending.clear("first"))
        assertFalse(pending.expire("first", 30_000))
        assertEquals("Mapas", pending.current?.target)
    }

    @Test fun appExpiresAtThirtySecondsAndCannotBeCancelledTwice() {
        val pending = PendingAppLaunch<String>()
        pending.prepare("Música", "request", 100)
        assertFalse(pending.expire("request", 30_099))
        assertTrue(pending.expire("request", 30_100))
        assertNull(pending.current)
        assertFalse(pending.clear("request"))
    }

    @Test fun dismissingOrCancellingTheCurrentRequestClearsIt() {
        val pending = PendingAppLaunch<String>()
        pending.prepare("Música", "request", 100)
        assertTrue(pending.clear("request"))
        assertNull(pending.current)
    }

    @Test fun newVoiceCommandCanClearTheCurrentAppWithoutItsToken() {
        val pending = PendingAppLaunch<String>()
        pending.prepare("Música", "request", 100)
        assertTrue(pending.clear())
        assertNull(pending.current)
        assertFalse(pending.expire("request", 30_100))
    }
}
