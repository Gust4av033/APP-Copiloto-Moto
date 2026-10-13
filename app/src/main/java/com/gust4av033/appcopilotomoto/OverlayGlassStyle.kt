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
            if (night) intArrayOf(0xEA173C5A.toInt(), 0xE6112D48.toInt(), 0xEC0A2037.toInt())
            else intArrayOf(0xEFE3F1FF.toInt(), 0xEBD1E8FA.toInt(), 0xF0C5DEF2.toInt())).apply {
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
            if (primary) intArrayOf(0xFF104F87.toInt(), 0xFF0D345A.toInt())
            else if (night) intArrayOf(0xB8244F73.toInt(), 0xD1163857.toInt())
            else intArrayOf(0xE6FFFFFF.toInt(), 0xE6CDE5F8.toInt())).apply {
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

