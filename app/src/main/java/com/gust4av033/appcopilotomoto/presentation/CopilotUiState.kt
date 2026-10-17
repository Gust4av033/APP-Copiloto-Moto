package com.gust4av033.appcopilotomoto.presentation

import androidx.compose.runtime.Immutable
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.ContactChatCapability
import com.gust4av033.appcopilotomoto.MusicPlayer
import com.gust4av033.appcopilotomoto.TripCallPhase

enum class CopilotScreen(val titleRes: Int, val iconRes: Int, val inMainNavigation: Boolean = true) {
    HOME(R.string.nav_home, R.drawable.ic_home),
    TRIP(R.string.nav_trip, R.drawable.ic_trip),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
    COMMANDS(R.string.nav_commands, R.drawable.ic_commands, inMainNavigation = false),
    DIAGNOSTICS(R.string.settings_basic_help, R.drawable.ic_help, inMainNavigation = false),
    TESTS(R.string.nav_tests, R.drawable.ic_tests, inMainNavigation = false)
}

@Immutable
data class ChoiceUi(val id: String, val titleRes: Int, val descriptionRes: Int? = null)

enum class VoiceRouteUi { NONE, SCO, LE, OTHER }

@Immutable
data class AudioDeviceUi(val id: Int, val label: String, val technicalLabel: String = label)

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
    val controlHeadsetName: String = "",
    val observedControlKeys: Set<Int> = emptySet(),
    val assistantInvocationObserved: Boolean = false,
    val controlProfilePersistent: Boolean = false,
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
    val developerAccess: com.gust4av033.appcopilotomoto.DeveloperAccess = com.gust4av033.appcopilotomoto.DeveloperAccess(),
    val developerConfirmationRequested: Boolean = false,
    val developerDiagnosticsUnlocked: Boolean = com.gust4av033.appcopilotomoto.BuildConfig.SHOW_DIAGNOSTICS_BY_DEFAULT,
    val accessibilityWazeAvailable: Boolean = com.gust4av033.appcopilotomoto.BuildConfig.ALLOW_ACCESSIBILITY_WAZE,
    val installedVersion: String = "",
    val microphoneGranted: Boolean = false,
    val bluetoothGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val callsGranted: Boolean = false,
    val configurationNotice: String = "",
    val standaloneListening: Boolean = false,
    val interactionPhase: com.gust4av033.appcopilotomoto.TripInteractionPhase = com.gust4av033.appcopilotomoto.TripInteractionPhase.PREPARING,
    val sessionProblem: Boolean = false,
    val tripActive: Boolean = false,
    val keepScreenOnDuringTrip: Boolean = true,
    val restoringAudio: Boolean = false,
    val commandReady: Boolean = false,
    val switchingMode: Boolean = false,
    val overlayEnabled: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val overlayHidden: Boolean = false,
    val overlayMessage: String = "",
    val callPhase: TripCallPhase = TripCallPhase.NONE,
    val measuring: Boolean = false,
    val listening: Boolean = false,
    val wakeEnabled: Boolean = false,
    val wakeTransition: Boolean = false,
    val tripStatus: String = "",
    val lastCommandResult: String = "",
    val pendingAppName: String? = null,
    val pendingDraftText: String? = null,
    val pendingAppToken: String? = null,
    val lastTripCommand: CommandUi? = null,
    val lastTestCommand: CommandUi? = null,
    val metrics: TripMetricsUi = TripMetricsUi(),
    val phraseId: String = "ALEXA",
    val phraseLabel: String = "Alexa",
    val sensitivity: Float = 80f,
    val inputId: String = "BLUETOOTH",
    val manualActivationOnly: Boolean = false,
    val tripButtonBinding: String = "NONE",
    val mediaAccessGranted: Boolean = false,
    val pipelineMetrics: com.gust4av033.appcopilotomoto.TripPipelineMetrics = com.gust4av033.appcopilotomoto.TripPipelineMetrics(),
    val buttonProbeActive: Boolean = false,
    val buttonProbeLog: List<String> = emptyList(),
    val buttonProbeMessage: String = "",
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
    val speechLanguages: com.gust4av033.appcopilotomoto.SpeechLanguageSnapshot = com.gust4av033.appcopilotomoto.SpeechLanguageSnapshot(),
    val appCatalogMessage: String = "",
    val appCatalogLoading: Boolean = false,
    val appCommands: List<String> = emptyList(),
    val musicPlayers: List<MusicPlayer> = emptyList(),
    val selectedMusicPlayer: String? = null,
    val musicPlayersMessage: String = "",
    val currentTripMusicPlayer: com.gust4av033.appcopilotomoto.ResolvedMusicPlayer? = null,
    val currentTripMusicMessage: String = "",
    val navigationAvailable: Boolean = false,
    val navigationProvider: com.gust4av033.appcopilotomoto.NavigationApp = com.gust4av033.appcopilotomoto.NavigationApp.WAZE,
    val navigationHomeAddress: String = "",
    val navigationWorkAddress: String = "",
    val navigationHomeCoordinates: String = "",
    val navigationWorkCoordinates: String = "",
    val nextTripDestination: com.gust4av033.appcopilotomoto.NavigationDestination? = null,
    val tripLocationEnabled: Boolean = false,
    val tripLocationAvailable: Boolean = false,
    val tripContextReport: String = "",
    val wazeObservationEnabled: Boolean = false,
    val wazeScreenEnabled: Boolean = false,
    val wazeScreenGranted: Boolean = false,
    val wazeScreen: com.gust4av033.appcopilotomoto.WazeScreenSnapshot = com.gust4av033.appcopilotomoto.WazeScreenSnapshot(),
    val wazeObservationGranted: Boolean = false,
    val wazeObservation: com.gust4av033.appcopilotomoto.WazeObservationSnapshot = com.gust4av033.appcopilotomoto.WazeObservationSnapshot(),
    val navigationContext: com.gust4av033.appcopilotomoto.TripNavigationContext = com.gust4av033.appcopilotomoto.TripNavigationContext(),
    val observedRoute: com.gust4av033.appcopilotomoto.TripContextSnapshot = com.gust4av033.appcopilotomoto.TripContextSnapshot(),
    val observedRouteAtElapsedMs: Long = 0L,
    val navigationNoticesReady: Boolean = true,
    val lastNavigationRequest: String = "",
    val chatCapabilities: List<ContactChatCapability> = emptyList(),
    val chatContactsGranted: Boolean = false,
    val chatIntegrationMessage: String = "",
    val probeMessage: String = "",
    val wakeMessage: String = "",
    val wakeDetections: Int = 0,
    val audioDiagnostic: String = "",
    val audioBefore: String = "",
    val audioDuring: String = "",
    val conversationAudioStatus: String = "",
    val informationalSpeechStatus: String = "",
    val greetingWarning: String = "",
    val musicSearchStatus: String = "",
    val audioAfter: String = "",
    val callMessage: String = "",
    val fixedContact: String? = null,
    val probe: ProbeUi? = null
) {
    val developerEnabled: Boolean get() = developerAccess.canViewDiagnostics && developerDiagnosticsUnlocked
    val developerToolsAvailable: Boolean get() = developerAccess.canRunExperiments
    val buttonMode: Boolean get() = inputId == "BUTTON" || manualActivationOnly
    val canStartTrip: Boolean get() = !busy && !audioDevices.selectionMissing
    val canRequestTripCommand: Boolean get() = tripActive && commandReady && !restoringAudio && !switchingMode && callPhase == TripCallPhase.NONE
    val busy: Boolean get() = tripActive || restoringAudio || measuring || listening || wakeEnabled || wakeTransition || buttonProbeActive
    val canListen: Boolean get() = developerToolsAvailable && !buttonProbeActive && !measuring && !wakeEnabled && !wakeTransition && !tripActive && !restoringAudio
    val canToggleWake: Boolean get() = developerToolsAvailable && !buttonProbeActive && !tripActive && !restoringAudio && !measuring &&
        (wakeEnabled || (!listening && !wakeTransition))
}

