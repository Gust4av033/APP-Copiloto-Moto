package com.gust4av033.appcopilotomoto.presentation.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Fondo propio y estático: ninguna lectura de pantalla ni blur de controles. */
@Composable
fun CopilotBackdrop(modifier: Modifier = Modifier) {
    val dark = LocalGlassDark.current
    Canvas(modifier.fillMaxSize()) {
        drawRect(if (dark) StormGlass.NightBackdrop else StormGlass.DayBackdrop)
        val colors = if (dark) StormGlass.NightGlows else StormGlass.DayGlows
        glow(colors[0], Offset(size.width * .05f, size.height * .08f), size.width * 1.0f)
        glow(colors[1], Offset(size.width * 1.04f, size.height * .43f), size.width * .92f)
        glow(colors[2], Offset(size.width * .10f, size.height * .95f), size.width * .9f)
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float) {
    drawRect(Brush.radialGradient(listOf(color.copy(alpha = StormGlass.GlowAlpha), color.copy(alpha = 0f)), center, radius))
}

/** Solo el material es translúcido; texto e iconos conservan su opacidad. */
object Glass {
    @Composable fun surface(strong: Boolean = false): Color {
        val dark = LocalGlassDark.current
        return if (dark) StormGlass.NightTint.copy(alpha = if (strong) StormGlass.NightStrongAlpha else StormGlass.NightAlpha)
            else StormGlass.DayTint.copy(alpha = if (strong) StormGlass.DayStrongAlpha else StormGlass.DayAlpha)
    }

    @Composable fun outline(): Color = if (LocalGlassDark.current)
        Color(0xFF9AD6FF).copy(alpha = .36f) else Color.White.copy(alpha = .90f)

    @Composable fun rim(): Brush = Brush.linearGradient(listOf(
        Color(0xFFE0F4FF).copy(alpha = if (LocalGlassDark.current) .66f else .98f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = .32f),
        Color(0xFF70B7EE).copy(alpha = .12f)
    ))

    @Composable fun accent(): Color = MaterialTheme.colorScheme.tertiary
}

/** Colorea solo el panel. Surface fija el color de texto y conserva el ripple de clic. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val color = Glass.surface(strong)
    val border = BorderStroke(1.dp, Glass.rim())
    val contentColor = MaterialTheme.colorScheme.onSurface
    // A static reflection is painted behind content; there is no screenshot blur or moving texture.
    val reflection = Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (LocalGlassDark.current) .08f else .18f),
        Color.White.copy(alpha = .01f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = .035f)
    ))
    val reflectedContent: @Composable () -> Unit = { Box(Modifier.background(reflection)) { content() } }
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColor,
            border = border, shadowElevation = if (strong) 12.dp else 6.dp, content = reflectedContent)
    } else {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color,
            contentColor = contentColor, border = border,
            shadowElevation = if (strong) 12.dp else 6.dp, content = reflectedContent)
    }
}

@Composable
fun glassTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)
