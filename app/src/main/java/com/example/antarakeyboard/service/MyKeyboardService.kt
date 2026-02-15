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
import com.example.antarakeyboard.data.EdgeKeyPrefs
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

class MyKeyboardService : InputMethodService() {

    /* ───────── STATE ───────── */
    private var isShifted = false
    private var isDrawing = false

    private var currentKeyboardConfig: KeyboardConfig = defaultKeyboardLayout
    private var alphabetLayout: KeyboardConfig? = null
    private var currentShape: KeyShape = KeyShape.HEX

    private val mainHandler = Handler(Looper.getMainLooper())

    // swipe helpers
    private var swipeStartX = 0f
    private var swipeStartY = 0f

    // preview popup
    private var previewPopup: PopupWindow? = null

    private lateinit var rootView: View
    private lateinit var keyboardContainer: LinearLayout
    private lateinit var overlayLayer: FrameLayout

    // numeric config
    private val myDefaultNumericConfig: KeyboardConfig
        get() = defaultNumericLayout

    // layout tuning
    private val KEY_GAP_DP = 1
    private val ROW_PAD_V_DP = 2
    private val OVERLAP_RATIO = 0.25f

    /* ───────── LIFECYCLE ───────── */
    override fun onCreateInputView(): View {
        rootView = layoutInflater.inflate(R.layout.keyboard_view, null)

        overlayLayer = rootView.findViewById<FrameLayout?>(R.id.keyboardRoot)
            ?: throw IllegalStateException("keyboard_view.xml mora imati FrameLayout id=keyboardRoot")

        keyboardContainer = rootView.findViewById<LinearLayout?>(R.id.keyboardContainer)
            ?: throw IllegalStateException("keyboard_view.xml nema LinearLayout id=keyboardContainer")

        // Insets: base + inset
        val basePadL = rootView.paddingLeft
        val basePadT = rootView.paddingTop
        val basePadR = rootView.paddingRight
        val basePadB = rootView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(basePadL, basePadT, basePadR, basePadB + bottomInset)
            insets
        }

        currentShape = KeyboardPrefs.getShape(this)
        currentKeyboardConfig = KeyboardPrefs.loadLayout(this)
        currentKeyboardConfig = applyEdgeKeys(currentKeyboardConfig)
        alphabetLayout = currentKeyboardConfig

        redrawKeyboard()
        return rootView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        setExtractViewShown(false)

        currentShape = KeyboardPrefs.getShape(this)
        currentKeyboardConfig = KeyboardPrefs.loadLayout(this)
        currentKeyboardConfig = applyEdgeKeys(currentKeyboardConfig)

        val hasLetters = currentKeyboardConfig.rows.any { row ->
            row.keys.any { k -> k.label.length == 1 && k.label[0].isLetter() }
        }
        if (hasLetters) alphabetLayout = currentKeyboardConfig

        redrawKeyboard()
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onCreateExtractTextView(): View? = null

    /* ───────── EDGE KEYS: remove from layout (overlay only) ───────── */
    private fun applyEdgeKeys(cfg: KeyboardConfig): KeyboardConfig {
        var shiftPos = EdgeKeyPrefs.getShift(this)
        var bkspPos = EdgeKeyPrefs.getBackspace(this)

        // safety: ne smiju biti isti slot
        if (shiftPos.row == bkspPos.row && shiftPos.side == bkspPos.side) {
            shiftPos = EdgePos(3, EdgePos.Side.LEFT)
            bkspPos = EdgePos(3, EdgePos.Side.RIGHT)
            EdgeKeyPrefs.setShift(this, shiftPos)
            EdgeKeyPrefs.setBackspace(this, bkspPos)
        }

        // Makni ⇧ i ⌫ iz svih redova
        cfg.rows.forEach { row ->
            row.keys.removeAll { it.label == "⇧" || it.label == "⌫" }
        }
        return cfg
    }

    /* ───────── HELPERS ───────── */
    private fun isPortrait() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun keyHeight(): Int {
        val manual = KeyboardPrefs.getKeyHeightPx(this)
        if (manual > 0) return manual.coerceIn(dp(28), dp(140))

        // fallback: stari auto
        val base = (resources.displayMetrics.heightPixels * if (isPortrait()) 0.07 else 0.05).toInt()
        val scale = KeyboardPrefs.getScale(this).coerceIn(0.7f, 1.7f)
        return (base * scale).toInt().coerceAtLeast(dp(36))
    }

