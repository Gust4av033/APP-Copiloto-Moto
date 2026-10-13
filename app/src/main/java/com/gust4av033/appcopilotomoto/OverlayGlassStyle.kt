package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import com.gust4av033.appcopilotomoto.presentation.theme.CopilotPalette

/** Vidrio pintado: no captura ni desenfoca el contenido de otras aplicaciones. */
internal object OverlayGlassStyle {
    private fun dp(context: Context, value: Int) = value * context.resources.displayMetrics.density

    fun panel(context: Context, night: Boolean): LayerDrawable {
        val base = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (night) intArrayOf(0xEA2F2857.toInt(), 0xE61A2745.toInt(), 0xEC171D39.toInt())
            else intArrayOf(0xEFF7F3FF.toInt(), 0xEBEDF7FF.toInt(), 0xF0E3EBFB.toInt())).apply {
            cornerRadius = dp(context, 24)
            setStroke(dp(context, 1).toInt().coerceAtLeast(1), if (night) 0xA39CDCF5.toInt() else 0xCFFFFFFF.toInt())
        }
        val reflection = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x44FFFFFF, 0x08FFFFFF, 0x1A79DDFF)).apply { cornerRadius = dp(context, 23) }
        val innerRim = GradientDrawable().apply {
            cornerRadius = dp(context, 22)
            setColor(Color.TRANSPARENT)
            setStroke(dp(context, 1).toInt().coerceAtLeast(1), if (night) 0x2044B7EA else 0x3689A4D8)
        }
        val inset = dp(context, 2).toInt()
        return LayerDrawable(arrayOf(base, reflection, innerRim)).apply {
            setLayerInset(1, inset, inset, inset, inset)
            setLayerInset(2, inset, inset, inset, inset)
        }
    }

    fun button(context: Context, night: Boolean, primary: Boolean): RippleDrawable {
        val normal = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (primary) intArrayOf(0xFF6546A2.toInt(), 0xFF28538C.toInt())
            else if (night) intArrayOf(0xB832405F.toInt(), 0xD126304D.toInt())
            else intArrayOf(0xE6FFFFFF.toInt(), 0xE6E5ECFA.toInt())).apply {
            cornerRadius = dp(context, 16)
            setStroke(dp(context, 1).toInt().coerceAtLeast(1), if (primary) 0xAA9DCFFF.toInt() else 0x4588ABD5)
        }
        val disabled = GradientDrawable().apply {
            cornerRadius = dp(context, 16)
            setColor(if (night) 0xF22A344C.toInt() else 0xF2DDE4F1.toInt())
        }
        val sheen = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x26FFFFFF, 0x00FFFFFF, 0x0B79DDFF)).apply { cornerRadius = dp(context, 16) }
        val raised = LayerDrawable(arrayOf(normal, sheen))
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), disabled)
            addState(intArrayOf(), raised)
        }
        val mask = GradientDrawable().apply { cornerRadius = dp(context, 16); setColor(Color.WHITE) }
        return RippleDrawable(ColorStateList.valueOf(0x4079DDFF), states, mask)
    }

    fun textColors(night: Boolean, primary: Boolean): ColorStateList {
        val normal = if (primary) CopilotPalette.HeroText else if (night) CopilotPalette.NightText else CopilotPalette.DayText
        val disabled = if (night) 0xFFB7BED2.toInt() else 0xFF687388.toInt()
        return ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()), intArrayOf(normal, disabled))
    }

    fun touchCircle(): RippleDrawable = RippleDrawable(ColorStateList.valueOf(0x3379DDFF), null,
        GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
}

