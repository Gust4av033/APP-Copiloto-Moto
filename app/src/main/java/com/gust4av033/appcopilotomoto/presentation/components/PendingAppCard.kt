package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

/** Cada acción conserva el token del aviso visible; el servicio comprueba su vigencia. */
@Composable
fun PendingAppCard(state: CopilotUiState, actions: CopilotActions) {
    val name = state.pendingAppName ?: return
    val token = state.pendingAppToken
    SectionCard(stringResource(R.string.trip_pending_app)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        SupportingText(stringResource(R.string.trip_pending_app_hint))
        ActionButton(stringResource(R.string.trip_open_app),
            enabled = state.tripActive && token != null,
            onClick = { token?.let(actions.onOpenPendingApp) })
        ActionButton(stringResource(R.string.trip_cancel_app),
            enabled = state.tripActive && token != null,
            onClick = { token?.let(actions.onCancelPendingApp) })
    }
}
