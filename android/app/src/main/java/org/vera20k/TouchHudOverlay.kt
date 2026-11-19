package org.vera20k

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.roundToInt

/** Launcher-configured RA2YRPORT-inspired buttons in touch-through panels. */
internal class TouchHudOverlay(private val activity: VeraActivity) {
    private val prefs = activity.getSharedPreferences(TouchHudSettings.PREFS, android.content.Context.MODE_PRIVATE)
    private var windowManager: WindowManager? = null
    private val buttons = linkedMapOf<String, TextView>()
    private val setButtons = mutableListOf<TextView>()

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
            setButtons.forEach { runCatching { manager.removeView(it) } }
        }
        buttons.clear()
        setButtons.clear()
        windowManager = null
    }

    private fun populate() {
        val manager = windowManager ?: return
        buttons.values.forEach { runCatching { manager.removeView(it) } }
        setButtons.forEach { runCatching { manager.removeView(it) } }
        buttons.clear()
        setButtons.clear()

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
        val dock = prefs.getInt(TouchHudSettings.HUD_DOCK, 0).coerceIn(0, 2)
        val controlSets = TouchHudSettings.parseControlSets(prefs.getString(TouchHudSettings.HUD_CONTROL_SETS, null))
        val activeSet = prefs.getInt(TouchHudSettings.HUD_ACTIVE_SET, 1).coerceIn(1, 2)
        val visibleNow = TouchHudSettings.visibleForSet(visible, controlSets, activeSet)

        TouchHudSettings.buttons.filter { it.id in visibleNow }.forEach { action ->
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
                    sendKey(action.id, action.defaultKeyCode)
                }
                setOnLongClickListener {
                    if (action.groupNumber != null) {
                        sendChord(action.defaultKeyCode, create = true)
                        true
                    } else false
                }
            }
            buttons[action.id] = view
            val point = positions[action.id] ?: TouchHudSettings.defaultPosition(action.id, layout, dock)
            addPanel(manager, view, point)
        }

        TouchHudSettings.setSelectorButtons.forEach { selector ->
            val setNumber = if (selector.id == TouchHudSettings.SET_ONE) 1 else 2
            val itemScale = globalScale * (sizes[selector.id] ?: 100) / 100f
            val setButton = TextView(activity).apply {
                text = selector.label
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 11f * itemScale
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), dp(8), dp(10), dp(8))
                minWidth = dp((58 * itemScale).roundToInt())
                minHeight = dp((42 * itemScale).roundToInt())
                background = buttonBackground(0.94f, edit = activeSet == setNumber)
                contentDescription = "Show control set $setNumber"
                setOnClickListener {
                    prefs.edit().putInt(TouchHudSettings.HUD_ACTIVE_SET, setNumber).apply()
                    populate()
                }
            }
            setButtons += setButton
            val point = positions[selector.id]
                ?: TouchHudSettings.defaultPosition(selector.id, layout, dock)
            addPanel(manager, setButton, point)
        }
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

    private fun buttonBackground(opacity: Float, edit: Boolean = false) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(Color.argb((opacity * 220).roundToInt().coerceIn(1, 255), 10, 22, 17))
        setStroke(dp(1), if (edit) Color.rgb(191, 148, 58) else Color.rgb(74, 190, 119))
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).roundToInt()
}
