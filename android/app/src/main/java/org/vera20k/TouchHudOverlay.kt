package org.vera20k

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/** Movable RA2YRPORT-inspired buttons in small, touch-through application panels. */
internal class TouchHudOverlay(private val activity: VeraActivity) {
    private val prefs = activity.getSharedPreferences(TouchHudSettings.PREFS, android.content.Context.MODE_PRIVATE)
    private var windowManager: WindowManager? = null
    private val buttons = linkedMapOf<String, TextView>()
    private var editButton: TextView? = null
    private var foldButton: TextView? = null
    private var editMode = false

    fun attach() {
        if (windowManager != null || !prefs.getBoolean(TouchHudSettings.HUD_ENABLED, true)) return
        val manager = activity.getSystemService(WindowManager::class.java)
        windowManager = manager
        activity.window.decorView.post { populate() }
    }

    fun detach() {
        val manager = windowManager
        if (manager != null) {
            buttons.values.forEach { runCatching { manager.removeView(it) } }
            editButton?.let { runCatching { manager.removeView(it) } }
            foldButton?.let { runCatching { manager.removeView(it) } }
        }
        buttons.clear()
        editButton = null
        foldButton = null
        windowManager = null
    }

    private fun populate() {
        val manager = windowManager ?: return
        buttons.values.forEach { runCatching { manager.removeView(it) } }
        editButton?.let { runCatching { manager.removeView(it) } }
        foldButton?.let { runCatching { manager.removeView(it) } }
        buttons.clear()
        editButton = null
        foldButton = null

        val visible = TouchHudSettings.parseVisible(
            prefs.getString(TouchHudSettings.HUD_VISIBLE, null),
            prefs.contains(TouchHudSettings.HUD_VISIBLE),
        )
        val positions = TouchHudSettings.parsePositions(prefs.getString(TouchHudSettings.HUD_POSITIONS, null)).toMutableMap()
        val sizes = TouchHudSettings.parseSizes(prefs.getString(TouchHudSettings.HUD_SIZES, null)).toMutableMap()
        val globalScale = TouchHudSettings.clampScale(prefs.getInt(TouchHudSettings.HUD_SCALE, 100)) / 100f
        val opacity = TouchHudSettings.clampOpacity(prefs.getInt(TouchHudSettings.HUD_OPACITY, 88)) / 100f
        val layout = prefs.getInt(TouchHudSettings.HUD_LAYOUT, TouchHudSettings.LAYOUT_FREE)
            .coerceIn(TouchHudSettings.LAYOUT_FREE, TouchHudSettings.LAYOUT_YURI)
        val collapsed = prefs.getBoolean(TouchHudSettings.HUD_COLLAPSED, false) && !editMode

        TouchHudSettings.buttons.filter { it.id in visible && !collapsed }.forEach { action ->
            val itemScale = globalScale * (sizes[action.id] ?: 100) / 100f
            val view = TextView(activity).apply {
                text = action.label
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 12f * itemScale
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                minWidth = dp((58 * itemScale).roundToInt())
                minHeight = dp((42 * itemScale).roundToInt())
                setPadding(dp(5), dp(3), dp(5), dp(3))
                background = buttonBackground(opacity)
                contentDescription = if (action.groupNumber == null) "${action.label} command" else "${action.label} select control group; long press to assign"
                isFocusable = false
                isClickable = true
                setOnClickListener {
                    if (editMode) {
                        val next = when (sizes[action.id] ?: 100) { 70 -> 100; 100 -> 130; else -> 70 }
                        sizes[action.id] = next
                        saveSizes(sizes)
                        resize(this, globalScale * next / 100f)
                        announceForAccessibility("${action.label} size $next percent")
                    } else {
                        sendKey(action.id, action.defaultKeyCode)
                    }
                }
                setOnLongClickListener {
                    if (!editMode && action.groupNumber != null) {
                        sendChord(action.defaultKeyCode, create = true)
                        true
                    } else false
                }
                setOnTouchListener(dragListener(this, action.id, positions))
            }
            buttons[action.id] = view
            val point = positions[action.id] ?: TouchHudSettings.defaultPosition(action.id, layout)
            addPanel(manager, view, point)
        }

        val editor = TextView(activity).apply {
            text = if (editMode) "SAVE" else "EDIT HUD"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = buttonBackground(0.94f, edit = true)
            contentDescription = if (editMode) "Save floating control layout" else "Edit floating control layout"
            setOnClickListener {
                editMode = !editMode
                if (!editMode) savePositions(positionsForViews())
                populate()
            }
        }
        editButton = editor
        addPanel(manager, editor, 0.82f to 0.06f)

        val fold = TextView(activity).apply {
            text = if (collapsed) "SHOW HUD" else "HIDE HUD"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = buttonBackground(0.94f)
            contentDescription = if (collapsed) "Show floating controls" else "Hide floating controls"
            setOnClickListener {
                prefs.edit().putBoolean(TouchHudSettings.HUD_COLLAPSED, !collapsed).apply()
                populate()
            }
        }
        foldButton = fold
        addPanel(manager, fold, 0.82f to 0.15f)
    }

