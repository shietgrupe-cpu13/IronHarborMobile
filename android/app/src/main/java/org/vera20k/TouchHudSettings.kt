package org.vera20k

import android.view.KeyEvent

/** Persisted Android control layout. It contains presentation/input bindings only;
 * actions are sent to the engine through the ordinary keyboard event path. */
internal object TouchHudSettings {
    const val PREFS = "iron_harbor_controls"
    const val HUD_ENABLED = "hud_enabled"
    const val HUD_VISIBLE = "hud_visible"
    const val HUD_POSITIONS = "hud_positions"
    const val HUD_SIZES = "hud_sizes"
    const val HUD_BUTTON_OPACITIES = "hud_button_opacities"
    const val HUD_BUTTON_LABELS = "hud_button_labels"
    const val HUD_SCALE = "hud_scale"
    const val HUD_OPACITY = "hud_opacity"
    const val HUD_LAYOUT = "hud_layout"
    const val HUD_CONTROL_SETS = "hud_control_sets"
    const val HUD_ACTIVE_SET = "hud_active_set"
    const val HUD_DOCK = "hud_dock"
    const val HUD_TOGGLE = "HUD_TOGGLE"
    const val HUD_TEXT = "HUD_TEXT"
    const val HUD_EDIT = "HUD_EDIT"
    const val HUD_HIDDEN = "hud_hidden"
    const val SET_ONE = "SET1"
    const val SET_TWO = "SET2"
    const val CONTROL_MODE = "control_mode"
    const val MODE_TOUCH = "touch"
    const val MODE_MIXED = "mixed"
    const val MODE_EXTERNAL = "external"

    fun parseControlMode(value: String?): String = when (value?.trim()?.lowercase()) {
        MODE_TOUCH -> MODE_TOUCH
        MODE_EXTERNAL -> MODE_EXTERNAL
        else -> MODE_MIXED
    }

    const val LAYOUT_FREE = 0
    const val LAYOUT_TWO_THUMB = 1
    const val LAYOUT_YURI = 2

    data class Button(
        val id: String,
        val label: String,
        val defaultKeyCode: Int,
        val groupNumber: Int? = null,
        val shortcutLabel: String? = null,
    )

    // Android KeyEvent values match winit's physical keys. Defaults below use
    // Yuri's Revenge hotkeys for actions with a direct one-key equivalent.
    // Modifier-plus-click commands remain unbound; a one-shot key cannot
    // faithfully perform those interactions.
    private val commandButtons = listOf(
        Button("STOP", "STOP", 47),
        Button("GUARD", "GUARD", 35),
        Button("DEPLOY", "DEPLOY", 32),
        Button("SCATTER", "SCATTER", 52),
    ) + listOf(
        "FIRE" to "Force fire", "MOVE" to "Attack move", "ADD" to "Add selection",
        "MORE" to "More commands", "BOX" to "Box selection", "ROUTE" to "Waypoint route",
        "HOME" to "Home base", "TYPE" to "Select matching units", "NEXT" to "Next unit",
        "PREV" to "Previous unit", "EVENT" to "Last event", "FOLLOW" to "Follow selection",
        "BRIEF" to "Mission briefing", "PSI" to "Yuri: Psi Wave", "MINER" to "Slave Miner deploy / pack",
        "REVEAL" to "Psychic Reveal", "DOMINATOR" to "Psychic Dominator",
        "MUTATOR" to "Genetic Mutator", "BUILD" to "Build / confirm placement",
        "PATROL" to "Patrol between two points",
    ).map { (id, label) ->
        val keyCode = when (id) {
            // Planning Mode is a held key, not a one-shot command: the player
            // holds Z while clicking each route point, then releases it.
            "ROUTE" -> KeyEvent.KEYCODE_Z
            "TYPE" -> 48 // T: select matching unit type
            "NEXT" -> 41 // M: next unit
            "PREV" -> 42 // N: previous selection
            "EVENT" -> 62 // Space: last radar event
            "FOLLOW" -> 34 // F: follow selection
            "HOME" -> 36 // H: center on home/base
            else -> 0 // No direct single-key equivalent for this HUD action.
        }
        Button(id, label, keyCode)
    }

