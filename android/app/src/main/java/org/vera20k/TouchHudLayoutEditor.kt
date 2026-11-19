package org.vera20k

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Full-screen launcher preview for arranging floating game controls before launch. */
internal class TouchHudLayoutEditor(private val activity: Activity) {
    fun show(
        visible: MutableSet<String>,
        positions: MutableMap<String, Pair<Float, Float>>,
        sizes: MutableMap<String, Int>,
        controlSets: Map<String, Int>,
        layout: Int,
        dock: Int,
        scale: Int,
        opacity: Int,
        onApply: () -> Unit,
    ) {
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 18, 21))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val heading = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val instruction = label("DRAG BUTTONS TO POSITION · TAP TO SELECT", 14, true).apply {
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        heading.addView(instruction, LinearLayout.LayoutParams(0, dp(42), 1f))
        val previewSet = Spinner(activity).apply {
            adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Preview all", "Preview set 1", "Preview set 2"),
            )
            setSelection(0)
            contentDescription = "Preview control set"
        }
        heading.addView(previewSet, LinearLayout.LayoutParams(dp(172), dp(48)))
        root.addView(heading)

        val selectedLabel = label("Select a control", 13)
        val preview = TouchHudLayoutPreview(
            activity, visible, positions, sizes, controlSets, layout, dock, scale, opacity,
        ) { selected ->
            selectedLabel.text = selected?.let { "${it.label}  ·  ${sizes[it.id] ?: 100}%" }
                ?: "No control selected"
        }
        previewSet.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long,
            ) {
                preview.previewSet = position
                preview.invalidate()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))

        val sizeRow = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        sizeRow.addView(controlButton("SIZE −") { preview.resizeSelected(-10) }, weighted())
        sizeRow.addView(selectedLabel.also { it.gravity = Gravity.CENTER }, weighted())
        sizeRow.addView(controlButton("SIZE +") { preview.resizeSelected(10) }, weighted())
        root.addView(sizeRow, LinearLayout.LayoutParams(-1, dp(48)))

        val actionRow = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val hide = controlButton("HIDE SELECTED") { preview.hideSelected() }
        val restore = controlButton("RESTORE CONTROLS") { showRestoreDialog(visible, preview) }
        val reset = controlButton("RESET POSITIONS") { preview.resetPositions() }
        actionRow.addView(hide, weighted())
        actionRow.addView(restore, weighted())
        actionRow.addView(reset, weighted())
        root.addView(actionRow, LinearLayout.LayoutParams(-1, dp(48)))

        val commitRow = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        commitRow.addView(controlButton("CANCEL") { dialog.dismiss() }, weighted())
        commitRow.addView(controlButton("SAVE LAYOUT", primary = true) {
            onApply()
            dialog.dismiss()
        }, weighted())
        root.addView(commitRow, LinearLayout.LayoutParams(-1, dp(50)))

        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
                decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
            }
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun showRestoreDialog(visible: MutableSet<String>, preview: TouchHudLayoutPreview) {
        val actions = TouchHudSettings.buttons
        val checked = BooleanArray(actions.size) { actions[it].id in visible }
        AlertDialog.Builder(activity)
            .setTitle("Show or hide HUD buttons")
            .setMultiChoiceItems(actions.map { it.label }.toTypedArray(), checked) { _, index, isChecked ->
                if (isChecked) visible.add(actions[index].id) else visible.remove(actions[index].id)
                preview.selectFirstVisible()
                preview.invalidate()
            }
            .setPositiveButton("DONE", null)
            .setNegativeButton("CANCEL") { _, _ ->
                // Revert only this checklist; the layout preview remains a separate draft.
                actions.forEachIndexed { index, button ->
                    if (checked[index]) visible.add(button.id) else visible.remove(button.id)
                }
                preview.selectFirstVisible()
                preview.invalidate()
            }
            .show()
    }

    private fun controlButton(text: String, primary: Boolean = false, action: () -> Unit) =
        Button(activity).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 12f
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(if (primary) Color.rgb(42, 107, 77) else Color.rgb(28, 43, 44))
                setStroke(dp(1), if (primary) Color.rgb(163, 220, 130) else Color.rgb(71, 119, 100))
            }
            setOnClickListener { action() }
        }

    private fun label(text: String, size: Int, bold: Boolean = false) = TextView(activity).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(Color.rgb(221, 232, 224))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun weighted() = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
        marginStart = dp(3)
        marginEnd = dp(3)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
}

