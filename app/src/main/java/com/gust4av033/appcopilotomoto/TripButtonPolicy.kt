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
    private val recent = ArrayDeque<String>()

    fun decide(binding: TripButtonBinding, tripActive: Boolean, ready: Boolean,
               keyCode: Int, down: Boolean, repeats: Int, downTime: Long, deviceId: Int): ButtonDecision {
        if (!tripActive || binding.keyCode != keyCode) return ButtonDecision.PASS
        if (!down || repeats != 0) return ButtonDecision.CONSUME
        val token = "$deviceId:$keyCode:$downTime"
        if (token in recent) return ButtonDecision.CONSUME
        recent.addLast(token)
        if (recent.size > 16) recent.removeFirst()
        return if (ready) ButtonDecision.ACTIVATE else ButtonDecision.CONSUME
    }
}

/** Puente únicamente durante la vida del coordinador; también evita redispatch a nuestra sesión. */
internal object TripButtonRuntime {
    var handler: ((android.view.KeyEvent) -> Boolean)? = null
    val intercepting: Boolean get() = handler != null
}
