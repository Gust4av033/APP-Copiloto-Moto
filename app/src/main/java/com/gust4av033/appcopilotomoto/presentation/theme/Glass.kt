package com.gust4av033.appcopilotomoto.presentation.theme

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RadialGradient as AndroidRadialGradient
import android.graphics.LinearGradient as AndroidLinearGradient
import android.graphics.Shader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Backdrop, tint and contrast tokens for both themes. */
internal object StormGlass {
    val DayBackdrop = Color(0xFFDCE9F7)
    val NightBackdrop = Color(CopilotPalette.Midnight)
    val DayTint = Color(0xFFE0F0FF)
    val NightTint = Color(CopilotPalette.Abyss)
    const val DayAlpha = .72f
    const val DayStrongAlpha = .78f
    const val NightAlpha = .66f
    const val NightStrongAlpha = .58f
    val DayGlows = listOf(Color(0xFF79B5E2), Color(0xFFA2DFFF), Color(0xFF82A9D4))
    val NightGlows = listOf(Color(0xFF1644C3), Color(0xFF126589), Color(0xFF183B8C))
    const val GlowAlpha = .48f
    val DayAction = Color(0xFF075BA8)
    val NightAction = Color(CopilotPalette.ActionBottom)
}

internal val LocalGlassDark = staticCompositionLocalOf { false }

/** Shared surface, button and geometry tokens. */
object Glass {
    val heroRadius = 28.dp
    val cardRadius = 20.dp
    val controlRadius = 16.dp
    val rimWidth = 1.dp
    val innerRimWidth = 1.dp
    val iconTileSize = 44.dp
    val iconSize = 24.dp
    val iconButtonSize = 48.dp
    val actionHeight = 52.dp
    val pageDot = 8.dp
    val pageActiveWidth = 24.dp
    const val pageGlowAlpha = .30f
    const val pressScale = .98f
    const val secondaryButtonAlpha = .72f
    val primaryTop = Color(CopilotPalette.ActionTop)
    val primaryBottom = Color(CopilotPalette.ActionBottom)
    val destructiveTop = Color(0xFF81394F)
    val destructiveBottom = Color(0xFF4E203A)

    @Composable fun surface(strong: Boolean = false): Color = if (LocalGlassDark.current)
        StormGlass.NightTint.copy(alpha = if (strong) StormGlass.NightStrongAlpha else StormGlass.NightAlpha)
    else StormGlass.DayTint.copy(alpha = if (strong) StormGlass.DayStrongAlpha else StormGlass.DayAlpha)

    const val heroGlowAlpha = .12f
    const val nightReflectionAlpha = .035f
    const val dayReflectionAlpha = .12f
    const val innerRimAlpha = .025f
    const val nightLowerReflectionAlpha = .025f

    @Composable fun outline(): Color = if (LocalGlassDark.current)
        Color(CopilotPalette.Lightning).copy(alpha = .18f) else Color.White.copy(alpha = .62f)

    @Composable fun rim(): Brush = Brush.linearGradient(if (LocalGlassDark.current) listOf(
        Color(0xFFB1D8FF).copy(alpha = .28f), Color(0xFFB1D8FF).copy(alpha = .055f),
        Color(CopilotPalette.Lightning).copy(alpha = .14f)
    ) else listOf(Color.White.copy(alpha = .85f), Color.White.copy(alpha = .22f),
        Color(0xFF5786BA).copy(alpha = .18f)))

    @Composable fun accent(): Color = MaterialTheme.colorScheme.tertiary
}

enum class GlassMode { FULL, LITE }
internal data class GlassBackdrop(val bitmap: androidx.compose.ui.graphics.ImageBitmap, val size: IntSize, val origin: Offset)
internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Draws only APP-owned artwork. The small copy is created once per size/theme. */
@Composable
fun CopilotBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val dark = LocalGlassDark.current
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    var sceneOrigin by remember { mutableStateOf(Offset.Zero) }
    val sharp = remember(sceneSize, dark) {
        if (sceneSize.width == 0 || sceneSize.height == 0) null else {
            val bitmap = Bitmap.createBitmap(sceneSize.width, sceneSize.height, Bitmap.Config.ARGB_8888)
            paintScene(AndroidCanvas(bitmap), bitmap.width.toFloat(), bitmap.height.toFloat(), dark)
            bitmap.asImageBitmap()
        }
    }
    val small = remember(sceneSize, dark) {
        if (sceneSize.width == 0 || sceneSize.height == 0) null else {
            val bitmap = Bitmap.createBitmap((sceneSize.width / 8).coerceAtLeast(1),
                (sceneSize.height / 8).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            paintScene(AndroidCanvas(bitmap), bitmap.width.toFloat(), bitmap.height.toFloat(), dark)
            bitmap.asImageBitmap()
        }
    }
    Box(modifier.fillMaxSize().onSizeChanged { sceneSize = it }
        .onGloballyPositioned { sceneOrigin = it.positionInWindow() }) {
        Canvas(Modifier.fillMaxSize()) {
            sharp?.let { drawImage(it) }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides small?.let { GlassBackdrop(it, sceneSize, sceneOrigin) }) {
            content()
        }
    }
}

