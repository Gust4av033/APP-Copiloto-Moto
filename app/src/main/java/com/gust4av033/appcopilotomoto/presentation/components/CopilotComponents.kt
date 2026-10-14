package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.ChoiceUi
import com.gust4av033.appcopilotomoto.TripCallPhase
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import com.gust4av033.appcopilotomoto.presentation.theme.GlassPanel
import com.gust4av033.appcopilotomoto.presentation.theme.StormGlass
import com.gust4av033.appcopilotomoto.presentation.theme.LocalGlassDark

@Composable
fun SessionStatus(active: Boolean, restoring: Boolean = false, callPhase: TripCallPhase = TripCallPhase.NONE) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            stringResource(when {
                restoring -> R.string.session_restoring
                callPhase == TripCallPhase.CALL -> R.string.session_call
                callPhase == TripCallPhase.RECOVERING -> R.string.session_call_recovering
                active -> R.string.trip_active
                else -> R.string.trip_stopped
            }),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
fun SupportingText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ToggleSettingRow(label: String, checked: Boolean, enabled: Boolean = true,
                     onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun ActionButton(label: String, enabled: Boolean = true, primary: Boolean = false,
                 destructive: Boolean = false, iconRes: Int? = null,
                 disabledReason: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) Glass.pressScale else 1f, label = "button press")
    val shape = RoundedCornerShape(Glass.controlRadius)
    val tint = when {
        !enabled -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant))
        destructive -> Brush.verticalGradient(listOf(Glass.destructiveTop, Glass.destructiveBottom))
        primary -> Brush.verticalGradient(listOf(Glass.primaryTop, Glass.primaryBottom))
        else -> Brush.verticalGradient(listOf(Glass.surface(), MaterialTheme.colorScheme.secondaryContainer.copy(alpha = Glass.secondaryButtonAlpha)))
    }
    val foreground = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        primary || destructive -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(Modifier.fillMaxWidth().heightIn(min = Glass.actionHeight)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape).background(tint)
        .border(Glass.rimWidth, if (pressed) Brush.linearGradient(listOf(Color.White, Glass.accent())) else Glass.rim(), shape)
        .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction,
            indication = null, onClick = onClick)
        .then(if (!enabled && disabledReason != null) Modifier.semantics { stateDescription = disabledReason }
            else Modifier), contentAlignment = Alignment.Center) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            if (iconRes != null) {
                Icon(painterResource(iconRes), null, Modifier.size(20.dp), tint = foreground)
                Spacer(Modifier.width(8.dp))
            }
            Text(label, color = foreground, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ChoiceGroup(options: List<ChoiceUi>, selectedId: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val selected = selectedId == option.id
            Surface(shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f)
                    else androidx.compose.ui.graphics.Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .62f)
                    else Glass.outline())) {
                Row(
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp).selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.id) }
                    ).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, enabled = enabled, onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(option.titleRes), style = MaterialTheme.typography.titleSmall)
                        option.descriptionRes?.let { SupportingText(stringResource(it)) }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableSectionCard(title: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(initiallyExpanded) }
    GlassPanel(Modifier.fillMaxWidth()) {
        Column {
        Surface(onClick = { expanded = !expanded }, color = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.ic_expand),
                    stringResource(if (expanded) R.string.details_hide else R.string.details_show),
                    Modifier.rotate(if (expanded) 180f else 0f), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (expanded) Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

