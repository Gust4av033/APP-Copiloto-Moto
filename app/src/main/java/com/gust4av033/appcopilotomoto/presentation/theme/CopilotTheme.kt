package com.gust4av033.appcopilotomoto.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DayColors = lightColorScheme(
    primary = Color(0xFF006B60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA4F2DF),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A635D),
    secondaryContainer = Color(0xFFCDE8E0),
    onSecondaryContainer = Color(0xFF07201A),
    background = Color(0xFFF3F6F4),
    onBackground = Color(0xFF15211E),
    surface = Color(0xFFFAFDFB),
    onSurface = Color(0xFF15211E),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF3E4944),
    outline = Color(0xFF6D7973)
)

private val NightColors = darkColorScheme(
    primary = Color(0xFF83D6C3),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFA4F2DF),
    secondary = Color(0xFFB1CCC3),
    secondaryContainer = Color(0xFF324B44),
    onSecondaryContainer = Color(0xFFCDE8E0),
    background = Color(0xFF101815),
    onBackground = Color(0xFFE1EAE4),
    surface = Color(0xFF17211D),
    onSurface = Color(0xFFE1EAE4),
    surfaceVariant = Color(0xFF3E4944),
    onSurfaceVariant = Color(0xFFBEC9C1),
    outline = Color(0xFF88948C)
)

@Composable
fun CopilotTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) NightColors else DayColors, content = content)
}
