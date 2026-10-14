package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.presentation.theme.Glass

@Composable
fun IconTile(iconRes: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(Glass.iconTileSize).clip(RoundedCornerShape(Glass.controlRadius))
        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .40f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .24f))))
        .border(Glass.rimWidth, Glass.rim(), RoundedCornerShape(Glass.controlRadius)),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(iconRes), null, Modifier.size(Glass.iconSize),
            tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun GlassIconButton(iconRes: Int, description: String, enabled: Boolean = true,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(Glass.iconButtonSize).clip(CircleShape)
        .background(Glass.surface()).border(Glass.rimWidth, Glass.rim(), CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(iconRes), description, Modifier.size(Glass.iconSize),
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PageIndicator(page: Int, count: Int, modifier: Modifier = Modifier) {
    val glow = Glass.accent()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val active = index == page
            Box(Modifier.size(width = if (active) Glass.pageActiveWidth else Glass.pageDot,
                height = Glass.pageDot).drawBehind {
                    if (active) drawCircle(Brush.radialGradient(listOf(
                        glow.copy(alpha = Glass.pageGlowAlpha), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f), radius = size.width),
                        radius = size.width, center = Offset(size.width / 2f, size.height / 2f))
                }.clip(CircleShape)
                .background(if (active) Brush.horizontalGradient(listOf(Glass.primaryTop, Glass.accent()))
                    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.outline,
                        MaterialTheme.colorScheme.outline))))
        }
    }
}