    private fun addPanel(manager: WindowManager, view: View, point: Pair<Float, Float>) {
        val params = panelParams(point)
        try {
            manager.addView(view, params)
        } catch (error: WindowManager.BadTokenException) {
            android.util.Log.e("IronHarborHUD", "Could not attach floating control", error)
        }
    }

    private fun panelParams(point: Pair<Float, Float>) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        token = activity.window.decorView.windowToken
        gravity = Gravity.TOP or Gravity.START
        x = (point.first * (activity.window.decorView.width - dp(64)).coerceAtLeast(0)).roundToInt()
        y = (point.second * (activity.window.decorView.height - dp(48)).coerceAtLeast(0)).roundToInt()
        title = "Iron Harbor Mobile floating control"
    }

    private fun dragListener(
        view: TextView,
        id: String,
        positions: MutableMap<String, Pair<Float, Float>>,
    ) = View.OnTouchListener { _, event ->
        if (!editMode) return@OnTouchListener false
        val manager = windowManager ?: return@OnTouchListener true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val params = view.layoutParams as WindowManager.LayoutParams
                view.tag = floatArrayOf(event.rawX, event.rawY, params.x.toFloat(), params.y.toFloat(), 0f)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val state = view.tag as? FloatArray ?: return@OnTouchListener true
                val dx = event.rawX - state[0]
                val dy = event.rawY - state[1]
                if (abs(dx) + abs(dy) > dp(5)) state[4] = 1f
                if (state[4] != 0f) {
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x = (state[2] + dx).roundToInt().coerceIn(0, (activity.window.decorView.width - view.width).coerceAtLeast(0))
                    params.y = (state[3] + dy).roundToInt().coerceIn(0, (activity.window.decorView.height - view.height).coerceAtLeast(0))
                    manager.updateViewLayout(view, params)
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val state = view.tag as? FloatArray
                if (state != null && state[4] != 0f) {
                    positions[id] = normalizedPosition(view)
                    savePositions(positions)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    view.performClick()
                }
                view.tag = null
                true
            }
            else -> true
        }
    }

    private fun normalizedPosition(view: View): Pair<Float, Float> {
        val params = view.layoutParams as WindowManager.LayoutParams
        val maxX = (activity.window.decorView.width - view.width).coerceAtLeast(1)
        val maxY = (activity.window.decorView.height - view.height).coerceAtLeast(1)
        return (params.x.toFloat() / maxX).coerceIn(0f, 1f) to (params.y.toFloat() / maxY).coerceIn(0f, 1f)
    }

    private fun positionsForViews(): MutableMap<String, Pair<Float, Float>> =
        TouchHudSettings.parsePositions(prefs.getString(TouchHudSettings.HUD_POSITIONS, null))
            .toMutableMap()
            .apply { buttons.forEach { (id, view) -> put(id, normalizedPosition(view)) } }

    private fun savePositions(positions: Map<String, Pair<Float, Float>>) {
        prefs.edit().putString(TouchHudSettings.HUD_POSITIONS, TouchHudSettings.encodePositions(positions)).apply()
    }

    private fun saveSizes(sizes: Map<String, Int>) {
        prefs.edit().putString(TouchHudSettings.HUD_SIZES, TouchHudSettings.encodeSizes(sizes)).apply()
    }

    private fun sendKey(id: String, default: Int) {
        val keyCode = prefs.getInt("key_$id", default)
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            android.util.Log.w("IronHarborHUD", "No game key is bound for $id; bind its existing engine key in the launcher")
            return
        }
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun sendChord(keyCode: Int, create: Boolean) {
        if (!create) return
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT))
    }

    private fun resize(view: TextView, scale: Float) {
        view.textSize = 12f * scale
        view.minWidth = dp((58 * scale).roundToInt())
        view.minHeight = dp((42 * scale).roundToInt())
        windowManager?.updateViewLayout(view, view.layoutParams)
    }

    private fun buttonBackground(opacity: Float, edit: Boolean = false) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(Color.argb((opacity * 220).roundToInt().coerceIn(1, 255), 10, 22, 17))
        setStroke(dp(1), if (edit) Color.rgb(191, 148, 58) else Color.rgb(74, 190, 119))
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).roundToInt()
}
