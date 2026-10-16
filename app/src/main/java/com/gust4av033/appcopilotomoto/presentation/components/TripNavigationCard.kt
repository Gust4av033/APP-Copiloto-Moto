package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.NavigationRequestKind
import com.gust4av033.appcopilotomoto.NavigationApp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.TripNavigationContext
import com.gust4av033.appcopilotomoto.TripContextSnapshot
import com.gust4av033.appcopilotomoto.ContextValue
import com.gust4av033.appcopilotomoto.Freshness
import com.gust4av033.appcopilotomoto.EtaSource
import kotlin.math.roundToInt

@Composable
fun TripNavigationCard(context: TripNavigationContext, observed: TripContextSnapshot = TripContextSnapshot(),
    nowElapsed: Long = 0L, noticesReady: Boolean = true) {
    SectionCard(stringResource(R.string.trip_navigation_title)) {
        val request = context.lastDispatch
        if (request == null) SupportingText(stringResource(R.string.trip_navigation_empty))
        else {
            val provider = NavigationApp.fromId(request.providerId)
            SupportingText(stringResource(if (request.kind == NavigationRequestKind.SEARCH)
                R.string.trip_navigation_search_sent else R.string.trip_navigation_favorite_sent,
                provider.label, request.requestedDestination))
            if (request.kind == NavigationRequestKind.SEARCH)
                SupportingText(stringResource(R.string.trip_navigation_search_unconfirmed))
        }
        fun <T> ContextValue<T>?.recent(): ContextValue<T>? = this?.takeIf {
            nowElapsed > 0 && it.freshness(nowElapsed) != Freshness.EXPIRED
        }
        val route = observed.navigation
        val eta = route.remainingMinutes.recent()
        val distance = route.remainingDistanceText.recent()
        val arrival = route.arrivalClockText.recent()
        val turn = route.nextTurnInstruction.recent()
        val turnDistance = route.distanceToNextTurnText.recent()
            ?.takeIf { it.source == turn?.source &&
                it.capturedAtElapsedRealtimeMs == turn.capturedAtElapsedRealtimeMs }
        val street = route.currentOrTargetStreet.recent()
        val alert = route.routeAlertText.recent()
        val hasLiveData = listOf(eta, distance, arrival, turn, street, alert).any { it != null }
        val lastEta = observed.lastObservedEta?.takeIf { nowElapsed > 0 && it.valid(nowElapsed) }
        if (hasLiveData) {
            val provider = route.activeProvider.recent()?.value.orEmpty()
            SupportingText(stringResource(R.string.trip_navigation_observed_source, provider))
            eta?.let { SupportingText(stringResource(R.string.trip_navigation_eta, it.value)) }
            distance?.let { SupportingText(stringResource(R.string.trip_navigation_remaining_distance, it.value)) }
            arrival?.let { SupportingText(stringResource(R.string.trip_navigation_arrival_clock, it.value)) }
            turn?.let { SupportingText(stringResource(R.string.trip_navigation_next_turn, it.value)) }
            turnDistance?.let { SupportingText(stringResource(R.string.trip_navigation_turn_distance, it.value)) }
            street?.let { SupportingText(stringResource(R.string.trip_navigation_street, it.value)) }
            alert?.let { SupportingText(stringResource(R.string.trip_navigation_alert, it.value)) }
            if (eta == null && lastEta != null) {
                val lastProvider = if (lastEta.source == EtaSource.GOOGLE_MAPS) "Google Maps" else "Waze"
                SupportingText(stringResource(R.string.trip_navigation_last_eta, lastProvider,
                    lastEta.remainingMinutes, lastEta.ageMs(nowElapsed) / 1000))
            }
        } else if (lastEta != null) {
            val provider = if (lastEta.source == EtaSource.GOOGLE_MAPS) "Google Maps" else "Waze"
            SupportingText(stringResource(R.string.trip_navigation_last_eta, provider,
                lastEta.remainingMinutes, lastEta.ageMs(nowElapsed) / 1000))
        } else {
            SupportingText(stringResource(if (noticesReady) R.string.trip_navigation_data_unavailable
                else R.string.trip_navigation_notice_access_missing))
        }
        observed.sessionDurationMs.recent()?.value?.takeIf { it >= 60_000 }?.let {
            SupportingText(stringResource(R.string.trip_navigation_app_duration, it / 60_000))
        }
        if (distance == null) {
            val target = observed.target.recent()?.value
            if (target != null) {
                val meters = observed.straightLineMeters(nowElapsed)
                if (meters != null) {
                    val shown = if (meters < 1_000)
                        "${((meters / 100).roundToInt().coerceAtLeast(1)) * 100} m"
                    else "${((meters / 100).roundToInt() / 10.0).toString().replace('.', ',')} km"
                    SupportingText(stringResource(R.string.trip_navigation_own_distance, target.label, shown))
                } else SupportingText(stringResource(R.string.trip_navigation_gps_unavailable, target.label))
            }
        }
        SupportingText(stringResource(if (hasLiveData || lastEta != null)
            R.string.trip_navigation_observed_scope else R.string.trip_navigation_route_unknown))
    }
}
