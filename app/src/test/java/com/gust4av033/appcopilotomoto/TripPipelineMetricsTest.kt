package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPipelineMetricsTest {
    @Test fun `enviar una accion no confirma su efecto externo`() {
        val metrics = TripPipelineMetrics().record(PipelineEvent.ACTION_DISPATCHED)
        assertEquals(1, metrics.count(PipelineEvent.ACTION_DISPATCHED))
        assertEquals(0, metrics.count(PipelineEvent.ACTION_CONFIRMED_BY_READBACK))
        assertEquals(0, metrics.count(PipelineEvent.EXTERNAL_EFFECT_ANNOTATED))
    }

    @Test fun `intentos observados y falsos positivos no alteran detecciones`() {
        val metrics = TripPipelineMetrics().record(PipelineEvent.ACTIVATION_ATTEMPT_ANNOTATED)
            .record(PipelineEvent.FALSE_POSITIVE_ANNOTATED)
        assertEquals(0, metrics.count(PipelineEvent.WAKE_DETECTION))
        assertEquals(1, metrics.count(PipelineEvent.FALSE_POSITIVE_ANNOTATED))
    }

    @Test fun `cancelacion no se suma como reconocimiento fallido`() {
        val metrics = TripPipelineMetrics().record(PipelineEvent.STT_STARTED).record(PipelineEvent.STT_CANCELLED)
        assertEquals(1, metrics.count(PipelineEvent.STT_STARTED))
        assertEquals(0, metrics.count(PipelineEvent.STT_FAILURE))
        assertTrue(metrics.report().contains("stt_cancelled=1"))
    }
}
