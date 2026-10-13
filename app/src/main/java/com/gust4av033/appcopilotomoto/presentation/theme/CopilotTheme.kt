package com.gust4av033.appcopilotomoto.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DayColors = lightColorScheme(
    primary = Color(0xFF7136B8), onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDDFD), onPrimaryContainer = Color(0xFF32144F),
    secondary = Color(0xFF145BB7), secondaryContainer = Color(0xFFD9E9FF), onSecondaryContainer = Color(0xFF102C55),
    tertiary = Color(0xFF006C89), tertiaryContainer = Color(0xFFBAEFFF), onTertiaryContainer = Color(0xFF003548),
    background = Color(0xFFE9EEFA), onBackground = Color(0xFF20253D),
    surface = Color(0xFFF9FBFF), onSurface = Color(0xFF20253D),
    surfaceVariant = Color(0xFFEAE4F4), onSurfaceVariant = Color(0xFF5D526F),
    outline = Color(0xFF7D7192), outlineVariant = Color(0xFFDFD7EF)
)
private val NightColors = darkColorScheme(
    primary = Color(0xFFB69CFF), onPrimary = Color(0xFF251442),
    primaryContainer = Color(0xFF4B286D), onPrimaryContainer = Color(0xFFEBDDFD),
    secondary = Color(0xFF82AFFF), secondaryContainer = Color(0xFF183864), onSecondaryContainer = Color(0xFFD9E9FF),
    tertiary = Color(0xFF6DDCFF), tertiaryContainer = Color(0xFF004C64), onTertiaryContainer = Color(0xFFBAEFFF),
    background = Color(0xFF0B1020), onBackground = Color(0xFFF4F6FF),
    surface = Color(0xFF151D31), onSurface = Color(0xFFF4F6FF),
    surfaceVariant = Color(0xFF24304B), onSurfaceVariant = Color(0xFFBDC5DA),
    outline = Color(0xFF9288A6), outlineVariant = Color(0xFF3E3455)
)
private val CopilotType = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
)
private val CopilotShapes = Shapes(
    small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)
)

@Composable
fun CopilotTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) NightColors else DayColors,
        typography = CopilotType, shapes = CopilotShapes, content = content)
}
