package org.vera20k

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchHudSettingsTest {
    @Test fun visibleButtonStorageKeepsOnlyKnownIdsAndAllowsAllHidden() {
        assertEquals(TouchHudSettings.defaultVisible, TouchHudSettings.parseVisible(null, false))
        assertEquals(setOf("STOP", "C1"), TouchHudSettings.parseVisible("STOP,C1,UNKNOWN,STOP", true))
        assertTrue(TouchHudSettings.parseVisible("", true).isEmpty())
        assertEquals("STOP,C1", TouchHudSettings.encodeVisible(setOf("C1", "STOP", "UNKNOWN")))
    }

    @Test fun normalizedPositionsClampAndRejectMalformedValues() {
        assertEquals(
            mapOf("STOP" to (0f to 1f), "C2" to (0.25f to 0.5f)),
            TouchHudSettings.parsePositions("STOP:-2,4;C2:0.25,0.5;STOP:NaN,0;bad"),
        )
        val roundTrip = TouchHudSettings.parsePositions(
            TouchHudSettings.encodePositions(mapOf("STOP" to (0.15f to 0.8f), "C2" to (0.75f to 0.9f))),
        )
        assertEquals(0.15f, roundTrip.getValue("STOP").first, 0.0001f)
        assertEquals(0.9f, roundTrip.getValue("C2").second, 0.0001f)
    }

    @Test fun sizesAndOpacityStayWithinTouchSafeBounds() {
        assertEquals(70, TouchHudSettings.clampScale(0))
        assertEquals(150, TouchHudSettings.clampScale(999))
        assertEquals(30, TouchHudSettings.clampOpacity(-1))
        assertEquals(100, TouchHudSettings.clampOpacity(101))
        assertEquals(mapOf("STOP" to 70, "C1" to 150), TouchHudSettings.parseSizes("STOP:20;C1:200;INVALID:99"))
    }

    @Test fun controlGroupButtonsCoverRetailDigitSlots() {
        val groups = TouchHudSettings.buttons.filter { it.groupNumber != null }
        assertEquals((1..10).toList(), groups.map { it.groupNumber })
        assertEquals(8, groups.first().defaultKeyCode) // Android KEYCODE_1
        assertEquals(16, groups[8].defaultKeyCode) // Android KEYCODE_9
        assertEquals(7, groups.last().defaultKeyCode) // Android KEYCODE_0 / retail team 10
    }

    @Test fun commandButtonsUseStockAndroidPhysicalKeyCodes() {
        assertEquals(47, TouchHudSettings.buttons.first { it.id == "STOP" }.defaultKeyCode) // S
        assertEquals(29, TouchHudSettings.buttons.first { it.id == "GUARD" }.defaultKeyCode) // A
        assertEquals(32, TouchHudSettings.buttons.first { it.id == "DEPLOY" }.defaultKeyCode) // D
        assertEquals(51, TouchHudSettings.buttons.first { it.id == "SCATTER" }.defaultKeyCode) // W
    }
}
