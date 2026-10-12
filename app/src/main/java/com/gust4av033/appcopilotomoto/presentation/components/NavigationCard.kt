package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

@Composable
fun NavigationCard(state: CopilotUiState, actions: CopilotActions) {
    SectionCard(stringResource(R.string.navigation_title)) {
        SupportingText(stringResource(if (state.navigationAvailable) R.string.navigation_ready else R.string.navigation_missing))
        SupportingText(stringResource(R.string.navigation_setup))
        SupportingText(stringResource(R.string.navigation_commands))
        SupportingText(stringResource(R.string.navigation_confirmation))
        SupportingText(if (state.lastNavigationRequest.isBlank()) stringResource(R.string.navigation_last_empty)
            else stringResource(R.string.navigation_last, state.lastNavigationRequest))
        SupportingText(stringResource(R.string.navigation_scope))
        if (state.lastNavigationRequest.isNotBlank()) ActionButton(stringResource(R.string.navigation_forget),
            enabled = !state.busy, onClick = actions.onForgetNavigationRequest)
    }
}
