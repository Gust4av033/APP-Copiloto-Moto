package com.gust4av033.appcopilotomoto

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gust4av033.appcopilotomoto.presentation.theme.CopilotPalette
import kotlin.math.abs

object TripOverlayPreferences {
    private const val PREFS = "trip_overlay"
    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
    }
}

data class TripOverlaySnapshot(val mode: WakeAudioMode, val phase: TripInteractionPhase,
    val call: TripCallPhase, val ready: Boolean, val manualActivationOnly: Boolean = false)

/** Una sola ventana del servicio de viaje. No posee ni inicia micrófonos o detectores. */
class TripOverlay(
    baseContext: Context,
    private val onRequestCommand: () -> Unit,
    private val onOpenApp: () -> Unit,
    private val onHide: () -> Unit,
    private val onFailure: () -> Unit
) {
    private val context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching {
        val display = baseContext.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        baseContext.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }.getOrDefault(baseContext) else baseContext
    private val manager = context.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences("trip_overlay", Context.MODE_PRIVATE)
    private var anchor = OverlayAnchor(prefs.getBoolean("right", true), prefs.getFloat("vertical", 0.45f))
    private var view: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var badge: TextView? = null
    private var orbHandle: FrameLayout? = null
    private var orbIcon: ImageView? = null
    private var orbIndicator: View? = null
    private var lastOrbIcon = 0
    private var lastOrbIndicator = 0
    private var status: TextView? = null
    private var request: Button? = null
    private var contentScroll: ScrollView? = null
    private var expanded = false
    private var draggingWindow = false
    private var lastConfiguration: Configuration? = null
    private var snapshot: TripOverlaySnapshot? = null
    private var failed = false
    private val margin get() = dp(8)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun render(visible: Boolean, state: TripOverlaySnapshot) {
        if (!visible || !Settings.canDrawOverlays(context)) {
            close()
            failed = false
            return
        }
        if (failed) return
        runCatching {
            snapshot = state
            if (view == null) attach()
            else if (lastConfiguration != context.resources.configuration) buildContent()
            updateState()
            if (!draggingWindow) position()
        }.onFailure {
            close(); failed = true; onFailure()
        }
    }

    private fun attach() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            elevation = 0f
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE && expanded) {
                    minimize()
                    true
                } else false
            }
        }
        val layout = WindowManager.LayoutParams(dp(64), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT }
        view = root
        params = layout
        buildContent()
        position(update = false)
        runCatching { manager.addView(root, layout) }.onFailure {
            close()
            failed = true
            onFailure()
        }
    }

    private fun colors(): Pair<Int, Int> {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (night) CopilotPalette.NightSurface to CopilotPalette.NightText
            else CopilotPalette.DaySurface to CopilotPalette.DayText
    }

    private fun panelBounds(): OverlayPanelBounds {
        val (width, height) = availableSize()
        return OverlayPanelBounds.fit(width, height, context.resources.displayMetrics.density,
            context.resources.configuration.fontScale)
    }

    private fun buildContent() {
        val root = view ?: return
        root.removeAllViews()
        lastConfiguration = Configuration(context.resources.configuration)
        val (_, contentColor) = colors()
        root.background = if (expanded) OverlayGlassStyle.panel(context, night()) else null
        root.clipToOutline = expanded
        root.setPadding(if (expanded) dp(12) else 0, if (expanded) dp(8) else 0,
            if (expanded) dp(12) else 0, if (expanded) dp(10) else 0)
        badge = null; status = null; request = null; contentScroll = null
        orbHandle = null; orbIcon = null; orbIndicator = null
        lastOrbIcon = 0; lastOrbIndicator = 0
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(if (expanded) 48 else 64)
        }
        root.addView(header, LinearLayout.LayoutParams(-1, if (expanded) -2 else dp(64)))
        if (!expanded) {
            orbHandle = FrameLayout(context).apply {
                minimumHeight = dp(64)
                minimumWidth = dp(64)
                isClickable = true
                isFocusable = true
                background = context.getDrawable(R.drawable.overlay_water_orb)
                foreground = OverlayGlassStyle.touchCircle()
                setOnClickListener { expanded = true; buildContent(); updateState(); position() }
                drag(this)
            }.also { handle ->
                orbIcon = ImageView(context).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    imageTintList = ColorStateList.valueOf(CopilotPalette.HeroText)
                }.also { handle.addView(it, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER).apply { topMargin = -dp(3) }) }
                orbIndicator = View(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                    .also { handle.addView(it, FrameLayout.LayoutParams(dp(7), dp(7), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)
                        .apply { bottomMargin = dp(12) }) }
                header.addView(handle, LinearLayout.LayoutParams(dp(64), dp(64)))
            }
            return
        }

        header.addView(FrameLayout(context).apply {
            background = context.getDrawable(R.drawable.overlay_water_orb)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            orbIcon = ImageView(context).apply {
                imageTintList = ColorStateList.valueOf(CopilotPalette.HeroText)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }.also { addView(it, FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER)) }
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(6) })
        val titleColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        badge = TextView(context).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(contentColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }.also { titleColumn.addView(it) }
        status = TextView(context).apply {
            textSize = 12.5f
            setTextColor(if (night()) 0xFFCDD6EB.toInt() else 0xFF465671.toInt())
            setPadding(0, dp(2), 0, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }.also { titleColumn.addView(it) }
        orbHandle = FrameLayout(context).apply {
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            addView(titleColumn, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
            setOnClickListener { minimize() }
            drag(this)
        }.also { header.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        header.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_overlay_close)
            imageTintList = ColorStateList.valueOf(contentColor)
            contentDescription = context.getString(R.string.overlay_minimize)
            isClickable = true
            isFocusable = true
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = OverlayGlassStyle.touchCircle()
            setOnClickListener { minimize() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))

        val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { addView(panel); isFillViewport = false }
        contentScroll = scroll
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        request = button(R.string.overlay_command, R.drawable.ic_mic, primary = true) {
            expanded = false
            buildContent()
            updateState()
            position()
            onRequestCommand()
        }.also { panel.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }) }
        val stacked = panelBounds().stackedActions
        val openApp = button(R.string.overlay_open_app, R.drawable.ic_home, compact = !stacked, action = onOpenApp)
        val hide = button(R.string.overlay_hide_short, R.drawable.ic_overlay_hide, compact = !stacked) { onHide(); close() }
            .apply { contentDescription = context.getString(R.string.overlay_hide) }
        if (stacked) {
            panel.addView(openApp, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            // Ocultar permanece fuera del scroll también con texto grande.
            root.addView(hide, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        } else {
            val shortcuts = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            shortcuts.addView(openApp, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
            shortcuts.addView(hide, LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(shortcuts, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }

    private fun minimize() {
        if (!expanded) return
        expanded = false
        buildContent()
        updateState()
        position()
    }

    private fun night() = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun button(label: Int, iconRes: Int, primary: Boolean = false, compact: Boolean = false, action: () -> Unit): Button = Button(context).apply {
        text = context.getString(label)
        isAllCaps = false
        textSize = if (compact) 12f else 14f
        gravity = if (compact) Gravity.CENTER else Gravity.CENTER_VERTICAL
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(if (compact) 8 else 12), dp(10), dp(if (compact) 8 else 12), dp(10))
        compoundDrawablePadding = dp(if (compact) 6 else 8)
        backgroundTintList = null
        val textColors = OverlayGlassStyle.textColors(night(), primary)
        setTextColor(textColors)
        context.getDrawable(iconRes)?.mutate()?.let { icon ->
            icon.setTintList(textColors)
            icon.setBounds(0, 0, dp(if (compact) 18 else 22), dp(if (compact) 18 else 22))
            setCompoundDrawablesRelative(icon, null, null, null)
        }
        background = OverlayGlassStyle.button(context, night(), primary)
        minHeight = dp(if (compact) 48 else 52)
        minWidth = 0
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { action() }
    }

    private fun updateState() {
        val state = snapshot ?: return
        val label = context.getString(when {
            state.call == TripCallPhase.CALL -> R.string.session_call
            state.call == TripCallPhase.RECOVERING -> R.string.session_call_recovering
            state.phase == TripInteractionPhase.WAITING -> if (state.mode == WakeAudioMode.BUTTON || state.manualActivationOnly)
                R.string.overlay_wait_button else R.string.overlay_wait_voice
            state.phase == TripInteractionPhase.LISTENING -> R.string.overlay_listening
            state.phase == TripInteractionPhase.PROCESSING -> R.string.overlay_processing
            state.phase == TripInteractionPhase.SPEAKING -> R.string.overlay_speaking
            state.phase == TripInteractionPhase.CONFIRMING -> R.string.overlay_confirming
            state.phase == TripInteractionPhase.SWITCHING -> R.string.overlay_switching
            else -> R.string.overlay_preparing
        })
        val icon = when {
            state.call != TripCallPhase.NONE -> R.drawable.ic_phone
            state.phase == TripInteractionPhase.CONFIRMING -> R.drawable.ic_overlay_confirm
            state.phase == TripInteractionPhase.LISTENING -> R.drawable.ic_mic
            state.phase == TripInteractionPhase.SPEAKING -> R.drawable.ic_overlay_speaking
            state.phase == TripInteractionPhase.WAITING -> if (state.mode == WakeAudioMode.BUTTON || state.manualActivationOnly)
                R.drawable.ic_button else R.drawable.ic_overlay_wave
            else -> R.drawable.ic_overlay_pending
        }
        val indicator = when {
            state.call != TripCallPhase.NONE -> 0xFF9EB8FF.toInt()
            state.phase == TripInteractionPhase.CONFIRMING -> 0xFFFFD295.toInt()
            state.phase == TripInteractionPhase.LISTENING -> CopilotPalette.Sky
            else -> 0xFFD3BFFF.toInt()
        }
        if (lastOrbIcon != icon) { orbIcon?.setImageResource(icon); lastOrbIcon = icon }
        if (lastOrbIndicator != indicator) {
            orbIndicator?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(indicator)
                setStroke(dp(1).coerceAtLeast(1), 0xAA152448.toInt())
            }
            lastOrbIndicator = indicator
        }
        val description = context.getString(R.string.overlay_description, state.mode.modeNumber, label)
        orbHandle?.let { if (it.contentDescription != description) it.contentDescription = description }
        badge?.let {
            val title = context.getString(R.string.overlay_panel_mode, state.mode.modeNumber)
            if (it.text.toString() != title) it.text = title
            if (it.contentDescription != description) it.contentDescription = description
        }
        val compactLabel = if (state.call == TripCallPhase.NONE && state.phase == TripInteractionPhase.WAITING)
            context.getString(if (state.mode == WakeAudioMode.BUTTON || state.manualActivationOnly)
                R.string.overlay_wait_button_compact else R.string.overlay_wait_voice_compact) else label
        status?.let { if (it.text.toString() != compactLabel) it.text = compactLabel }
        request?.isEnabled = state.ready && state.call == TripCallPhase.NONE
    }

    private fun availableSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = manager.currentWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return (metrics.bounds.width() - bars.left - bars.right) to (metrics.bounds.height() - bars.top - bars.bottom)
        }
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to (metrics.heightPixels - dp(56)).coerceAtLeast(dp(56))
    }

    private fun position(update: Boolean = true) {
        val root = view ?: return
        val layout = params ?: return
        val (width, height) = availableSize()
        if (expanded) {
            val bounds = panelBounds()
            layout.width = bounds.widthPx
            val scrollLayout = contentScroll?.layoutParams as? LinearLayout.LayoutParams
            // Medir sin peso obtiene altura natural; el peso solo distribuye la altura final.
            scrollLayout?.let { it.height = -2; it.weight = 0f }
            root.measure(View.MeasureSpec.makeMeasureSpec(layout.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            layout.height = root.measuredHeight.coerceIn(1, bounds.maxHeightPx)
            scrollLayout?.let { it.height = 0; it.weight = 1f }
        } else {
            layout.width = dp(64).coerceAtMost((width - margin * 2).coerceAtLeast(1))
            layout.height = dp(64).coerceAtMost((height - margin * 2).coerceAtLeast(1))
        }
        root.measure(View.MeasureSpec.makeMeasureSpec(layout.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(layout.height, View.MeasureSpec.EXACTLY))
        val point = anchor.place(width, height, layout.width, root.measuredHeight, margin)
        layout.x = point.x
        layout.y = point.y
        if (update && root.isAttachedToWindow) updateLayout()
    }

    @SuppressLint("ClickableViewAccessibility") // El toque sin desplazamiento invoca performClick().
    private fun drag(handle: View) {
        var downX = 0f
        var downY = 0f
        var oldX = 0
        var oldY = 0
        var dragging = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        handle.setOnTouchListener { _, event ->
            val layout = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    draggingWindow = true
                    handle.isPressed = true
                    handle.drawableHotspotChanged(event.x, event.y)
                    downX = event.rawX; downY = event.rawY
                    oldX = layout.x; oldY = layout.y; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop) dragging = true
                    if (dragging) {
                        handle.isPressed = false
                        val (width, height) = availableSize()
                        layout.x = (oldX + event.rawX.toInt() - downX.toInt()).coerceIn(0, (width - layout.width).coerceAtLeast(0))
                        layout.y = (oldY + event.rawY.toInt() - downY.toInt()).coerceIn(0, (height - (view?.measuredHeight ?: dp(56))).coerceAtLeast(0))
                        updateLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    draggingWindow = false
                    handle.isPressed = false
                    if (dragging) {
                        val (width, height) = availableSize()
                        val maxY = (height - (view?.measuredHeight ?: dp(56)) - margin).coerceAtLeast(margin)
                        anchor = OverlayAnchor(layout.x + layout.width / 2 >= width / 2,
                            ((layout.y - margin).toFloat() / (maxY - margin).coerceAtLeast(1)).coerceIn(0f, 1f))
                        prefs.edit().putBoolean("right", anchor.right).putFloat("vertical", anchor.verticalFraction).apply()
                        position()
                    } else handle.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> { handle.isPressed = false; draggingWindow = false; position(); true }
                else -> false
            }
        }
    }

    private fun updateLayout() {
        runCatching { view?.let { manager.updateViewLayout(it, params) } }.onFailure {
            close(); failed = true; onFailure()
        }
    }

    fun close() {
        view?.let { root -> if (root.isAttachedToWindow) runCatching { manager.removeViewImmediate(root) } }
        view = null; params = null; badge = null; status = null; request = null
        contentScroll = null
        orbHandle = null; orbIcon = null; orbIndicator = null
        lastOrbIcon = 0; lastOrbIndicator = 0
        expanded = false
        draggingWindow = false
        lastConfiguration = null
    }
}
