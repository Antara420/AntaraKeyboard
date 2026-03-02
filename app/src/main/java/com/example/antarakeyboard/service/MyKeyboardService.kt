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
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.GridLayout



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
    private var lpRects: List<android.graphics.Rect> = emptyList()
    // --- long press popup state ---
    private var longPressPopup: PopupWindow? = null
    private var lpChars: List<String> = emptyList()
    private var lpSelectedIndex: Int = 0
    private var lpPreviewTv: TextView? = null
    private var lpGrid: GridLayout? = null


    /* ───────── LIFECYCLE ───────── */
    override fun onCreateInputView(): View {
        val isDark = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            .getBoolean("dark_mode", true)

        val themedCtx = android.view.ContextThemeWrapper(
            this,
            if (isDark) R.style.Theme_AntaraKeyboard else R.style.Theme_AntaraKeyboard_Light
        )
        lastIsDark = isDark
        // 1) inflate
        rootView = layoutInflater.cloneInContext(themedCtx).inflate(R.layout.keyboard_view, null)

        // 2) findViewById ODMAH
        overlayLayer = rootView.findViewById(R.id.keyboardRoot)
        keyboardContainer = rootView.findViewById(R.id.keyboardContainer)

        overlayLayer.clipChildren = false
        overlayLayer.clipToPadding = false
        // 3) sad smiješ dirat overlay/keyboard

        keyboardContainer.clipChildren = false
        keyboardContainer.clipToPadding = false

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

        ViewCompat.setOnApplyWindowInsetsListener(overlayLayer) { v, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            lastBottomInsetPx = bottomInset
            v.setPadding(basePadL, basePadT, basePadR, basePadB + bottomInset)


            insets
        }
        targetKeyboardHeightPx = computeTargetKeyboardHeight()
        overlayLayer.post { applyKeyboardHeight(targetKeyboardHeightPx + lastBottomInsetPx) }

        currentShape = KeyboardPrefs.getShape(this)
        currentKeyboardConfig = applyEdgeKeys(KeyboardPrefs.loadLayout(this))
        alphabetLayout = currentKeyboardConfig

        overlayLayer.post { redrawKeyboard() }
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
        overlayLayer.post { redrawKeyboard() }
    }
    private fun syncOverlayHeightToContent() {
        if (!::overlayLayer.isInitialized || !::keyboardContainer.isInitialized) return
        val contentH = keyboardContainer.height
        if (contentH <= 0) return

        // koliko realno treba da stane sadržaj
        val desired = contentH + overlayLayer.paddingTop + overlayLayer.paddingBottom

        // tvoja "normalna" ciljna visina (ratio) + inset
        val target = (targetKeyboardHeightPx + lastBottomInsetPx).coerceAtLeast(dp(230))

        // ✅ clamp: ne dopusti da bude veće od targeta
        val newH = desired.coerceIn(dp(230), target)

        val lp = overlayLayer.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, newH
        )

        if (lp.height != newH) {
            lp.height = newH
            overlayLayer.layoutParams = lp
            overlayLayer.minimumHeight = newH
            overlayLayer.requestLayout()
        }
    }
    private fun recreateInputView() {
        currentKeyboardConfig = applyEdgeKeys(KeyboardPrefs.loadLayout(this))
        redrawKeyboard()
    }
    override fun onEvaluateFullscreenMode() = false
    override fun onCreateExtractTextView(): View? = null
    /* ───────── EDGE KEYS: remove from layout (overlay only) ───────── */
    // ✅ KORAK 8: sigurniji applyEdgeKeys (ne briše “normalne” tipke po labelu)
    private fun applyEdgeKeys(cfg: KeyboardConfig): KeyboardConfig {
        val copy = cfg.copy(
            rows = cfg.rows.map { r -> r.copy(keys = r.keys.toMutableList()) }.toMutableList(),
            specialLeft = cfg.specialLeft.toMutableList(),
            specialRight = cfg.specialRight.toMutableList()
        )

        val slots = EdgeSlotsStorage.load(this).filter { it.type != EdgeActionType.NONE }
        if (slots.isEmpty()) return copy

        // Koje tipke MORAJU nestat iz normalnog layouta kad su na edgeu
        val removeLabels = buildSet<String> {
            slots.forEach { s ->
                when (s.type) {
                    EdgeActionType.SHIFT -> add("⇧")
                    EdgeActionType.BACKSPACE -> add("⌫")
                    EdgeActionType.ENTER -> add("↵")
                    EdgeActionType.SPACE -> add(" ")
                    EdgeActionType.CHAR -> {
                        // za char edge: makni i label i value ako ih koristiš
                        if (s.label.isNotBlank()) add(s.label)
                        val v = s.value
                        if (!v.isNullOrBlank()) add(v)
                    }
                    EdgeActionType.NONE -> Unit
                }
            }
        }

        fun strip(list: MutableList<KeyConfig>) {
            list.removeAll { it.label in removeLabels }
        }

        copy.rows.forEach { strip(it.keys) }
        strip(copy.specialLeft)
        strip(copy.specialRight)

        return copy
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        hideLongPressPopup()
    }


    /* ───────── HELPERS ───────── */
    private fun isPortrait() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun computeTargetKeyboardHeight(): Int {
        val screenH = resources.displayMetrics.heightPixels
        val ratio = if (isPortrait()) 0.36f else 0.28f
        return (screenH * ratio).roundToInt().coerceAtLeast(dp(230))
    }

    private fun applyKeyboardHeight(h: Int) {
        val height = h.coerceAtLeast(dp(230))
        val lp = overlayLayer.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        )

        if (lp.height != height) {
            lp.height = height
            overlayLayer.layoutParams = lp
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

        val containerH = (targetKeyboardHeightPx + lastBottomInsetPx).takeIf { it > 0 }
            ?: computeTargetKeyboardHeight()

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
    fun showPreview(view: TextView) { /* disabled */ }
    fun hidePreview() { /* disabled */ }
    fun toggleShift() {
        isShifted = !isShifted
        redrawKeyboard()
    }
    fun commitText(text: String) {

        // ✅ layout switching
        when (text) {
            "123" -> {
                // prebaci na numeric
                currentKeyboardConfig = applyEdgeKeys(myDefaultNumericConfig)
                redrawKeyboard()
                return
            }
            "ABC", "abc" -> {
                // vrati na alfabet (ako imaš spremljen)
                currentKeyboardConfig = applyEdgeKeys(alphabetLayout ?: defaultKeyboardLayout)
                redrawKeyboard()
                return
            }
        }

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

            if (keyboardContainer.childCount == 0) return@post
            if (overlayLayer.width <= 0) {
                overlayLayer.post { drawEdgeSlots() }
                return@post
            }

            // ✅ kreni od samog ruba
            val safe = 0

            // ✅ malo širi slotovi
            val keyW = computeRowSizing(7, availableKeyboardWidthPx()).keyW
            val slotW = (keyW * 0.68f).toInt().coerceIn(dp(28), dp(80))

            fun rowIndexForVisual(i: Int): Int = when (i) {
                0 -> 0
                1 -> keyboardContainer.childCount / 2
                else -> keyboardContainer.childCount - 1
            }.coerceIn(0, keyboardContainer.childCount - 1)

            val ovLoc = IntArray(2)
            overlayLayer.getLocationOnScreen(ovLoc)

            for (i in 0..2) {
                val rowIdx = rowIndexForVisual(i)
                val row = keyboardContainer.getChildAt(rowIdx) ?: continue
                if (row.height < dp(24)) continue

                val rowLoc = IntArray(2)
                row.getLocationOnScreen(rowLoc)

                val top = (rowLoc[1] - ovLoc[1]).coerceIn(
                    safe,
                    maxOf(safe, overlayLayer.height - row.height - safe)
                )
                val h = row.height

                fun addSlot(tag: String, isLeft: Boolean) {
                    val v = View(this).apply {
                        this.tag = tag
                        setBackgroundResource(if (isLeft) R.drawable.hex_half_left else R.drawable.hex_half_right)
                    }

                    val lp = FrameLayout.LayoutParams(slotW, h).apply {
                        gravity = Gravity.START

                        // ✅ gurni skroz do ekrana, ignoriraj padding overlayLayer-a
                        leftMargin = if (isLeft) {
                            -overlayLayer.paddingLeft
                        } else {
                            overlayLayer.width - slotW + overlayLayer.paddingRight
                        }

                        topMargin = top
                    }

                    overlayLayer.addView(v, lp)
                }

                addSlot("edge_slot_left_$i", true)
                addSlot("edge_slot_right_$i", false)
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
            keyboardContainer.post {
                syncOverlayHeightToContent()
                drawEdgeSlots()
                drawEdgeIcons()
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
    private fun drawEdgeIcons() {
        overlayLayer.post {
            // makni stare ikone (slotove NE diramo)
            val toRemove = mutableListOf<View>()
            for (i in 0 until overlayLayer.childCount) {
                val v = overlayLayer.getChildAt(i)
                val tag = v.tag?.toString() ?: continue
                if (tag.startsWith("edge_icon_")) toRemove.add(v)
            }
            toRemove.forEach { overlayLayer.removeView(it) }

            val safe = dp(1)
            val slots: List<EdgeSlot> = EdgeSlotsStorage.load(this)

            fun addIcon(slot: EdgeSlot) {
                if (slot.type == EdgeActionType.NONE) return

                val visualIndex = (slot.index / 2).coerceIn(0, 2)
                val slotTag = if (slot.side == EdgePos.Side.LEFT) "edge_slot_left_$visualIndex"
                else "edge_slot_right_$visualIndex"

                val iconTag = "edge_icon_${slot.index}"
                if (overlayLayer.findViewWithTag<View>(iconTag) != null) return

                val slotView = overlayLayer.findViewWithTag<View>(slotTag) ?: return

                if (slotView.width <= 0 || slotView.height <= 0) {
                    slotView.post { if (overlayLayer.findViewWithTag<View>(iconTag) == null) addIcon(slot) }
                    return
                }

                val slotLoc = IntArray(2)
                val ovLoc = IntArray(2)
                slotView.getLocationOnScreen(slotLoc)
                overlayLayer.getLocationOnScreen(ovLoc)

                val boxW = slotView.width
                val boxH = slotView.height

                val left = (slotLoc[0] - ovLoc[0]).coerceIn(safe, overlayLayer.width - boxW - safe)
                val top = (slotLoc[1] - ovLoc[1]).coerceIn(safe, overlayLayer.height - boxH - safe)

                val box = FrameLayout(this).apply {
                    tag = iconTag
                    layoutParams = FrameLayout.LayoutParams(boxW, boxH).apply {
                        gravity = Gravity.START
                        leftMargin = left
                        topMargin = top
                    }
                    setBackgroundColor(0x00000000)
                    isClickable = true
                }

                val icon = TextView(this).apply {
                    text = slot.label
                    gravity = Gravity.CENTER
                    includeFontPadding = false

                    val selectedShift = (slot.type == EdgeActionType.SHIFT && isShifted)
                    setTextColor(if (selectedShift) getColor(R.color.special_text) else getColor(R.color.key_text))

                    // malo veće, ali clampano
                    textSize = (boxH * 0.30f / resources.displayMetrics.scaledDensity).coerceIn(12f, 22f)

                    // “gurni” prema unutra (da sjedi na half-hexu)
                    if (slot.side == EdgePos.Side.LEFT) setPadding(dp(7), 0, dp(2), 0)
                    else setPadding(dp(2), 0, dp(7), 0)
                }

                box.addView(
                    icon,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                box.setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            slotView.isPressed = true
                            icon.alpha = 0.65f
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
    // --- long press popup state ---
    private val LIVE_REPLACE = false
    private var lpHasLiveInserted = false

    private fun showLongPressPopup(anchor: View, chars: List<String>) {
        if (chars.isEmpty()) return

        hideLongPressPopup()

        lpChars = chars
        lpSelectedIndex = 0
        lpHasLiveInserted = false

        val maxW = (overlayLayer.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels) - dp(16)

        val cols = minOf(7, chars.size)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(0xFFFFFFFF.toInt())
            // bitno: root mora znati svoju širinu
            layoutParams = ViewGroup.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Manji preview (da ne jede prostor)
        val preview = TextView(this).apply {
            text = chars.first()
            textSize = 26f
            setTextColor(0xFF000000.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, dp(6))
        }
        lpPreviewTv = preview
        root.addView(
            preview,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val grid = GridLayout(this).apply {
            columnCount = cols
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        lpGrid = grid

        val popupKeyH = (keyHeight() * 0.62f).toInt().coerceIn(dp(28), dp(70))
        val popupTextSize = if (isPortrait()) 16f else 14f

        chars.forEachIndexed { idx, ch ->
            val kv = KeyView(this).apply {
                tag = idx
                text = ch
                isAllCaps = false
                shape = currentShape
                gravity = Gravity.CENTER
                isClickable = false
                isFocusable = false
                textSize = popupTextSize
                setTextColor(getColor(R.color.key_text))
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
            }

            // Svaka tipka puni svoj stupac (width=0 + weight 1f)
            val lp = GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(idx / cols)
                columnSpec = GridLayout.spec(idx % cols, 1f)
                width = 0
                height = popupKeyH
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(kv, lp)
        }

        root.addView(grid)

        val pw = PopupWindow(
            root,
            maxW,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = false
            isFocusable = false
            // bitno: da se ne “odreže” čudno
            isClippingEnabled = true
            elevation = dp(10).toFloat()
            setOnDismissListener {
                longPressPopup = null
                lpPreviewTv = null
                lpGrid = null
                lpChars = emptyList()
                lpHasLiveInserted = false
            }
        }

        // izmjeri da clamp radi točno
        root.measure(
            View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val anchorLoc = IntArray(2)
        val rootLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        overlayLayer.getLocationOnScreen(rootLoc)

        val popupW = maxW
        val popupH = root.measuredHeight

        val desiredX = anchorLoc[0] - rootLoc[0] + anchor.width / 2 - popupW / 2
        val desiredY = anchorLoc[1] - rootLoc[1] - popupH - dp(10)

        val x = desiredX.coerceIn(dp(8), overlayLayer.width - popupW - dp(8))
        val y = desiredY.coerceIn(dp(8), overlayLayer.height - popupH - dp(8))

        pw.showAtLocation(overlayLayer, Gravity.NO_GRAVITY, x, y)
        longPressPopup = pw

        updateLongPressHighlight()
        root.post {
            val rects = MutableList(lpChars.size) { android.graphics.Rect() }

            val g = lpGrid ?: return@post
            for (i in 0 until g.childCount) {
                val child = g.getChildAt(i)
                val idx = (child.tag as? Int) ?: continue
                val loc = IntArray(2)
                child.getLocationOnScreen(loc)
                rects[idx] = android.graphics.Rect(
                    loc[0], loc[1],
                    loc[0] + child.width,
                    loc[1] + child.height
                )
            }
            lpRects = rects
        }
        if (LIVE_REPLACE) {
            commitLiveSelected()
        }
    }

    private fun updateLongPressHighlight() {
        val grid = lpGrid ?: return

        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            val idx = (child.tag as? Int) ?: continue

            if (child is KeyView) {
                if (idx == lpSelectedIndex) {
                    child.customBgColor = getColor(R.color.special_fill)
                    child.setTextColor(getColor(R.color.special_text))
                    child.alpha = 1f
                } else {
                    child.customBgColor = null
                    child.setTextColor(getColor(R.color.key_text))
                    child.alpha = 0.65f
                }
            } else {
                child.alpha = if (idx == lpSelectedIndex) 1f else 0.65f
            }

            child.scaleX = if (idx == lpSelectedIndex) 1.06f else 1f
            child.scaleY = if (idx == lpSelectedIndex) 1.06f else 1f
        }

        lpPreviewTv?.text = lpChars.getOrNull(lpSelectedIndex) ?: ""
    }

    private fun moveLpSelection(dx: Int, dy: Int) {
        if (lpChars.isEmpty()) return

        val cols = lpGrid?.columnCount ?: 7
        val total = lpChars.size
        val rows = (total + cols - 1) / cols

        val curRow = lpSelectedIndex / cols
        val curCol = lpSelectedIndex % cols

        var newRow = (curRow + dy).coerceIn(0, rows - 1)
        var newCol = (curCol + dx).coerceIn(0, cols - 1)

        var newIndex = newRow * cols + newCol

        // zadnji red može imati manje elemenata
        if (newIndex >= total) {
            // pomakni ulijevo dok ne uđe u range
            while (newIndex >= total && newCol > 0) {
                newCol--
                newIndex = newRow * cols + newCol
            }
            if (newIndex >= total) newIndex = total - 1
        }

        if (newIndex != lpSelectedIndex) {
            lpSelectedIndex = newIndex
            updateLongPressHighlight()

            if (LIVE_REPLACE) {
                replaceLiveSelected()
            }
        }
    }

    private fun commitLiveSelected() {
        val ch = lpChars.getOrNull(lpSelectedIndex) ?: return
        currentInputConnection?.commitText(ch, 1)
        lpHasLiveInserted = true
    }

    private fun replaceLiveSelected() {
        // briši prethodni samo ako smo već nešto upisali u live modu
        if (lpHasLiveInserted) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
        commitLiveSelected()
    }

    private fun hideLongPressPopup() {
        longPressPopup?.dismiss()
        longPressPopup = null
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

        val nonBindable = setOf("⇧", "⌫", "↵", "123", "ABC", "abc", " ")
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        var longPressTriggered = false

        // swipe state
        var startX = 0f
        var startY = 0f
        val step = dp(18) // koliko px moraš pomaknut za 1 “korak”

        val longPressRunnable = Runnable {
            if (label in nonBindable) return@Runnable
            val binds = keyConfig.longPressBindings
            if (binds.isNotEmpty()) {
                longPressTriggered = true
                showLongPressPopup(this, binds)

                // prvi znak selektiran
                lpSelectedIndex = 0
                updateLongPressHighlight()

                // reset swipa da prvi pomak bude “čist”
                // (inače može odmah preskočit)
                // startX/startY ostaju na zadnjem touch pointu
            }
        }

        isLongClickable = true

        setOnTouchListener { v, e ->
            when (e.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX
                    startY = e.rawY

                    longPressTriggered = false
                    hideLongPressPopup()

                    mainHandler.removeCallbacks(longPressRunnable)
                    mainHandler.postDelayed(longPressRunnable, longPressTimeout)

                    // da tipka dobije pressed state / vizual
                    inputController.handleTouch(v as TextView, e)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (longPressTriggered) {
                        // dok je popup aktivan: ne diraj inputController
                        val dx = e.rawX - startX
                        val dy = e.rawY - startY

                        val adx = kotlin.math.abs(dx)
                        val ady = kotlin.math.abs(dy)

                        // 1) “step” navigacija
                        if (adx > ady && adx > step) {
                            moveLpSelection(if (dx > 0) +1 else -1, 0)
                            startX = e.rawX
                            startY = e.rawY
                            true
                        } else if (ady > step) {
                            moveLpSelection(0, if (dy > 0) +1 else -1)
                            startX = e.rawX
                            startY = e.rawY
                            true
                        } else {
                            // 2) opcionalno: hover po rectovima (pratimo prst)
                            if (lpRects.isNotEmpty()) {
                                val rx = e.rawX.toInt()
                                val ry = e.rawY.toInt()
                                val newIdx = lpRects.indexOfFirst { it.contains(rx, ry) }
                                if (newIdx != -1 && newIdx != lpSelectedIndex) {
                                    lpSelectedIndex = newIdx
                                    updateLongPressHighlight()
                                }
                            }
                            true
                        }
                    } else {
                        inputController.handleTouch(v as TextView, e)
                        true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)

                    if (longPressTriggered) {
                        // pusti -> commit selektirani char
                        lpChars.getOrNull(lpSelectedIndex)?.let { ch ->
                            currentInputConnection?.commitText(ch, 1)
                        }
                        hideLongPressPopup()
                        v.isPressed = false
                        true
                    } else {
                        inputController.handleTouch(v as TextView, e)
                        true
                    }
                }

                else -> false
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
