package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class InformationalSpeechCycleTest {
    private class FakeAudio : InformationalAudioPort {
        var duckRequests = 0
        var releases = 0
        var stops = 0
        var spoken = mutableListOf<String>()
        var finish: ((Boolean) -> Unit)? = null
        override fun requestDucking(): Boolean { duckRequests++; return true }
        override fun speak(text: String, onFinished: (Boolean) -> Unit) { spoken += text; finish = onFinished }
        override fun stop() { stops++ }
        override fun releaseDucking() { releases++ }
    }
    private class FakeTimer : InformationalSpeechTimer {
        var delay = 0L
        var action: (() -> Unit)? = null
        override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
            delay = delayMs
            this.action = action
            return { this.action = null }
        }
        fun fire() { action?.invoke() }
    }

    @Test fun duckingIsHeldForOneInformationalUtteranceWithoutPauseOrCapture() {
        val audio = FakeAudio(); val timer = FakeTimer()
        val cycle = InformationalSpeechCycle(audio, timer)
        assertTrue(cycle.speak("Copiloto listo."))
        assertFalse(cycle.speak("Viaje finalizado."))
        assertEquals(1, audio.duckRequests)
        assertEquals(listOf("Copiloto listo."), audio.spoken)
        // The port has no pausePlayback or beginCapture method; the only side effect is ducking.
        audio.finish?.invoke(true)
        assertFalse(cycle.isSpeaking)
        assertEquals(1, audio.releases)
        assertEquals(0, audio.stops)
        assertNull(timer.action)
    }

    @Test fun timeoutStopsSpeechAndLateCompletionCannotReleaseTwice() {
        val audio = FakeAudio(); val timer = FakeTimer()
        val cycle = InformationalSpeechCycle(audio, timer)
        val finished = mutableListOf<Boolean>()
        assertTrue(cycle.speak("Viaje finalizado.") { finished += it })
        val late = audio.finish
        assertEquals(6_000L, timer.delay)
        timer.fire()
        late?.invoke(true)
        assertEquals(listOf(false), finished)
        assertEquals(1, audio.stops)
        assertEquals(1, audio.releases)
    }
}
