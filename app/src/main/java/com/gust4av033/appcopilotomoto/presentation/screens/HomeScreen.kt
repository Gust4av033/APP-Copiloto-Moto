package com.gust4av033.appcopilotomoto.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotScreen
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.components.FullScreenTripMap
import com.gust4av033.appcopilotomoto.presentation.components.GlassIconButton
import com.gust4av033.appcopilotomoto.presentation.components.QuickLink
import com.gust4av033.appcopilotomoto.presentation.components.SupportingText
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import com.gust4av033.appcopilotomoto.presentation.theme.GlassMode
import com.gust4av033.appcopilotomoto.presentation.theme.GlassPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: CopilotUiState, actions: CopilotActions, onNavigate: (CopilotScreen) -> Unit) {
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val session = state.tripMap
    val now = state.observedRouteAtElapsedMs
    val fix = session.lastFix?.value
    val gpsAllowed = state.tripLocationEnabled && state.tripLocationAvailable
    val gpsChip = when {
        !state.tripLocationEnabled -> R.string.home_map_chip_gps_off
        !state.tripLocationAvailable -> R.string.home_map_chip_gps_unavailable
        fix == null -> R.string.home_map_chip_gps_wait
        session.currentFix(now) != null -> R.string.home_map_chip_gps_live
        else -> R.string.home_map_chip_gps_last
    }
    val audioLabel = if (state.tripActive) {
        if (state.listening) stringResource(R.string.settings_voice_listening)
        else if (state.interactionPhase.name == "SPEAKING") stringResource(R.string.settings_voice_speaking)
        else stringResource(R.string.settings_voice_ready)
    } else stringResource(R.string.trip_stopped)

    Box(Modifier.fillMaxSize()) {
        if (fix != null && gpsAllowed) {
            FullScreenTripMap(session, now, state.mapOnlineEnabled, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    mode = GlassMode.FULL) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(
                            if (!state.tripLocationEnabled) R.string.home_map_empty_gps_off
                            else if (!state.tripLocationAvailable) R.string.home_map_empty_unavailable
                            else R.string.home_map_empty_wait
                        ), style = MaterialTheme.typography.bodyLarge)
                        SupportingText(stringResource(R.string.trip_map_start_to_locate))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPanel(modifier = Modifier.weight(1f).testTag("home_map_status"), mode = GlassMode.LITE,
                shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(gpsChip), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(if (state.tripActive) R.string.home_map_chip_session_active
                        else R.string.home_map_chip_session_stopped),
                        style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.home_map_chip_audio, audioLabel),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            GlassIconButton(R.drawable.ic_settings, stringResource(R.string.home_map_sheet_open),
                onClick = { sheetOpen = true })
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.home_map_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
                SupportingText(stringResource(R.string.home_map_sheet_hint))
                SupportingText(stringResource(gpsChip))
                SupportingText(stringResource(if (state.tripActive) R.string.home_map_chip_session_active
                    else R.string.home_map_chip_session_stopped))
                QuickLink(stringResource(R.string.nav_settings), stringResource(R.string.settings_subtitle),
                    R.drawable.ic_settings) {
                    sheetOpen = false
                    onNavigate(CopilotScreen.SETTINGS)
                }
                QuickLink(stringResource(R.string.home_view_commands),
                    stringResource(R.string.home_commands_short), R.drawable.ic_commands) {
                    sheetOpen = false
                    onNavigate(CopilotScreen.COMMANDS)
                }
                QuickLink(stringResource(R.string.settings_basic_help),
                    stringResource(R.string.settings_basic_help_hint), R.drawable.ic_help) {
                    sheetOpen = false
                    onNavigate(CopilotScreen.DIAGNOSTICS)
                }
                QuickLink(stringResource(R.string.settings_open_android_permissions),
                    stringResource(R.string.settings_permissions_hint), R.drawable.ic_settings,
                    actions.onOpenAppPermissions)
            }
        }
    }
}
