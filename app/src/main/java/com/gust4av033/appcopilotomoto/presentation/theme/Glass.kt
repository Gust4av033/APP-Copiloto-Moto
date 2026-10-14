package com.gust4av033.appcopilotomoto.presentation.theme

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient as AndroidLinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient as AndroidRadialGradient
import android.graphics.RectF
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Backdrop, tint and contrast tokens for both themes. */
internal object StormGlass {
    val DayBackdrop = Color(0xFFDDEBFA)
    val NightBackdrop = Color(CopilotPalette.Midnight)
    val DayTint = Color(0xFFE0F0FF)
    val NightTint = Color(CopilotPalette.Abyss)
    const val DayAlpha = .72f
    const val DayStrongAlpha = .78f
    const val NightAlpha = .50f
    const val NightStrongAlpha = .46f
    val DayGlows = listOf(Color(0xFF68B7FF), Color(0xFF79DDFF), Color(0xFF4B6FBE))
    val NightGlows = listOf(Color(CopilotPalette.Electric), Color(CopilotPalette.Lightning), Color(CopilotPalette.Indigo))
    const val GlowAlpha = .25f
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
    val innerRimWidth = 2.dp
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

    @Composable fun outline(): Color = if (LocalGlassDark.current)
        Color(0xFF79DDFF).copy(alpha = .45f) else Color.White.copy(alpha = .90f)

    @Composable fun rim(): Brush = Brush.linearGradient(listOf(Color.White.copy(alpha = .75f),
        Color.White.copy(alpha = .08f), Color(CopilotPalette.Lightning).copy(alpha = .45f)))

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
    canvas.drawColor(if (dark) 0xFF040A1C.toInt() else 0xFFDDEBFA.toInt())
    val x = w * 1.07f
    val y = h * .34f
    val radius = w * .59f
    p.shader = AndroidRadialGradient(x, y, radius,
        if (dark) intArrayOf(0xFF2F6BFF.toInt(), 0xCC123A9E.toInt(), 0x00123A9E)
        else intArrayOf(0xFF68B7FF.toInt(), 0x995E91D4.toInt(), 0x005E91D4),
        null, Shader.TileMode.CLAMP)
    canvas.drawCircle(x, y, radius, p)
    p.shader = null
    p.color = if (dark) 0x8879DDFF.toInt() else 0x8861A8DB.toInt()
    p.style = Paint.Style.STROKE
    p.strokeWidth = (w * .006f).coerceAtLeast(1f)
    canvas.drawArc(RectF(-w * .68f, h * .46f, w * .61f, h * 1.04f), 212f, 212f, false, p)
    p.style = Paint.Style.FILL
    p.shader = AndroidLinearGradient(-w * .2f, h * .82f, w * .85f, h * .08f,
        intArrayOf(0x0079DDFF, if (dark) 0x8879DDFF.toInt() else 0x6669AFEB, 0x0079DDFF),
        null, Shader.TileMode.CLAMP)
    canvas.save()
    canvas.rotate(-28f, w * .4f, h * .43f)
    canvas.drawRoundRect(RectF(-w * .3f, h * .41f, w * 1.2f, h * .425f), h * .008f, h * .008f, p)
    canvas.restore()
    p.shader = AndroidRadialGradient(w * .06f, h * .93f, w * .42f,
        intArrayOf(if (dark) 0x3F3A2E9E else 0x294B6FBE, 0x003A2E9E), null, Shader.TileMode.CLAMP)
    canvas.drawCircle(w * .06f, h * .93f, w * .42f, p)
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
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2F6BFF).copy(alpha = .30f), Color.Transparent),
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
            drawRect(if (dark) Color(0xFF040A1C).copy(alpha = if (strong) .46f else .50f)
                else Color(0xFFE0F0FF).copy(alpha = if (strong) .78f else .72f))
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .16f else .22f),
                Color.Transparent, Color(0xFF2F6BFF).copy(alpha = .06f))))
            drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = .20f), Color.Transparent),
                center = Offset(size.width * .25f, 0f), radius = size.width * .85f),
                topLeft = Offset(-size.width * .25f, -size.height * .34f),
                size = Size(size.width * 1.1f, size.height * .65f))
            drawContent()
        }
        .border(BorderStroke(Glass.rimWidth, rim), shape)
        .border(BorderStroke(Glass.innerRimWidth, Color.White.copy(alpha = .10f)), shape)
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
