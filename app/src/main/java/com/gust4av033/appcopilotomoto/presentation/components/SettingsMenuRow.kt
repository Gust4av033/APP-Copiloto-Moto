package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.SettingsSection
import com.gust4av033.appcopilotomoto.presentation.theme.GlassPanel

@Composable
fun SettingsMenuRow(section: SettingsSection, value: String?, onClick: () -> Unit) {
    GlassPanel(onClick = onClick, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 76.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .74f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Icon(painterResource(section.iconRes), null, Modifier.padding(10.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(section.titleRes), style = MaterialTheme.typography.titleMedium)
                if (value == null) SupportingText(stringResource(section.hintRes))
                if (value != null) Text(value, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_expand), null, Modifier.size(20.dp).rotate(-90f),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

