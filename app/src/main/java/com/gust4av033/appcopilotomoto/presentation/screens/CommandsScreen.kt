package com.gust4av033.appcopilotomoto.presentation.screens

import androidx.compose.foundation.layout.fillMaxWidth
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
import com.gust4av033.appcopilotomoto.MinimalIntentRouter
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.components.ActionButton
import com.gust4av033.appcopilotomoto.presentation.components.CommandPhraseTestCard
import com.gust4av033.appcopilotomoto.presentation.components.PendingAppCard
import com.gust4av033.appcopilotomoto.presentation.components.IntegrationsCard
import com.gust4av033.appcopilotomoto.presentation.components.SectionCard
import com.gust4av033.appcopilotomoto.presentation.components.SupportingText

@Composable
fun CommandsScreen(state: CopilotUiState, actions: CopilotActions) {
    PendingAppCard(state, actions)
    SectionCard(stringResource(R.string.trip_commands)) {
        SupportingText(if (state.buttonMode) stringResource(R.string.commands_button_intro)
            else stringResource(R.string.commands_voice_intro, state.phraseLabel))
        Text(stringResource(R.string.commands_music))
        Text(stringResource(R.string.commands_volume))
        Text(stringResource(R.string.commands_modes))
        SupportingText(stringResource(R.string.mode_switch_hint))
        Text(stringResource(R.string.commands_call))
        SupportingText(stringResource(R.string.commands_call_recovery))
        SupportingText(stringResource(R.string.commands_incoming_calls))
        Text(stringResource(R.string.commands_chat_draft))
        SupportingText(stringResource(R.string.chat_draft_pending_hint))
        Text(stringResource(R.string.commands_cancel))
        SupportingText(stringResource(R.string.commands_stop))
    }
    IntegrationsCard(state, actions)
    CommandPhraseTestCard()
    var query by rememberSaveable { mutableStateOf("") }
    var visibleCount by rememberSaveable(query) { mutableStateOf(20) }
    val matches = remember(query, state.appCommands) {
        val normalized = MinimalIntentRouter.normalize(query)
        state.appCommands.filter { MinimalIntentRouter.normalize(it).contains(normalized) }
    }
    SectionCard(stringResource(R.string.tests_apps_title)) {
        SupportingText(state.appCatalogMessage)
        SupportingText(stringResource(R.string.commands_apps_catalog_hint))
        ActionButton(stringResource(R.string.tests_apps_refresh), onClick = actions.onRefreshApps)
        OutlinedTextField(value = query, onValueChange = { query = it.take(100) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.commands_apps_search)) })
        SupportingText(stringResource(R.string.commands_apps_count, matches.size))
        if (matches.isEmpty()) SupportingText(stringResource(if (state.appCommands.isEmpty())
            R.string.commands_apps_empty else R.string.commands_apps_no_match))
        matches.take(visibleCount).forEach { Text(it) }
        if (matches.size > visibleCount) TextButton(onClick = { visibleCount += 20 }) {
            Text(stringResource(R.string.commands_apps_more))
        }
    }
}
