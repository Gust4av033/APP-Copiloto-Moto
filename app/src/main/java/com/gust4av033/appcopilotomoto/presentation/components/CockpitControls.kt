package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.presentation.theme.Glass

/** Compact illuminated controls; actual actions/states, with no decorative dead buttons. */
@Composable
internal fun CockpitButton(label: String, icon: Int?, modifier: Modifier = Modifier,
    selected: Boolean = false, primary: Boolean = false, destructive: Boolean = false,
    enabled: Boolean = true, description: String = label, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Glass.controlRadius)
    val fill = when {
        !enabled -> listOf(colors.surfaceVariant, colors.surface)
        destructive -> listOf(Glass.destructiveTop, Glass.destructiveBottom)
        primary -> listOf(Glass.primaryTop, Glass.primaryBottom)
        selected -> listOf(colors.secondaryContainer, colors.primaryContainer)
        else -> listOf(colors.surfaceVariant, colors.surface)
    }
    val ink = if (!enabled) colors.onSurfaceVariant else if (primary || destructive) androidx.compose.ui.graphics.Color.White else colors.onSurface
    Column(modifier.heightIn(min = Glass.cockpitButtonHeight).clip(shape).background(Brush.verticalGradient(fill))
        .border(Glass.rimWidth, colors.primary.copy(alpha = Glass.cockpitBorderAlpha), shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description; this.selected = selected }
        .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(Glass.iconSize), tint = ink)
        Text(label, color = if (enabled) ink else colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}
