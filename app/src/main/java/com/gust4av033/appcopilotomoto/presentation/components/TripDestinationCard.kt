package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.NavigationDestination
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

/** Destination supplied by the rider, independent of the external navigator's route. */
@Composable
fun TripDestinationCard(state: CopilotUiState, actions: CopilotActions) {
    val chosen = if (state.tripActive) state.navigationContext.ownDestination?.label
    else state.nextTripDestination?.label
    val homeReady = state.navigationHomeCoordinates.isNotBlank()
    val workReady = state.navigationWorkCoordinates.isNotBlank()
    SectionCard(stringResource(R.string.trip_destination_title)) {
        SupportingText(stringResource(if (state.tripActive) R.string.trip_destination_active_hint
            else R.string.trip_destination_next_hint))
        if (chosen != null) SupportingText(stringResource(R.string.trip_destination_chosen, chosen))
        for (destination in NavigationDestination.entries) {
            val ready = if (destination == NavigationDestination.HOME) homeReady else workReady
            ActionButton(stringResource(R.string.trip_destination_choose, destination.label),
                enabled = ready && chosen != destination.label,
                disabledReason = stringResource(if (!ready) R.string.trip_destination_missing_coordinates
                    else R.string.trip_destination_selected),
                onClick = { actions.onTripDestinationChange(destination) })
            if (!ready) SupportingText(stringResource(R.string.trip_destination_missing_one, destination.label))
        }
        if (chosen != null) ActionButton(stringResource(R.string.trip_destination_clear),
            onClick = { actions.onTripDestinationChange(null) })
        SupportingText(stringResource(R.string.trip_destination_scope))
    }
}
