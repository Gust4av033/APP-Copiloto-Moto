package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class TripMusicStateTest {
    @Test fun choiceIsBoundToOneTripAndNoticeIsOncePerPlayer() {
        val spotify = MusicPlayer("com.spotify.music", "Spotify", false)
        TripMusicState.beginSession("trip-1")
        assertFalse(TripMusicState.select("old-trip", spotify, MusicPlayerSelection.POWERAMP_PACKAGE))
        assertTrue(TripMusicState.select("trip-1", spotify, MusicPlayerSelection.POWERAMP_PACKAGE))
        TripMusicState.rememberPlaying("trip-1", spotify.packageName)
        assertEquals(spotify.packageName, TripMusicState.cyclePlayingPackage)
        TripMusicState.clearCycle("trip-1")
        assertNull(TripMusicState.cyclePlayingPackage)
        assertFalse(TripMusicState.select("trip-1", spotify, MusicPlayerSelection.POWERAMP_PACKAGE))
        assertEquals(spotify.packageName, TripMusicState.activePackage)
        TripMusicState.clear("trip-1")
        assertNull(TripMusicState.activePackage)
        TripMusicState.endSession()
        assertNull(TripMusicState.activePackage)
        TripMusicState.beginSession("trip-2")
        assertTrue(TripMusicState.select("trip-2", spotify, MusicPlayerSelection.POWERAMP_PACKAGE))
        TripMusicState.endSession()
    }
}
