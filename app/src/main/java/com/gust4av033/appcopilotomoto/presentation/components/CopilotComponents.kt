package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
                 iconRes: Int? = null, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 52.dp)
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = if (LocalGlassDark.current) StormGlass.NightAction else StormGlass.DayAction,
                contentColor = androidx.compose.ui.graphics.Color.White)) {
            if (iconRes != null) {
                Icon(painterResource(iconRes), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label)
        }
    } else {
        val outline = Glass.outline()
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .66f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
            border = BorderStroke(1.dp, outline.copy(alpha = if (enabled) outline.alpha else .12f))) {
            if (iconRes != null) {
                Icon(painterResource(iconRes), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label)
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