    private val groupButtons = (1..10).map { number ->
        val digit = if (number == 10) 0 else number
        Button("C$number", "C$number", 7 + digit, number, "Ctrl+$digit")
    }

    val setSelectorButtons = listOf(
        Button(SET_ONE, "SET 1", 0),
        Button(SET_TWO, "SET 2", 0),
    )
    /** Utility controls remain visible when the command HUD is hidden. */
    val utilityButtons = listOf(
        Button(HUD_EDIT, "EDIT", 0),
        Button(HUD_TOGGLE, "HIDE", 0),
        Button(HUD_TEXT, "TXT", 0),
    )
    // The set selectors participate in visibility/restore alongside actions,
    // but remain outside key remapping and engine command bindings.
    val buttons: List<Button> = commandButtons.take(4) + groupButtons + commandButtons.drop(4) + setSelectorButtons
    val layoutButtons: List<Button> = buttons + utilityButtons
    val defaultVisible: Set<String> = commandButtons.take(4).mapTo(linkedSetOf()) { it.id } +
        groupButtons.take(5).map { it.id } + listOf("TYPE") + setSelectorButtons.map { it.id }
    val remappableActions: List<Button> = commandButtons

    fun parseVisible(encoded: String?, isStored: Boolean): Set<String> {
        if (!isStored) return defaultVisible
        if (encoded.isNullOrBlank()) return emptySet()
        val valid = buttons.mapTo(hashSetOf()) { it.id }
        return encoded.split(',').map(String::trim).filter { it in valid }.toCollection(linkedSetOf())
    }

    fun encodeVisible(ids: Set<String>): String {
        val selected = ids.toSet()
        return buttons.asSequence().map { it.id }.filter { it in selected }.joinToString(",")
    }

    fun clampScale(value: Int): Int = value.coerceIn(70, 150)
    fun clampOpacity(value: Int): Int = value.coerceIn(30, 100)
    fun clampButtonSize(value: Int): Int = value.coerceIn(70, 150)

    fun buttonPixels(basePixels: Int, globalScale: Int, buttonScale: Int): Int =
        (basePixels.toLong() * clampScale(globalScale) * clampButtonSize(buttonScale) / 10_000L)
            .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    fun buttonAlpha(opacity: Int): Float = clampOpacity(opacity) / 100f

    fun buttonOpacity(id: String, opacities: Map<String, Int>, fallback: Int): Int =
        clampOpacity(opacities[id] ?: fallback)

    fun visibleForSet(visible: Set<String>, controlSets: Map<String, Int>, activeSet: Int): Set<String> =
        buttons.asSequence()
            .filter { it.id in visible }
            .filter {
                it.groupNumber != null || it.id == SET_ONE || it.id == SET_TWO ||
                    activeSet == 0 || (controlSets[it.id] ?: 0) == 0 || controlSets[it.id] == activeSet
            }
            .mapTo(linkedSetOf()) { it.id }

