package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.ChoiceUi

@Composable
fun SessionStatus(active: Boolean) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            stringResource(if (active) R.string.trip_active else R.string.trip_stopped),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
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
fun ActionButton(label: String, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 52.dp)
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    }
}

@Composable
fun ChoiceGroup(options: List<ChoiceUi>, selectedId: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp).selectable(
                    selected = selectedId == option.id,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onSelect(option.id) }
                ).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selectedId == option.id, enabled = enabled, onClick = null)
                Text(stringResource(option.titleRes), modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}
