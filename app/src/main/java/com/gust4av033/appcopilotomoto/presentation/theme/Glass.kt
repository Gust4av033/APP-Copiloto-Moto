package com.gust4av033.appcopilotomoto.presentation.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

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
    drawRect(Brush.radialGradient(listOf(color.copy(alpha = .34f), color.copy(alpha = 0f)), center, radius))
}

/** Solo el material es translúcido; texto e iconos conservan su opacidad. */
object Glass {
    @Composable fun surface(strong: Boolean = false): Color {
        val dark = androidx.compose.foundation.isSystemInDarkTheme()
        return if (dark) Color(0xFF172039).copy(alpha = if (strong) .88f else .76f)
            else Color(0xFFFFFFFF).copy(alpha = if (strong) .92f else .79f)
    }

    @Composable fun outline(): Color = if (androidx.compose.foundation.isSystemInDarkTheme())
        Color.White.copy(alpha = .17f) else Color.White.copy(alpha = .8f)

    @Composable fun accent(): Color = MaterialTheme.colorScheme.tertiary
}
