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
import kotlin.math.roundToInt

class MyKeyboardService : InputMethodService() {

    /* ───────── STATE ───────── */
    private var isShifted = false
    private var isDrawing = false

    // nav bar / gesture inset (da dignemo edge icons)
    private var lastBottomInsetPx: Int = 0

    private var currentKeyboardConfig: KeyboardConfig = defaultKeyboardLayout
    private var alphabetLayout: KeyboardConfig? = null
    private var currentShape: KeyShape = KeyShape.HEX

    // Tag na row view: jel taj row rezervirao slot lijevo/desno
    private data class RowEdgeSlots(val left: Boolean, val right: Boolean)

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
    private val OVERLAP_RATIO = 0.18f

    // target visina tipkovnice (bez insets-a), da keyHeight zna koliki prostor ima
    private var targetKeyboardHeightPx: Int = 0

    /* ───────── LIFECYCLE ───────── */
    override fun onCreateInputView(): View {
        rootView = layoutInflater.inflate(R.layout.keyboard_view, null)

        overlayLayer = rootView.findViewById<FrameLayout>(R.id.keyboardRoot)
        keyboardContainer = rootView.findViewById<LinearLayout>(R.id.keyboardContainer)

        // osiguraj da container puni visinu root-a
        keyboardContainer.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )

        // base padding (bez insets-a)
        val basePadL = overlayLayer.paddingLeft
        val basePadT = overlayLayer.paddingTop
        val basePadR = overlayLayer.paddingRight
        val basePadB = overlayLayer.paddingBottom

        // postavi željenu visinu tipkovnice (bez insets-a)
        targetKeyboardHeightPx = computeTargetKeyboardHeight()

        // IME ponekad ignorira layoutParams odmah -> stavi u post{}
        overlayLayer.post {
            applyKeyboardHeight(targetKeyboardHeightPx + lastBottomInsetPx)
        }

        // Insets: dodaj bottom inset na padding i na visinu (da ne "pojede" prostor)
        ViewCompat.setOnApplyWindowInsetsListener(overlayLayer) { v, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            lastBottomInsetPx = bottomInset

            // update padding
            v.setPadding(basePadL, basePadT, basePadR, basePadB + bottomInset)

            // update height (target + inset)
            targetKeyboardHeightPx = computeTargetKeyboardHeight()
            applyKeyboardHeight(targetKeyboardHeightPx + bottomInset)

            insets
        }

        currentShape = KeyboardPrefs.getShape(this)
        currentKeyboardConfig = applyEdgeKeys(KeyboardPrefs.loadLayout(this))
        alphabetLayout = currentKeyboardConfig

        redrawKeyboard()
        overlayLayer.post {
            redrawKeyboard()
        }
        return rootView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        setExtractViewShown(false)

        currentShape = KeyboardPrefs.getShape(this)
        currentKeyboardConfig = applyEdgeKeys(KeyboardPrefs.loadLayout(this))

        val hasLetters = currentKeyboardConfig.rows.any { row ->
            row.keys.any { k -> k.label.length == 1 && k.label[0].isLetter() }
        }
        if (hasLetters) alphabetLayout = currentKeyboardConfig

        // refresh target height (može se promijeniti zbog rotacije)
        targetKeyboardHeightPx = computeTargetKeyboardHeight()
        overlayLayer.post { applyKeyboardHeight(targetKeyboardHeightPx + lastBottomInsetPx) }

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

