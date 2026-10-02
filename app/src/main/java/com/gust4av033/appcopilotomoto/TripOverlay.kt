package com.gust4av033.appcopilotomoto

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.Display
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
    private var status: TextView? = null
    private var request: Button? = null
    private var expanded = false
    private var draggingWindow = false
    private var body: LinearLayout? = null
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
            updateState()
            if (!draggingWindow) position()
        }.onFailure {
            close(); failed = true; onFailure()
        }
    }

    private fun attach() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(6).toFloat()
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE && expanded) {
                    minimize()
                    true
                } else false
            }
        }
        val layout = WindowManager.LayoutParams(dp(56), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
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
        return if (night) Color.rgb(27, 36, 43) to Color.rgb(231, 242, 247)
            else Color.rgb(242, 249, 252) to Color.rgb(22, 40, 49)
    }

    private fun buildContent() {
        val root = view ?: return
        root.removeAllViews()
        val (background, foreground) = colors()
        root.background = GradientDrawable().apply {
            setColor(background)
            cornerRadius = dp(if (expanded) 20 else 28).toFloat()
            setStroke(dp(1).coerceAtLeast(1), Color.rgb(45, 131, 151))
        }
        root.setPadding(if (expanded) dp(12) else 0, 0, if (expanded) dp(12) else 0, if (expanded) dp(8) else 0)
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(56)))
        badge = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = if (expanded) 16f else 12f
            setTextColor(foreground)
            minimumHeight = dp(56)
            isClickable = true
            setOnClickListener { expanded = !expanded; buildContent(); updateState(); position() }
            drag(this)
        }.also { header.addView(it, LinearLayout.LayoutParams(0, dp(56), 1f)) }
        if (expanded) header.addView(Button(context).apply {
            text = "×"
            textSize = 22f
            setTextColor(foreground)
            backgroundTintList = ColorStateList.valueOf(background)
            contentDescription = context.getString(R.string.overlay_minimize)
            setOnClickListener { minimize() }
        }, LinearLayout.LayoutParams(dp(48), dp(56)))
        status = null
        request = null
        body = null
        if (expanded) {
            val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val scroll = ScrollView(context).apply { addView(panel); isFillViewport = false }
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            body = panel
            status = TextView(context).apply {
                textSize = 14f
                setTextColor(foreground)
                setPadding(0, 0, 0, dp(8))
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }.also { body?.addView(it) }
            request = button(R.string.trip_manual_command) {
                expanded = false
                buildContent()
                updateState()
                position()
                onRequestCommand()
            }
            button(R.string.overlay_open_app, onOpenApp)
            // Fuera del área desplazable: ocultar siempre queda a mano con texto grande.
            body = root
            button(R.string.overlay_hide) { onHide(); close() }
        }
    }

    private fun minimize() {
        if (!expanded) return
        expanded = false
        buildContent()
        updateState()
        position()
    }

    private fun button(label: Int, action: () -> Unit): Button = Button(context).apply {
        text = context.getString(label)
        isAllCaps = false
        val (background, foreground) = colors()
        backgroundTintList = ColorStateList.valueOf(background)
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()), intArrayOf(foreground, Color.GRAY)))
        minHeight = dp(48)
        setOnClickListener { action() }
    }.also { body?.addView(it, LinearLayout.LayoutParams(-1, -2)) }

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
        val symbol = when {
            state.call != TripCallPhase.NONE -> "☎"
            state.phase == TripInteractionPhase.CONFIRMING -> "?"
            state.phase == TripInteractionPhase.LISTENING -> "●"
            state.phase == TripInteractionPhase.WAITING -> "•"
            state.phase == TripInteractionPhase.SWITCHING -> "↔"
            else -> "…"
        }
        val description = context.getString(R.string.overlay_description, state.mode.modeNumber, label)
        val title = if (expanded) context.getString(R.string.overlay_panel_title, state.mode.modeNumber) else "APP\n$symbol"
        badge?.let {
            if (it.text.toString() != title) it.text = title
            if (it.contentDescription != description) it.contentDescription = description
        }
        badge?.alpha = if (!expanded && state.ready) 0.75f else 1f
        status?.let { if (it.text.toString() != label) it.text = label }
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
        layout.width = dp(if (expanded) 260 else 56).coerceAtMost((width - margin * 2).coerceAtLeast(dp(48)))
        layout.height = dp(if (expanded) 320 else 56).coerceAtMost((height - margin * 2).coerceAtLeast(dp(56)))
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
                    downX = event.rawX; downY = event.rawY
                    oldX = layout.x; oldY = layout.y; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop) dragging = true
                    if (dragging) {
                        val (width, height) = availableSize()
                        layout.x = (oldX + event.rawX.toInt() - downX.toInt()).coerceIn(0, (width - layout.width).coerceAtLeast(0))
                        layout.y = (oldY + event.rawY.toInt() - downY.toInt()).coerceIn(0, (height - (view?.measuredHeight ?: dp(56))).coerceAtLeast(0))
                        updateLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    draggingWindow = false
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
                MotionEvent.ACTION_CANCEL -> { draggingWindow = false; position(); true }
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
        expanded = false
        draggingWindow = false
        body = null
    }
}

