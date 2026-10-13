package com.gust4av033.appcopilotomoto

/** Reacquires the headset after TTS/STT; only the current conversation may open a mic. */
internal class FollowUpCaptureGate(
    private val background: (() -> Unit) -> Unit,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val now: () -> Long
) {
    @Volatile private var sequence = 0L

    fun prepare(isCurrent: () -> Boolean, selectRoute: () -> Result<Unit>, routeReady: () -> Boolean,
                onReady: () -> Unit, onFailure: (String) -> Unit) {
        val id = ++sequence
        fun valid() = id == sequence && isCurrent()
        fun fail(message: String) {
            if (!valid()) return
            sequence++
            onFailure(message)
        }
        // Drain the last spoken audio before reselecting the communication device.
        schedule(300L) {
            if (!valid()) return@schedule
            background work@ {
                if (id != sequence) return@work
                val selected = runCatching { selectRoute().getOrThrow() }
                schedule(0L) selected@ {
                    if (!valid()) return@selected
                    if (selected.isFailure) {
                        fail("No se pudo preparar el casco: ${selected.exceptionOrNull()?.message}")
                        return@selected
                    }
                    val deadline = now() + 4_000L
                    var observedReady = false
                    fun poll() {
                        if (!valid()) return
                        if (now() >= deadline) {
                            fail("La ruta Bluetooth no llegó a estar lista para la siguiente respuesta.")
                        } else if (runCatching { routeReady() }.getOrDefault(false)) {
                            if (observedReady) {
                                sequence++
                                onReady()
                            } else {
                                observedReady = true
                                schedule(250L) { poll() }
                            }
                        } else {
                            observedReady = false
                            schedule(250L) { poll() }
                        }
                    }
                    poll()
                }
            }
        }
    }

    fun cancel() { sequence++ }
}