        // Izbaci ⇧ i ⌫ iz redova (da se ne crtaju kao normalne tipke)
        copy.rows.forEach { row ->
            row.keys.removeAll { it.label == "⇧" || it.label == "⌫" }
        }
        return copy
    }

    /* ───────── HELPERS ───────── */
    private fun isPortrait() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun computeTargetKeyboardHeight(): Int {
        val screenH = resources.displayMetrics.heightPixels
        val ratio = if (isPortrait()) 0.33f else 0.25f   // ✅ manje nego 0.40/0.30
        return (screenH * ratio).roundToInt().coerceAtLeast(dp(220))
    }

    private fun applyKeyboardHeight(h: Int) {
        val height = h.coerceAtLeast(dp(220))
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
        //onCreateInputViewal manual = KeyboardPrefs.getKeyHeightPx(this)
        //if (manual > 0) return manual.coerceIn(dp(28), dp(160))
        val scale = KeyboardPrefs.getScale(this).coerceIn(0.7f, 1.7f)
        val rows = totalVisibleRows()

        // koliko realno prostora ima tipkovnica (bez paddinga)
        val containerH = (overlayLayer.height.takeIf { it > 0 }
            ?: (resources.displayMetrics.heightPixels * 0.38f).toInt())

        val usableH = (containerH - overlayLayer.paddingTop - overlayLayer.paddingBottom)
            .coerceAtLeast(dp(160))

        // zbog HEX overlap-a ukupna visina nije rows*keyH nego malo manje
        val denom = if (currentShape == KeyShape.HEX) {
            (rows - (rows - 1) * OVERLAP_RATIO).coerceAtLeast(1f)
        } else {
            rows.toFloat()
        }

        // malo rezerve za vPad i margine
        val usableForKeys = (usableH * 0.92f).toInt()

        val baseKeyH = (usableForKeys / denom).toInt()
        return ((baseKeyH * scale) * 0.90f).toInt().coerceIn(dp(34), dp(170))    }

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

    /* ───────── LAYOUT MATH ───────── */
    private data class RowSizing(
        val keyW: Int,
        val keyH: Int,
        val gapPx: Int,
        val outerPadPx: Int,
        val overlapPx: Int,   // hex vertical overlap
        val triOverlapX: Int, // triangle horizontal overlap
        val triOverlapY: Int  // triangle vertical overlap
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

    /* ───────── REDRAW ───────── */
    private fun redrawKeyboard() {
        if (isDrawing) return
        isDrawing = true

        mainHandler.post {
            keyboardContainer.removeAllViews()
            clearEdgeOverlays()

            val availW = availableKeyboardWidthPx()

            var spaceIndex = 0

            fun buildRow(keys: List<KeyConfig>, containerRowIndex: Int) {

                val availW = availableKeyboardWidthPx()

                val offset = if (currentKeyboardConfig.specialLeft.isNotEmpty()) 1 else 0
                val cfgRowIdx = containerRowIndex - offset

                // samo cfg rows 0/2/4 su "odd" (1/3/5)
                val oddRowNumber = when (cfgRowIdx) {
                    0 -> 1
                    2 -> 3
                    4 -> 5
                    else -> -1
                }
                val isOddRow = (oddRowNumber == 1 || oddRowNumber == 3 || oddRowNumber == 5)

                // prvo sizing iz realnog availW (NEMA više slotL/slotR!)
                val sizing = computeRowSizing(keys.size, availW)

                // half-hex dimenzije (tweak po oku)
                val halfW = sizing.keyW
                val halfH = sizing.keyH

                val vPad = when (currentShape) {
                    KeyShape.TRIANGLE -> 0
                    KeyShape.HEX -> dp(1)        // ✅ fiksno malo, ne 5% visine

                    else -> (sizing.keyH * 0.05f).toInt().coerceIn(dp(2), dp(10))
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, vPad, 0, vPad)
                    clipToPadding = false

                    // bitno: da drawEdgeIcons kasnije zna koji je to row
                    tag = RowEdgeSlots(left = isOddRow, right = isOddRow)
                }

                // LEFT HALF (samo na redovima 1/3/5)
                if (isOddRow) {
                    val leftHalf = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(halfW, halfH)
                        setBackgroundResource(R.drawable.hex_half_left)   // ✅ LEFT drawable
                        tag = "edge_left_slot"
                    }
                    row.addView(leftHalf)
                }

                // normalne tipke
                keys.forEachIndexed { i, key ->
                    val kv = createKey(key)

                    // TRI flip (ako koristiš TRI)
                    if (currentShape == KeyShape.TRIANGLE) {
                        kv.triangleFlipped = (i % 2 == 1)
                    }

                    // SPACE colors
                    if (kv.text.toString() == " ") {
                        val linked = KeyboardPrefs.isSpaceLinked(this)
                        val c1 = KeyboardPrefs.getSpace1Bg(this)
                        val c2 = if (linked) c1 else KeyboardPrefs.getSpace2Bg(this)
                        kv.customBgColor = if (spaceIndex == 0) c1 else c2
                        spaceIndex++
                    }

                    val lp = LinearLayout.LayoutParams(sizing.keyW, sizing.keyH)
                    // ako postoji leftHalf, i prva tipka treba margin
                    if (i > 0 || isOddRow) lp.leftMargin = sizing.gapPx
                    row.addView(kv, lp)
                }

                // RIGHT HALF (samo na redovima 1/3/5)
                if (isOddRow) {
                    val rightHalf = View(this).apply {
                        val lp = LinearLayout.LayoutParams(halfW, halfH)
                        lp.leftMargin = sizing.gapPx
                        layoutParams = lp
                        setBackgroundResource(R.drawable.hex_half_right)  // ✅ RIGHT drawable
                        tag = "edge_right_slot"
                    }
                    row.addView(rightHalf)
                }

                val lpRow = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                if (containerRowIndex > 0) {
                    lpRow.topMargin = when (currentShape) {
                        KeyShape.HEX -> -sizing.overlapPx   // ✅ bez +dp(2)
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

            drawEdgeIcons()
            isDrawing = false
        }
    }

    private fun clearEdgeOverlays() {
        val toRemove = mutableListOf<View>()
        for (i in 0 until overlayLayer.childCount) {
            val v = overlayLayer.getChildAt(i)
            if (v.tag == "edge_icon") toRemove.add(v)
        }
        toRemove.forEach { overlayLayer.removeView(it) }
    }

    private fun drawEdgeIcons() {
        overlayLayer.post {
            clearEdgeOverlays()

            val shift = EdgeKeyPrefs.getShift(this)
            val bksp = EdgeKeyPrefs.getBackspace(this)

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

                val offset = if (currentKeyboardConfig.specialLeft.isNotEmpty()) 1 else 0
                val childIndex = oddIdx + offset
                if (childIndex !in 0 until keyboardContainer.childCount) return

                val rowView = keyboardContainer.getChildAt(childIndex)

                // nađi half-slot view
                val slotTag = if (pos.side == EdgePos.Side.LEFT) "edge_left_slot" else "edge_right_slot"
                val slotView = rowView.findViewWithTag<View>(slotTag) ?: return

                // slot se mora imati dimenzije
                if (slotView.width <= 0 || slotView.height <= 0) return

                val boxW = slotView.width
                val boxH = slotView.height

                // touch zona = cijeli slot
                val box = FrameLayout(this).apply {
                    tag = "edge_icon"
                    clipChildren = false
                    clipToPadding = false
                }

                // ikona malo veća, ali unutar slota
                val iconSize = (boxH * 0.46f).toInt().coerceIn(dp(16), dp(26))

                val icon = TextView(this).apply {
                    text = symbol
                    textSize = if (isPortrait()) 16f else 14f
                    setTextColor(getColor(R.color.key_text))
                    gravity = Gravity.CENTER
                }

                // bias: u half-hexu vizualni centar nije u sredini pravokutnika
                val bias = (boxW * 0.24f).toInt().coerceAtLeast(dp(7))
                val biasSigned = if (pos.side == EdgePos.Side.LEFT) +bias else -bias

                val iconLp = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER
                    leftMargin = biasSigned
                }
                box.addView(icon, iconLp)

                // screen -> overlay local coords
                val slotLoc = IntArray(2)
                val overlayLoc = IntArray(2)
                slotView.getLocationOnScreen(slotLoc)
                overlayLayer.getLocationOnScreen(overlayLoc)

                val rawLeft = slotLoc[0] - overlayLoc[0]
                val rawTop = slotLoc[1] - overlayLoc[1]

                // FrameLayout padding se dodaje child poziciji, zato ga oduzmi iz margina
                val left = (rawLeft - overlayLayer.paddingLeft)
                    .coerceAtLeast(safeEdge)
                    .coerceAtMost(maxOf(safeEdge, overlayLayer.width - boxW - safeEdge))

                val top = (rawTop - overlayLayer.paddingTop)
                    .coerceAtLeast(safeEdge)
                    .coerceAtMost(maxOf(safeEdge, overlayLayer.height - boxH - safeEdge))

                val lp = FrameLayout.LayoutParams(boxW, boxH).apply {
                    gravity = Gravity.START
                    leftMargin = left
                    topMargin = top
                }
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

        shape = currentShape
        isSpecial = (label == "↵")

        setTextColor(0xFFFFFFFF.toInt())
        textSize = if (isPortrait()) 18f else 16f
        gravity = Gravity.CENTER

        if (label == "↵") {
            customBgColor = KeyboardPrefs.getEnterBg(context)
            setTextColor(KeyboardPrefs.getEnterIcon(context))
        }

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
                currentKeyboardConfig = applyEdgeKeys(myDefaultNumericConfig)
                redrawKeyboard()
            }

            "ABC", "abc" -> {
                currentKeyboardConfig = applyEdgeKeys(alphabetLayout ?: KeyboardPrefs.loadLayout(this))
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