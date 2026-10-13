package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotScreen
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.theme.GlassPanel
import kotlinx.coroutines.launch

/** Swiping changes presentation only. The service is invoked only by explicit action buttons. */
@Composable
fun HomeDashboardPager(state: CopilotUiState, actions: CopilotActions, onNavigate: (CopilotScreen) -> Unit) {
    val labels = listOf(R.string.home_panel_copilot, R.string.home_panel_equipment, R.string.home_panel_route)
    val pager = rememberPagerState(pageCount = { labels.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                labels.forEachIndexed { index, title ->
                    val active = pager.currentPage == index
                    TextButton(
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        modifier = Modifier.weight(1f).sizeIn(minHeight = 48.dp).testTag("home_tab_$index")
                            .semantics { selected = active; role = Role.Tab },
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    ) { Text(stringResource(title), style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        HorizontalPager(
            state = pager, modifier = Modifier.fillMaxWidth().testTag("home_dashboard_pager"),
            pageSpacing = 12.dp, verticalAlignment = Alignment.Top, key = { it }
        ) { page ->
            when (page) {
                0 -> JourneyHero(state, tripScreen = false, onPrimary = {
                    if (!state.tripActive) actions.onToggleTrip()
                    onNavigate(CopilotScreen.TRIP)
                }, onCommand = actions.onManualTripCommand)
                1 -> EquipmentPanel(state, actions, onNavigate)
                else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TripNavigationCard(state.navigationContext)
                    ActionButton(stringResource(R.string.home_open_trip), iconRes = R.drawable.ic_trip,
                        onClick = { onNavigate(CopilotScreen.TRIP) })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_panel_hint), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.home_panel_count, pager.currentPage + 1, labels.size),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EquipmentPanel(state: CopilotUiState, actions: CopilotActions, onNavigate: (CopilotScreen) -> Unit) {
    SectionCard(stringResource(R.string.home_panel_equipment)) {
        val headset = state.audioDevices.selectedLabel.ifBlank {
            state.audioDevices.automaticHeadset.ifBlank { stringResource(R.string.devices_auto_unresolved) }
        }
        Text(stringResource(R.string.home_headset, headset), style = MaterialTheme.typography.titleSmall)
        SupportingText(if (state.buttonMode) stringResource(R.string.home_button_short)
            else stringResource(R.string.mode_voice_instruction, state.phraseLabel))
        if (state.audioDevices.selectionMissing) SupportingText(stringResource(R.string.devices_selection_lost))
        val player = state.musicPlayers.firstOrNull { it.packageName == state.selectedMusicPlayer }?.label
            ?: stringResource(if (state.selectedMusicPlayer == null) R.string.music_player_auto else R.string.music_player_missing)
        Text(stringResource(R.string.home_panel_player, player), style = MaterialTheme.typography.bodyMedium)
        if (state.buttonMode && state.tripButtonBinding == "NONE" && !state.assistantSelected) {
            SupportingText(stringResource(R.string.home_assistant_required))
            ActionButton(stringResource(R.string.assistant_select), enabled = !state.busy,
                iconRes = R.drawable.ic_button, onClick = actions.onSelectAssistant)
        }
        if (state.busy && !state.tripActive) SupportingText(stringResource(R.string.home_test_active))
        ActionButton(stringResource(R.string.home_configure), iconRes = R.drawable.ic_settings,
            onClick = { onNavigate(CopilotScreen.SETTINGS) })
        ActionButton(stringResource(R.string.settings_basic_help), iconRes = R.drawable.ic_help,
            onClick = { onNavigate(CopilotScreen.DIAGNOSTICS) })
    }
}
