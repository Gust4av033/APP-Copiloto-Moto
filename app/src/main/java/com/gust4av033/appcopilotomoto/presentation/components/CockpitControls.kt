package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.gust4av033.appcopilotomoto.R
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

/** No delayed service action can be orphaned by navigation or activity recreation. */
@Composable
internal fun HoldToStopButton(modifier: Modifier = Modifier, testTagName: String = "ride_stop_hold", onStop: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val stop by rememberUpdatedState(onStop)
    var confirm by remember { mutableStateOf(false) }
    val label = stringResource(R.string.ride_stop_hold)
    val shape = RoundedCornerShape(Glass.controlRadius)
    Row(modifier.testTag(testTagName).heightIn(min = Glass.cockpitButtonHeight)
        .clip(shape).background(Glass.rideSurface.copy(alpha = Glass.rideSurfaceAlpha))
        .border(Glass.rideListeningBorder, Glass.rideWarning, shape)
        .semantics(mergeDescendants = true) {
            role = Role.Button; contentDescription = label
            onClick { confirm = true; true }
            onLongClick(label) { stop(); true }
        }
        .onKeyEvent {
            if (it.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter)) {
                if (it.type == KeyEventType.KeyUp) confirm = true
                true
            } else false
        }.focusable()
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                coroutineScope {
                    val hold = launch {
                        launch { progress.animateTo(1f, tween(Glass.rideStopHoldMs, easing = LinearEasing)) }
                        // Animation duration scale must never shorten the required hold.
                        delay(Glass.rideStopHoldMs.toLong())
                        stop()
                    }
                    try { tryAwaitRelease() } finally { hold.cancel(); progress.snapTo(0f) }
                }
            })
        }.padding(Glass.cockpitGap), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Glass.cockpitGap, Alignment.CenterHorizontally)) {
        Box(Modifier.size(Glass.iconTileSize), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxSize(),
                color = Glass.rideWarning, trackColor = Glass.rideMuted, strokeWidth = Glass.rideListeningBorder)
            Icon(painterResource(R.drawable.ic_stop), null, Modifier.size(Glass.iconSize), tint = Glass.rideInk)
        }
        Text(label, color = Glass.rideInk, style = MaterialTheme.typography.labelMedium)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.ride_stop_confirm)) },
        confirmButton = { TextButton(onClick = { confirm = false; stop() }) { Text(stringResource(R.string.map_stop_short)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.ride_stop_cancel)) } })
}