    fun defaultPosition(id: String, layout: Int = LAYOUT_FREE, dock: Int = 0): Pair<Float, Float> {
        if (id == HUD_EDIT) return when (dock.coerceIn(0, 2)) {
            0 -> 0.03f to 0.05f
            1 -> 0.03f to 0.05f
            else -> 0.90f to 0.05f
        }
        if (id == HUD_TOGGLE) return when (dock.coerceIn(0, 2)) {
            0 -> 0.03f to 0.90f
            1 -> 0.03f to 0.50f
            else -> 0.90f to 0.90f
        }
        if (id == HUD_TEXT) return when (dock.coerceIn(0, 2)) {
            0 -> 0.03f to 0.78f
            1 -> 0.03f to 0.61f
            else -> 0.90f to 0.78f
        }
        if (id == SET_ONE) return 0.48f to 0.06f
        if (id == SET_TWO) return 0.64f to 0.06f
        val button = buttons.firstOrNull { it.id == id } ?: return 0.85f to 0.85f
        val actionIndex = commandButtons.indexOfFirst { it.id == id }
        if (dock == 1) {
            if (button.groupNumber != null) return 0.03f to (0.06f + (button.groupNumber - 1) * 0.085f).coerceAtMost(0.88f)
            val slot = actionIndex.coerceAtLeast(0)
            return (0.15f + (slot % 2) * 0.12f) to (0.06f + (slot / 2) * 0.085f).coerceAtMost(0.88f)
        }
        if (dock == 2) {
            if (button.groupNumber != null) return 0.92f to (0.06f + (button.groupNumber - 1) * 0.085f).coerceAtMost(0.88f)
            val slot = actionIndex.coerceAtLeast(0)
            return (0.48f + (slot % 4) * 0.12f) to (0.82f - (slot / 4) * 0.12f).coerceAtLeast(0.06f)
        }
        if (layout == LAYOUT_TWO_THUMB) {
            if (button.groupNumber != null) return 0.03f to (0.10f + ((button.groupNumber - 1) % 5) * 0.15f)
            if (actionIndex >= 4) {
                val slot = actionIndex - 4
                return (0.55f + (slot % 4) * 0.12f) to (0.09f + (slot / 4) * 0.14f).coerceAtMost(0.79f)
            }
            return when (id) {
                "STOP" -> 0.05f to 0.73f
                "GUARD" -> 0.17f to 0.73f
                "DEPLOY" -> 0.29f to 0.73f
                "SCATTER" -> 0.41f to 0.73f
                else -> 0.86f to 0.82f
            }
        }
        if (layout == LAYOUT_YURI) {
            if (button.groupNumber != null) return 0.91f to (0.08f + ((button.groupNumber - 1) % 5) * 0.15f)
            if (actionIndex >= 4) {
                val slot = actionIndex - 4
                return (0.52f + (slot % 4) * 0.12f) to (0.08f + (slot / 4) * 0.14f).coerceAtMost(0.78f)
            }
            return when (id) {
                "STOP" -> 0.03f to 0.78f
                "GUARD" -> 0.14f to 0.78f
                "DEPLOY" -> 0.25f to 0.78f
                "SCATTER" -> 0.36f to 0.78f
                else -> 0.78f to 0.84f
            }
        }
        val index = button.groupNumber?.minus(1) ?: actionIndex.coerceAtLeast(0)
        val row = if (button.groupNumber == null) 0 else 1 + index / 5
        val column = if (button.groupNumber == null) index % 8 else index % 5
        val actionRow = if (button.groupNumber == null) index / 8 else row
        return (0.04f + column * 0.12f).coerceIn(0f, 1f) to
            (if (button.groupNumber == null) 0.82f - actionRow * 0.14f else 0.10f + actionRow * 0.15f).coerceIn(0f, 1f)
    }

    fun parsePositions(encoded: String?): Map<String, Pair<Float, Float>> {
        val valid = layoutButtons.mapTo(hashSetOf()) { it.id }
        val result = linkedMapOf<String, Pair<Float, Float>>()
        encoded.orEmpty().split(';').forEach { entry ->
            val pair = entry.split(':', limit = 2)
            if (pair.size != 2 || pair[0] !in valid) return@forEach
            val values = pair[1].split(',')
            if (values.size < 2) return@forEach
            val x = values[0].toFloatOrNull() ?: return@forEach
            val y = values[1].toFloatOrNull() ?: return@forEach
            if (!x.isFinite() || !y.isFinite()) return@forEach
            result[pair[0]] = x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
        }
        return result
    }

    fun encodePositions(positions: Map<String, Pair<Float, Float>>): String = layoutButtons.asSequence()
        .map { it.id }
        .mapNotNull { id -> positions[id]?.let { id to it } }
        .joinToString(";") { (id, point) -> "$id:${point.first.coerceIn(0f, 1f)},${point.second.coerceIn(0f, 1f)}" }

