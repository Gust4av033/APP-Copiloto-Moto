package com.gust4av033.appcopilotomoto.presentation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.TripCallPhase
import com.gust4av033.appcopilotomoto.MinimalIntentRouter
import com.gust4av033.appcopilotomoto.presentation.screens.CommandsScreen
import com.gust4av033.appcopilotomoto.presentation.screens.HomeScreen
import com.gust4av033.appcopilotomoto.presentation.screens.SettingsScreen
import com.gust4av033.appcopilotomoto.presentation.screens.TestsScreen
import com.gust4av033.appcopilotomoto.presentation.screens.TripScreen
import com.gust4av033.appcopilotomoto.presentation.theme.CopilotTheme

@Composable
fun CopilotApp(
    state: CopilotUiState,
    selectedScreen: CopilotScreen,
    onScreenChange: (CopilotScreen) -> Unit,
    actions: CopilotActions
) {
    // Las rutas secundarias conservan su origen; navegar no toca el runtime.
    var secondaryParentName by rememberSaveable { mutableStateOf(CopilotScreen.SETTINGS.name) }
    val secondaryParent = runCatching { CopilotScreen.valueOf(secondaryParentName) }.getOrDefault(CopilotScreen.SETTINGS)
    val navigate: (CopilotScreen) -> Unit = { screen ->
        if (!screen.inMainNavigation && selectedScreen.inMainNavigation) {
            secondaryParentName = selectedScreen.name
        }
        onScreenChange(screen)
    }
    val navigateBack: () -> Unit = {
        navigate(if (!selectedScreen.inMainNavigation) secondaryParent else CopilotScreen.HOME)
    }
    val mainSelection = if (!selectedScreen.inMainNavigation) secondaryParent else selectedScreen
    if (!LocalInspectionMode.current) {
        BackHandler(enabled = selectedScreen != CopilotScreen.HOME, onBack = navigateBack)
    }
    CopilotTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    if (state.tripActive && selectedScreen != CopilotScreen.TRIP) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(when (state.callPhase) {
                                    TripCallPhase.CALL -> R.string.session_call
                                    TripCallPhase.RECOVERING -> R.string.session_call_recovering
                                    TripCallPhase.NONE -> R.string.trip_active
                                }), modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge)
                                TextButton(onClick = { navigate(CopilotScreen.TRIP) }) {
                                    Text(stringResource(R.string.home_open_trip))
                                }
                            }
                        }
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        CopilotScreen.entries.filter { it.inMainNavigation }.forEach { screen ->
                            NavigationBarItem(
                                selected = mainSelection == screen,
                                onClick = { navigate(screen) },
                                icon = { Icon(painterResource(screen.iconRes), contentDescription = null, modifier = Modifier.size(24.dp)) },
                                label = { Text(stringResource(screen.titleRes)) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            val screenState = rememberSaveableStateHolder()
            screenState.SaveableStateProvider(selectedScreen.name) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!selectedScreen.inMainNavigation) {
                            TextButton(onClick = navigateBack) {
                                Text(stringResource(R.string.back_to_screen, stringResource(secondaryParent.titleRes)))
                            }
                        }
                        Text(stringResource(R.string.ui_brand), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(selectedScreen.titleRes), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.ui_phase) + " · " + stringResource(R.string.ui_revision),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(4.dp))
                        when (selectedScreen) {
                            CopilotScreen.HOME -> HomeScreen(state, actions, navigate)
                            CopilotScreen.TRIP -> TripScreen(state, actions,
                                onOpenCommands = { navigate(CopilotScreen.COMMANDS) },
                                onOpenTests = { navigate(CopilotScreen.TESTS) })
                            CopilotScreen.COMMANDS -> CommandsScreen(state, actions)
                            CopilotScreen.TESTS -> TestsScreen(state, actions)
                            CopilotScreen.SETTINGS -> SettingsScreen(state, actions,
                                onOpenCommands = { navigate(CopilotScreen.COMMANDS) },
                                onOpenTests = { navigate(CopilotScreen.TESTS) })
                        }
                    }
                }
            }
        }
    }
}

// Datos exclusivamente de preview: nunca se invocan servicios o proveedores de audio.
private val PreviewState = CopilotUiState(
    tripStatus = "Iniciá la sesión con el casco conectado.",
    lastCommandResult = "Todavía no se ha enviado una orden.",
    audioDiagnostic = "Actualizá los niveles antes y después de iniciar el viaje.",
    audioBefore = "Modo normal · música 10/15",
    audioDuring = "Modo comunicación · comunicación 8/15",
    audioAfter = "Modo normal · música 10/15",
    voiceMessage = "Listo para escuchar una orden corta.",
    probeMessage = "La prueba dura cinco segundos.",
    wakeMessage = "Detector detenido.",
    assistantMessage = "Elegí APP como asistente para probar el botón del casco.",
    callMessage = "Las llamadas necesitan permisos de teléfono y contactos."
)

