package com.gust4av033.appcopilotomoto.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val DayColors = lightColorScheme(
    primary = Color(0xFF075BA8), onPrimary = Color.White,
    primaryContainer = Color(0xFFC6E3FD), onPrimaryContainer = Color(0xFF082D50),
    secondary = Color(0xFF235F96), secondaryContainer = Color(0xFFC4DFF6), onSecondaryContainer = Color(0xFF103150),
    tertiary = Color(0xFF006C89), tertiaryContainer = Color(0xFFBAEFFF), onTertiaryContainer = Color(0xFF003548),
    background = StormGlass.DayBackdrop, onBackground = Color(0xFF102D46),
    surface = Color(0xFFEAF4FC), onSurface = Color(0xFF102D46),
    surfaceVariant = Color(0xFFCBDDED), onSurfaceVariant = Color(0xFF3D536B),
    outline = Color(0xFF597992), outlineVariant = Color(0xFFA5C3DD)
)
internal val NightColors = darkColorScheme(
    primary = Color(0xFF97CBFA), onPrimary = Color(0xFF082D50),
    primaryContainer = Color(0xFF153F66), onPrimaryContainer = Color(0xFFE0F1FF),
    secondary = Color(0xFF92C9FA), secondaryContainer = Color(0xFF163E63), onSecondaryContainer = Color(0xFFE0F1FF),
    tertiary = Color(0xFF6DDCFF), tertiaryContainer = Color(0xFF004C64), onTertiaryContainer = Color(0xFFBAEFFF),
    background = StormGlass.NightBackdrop, onBackground = Color(0xFFEAF5FF),
    surface = Color(0xFF0D2740), onSurface = Color(0xFFEAF5FF),
    surfaceVariant = Color(0xFF183952), onSurfaceVariant = Color(0xFFBCD4E8),
    outline = Color(0xFF78A6C8), outlineVariant = Color(0xFF305777)
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
    CompositionLocalProvider(LocalGlassDark provides darkTheme) {
        MaterialTheme(colorScheme = if (darkTheme) NightColors else DayColors,
            typography = CopilotType, shapes = CopilotShapes, content = content)
    }
}

