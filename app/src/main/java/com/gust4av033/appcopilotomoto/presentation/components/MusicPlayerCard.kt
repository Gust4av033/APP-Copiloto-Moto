package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

@Composable
fun MusicPlayerCard(state: CopilotUiState, actions: CopilotActions) {
    SectionCard(stringResource(R.string.music_settings_title)) {
        SupportingText(stringResource(R.string.music_settings_hint))
        Column(Modifier.selectableGroup()) {
            MusicChoice(stringResource(R.string.music_player_auto), state.selectedMusicPlayer == null, !state.busy) {
                actions.onMusicPlayerChange(null)
            }
            state.musicPlayers.forEach { player ->
                MusicChoice(player.label, state.selectedMusicPlayer == player.packageName, !state.busy) {
                    actions.onMusicPlayerChange(player.packageName)
                }
                SupportingText(stringResource(when {
                    player.backgroundSearch -> R.string.music_search_background
                    player.sessionSearch -> R.string.music_search_session
                    player.intentSearch -> R.string.music_search_visible
                    else -> R.string.music_search_unavailable
                }))
            }
        }
        if (state.appCatalogLoading) SupportingText(stringResource(R.string.catalog_loading))
        else if (state.musicPlayers.isEmpty()) SupportingText(stringResource(R.string.music_no_players))
        if (!state.appCatalogLoading && state.selectedMusicPlayer != null && state.musicPlayers.none { it.packageName == state.selectedMusicPlayer }) {
            SupportingText(stringResource(R.string.music_player_missing))
        }
        if (state.developerEnabled) SupportingText(state.musicPlayersMessage)
        ActionButton(stringResource(R.string.music_refresh_players), enabled = !state.appCatalogLoading, onClick = actions.onRefreshApps)
        SupportingText(stringResource(R.string.music_search_scope))
    }
}

@Composable
private fun MusicChoice(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, enabled = enabled, onClick = null)
        Text(label, Modifier.weight(1f).padding(start = 12.dp))
    }
}