    fun parseSizes(encoded: String?): Map<String, Int> {
        val valid = layoutButtons.mapTo(hashSetOf()) { it.id }
        return encoded.orEmpty().split(';').mapNotNull { entry ->
            val pair = entry.split(':', limit = 2)
            if (pair.size != 2 || pair[0] !in valid) return@mapNotNull null
            val size = pair[1].toIntOrNull() ?: return@mapNotNull null
            pair[0] to clampButtonSize(size)
        }.toMap()
    }

    fun encodeSizes(sizes: Map<String, Int>): String = layoutButtons.asSequence()
        .mapNotNull { button -> sizes[button.id]?.let { button.id to clampButtonSize(it) } }
        .joinToString(";") { (id, size) -> "$id:$size" }

    fun parseButtonOpacities(encoded: String?): Map<String, Int> {
        val valid = layoutButtons.mapTo(hashSetOf()) { it.id }
        return encoded.orEmpty().split(';').mapNotNull { entry ->
            val pair = entry.split(':', limit = 2)
            if (pair.size != 2 || pair[0] !in valid) return@mapNotNull null
            val opacity = pair[1].toIntOrNull() ?: return@mapNotNull null
            pair[0] to clampOpacity(opacity)
        }.toMap()
    }

    fun encodeButtonOpacities(opacities: Map<String, Int>): String = layoutButtons.asSequence()
        .mapNotNull { button -> opacities[button.id]?.let { button.id to clampOpacity(it) } }
        .joinToString(";") { (id, opacity) -> "$id:$opacity" }