    private fun availableKeyboardWidthPx(): Int {
        val w = rootView.width
        val base = if (w > 0) w else resources.displayMetrics.widthPixels
        return (base - rootView.paddingLeft - rootView.paddingRight).coerceAtLeast(dp(200))
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
            rootView,
            Gravity.NO_GRAVITY,
            loc[0] + view.width / 2 - tv.measuredWidth / 2,
            loc[1] - tv.measuredHeight - dp(10)
        )
    }

    private fun hideKeyPreview() {
        previewPopup?.dismiss()
        previewPopup = null
    }

    /* ───────── LAYOUT MATH (6 vs 7) ───────── */
    private data class RowSizing(
        val keySizePx: Int,
        val gapPx: Int,
        val outerPadPx: Int,
        val overlapPx: Int
    )

    private fun computeRowSizing(count: Int, availW: Int): RowSizing {
        val gap = dp(KEY_GAP_DP)
        val stdKey = ((availW - (7 - 1) * gap) / 7f).toInt().coerceAtLeast(dp(36))

        val keySize: Int
        val outer: Int

        if (count == 7) {
            keySize = ((availW - (count - 1) * gap) / count.toFloat()).toInt().coerceAtLeast(dp(36))
            outer = 0
        } else if (count == 6) {
            keySize = stdKey
            val used = count * keySize + (count - 1) * gap
            outer = ((availW - used) / 2).coerceAtLeast(0)
        } else {
            keySize = ((availW - (count - 1) * gap) / max(1, count).toFloat()).toInt().coerceAtLeast(dp(36))
            outer = 0
        }

        val limitedKey = minOf(keySize, keyHeight())
        val overlap = if (currentShape == KeyShape.HEX) (limitedKey * OVERLAP_RATIO).toInt() else 0

        return RowSizing(
            keySizePx = limitedKey,
            gapPx = gap,
            outerPadPx = outer,
            overlapPx = overlap
        )
    }

    /* ───────── REDRAW ───────── */
    private fun redrawKeyboard() {
        if (isDrawing) return
        isDrawing = true

        mainHandler.post {
            keyboardContainer.removeAllViews()
            clearEdgeOverlays()

            val availW = availableKeyboardWidthPx()

            fun buildRow(keys: List<KeyConfig>, rowIndex: Int) {
                val sizing = computeRowSizing(keys.size, availW)

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(sizing.outerPadPx, dp(ROW_PAD_V_DP), sizing.outerPadPx, dp(ROW_PAD_V_DP))
                    clipToPadding = false
                }

                keys.forEachIndexed { i, key ->
                    val kv = createKey(key)

                    if (currentShape == KeyShape.TRIANGLE) {
                        kv.triangleFlipped = (i % 2 == 1)
                    }

                    val lp = LinearLayout.LayoutParams(sizing.keySizePx, sizing.keySizePx)
                    if (i > 0) lp.leftMargin = sizing.gapPx
                    row.addView(kv, lp)
                }

                val lpRow = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                if (rowIndex > 0 && sizing.overlapPx > 0) lpRow.topMargin = -sizing.overlapPx

                keyboardContainer.addView(row, lpRow)
            }

            // special left
            if (currentKeyboardConfig.specialLeft.isNotEmpty()) {
                buildRow(currentKeyboardConfig.specialLeft, 0)
            }

            // rows
            currentKeyboardConfig.rows.forEachIndexed { idx, rowConfig ->
                val rowIndex = idx + if (currentKeyboardConfig.specialLeft.isNotEmpty()) 1 else 0
                buildRow(rowConfig.keys, rowIndex)
            }

            // special right
            if (currentKeyboardConfig.specialRight.isNotEmpty()) {
                val rowIndex = currentKeyboardConfig.rows.size +
                        if (currentKeyboardConfig.specialLeft.isNotEmpty()) 1 else 0
                buildRow(currentKeyboardConfig.specialRight, rowIndex)
            }

            // overlay edge icons AFTER layout settles
            drawEdgeIcons()

            isDrawing = false
        }
    }

    private fun clearEdgeOverlays() {
        // makni sve views s tagom edge_icon
        val toRemove = mutableListOf<View>()
        for (i in 0 until overlayLayer.childCount) {
            val v = overlayLayer.getChildAt(i)
            if (v.tag == "edge_icon") toRemove.add(v)
        }
        toRemove.forEach { overlayLayer.removeView(it) }
    }

    /**
     * SHIFT/BKSP overlay:
     * - nema utjecaja na layout tipki
     * - ikona 40% keyHeight
     * - tap-area veća (frame)
     * - pozicija = centar u paddingu reda (gap), clamp da se ne reže
     */
    private fun drawEdgeIcons() {
        overlayLayer.post {
            clearEdgeOverlays()

            val shift = EdgeKeyPrefs.getShift(this)
            val bksp = EdgeKeyPrefs.getBackspace(this)

            val iconBoxH = keyHeight()
            val iconSize = (iconBoxH * 0.4f).toInt().coerceAtLeast(dp(14))
            val desiredTapW = dp(44)
            val safeEdge = dp(2)

            fun rowIndexFromOddRow(n: Int): Int = when (n) {
                1 -> 0
                3 -> 2
                5 -> 4
                else -> -1
            }

            fun addIcon(pos: EdgePos, symbol: String, tapText: String) {
                val oddIdx = rowIndexFromOddRow(pos.row)
                if (oddIdx == -1) return
                if (oddIdx >= currentKeyboardConfig.rows.size) return

                val offset = if (currentKeyboardConfig.specialLeft.isNotEmpty()) 1 else 0
                val childIndex = oddIdx + offset
                if (childIndex !in 0 until keyboardContainer.childCount) return

                val rowView = keyboardContainer.getChildAt(childIndex)

                // paddingLeft/paddingRight su "gap" gdje želimo ikonu
                val gapL = rowView.paddingLeft
                val gapR = rowView.paddingRight

                val tapW = when (pos.side) {
                    EdgePos.Side.LEFT -> minOf(desiredTapW, gapL).coerceAtLeast(iconSize)
                    EdgePos.Side.RIGHT -> minOf(desiredTapW, gapR).coerceAtLeast(iconSize)
                }

                val box = FrameLayout(this).apply {
                    tag = "edge_icon"
                    layoutParams = FrameLayout.LayoutParams(tapW, iconBoxH)
                    clipChildren = false
                    clipToPadding = false
                }

                val icon = TextView(this).apply {
                    text = symbol
                    textSize = if (isPortrait()) 14f else 12f
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
                }
                box.addView(icon)

                // vertikalno: centar u rowView
                val top = rowView.top + ((rowView.height - iconBoxH) / 2).coerceAtLeast(0)

                // horizontalno: centar unutar lijevog/desnog gap-a
                val leftInParent = if (pos.side == EdgePos.Side.LEFT) {
                    rowView.left + ((gapL - tapW) / 2).coerceAtLeast(0)
                } else {
                    (rowView.left + rowView.width - gapR) + ((gapR - tapW) / 2).coerceAtLeast(0)
                }

                val clampedLeft = leftInParent
                    .coerceAtLeast(safeEdge)
                    .coerceAtMost(maxOf(safeEdge, overlayLayer.width - tapW - safeEdge))

                val lp = box.layoutParams as FrameLayout.LayoutParams
                lp.gravity = Gravity.START
                lp.topMargin = top
                lp.leftMargin = clampedLeft
                box.layoutParams = lp

                box.setOnTouchListener { _, e ->
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            icon.alpha = 0.5f
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            icon.alpha = 1f
                            if (e.action == MotionEvent.ACTION_UP) handleClick(tapText)
                            true
                        }
                        else -> false
                    }
                }

                overlayLayer.addView(box)
            }

            addIcon(shift, "⇧", "⇧")
            addIcon(bksp, "⌫", "⌫")
        }
    }

    /* ───────── KEY VIEW ───────── */
    private fun createKey(keyConfig: KeyConfig): KeyView = KeyView(this).apply {
        val label = keyConfig.label

        text = label
        isAllCaps = false
        setTextColor(0xFFFFFFFF.toInt())
        textSize = if (isPortrait()) 18f else 16f
        gravity = Gravity.CENTER

        shape = currentShape
        isSpecial = (label == "↵" || label == "⌫")

        setOnTouchListener { v, e -> handleTouch(v as TextView, e) }

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

    /* ───────── TOUCH / CLICK ───────── */
    private fun handleTouch(view: TextView, e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = e.x
                swipeStartY = e.y

                val t = view.text.toString()
                if (t !in listOf("⇧", " ", "123", "↵", "⌫", "ABC", "abc")) showKeyPreview(view)

                view.isPressed = true
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                hideKeyPreview()

                val dy = e.y - swipeStartY
                val text = view.text.toString()

                if (dy < -60 && text.length == 1 && text[0].isLetter()) {
                    currentInputConnection?.commitText(text.uppercase(), 1)
                    return true
                }

                handleClick(text)
                return true
            }
        }
        return false
    }

    private fun handleClick(text: String) {
        when (text) {
            "123" -> {
                alphabetLayout = currentKeyboardConfig
                currentKeyboardConfig = myDefaultNumericConfig
                currentKeyboardConfig = applyEdgeKeys(currentKeyboardConfig)
                redrawKeyboard()
            }

            "ABC", "abc" -> {
                currentKeyboardConfig = alphabetLayout ?: KeyboardPrefs.loadLayout(this)
                currentKeyboardConfig = applyEdgeKeys(currentKeyboardConfig)
                redrawKeyboard()
            }

            "⇧" -> isShifted = !isShifted
            "⌫" -> currentInputConnection?.deleteSurroundingText(1, 0)
            "↵" -> currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            " " -> currentInputConnection?.commitText(" ", 1)

            else -> {
                val out =
                    if (text.length == 1 && text[0].isLetter()) {
                        if (isShifted) text.uppercase() else text.lowercase()
                    } else text
                currentInputConnection?.commitText(out, 1)
            }
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
