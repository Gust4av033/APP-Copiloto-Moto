package com.gust4av033.appcopilotomoto

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.gust4av033.appcopilotomoto.presentation.theme.DayColors
import com.gust4av033.appcopilotomoto.presentation.theme.NightColors
import com.gust4av033.appcopilotomoto.presentation.theme.StormGlass
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsContrastTest {
    private fun ratio(a:Color,b:Color):Double {
        val x=a.luminance().toDouble();val y=b.luminance().toDouble()
        return (maxOf(x,y)+0.05)/(minOf(x,y)+0.05)
    }
    @Test fun dayTextIsReadableOverGlassAndButtonSurfaces() {
        val c=DayColors;val glass=StormGlass.DayTint.copy(alpha=StormGlass.DayAlpha).compositeOver(c.background)
        assertTrue(ratio(c.onSurface,glass)>=4.5);assertTrue(ratio(c.onSurfaceVariant,glass)>=4.5)
        assertTrue(ratio(c.onPrimary,c.primary)>=4.5)
        assertTrue(ratio(c.onSecondaryContainer,c.secondaryContainer.copy(alpha=.74f).compositeOver(glass))>=4.5)
    }
    @Test fun nightTextIsReadableOverGlassAndButtonSurfaces() {
        val c=NightColors;val glass=StormGlass.NightTint.copy(alpha=StormGlass.NightAlpha).compositeOver(c.background)
        assertTrue(ratio(c.onSurface,glass)>=4.5);assertTrue(ratio(c.onSurfaceVariant,glass)>=4.5)
        assertTrue(ratio(c.onPrimary,c.primary)>=4.5)
        assertTrue(ratio(c.onSecondaryContainer,c.secondaryContainer.copy(alpha=.74f).compositeOver(glass))>=4.5)
    }
    @Test fun intenseBlueActionsKeepWhiteLabelsReadable() {
        assertTrue(ratio(Color.White, StormGlass.DayAction)>=4.5)
        assertTrue(ratio(Color.White, StormGlass.NightAction)>=4.5)
        assertTrue(ratio(Color.White, Glass.primaryTop)>=4.5)
        assertTrue(ratio(Color.White, Glass.primaryBottom)>=4.5)
        assertTrue(ratio(Color.White, Glass.destructiveTop)>=4.5)
        assertTrue(ratio(Color.White, Glass.destructiveBottom)>=4.5)
    }
    @Test fun labelsRemainReadableOverMaximumCombinedGlowsAndReflections() {
        for (night in listOf(false,true)) {
            val c=if (night) NightColors else DayColors
            val glows=if (night) StormGlass.NightGlows else StormGlass.DayGlows
            val backdrop=glows.fold(c.background) { base,color -> color.copy(alpha=StormGlass.GlowAlpha).compositeOver(base) }
            val tint=if (night) StormGlass.NightTint else StormGlass.DayTint
            val alphas=if (night) listOf(StormGlass.NightAlpha,StormGlass.NightStrongAlpha) else listOf(StormGlass.DayAlpha,StormGlass.DayStrongAlpha)
            for (alpha in alphas) {
                val glass=Color.White.copy(alpha=if (night) .08f else .18f).compositeOver(tint.copy(alpha=alpha).compositeOver(backdrop))
                assertTrue(ratio(c.onSurface,glass)>=4.5)
                assertTrue(ratio(c.onSurfaceVariant,glass)>=4.5)
                val button=c.secondaryContainer.copy(alpha=.66f).compositeOver(glass)
                assertTrue(ratio(c.onSecondaryContainer,button)>=4.5)
            }
        }
    }
}
