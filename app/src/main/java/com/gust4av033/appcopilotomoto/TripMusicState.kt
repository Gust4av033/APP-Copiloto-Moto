package com.gust4av033.appcopilotomoto

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A rider's temporary player choice; the Settings preference is never changed here. */
internal object TripMusicState {
    var sessionId: String? = null
        private set
    var activePackage by mutableStateOf<String?>(null)
        private set
    var activeLabel by mutableStateOf<String?>(null)
        private set
    var cyclePlayingPackage: String? = null
        private set
    private var announcedPackage: String? = null

    fun beginSession(id: String?) {
        sessionId = id
        activePackage = null
        activeLabel = null
        cyclePlayingPackage = null
        announcedPackage = null
    }

    /** Returns true only for the first spoken notice for this player in this trip. */
    fun select(id: String?, player: MusicPlayer, preferredPackage: String?): Boolean {
        if (id == null || id != sessionId) return false
        activePackage = player.packageName
        activeLabel = player.label
        val announce = player.packageName != preferredPackage && announcedPackage != player.packageName
        if (announce) announcedPackage = player.packageName
        return announce
    }

    fun clear(id: String?) {
        if (id == null || id != sessionId) return
        activePackage = null
        activeLabel = null
    }

    fun rememberPlaying(id: String?, packageName: String?) {
        if (id != null && id == sessionId) cyclePlayingPackage = packageName
    }

    fun clearCycle(id: String?) {
        if (id != null && id == sessionId) cyclePlayingPackage = null
    }

    fun endSession() = beginSession(null)
}
