package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.NavigationApp
import com.gust4av033.appcopilotomoto.NavigationDestination
import com.gust4av033.appcopilotomoto.NavigationPreferences
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.ChoiceUi
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

@Composable
fun NavigationCard(state: CopilotUiState, actions: CopilotActions) {
    SectionCard(stringResource(R.string.navigation_title)) {
        ChoiceGroup(listOf(ChoiceUi(NavigationApp.WAZE.id, R.string.navigation_provider_waze),
            ChoiceUi(NavigationApp.GOOGLE_MAPS.id, R.string.navigation_provider_maps)),
            state.navigationProvider.id, !state.busy && !state.appCatalogLoading, actions.onNavigationProviderChange)
        SupportingText(if (state.appCatalogLoading) stringResource(R.string.catalog_loading)
            else stringResource(if (state.navigationAvailable) R.string.navigation_ready else R.string.navigation_missing,
                state.navigationProvider.label))
        SupportingText(stringResource(if (state.navigationProvider == NavigationApp.WAZE)
            R.string.navigation_setup else R.string.navigation_maps_setup))
        if (state.navigationProvider == NavigationApp.WAZE)
            SupportingText(stringResource(R.string.navigation_waze_limited_context))
        if (state.navigationProvider == NavigationApp.GOOGLE_MAPS) {
            NavigationAddressEditor(state, actions, NavigationDestination.HOME, state.navigationHomeAddress)
            NavigationAddressEditor(state, actions, NavigationDestination.WORK, state.navigationWorkAddress)
        }
        NavigationCoordinatesEditor(state, actions, NavigationDestination.HOME, state.navigationHomeCoordinates)
        NavigationCoordinatesEditor(state, actions, NavigationDestination.WORK, state.navigationWorkCoordinates)
        SupportingText(stringResource(R.string.navigation_commands))
        SupportingText(stringResource(R.string.navigation_request_command))
        SupportingText(stringResource(R.string.navigation_search_hint))
        SupportingText(stringResource(R.string.navigation_confirmation))
        SupportingText(if (state.lastNavigationRequest.isBlank()) stringResource(R.string.navigation_last_empty)
            else stringResource(R.string.navigation_last, state.navigationProvider.label, state.lastNavigationRequest))
        SupportingText(stringResource(if (state.navigationProvider == NavigationApp.WAZE)
            R.string.navigation_scope else R.string.navigation_maps_scope))
        if (state.lastNavigationRequest.isNotBlank()) ActionButton(stringResource(R.string.navigation_forget),
            enabled = !state.busy, onClick = actions.onForgetNavigationRequest)
    }
}

@Composable
private fun NavigationCoordinatesEditor(state: CopilotUiState, actions: CopilotActions,
    destination: NavigationDestination, saved: String) {
    var draft by rememberSaveable(destination.name, saved) { mutableStateOf(saved) }
    val valid = draft.isBlank() || com.gust4av033.appcopilotomoto.parseCoordinates(destination.label, draft) != null
    OutlinedTextField(value = draft, onValueChange = { draft = it.take(80) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy, isError = !valid,
        label = { Text(stringResource(R.string.navigation_coordinates_label, destination.label)) },
        colors = com.gust4av033.appcopilotomoto.presentation.theme.glassTextFieldColors())
    ActionButton(stringResource(R.string.navigation_coordinates_save),
        enabled = !state.busy && valid && draft.trim() != saved,
        onClick = { actions.onNavigationCoordinatesSave(destination, draft) })
    ActionButton(stringResource(R.string.navigation_coordinates_pin),
        enabled = !state.busy && state.tripLocationAvailable,
        onClick = { actions.onNavigationPinCurrentGps(destination) })
}

@Composable
private fun NavigationAddressEditor(state: CopilotUiState, actions: CopilotActions,
    destination: NavigationDestination, saved: String) {
    var draft by rememberSaveable(destination.name, saved) { mutableStateOf(saved) }
    val label = stringResource(if (destination == NavigationDestination.HOME)
        R.string.navigation_maps_home else R.string.navigation_maps_work)
    OutlinedTextField(value = draft, onValueChange = { draft = it.take(160) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy,
        isError = !NavigationPreferences.validAddress(draft),
        label = { Text(label) },
        colors = com.gust4av033.appcopilotomoto.presentation.theme.glassTextFieldColors())
    ActionButton(stringResource(R.string.navigation_maps_save, label),
        enabled = !state.busy && NavigationPreferences.validAddress(draft) && draft.trim() != saved,
        onClick = { actions.onNavigationAddressSave(destination, draft) })
}
