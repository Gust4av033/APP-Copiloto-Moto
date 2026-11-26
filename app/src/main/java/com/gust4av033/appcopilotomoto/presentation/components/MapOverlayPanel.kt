package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import com.gust4av033.appcopilotomoto.presentation.theme.GlassMode
import com.gust4av033.appcopilotomoto.presentation.theme.GlassPanel

/** Cartographic labels must not show through the controls, even over a bright map. */
@Composable
internal fun MapOverlayPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(Glass.cardRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = Glass.mapOverlayOpacity)) {
        GlassPanel(mode = GlassMode.LITE, strong = true, content = content)
    }
}
