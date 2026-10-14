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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gust4av033.appcopilotomoto.R

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
    primary = Color(0xFF8DB4FF), onPrimary = Color(0xFF06142F),
    primaryContainer = Color(CopilotPalette.Storm), onPrimaryContainer = Color(0xFFDDE8FF),
    secondary = Color(0xFF5C9BFF), secondaryContainer = Color(0xFF0F2E73), onSecondaryContainer = Color(0xFFEAF1FF),
    tertiary = Color(0xFF6DDCFF), tertiaryContainer = Color(0xFF004C64), onTertiaryContainer = Color(0xFFBAEFFF),
    background = StormGlass.NightBackdrop, onBackground = Color(0xFFEAF1FF),
    surface = Color(CopilotPalette.Abyss), onSurface = Color(0xFFEAF1FF),
    surfaceVariant = Color(0xFF14244D), onSurfaceVariant = Color(0xFFC3D3F2),
    outline = Color(0xFF6C7FA8), outlineVariant = Color(0xFF2A3B66)
)
private val AppFont = FontFamily(Font(R.font.manrope))
private val CopilotType = Typography(
    displayLarge = TextStyle(fontFamily = AppFont, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = AppFont, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = AppFont, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = AppFont, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = AppFont, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = AppFont, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = AppFont, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = AppFont, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = AppFont, fontSize = 11.sp)
)
private val CopilotShapes = Shapes(
    small = RoundedCornerShape(Glass.controlRadius), medium = RoundedCornerShape(Glass.cardRadius),
    large = RoundedCornerShape(Glass.heroRadius)
)

@Composable
fun CopilotTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassDark provides darkTheme) {
        MaterialTheme(colorScheme = if (darkTheme) NightColors else DayColors,
            typography = CopilotType, shapes = CopilotShapes, content = content)
    }
}

