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
    primary = Color(0xFF006B69), onPrimary = Color.White,
    primaryContainer = Color(0xFFD2EFEB), onPrimaryContainer = Color(0xFF073B3C),
    secondary = Color(0xFF425C70), secondaryContainer = Color(0xFFE0EAF1), onSecondaryContainer = Color(0xFF173246),
    tertiary = Color(0xFF895A16), tertiaryContainer = Color(0xFFFFEDD0), onTertiaryContainer = Color(0xFF503300),
    background = Color(0xFFF2F5F8), onBackground = Color(0xFF172B3A),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF172B3A),
    surfaceVariant = Color(0xFFE7EDF2), onSurfaceVariant = Color(0xFF526574),
    outline = Color(0xFF768994), outlineVariant = Color(0xFFDCE5EB)
)
private val NightColors = darkColorScheme(
    primary = Color(0xFF80DAD0), onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF104D50), onPrimaryContainer = Color(0xFFBAF4EA),
    secondary = Color(0xFFB0CBDD), secondaryContainer = Color(0xFF273F50), onSecondaryContainer = Color(0xFFD9EAF6),
    tertiary = Color(0xFFEBC17E), tertiaryContainer = Color(0xFF574117), onTertiaryContainer = Color(0xFFFFE6B9),
    background = Color(0xFF0C1722), onBackground = Color(0xFFE0ECF4),
    surface = Color(0xFF142432), onSurface = Color(0xFFE0ECF4),
    surfaceVariant = Color(0xFF203441), onSurfaceVariant = Color(0xFFB3C5D1),
    outline = Color(0xFF8BA2AF), outlineVariant = Color(0xFF2C424F)
)
private val CopilotType = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
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
