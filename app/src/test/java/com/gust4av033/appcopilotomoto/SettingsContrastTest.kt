package com.gust4av033.appcopilotomoto

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.gust4av033.appcopilotomoto.presentation.theme.DayColors
import com.gust4av033.appcopilotomoto.presentation.theme.NightColors
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsContrastTest {
    private fun ratio(a:Color,b:Color):Double {
        val x=a.luminance().toDouble();val y=b.luminance().toDouble()
        return (maxOf(x,y)+0.05)/(minOf(x,y)+0.05)
    }
    @Test fun dayTextIsReadableOverGlassAndButtonSurfaces() {
        val c=DayColors;val glass=Color.White.copy(alpha=.64f).compositeOver(c.background)
        assertTrue(ratio(c.onSurface,glass)>=4.5);assertTrue(ratio(c.onSurfaceVariant,glass)>=4.5)
        assertTrue(ratio(c.onPrimary,c.primary)>=4.5)
        assertTrue(ratio(c.onSecondaryContainer,c.secondaryContainer.copy(alpha=.74f).compositeOver(glass))>=4.5)
    }
    @Test fun nightTextIsReadableOverGlassAndButtonSurfaces() {
        val c=NightColors;val glass=Color(0xFF172039).copy(alpha=.39f).compositeOver(c.background)
        assertTrue(ratio(c.onSurface,glass)>=4.5);assertTrue(ratio(c.onSurfaceVariant,glass)>=4.5)
        assertTrue(ratio(c.onPrimary,c.primary)>=4.5)
        assertTrue(ratio(c.onSecondaryContainer,c.secondaryContainer.copy(alpha=.74f).compositeOver(glass))>=4.5)
    }
}