fun CopilotScreen.visibleFor(state: CopilotUiState): CopilotScreen =
    if (this == CopilotScreen.TESTS && !state.developerEnabled) CopilotScreen.DIAGNOSTICS else this

/** La Activity adapta estas acciones al runtime existente. Los previews usan callbacks vacíos. */
data class CopilotActions(
    val onVersionTap: () -> Unit = {},
    val onConfirmDeveloper: () -> Unit = {},
    val onCancelDeveloper: () -> Unit = {},
    val onDisableDeveloper: () -> Unit = {},
    val onOpenBasicDiagnostics: () -> Unit = {},
    val onOpenAppPermissions: () -> Unit = {},
    val onCopyBasicDiagnostics: (String) -> Unit = {},
    val onCancelStandaloneCommand: () -> Unit = {},
    val onToggleTrip: () -> Unit = {},
    val onForgetNavigationRequest: () -> Unit = {},
    val onNavigationProviderChange: (String) -> Unit = {},
    val onNavigationAddressSave: (com.gust4av033.appcopilotomoto.NavigationDestination, String) -> Unit = { _, _ -> },
    val onNavigationCoordinatesSave: (com.gust4av033.appcopilotomoto.NavigationDestination, String) -> Unit = { _, _ -> },
    val onNavigationPinCurrentGps: (com.gust4av033.appcopilotomoto.NavigationDestination) -> Unit = {},
    val onTripDestinationChange: (com.gust4av033.appcopilotomoto.NavigationDestination?) -> Unit = {},
    val onTripLocationChange: (Boolean) -> Unit = {},
    val onForgetTripHistory: () -> Unit = {},
    val onExportLastTrip: (Boolean) -> Unit = {},
    val onWazeObservationChange: (Boolean) -> Unit = {},
    val onWazeScreenChange: (Boolean) -> Unit = {},
    val onWazeScreenPermission: () -> Unit = {},
    val onWazeObservationPermission: () -> Unit = {},
    val onKeepScreenOnChange: (Boolean) -> Unit = {},
    val onManualTripCommand: () -> Unit = {},
    val onCancelPendingApp: (String) -> Unit = {},
    val onOpenPendingApp: (String) -> Unit = {},
    val onCopyDiagnostics: (String) -> Unit = {},
    val onRefreshAudio: () -> Unit = {},
    val onRefreshDevices: () -> Unit = {},
    val onSelectHeadset: (Int?) -> Unit = {},
    val onOpenAudioControls: () -> Unit = {},
    val onSelectAssistant: () -> Unit = {},
    val onToggleListening: () -> Unit = {},
    val onRefreshSpeechLanguages: () -> Unit = {},
    val onPrepareEnglish: () -> Unit = {},
    val onRefreshApps: () -> Unit = {},
    val onEnableChatContacts: () -> Unit = {},
    val onMusicPlayerChange: (String?) -> Unit = {},
    val onMeasure: () -> Unit = {},
    val onToggleWake: () -> Unit = {},
    val onPhraseChange: (String) -> Unit = {},
    val onSensitivityChange: (Float) -> Unit = {},
    val onInputChange: (String) -> Unit = {},
    val onManualActivationOnlyChange: (Boolean) -> Unit = {},
    val onTripButtonBindingChange: (String) -> Unit = {},
    val onMediaAccessSettings: () -> Unit = {},
    val onTestObservation: (com.gust4av033.appcopilotomoto.PipelineEvent) -> Unit = {},
    val onToggleButtonProbe: () -> Unit = {},
    val onOpenButtonProbe: () -> Unit = {},
    val onOverlayEnabledChange: (Boolean) -> Unit = {},
    val onOverlayPermission: () -> Unit = {},
    val onShowOverlay: () -> Unit = {},
    val onEnableNamedCalls: () -> Unit = {},
    val onChooseContact: () -> Unit = {},
    val onClearContact: () -> Unit = {},
    val onOpenPrivacyPolicy: () -> Unit = {}
)



