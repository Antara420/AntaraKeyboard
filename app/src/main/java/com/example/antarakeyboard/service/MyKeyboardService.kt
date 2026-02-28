package com.example.antarakeyboard.service

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.ui.BindLongPressDialog
import com.example.antarakeyboard.ui.KeyView
import com.example.antarakeyboard.ui.SpecialCharsDialog
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import com.example.antarakeyboard.ui.defaultNumericLayout
import kotlin.math.max
import kotlin.math.roundToInt
import com.example.antarakeyboard.service.input.KeyInputController
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot

class MyKeyboardService : InputMethodService() {
    /* ───────── STATE ───────── */
    private var isShifted = false
    private var isDrawing = false
    private var lastBottomInsetPx: Int = 0
    private var currentKeyboardConfig: KeyboardConfig = defaultKeyboardLayout
    private var alphabetLayout: KeyboardConfig? = null
    private var currentShape: KeyShape = KeyShape.HEX
    private val mainHandler = Handler(Looper.getMainLooper())
    private var previewPopup: PopupWindow? = null
    private lateinit var rootView: View
    private lateinit var keyboardContainer: LinearLayout
    private lateinit var overlayLayer: FrameLayout
    private val myDefaultNumericConfig: KeyboardConfig
        get() = defaultNumericLayout
    private val KEY_GAP_DP = 1
    private val OVERLAP_RATIO = 0.18f
    private var lastIsDark: Boolean? = null
    private var targetKeyboardHeightPx: Int = 0
    lateinit var inputController: KeyInputController
    /* ───────── LIFECYCLE ───────── */
    override fun onCreateInputView(): View {
        val isDark = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            .getBoolean("dark_mode", true)
        lastIsDark = isDark
        val themedCtx = android.view.ContextThemeWrapper(
            this,
            if (isDark) R.style.Theme_AntaraKeyboard else R.style.Theme_AntaraKeyboard_Light
        )
        rootView = layoutInflater.cloneInContext(themedCtx).inflate(R.layout.keyboard_view, null)
        overlayLayer = rootView.findViewById(R.id.keyboardRoot)
        keyboardContainer = rootView.findViewById(R.id.keyboardContainer)
        // Container neka bude WRAP_CONTENT i dolje
        keyboardContainer.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        inputController = KeyInputController(this)
        val basePadL = overlayLayer.paddingLeft
        val basePadT = overlayLayer.paddingTop
        val basePadR = overlayLayer.paddingRight
        val basePadB = overlayLayer.paddingBottom
        targetKeyboardHeightPx = computeTargetKeyboardHeight()
        overlayLayer.post {
            applyKeyboardHeight(targetKeyboardHeightPx + lastBottomInsetPx)
        }
        ViewCompat.setOnApplyWindowInsetsListener(overlayLayer) { v, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            lastBottomInsetPx = bottomInset
            v.setPadding(basePadL, basePadT, basePadR, basePadB + bottomInset)
            targetKeyboardHeightPx = computeTargetKeyboardHeight()
            applyKeyboardHeight(targetKeyboardHeightPx + bottomInset)
            insets
        }
        currentShape = KeyboardPrefs.getShape(this)
        currentKeyboardConfig = applyEdgeKeys(KeyboardPrefs.loadLayout(this))
        alphabetLayout = currentKeyboardConfig
        redrawKeyboard()
        return rootView
    }
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        setExtractViewShown(false)
        val isDarkNow = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            .getBoolean("dark_mode", true)
        if (lastIsDark != null && lastIsDark != isDarkNow) {
            lastIsDark = isDarkNow
            recreateInputView()
            return
        }
        lastIsDark = isDarkNow
        currentShape = KeyboardPrefs.getShape(this)
        currentKeyboardConfig = applyEdgeKeys(KeyboardPrefs.loadLayout(this))
        val hasLetters = currentKeyboardConfig.rows.any { row ->
            row.keys.any { k -> k.label.length == 1 && k.label[0].isLetter() }
        }
        if (hasLetters) alphabetLayout = currentKeyboardConfig
        targetKeyboardHeightPx = computeTargetKeyboardHeight()
        overlayLayer.post { applyKeyboardHeight(targetKeyboardHeightPx + lastBottomInsetPx) }
        redrawKeyboard()
    }
    private fun recreateInputView() {
        currentKeyboardConfig = applyEdgeKeys(KeyboardPrefs.loadLayout(this))
        redrawKeyboard()
    }
    override fun onEvaluateFullscreenMode() = false
    override fun onCreateExtractTextView(): View? = null
    /* ───────── EDGE KEYS: remove from layout (overlay only) ───────── */
    private fun applyEdgeKeys(cfg: KeyboardConfig): KeyboardConfig {

        val copy = cfg.copy(
            rows = cfg.rows.map { r -> r.copy(keys = r.keys.toMutableList()) }.toMutableList(),
            specialLeft = cfg.specialLeft.toMutableList(),
            specialRight = cfg.specialRight.toMutableList()
        )
        // 🔥 uzmi sve aktivne edge slotove (osim NONE)
        val activeEdgeLabels = EdgeSlotsStorage.load(this)
            .filter { it.type != EdgeActionType.NONE }
            .map { it.label }
            .filter { it.isNotBlank() }
            .toSet()
        if (activeEdgeLabels.isEmpty()) return copy
        // makni iz glavnih redova
        copy.rows.forEach { row ->
            row.keys.removeAll { it.label in activeEdgeLabels }
        }
        // makni iz specialLeft i specialRight
        copy.specialLeft.removeAll { it.label in activeEdgeLabels }
        copy.specialRight.removeAll { it.label in activeEdgeLabels }
        return copy
    }
    /* ───────── HELPERS ───────── */
    private fun isPortrait() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun computeTargetKeyboardHeight(): Int {
        val screenH = resources.displayMetrics.heightPixels
        val ratio = if (isPortrait()) 0.31f else 0.24f
        return (screenH * ratio).roundToInt().coerceAtLeast(dp(185))
    }
    private fun applyKeyboardHeight(h: Int) {
        val height = h.coerceAtLeast(dp(185))
        val lp = overlayLayer.layoutParams
        if (lp != null) {
            if (lp.height != height) {
                lp.height = height
                overlayLayer.layoutParams = lp
                overlayLayer.minimumHeight = height
                overlayLayer.requestLayout()
            }
        } else {
            overlayLayer.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
            overlayLayer.minimumHeight = height
            overlayLayer.requestLayout()
        }
    }
    private fun totalVisibleRows(): Int {
        var rows = currentKeyboardConfig.rows.size
        if (currentKeyboardConfig.specialLeft.isNotEmpty()) rows += 1
        if (currentKeyboardConfig.specialRight.isNotEmpty()) rows += 1
        return rows.coerceAtLeast(1)
    }
    private fun keyHeight(): Int {
        val scale = KeyboardPrefs.getScale(this).coerceIn(0.7f, 1.7f)
        val rows = totalVisibleRows()
        val containerH = (overlayLayer.height.takeIf { it > 0 }
            ?: (resources.displayMetrics.heightPixels * 0.33f).toInt())
        val usableH = (containerH - overlayLayer.paddingTop - overlayLayer.paddingBottom)
            .coerceAtLeast(dp(150))
        val denom = if (currentShape == KeyShape.HEX) {
            (rows - (rows - 1) * OVERLAP_RATIO).coerceAtLeast(1f)
        } else rows.toFloat()
        val usableForKeys = (usableH * 0.92f).toInt()
        val baseKeyH = (usableForKeys / denom).toInt()
        return ((baseKeyH * scale) * 0.98f).toInt().coerceIn(dp(36), dp(175))
    }
    private fun availableKeyboardWidthPx(): Int {
        val w = overlayLayer.width
        val base = if (w > 0) w else resources.displayMetrics.widthPixels
        return (base - overlayLayer.paddingLeft - overlayLayer.paddingRight).coerceAtLeast(dp(200))
    }
    /* ───────── PREVIEW (popup slovo) ───────── */
    private fun showKeyPreview(view: TextView) {
        hideKeyPreview()
        val tv = TextView(this).apply {
            text = view.text
            textSize = 28f
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }
        tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        previewPopup = PopupWindow(tv, tv.measuredWidth, tv.measuredHeight, false)
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        previewPopup?.showAtLocation(
            overlayLayer,
            Gravity.NO_GRAVITY,
            loc[0] + view.width / 2 - tv.measuredWidth / 2,
            loc[1] - tv.measuredHeight - dp(10)
        )
    }
    private fun hideKeyPreview() {
        previewPopup?.dismiss()
        previewPopup = null
    }
    /* ───────── INPUT API for Controller ───────── */
    fun showPreview(view: TextView) = showKeyPreview(view)
    fun hidePreview() = hideKeyPreview()
    fun toggleShift() {
        isShifted = !isShifted
        redrawKeyboard()
    }
    fun commitText(text: String) {
        val out =
            if (text.length == 1 && text[0].isLetter()) {
                if (isShifted) text.uppercase() else text.lowercase()
            } else text
        currentInputConnection?.commitText(out, 1)
    }
    /* ───────── LAYOUT MATH ───────── */
    private data class RowSizing(
        val keyW: Int,
        val keyH: Int,
        val gapPx: Int,
        val outerPadPx: Int,
        val overlapPx: Int,
        val triOverlapX: Int,
        val triOverlapY: Int
    )
    private fun gapPxForShape(): Int = dp(KEY_GAP_DP)
    private fun computeRowSizing(count: Int, availW: Int): RowSizing {
        val gap = gapPxForShape()
        val stdKeyW = ((availW - (7 - 1) * gap) / 7f).toInt().coerceAtLeast(dp(36))
        val keyW: Int
        val outer: Int
        if (count == 7) {
            keyW = ((availW - (count - 1) * gap) / count.toFloat()).toInt().coerceAtLeast(dp(36))
            outer = 0
        } else if (count == 6) {
            keyW = stdKeyW
            val used = count * keyW + (count - 1) * gap
            outer = ((availW - used) / 2).coerceAtLeast(0)
        } else {
            keyW = ((availW - (count - 1) * gap) / max(1, count).toFloat())
                .toInt()
                .coerceAtLeast(dp(36))
            outer = 0
        }
        val keyH = keyHeight()
        val hexOverlap = if (currentShape == KeyShape.HEX) (keyH * OVERLAP_RATIO).toInt() else 0
        val triOX = 0
        val triOY = 0
        val finalOuter = if (currentShape == KeyShape.TRIANGLE) 0 else outer
        return RowSizing(
            keyW = keyW,
            keyH = keyH,
            gapPx = gap,
            outerPadPx = finalOuter,
            overlapPx = hexOverlap,
            triOverlapX = triOX,
            triOverlapY = triOY
        )
    }
    /* ───────── OVERLAY CLEANUP ───────── */
    private fun clearEdgeSlots() {
        val toRemove = mutableListOf<View>()
        for (i in 0 until overlayLayer.childCount) {
            val v = overlayLayer.getChildAt(i)
            val tag = v.tag?.toString() ?: continue
            if (tag.startsWith("edge_slot_")) toRemove.add(v)
        }
        toRemove.forEach { overlayLayer.removeView(it) }
    }
    private fun drawEdgeSlots() {
        overlayLayer.post {
            clearEdgeSlots()
            val visualRows = 3
            val safeEdge = dp(2)
            val totalHeight = overlayLayer.height
            if (totalHeight <= 0) return@post
            val slotHeight = totalHeight / visualRows
            val slotWidth = (overlayLayer.width * 0.08f).toInt().coerceAtLeast(dp(18))
            for (i in 0 until visualRows) {
                val top = i * slotHeight
                fun addSlot(tag: String, x: Int) {
                    val v = View(this).apply {
                        this.tag = tag
                        setBackgroundResource(
                            if (tag.contains("left"))
                                R.drawable.hex_half_left
                            else
                                R.drawable.hex_half_right
                        )
                    }
                    val lp = FrameLayout.LayoutParams(slotWidth, slotHeight).apply {
                        gravity = Gravity.START
                        leftMargin = x
                        topMargin = top
                    }
                    overlayLayer.addView(v, lp)
                }
                addSlot("edge_slot_left_$i", safeEdge)
                addSlot(
                    "edge_slot_right_$i",
                    overlayLayer.width - slotWidth - safeEdge
                )
            }
        }
    }
    /* ───────── REDRAW ───────── */
    private fun redrawKeyboard() {
        if (!::keyboardContainer.isInitialized) return
        if (!::overlayLayer.isInitialized) return
        if (isDrawing) return
        isDrawing = true
        mainHandler.post {
            keyboardContainer.removeAllViews()
            var spaceIndex = 0
            fun buildRow(keys: List<KeyConfig>, containerRowIndex: Int) {
                val availW = availableKeyboardWidthPx()
                val sizing = computeRowSizing(keys.size, availW)
                val vPad = when (currentShape) {
                    KeyShape.TRIANGLE -> 0
                    KeyShape.HEX -> dp(1)
                    else -> dp(2)
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, vPad, 0, vPad)
                    clipToPadding = false
                }
                keys.forEachIndexed { i, key ->
                    val kv = createKey(key)
                    if (currentShape == KeyShape.TRIANGLE) {
                        kv.triangleFlipped = (i % 2 == 1)
                    }
                    if (kv.text.toString() == " ") {
                        val linked = KeyboardPrefs.isSpaceLinked(this)
                        val c1 = KeyboardPrefs.getSpace1Bg(this)
                        val c2 = if (linked) c1 else KeyboardPrefs.getSpace2Bg(this)
                        kv.customBgColor = if (spaceIndex == 0) c1 else c2
                        spaceIndex++
                    }
                    val lp = LinearLayout.LayoutParams(sizing.keyW, sizing.keyH)
                    if (i > 0) lp.leftMargin = sizing.gapPx
                    row.addView(kv, lp)
                }
                val lpRow = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                if (containerRowIndex > 0) {
                    lpRow.topMargin = when (currentShape) {
                        KeyShape.HEX -> -sizing.overlapPx
                        else -> 0
                    }
                }
                keyboardContainer.addView(row, lpRow)
            }
            if (currentKeyboardConfig.specialLeft.isNotEmpty()) {
                buildRow(currentKeyboardConfig.specialLeft, 0)
            }
            currentKeyboardConfig.rows.forEachIndexed { idx, rowConfig ->
                val rowIndex = idx + if (currentKeyboardConfig.specialLeft.isNotEmpty()) 1 else 0
                buildRow(rowConfig.keys, rowIndex)
            }
            if (currentKeyboardConfig.specialRight.isNotEmpty()) {
                val rowIndex = currentKeyboardConfig.rows.size +
                        if (currentKeyboardConfig.specialLeft.isNotEmpty()) 1 else 0
                buildRow(currentKeyboardConfig.specialRight, rowIndex)
            }
            // nakon što se row-evi layoutaju, nacrtaj slotove pa ikone (anchor na slot)
            keyboardContainer.post {
                drawEdgeSlots()
                overlayLayer.post { drawEdgeIcons() }
                isDrawing = false
            }
        }
    }

    private fun performEdgeAction(slot: EdgeSlot) {
        when (slot.type) {
            EdgeActionType.SHIFT -> toggleShift()
            EdgeActionType.BACKSPACE -> currentInputConnection?.deleteSurroundingText(1, 0)
            EdgeActionType.ENTER -> currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            )
            EdgeActionType.SPACE -> currentInputConnection?.commitText(" ", 1)
            EdgeActionType.CHAR -> slot.value?.let { currentInputConnection?.commitText(it, 1) }
            EdgeActionType.NONE -> Unit
        }
    }
    /* ✅ EDGE ICONS: anchor to OVERLAY slot tag */
    /* ✅ EDGE ICONS: anchor to OVERLAY slot tag */
    private fun drawEdgeIcons() {
        overlayLayer.post {
            // makni stare overlay ikone (slotove ne diramo)
            val toRemove = mutableListOf<View>()
            for (i in 0 until overlayLayer.childCount) {
                val v = overlayLayer.getChildAt(i)
                if (v.tag == "edge_icon") toRemove.add(v)
            }
            toRemove.forEach { overlayLayer.removeView(it) }

            val safeEdge = dp(2)
            val slots: List<EdgeSlot> = EdgeSlotsStorage.load(this)

            fun addIcon(slot: EdgeSlot) {
                if (slot.type == EdgeActionType.NONE) return

                // 0,1 -> row 0; 2,3 -> row 1; 4,5 -> row 2
                val visualIndex = slot.index / 2

                val slotTag = if (slot.side == EdgePos.Side.LEFT) {
                    "edge_slot_left_$visualIndex"
                } else {
                    "edge_slot_right_$visualIndex"
                }

                val slotView = overlayLayer.findViewWithTag<View>(slotTag) ?: return

                if (slotView.width <= 0 || slotView.height <= 0) {
                    slotView.post { addIcon(slot) }
                    return
                }

                val boxW = slotView.width
                val boxH = slotView.height
                if (boxW <= 0 || boxH <= 0) return

                val slotLoc = IntArray(2)
                val overlayLoc = IntArray(2)
                slotView.getLocationOnScreen(slotLoc)
                overlayLayer.getLocationOnScreen(overlayLoc)

                val rawLeft = slotLoc[0] - overlayLoc[0]
                val rawTop = slotLoc[1] - overlayLoc[1]

                val left = rawLeft
                    .coerceAtLeast(safeEdge)
                    .coerceAtMost(maxOf(safeEdge, overlayLayer.width - boxW - safeEdge))

                val top = rawTop
                    .coerceAtLeast(safeEdge)
                    .coerceAtMost(maxOf(safeEdge, overlayLayer.height - boxH - safeEdge))

                val box = FrameLayout(this).apply {
                    tag = "edge_icon"
                    layoutParams = FrameLayout.LayoutParams(boxW, boxH).apply {
                        gravity = Gravity.START
                        leftMargin = left
                        topMargin = top
                    }
                }

                val iconSize = (boxH * 0.46f).toInt().coerceIn(dp(16), dp(26))

                val icon = TextView(this).apply {
                    text = slot.label
                    textSize = if (isPortrait()) 16f else 14f
                    gravity = Gravity.CENTER
                    includeFontPadding = false

                    // SHIFT indikator
                    if (slot.type == EdgeActionType.SHIFT && isShifted) {
                        setTextColor(getColor(R.color.special_fill))
                        scaleX = 1.15f
                        scaleY = 1.15f
                    } else {
                        setTextColor(getColor(R.color.key_text))
                        scaleX = 1f
                        scaleY = 1f
                    }
                }

                val biasToEdge = when (slot.side) {
                    EdgePos.Side.LEFT -> -dp(10)
                    EdgePos.Side.RIGHT -> dp(10)
                }

                val iconLp = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER
                    leftMargin = biasToEdge
                }
                box.addView(icon, iconLp)

                box.setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            slotView.isPressed = true
                            icon.alpha = 0.55f
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            slotView.isPressed = false
                            icon.alpha = 1f
                            performEdgeAction(slot)
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            slotView.isPressed = false
                            icon.alpha = 1f
                            true
                        }
                        else -> false
                    }
                }

                overlayLayer.addView(box)
            }

            slots.forEach { addIcon(it) }
        }
    }
    /* ───────── KEY VIEW ───────── */
    private fun createKey(keyConfig: KeyConfig): KeyView = KeyView(this).apply {
        val label = keyConfig.label
        text = label
        isAllCaps = false
        shape = currentShape
        isSpecial = (label == "↵")
        setTextColor(getColor(R.color.key_text))
        textSize = if (isPortrait()) 18f else 16f
        gravity = Gravity.CENTER
        if (label == "↵") {
            customBgColor = KeyboardPrefs.getEnterBg(context)
            setTextColor(KeyboardPrefs.getEnterIcon(context))
        }
        setOnTouchListener { v, e ->
            inputController.handleTouch(v as TextView, e)
        }
        val nonBindable = setOf("⇧", "⌫", "↵", "123", "ABC", "abc", " ")
        setOnLongClickListener {
            if (label in nonBindable) return@setOnLongClickListener false
            val binds = keyConfig.longPressBindings
            if (binds.isEmpty()) {
                BindLongPressDialog(context, currentKeyboardConfig) { bind ->
                    if (!keyConfig.longPressBindings.contains(bind.charValue)) {
                        keyConfig.longPressBindings.add(bind.charValue)
                    }
                    KeyboardPrefs.saveLayout(context, currentKeyboardConfig)
                    Toast.makeText(context, "Bind: $label → ${bind.charValue}", Toast.LENGTH_SHORT).show()
                }.show()
            } else {
                SpecialCharsDialog(context, binds) { chosen ->
                    currentInputConnection?.commitText(chosen, 1)
                }.show()
            }
            true
        }
    }
    /* ───────── INNER CLASSES ───────── */
    class CharSelectorAdapter(
        private val items: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<CharSelectorAdapter.ViewHolder>() {
        private var selectedPos = RecyclerView.NO_POSITION
        inner class ViewHolder(val button: android.widget.Button) : RecyclerView.ViewHolder(button) {
            fun bind(char: String, isSelected: Boolean) {
                button.text = char
                button.setBackgroundColor(if (isSelected) 0xFFFFCC80.toInt() else 0x00000000)
                button.setOnClickListener {
                    val old = selectedPos
                    val newPos = bindingAdapterPosition
                    if (newPos == RecyclerView.NO_POSITION) return@setOnClickListener
                    selectedPos = newPos
                    notifyItemChanged(old)
                    notifyItemChanged(selectedPos)
                    onItemClick(char)
                }
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val btn = android.widget.Button(parent.context).apply {
                isAllCaps = false
                textSize = 18f
                setPadding(16, 16, 16, 16)
            }
            return ViewHolder(btn)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], position == selectedPos)
        }
        override fun getItemCount() = items.size
    }
}