package com.gust4av033.appcopilotomoto

data class OverlayPoint(val x: Int, val y: Int)

/** Posición relativa al espacio utilizable: mantiene el borde y vuelve a limitar al girar. */
data class OverlayAnchor(val right: Boolean = true, val verticalFraction: Float = 0.45f) {
    fun place(width: Int, height: Int, viewWidth: Int, viewHeight: Int, margin: Int): OverlayPoint {
        val maxX = (width - viewWidth - margin).coerceAtLeast(0)
        val minX = margin.coerceAtMost(maxX).coerceAtLeast(0)
        val maxY = (height - viewHeight - margin).coerceAtLeast(0)
        val minY = margin.coerceAtMost(maxY).coerceAtLeast(0)
        val fraction = verticalFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.45f
        return OverlayPoint(if (right) maxX else minX, minY + ((maxY - minY) * fraction).toInt())
    }
}
