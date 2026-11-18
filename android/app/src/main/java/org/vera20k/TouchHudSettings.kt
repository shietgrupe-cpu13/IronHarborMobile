package org.vera20k

/** Persisted Android control layout. It contains presentation/input bindings only;
 * actions are sent to the engine through the ordinary keyboard event path. */
internal object TouchHudSettings {
    const val PREFS = "iron_harbor_controls"
    const val HUD_ENABLED = "hud_enabled"
    const val HUD_VISIBLE = "hud_visible"
    const val HUD_POSITIONS = "hud_positions"
    const val HUD_SIZES = "hud_sizes"
    const val HUD_SCALE = "hud_scale"
    const val HUD_OPACITY = "hud_opacity"

    data class Button(val id: String, val label: String, val defaultKeyCode: Int, val groupNumber: Int? = null)

    // Android KeyEvent values for S, A, D and W match winit's physical keys.
    // These are the stock VERA/RA2 Hotkey values; players can bind each action
    // to another key that already has the desired engine command.
    val buttons: List<Button> = listOf(
        Button("STOP", "STOP", 47),
        Button("GUARD", "GUARD", 29),
        Button("DEPLOY", "DEPLOY", 32),
        Button("SCATTER", "SCATTER", 51),
    ) + (1..10).map { number ->
        val digit = if (number == 10) 0 else number
        Button("C$number", "C$number", if (digit == 0) 7 else 7 + digit, number)
    }

    val defaultVisible: Set<String> = buttons.take(9).mapTo(linkedSetOf()) { it.id }

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

    fun defaultPosition(id: String): Pair<Float, Float> {
        val button = buttons.firstOrNull { it.id == id } ?: return 0.85f to 0.85f
        val index = button.groupNumber?.minus(1) ?: when (id) {
            "STOP" -> 0
            "GUARD" -> 1
            "DEPLOY" -> 2
            "SCATTER" -> 3
            else -> 0
        }
        val row = if (button.groupNumber == null) 0 else 1 + index / 5
        val column = if (button.groupNumber == null) index else index % 5
        return (0.06f + column * 0.135f).coerceIn(0f, 1f) to (0.82f + row * 0.085f).coerceAtMost(1f)
    }

    fun parsePositions(encoded: String?): Map<String, Pair<Float, Float>> {
        val valid = buttons.mapTo(hashSetOf()) { it.id }
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

    fun encodePositions(positions: Map<String, Pair<Float, Float>>): String = buttons.asSequence()
        .mapNotNull { button -> positions[button.id]?.let { button.id to it } }
        .joinToString(";") { (id, point) -> "$id:${point.first.coerceIn(0f, 1f)},${point.second.coerceIn(0f, 1f)}" }

    fun parseSizes(encoded: String?): Map<String, Int> {
        val valid = buttons.mapTo(hashSetOf()) { it.id }
        return encoded.orEmpty().split(';').mapNotNull { entry ->
            val pair = entry.split(':', limit = 2)
            if (pair.size != 2 || pair[0] !in valid) return@mapNotNull null
            val size = pair[1].toIntOrNull() ?: return@mapNotNull null
            pair[0] to clampButtonSize(size)
        }.toMap()
    }

    fun encodeSizes(sizes: Map<String, Int>): String = buttons.asSequence()
        .mapNotNull { button -> sizes[button.id]?.let { button.id to clampButtonSize(it) } }
        .joinToString(";") { (id, size) -> "$id:$size" }
}
