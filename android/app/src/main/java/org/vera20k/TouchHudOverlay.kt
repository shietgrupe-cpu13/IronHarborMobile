package org.vera20k

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.roundToInt

/** Touch-through game controls with an in-game editor; actions use the normal game-key path. */
internal class TouchHudOverlay(
    private val activity: VeraActivity,
    private val immersiveFullscreen: Boolean,
) {
    private val prefs = activity.getSharedPreferences(TouchHudSettings.PREFS, android.content.Context.MODE_PRIVATE)
    private var windowManager: WindowManager? = null
    private val controls = linkedMapOf<String, TextView>()
    private var editSurface: View? = null
    private var editing = false
    private var safeLeft = 0
    private var safeTop = 0
    private var safeRight = 0
    private var safeBottom = 0

    fun attach() {
        if (windowManager != null || !prefs.getBoolean(TouchHudSettings.HUD_ENABLED, true)) return
        windowManager = activity.getSystemService(WindowManager::class.java)
        updateWindowInsets(activity.window.decorView.rootWindowInsets)
        activity.window.decorView.post { populate() }
    }

    fun updateWindowInsets(insets: WindowInsets?) {
        var left = 0
        var top = 0
        var right = 0
        var bottom = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            insets?.displayCutout?.let { cutout ->
                left = maxOf(left, cutout.safeInsetLeft)
                top = maxOf(top, cutout.safeInsetTop)
                right = maxOf(right, cutout.safeInsetRight)
                bottom = maxOf(bottom, cutout.safeInsetBottom)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            insets?.systemGestureInsets?.let { gestures ->
                left = maxOf(left, gestures.left)
                right = maxOf(right, gestures.right)
                bottom = maxOf(bottom, gestures.bottom)
            }
        }
        if (!immersiveFullscreen && insets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                left = maxOf(left, bars.left)
                top = maxOf(top, bars.top)
                right = maxOf(right, bars.right)
                bottom = maxOf(bottom, bars.bottom)
            } else {
                left = maxOf(left, insets.systemWindowInsetLeft)
                top = maxOf(top, insets.systemWindowInsetTop)
                right = maxOf(right, insets.systemWindowInsetRight)
                bottom = maxOf(bottom, insets.systemWindowInsetBottom)
            }
        }
        if (left == safeLeft && top == safeTop && right == safeRight && bottom == safeBottom) return
        safeLeft = left
        safeTop = top
        safeRight = right
        safeBottom = bottom
        if (windowManager != null) populate()
    }

    fun detach() {
        val manager = windowManager
        if (manager != null) controls.values.forEach { runCatching { manager.removeView(it) } }
        if (manager != null) editSurface?.let { runCatching { manager.removeView(it) } }
        controls.clear()
        editSurface = null
        windowManager = null
        editing = false
    }

    private fun populate() {
        val manager = windowManager ?: return
        controls.values.forEach { runCatching { manager.removeView(it) } }
        editSurface?.let { runCatching { manager.removeView(it) } }
        editSurface = null
        controls.clear()
        if (editing) addEditSurface(manager)

        val visible = TouchHudSettings.parseVisible(
            prefs.getString(TouchHudSettings.HUD_VISIBLE, null),
            prefs.contains(TouchHudSettings.HUD_VISIBLE),
        )
        val positions = TouchHudSettings.parsePositions(prefs.getString(TouchHudSettings.HUD_POSITIONS, null))
        val sizes = TouchHudSettings.parseSizes(prefs.getString(TouchHudSettings.HUD_SIZES, null))
        val globalScale = TouchHudSettings.clampScale(prefs.getInt(TouchHudSettings.HUD_SCALE, 100)) / 100f
        val opacity = TouchHudSettings.clampOpacity(prefs.getInt(TouchHudSettings.HUD_OPACITY, 88)) / 100f
        val layout = prefs.getInt(TouchHudSettings.HUD_LAYOUT, TouchHudSettings.LAYOUT_FREE)
            .coerceIn(TouchHudSettings.LAYOUT_FREE, TouchHudSettings.LAYOUT_YURI)
        val dock = prefs.getInt(TouchHudSettings.HUD_DOCK, 0).coerceIn(0, 2)
        val controlSets = TouchHudSettings.parseControlSets(prefs.getString(TouchHudSettings.HUD_CONTROL_SETS, null))
        val activeSet = prefs.getInt(TouchHudSettings.HUD_ACTIVE_SET, 1).coerceIn(1, 2)
        val hidden = prefs.getBoolean(TouchHudSettings.HUD_HIDDEN, false)

        if (!hidden) {
            TouchHudSettings.visibleForSet(visible, controlSets, activeSet).forEach { id ->
                TouchHudSettings.buttons.firstOrNull { it.id == id }?.let { button ->
                    addControl(manager, button, positions, sizes, globalScale, opacity, layout, dock)
                }
            }
        }
        TouchHudSettings.utilityButtons.forEach { utility ->
            addControl(manager, utility, positions, sizes, globalScale, opacity, layout, dock, activeSet, hidden)
        }
    }

    private fun addControl(
        manager: WindowManager,
        action: TouchHudSettings.Button,
        positions: Map<String, Pair<Float, Float>>,
        sizes: Map<String, Int>,
        globalScale: Float,
        opacity: Float,
        layout: Int,
        dock: Int,
        activeSet: Int = 0,
        hidden: Boolean = false,
    ) {
        val itemScale = globalScale * (sizes[action.id] ?: 100) / 100f
        val isUtility = action.id == TouchHudSettings.HUD_EDIT ||
            action.id == TouchHudSettings.HUD_TOGGLE || action.id == TouchHudSettings.HUD_TEXT
        val label = when (action.id) {
            TouchHudSettings.HUD_EDIT -> if (editing) "DONE" else "EDIT"
            TouchHudSettings.HUD_TOGGLE -> if (hidden) "SHOW" else "HIDE"
            else -> action.label
        }
        val view = TextView(activity).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = (if (isUtility) 11f else 12f) * itemScale
            typeface = if (isUtility) Typeface.DEFAULT_BOLD else Typeface.create("sans-serif-condensed", Typeface.BOLD)
            minWidth = dp((58 * itemScale).roundToInt())
            minHeight = dp((42 * itemScale).roundToInt())
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = buttonBackground(opacity, editing && isUtility)
            contentDescription = when (action.id) {
                TouchHudSettings.HUD_EDIT -> if (editing) "Finish moving game controls" else "Edit floating game controls"
                TouchHudSettings.HUD_TOGGLE -> if (hidden) "Show floating game controls" else "Hide floating game controls"
                TouchHudSettings.HUD_TEXT -> "Open text entry for the focused game field"
                else -> if (action.groupNumber == null) "${action.label} command" else
                    "${action.label} control group; keyboard shortcut ${action.shortcutLabel}; tap to assign empty/select set, hold to add or clear"
            }
            isFocusable = false
            isClickable = true
            setOnClickListener {
                if (editing) return@setOnClickListener
                when (action.id) {
                    TouchHudSettings.HUD_EDIT -> {
                        editing = true
                        Toast.makeText(activity, "Drag controls to move them. Tap DONE to finish.", Toast.LENGTH_SHORT).show()
                        populate()
                    }
                    TouchHudSettings.HUD_TOGGLE -> {
                        prefs.edit().putBoolean(TouchHudSettings.HUD_HIDDEN, !hidden).apply()
                        populate()
                    }
                    TouchHudSettings.HUD_TEXT -> activity.showGameTextInput()
                    TouchHudSettings.SET_ONE -> selectSet(1)
                    TouchHudSettings.SET_TWO -> selectSet(2)
                    else -> if (action.groupNumber != null) {
                        activity.sendHudControlGroup(action.groupNumber, held = false)
                    } else {
                        sendKey(action.id, action.defaultKeyCode)
                    }
                }
            }
            setOnLongClickListener {
                if (!editing && action.groupNumber != null) {
                    activity.sendHudControlGroup(action.groupNumber, held = true)
                    true
                } else false
            }
            setOnTouchListener { touched, event ->
                if (!editing) return@setOnTouchListener false
                editTouch(action.id, touched, event)
            }
        }
        controls[action.id] = view
        val point = positions[action.id] ?: TouchHudSettings.defaultPosition(action.id, layout, dock)
        try {
            manager.addView(view, panelParams(point))
        } catch (error: WindowManager.BadTokenException) {
            controls.remove(action.id)
            android.util.Log.e("IronHarborHUD", "Could not attach floating control ${action.id}", error)
        }
    }

    private fun editTouch(id: String, view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.tag = HudDragState(
                    event.rawX,
                    event.rawY,
                    (view.layoutParams as? WindowManager.LayoutParams)?.x ?: 0,
                    (view.layoutParams as? WindowManager.LayoutParams)?.y ?: 0,
                )
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val state = view.tag as? HudDragState ?: return true
                val manager = windowManager ?: return true
                val params = view.layoutParams as? WindowManager.LayoutParams ?: return true
                val dx = event.rawX - state.rawX
                val dy = event.rawY - state.rawY
                if (!state.dragging && (abs(dx) >= dragSlop || abs(dy) >= dragSlop)) state.dragging = true
                if (state.dragging) {
                    val decor = activity.window.decorView
                    params.x = (state.x + dx.roundToInt()).coerceIn(
                        safeLeft,
                        (decor.width - safeRight - view.width).coerceAtLeast(safeLeft),
                    )
                    params.y = (state.y + dy.roundToInt()).coerceIn(
                        safeTop,
                        (decor.height - safeBottom - view.height).coerceAtLeast(safeTop),
                    )
                    runCatching { manager.updateViewLayout(view, params) }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val state = view.tag as? HudDragState
                if (state?.dragging == true) savePosition(id, view)
                else if (id == TouchHudSettings.HUD_EDIT) {
                    editing = false
                    Toast.makeText(activity, "Game control layout saved.", Toast.LENGTH_SHORT).show()
                    populate()
                }
                view.tag = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                view.tag = null
                return true
            }
        }
        return true
    }

    private fun savePosition(id: String, view: View) {
        val decor = activity.window.decorView
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val maxX = (decor.width - safeLeft - safeRight - view.width).coerceAtLeast(1)
        val maxY = (decor.height - safeTop - safeBottom - view.height).coerceAtLeast(1)
        val positions = TouchHudSettings.parsePositions(prefs.getString(TouchHudSettings.HUD_POSITIONS, null)).toMutableMap()
        positions[id] = ((params.x - safeLeft).toFloat() / maxX).coerceIn(0f, 1f) to
            ((params.y - safeTop).toFloat() / maxY).coerceIn(0f, 1f)
        prefs.edit().putString(TouchHudSettings.HUD_POSITIONS, TouchHudSettings.encodePositions(positions)).apply()
    }

    private fun selectSet(setNumber: Int) {
        prefs.edit().putInt(TouchHudSettings.HUD_ACTIVE_SET, setNumber).apply()
        populate()
    }

    /** A transparent full-screen layer keeps the live match visible as the edit canvas. */
    private fun addEditSurface(manager: WindowManager) {
        val banner = TextView(activity).apply {
            text = "HUD EDIT MODE    /    DRAG CONTROLS    /    TAP DONE TO FINISH"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = buttonBackground(0.96f, edit = true)
            contentDescription = "Move floating controls over the live game screen"
        }
        val surface = FrameLayout(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(View(activity).apply {
                setBackgroundColor(Color.argb(54, 4, 12, 10))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(banner, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                leftMargin = safeLeft + dp(88)
                rightMargin = safeRight + dp(88)
                topMargin = safeTop + dp(6)
            })
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            token = activity.window.decorView.windowToken
            gravity = Gravity.TOP or Gravity.START
            title = "Iron Harbor Mobile HUD editor canvas"
        }
        try {
            manager.addView(surface, params)
            editSurface = surface
        } catch (error: WindowManager.BadTokenException) {
            android.util.Log.e("IronHarborHUD", "Could not show in-game HUD editing canvas", error)
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
        x = safeLeft + (point.first * (
            activity.window.decorView.width - safeLeft - safeRight - dp(64)
        ).coerceAtLeast(0)).roundToInt()
        y = safeTop + (point.second * (
            activity.window.decorView.height - safeTop - safeBottom - dp(48)
        ).coerceAtLeast(0)).roundToInt()
        title = "Iron Harbor Mobile floating control"
    }

    private fun sendKey(id: String, default: Int) {
        val keyCode = prefs.getInt("key_$id", default)
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            android.util.Log.w("IronHarborHUD", "No game key is bound for $id; bind its existing engine key in the launcher")
            return
        }
        activity.sendHudKey(keyCode, true)
        activity.sendHudKey(keyCode, false)
    }

    private val dragSlop: Float get() = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).roundToInt()

    private fun buttonBackground(opacity: Float, edit: Boolean = false) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(Color.argb((opacity * 220).roundToInt().coerceIn(1, 255), 10, 22, 17))
        setStroke(dp(if (edit) 2 else 1), if (edit) Color.rgb(232, 182, 70) else Color.rgb(74, 190, 119))
    }

    private class HudDragState(
        val rawX: Float,
        val rawY: Float,
        val x: Int,
        val y: Int,
        var dragging: Boolean = false,
    )
}