private val PreviewDevices = AudioDevicesUi(
    supported = true, permissionGranted = true,
    headsets = listOf(AudioDeviceUi(11, "Y12 Pro · SCO clásico · ID 11"),
        AudioDeviceUi(22, "Skullcandy · SCO clásico · ID 22")),
    selectedId = 11, selectedLabel = "Y12 Pro · SCO clásico · ID 11",
    musicOutput = "Y12 Pro · A2DP · ID 12",
    musicVolume = "7/15", communicationVolume = "10/15"
)

@Preview(name = "Dispositivos: dos cascos", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Preview(name = "Dispositivos: texto grande", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", fontScale = 1.5f)
@Composable
private fun DevicesPreview() { DeviceCardPreview(PreviewDevices) }

@Preview(name = "Dispositivos: sesión activa", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActiveDevicesPreview() {
    DeviceCardPreview(PreviewDevices.copy(communicationRoute = "Y12 Pro · SCO clásico · ID 11"), active = true)
}

@Preview(name = "Dispositivos: casco desconectado", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun MissingDevicesPreview() {
    DeviceCardPreview(PreviewDevices.copy(selectionMissing = true,
        headsets = PreviewDevices.headsets.filter { it.id == 22 }))
}

@Composable
private fun DeviceCardPreview(devices: AudioDevicesUi, active: Boolean = false) {
    CopilotTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            com.gust4av033.appcopilotomoto.presentation.components.AudioDevicesCard(
                PreviewState.copy(tripActive = active, audioDevices = devices), CopilotActions()
            )
        }
    }
}

@Preview(name = "Inicio día", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Preview(name = "Inicio noche", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Inicio texto grande", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", fontScale = 1.5f)
@Composable
private fun HomePreview() {
    CopilotApp(PreviewState, CopilotScreen.HOME, {}, CopilotActions())
}

@Preview(name = "Inicio sesión activa", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun ActiveHomePreview() {
    CopilotApp(PreviewState.copy(tripActive = true, tripStatus = "Esperando Alexa."), CopilotScreen.HOME, {}, CopilotActions())
}

@Preview(name = "Viaje día", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Preview(name = "Viaje noche", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Viaje texto grande", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", fontScale = 1.5f)
@Composable
private fun TripPreview() {
    CopilotApp(PreviewState, CopilotScreen.TRIP, {}, CopilotActions())
}

@Preview(name = "Viaje activo", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun ActiveTripPreview() {
    CopilotApp(
        PreviewState.copy(tripActive = true, tripStatus = "Esperando Alexa.", metrics = TripMetricsUi(4, 1, 5, 5, 0)),
        CopilotScreen.TRIP, {}, CopilotActions()
    )
}

@Preview(name = "Pruebas", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun TestsPreview() {
    CopilotApp(PreviewState, CopilotScreen.TESTS, {}, CopilotActions())
}

@Preview(name = "Ajustes", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Preview(name = "Ajustes texto grande", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", fontScale = 1.5f)
@Composable
private fun SettingsPreview() {
    CopilotApp(PreviewState, CopilotScreen.SETTINGS, {}, CopilotActions())
}

@Preview(name = "Botón: Inicio", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun ButtonHomePreview() {
    CopilotApp(PreviewState.copy(inputId = "BUTTON", audioDevices = PreviewDevices), CopilotScreen.HOME, {}, CopilotActions())
}

@Preview(name = "Botón: Viaje noche", showBackground = true, widthDp = 360, heightDp = 800,
    locale = "es", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Botón: Viaje texto grande", showBackground = true, widthDp = 360, heightDp = 800,
    locale = "es", fontScale = 1.5f)
@Composable
private fun ButtonTripPreview() {
    CopilotApp(PreviewState.copy(inputId = "BUTTON", tripActive = true, commandReady = true,
        tripStatus = "Esperando el botón del casco. Música por la salida normal; micrófono cerrado.",
        audioDevices = PreviewDevices), CopilotScreen.TRIP, {}, CopilotActions())
}

@Preview(name = "Restaurando audio", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun RestoringPreview() {
    CopilotApp(PreviewState.copy(inputId = "BUTTON", restoringAudio = true,
        tripStatus = "Restaurando música…", audioDevices = PreviewDevices), CopilotScreen.HOME, {}, CopilotActions())
}

@Preview(name = "Botón: Ajustes", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun ButtonSettingsPreview() {
    CopilotApp(PreviewState.copy(inputId = "BUTTON", audioDevices = PreviewDevices), CopilotScreen.SETTINGS, {}, CopilotActions())
}

@Preview(name = "Fase 1: aplicación preparada", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Preview(name = "Fase 1: aplicación texto grande", showBackground = true, widthDp = 360, heightDp = 800,
    locale = "es", fontScale = 1.5f)
@Composable
private fun PreparedAppPreview() {
    CopilotApp(PreviewState.copy(inputId = "BUTTON", tripActive = true, commandReady = true,
        pendingAppName = "Spotify", pendingAppToken = "preview", lastTripCommand = MinimalIntentRouter.route("abre Spotify").toCommandUi(),
        lastCommandResult = "Encontré Spotify. Tocá la acción de la notificación para abrirla.",
        audioDevices = PreviewDevices), CopilotScreen.TRIP, {}, CopilotActions())
}


@Preview(name = "Comandos: aplicaciones", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Preview(name = "Comandos: texto grande", showBackground = true, widthDp = 360, heightDp = 800,
    locale = "es", fontScale = 1.5f)
@Composable
private fun CommandsPreview() {
    CopilotApp(PreviewState.copy(inputId = "BUTTON", appCatalogMessage = "3 accesos disponibles.",
        appCommands = listOf("abre Spotify", "abre Waze", "abre YouTube Music")),
        CopilotScreen.COMMANDS, {}, CopilotActions())
}

@Preview(name = "Viaje: llamada", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun CallPreview() {
    CopilotApp(PreviewState.copy(tripActive = true, inputId = "BUTTON", callPhase = TripCallPhase.CALL,
        tripStatus = "Viaje en espera durante la llamada."), CopilotScreen.TRIP, {}, CopilotActions())
}

@Preview(name = "Inicio: recuperar llamada", showBackground = true, widthDp = 360, heightDp = 800,
    locale = "es", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CallRecoveryPreview() {
    CopilotApp(PreviewState.copy(tripActive = true, inputId = "BUTTON", callPhase = TripCallPhase.RECOVERING,
        tripStatus = "Llamada terminada; recuperando modo viaje…"), CopilotScreen.HOME, {}, CopilotActions())
}

@Preview(name = "Burbuja: permiso pendiente", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Preview(name = "Burbuja: texto grande", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", fontScale = 1.5f)
@Composable
private fun OverlaySettingsPreview() {
    CopilotApp(PreviewState.copy(overlayEnabled = true), CopilotScreen.SETTINGS, {}, CopilotActions())
}

@Preview(name = "Cambio de modo: controles bloqueados", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun SwitchingModePreview() {
    CopilotApp(PreviewState.copy(tripActive = true, switchingMode = true, inputId = "BUTTON",
        overlayEnabled = true, overlayPermissionGranted = true,
        tripStatus = "Cambiando al modo 1; liberando audio…"), CopilotScreen.SETTINGS, {}, CopilotActions())
}

@Preview(name = "Borrador WhatsApp: revisar antes de abrir", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", fontScale = 1.5f)
@Composable
private fun WhatsAppDraftPreview() {
    CopilotApp(PreviewState.copy(tripActive = true, commandReady = false,
        pendingAppName = "WhatsApp · borrador para Ana", pendingAppToken = "draft-preview",
        pendingDraftText = "Voy llegando. Te aviso cuando llegue.",
        tripStatus = "Confirmación: uno abre el borrador, dos lo descarta."),
        CopilotScreen.TRIP, {}, CopilotActions())
}

@Preview(name = "Continuo: activación manual", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Preview(name = "Continuo manual: texto grande", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", fontScale = 1.5f)
@Composable
private fun ManualContinuousTripPreview() {
    CopilotApp(PreviewState.copy(inputId = "BLUETOOTH", manualActivationOnly = true,
        tripActive = true, commandReady = true, audioDevices = PreviewDevices,
        tripStatus = "Esperando activación manual; micrófono cerrado."),
        CopilotScreen.TRIP, {}, CopilotActions())
}

@Preview(name = "Prueba Y12: evento recibido", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun HeadsetButtonTestPreview() {
    CopilotApp(PreviewState.copy(buttonProbeActive = true,
        buttonProbeMessage = "Prueba activa; micrófono cerrado.",
        buttonProbeLog = listOf("3400 ms · MediaSession · KEYCODE_MEDIA_PLAY_PAUSE · DOWN · repeticiones=0")),
        CopilotScreen.TESTS, {}, CopilotActions())
}
