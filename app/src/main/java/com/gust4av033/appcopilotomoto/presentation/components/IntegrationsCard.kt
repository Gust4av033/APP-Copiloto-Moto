package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.ChatApp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

@Composable
fun IntegrationsCard(state: CopilotUiState, actions: CopilotActions, includeNavigation: Boolean = true) {
    SectionCard(stringResource(R.string.integrations_title)) {
        if (state.appCatalogLoading) SupportingText(stringResource(R.string.catalog_loading))
        else SupportingText(stringResource(R.string.integrations_apps, state.appCommands.size))
        SupportingText(stringResource(R.string.integrations_music, state.musicPlayers.size))
        ChatApp.entries.forEach { app ->
            val available = state.chatCapabilities.any { it.app == app && it.available }
            Text(app.label)
            SupportingText(if (state.appCatalogLoading) stringResource(R.string.catalog_loading)
                else stringResource(if (!available) R.string.integrations_chat_unavailable
                    else if (state.chatContactsGranted) R.string.integrations_chat_ready
                    else R.string.integrations_chat_permission))
        }
        ActionButton(stringResource(R.string.integrations_contacts),
            enabled = !state.busy && !state.chatContactsGranted,
            onClick = actions.onEnableChatContacts)
        ActionButton(stringResource(R.string.tests_apps_refresh), enabled = !state.appCatalogLoading, onClick = actions.onRefreshApps)
        if (state.chatIntegrationMessage.isNotBlank()) SupportingText(state.chatIntegrationMessage)
        SupportingText(stringResource(R.string.commands_chat))
        SupportingText(stringResource(R.string.commands_chat_draft))
        SupportingText(stringResource(R.string.integrations_chat_hint))
    }
    if (includeNavigation) NavigationCard(state, actions)
}
