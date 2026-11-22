package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Guards the shared control tokens copied from the Season chip. */
class AppControlsTest {
    @Test fun seasonChipMetricsAreTheSingleSourceOfTruth() {
        assertEquals(8, AppCornerRadius.value.toInt())
        assertEquals(30, AppControlSpec.ChipHeight.value.toInt())
        assertEquals(12, AppControlSpec.ChipPaddingHorizontal.value.toInt())
        assertEquals(6, AppControlSpec.ChipPaddingVertical.value.toInt())
        assertEquals(8, AppControlSpec.ChipGap.value.toInt())
        assertEquals(14f, AppControlSpec.ChipTextSize.value)
    }

    @Test fun buttonHeightsMatchTheSpec() {
        assertEquals(40, AppControlSpec.ButtonHeight.value.toInt())
        assertEquals(48, AppControlSpec.LargeButtonHeight.value.toInt())
        assertEquals(36, AppControlSpec.IconButtonSize.value.toInt())
        assertEquals(20, AppControlSpec.IconSize.value.toInt())
        assertEquals(48, AppControlSpec.TouchSize.value.toInt())
        assertEquals(14f, AppControlSpec.ButtonTextSize.value)
    }

    @Test fun selectedAndUnselectedColoursDiffer() {
        assertNotEquals(AppControlSpec.SelectedFill, AppControlSpec.UnselectedFill)
        assertNotEquals(AppControlSpec.SelectedText, AppControlSpec.UnselectedText)
        assertEquals(0.4f, AppControlSpec.DisabledAlpha)
    }

    @Test fun allControlShapesShareOneRadius() {
        assertEquals(AppControlShape.topStart, AppControlShape.topEnd)
        assertEquals(AppControlShape.topStart, AppControlShape.bottomStart)
        assertEquals(AppControlShape.topStart, AppControlShape.bottomEnd)
    }
}
