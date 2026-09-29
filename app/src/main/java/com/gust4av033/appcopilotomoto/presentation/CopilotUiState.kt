package com.gust4av033.appcopilotomoto.presentation

import androidx.compose.runtime.Immutable
import com.gust4av033.appcopilotomoto.R

enum class CopilotScreen(val titleRes: Int, val iconRes: Int) {
    TRIP(R.string.nav_trip, R.drawable.ic_trip),
    TESTS(R.string.nav_tests, R.drawable.ic_tests),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings)
}

@Immutable
data class ChoiceUi(val id: String, val titleRes: Int)

@Immutable
data class TripMetricsUi(
    val detections: Int = 0,
    val manualCommands: Int = 0,
    val recognized: Int = 0,
    val actions: Int = 0,
    val failures: Int = 0,
    val readyLatencyMs: Long = 0,
    val totalLatencyMs: Long = 0
)

@Immutable
data class ProbeUi(
    val requestedInput: String,
    val actualInput: String,
    val selectedOutput: String,
    val durationMs: Long,
    val sampleCount: Long,
    val rmsDbfs: Int,
    val peakDbfs: Int,
    val warning: String? = null
)

/** Snapshot de presentación. No crea servicios, grabadores ni proveedores de voz. */
@Immutable
data class CopilotUiState(
    val tripActive: Boolean = false,
    val measuring: Boolean = false,
    val listening: Boolean = false,
    val wakeEnabled: Boolean = false,
    val wakeTransition: Boolean = false,
    val tripStatus: String = "",
    val lastCommandResult: String = "",
    val metrics: TripMetricsUi = TripMetricsUi(),
    val phraseId: String = "ALEXA",
    val phraseLabel: String = "Alexa",
    val sensitivity: Float = 80f,
    val inputId: String = "BLUETOOTH",
    val phrases: List<ChoiceUi> = listOf(
        ChoiceUi("ALEXA", R.string.phrase_alexa),
        ChoiceUi("HEY_JARVIS", R.string.phrase_jarvis)
    ),
    val inputs: List<ChoiceUi> = listOf(
        ChoiceUi("BLUETOOTH", R.string.input_headset),
        ChoiceUi("PHONE_WAKE", R.string.input_phone_test)
    ),
    val assistantSelected: Boolean = false,
    val assistantMessage: String = "",
    val voiceMessage: String = "",
    val probeMessage: String = "",
    val wakeMessage: String = "",
    val wakeDetections: Int = 0,
    val audioDiagnostic: String = "",
    val audioBefore: String = "",
    val audioDuring: String = "",
    val audioAfter: String = "",
    val callMessage: String = "",
    val fixedContact: String? = null,
    val probe: ProbeUi? = null
) {
    val busy: Boolean get() = tripActive || measuring || listening || wakeEnabled || wakeTransition
    val canListen: Boolean get() = !measuring && !wakeEnabled && !wakeTransition && !tripActive
    val canToggleWake: Boolean get() = !tripActive && !measuring &&
        (wakeEnabled || (!listening && !wakeTransition))
}

/** La Activity adapta estas acciones al runtime existente. Los previews usan callbacks vacíos. */
data class CopilotActions(
    val onToggleTrip: () -> Unit = {},
    val onRefreshAudio: () -> Unit = {},
    val onSelectAssistant: () -> Unit = {},
    val onToggleListening: () -> Unit = {},
    val onMeasure: () -> Unit = {},
    val onToggleWake: () -> Unit = {},
    val onPhraseChange: (String) -> Unit = {},
    val onSensitivityChange: (Float) -> Unit = {},
    val onInputChange: (String) -> Unit = {},
    val onEnableNamedCalls: () -> Unit = {},
    val onChooseContact: () -> Unit = {},
    val onClearContact: () -> Unit = {}
)
