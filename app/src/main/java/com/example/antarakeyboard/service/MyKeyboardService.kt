package com.example.antarakeyboard.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.service.input.KeyInputController
import com.example.antarakeyboard.ui.KeyView
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import com.example.antarakeyboard.ui.defaultNumericLayout
import kotlin.math.max
import kotlin.math.roundToInt

class MyKeyboardService : InputMethodService() {

    /* ───────── STATE ───────── */

    private var isShifted = false
    private var isDrawing = false
    private var lastBottomInsetPx: Int = 0

    private var currentKeyboardConfig: KeyboardConfig = defaultKeyboardLayout
    private var currentShape: KeyShape = KeyShape.HEX

    private val mainHandler = Handler(Looper.getMainLooper())
    private val swipeEditHandler = Handler(Looper.getMainLooper())
    private val backspaceHoldHandler = Handler(Looper.getMainLooper())

    private var previewPopup: PopupWindow? = null
    private var longPressPopup: PopupWindow? = null

    private lateinit var rootView: View
    private lateinit var keyboardContainer: LinearLayout
    private lateinit var overlayLayer: FrameLayout
    private lateinit var themedCtx: Context

    private val myDefaultNumericConfig: KeyboardConfig
        get() = KeyboardPrefs.loadNumericLayout(this)

    private val OVERLAP_RATIO = 0.18f

    private var lastIsDark: Boolean? = null
    private var targetKeyboardHeightPx: Int = 0

    lateinit var inputController: KeyInputController

    private var lpRects: List<android.graphics.Rect> = emptyList()
    private var lpChars: List<String> = emptyList()
    private var lpSelectedIndex: Int = 0
    private var lpPreviewTv: TextView? = null
    private var lpGrid: GridLayout? = null

    private var alphabetLayoutLower: KeyboardConfig? = null
    private var alphabetLayoutUpper: KeyboardConfig? = null

    private var deleteRepeatRunnable: Runnable? = null
    private var restoreRepeatRunnable: Runnable? = null
    private var backspaceHoldRunnable: Runnable? = null
    private var backspaceStartHoldRunnable: Runnable? = null

    private var deleteRepeatMs: Long = 120L
    private var restoreRepeatMs: Long = 120L
    private var backspaceHoldMs: Long = 90L

    private var lastDeletedText: String = ""
    private val currentDeleteBatch = StringBuilder()
    private var restoreProgressIndex: Int = 0

    private var isDeleteGestureActive = false
    private var isRestoreGestureActive = false
    private var isBackspaceHoldActive = false

    private val LIVE_REPLACE = false
    private var lpHasLiveInserted = false
    private val EDGE_GHOST_MARKER = "__EDGE_GHOST__"
    private val USER_EMPTY_MARKER = "__USER_EMPTY__"

    /* ───────── LIFECYCLE ───────── */

    override fun onCreateInputView(): View {
        val isDark = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            .getBoolean("dark_mode", true)

        val themeRes = if (isDark) {
            R.style.Theme_AntaraKeyboard_Dark
        } else {
            R.style.Theme_AntaraKeyboard_Light
        }

        themedCtx = ContextThemeWrapper(this, themeRes)
        lastIsDark = isDark

        rootView = layoutInflater.cloneInContext(themedCtx)
            .inflate(R.layout.keyboard_view, null)

        overlayLayer = rootView.findViewById(R.id.keyboardRoot)
        keyboardContainer = rootView.findViewById(R.id.keyboardContainer)

        lastBottomInsetPx = 0

        val bg = keyboardBgColor(themedCtx)
        window?.window?.setBackgroundDrawable(ColorDrawable(bg))
        rootView.setBackgroundColor(bg)
        overlayLayer.setBackgroundColor(bg)
        keyboardContainer.setBackgroundColor(bg)

        overlayLayer.clipChildren = false
        overlayLayer.clipToPadding = false
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
            val navInset = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom

            val tappableInset = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.tappableElement()
            ).bottom

