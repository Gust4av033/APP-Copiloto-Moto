package com.gust4av033.appcopilotomoto

enum class TripButtonBinding(val keyCode: Int?) {
    NONE(null), PREVIOUS(88), NEXT(87), PAUSE(127), PLAY_PAUSE(85);

    companion object {
        const val PREFERENCE = "trip_button_binding"
        fun parse(value: String?): TripButtonBinding = entries.firstOrNull { it.name == value } ?: NONE
    }
}

enum class ButtonDecision { PASS, CONSUME, ACTIVATE }

/** Sin ventanas de doble toque: una sola pulsación física y ninguna repetición automática. */
internal class TripButtonPolicy {
    private data class Press(val downTime: Long, val receivedAtMs: Long)
    private val held = mutableMapOf<Pair<Int, Int>, Press>()

    companion object {
        // Si Android pierde UP al pasar a voz, la tecla no queda bloqueada todo el viaje.
        internal const val LOST_UP_TIMEOUT_MS = 2_000L
    }

    fun decide(binding: TripButtonBinding, tripActive: Boolean, ready: Boolean,
               keyCode: Int, down: Boolean, repeats: Int, downTime: Long, deviceId: Int,
               receivedAtMs: Long = System.nanoTime() / 1_000_000L): ButtonDecision {
        if (!tripActive) { held.clear(); return ButtonDecision.PASS }
        if (binding.keyCode != keyCode) return ButtonDecision.PASS
        val key = deviceId to keyCode
        val previous = held[key]
        if (!down) {
            // Un UP atrasado con tiempo válido no libera otra pulsación más reciente.
            if (previous == null || downTime <= 0L || previous.downTime <= 0L || previous.downTime == downTime) {
                held.remove(key)
            }
            return ButtonDecision.CONSUME
        }
        if (repeats != 0) {
            held[key] = Press(downTime, receivedAtMs)
            return ButtonDecision.CONSUME
        }
        val newTimestamp = previous != null && downTime > 0L && previous.downTime > 0L && downTime > previous.downTime
        if (previous != null && !newTimestamp && receivedAtMs - previous.receivedAtMs < LOST_UP_TIMEOUT_MS) {
            held[key] = previous.copy(receivedAtMs = receivedAtMs)
            return ButtonDecision.CONSUME
        }
        // AVRCP puede entregar downTime=0 en todas las pulsaciones. UP termina el ciclo;
        // el timestamp no es un identificador que se pueda recordar entre órdenes.
        held[key] = Press(downTime, receivedAtMs)
        return if (ready) ButtonDecision.ACTIVATE else ButtonDecision.CONSUME
    }
}

/** Puente únicamente durante la vida del coordinador; también evita redispatch a nuestra sesión. */
internal object TripButtonRuntime {
    var handler: ((android.view.KeyEvent) -> Boolean)? = null
    val intercepting: Boolean get() = handler != null
}