private class TouchHudLayoutPreview(
    private val activity: Activity,
    private val visible: MutableSet<String>,
    private val positions: MutableMap<String, Pair<Float, Float>>,
    private val sizes: MutableMap<String, Int>,
    private val controlSets: Map<String, Int>,
    private val layout: Int,
    private val dock: Int,
    private val globalScale: Int,
    private val opacity: Int,
    private val onSelected: (TouchHudSettings.Button?) -> Unit,
) : View(activity) {
    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(9, 23, 26) }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(38, 107, 173, 141)
        strokeWidth = density
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val hitBoxes = linkedMapOf<String, RectF>()
    var previewSet: Int = 0
    private var selectedId: String? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var dragging = false

    init {
        isFocusable = true
        contentDescription = "Draggable preview of the floating game controls"
        selectFirstVisible()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val step = dp(48).coerceAtLeast(1).toFloat()
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }
        hitBoxes.clear()
        val active = TouchHudSettings.visibleForSet(visible, controlSets, previewSet)
        val items = TouchHudSettings.buttons.filter { it.id in active } + TouchHudSettings.setSelectorButtons
        val maxButtonWidth = (width * 0.30f).coerceAtLeast(dp(64).toFloat())
        items.forEach { button ->
            val itemScale = globalScale / 100f * (sizes[button.id] ?: 100) / 100f
            textPaint.textSize = dp(12).toFloat() * itemScale
            val rectWidth = max(dp(58).toFloat() * itemScale, textPaint.measureText(button.label) + dp(18) * itemScale)
                .coerceAtMost(maxButtonWidth)
            val rectHeight = dp(42).toFloat() * itemScale
            val stored = positions[button.id]
                ?: TouchHudSettings.defaultPosition(button.id, layout, dock)
            val left = (stored.first.coerceIn(0f, 1f) * (width - rectWidth).coerceAtLeast(0f))
                .coerceIn(0f, (width - rectWidth).coerceAtLeast(0f))
            val top = (stored.second.coerceIn(0f, 1f) * (height - rectHeight).coerceAtLeast(0f))
                .coerceIn(0f, (height - rectHeight).coerceAtLeast(0f))
            val bounds = RectF(left, top, left + rectWidth, top + rectHeight)
            hitBoxes[button.id] = bounds
            val selected = button.id == selectedId
            val assignment = if (button.id in TouchHudSettings.setSelectorButtons.map { it.id }) {
                if ((button.id == TouchHudSettings.SET_ONE && previewSet == 1) ||
                    (button.id == TouchHudSettings.SET_TWO && previewSet == 2)) 1 else 0
            } else controlSets[button.id] ?: 0
            val alpha = (opacity.coerceIn(30, 100) * 2.2f).roundToInt().coerceIn(1, 255)
            buttonPaint.color = Color.argb(alpha, 10, 26, 24)
            canvas.drawRoundRect(bounds, dp(7).toFloat(), dp(7).toFloat(), buttonPaint)
            outlinePaint.color = when {
                selected -> Color.rgb(226, 185, 91)
                assignment == 1 -> Color.rgb(102, 177, 220)
                assignment == 2 -> Color.rgb(195, 130, 204)
                else -> Color.rgb(89, 195, 131)
            }
            canvas.drawRoundRect(bounds, dp(7).toFloat(), dp(7).toFloat(), outlinePaint)
            val baseline = bounds.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(button.label, bounds.centerX(), baseline, textPaint)
        }
        if (items.isEmpty()) {
            textPaint.textSize = dp(17).toFloat()
            canvas.drawText("No buttons in this preview set", width / 2f, height / 2f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                selectedId = hitBoxes.entries.lastOrNull { it.value.contains(event.x, event.y) }?.key
                val bounds = selectedId?.let(hitBoxes::get)
                if (bounds == null) {
                    onSelected(null)
                    invalidate()
                    return true
                }
                dragOffsetX = event.x - bounds.left
                dragOffsetY = event.y - bounds.top
                dragging = true
                onSelected(TouchHudSettings.layoutButtons.firstOrNull { it.id == selectedId })
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val id = selectedId ?: return true
                val bounds = hitBoxes[id] ?: return true
                val left = (event.x - dragOffsetX).coerceIn(0f, (width - bounds.width()).coerceAtLeast(0f))
                val top = (event.y - dragOffsetY).coerceIn(0f, (height - bounds.height()).coerceAtLeast(0f))
                positions[id] = (
                    left / (width - bounds.width()).coerceAtLeast(1f)
                    ) to (top / (height - bounds.height()).coerceAtLeast(1f))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun resizeSelected(delta: Int) {
        val id = selectedId ?: return
        sizes[id] = TouchHudSettings.clampButtonSize((sizes[id] ?: 100) + delta)
        onSelected(TouchHudSettings.layoutButtons.firstOrNull { it.id == id })
        invalidate()
    }

    fun hideSelected() {
        val id = selectedId ?: return
        if (id in TouchHudSettings.setSelectorButtons.map { it.id }) return
        visible.remove(id)
        selectFirstVisible()
        invalidate()
    }

    fun resetPositions() {
        positions.clear()
        invalidate()
    }

    fun selectFirstVisible() {
        val first = TouchHudSettings.buttons.firstOrNull { it.id in visible }
            ?: TouchHudSettings.setSelectorButtons.first()
        selectedId = first.id
        onSelected(first)
    }

    private fun dp(value: Int) = (value * density).roundToInt()
}
