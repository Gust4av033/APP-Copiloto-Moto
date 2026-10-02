package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Test

class TripActivationPolicyTest {
    @Test fun manualGatePreventsEverySelectedModeFromStartingAWakeMicrophone() {
        WakeAudioMode.entries.forEach { selected ->
            assertEquals(selected.name, WakeAudioMode.BUTTON,
                TripActivationPolicy.effectiveMode(selected, manualOnly = true))
        }
    }

    @Test fun disablingManualGateRestoresSelectedVoiceInputAndPreservesButtonMode() {
        WakeAudioMode.entries.forEach { selected ->
            assertEquals(selected, TripActivationPolicy.effectiveMode(selected, manualOnly = false))
        }
    }

    @Test fun changingSelectedModeDoesNotBypassAnEnabledManualGate() {
        listOf("activa modo 1", "activa modo 3", "activa modo 2").forEach { spoken ->
            val selected = checkNotNull(MinimalIntentRouter.route(spoken).audioMode)
            assertEquals(WakeAudioMode.BUTTON, TripActivationPolicy.effectiveMode(selected, true))
        }
    }
}
