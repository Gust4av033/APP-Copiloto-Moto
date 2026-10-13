package com.gust4av033.appcopilotomoto.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.MinimalIntentRouter
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.components.*

fun LazyListScope.CommandsScreen(state: CopilotUiState, actions: CopilotActions) {
    if (state.pendingAppName != null) item(key = "pending") { PendingAppCard(state, actions) }
    item(key = "activation") {
        SectionCard(stringResource(R.string.trip_commands)) {
            SupportingText(if (state.buttonMode) stringResource(R.string.commands_button_intro)
                else stringResource(R.string.commands_voice_intro, state.phraseLabel))
        }
    }
    item(key = "music") {
        ExpandableSectionCard(stringResource(R.string.command_group_music)) {
            Text(stringResource(R.string.commands_music))
            Text(stringResource(R.string.commands_volume))
        }
    }
    item(key = "modes") {
        ExpandableSectionCard(stringResource(R.string.command_group_trip)) {
            Text(stringResource(R.string.commands_modes))
            SupportingText(stringResource(R.string.mode_switch_hint))
            Text(stringResource(R.string.commands_cancel))
            SupportingText(stringResource(R.string.commands_stop))
        }
    }
    item(key = "chat") {
        ExpandableSectionCard(stringResource(R.string.command_group_chat)) {
            Text(stringResource(R.string.commands_chat_draft))
            SupportingText(stringResource(R.string.chat_draft_pending_hint))
            Text(stringResource(R.string.commands_call))
            SupportingText(stringResource(R.string.commands_call_recovery))
            SupportingText(stringResource(R.string.commands_incoming_calls))
        }
    }
    item(key = "navigation") {
        ExpandableSectionCard(stringResource(R.string.command_group_navigation)) {
            SupportingText(if (state.appCatalogLoading) stringResource(R.string.catalog_loading)
                else stringResource(if (state.navigationAvailable) R.string.navigation_ready else R.string.navigation_missing))
            Text(stringResource(R.string.navigation_commands))
            SupportingText(stringResource(R.string.navigation_search_hint))
            SupportingText(stringResource(R.string.navigation_confirmation))
            SupportingText(stringResource(R.string.navigation_scope))
        }
    }
    item(key = "apps_guide") {
        ExpandableSectionCard(stringResource(R.string.command_group_apps)) {
            Text(stringResource(R.string.commands_apps))
        }
    }
    if (state.developerEnabled) item(key = "phrase") { CommandPhraseTestCard() }
    item(key = "catalog") { AppCatalogCard(state, actions) }
}

@Composable
private fun AppCatalogCard(state: CopilotUiState, actions: CopilotActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var visibleCount by rememberSaveable(query) { mutableStateOf(20) }
    val matches = remember(query, state.appCommands) {
        val normalized = MinimalIntentRouter.normalize(query)
        state.appCommands.filter { MinimalIntentRouter.normalize(it).contains(normalized) }
    }
    SectionCard(stringResource(R.string.tests_apps_title)) {
        if (state.appCatalogLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            SupportingText(stringResource(R.string.catalog_loading))
        } else SupportingText(stringResource(R.string.integrations_apps, state.appCommands.size))
        SupportingText(stringResource(R.string.commands_apps_catalog_hint))
        ActionButton(stringResource(R.string.tests_apps_refresh), enabled = !state.appCatalogLoading, onClick = actions.onRefreshApps)
        OutlinedTextField(value = query, onValueChange = { query = it.take(100) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.commands_apps_search)) },
            colors = com.gust4av033.appcopilotomoto.presentation.theme.glassTextFieldColors())
        SupportingText(stringResource(R.string.commands_apps_count, matches.size))
        if (matches.isEmpty() && !state.appCatalogLoading) SupportingText(stringResource(if (state.appCommands.isEmpty())
            R.string.commands_apps_empty else R.string.commands_apps_no_match))
        matches.take(visibleCount).forEach {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (matches.size > visibleCount) TextButton(onClick = { visibleCount += 20 }) {
            Text(stringResource(R.string.commands_apps_more))
        }
    }
}

