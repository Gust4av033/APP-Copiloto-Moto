package com.gust4av033.appcopilotomoto.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Shared material tokens: translucency belongs to surfaces, never to text or the entire UI. */
internal object StormGlass {
    val DayBackdrop = Color(0xFFD9E7F4)
    val NightBackdrop = Color(0xFF061426)
    val DayTint = Color(0xFFE0F0FF)
    val NightTint = Color(0xFF123A60)
    const val DayAlpha = .72f
    const val DayStrongAlpha = .84f
    const val NightAlpha = .46f
    const val NightStrongAlpha = .64f
    val DayGlows = listOf(Color(0xFF79B5E2), Color(0xFFA2DFFF), Color(0xFF82A9D4))
    val NightGlows = listOf(Color(0xFF15578E), Color(0xFF1975AB), Color(0xFF20496E))
    const val GlowAlpha = .42f
    val DayAction = Color(0xFF075BA8)
    val NightAction = Color(0xFF226BB5)
}

internal val LocalGlassDark = staticCompositionLocalOf { false }
