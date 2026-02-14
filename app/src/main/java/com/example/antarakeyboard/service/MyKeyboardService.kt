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

    // numeric config
    private val myDefaultNumericConfig: KeyboardConfig
        get() = defaultNumericLayout

    // layout tuning
    private val KEY_GAP_DP = 1        // minimalan razmak
    private val ROW_PAD_V_DP = 2      // mali vert padding reda
    private val OVERLAP_RATIO = 0.25f // hex overlap kao dio veličine tipke

    /* ───────── LIFECYCLE ───────── */
    override fun onCreateInputView(): View {
        rootView = layoutInflater.inflate(R.layout.keyboard_view, null)

        // ✅ Insets: base + inset (ne zbrajati!)
        val basePadL = rootView.paddingLeft
        val basePadT = rootView.paddingTop
        val basePadR = rootView.paddingRight
        val basePadB = rootView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(basePadL, basePadT, basePadR, basePadB + bottomInset)
            insets
        }

        keyboardContainer = rootView.findViewById<LinearLayout?>(R.id.keyboardContainer)
            ?: throw IllegalStateException("keyboard_view.xml nema LinearLayout s id=keyboardContainer")

        // load prefs
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

        // ako smo u numeric modu, ne želimo overwrite-at alphabetLayout
        val hasLetters = currentKeyboardConfig.rows.any { row ->
            row.keys.any { k -> k.label.length == 1 && k.label[0].isLetter() }
        }
        if (hasLetters) alphabetLayout = currentKeyboardConfig

        redrawKeyboard()
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onCreateExtractTextView(): View? = null

    /* ───────── EDGE KEY APPLY (SHIFT/BKSP) ───────── */
    private fun applyEdgeKeys(cfg: KeyboardConfig): KeyboardConfig {
        var shiftPos = EdgeKeyPrefs.getShift(this)
        var bkspPos = EdgeKeyPrefs.getBackspace(this)

        // safety: ne smiju biti na istom mjestu
        if (shiftPos.row == bkspPos.row && shiftPos.side == bkspPos.side) {
            shiftPos = EdgePos(3, EdgePos.Side.LEFT)
            bkspPos = EdgePos(3, EdgePos.Side.RIGHT)
            EdgeKeyPrefs.setShift(this, shiftPos)
            EdgeKeyPrefs.setBackspace(this, bkspPos)
        }

        // 1) makni sve kopije ⇧ i ⌫ iz svih redova
        cfg.rows.forEach { row ->
            row.keys.removeAll { it.label == "⇧" || it.label == "⌫" }
        }

        // mapiranje: row 1/3/5 -> index 0/2/4
        fun idxFromOddRow(n: Int): Int = when (n) {
            1 -> 0
            3 -> 2
            5 -> 4
            else -> 2
        }

        fun insert(label: String, pos: EdgePos) {
            val idx = idxFromOddRow(pos.row)
            if (idx !in cfg.rows.indices) return
            val row = cfg.rows[idx]

            val k = KeyConfig(label = label)

            if (pos.side == EdgePos.Side.LEFT) row.keys.add(0, k) else row.keys.add(k)
        }

        // 2) ubaci na ciljano mjesto
        insert("⇧", shiftPos)
        insert("⌫", bkspPos)

        return cfg
    }

    /* ───────── HELPERS ───────── */
    private fun isPortrait() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun keyHeight(): Int {
        val base = (resources.displayMetrics.heightPixels * if (isPortrait()) 0.07 else 0.05).toInt()
        val scale = KeyboardPrefs.getScale(this).coerceIn(0.7f, 1.7f)
        return (base * scale).toInt().coerceAtLeast(dp(36))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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

            val availW = availableKeyboardWidthPx()

            fun buildRow(keys: List<KeyConfig>, rowIndex: Int) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

                val sizing = computeRowSizing(keys.size, availW)

                row.setPadding(sizing.outerPadPx, dp(ROW_PAD_V_DP), sizing.outerPadPx, dp(ROW_PAD_V_DP))
                row.clipToPadding = false

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

                if (rowIndex > 0 && sizing.overlapPx > 0) {
                    lpRow.topMargin = -sizing.overlapPx
                }

                keyboardContainer.addView(row, lpRow)
            }

            // special left
            if (currentKeyboardConfig.specialLeft.isNotEmpty()) {
                buildRow(currentKeyboardConfig.specialLeft, 0)
            }

            // main rows
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

            isDrawing = false
        }
    }

    /* ───────── KEY VIEW ───────── */
    private fun createKey(keyConfig: KeyConfig): KeyView = KeyView(this).apply {
        val label = keyConfig.label

        text = label
        isAllCaps = false
        setTextColor(0xFFFFFFFF.toInt())
        textSize = if (isPortrait()) 18f else 16f
        gravity = android.view.Gravity.CENTER

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

                // swipe up: force uppercase slova
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
                // u numeric layoutu ne mičemo edge keys
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
