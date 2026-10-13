package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class SpeechLanguagePolicyTest {
    @Test fun installedSpanishAndEnglishEnableAutomaticCommandsAndDictation() {
        val ready = SpeechLanguagePolicy.fromInstalled(36, listOf("es-ES", "en-US"), "es-SV")
        assertEquals(SpeechLanguagePhase.PREPARED, ready.phase)
        assertEquals("es-ES", ready.spanishTag)
        assertEquals("en-US", ready.englishTag)
        assertTrue(SpeechLanguagePolicy.automaticFor(SpeechCaptureMode.COMMAND, ready))
        assertTrue(SpeechLanguagePolicy.automaticFor(SpeechCaptureMode.DICTATION, ready))
        assertFalse(SpeechLanguagePolicy.automaticFor(SpeechCaptureMode.CONFIRMATION, ready))
        assertFalse(SpeechLanguagePolicy.automaticFor(SpeechCaptureMode.SONG_TITLE, ready))
        assertFalse(ready.switchObserved) // Model availability does not certify a successful switch.
    }

    @Test fun aMissingModelNeverAddsAnEnglishSwitchThatCouldAbortSpanish() {
        val missing = SpeechLanguagePolicy.fromInstalled(36, listOf("es-US"), "es-SV")
        assertEquals(SpeechLanguagePhase.MISSING_ENGLISH, missing.phase)
        assertEquals("es-US", missing.spanishTag)
        assertFalse(SpeechLanguagePolicy.automaticFor(SpeechCaptureMode.COMMAND, missing))
        val noSpanish = SpeechLanguagePolicy.fromInstalled(36, listOf("en-US"), "es-SV")
        assertEquals(SpeechLanguagePhase.UNAVAILABLE, noSpanish.phase)
        assertFalse(noSpanish.canRequestAutomatic)
    }

    @Test fun engineQueriesAndOldAndroidDoNotBecomeFalseBilingualSupport() {
        val old = SpeechLanguagePolicy.fromInstalled(33, listOf("es-ES", "en-US"), "es-ES")
        assertEquals(SpeechLanguagePhase.UNSUPPORTED, old.phase)
        assertFalse(old.canRequestAutomatic)
        listOf(SpeechLanguagePhase.NOT_CHECKED, SpeechLanguagePhase.CHECKING, SpeechLanguagePhase.FAILED).forEach {
            assertFalse(SpeechLanguagePolicy.automaticFor(SpeechCaptureMode.COMMAND,
                SpeechLanguageSnapshot(it, englishTag = "en-US")))
        }
    }

    @Test fun installedRegionalModelsAreUsedWithoutInventingADownloadedLocale() {
        val regional = SpeechLanguagePolicy.fromInstalled(36, listOf("es_MX", "en_GB", "fr-FR"), "es-MX")
        assertEquals("es-MX", regional.spanishTag)
        assertEquals("en-GB", regional.englishTag)
        assertTrue(regional.canRequestAutomatic)
        val us = SpeechLanguagePolicy.fromInstalled(36, listOf("es-MX", "en-GB", "en-US"), "es-MX")
        assertEquals("en-US", us.englishTag)
    }

    @Test fun aNativeRejectionFallsBackToSpanishDespitePreviouslyInstalledModels() {
        val ready = SpeechLanguagePolicy.fromInstalled(36, listOf("es-MX", "en-US"), "es-MX")
        val rejected = SpeechLanguagePolicy.rejectedAutomatic(ready, "rechazado")
        assertEquals("es-MX", rejected.spanishTag)
        assertEquals("en-US", rejected.englishTag)
        assertEquals("rechazado", rejected.detail)
        assertFalse(SpeechLanguagePolicy.automaticFor(SpeechCaptureMode.COMMAND, rejected))
        assertFalse(SpeechLanguagePolicy.automaticFor(SpeechCaptureMode.DICTATION, rejected))
    }
}
