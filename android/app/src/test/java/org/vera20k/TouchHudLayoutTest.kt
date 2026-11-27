package org.vera20k

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchHudLayoutTest {
    @Test
    fun roundTripKeepsKnownButtonPositions() {
        val positions = mapOf(
            "hud" to TouchHudPoint(0.91f, 0.82f),
            "type" to TouchHudPoint(0.24f, 0.68f),
            "group-10" to TouchHudPoint(0.73f, 0.32f),
        )

        assertEquals(positions, TouchHudLayout.parse(TouchHudLayout.serialize(positions)))
    }

    @Test
    fun parseClampsCoordinatesAndIgnoresUnknownOrMalformedEntries() {
        val parsed = TouchHudLayout.parse("hud,1.4,-0.2;unknown,0.5,0.5;edit,NaN,0.5;type,0.4,0.6")

        assertEquals(TouchHudPoint(1f, 0f), parsed["hud"])
        assertEquals(TouchHudPoint(0.4f, 0.6f), parsed["type"])
        assertFalse(parsed.containsKey("unknown"))
        assertFalse(parsed.containsKey("edit"))
    }

    @Test
    fun idsCoverTheVisibleHudAndExcludeUnrecognizedButtons() {
        assertTrue(TouchHudLayout.buttonIds.contains("hud"))
        assertTrue(TouchHudLayout.buttonIds.contains("group-1"))
        assertTrue(TouchHudLayout.buttonIds.contains("group-10"))
        assertTrue(TouchHudLayout.buttonIds.contains("STOP"))
        assertFalse(TouchHudLayout.buttonIds.contains("group-11"))
        assertFalse(TouchHudLayout.buttonIds.contains("UNRECOGNIZED"))
    }

    @Test
    fun legacyPositionsMigrateToThePortableSettingsIdsAndBack() {
        val legacy = mapOf(
            "hud" to TouchHudPoint(0.12f, 0.88f),
            "edit" to TouchHudPoint(0.23f, 0.77f),
            "type" to TouchHudPoint(0.34f, 0.66f),
            "group-1" to TouchHudPoint(0.45f, 0.55f),
            "group-10" to TouchHudPoint(0.56f, 0.44f),
        )

        val portable = TouchHudLayout.toSettingsPositions(legacy)
        assertEquals(legacy, TouchHudLayout.fromSettingsPositions(portable))
        assertEquals(legacy["hud"]?.let { it.x to it.y }, portable[TouchHudSettings.HUD_TOGGLE])
        assertEquals(legacy["edit"]?.let { it.x to it.y }, portable[TouchHudSettings.HUD_EDIT])
        assertFalse(portable.containsKey("C11"))
    }

    @Test
    fun configuredCommandPositionsRoundTripThroughTheSharedLayoutStore() {
        val source = mapOf("STOP" to TouchHudPoint(0.32f, 0.74f))

        assertEquals(source, TouchHudLayout.fromSettingsPositions(TouchHudLayout.toSettingsPositions(source)))
    }
}
