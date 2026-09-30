package com.gust4av033.appcopilotomoto.presentation

import androidx.compose.runtime.Immutable
import com.gust4av033.appcopilotomoto.R

enum class CopilotScreen(val titleRes: Int, val iconRes: Int, val inMainNavigation: Boolean = true) {
    HOME(R.string.nav_home, R.drawable.ic_home),
    TRIP(R.string.nav_trip, R.drawable.ic_trip),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
    TESTS(R.string.nav_tests, R.drawable.ic_tests, inMainNavigation = false)
}

@Immutable
data class ChoiceUi(val id: String, val titleRes: Int, val descriptionRes: Int? = null)

enum class VoiceRouteUi { NONE, SCO, LE, OTHER }

@Immutable
data class AudioDeviceUi(val id: Int, val label: String)

/** Lecturas pasivas del sistema; una ruta de comunicación no demuestra la entrada grabada. */
@Immutable
data class AudioDevicesUi(
    val supported: Boolean = false,
    val permissionGranted: Boolean = false,
    val headsets: List<AudioDeviceUi> = emptyList(),
    val bluetoothOutputs: List<String> = emptyList(),
    val bluetoothInputs: List<String> = emptyList(),
    val selectedId: Int? = null,
    val selectedLabel: String = "",
    val selectionMissing: Boolean = false,
    val automaticHeadset: String = "",
    val communicationRoute: String = "",
    val voiceRoute: VoiceRouteUi = VoiceRouteUi.NONE,
    val musicOutput: String = "",
    val musicVolume: String = "",
    val communicationVolume: String = "",
    val message: String = ""
)

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
    val restoringAudio: Boolean = false,
    val commandReady: Boolean = false,
    val measuring: Boolean = false,
    val listening: Boolean = false,
    val wakeEnabled: Boolean = false,
    val wakeTransition: Boolean = false,
    val tripStatus: String = "",
    val lastCommandResult: String = "",
    val lastTripCommand: CommandUi? = null,
    val lastTestCommand: CommandUi? = null,
    val notificationAppLabel: String? = null,
    val metrics: TripMetricsUi = TripMetricsUi(),
    val phraseId: String = "ALEXA",
    val phraseLabel: String = "Alexa",
    val sensitivity: Float = 80f,
    val inputId: String = "BLUETOOTH",
    val audioCaptureGeneration: Long = 0,
    val audioDevices: AudioDevicesUi = AudioDevicesUi(),
    val phrases: List<ChoiceUi> = listOf(
        ChoiceUi("ALEXA", R.string.phrase_alexa),
        ChoiceUi("HEY_JARVIS", R.string.phrase_jarvis)
    ),
    val inputs: List<ChoiceUi> = listOf(
        ChoiceUi("BLUETOOTH", R.string.input_headset, R.string.mode_voice_detail),
        ChoiceUi("BUTTON", R.string.input_button_quality, R.string.mode_button_detail),
        ChoiceUi("PHONE_WAKE", R.string.input_phone_test, R.string.trip_phone_wake_trial)
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
    val buttonMode: Boolean get() = inputId == "BUTTON"
    val canStartTrip: Boolean get() = !busy && !audioDevices.selectionMissing
    val canRequestTripCommand: Boolean get() = tripActive && commandReady && !restoringAudio
    val busy: Boolean get() = tripActive || restoringAudio || measuring || listening || wakeEnabled || wakeTransition
    val canListen: Boolean get() = !measuring && !wakeEnabled && !wakeTransition && !tripActive && !restoringAudio
    val canToggleWake: Boolean get() = !tripActive && !restoringAudio && !measuring &&
        (wakeEnabled || (!listening && !wakeTransition))
}

/** La Activity adapta estas acciones al runtime existente. Los previews usan callbacks vacíos. */
data class CopilotActions(
    val onToggleTrip: () -> Unit = {},
    val onManualTripCommand: () -> Unit = {},
    val onCopyDiagnostics: (String) -> Unit = {},
    val onRefreshAudio: () -> Unit = {},
    val onRefreshDevices: () -> Unit = {},
    val onSelectHeadset: (Int?) -> Unit = {},
    val onOpenAudioControls: () -> Unit = {},
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