            val gestureInset = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemGestures()
            ).bottom

            val bottomInset = maxOf(navInset, tappableInset, gestureInset)
            val insetChanged = lastBottomInsetPx != bottomInset

            lastBottomInsetPx = bottomInset
            v.setPadding(basePadL, basePadT, basePadR, basePadB + bottomInset)

            v.post {
                syncOverlayHeightToContent()
                if (insetChanged) {
                    redrawKeyboard()
                } else {
                    overlayLayer.requestLayout()
                }
            }

            insets
        }

        ViewCompat.requestApplyInsets(overlayLayer)

        targetKeyboardHeightPx = computeTargetKeyboardHeight()
        currentShape = KeyboardPrefs.getShape(this)

        val baseCfg = KeyboardPrefs.loadLayout(this)
        alphabetLayoutLower = baseCfg
        alphabetLayoutUpper = makeUppercaseConfig(baseCfg)

        val activeAlphabet = if (isShifted) alphabetLayoutUpper else alphabetLayoutLower
        currentKeyboardConfig = applyEdgeKeys(activeAlphabet ?: baseCfg)

        overlayLayer.post { redrawKeyboard() }
        return rootView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        setExtractViewShown(false)

        isShifted = false

        val isDarkNow = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            .getBoolean("dark_mode", true)

        if (lastIsDark != null && lastIsDark != isDarkNow) {
            lastIsDark = isDarkNow
            recreateInputView()
            return
        }
        lastIsDark = isDarkNow

        currentShape = KeyboardPrefs.getShape(this)

        val baseCfg = KeyboardPrefs.loadLayout(this)
        currentKeyboardConfig = applyEdgeKeys(baseCfg)

        val hasLetters = baseCfg.rows.any { row ->
            row.keys.any { k -> k.label.length == 1 && k.label[0].isLetter() }
        }

        if (hasLetters) {
            alphabetLayoutLower = baseCfg
            alphabetLayoutUpper = makeUppercaseConfig(baseCfg)
            currentKeyboardConfig = applyEdgeKeys(alphabetLayoutLower ?: baseCfg)
        }

        targetKeyboardHeightPx = computeTargetKeyboardHeight()
        overlayLayer.post { redrawKeyboard() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        resetTransientState()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        resetTransientState()
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onCreateExtractTextView(): View? = null

    private fun resetTransientState() {
        isShifted = false

        stopSwipeDelete()
        stopSwipeRestore()
        stopBackspaceHold()

        currentDeleteBatch.clear()
        isDeleteGestureActive = false
        isRestoreGestureActive = false
        isBackspaceHoldActive = false
        restoreProgressIndex = 0

        hideLongPressPopup()
        hideKeyPreview()
    }

    private fun recreateInputView() {
        setInputView(onCreateInputView())
    }

    /* ───────── HELPERS ───────── */

    private fun themeColor(ctx: Context, attr: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        val th = ctx.theme
        return if (
            th.resolveAttribute(attr, tv, true) &&
            tv.type in android.util.TypedValue.TYPE_FIRST_COLOR_INT..android.util.TypedValue.TYPE_LAST_COLOR_INT
        ) {
            tv.data
        } else {
            fallback
        }
    }

    private fun keyboardBgColor(ctx: Context): Int {
        return themeColor(
            ctx,
            android.R.attr.windowBackground,
            themeColor(ctx, android.R.attr.colorBackground, Color.BLACK)
        )
    }

    private fun edgeIconTextColor(ctx: Context): Int =
        themeColor(ctx, R.attr.edgeIconText, 0xFFFFFFFF.toInt())

    private fun edgeIconActiveColor(ctx: Context): Int =
        themeColor(ctx, R.attr.edgeIconTextActive, edgeIconTextColor(ctx))

    private fun isPortrait() =
        resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun computeTargetKeyboardHeight(): Int {
        val screenH = resources.displayMetrics.heightPixels
        val ratio = if (isPortrait()) 0.36f else 0.28f
        return (screenH * ratio).roundToInt().coerceAtLeast(dp(230))
    }

    private fun totalVisibleRows(): Int {
        var rows = currentKeyboardConfig.rows.size
        if (currentKeyboardConfig.specialLeft.isNotEmpty()) rows += 1
        if (currentKeyboardConfig.specialRight.isNotEmpty()) rows += 1
        return rows.coerceAtLeast(1)
    }

    private fun keyHeight(): Int {
        val rows = totalVisibleRows()

        val containerH = (targetKeyboardHeightPx + lastBottomInsetPx).takeIf { it > 0 }
            ?: computeTargetKeyboardHeight()

        val usableH = (containerH - overlayLayer.paddingTop - overlayLayer.paddingBottom)
            .coerceAtLeast(dp(150))

        val denom = when (currentShape) {
            KeyShape.HEX -> (rows - (rows - 1) * OVERLAP_RATIO).coerceAtLeast(1f)
            KeyShape.TRIANGLE -> rows.toFloat()
            KeyShape.CIRCLE -> rows.toFloat()
            KeyShape.CUBE -> rows.toFloat()
        }

        val usableForKeys = (usableH * 0.92f).toInt()
        val baseH = (usableForKeys / denom).toInt().coerceAtLeast(dp(36))

        return when (currentShape) {
            KeyShape.HEX -> (baseH * 1.08f).toInt()
            KeyShape.TRIANGLE -> (baseH * 0.92f).toInt()
            KeyShape.CIRCLE -> (baseH * 0.96f).toInt()
            KeyShape.CUBE -> (baseH * 0.96f).toInt()
        }
    }

    private fun availableKeyboardWidthPx(): Int {
        val w = overlayLayer.width
        val base = if (w > 0) w else resources.displayMetrics.widthPixels
        return (base - overlayLayer.paddingLeft - overlayLayer.paddingRight).coerceAtLeast(dp(200))
    }

    private fun syncOverlayHeightToContent() {
        if (!::overlayLayer.isInitialized || !::keyboardContainer.isInitialized) return

        val contentH = keyboardContainer.height
        if (contentH <= 0) return

        val extraBottomSafety = dp(16)
        val desired = contentH +
                overlayLayer.paddingTop +
                overlayLayer.paddingBottom +
                extraBottomSafety

        val maxTarget = (computeTargetKeyboardHeight() + lastBottomInsetPx)
            .coerceAtLeast(dp(230))

        val newH = desired.coerceIn(dp(180), maxTarget)

        val lp = overlayLayer.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            newH
        )

        if (lp.height != newH) {
            lp.height = newH
            overlayLayer.layoutParams = lp
            overlayLayer.minimumHeight = newH
            overlayLayer.requestLayout()
        }
    }

    /* ───────── DELETE / RESTORE LOGIC ───────── */

    private fun beginDeleteBatch() {
        currentDeleteBatch.clear()
    }

    private fun appendDeletedChar(ch: String) {
        currentDeleteBatch.insert(0, ch)
    }

    private fun finishDeleteBatch() {
        val result = currentDeleteBatch.toString()
        if (result.isNotEmpty()) {
            lastDeletedText = result
            restoreProgressIndex = 0
        }
    }

    private fun clearRestoreBuffer() {
        lastDeletedText = ""
        restoreProgressIndex = 0
    }

    private fun swipeRepeatDelay(absDx: Float): Long {
        return when {
            absDx > dp(170) -> 25L
            absDx > dp(140) -> 40L
            absDx > dp(110) -> 55L
            absDx > dp(80) -> 75L
            absDx > dp(60) -> 95L
            else -> 120L
        }
    }

    private fun deleteOneForSwipe() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        if (before.isEmpty()) return

        appendDeletedChar(before)
        ic.deleteSurroundingText(1, 0)
    }

    private fun restoreOneForSwipe() {
        val ic = currentInputConnection ?: return
        if (lastDeletedText.isEmpty()) return
        if (restoreProgressIndex >= lastDeletedText.length) return

        val ch = lastDeletedText[restoreProgressIndex].toString()
        ic.commitText(ch, 1)
        restoreProgressIndex++

        if (restoreProgressIndex >= lastDeletedText.length) {
            lastDeletedText = ""
            restoreProgressIndex = 0
        }
    }

    fun startSwipeDelete(absDx: Float) {
        stopSwipeRestore()
        deleteRepeatMs = swipeRepeatDelay(absDx)

        if (!isDeleteGestureActive) {
            isDeleteGestureActive = true
            beginDeleteBatch()
        }

        if (deleteRepeatRunnable != null) return

        deleteRepeatRunnable = object : Runnable {
            override fun run() {
                deleteOneForSwipe()
                swipeEditHandler.postDelayed(this, deleteRepeatMs)
            }
        }

        deleteOneForSwipe()
        swipeEditHandler.postDelayed(deleteRepeatRunnable!!, deleteRepeatMs)
    }

    fun updateSwipeDelete(absDx: Float) {
        deleteRepeatMs = swipeRepeatDelay(absDx)
    }

    fun stopSwipeDelete() {
        deleteRepeatRunnable?.let { swipeEditHandler.removeCallbacks(it) }
        deleteRepeatRunnable = null

        if (isDeleteGestureActive) {
            finishDeleteBatch()
            isDeleteGestureActive = false
        }
    }
    private fun restoreRepeatDelay(absDx: Float): Long {
        return when {
            absDx > dp(170) -> 14L
            absDx > dp(140) -> 24L
            absDx > dp(110) -> 36L
            absDx > dp(80) -> 50L
            absDx > dp(60) -> 68L
            else -> 90L
        }
    }
    fun startSwipeRestore(absDx: Float) {
        stopSwipeDelete()
        restoreRepeatMs = restoreRepeatDelay(absDx)

        if (!isRestoreGestureActive) {
            isRestoreGestureActive = true
        }

        if (restoreRepeatRunnable != null) return

        restoreRepeatRunnable = object : Runnable {
            override fun run() {
                restoreOneForSwipe()
                swipeEditHandler.postDelayed(this, restoreRepeatMs)
            }
        }

        restoreOneForSwipe()
        swipeEditHandler.postDelayed(restoreRepeatRunnable!!, restoreRepeatMs)
    }

    fun updateSwipeRestore(absDx: Float) {
        restoreRepeatMs = restoreRepeatDelay(absDx)
    }



    fun stopSwipeRestore() {
        restoreRepeatRunnable?.let { swipeEditHandler.removeCallbacks(it) }
        restoreRepeatRunnable = null
        isRestoreGestureActive = false
    }

    fun startBackspaceHold() {
        cancelPendingBackspaceHold()

        if (!isBackspaceHoldActive) {
            isBackspaceHoldActive = true
            beginDeleteBatch()
        }

        if (backspaceHoldRunnable != null) return

        backspaceHoldRunnable = object : Runnable {
            override fun run() {
                deleteOneForSwipe()
                backspaceHoldHandler.postDelayed(this, backspaceHoldMs)
            }
        }

        // prvi delete odmah kad hold stvarno krene
        deleteOneForSwipe()
        backspaceHoldHandler.postDelayed(backspaceHoldRunnable!!, backspaceHoldMs)
    }
    fun commitExactText(text: String) {
        clearRestoreBuffer()
        currentInputConnection?.commitText(text, 1)
    }
    fun isBackspaceHoldRunning(): Boolean {
        return isBackspaceHoldActive
    }

    fun stopBackspaceHold() {
        cancelPendingBackspaceHold()

        backspaceHoldRunnable?.let { backspaceHoldHandler.removeCallbacks(it) }
        backspaceHoldRunnable = null

        if (isBackspaceHoldActive) {
            finishDeleteBatch()
            isBackspaceHoldActive = false
        }
    }

    /* ───────── INPUT API for Controller ───────── */

    fun showPreview(view: TextView) { /* disabled */ }
    fun hidePreview() { /* disabled */ }
    fun scheduleBackspaceHold() {
        cancelPendingBackspaceHold()

        backspaceStartHoldRunnable = Runnable {
            startBackspaceHold()
        }

        backspaceHoldHandler.postDelayed(
            backspaceStartHoldRunnable!!,
            ViewConfiguration.getLongPressTimeout().toLong()
        )
    }

    fun cancelPendingBackspaceHold() {
        backspaceStartHoldRunnable?.let { backspaceHoldHandler.removeCallbacks(it) }
        backspaceStartHoldRunnable = null
    }
    fun backspaceOnce() {
        beginDeleteBatch()
        deleteOneForSwipe()
        finishDeleteBatch()
    }

    fun sendEnter() {
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        )
    }

    fun toggleShift() {
        isShifted = !isShifted

        val isNumeric = currentKeyboardConfig.rows.any { row ->
            row.keys.any { it.label == "123" || it.label.equals("abc", true) }
        }

        if (!isNumeric) {
            val lower = alphabetLayoutLower
            val upper = alphabetLayoutUpper
            if (lower != null && upper != null) {
                currentKeyboardConfig = applyEdgeKeys(if (isShifted) upper else lower)
            }
        }

        redrawKeyboard()
    }

    fun commitText(text: String) {
        if (text != "123" && text != "ABC" && text != "abc") {
            clearRestoreBuffer()
        }

        when (text) {
            "123" -> {
                currentKeyboardConfig = applyEdgeKeys(myDefaultNumericConfig)
                redrawKeyboard()
                return
            }

            "ABC", "abc" -> {
                val baseCfg = KeyboardPrefs.loadLayout(this)
                alphabetLayoutLower = baseCfg
                alphabetLayoutUpper = makeUppercaseConfig(baseCfg)
                currentKeyboardConfig = applyEdgeKeys(
                    if (isShifted) alphabetLayoutUpper ?: baseCfg
                    else alphabetLayoutLower ?: baseCfg
                )
                redrawKeyboard()
                return
            }
        }

        val out = if (text.length == 1 && text[0].isLetter()) {
            if (isShifted) text.uppercase() else text.lowercase()
        } else {
            text
        }

        currentInputConnection?.commitText(out, 1)
    }

    /* ───────── PREVIEW ───────── */

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

    /* ───────── EDGE KEYS ───────── */

    private fun applyEdgeKeys(cfg: KeyboardConfig): KeyboardConfig {
        val copy = cfg.copy(
            rows = cfg.rows.map { row ->
                row.copy(
                    keys = row.keys.map { key ->
                        key.copy(longPressBindings = key.longPressBindings.toMutableList())
                    }.toMutableList()
                )
            }.toMutableList(),
            specialLeft = cfg.specialLeft.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList(),
            specialRight = cfg.specialRight.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList()
        )

        val slots = EdgeSlotsStorage.load(this).filter { it.type != EdgeActionType.NONE }
        if (slots.isEmpty()) return copy

        val labelsToHideFromMainLayout = buildSet<String> {
            slots.forEach { s ->
                when (s.type) {
                    EdgeActionType.SHIFT -> add("⇧")
                    EdgeActionType.BACKSPACE -> add("⌫")
                    EdgeActionType.ENTER -> add("↵")
                    EdgeActionType.SPACE -> Unit
                    EdgeActionType.CHAR -> Unit
                    EdgeActionType.NONE -> Unit
                }
            }
        }

        fun replaceWithGhostPlaceholder(list: MutableList<KeyConfig>) {
            for (i in list.indices) {
                val key = list[i]
                if (key.label in labelsToHideFromMainLayout) {
                    list[i] = key.copy(
                        label = "",
                        longPressBindings = mutableListOf(EDGE_GHOST_MARKER)
                    )
                }
            }
        }

        copy.rows.forEach { replaceWithGhostPlaceholder(it.keys) }
        replaceWithGhostPlaceholder(copy.specialLeft)
        replaceWithGhostPlaceholder(copy.specialRight)

        return copy
    }

    /* ───────── LONG PRESS POPUP ───────── */

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
            layoutParams = ViewGroup.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

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
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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
                setTextColor(themeColor(this@MyKeyboardService, R.attr.keyText, Color.WHITE))
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
            }

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
                    loc[0],
                    loc[1],
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

        val fillActive = themeColor(this, R.attr.enterFill, 0xFF2E55E7.toInt())
        val textActive = themeColor(this, R.attr.enterText, 0xFFFFFFFF.toInt())
        val textNormal = themeColor(this, R.attr.keyText, 0xFFFFFFFF.toInt())

        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            val idx = (child.tag as? Int) ?: continue

            if (child is KeyView) {
                if (idx == lpSelectedIndex) {
                    child.customBgColor = fillActive
                    child.setTextColor(textActive)
                    child.alpha = 1f
                } else {
                    child.customBgColor = null
                    child.setTextColor(textNormal)
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

        if (newIndex >= total) {
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
        if (lpHasLiveInserted) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
        commitLiveSelected()
    }

    private fun hideLongPressPopup() {
        longPressPopup?.dismiss()
        longPressPopup = null
    }

    /* ───────── LAYOUT ───────── */

    private data class RowSizing(
        val keyW: Int,
        val keyH: Int,
        val gapPx: Int,
        val outerPadPx: Int,
        val overlapPx: Int,
        val triOverlapX: Int,
        val triOverlapY: Int
    )

    private fun gapPxForShape(): Int = when (currentShape) {
        KeyShape.HEX -> dp(1)
        KeyShape.TRIANGLE -> dp(0)
        KeyShape.CIRCLE -> dp(4)
        KeyShape.CUBE -> dp(4)
    }

    private fun computeRowSizing(count: Int, availW: Int): RowSizing {
        val gap = gapPxForShape()

        val effectiveAvailW = when (currentShape) {
            KeyShape.HEX -> availW
            KeyShape.TRIANGLE -> (availW * 0.78f).toInt()
            KeyShape.CIRCLE -> (availW * 0.92f).toInt()
            KeyShape.CUBE -> (availW * 0.92f).toInt()
        }

        val stdKeyW = ((effectiveAvailW - (7 - 1) * gap) / 7f)
            .toInt()
            .coerceAtLeast(dp(36))

        val keyW: Int
        val outer: Int

        if (count == 7) {
            keyW = ((effectiveAvailW - (count - 1) * gap) / count.toFloat())
                .toInt()
                .coerceAtLeast(dp(36))
            val used = count * keyW + (count - 1) * gap
            outer = ((availW - used) / 2).coerceAtLeast(0)
        } else if (count == 6) {
            keyW = stdKeyW
            val used = count * keyW + (count - 1) * gap
            outer = ((availW - used) / 2).coerceAtLeast(0)
        } else {
            keyW = ((effectiveAvailW - (count - 1) * gap) / max(1, count).toFloat())
                .toInt()
                .coerceAtLeast(dp(36))
            val used = count * keyW + (count - 1) * gap
            outer = ((availW - used) / 2).coerceAtLeast(0)
        }

        val keyH = keyHeight()
        val overlap = when (currentShape) {
            KeyShape.HEX -> (keyH * OVERLAP_RATIO).toInt()
            else -> 0
        }

        return RowSizing(
            keyW = keyW,
            keyH = keyH,
            gapPx = gap,
            outerPadPx = outer,
            overlapPx = overlap,
            triOverlapX = 0,
            triOverlapY = 0
        )
    }

    private fun redrawKeyboard() {
        if (!::keyboardContainer.isInitialized) return
        if (!::overlayLayer.isInitialized) return
        if (isDrawing) return

        isDrawing = true

        mainHandler.post {
            keyboardContainer.removeAllViews()
            var spaceIndex = 0

            fun buildRow(keys: List<KeyConfig>, containerRowIndex: Int) {
                val rowKeys = keys.filterNot { key ->
                    key.longPressBindings.contains(EDGE_GHOST_MARKER)
                }

                if (rowKeys.isEmpty()) return

                val availW = availableKeyboardWidthPx()
                val sizing = computeRowSizing(rowKeys.size, availW)

                val vPad = when (currentShape) {
                    KeyShape.TRIANGLE -> 0
                    KeyShape.HEX -> dp(1)
                    else -> dp(2)
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(sizing.outerPadPx, vPad, sizing.outerPadPx, vPad)
                    clipToPadding = false
                }

                rowKeys.forEachIndexed { i, key ->
                    val kv = createKey(key)

                    if (currentShape == KeyShape.TRIANGLE) {
                        kv.triangleFlipped = (i % 2 == 1)
                    }

                    if (key.label == " ") {
                        val linked = KeyboardPrefs.isSpaceLinked(this@MyKeyboardService)
                        val c1 = KeyboardPrefs.getSpace1Bg(this@MyKeyboardService)
                        val c2 = if (linked) c1 else KeyboardPrefs.getSpace2Bg(this@MyKeyboardService)
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
                overlayLayer.post {
                    drawEdgeSlots()
                    drawEdgeIcons()
                    isDrawing = false
                }
            }
        }
    }

    /* ───────── EDGE OVERLAY ───────── */

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
            if (overlayLayer.width <= 0 || overlayLayer.height <= 0) {
                overlayLayer.post { drawEdgeSlots() }
                return@post
            }

            val safeY = dp(2)
            val edgeOutset = dp(10)
            val liftY = dp(12)
            val rightNudge = dp(14)

            val keyW = computeRowSizing(7, availableKeyboardWidthPx()).keyW
            val slotW = (keyW * 0.46f).toInt().coerceIn(dp(22), dp(56))

            fun rowIndexForVisual(i: Int): Int = when (i) {
                0 -> 0
                1 -> keyboardContainer.childCount / 2
                else -> keyboardContainer.childCount - 1
            }.coerceIn(0, keyboardContainer.childCount - 1)

            val ovLoc = IntArray(2)
            overlayLayer.getLocationOnScreen(ovLoc)

            fun firstKey(row: View): View? =
                (row as? ViewGroup)?.takeIf { it.childCount > 0 }?.getChildAt(0)

            fun addSlot(tag: String, isLeft: Boolean, top: Int, h: Int) {
                val v = View(this).apply {
                    this.tag = tag
                    setBackgroundResource(
                        if (isLeft) R.drawable.hex_half_left_edge_sel
                        else R.drawable.hex_half_right_edge_sel
                    )
                    alpha = 0.95f
                }

                val lp = FrameLayout.LayoutParams(slotW, h).apply {
                    gravity = Gravity.START
                    leftMargin = if (isLeft) {
                        -edgeOutset
                    } else {
                        overlayLayer.width - slotW + edgeOutset - rightNudge
                    }
                    topMargin = top
                }

                overlayLayer.addView(v, 0, lp)
            }

            for (i in 0..2) {
                val row = keyboardContainer.getChildAt(rowIndexForVisual(i)) ?: continue
                val keyRef = firstKey(row) ?: continue

                if (keyRef.width <= 0 || keyRef.height <= 0) {
                    keyRef.post { drawEdgeSlots() }
                    return@post
                }

                val keyLoc = IntArray(2)
                keyRef.getLocationOnScreen(keyLoc)

                val top = (keyLoc[1] - ovLoc[1] - liftY).coerceIn(
                    safeY,
                    overlayLayer.height - keyRef.height - safeY
                )

                addSlot("edge_slot_left_$i", true, top, keyRef.height)
                addSlot("edge_slot_right_$i", false, top, keyRef.height)
            }
        }
    }

    private fun performEdgeAction(slot: EdgeSlot) {
        when (slot.type) {
            EdgeActionType.SHIFT -> toggleShift()
            EdgeActionType.BACKSPACE -> backspaceOnce()
            EdgeActionType.ENTER -> sendEnter()
            EdgeActionType.SPACE -> currentInputConnection?.commitText(" ", 1)
            EdgeActionType.CHAR -> slot.value?.let { currentInputConnection?.commitText(it, 1) }
            EdgeActionType.NONE -> Unit
        }
    }

    private fun drawEdgeIcons() {
        overlayLayer.post {
            val toRemove = mutableListOf<View>()
            for (i in 0 until overlayLayer.childCount) {
                val v = overlayLayer.getChildAt(i)
                val tag = v.tag?.toString() ?: continue
                if (tag.startsWith("edge_icon_")) toRemove.add(v)
            }
            toRemove.forEach { overlayLayer.removeView(it) }

            val safe = dp(2)
            val rightIconNudge = dp(14)
            val slots: List<EdgeSlot> = EdgeSlotsStorage.load(this)

            fun addIcon(slot: EdgeSlot) {
                if (slot.type == EdgeActionType.NONE) return

                val visualIndex = (slot.index / 2).coerceIn(0, 2)
                val slotTag = if (slot.side == EdgePos.Side.LEFT) {
                    "edge_slot_left_$visualIndex"
                } else {
                    "edge_slot_right_$visualIndex"
                }

                val iconTag = "edge_icon_${slot.index}"
                if (overlayLayer.findViewWithTag<View>(iconTag) != null) return

                val slotView = overlayLayer.findViewWithTag<View>(slotTag) ?: return
                if (slotView.width <= 0 || slotView.height <= 0) {
                    slotView.post { addIcon(slot) }
                    return
                }

                val slotLoc = IntArray(2)
                val ovLoc = IntArray(2)
                slotView.getLocationOnScreen(slotLoc)
                overlayLayer.getLocationOnScreen(ovLoc)

                val boxW = slotView.width
                val boxH = slotView.height

                var left = (slotLoc[0] - ovLoc[0]).coerceIn(
                    safe,
                    overlayLayer.width - boxW - safe
                )
                val top = (slotLoc[1] - ovLoc[1]).coerceIn(
                    safe,
                    overlayLayer.height - boxH - safe
                )

                if (slot.side == EdgePos.Side.RIGHT) {
                    left = (left - rightIconNudge).coerceAtLeast(safe)
                }

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
                    val isShiftSlot = slot.type == EdgeActionType.SHIFT
                    text = if (isShiftSlot && isShifted) "⇪" else slot.label
                    gravity = Gravity.CENTER
                    includeFontPadding = false

                    val selectedShift = slot.type == EdgeActionType.SHIFT && isShifted
                    setTextColor(
                        if (selectedShift) edgeIconActiveColor(themedCtx)
                        else edgeIconTextColor(themedCtx)
                    )

                    textSize = (boxH * 0.28f / resources.displayMetrics.scaledDensity)
                        .coerceIn(12f, 20f)

                    if (slot.side == EdgePos.Side.LEFT) {
                        setPadding(dp(8), 0, dp(2), 0)
                    } else {
                        setPadding(dp(2), 0, dp(8), 0)
                    }
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
                            slotView.alpha = 0.85f
                            icon.alpha = 0.70f

                            if (slot.type == EdgeActionType.BACKSPACE) {
                                scheduleBackspaceHold()
                            }

                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            slotView.isPressed = false
                            slotView.alpha = 1f
                            icon.alpha = 1f

                            if (slot.type == EdgeActionType.BACKSPACE) {
                                cancelPendingBackspaceHold()

                                if (isBackspaceHoldRunning()) {
                                    stopBackspaceHold()
                                } else {
                                    backspaceOnce()
                                }
                            } else {
                                performEdgeAction(slot)
                            }

                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            slotView.isPressed = false
                            slotView.alpha = 1f
                            icon.alpha = 1f

                            if (slot.type == EdgeActionType.BACKSPACE) {
                                cancelPendingBackspaceHold()
                                stopBackspaceHold()
                            }

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

    private fun makeUppercaseConfig(cfg: KeyboardConfig): KeyboardConfig {
        fun up(k: KeyConfig): KeyConfig {
            val lbl = k.label
            val newLbl = if (lbl.length == 1 && lbl[0].isLetter()) lbl.uppercase() else lbl
            return k.copy(label = newLbl, longPressBindings = k.longPressBindings.toMutableList())
        }

        return cfg.copy(
            rows = cfg.rows.map { it.copy(keys = it.keys.map(::up).toMutableList()) }.toMutableList(),
            specialLeft = cfg.specialLeft.map(::up).toMutableList(),
            specialRight = cfg.specialRight.map(::up).toMutableList()
        )
    }

    private fun createKey(keyConfig: KeyConfig): KeyView = KeyView(this).apply {
        val label = keyConfig.label

        if (label.isEmpty()) {
            val isEdgeGhost = keyConfig.longPressBindings.contains(EDGE_GHOST_MARKER)
            val isUserEmpty = keyConfig.longPressBindings.contains(USER_EMPTY_MARKER)

            text = ""
            isAllCaps = false
            shape = currentShape
            gravity = Gravity.CENTER
            isClickable = false
            isFocusable = false

            when {
                isEdgeGhost -> {
                    hideCompletely = true
                    alpha = 0f
                    customBgColor = android.graphics.Color.TRANSPARENT
                }

                isUserEmpty -> {
                    hideCompletely = false
                    alpha = 0.65f
                    customBgColor = themeColor(
                        this@MyKeyboardService,
                        R.attr.keyFill,
                        0xFF4A4A4A.toInt()

                    )
                }

                else -> {
                    hideCompletely = true
                    alpha = 0f
                    customBgColor = android.graphics.Color.TRANSPARENT
                }
            }

            return@apply
        }

        hideCompletely = false

        val display = if (label.length == 1 && label[0].isLetter()) {
            if (isShifted) label.uppercase() else label.lowercase()
        } else {
            label
        }

        text = display
        isAllCaps = false
        shape = currentShape
        isSpecial = (label == "↵")
        gravity = Gravity.CENTER

        setTextColor(themeColor(this@MyKeyboardService, R.attr.keyText, Color.WHITE))

        if (label == "↵") {
            customBgColor = KeyboardPrefs.getEnterBg(context)
            setTextColor(KeyboardPrefs.getEnterIcon(context))
        }

        if (label == "⇧") {
            if (isShifted) {
                text = "⇪"
                customBgColor = Color.WHITE
                setTextColor(Color.BLACK)
            } else {
                text = "⇧"
                customBgColor = null
                setTextColor(themeColor(this@MyKeyboardService, R.attr.keyText, Color.WHITE))
            }
        }

        val nonBindable = setOf("⇧", "⌫", "↵", "123", "ABC", "abc", " ")
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        var longPressTriggered = false
        var startX = 0f
        var startY = 0f
        val step = dp(18)

        val longPressRunnable = Runnable {
            if (label in nonBindable) return@Runnable
            val binds = keyConfig.longPressBindings
            if (binds.isNotEmpty()) {
                longPressTriggered = true
                showLongPressPopup(this, binds)
                lpSelectedIndex = 0
                updateLongPressHighlight()
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

                    if (label !in nonBindable) {
                        mainHandler.postDelayed(longPressRunnable, longPressTimeout)
                    }

                    inputController.handleTouch(v as TextView, e)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - startX
                    val dy = e.rawY - startY
                    val absDx = kotlin.math.abs(dx)
                    val absDy = kotlin.math.abs(dy)

                    // swipe ima prioritet nad long press popupom
                    if (!longPressTriggered && absDx > dp(20) && absDx > absDy * 1.05f) {
                        mainHandler.removeCallbacks(longPressRunnable)
                        hideLongPressPopup()
                    }

                    if (longPressTriggered) {
                        if (absDx > absDy && absDx > step) {
                            moveLpSelection(if (dx > 0) 1 else -1, 0)
                            startX = e.rawX
                            startY = e.rawY
                        } else if (absDy > step) {
                            moveLpSelection(0, if (dy > 0) 1 else -1)
                            startX = e.rawX
                            startY = e.rawY
                        } else {
                            if (lpRects.isNotEmpty()) {
                                val rx = e.rawX.toInt()
                                val ry = e.rawY.toInt()
                                val newIdx = lpRects.indexOfFirst { it.contains(rx, ry) }
                                if (newIdx != -1 && newIdx != lpSelectedIndex) {
                                    lpSelectedIndex = newIdx
                                    updateLongPressHighlight()
                                }
                            }
                        }
                    }

                    // uvijek proslijedi controlleru
                    inputController.handleTouch(v as TextView, e)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)

                    if (longPressTriggered) {
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

        inner class ViewHolder(val button: Button) : RecyclerView.ViewHolder(button) {
            fun bind(char: String, isSelected: Boolean) {
                button.text = char
                button.setBackgroundColor(
                    if (isSelected) 0xFFFFCC80.toInt() else 0x00000000
                )

                button.setOnClickListener {
                    val old = selectedPos
                    val newPos = bindingAdapterPosition
                    if (newPos == RecyclerView.NO_POSITION) return@setOnClickListener

                    selectedPos = newPos
                    if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
                    notifyItemChanged(selectedPos)

                    onItemClick(char)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val btn = Button(parent.context).apply {
                isAllCaps = false
                textSize = 18f
                setPadding(16, 16, 16, 16)
            }
            return ViewHolder(btn)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], position == selectedPos)
        }

        override fun getItemCount(): Int = items.size
    }
}