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
    val ownDestination: TargetDestination? = null,
    val ownDestinationSetAtEpochMs: Long = 0L,
    val ownDestinationSetAtElapsedMs: Long = 0L
) {
    fun record(session: String?, dispatch: NavigationDispatch): TripNavigationContext =
        if (sessionId != null && session == sessionId) {
            if (dispatch.kind == NavigationRequestKind.FAVORITE && ownDestination != null &&
                ownDestination != dispatch.target) copy(lastDispatch = dispatch, ownDestination = null,
                ownDestinationSetAtEpochMs = 0, ownDestinationSetAtElapsedMs = 0)
            else copy(lastDispatch = dispatch)
        } else this

    fun chooseOwnDestination(session: String?, destination: TargetDestination?,
        nowEpoch: Long = 0L, nowElapsed: Long = 0L): TripNavigationContext =
        if (sessionId != null && session == sessionId) copy(ownDestination = destination,
            ownDestinationSetAtEpochMs = if (destination == null) 0 else nowEpoch,
            ownDestinationSetAtElapsedMs = if (destination == null) 0 else nowElapsed) else this
}

/** Main-thread session state, observed by UI. Not persisted as a live route across process death. */
object TripNavigationState {
    var context by mutableStateOf(TripNavigationContext())
        private set

    fun beginSession(destination: TargetDestination? = null) {
        val nowElapsed = SystemClock.elapsedRealtime()
        context = TripNavigationContext(sessionId = UUID.randomUUID().toString(),
            startedAtElapsedRealtimeMs = nowElapsed, ownDestination = destination,
            ownDestinationSetAtEpochMs = if (destination == null) 0 else System.currentTimeMillis(),
            ownDestinationSetAtElapsedMs = if (destination == null) 0 else nowElapsed)
        WazeNotificationObservation.beginSession(context.sessionId)
        WazeScreenState.session(context.sessionId)
    }
    fun endSession() { context = TripNavigationContext(); WazeNotificationObservation.endSession(); WazeScreenState.session(null) }
    fun record(session: String?, dispatch: NavigationDispatch) { context = context.record(session, dispatch) }
    fun chooseOwnDestination(session: String?, destination: TargetDestination?) {
        context = context.chooseOwnDestination(session, destination,
            System.currentTimeMillis(), SystemClock.elapsedRealtime())
    }
}
