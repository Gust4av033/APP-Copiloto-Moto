package com.gust4av033.appcopilotomoto

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.SystemClock
import java.util.UUID

enum class NavigationRequestKind { FAVORITE, SEARCH }

/** Evidence of an APP dispatch, never evidence that the external navigator started a route. */
data class NavigationDispatch(
    val providerId: String,
    val kind: NavigationRequestKind,
    val requestedDestination: String,
    val dispatchedAtEpochMs: Long,
    val target: TargetDestination? = null,
    val dispatchedAtElapsedRealtimeMs: Long = 0L
)

data class TripNavigationContext(
    val sessionId: String? = null,
    val lastDispatch: NavigationDispatch? = null,
    val startedAtElapsedRealtimeMs: Long = 0L,
    /** APP's chosen destination; an external dispatch is not a route of APP. */
    val ownDestination: TargetDestination? = null
) {
    fun record(session: String?, dispatch: NavigationDispatch): TripNavigationContext =
        if (sessionId != null && session == sessionId) copy(lastDispatch = dispatch) else this

    fun chooseOwnDestination(session: String?, destination: TargetDestination?): TripNavigationContext =
        if (sessionId != null && session == sessionId) copy(ownDestination = destination) else this
}

/** Main-thread session state, observed by UI. Not persisted as a live route across process death. */
object TripNavigationState {
    var context by mutableStateOf(TripNavigationContext())
        private set

    fun beginSession() {
        context = TripNavigationContext(sessionId = UUID.randomUUID().toString(),
            startedAtElapsedRealtimeMs = SystemClock.elapsedRealtime())
        WazeNotificationObservation.beginSession(context.sessionId)
        WazeScreenState.session(context.sessionId)
    }
    fun endSession() { context = TripNavigationContext(); WazeNotificationObservation.endSession(); WazeScreenState.session(null) }
    fun record(session: String?, dispatch: NavigationDispatch) { context = context.record(session, dispatch) }
    fun chooseOwnDestination(session: String?, destination: TargetDestination?) {
        context = context.chooseOwnDestination(session, destination)
    }
}
