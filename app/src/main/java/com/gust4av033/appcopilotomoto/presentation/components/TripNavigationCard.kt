package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.NavigationRequestKind
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.TripNavigationContext

@Composable
fun TripNavigationCard(context: TripNavigationContext, screen: com.gust4av033.appcopilotomoto.WazeScreenSnapshot = com.gust4av033.appcopilotomoto.WazeScreenSnapshot()) {
    SectionCard(stringResource(R.string.trip_navigation_title)) {
        val request = context.lastDispatch
        if (request == null) SupportingText(stringResource(R.string.trip_navigation_empty))
        else {
            SupportingText(stringResource(if (request.kind == NavigationRequestKind.SEARCH)
                R.string.trip_navigation_search_sent else R.string.trip_navigation_favorite_sent,
                request.requestedDestination))
            if (request.kind == NavigationRequestKind.SEARCH)
                SupportingText(stringResource(R.string.trip_navigation_search_unconfirmed))
        }
        SupportingText(stringResource(R.string.trip_navigation_route_unknown))
        if (screen.turn == null) SupportingText(stringResource(R.string.trip_navigation_data_unavailable))
        else {
            SupportingText(stringResource(R.string.waze_screen_last, screen.turn.instruction, screen.turn.distance.orEmpty()))
            SupportingText(stringResource(R.string.waze_screen_last_scope))
        }
    }
}
