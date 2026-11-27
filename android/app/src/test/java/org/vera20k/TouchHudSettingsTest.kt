package org.vera20k

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchHudSettingsTest {
    @Test fun controlModePreferencesAreValidatedWithMixedDefault() {
        assertEquals(TouchHudSettings.MODE_MIXED, TouchHudSettings.parseControlMode(null))
        assertEquals(TouchHudSettings.MODE_TOUCH, TouchHudSettings.parseControlMode(" TOUCH "))
        assertEquals(TouchHudSettings.MODE_EXTERNAL, TouchHudSettings.parseControlMode("external"))
        assertEquals(TouchHudSettings.MODE_MIXED, TouchHudSettings.parseControlMode("invalid"))
    }

    @Test fun visibleButtonStorageKeepsOnlyKnownIdsAndAllowsAllHidden() {
        assertEquals(TouchHudSettings.defaultVisible, TouchHudSettings.parseVisible(null, false))
        assertEquals(setOf("STOP", "C1"), TouchHudSettings.parseVisible("STOP,C1,UNKNOWN,STOP", true))
        assertTrue(TouchHudSettings.parseVisible("", true).isEmpty())
        assertEquals("STOP,C1", TouchHudSettings.encodeVisible(setOf("C1", "STOP", "UNKNOWN")))
    }

    @Test fun setSelectorsCanBeHiddenAndRestoredThroughVisibleButtonStorage() {
        val selectors = setOf(TouchHudSettings.SET_ONE, TouchHudSettings.SET_TWO)
        assertTrue(TouchHudSettings.defaultVisible.containsAll(selectors))
        assertEquals(selectors, TouchHudSettings.parseVisible("SET1,SET2", true))
        assertEquals("SET1,SET2", TouchHudSettings.encodeVisible(selectors))
        assertEquals(selectors, TouchHudSettings.visibleForSet(selectors, emptyMap(), activeSet = 1))
        assertEquals(selectors, TouchHudSettings.visibleForSet(selectors, emptyMap(), activeSet = 2))
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

    @Test fun previewAndLiveSetsUseTheSameVisibilityRulesAndSaveSelectorPlacement() {
        val visible = setOf("STOP", "FIRE", "MOVE", "C1", "C2")
        val assignments = mapOf("FIRE" to 2, "MOVE" to 1)
        assertEquals(
            visible,
            TouchHudSettings.visibleForSet(visible, assignments, activeSet = 0),
        )
        assertEquals(
            setOf("STOP", "MOVE", "C1", "C2"),
            TouchHudSettings.visibleForSet(visible, assignments, activeSet = 1),
        )
        assertEquals(
            setOf("STOP", "FIRE", "C1", "C2"),
            TouchHudSettings.visibleForSet(visible, assignments, activeSet = 2),
        )
        assertEquals(
            mapOf(TouchHudSettings.SET_ONE to (0.2f to 0.3f), TouchHudSettings.SET_TWO to (0.7f to 0.4f)),
            TouchHudSettings.parsePositions(
                TouchHudSettings.encodePositions(
                    mapOf(TouchHudSettings.SET_ONE to (0.2f to 0.3f), TouchHudSettings.SET_TWO to (0.7f to 0.4f)),
                ),
            ),
        )
        assertEquals(
            mapOf(TouchHudSettings.SET_ONE to 120, TouchHudSettings.HUD_TOGGLE to 115),
            TouchHudSettings.parseSizes(TouchHudSettings.encodeSizes(mapOf(
                TouchHudSettings.SET_ONE to 120,
                TouchHudSettings.HUD_TOGGLE to 115,
            ))),
        )
    }

    @Test fun inGameEditAndHideControlsArePartOfTheMovableLayout() {
        assertEquals(listOf(TouchHudSettings.HUD_EDIT, TouchHudSettings.HUD_TOGGLE, TouchHudSettings.HUD_TEXT),
            TouchHudSettings.utilityButtons.map { it.id })
        assertTrue(TouchHudSettings.layoutButtons.any { it.id == TouchHudSettings.HUD_EDIT })
        assertTrue(TouchHudSettings.layoutButtons.any { it.id == TouchHudSettings.HUD_TOGGLE })
        assertTrue(TouchHudSettings.layoutButtons.any { it.id == TouchHudSettings.HUD_TEXT })
        val points = mapOf(
            TouchHudSettings.HUD_EDIT to (0.31f to 0.12f),
            TouchHudSettings.HUD_TOGGLE to (0.73f to 0.88f),
            TouchHudSettings.HUD_TEXT to (0.47f to 0.8f),
        )
        assertEquals(points, TouchHudSettings.parsePositions(TouchHudSettings.encodePositions(points)))
    }

    @Test fun textEntryButtonHasSafeDefaultPositionsAndCanBeMoved() {
        for (dock in 0..2) {
            val (x, y) = TouchHudSettings.defaultPosition(TouchHudSettings.HUD_TEXT, dock = dock)
            assertTrue("dock $dock x=$x", x in 0f..1f)
            assertTrue("dock $dock y=$y", y in 0f..1f)
        }
        assertEquals(0.47f to 0.8f, TouchHudSettings.parsePositions(
            "HUD_TEXT:0.47,0.8",
        ).getValue(TouchHudSettings.HUD_TEXT))
    }

    @Test fun commandButtonsUseYurisRevengeHotkeyDefaultsWhereActionsMatch() {
        assertEquals(47, TouchHudSettings.buttons.first { it.id == "STOP" }.defaultKeyCode) // S
        assertEquals(35, TouchHudSettings.buttons.first { it.id == "GUARD" }.defaultKeyCode) // G
        assertEquals(32, TouchHudSettings.buttons.first { it.id == "DEPLOY" }.defaultKeyCode) // D
        assertEquals(52, TouchHudSettings.buttons.first { it.id == "SCATTER" }.defaultKeyCode) // X
        assertEquals(48, TouchHudSettings.buttons.first { it.id == "TYPE" }.defaultKeyCode) // T
        assertEquals(KeyEvent.KEYCODE_Z, TouchHudSettings.buttons.first { it.id == "ROUTE" }.defaultKeyCode)
        assertEquals(41, TouchHudSettings.buttons.first { it.id == "NEXT" }.defaultKeyCode) // M
        assertEquals(42, TouchHudSettings.buttons.first { it.id == "PREV" }.defaultKeyCode) // N
        assertEquals(62, TouchHudSettings.buttons.first { it.id == "EVENT" }.defaultKeyCode) // Space
        assertEquals(34, TouchHudSettings.buttons.first { it.id == "FOLLOW" }.defaultKeyCode) // F
        assertEquals(36, TouchHudSettings.buttons.first { it.id == "HOME" }.defaultKeyCode) // H
        assertEquals(24, TouchHudSettings.remappableActions.size)
        assertEquals(0, TouchHudSettings.remappableActions.first { it.id == "FIRE" }.defaultKeyCode) // Unknown until bound to an existing game key.
    }

    @Test fun waypointButtonHoldsPlanningModeAcrossClicksAndReleasesOnLifecycleExit() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val latch = TouchHudKeyLatch { keyCode, pressed -> events += keyCode to pressed }
        assertTrue(latch.toggle("ROUTE", KeyEvent.KEYCODE_Z))
        assertEquals(listOf(KeyEvent.KEYCODE_Z to true), events)
        assertTrue(!latch.toggle("ROUTE", KeyEvent.KEYCODE_Z))
        assertEquals(listOf(KeyEvent.KEYCODE_Z to true, KeyEvent.KEYCODE_Z to false), events)
        latch.toggle("ROUTE", KeyEvent.KEYCODE_Z)
        latch.releaseAll()
        assertEquals(listOf(
            KeyEvent.KEYCODE_Z to true,
            KeyEvent.KEYCODE_Z to false,
            KeyEvent.KEYCODE_Z to true,
            KeyEvent.KEYCODE_Z to false,
        ), events)
    }

    @Test fun controlGroupButtonsMapToTheVanillaCtrlDigitTeams() {
        val groups = TouchHudSettings.buttons.filter { it.groupNumber != null }
        assertEquals(10, groups.size)
        for (group in groups) {
            val digit = group.groupNumber!! % 10
            assertEquals("C${group.groupNumber}", group.id)
            assertEquals(7 + digit, group.defaultKeyCode) // Ctrl+1..Ctrl+9; Ctrl+0 for C10
            assertEquals("Ctrl+$digit", group.shortcutLabel)
        }
    }

    @Test fun ra2InspiredPresetPositionsKeepControlsInsideNormalizedScreen() {
        for (layout in TouchHudSettings.LAYOUT_FREE..TouchHudSettings.LAYOUT_YURI) {
            TouchHudSettings.buttons.forEach { button ->
                val (x, y) = TouchHudSettings.defaultPosition(button.id, layout)
                assertTrue("${button.id} x=$x", x in 0f..1f)
                assertTrue("${button.id} y=$y", y in 0f..1f)
            }
        }
        for (dock in 0..2) {
            TouchHudSettings.buttons.forEach { button ->
                val (x, y) = TouchHudSettings.defaultPosition(button.id, TouchHudSettings.LAYOUT_FREE, dock)
                assertTrue("${button.id} dock $dock x=$x", x in 0f..1f)
                assertTrue("${button.id} dock $dock y=$y", y in 0f..1f)
            }
        }
        assertEquals(0.90f to 0.90f, TouchHudSettings.defaultPosition(TouchHudSettings.HUD_TOGGLE, dock = 2))
        assertTrue(TouchHudSettings.defaultPosition("C1", TouchHudSettings.LAYOUT_TWO_THUMB).first <
            TouchHudSettings.defaultPosition("C1", TouchHudSettings.LAYOUT_YURI).first)
    }

    @Test fun hudLayoutBackupRoundTripsAndRejectsUnsupportedOrMalformedData() {
        val encoded = TouchHudSettings.encodeLayoutBackup(
            setOf("STOP", "C3"),
            mapOf("STOP" to (0.2f to 0.7f), "C3" to (0.8f to 0.4f),
                TouchHudSettings.HUD_EDIT to (0.03f to 0.1f), TouchHudSettings.HUD_TOGGLE to (0.03f to 0.9f)),
            mapOf("C3" to 125, TouchHudSettings.HUD_TOGGLE to 110),
            120,
            70,
            TouchHudSettings.LAYOUT_YURI,
            mapOf("FIRE" to 32, "PATROL" to 1030, "UNKNOWN" to 44),
            mapOf("FIRE" to 1, "PATROL" to 2, "STOP" to 9, "MOVE" to 0),
            2,
            2,
            true,
        )
        val decoded = TouchHudSettings.decodeLayoutBackup(encoded)
        assertEquals(setOf("STOP", "C3"), decoded?.visible)
        assertEquals(125, decoded?.sizes?.get("C3"))
        assertEquals(120, decoded?.scale)
        assertEquals(70, decoded?.opacity)
        assertEquals(TouchHudSettings.LAYOUT_YURI, decoded?.layout)
        assertEquals(mapOf("FIRE" to 32), decoded?.keyBindings)
        assertEquals(mapOf("FIRE" to 1, "MOVE" to 0, "PATROL" to 2), decoded?.controlSets)
        assertEquals(2, decoded?.dock)
        assertEquals(2, decoded?.activeSet)
        assertTrue(decoded?.hidden == true)
        assertEquals(0.9f, decoded?.positions?.get(TouchHudSettings.HUD_TOGGLE)?.second ?: Float.NaN, 0.0001f)
        assertEquals(0.03f, decoded?.positions?.get(TouchHudSettings.HUD_EDIT)?.first ?: Float.NaN, 0.0001f)
        assertEquals(110, decoded?.sizes?.get(TouchHudSettings.HUD_TOGGLE))
        assertEquals(null, TouchHudSettings.decodeLayoutBackup(encoded.replace("version=3", "version=88")))
        assertEquals(null, TouchHudSettings.decodeLayoutBackup(encoded.replace("scale=120", "scale=999")))
        assertEquals(mapOf("GUARD" to 29), TouchHudSettings.parseKeyBindings("GUARD:29;STOP:0;FIRE:nope;OTHER:41"))
        assertEquals(mapOf("MOVE" to 2), TouchHudSettings.parseControlSets("MOVE:2;STOP:9;C1:1;BAD:nope"))
        val b14 = encoded.replace("version=3", "version=2").lineSequence()
            .filterNot { it.startsWith("dock=") }.joinToString("\n")
        assertEquals(mapOf("FIRE" to 1, "MOVE" to 0, "PATROL" to 2), TouchHudSettings.decodeLayoutBackup(b14)?.controlSets)
        val legacy = encoded.replace("version=3", "version=1").lineSequence()
            .filterNot { it.startsWith("controlsets=") || it.startsWith("dock=") }.joinToString("\n")
        assertEquals(emptyMap<String, Int>(), TouchHudSettings.decodeLayoutBackup(legacy)?.controlSets)
        assertEquals(1, TouchHudSettings.decodeLayoutBackup(legacy)?.activeSet)
        assertFalse(TouchHudSettings.decodeLayoutBackup(legacy)?.hidden ?: true)
        assertEquals(0, TouchHudSettings.decodeLayoutBackup(legacy)?.dock)
    }
}
