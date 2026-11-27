package org.vera20k

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/** A compact, optional view overlay; gameplay touch outside buttons still reaches GameActivity. */
internal class TouchHudOverlay(private val activity: VeraActivity) {
    private val density = activity.resources.displayMetrics.density
    private val baseButtonSize = (46 * density).toInt()
    private val preferences = activity.getSharedPreferences(TouchHudSettings.PREFS, 0)
    private val positions = loadPositions()
    private val overlay = FrameLayout(activity).apply {
        clipChildren = false
        clipToPadding = false
        isClickable = false
        isFocusable = false
        setBackgroundColor(Color.TRANSPARENT)
    }
    private val buttons = LinkedHashMap<String, TextView>()
    private val heldKeyDownTimes = HashMap<Int, Long>()
    private val heldKeys = TouchHudKeyLatch { keyCode, pressed ->
        val now = SystemClock.uptimeMillis()
        if (pressed) {
            heldKeyDownTimes[keyCode] = now
            dispatchKey(keyCode, KeyEvent.ACTION_DOWN, now, now, 0)
        } else {
            val downTime = heldKeyDownTimes.remove(keyCode) ?: now
            dispatchKey(keyCode, KeyEvent.ACTION_UP, downTime, now, 0)
        }
    }
    private var routeModeHeld = false
    private var controlsVisible = preferences.getBoolean(TouchHudSettings.HUD_ENABLED, true) &&
        !preferences.getBoolean(TouchHudSettings.HUD_HIDDEN, false)
    private var editing = false

