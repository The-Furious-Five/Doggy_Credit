package com.doggycredit.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.doggycredit.app.ui.theme.BrandActionColors
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandContrastTests {
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance(); val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
    @Test fun brandActionTextMeetsContrastOnItsSurface() {
        assertTrue(contrast(BrandActionColors.content, BrandActionColors.container) >= 4.5f)
    }
    @Test fun busyTextAndProgressMeetContrastOnTheirActualContainer() {
        assertTrue(contrast(BrandActionColors.busyContent, BrandActionColors.busyContainer) >= 4.5f)
    }
}
