package com.gust4av033.appcopilotomoto

/** Límites de la tarjeta dentro del espacio útil; no fuerza una altura de panel. */
internal data class OverlayPanelBounds(val widthPx: Int, val maxHeightPx: Int, val stackedActions: Boolean) {
    companion object {
        fun fit(width: Int, height: Int, density: Float, fontScale: Float): OverlayPanelBounds {
            val margin = (8 * density).toInt()
            val targetWidth = if (fontScale >= 1.3f) 264 else 244
            val widthPx = (targetWidth * density).toInt().coerceAtMost((width - margin * 2).coerceAtLeast(1))
            return OverlayPanelBounds(widthPx, (height - margin * 2).coerceAtLeast(1),
                fontScale >= 1.3f || widthPx / density < 224)
        }
    }
}