    fun attach() {
        if (!preferences.getBoolean(TouchHudSettings.HUD_ENABLED, true)) return
        addButton("hud", "HUD", "Show or hide touch controls") { toggleControls() }
        val editButton = addButton("edit", "EDIT", "Move controls; hold to open layout editor") {
            if (!editing) {
                releaseHeldInputs()
                editing = true
                setVisibility()
            } else {
                editing = false
                setVisibility()
            }
        }
        editButton.setOnLongClickListener {
            if (!editing) {
                showLayoutEditor()
                true
            } else false
        }

        TouchHudSettings.buttons.forEach { action ->
            val id = overlayId(action)
            val label = buttonLabel(id, action)
            val description = when {
                action.groupNumber != null -> "Control group ${action.groupNumber}: tap to select, hold to assign"
                action.id == TouchHudSettings.SET_ONE || action.id == TouchHudSettings.SET_TWO -> "Show control set ${if (action.id == TouchHudSettings.SET_ONE) 1 else 2}"
                else -> action.label
            }
            val button = addButton(id, label, description) { activate(action) }
            if (action.groupNumber != null) {
                val group = action.groupNumber
                button.setOnLongClickListener {
                    if (editing) return@setOnLongClickListener false
                    sendGroupAssign(group)
                    true
                }
            }
        }
        activity.addContentView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionButtons() }
        setVisibility()
        overlay.post { positionButtons() }
    }

    private fun addButton(id: String, label: String, description: String, click: () -> Unit): TextView {
        val button = TextView(activity).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            textSize = if (id == "hud" || id == "edit") 12f else 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(0xCC14231F.toInt())
                setStroke((1.5f * density).toInt(), 0xCC79C47E.toInt())
            }
            elevation = 6 * density
            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener { click() }
            setOnTouchListener { view, event -> dragButton(id, view, event) }
        }
        buttons[id] = button
        val size = buttonPixels(id)
        overlay.addView(button, FrameLayout.LayoutParams(size, size))
        return button
    }

    private fun toggleControls() {
        controlsVisible = !controlsVisible
        if (!controlsVisible) {
            releaseHeldInputs()
            editing = false
        }
        preferences.edit().putBoolean(TouchHudSettings.HUD_HIDDEN, !controlsVisible).apply()
        setVisibility()
    }

    fun releaseHeldInputs() {
        heldKeys.releaseAll()
        routeModeHeld = false
        buttons["ROUTE"]?.text = "ROUTE"
    }

    private fun setVisibility() {
        val visible = TouchHudSettings.parseVisible(
            preferences.getString(TouchHudSettings.HUD_VISIBLE, null),
            preferences.contains(TouchHudSettings.HUD_VISIBLE),
        )
        val controlSets = TouchHudSettings.parseControlSets(
            preferences.getString(TouchHudSettings.HUD_CONTROL_SETS, null),
        )
        val activeSet = preferences.getInt(TouchHudSettings.HUD_ACTIVE_SET, 1).coerceIn(1, 2)
        val active = TouchHudSettings.visibleForSet(visible, controlSets, activeSet)
        val buttonOpacities = TouchHudSettings.parseButtonOpacities(
            preferences.getString(TouchHudSettings.HUD_BUTTON_OPACITIES, null),
        )
        buttons.forEach { (id, button) ->
            button.visibility = when {
                id == "hud" -> View.VISIBLE
                id == "edit" -> View.VISIBLE
                controlsVisible && isEnabled(id, active) -> View.VISIBLE
                else -> View.GONE
            }
            button.alpha = TouchHudSettings.buttonAlpha(
                settingsId(id)?.let {
                    TouchHudSettings.buttonOpacity(
                        it,
                        buttonOpacities,
                        preferences.getInt(TouchHudSettings.HUD_OPACITY, 88),
                    )
                } ?: preferences.getInt(TouchHudSettings.HUD_OPACITY, 88),
            ) * if (editing && id != "edit") 0.62f else 1f
            val size = buttonPixels(id)
            button.layoutParams = (button.layoutParams ?: FrameLayout.LayoutParams(size, size)).apply {
                width = size
                height = size
            }
        }
        buttons["edit"]?.apply {
            text = if (editing) "DONE" else "EDIT"
            contentDescription = if (editing) "Finish moving touch controls" else "Move controls; hold to open layout editor"
        }
        buttons["ROUTE"]?.text = if (routeModeHeld) "ROUTE ON" else "ROUTE"
        positionButtons()
    }

    private fun showLayoutEditor() {
        releaseHeldInputs()
        val visible = TouchHudSettings.parseVisible(
            preferences.getString(TouchHudSettings.HUD_VISIBLE, null),
            preferences.contains(TouchHudSettings.HUD_VISIBLE),
        ).toMutableSet()
        val positions = TouchHudSettings.parsePositions(
            preferences.getString(TouchHudSettings.HUD_POSITIONS, null),
        ).toMutableMap()
        val sizes = TouchHudSettings.parseSizes(
            preferences.getString(TouchHudSettings.HUD_SIZES, null),
        ).toMutableMap()
        val buttonOpacities = TouchHudSettings.parseButtonOpacities(
            preferences.getString(TouchHudSettings.HUD_BUTTON_OPACITIES, null),
        ).toMutableMap()
        val controlSets = TouchHudSettings.parseControlSets(
            preferences.getString(TouchHudSettings.HUD_CONTROL_SETS, null),
        )
        val layout = preferences.getInt(TouchHudSettings.HUD_LAYOUT, TouchHudSettings.LAYOUT_FREE)
            .coerceIn(TouchHudSettings.LAYOUT_FREE, TouchHudSettings.LAYOUT_YURI)
        val dock = preferences.getInt(TouchHudSettings.HUD_DOCK, 0).coerceIn(0, 2)
        val scale = TouchHudSettings.clampScale(preferences.getInt(TouchHudSettings.HUD_SCALE, 100))
        val opacity = TouchHudSettings.clampOpacity(preferences.getInt(TouchHudSettings.HUD_OPACITY, 88))
        TouchHudLayoutEditor(activity).show(
            visible, positions, sizes, controlSets, layout, dock, scale, opacity, buttonOpacities,
        ) {
            preferences.edit()
                .putString(TouchHudSettings.HUD_VISIBLE, TouchHudSettings.encodeVisible(visible))
                .putString(TouchHudSettings.HUD_POSITIONS, TouchHudSettings.encodePositions(positions))
                .putString(TouchHudSettings.HUD_SIZES, TouchHudSettings.encodeSizes(sizes))
                .putString(TouchHudSettings.HUD_BUTTON_OPACITIES, TouchHudSettings.encodeButtonOpacities(buttonOpacities))
                .putInt(TouchHudSettings.HUD_SCALE, scale)
                .putInt(TouchHudSettings.HUD_OPACITY, opacity)
                .putInt(TouchHudSettings.HUD_LAYOUT, layout)
                .putInt(TouchHudSettings.HUD_DOCK, dock)
                .putBoolean(TouchHudSettings.HUD_ENABLED, true)
                .putBoolean(TouchHudSettings.HUD_HIDDEN, false)
                .apply()
            // Reload through the shared schema after saving; do not keep stale
            // compact-only coordinates in the live overlay after import/edit.
            this.positions.clear()
            this.positions.putAll(TouchHudLayout.fromSettingsPositions(
                TouchHudSettings.parsePositions(preferences.getString(TouchHudSettings.HUD_POSITIONS, null)),
            ))
            controlsVisible = true
            editing = false
            setVisibility()
        }
    }

    private fun dragButton(id: String, view: View, event: MotionEvent): Boolean {
        if (!editing) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.tag = DragOrigin(event.rawX, event.rawY, view.x, view.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val origin = view.tag as? DragOrigin ?: return true
                view.x = (origin.viewX + event.rawX - origin.rawX)
                    .coerceIn(0f, (overlay.width - view.width).coerceAtLeast(0).toFloat())
                view.y = (origin.viewY + event.rawY - origin.rawY)
                    .coerceIn(0f, (overlay.height - view.height).coerceAtLeast(0).toFloat())
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val origin = view.tag as? DragOrigin
                view.tag = null
                if (event.actionMasked == MotionEvent.ACTION_UP && origin != null &&
                    abs(event.rawX - origin.rawX) < touchSlop &&
                    abs(event.rawY - origin.rawY) < touchSlop && id == "edit"
                ) {
                    releaseHeldInputs()
                    editing = false
                    setVisibility()
                }
                savePosition(id, view)
                return true
            }
        }
        return true
    }

    private fun savePosition(id: String, view: View) {
        val maxX = (overlay.width - view.width).coerceAtLeast(1)
        val maxY = (overlay.height - view.height).coerceAtLeast(1)
        positions[id] = TouchHudPoint(view.x / maxX, view.y / maxY).bounded()
        val portable = TouchHudSettings.parsePositions(
            preferences.getString(TouchHudSettings.HUD_POSITIONS, null),
        ).toMutableMap().apply {
            putAll(TouchHudLayout.toSettingsPositions(positions))
        }
        preferences.edit()
            .putString(TouchHudSettings.HUD_POSITIONS, TouchHudSettings.encodePositions(portable))
            .apply()
    }

    private fun positionButtons() {
        if (overlay.width <= 0 || overlay.height <= 0) return
        val layout = preferences.getInt(TouchHudSettings.HUD_LAYOUT, TouchHudSettings.LAYOUT_FREE)
            .coerceIn(TouchHudSettings.LAYOUT_FREE, TouchHudSettings.LAYOUT_YURI)
        val dock = preferences.getInt(TouchHudSettings.HUD_DOCK, 0).coerceIn(0, 2)
        val configured = TouchHudLayout.fromSettingsPositions(
            TouchHudSettings.parsePositions(preferences.getString(TouchHudSettings.HUD_POSITIONS, null)),
        )
        for ((id, button) in buttons) {
            if (editing && button.tag is DragOrigin) continue
            val maxX = (overlay.width - button.width).coerceAtLeast(0)
            val maxY = (overlay.height - button.height).coerceAtLeast(0)
            val settingsId = settingsId(id)
            val default = settingsId?.let { TouchHudSettings.defaultPosition(it, layout, dock) }
                ?: (0.04f to 0.82f)
            val point = positions[id]?.let { it.x to it.y } ?: configured[id]?.let { it.x to it.y } ?: default
            button.x = (point.first * maxX).coerceIn(0f, maxX.toFloat())
            button.y = (point.second * maxY).coerceIn(0f, maxY.toFloat())
        }
    }

    private fun loadPositions(): MutableMap<String, TouchHudPoint> {
        if (preferences.contains(TouchHudSettings.HUD_POSITIONS)) {
            return TouchHudLayout.fromSettingsPositions(
                TouchHudSettings.parsePositions(preferences.getString(TouchHudSettings.HUD_POSITIONS, null)),
            ).toMutableMap()
        }

        // Earlier Android builds stored only the compact HUD positions in a
        // separate preference file. Preserve those coordinates and publish
        // them into the same versioned settings used by launcher import/export.
        val legacy = activity.getSharedPreferences(LEGACY_PREFERENCES, 0)
            .getString(LEGACY_POSITIONS, null)
        val migrated = TouchHudLayout.toSettingsPositions(TouchHudLayout.parse(legacy))
        if (migrated.isNotEmpty()) {
            preferences.edit()
                .putString(TouchHudSettings.HUD_POSITIONS, TouchHudSettings.encodePositions(migrated))
                .apply()
        }
        return TouchHudLayout.fromSettingsPositions(migrated).toMutableMap()
    }

    private fun isEnabled(id: String, active: Set<String>): Boolean {
        val settingsId = settingsId(id) ?: return false
        if (settingsId !in active) return false
        val action = TouchHudSettings.buttons.firstOrNull { it.id == settingsId } ?: return false
        return action.groupNumber != null || action.id in TouchHudSettings.setSelectorButtons.map { it.id } ||
            preferences.getInt("key_${action.id}", action.defaultKeyCode) != KeyEvent.KEYCODE_UNKNOWN
    }

    private fun settingsId(id: String): String? = when (id) {
        "hud" -> TouchHudSettings.HUD_TOGGLE
        "edit" -> TouchHudSettings.HUD_EDIT
        "type" -> "TYPE"
        else -> when {
            id.startsWith("group-") -> id.removePrefix("group-").toIntOrNull()
                ?.takeIf { it in 1..10 }?.let { "C$it" }
            id == "set1" -> TouchHudSettings.SET_ONE
            id == "set2" -> TouchHudSettings.SET_TWO
            else -> id.takeIf { candidate -> TouchHudSettings.layoutButtons.any { it.id == candidate } }
        }
    }

    private fun overlayId(action: TouchHudSettings.Button): String = when {
        action.groupNumber != null -> "group-${action.groupNumber}"
        action.id == "TYPE" -> "type"
        action.id == TouchHudSettings.SET_ONE -> "set1"
        action.id == TouchHudSettings.SET_TWO -> "set2"
        else -> action.id
    }

    private fun buttonLabel(id: String, action: TouchHudSettings.Button): String = when {
        id == "type" -> "T"
        action.groupNumber != null -> "C${action.groupNumber}"
        action.label.length <= 7 -> action.label.uppercase(java.util.Locale.ROOT)
        else -> action.id
    }

    private fun buttonPixels(id: String): Int {
        val settingsId = settingsId(id)
        val perButtonScale = settingsId?.let { id ->
            TouchHudSettings.parseSizes(preferences.getString(TouchHudSettings.HUD_SIZES, null))[id]
        } ?: 100
        val globalScale = preferences.getInt(TouchHudSettings.HUD_SCALE, 100)
        return TouchHudSettings.buttonPixels(baseButtonSize, globalScale, perButtonScale)
    }

    private fun activate(action: TouchHudSettings.Button) {
        if (editing) return
        when (action.id) {
            TouchHudSettings.SET_ONE -> {
                preferences.edit().putInt(TouchHudSettings.HUD_ACTIVE_SET, 1).apply()
                setVisibility()
            }
            TouchHudSettings.SET_TWO -> {
                preferences.edit().putInt(TouchHudSettings.HUD_ACTIVE_SET, 2).apply()
                setVisibility()
            }
            "ROUTE" -> {
                val keyCode = preferences.getInt("key_${action.id}", action.defaultKeyCode)
                routeModeHeld = heldKeys.toggle(action.id, keyCode)
                buttons[action.id]?.text = if (routeModeHeld) "ROUTE ON" else "ROUTE"
            }
            else -> {
                val keyCode = preferences.getInt("key_${action.id}", action.defaultKeyCode)
                if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                    Toast.makeText(activity, "Assign a game key to ${action.label} in HUD settings first.", Toast.LENGTH_SHORT).show()
                } else if (action.groupNumber != null) {
                    sendGroupRecall(action.groupNumber)
                } else {
                    sendKeyStroke(keyCode)
                }
            }
        }
    }

    private fun sendGroupRecall(group: Int) {
        sendKeyStroke(if (group == 10) KeyEvent.KEYCODE_0 else KeyEvent.KEYCODE_1 + group - 1)
    }

    private fun sendGroupAssign(group: Int) {
        sendKeyStroke(
            if (group == 10) KeyEvent.KEYCODE_0 else KeyEvent.KEYCODE_1 + group - 1,
            KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
            controlChord = true,
        )
    }

    /** GameActivity forwards these key edges to the same native input queue as hardware keys. */
    private fun sendKeyStroke(keyCode: Int, metaState: Int = 0, controlChord: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (controlChord) dispatchKey(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_DOWN, now, now, 0)
        dispatchKey(keyCode, KeyEvent.ACTION_DOWN, now, now, metaState)
        dispatchKey(keyCode, KeyEvent.ACTION_UP, now, now + 1, metaState)
        if (controlChord) dispatchKey(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_UP, now, now + 2, 0)
    }

    private fun dispatchKey(keyCode: Int, action: Int, downTime: Long, eventTime: Long, metaState: Int) {
        val event = KeyEvent(
            downTime,
            eventTime,
            action,
            keyCode,
            0,
            metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
        )
        if (action == KeyEvent.ACTION_DOWN) activity.forwardHudKeyDown(keyCode, event)
        else activity.forwardHudKeyUp(keyCode, event)
    }

    private data class DragOrigin(val rawX: Float, val rawY: Float, val viewX: Float, val viewY: Float)

    private val touchSlop = (8 * density)

    private companion object {
        const val LEGACY_PREFERENCES = "touch_hud"
        const val LEGACY_POSITIONS = "button_positions_v1"
    }
}
