package com.gust4av033.appcopilotomoto

import java.util.Locale

enum class SpeechLanguagePhase { NOT_CHECKED, CHECKING, PREPARED, MISSING_ENGLISH, UNSUPPORTED, UNAVAILABLE, FAILED }

data class SpeechLanguageSnapshot(
    val phase: SpeechLanguagePhase = SpeechLanguagePhase.NOT_CHECKED,
    val spanishTag: String = "es-ES",
    val englishTag: String? = null,
    val lastDetectedTag: String = "",
    val switchObserved: Boolean = false,
    val downloading: Boolean = false,
    val detail: String = ""
) {
    val canRequestAutomatic: Boolean get() = phase == SpeechLanguagePhase.PREPARED && englishTag != null
}

/** Installed models are a prerequisite, not proof that a vendor implements language switching. */
internal object SpeechLanguagePolicy {
    fun fromInstalled(api: Int, installed: List<String>, preferredSpanish: String): SpeechLanguageSnapshot {
        val tags = installed.map { it.replace('_', '-') }.distinct()
        val spanish = tags.firstOrNull { it.equals(preferredSpanish, true) && language(it) == "es" }
            ?: tags.firstOrNull { language(it) == "es" } ?: preferredSpanish.takeIf { language(it) == "es" } ?: "es-ES"
        val english = tags.firstOrNull { it.equals("en-US", true) }
            ?: tags.firstOrNull { language(it) == "en" }
        val phase = when {
            api < 34 -> SpeechLanguagePhase.UNSUPPORTED
            installed.none { language(it) == "es" } -> SpeechLanguagePhase.UNAVAILABLE
            english == null -> SpeechLanguagePhase.MISSING_ENGLISH
            else -> SpeechLanguagePhase.PREPARED
        }
        return SpeechLanguageSnapshot(phase, spanish, english)
    }

    fun automaticFor(mode: SpeechCaptureMode, snapshot: SpeechLanguageSnapshot): Boolean =
        snapshot.canRequestAutomatic && (mode == SpeechCaptureMode.COMMAND || mode == SpeechCaptureMode.DICTATION)

    fun rejectedAutomatic(snapshot: SpeechLanguageSnapshot, reason: String): SpeechLanguageSnapshot =
        snapshot.copy(phase = SpeechLanguagePhase.FAILED, detail = reason)

    private fun language(tag: String) = Locale.forLanguageTag(tag.replace('_', '-')).language
}