private fun paintScene(canvas: AndroidCanvas, w: Float, h: Float, dark: Boolean) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor((if (dark) StormGlass.NightBackdrop else StormGlass.DayBackdrop).toArgb())
    val colors = if (dark) StormGlass.NightGlows else StormGlass.DayGlows
    val glows = listOf(
        Triple(Offset(w * .82f, h * .12f), w * 1.22f, colors[0]),
        Triple(Offset(w * -.20f, h * .48f), w * .96f, colors[1]),
        Triple(Offset(w * .92f, h * .97f), w * 1.14f, colors[2])
    )
    glows.forEach { (center, radius, color) ->
        p.shader = AndroidRadialGradient(center.x, center.y, radius,
            intArrayOf(color.copy(alpha = StormGlass.GlowAlpha).toArgb(),
                color.copy(alpha = 0f).toArgb()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
    }
    // A quiet vertical light falloff gives the scene depth without a decorative shape.
    p.shader = AndroidLinearGradient(0f, 0f, 0f, h, intArrayOf(
        Color.Transparent.toArgb(), (if (dark) Color(CopilotPalette.Midnight).copy(alpha = .36f)
            else Color.White.copy(alpha = .16f)).toArgb(), Color.Transparent.toArgb()
    ), floatArrayOf(0f, .64f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, p)
    p.shader = null
}

/** Draws the corresponding part of the shared blurred background. LITE omits it. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, strong: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium, onClick: (() -> Unit)? = null,
    mode: GlassMode = GlassMode.FULL, content: @Composable () -> Unit) {
    val dark = LocalGlassDark.current
    val backdrop = LocalGlassBackdrop.current
    val rim = Glass.rim()
    var panelPosition by remember { mutableStateOf(Offset.Zero) }
    val decorated = modifier
        .then(if (strong) Modifier.drawWithContent {
            val center = Offset(size.width * .5f, size.height * .5f)
            drawCircle(Brush.radialGradient(listOf(Color(CopilotPalette.Electric).copy(alpha = Glass.heroGlowAlpha), Color.Transparent),
                center = center, radius = size.width * .8f), size.width * .8f, center)
            drawContent()
        } else Modifier)
        .onGloballyPositioned { panelPosition = it.positionInWindow() }
        .clip(shape)
        .drawWithContent {
            if (mode == GlassMode.FULL && backdrop != null && backdrop.size.width > 0 && backdrop.size.height > 0) {
                val relative = panelPosition - backdrop.origin
                val sourceX = (relative.x / backdrop.size.width * backdrop.bitmap.width).roundToInt().coerceIn(0, backdrop.bitmap.width - 1)
                val sourceY = (relative.y / backdrop.size.height * backdrop.bitmap.height).roundToInt().coerceIn(0, backdrop.bitmap.height - 1)
                val sourceW = (size.width / backdrop.size.width * backdrop.bitmap.width).roundToInt().coerceIn(1, backdrop.bitmap.width - sourceX)
                val sourceH = (size.height / backdrop.size.height * backdrop.bitmap.height).roundToInt().coerceIn(1, backdrop.bitmap.height - sourceY)
                drawImage(backdrop.bitmap, srcOffset = IntOffset(sourceX, sourceY), srcSize = IntSize(sourceW, sourceH),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Medium)
            }
            drawRect(if (dark) StormGlass.NightTint.copy(alpha = if (strong)
                StormGlass.NightStrongAlpha else StormGlass.NightAlpha) else StormGlass.DayTint.copy(alpha = if (strong)
                StormGlass.DayStrongAlpha else StormGlass.DayAlpha))
            // Edge light, rather than a white oval reflected across the labels.
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark)
                Glass.nightReflectionAlpha else Glass.dayReflectionAlpha), Color.Transparent,
                Color(CopilotPalette.Electric).copy(alpha = Glass.nightLowerReflectionAlpha))))
            drawContent()
        }
        .border(BorderStroke(Glass.rimWidth, rim), shape)
        .border(BorderStroke(Glass.innerRimWidth, Color.White.copy(alpha = Glass.innerRimAlpha)), shape)
        .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(decorated) { content() }
    }
}

@Composable
fun glassTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)
