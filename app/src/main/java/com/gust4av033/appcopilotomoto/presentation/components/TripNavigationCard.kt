package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.NavigationRequestKind
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.TripNavigationContext

@Composable
fun TripNavigationCard(context: TripNavigationContext) {
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
        SupportingText(stringResource(R.string.trip_navigation_data_unavailable))
    }
}
