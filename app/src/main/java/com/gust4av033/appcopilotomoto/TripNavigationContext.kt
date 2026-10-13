package com.gust4av033.appcopilotomoto

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class NavigationRequestKind { FAVORITE, SEARCH }

/** Evidence of an APP dispatch, never evidence that the external navigator started a route. */
data class NavigationDispatch(
    val providerId: String,
    val kind: NavigationRequestKind,
    val requestedDestination: String,
    val dispatchedAtEpochMs: Long
)

data class TripNavigationContext(
    val sessionId: String? = null,
    val lastDispatch: NavigationDispatch? = null
) {
    fun record(session: String?, dispatch: NavigationDispatch): TripNavigationContext =
        if (sessionId != null && session == sessionId) copy(lastDispatch = dispatch) else this
}

/** Main-thread session state, observed by UI. Not persisted as a live route across process death. */
object TripNavigationState {
    var context by mutableStateOf(TripNavigationContext())
        private set

    fun beginSession() { context = TripNavigationContext(sessionId = UUID.randomUUID().toString()) }
    fun endSession() { context = TripNavigationContext() }
    fun record(session: String?, dispatch: NavigationDispatch) { context = context.record(session, dispatch) }
}