    fun sanitizeButtonLabel(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.any { it.isISOControl() || it == ':' || it == ';' }) return null
        val label = trimmed.replace(Regex("\\s+"), " ")
        return label.takeIf { it.isNotEmpty() && it.length <= 8 }
    }

    fun parseButtonLabels(encoded: String?): Map<String, String> {
        val valid = layoutButtons.mapTo(hashSetOf()) { it.id }
        return encoded.orEmpty().split(';').mapNotNull { entry ->
            val pair = entry.split(':', limit = 2)
            if (pair.size != 2 || pair[0] !in valid) return@mapNotNull null
            sanitizeButtonLabel(pair[1])?.let { pair[0] to it }
        }.toMap()
    }

    fun encodeButtonLabels(labels: Map<String, String>): String = layoutButtons.asSequence()
        .mapNotNull { button -> labels[button.id]?.let(::sanitizeButtonLabel)?.let { button.id to it } }
        .joinToString(";") { (id, label) -> "$id:$label" }

    /** Stable, dependency-free backup format. Each value is validated on restore. */
    fun encodeLayoutBackup(
        visible: Set<String>,
        positions: Map<String, Pair<Float, Float>>,
        sizes: Map<String, Int>,
        scale: Int,
        opacity: Int,
        layout: Int,
        keyBindings: Map<String, Int> = emptyMap(),
        controlSets: Map<String, Int> = emptyMap(),
        dock: Int = 0,
        activeSet: Int = 1,
        hidden: Boolean = false,
        buttonOpacities: Map<String, Int> = emptyMap(),
        buttonLabels: Map<String, String> = emptyMap(),
    ): String = listOf(
        "format=iron-harbor-hud",
        "version=5",
        "visible=${encodeVisible(visible)}",
        "positions=${encodePositions(positions)}",
        "sizes=${encodeSizes(sizes)}",
        "keybindings=${encodeKeyBindings(keyBindings)}",
        "controlsets=${encodeControlSets(controlSets)}",
        "dock=${dock.coerceIn(0, 2)}",
        "activeSet=${activeSet.coerceIn(1, 2)}",
        "hidden=$hidden",
        "buttonOpacities=${encodeButtonOpacities(buttonOpacities)}",
        "buttonLabels=${encodeButtonLabels(buttonLabels)}",
        "scale=${clampScale(scale)}",
        "opacity=${clampOpacity(opacity)}",
        "layout=${layout.coerceIn(LAYOUT_FREE, LAYOUT_YURI)}",
        "",
    ).joinToString("\n")

    data class LayoutBackup(
        val visible: Set<String>,
        val positions: Map<String, Pair<Float, Float>>,
        val sizes: Map<String, Int>,
        val scale: Int,
        val opacity: Int,
        val layout: Int,
        val keyBindings: Map<String, Int>,
        val controlSets: Map<String, Int>,
        val dock: Int,
        val activeSet: Int,
        val hidden: Boolean,
        val buttonOpacities: Map<String, Int>,
        val buttonLabels: Map<String, String>,
    )

    fun decodeLayoutBackup(source: String): LayoutBackup? {
        val fields = source.lineSequence().mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
        }.toMap()
        val version = fields["version"]?.toIntOrNull()
        if (fields["format"] != "iron-harbor-hud" || version == null || version !in 1..5) return null
        val visible = parseVisible(fields["visible"], true)
        val scale = fields["scale"]?.toIntOrNull() ?: return null
        val opacity = fields["opacity"]?.toIntOrNull() ?: return null
        val layout = fields["layout"]?.toIntOrNull() ?: return null
        val dock = if (version >= 3) fields["dock"]?.toIntOrNull() ?: return null else 0
        val activeSet = fields["activeSet"]?.toIntOrNull() ?: 1
        val hidden = when (fields["hidden"]?.lowercase()) {
            null, "false" -> false
            "true" -> true
            else -> return null
        }
        if (scale !in 70..150 || opacity !in 30..100 || layout !in LAYOUT_FREE..LAYOUT_YURI || dock !in 0..2 || activeSet !in 1..2) return null
        return LayoutBackup(
            visible,
            parsePositions(fields["positions"]),
            parseSizes(fields["sizes"]),
            scale,
            opacity,
            layout,
            parseKeyBindings(fields["keybindings"]),
            if (version >= 2) parseControlSets(fields["controlsets"]) else emptyMap(),
            dock,
            activeSet,
            hidden,
            if (version >= 4) parseButtonOpacities(fields["buttonOpacities"]) else emptyMap(),
            if (version >= 5) parseButtonLabels(fields["buttonLabels"]) else emptyMap(),
        )
    }

    fun parseKeyBindings(encoded: String?): Map<String, Int> {
        val valid = remappableActions.mapTo(hashSetOf()) { it.id }
        return encoded.orEmpty().split(';').mapNotNull { entry ->
            val pair = entry.split(':', limit = 2)
            if (pair.size != 2 || pair[0] !in valid) return@mapNotNull null
            val keyCode = pair[1].toIntOrNull() ?: return@mapNotNull null
            if (keyCode !in 1..1024) return@mapNotNull null
            pair[0] to keyCode
        }.toMap()
    }

    fun encodeKeyBindings(keyBindings: Map<String, Int>): String = remappableActions.asSequence()
        .mapNotNull { action -> keyBindings[action.id]?.takeIf { it in 1..1024 }?.let { action.id to it } }
        .joinToString(";") { (id, keyCode) -> "$id:$keyCode" }

    fun parseControlSets(encoded: String?): Map<String, Int> {
        val valid = remappableActions.mapTo(hashSetOf()) { it.id }
        return encoded.orEmpty().split(';').mapNotNull { entry ->
            val pair = entry.split(':', limit = 2)
            if (pair.size != 2 || pair[0] !in valid) return@mapNotNull null
            val set = pair[1].toIntOrNull() ?: return@mapNotNull null
            if (set !in 0..2) return@mapNotNull null
            pair[0] to set
        }.toMap()
    }

    fun encodeControlSets(controlSets: Map<String, Int>): String = remappableActions.asSequence()
        .mapNotNull { action -> controlSets[action.id]?.takeIf { it in 0..2 }?.let { action.id to it } }
        .joinToString(";") { (id, set) -> "$id:$set" }
}
