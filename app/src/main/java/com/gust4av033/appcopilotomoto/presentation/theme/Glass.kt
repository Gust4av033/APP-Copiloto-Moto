package com.gust4av033.appcopilotomoto.presentation.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    Canvas(modifier.fillMaxSize()) {
        drawRect(if (dark) Color(0xFF0B1020) else Color(0xFFE9EEFA))
        glow(if (dark) Color(0xFF7250B8) else Color(0xFFB69CFF), Offset(size.width * .12f, size.height * .12f), size.width * .9f)
        glow(if (dark) Color(0xFF2873A5) else Color(0xFF6DDCFF), Offset(size.width * 1.06f, size.height * .42f), size.width * .95f)
        glow(if (dark) Color(0xFF375BB0) else Color(0xFF82AFFF), Offset(size.width * .10f, size.height * .92f), size.width * .85f)
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float) {
    drawRect(Brush.radialGradient(listOf(color.copy(alpha = .46f), color.copy(alpha = 0f)), center, radius))
}

/** Solo el material es translúcido; texto e iconos conservan su opacidad. */
object Glass {
    @Composable fun surface(strong: Boolean = false): Color {
        val dark = androidx.compose.foundation.isSystemInDarkTheme()
        return if (dark) Color(0xFF172039).copy(alpha = if (strong) .51f else .39f)
            else Color(0xFFFFFFFF).copy(alpha = if (strong) .76f else .64f)
    }

    @Composable fun outline(): Color = if (androidx.compose.foundation.isSystemInDarkTheme())
        Color.White.copy(alpha = .26f) else Color.White.copy(alpha = .88f)

    @Composable fun rim(): Brush = Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) .58f else .96f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = .28f),
        Color.White.copy(alpha = .10f)
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
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColor,
            border = border, shadowElevation = if (strong) 10.dp else 5.dp, content = content)
    } else {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color,
            contentColor = contentColor, border = border,
            shadowElevation = if (strong) 10.dp else 5.dp, content = content)
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
