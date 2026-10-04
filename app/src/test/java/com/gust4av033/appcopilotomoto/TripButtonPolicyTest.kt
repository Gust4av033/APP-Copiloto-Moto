package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Test

class TripButtonPolicyTest {
    @Test fun `diez pulsaciones bluetooth con downTime cero activan diez ordenes`() {
        val policy = TripButtonPolicy()
        repeat(10) {
            assertEquals("pulsación ${it + 1}", ButtonDecision.ACTIVATE,
                policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 0, -1))
            assertEquals(ButtonDecision.CONSUME,
                policy.decide(TripButtonBinding.PREVIOUS, true, false, 88, false, 0, 0, -1))
        }
    }

    @Test fun `soltar permite una nueva pulsacion aunque el timestamp positivo se repita`() {
        val policy = TripButtonPolicy()
        repeat(3) {
            assertEquals(ButtonDecision.ACTIVATE,
                policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 42, 1))
            policy.decide(TripButtonBinding.PREVIOUS, true, false, 88, false, 0, 42, 1)
        }
    }

    @Test fun `up perdido con timestamp cero no bloquea la siguiente orden`() {
        val policy = TripButtonPolicy()
        assertEquals(ButtonDecision.ACTIVATE,
            policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 0, -1, 100))
        assertEquals(ButtonDecision.CONSUME,
            policy.decide(TripButtonBinding.PREVIOUS, true, false, 88, true, 0, 0, -1, 110))
        assertEquals(ButtonDecision.ACTIVATE,
            policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 0, -1, 5_000))
    }

    @Test fun `mantener pulsado no activa al volver a estar listo`() {
        val policy = TripButtonPolicy()
        policy.decide(TripButtonBinding.PREVIOUS, true, false, 88, true, 0, 0, -1, 0)
        repeat(5) {
            assertEquals(ButtonDecision.CONSUME,
                policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 1, 0, -1, (it + 1) * 1_000L))
        }
        assertEquals(ButtonDecision.CONSUME,
            policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 0, -1, 5_100))
        policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, false, 0, 0, -1, 5_200)
        assertEquals(ButtonDecision.ACTIVATE,
            policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 0, -1, 5_300))
    }

    @Test fun `up atrasado no libera el down mas reciente`() {
        val policy = TripButtonPolicy()
        policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 42, 1, 100)
        policy.decide(TripButtonBinding.PREVIOUS, true, false, 88, true, 0, 43, 1, 110)
        policy.decide(TripButtonBinding.PREVIOUS, true, false, 88, false, 0, 42, 1, 120)
        assertEquals(ButtonDecision.CONSUME,
            policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 43, 1, 130))
    }

    @Test fun `una pulsacion ocupada con tiempo cero se descarta y la siguiente sirve`() {
        val policy = TripButtonPolicy()
        assertEquals(ButtonDecision.CONSUME,
            policy.decide(TripButtonBinding.PREVIOUS, true, false, 88, true, 0, 0, -1, 100))
        assertEquals(ButtonDecision.CONSUME,
            policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 0, -1, 110))
        policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, false, 0, 0, -1, 120)
        assertEquals(ButtonDecision.ACTIVATE,
            policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 0, -1, 130))
    }

    @Test fun `fuera de viaje y desactivado conservan el control original`() {
        val policy = TripButtonPolicy()
        assertEquals(ButtonDecision.PASS, policy.decide(TripButtonBinding.PREVIOUS, false, true, 88, true, 0, 1, -1))
        assertEquals(ButtonDecision.PASS, policy.decide(TripButtonBinding.NONE, true, true, 88, true, 0, 1, -1))
    }

    @Test fun `un down activa y su up no abre otra captura`() {
        val policy = TripButtonPolicy()
        assertEquals(ButtonDecision.ACTIVATE, policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 42, -1))
        assertEquals(ButtonDecision.CONSUME, policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, false, 0, 42, -1))
    }

    @Test fun `repeticiones y entrega duplicada activity session no reactivan`() {
        val policy = TripButtonPolicy()
        assertEquals(ButtonDecision.ACTIVATE, policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 42, -1))
        assertEquals(ButtonDecision.CONSUME, policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 42, -1))
        assertEquals(ButtonDecision.CONSUME, policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 1, 42, -1))
        assertEquals(ButtonDecision.ACTIVATE, policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 43, -1))
    }

    @Test fun `orden ocupada consume solo boton dedicado sin ejecutarlo mas tarde`() {
        val policy = TripButtonPolicy()
        assertEquals(ButtonDecision.CONSUME, policy.decide(TripButtonBinding.PREVIOUS, true, false, 88, true, 0, 42, -1))
        assertEquals(ButtonDecision.CONSUME, policy.decide(TripButtonBinding.PREVIOUS, true, true, 88, true, 0, 42, -1))
        assertEquals(ButtonDecision.PASS, policy.decide(TripButtonBinding.PREVIOUS, true, false, 87, true, 0, 43, -1))
    }

    @Test fun `eleccion invalida se restaura a desactivado`() {
        assertEquals(TripButtonBinding.NONE, TripButtonBinding.parse("UNKNOWN"))
        assertEquals(TripButtonBinding.PREVIOUS, TripButtonBinding.parse("PREVIOUS"))
    }
}
