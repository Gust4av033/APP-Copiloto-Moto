package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Test

class TripButtonPolicyTest {
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
