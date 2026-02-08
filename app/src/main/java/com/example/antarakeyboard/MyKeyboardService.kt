package com.example.antarakeyboard

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.*
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.util.Log

class MyKeyboardService : InputMethodService() {

    /* ───────── STATE ───────── */

    private var isShifted = false
    private var isNumericMode = false
    private var isDrawing = false
    private var bindMode = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private var isContinuousDelete = false
    private var continuousDeleteRunnable: Runnable? = null

    private var lastDeletedText = ""
    private var restoreIndex = 0

    private var swipeStartX = 0f
    private var swipeStartY = 0f

    private lateinit var rootView: View
    private lateinit var keyboardContainer: LinearLayout

    /* ───────── LAYOUT ───────── */

    private val row1 = "WETZIO"
    private val row2 = "QARGULP"
    private val row4 = "YSDNMJK"
    private val row5 = "XCVB"

    /* ───────── LIFECYCLE ───────── */

    override fun onCreateInputView(): View {
        rootView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardContainer = rootView.findViewById(R.id.keyboardContainer)

        rootView.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30)
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            else insets.systemWindowInsetBottom
            v.setPadding(0, 0, 0, bottom)
            insets
        }

        redrawKeyboard()
        return rootView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        setExtractViewShown(false)
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onCreateExtractTextView(): View? = null

    /* ───────── BIND DIALOG ───────── */

    private fun openBindDialog(key: String) {
        SpecialCharsDialog(this) { selected ->
            KeyBindingStore.bind(this, key, selected)
        }.show()
    }

    private fun showSpecialCharacters(key: String) {
        val bound = KeyBindingStore.getBindings(this, key)
        if (bound.isEmpty()) return

        SpecialCharsDialog(this) { char ->
            currentInputConnection?.commitText(char, 1)
        }.show()
    }

    /* ───────── HELPERS ───────── */

    private fun isPortrait() =
        resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun keyHeight(): Int =
        ((resources.displayMetrics.heightPixels *
                if (isPortrait()) 0.07 else 0.05) * 1.3).toInt()

    private fun numericKeyHeight(): Int =
        ((resources.displayMetrics.heightPixels *
                if (isPortrait()) 0.065 else 0.045) * 1.3).toInt()

    /* ───────── REDRAW ───────── */

    private fun redrawKeyboard() {
        if (isDrawing) return
        isDrawing = true

        mainHandler.post {
            keyboardContainer.removeAllViews()
            if (isNumericMode) drawNumeric() else drawAlphabet()
            isDrawing = false
        }
    }

    /* ───────── DRAW ───────── */

    private fun drawAlphabet() {
        val h = keyHeight()
        addLetterRow(row1, true, h)
        addLetterRow(row2, false, h)
        addSpecialRow(h)
        addLetterRow(row4, false, h)
        addBottomRow(h)
    }

    private fun drawNumeric() {
        val h = numericKeyHeight()
        addNumericRow(arrayOf("^","1","2","3","4","<"), 0.5f, h)
        addNumericRow(arrayOf("€","+","5","6","7","(",")"), 0.25f, h)
        addSpecialRow(h)
        addNumericRow(arrayOf("§","-","_","0","*","/","¿"), 0.25f, h)
        addNumericBottomRow(h)
    }

    /* ───────── BUTTON ───────── */

    private fun createButton(text: String): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = if (isPortrait()) 16f else 14f
            setPadding(6, 10, 6, 10)

            setOnTouchListener { v, e -> handleTouch(v as Button, e) }

            setOnLongClickListener {
                if (bindMode) {
                    openBindDialog(text)
                } else {
                    showSpecialCharacters(text)
                }
                true
            }
        }

    /* ───────── TOUCH ───────── */

    private fun handleTouch(button: Button, e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = e.x
                swipeStartY = e.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                val dx = e.x - swipeStartX
                val dy = e.y - swipeStartY

                when {
                    dy < -60 -> currentInputConnection?.commitText(button.text.toString().uppercase(), 1)
                    dx < -80 -> startContinuousDelete()
                    dx > 80 -> restoreText()
                    else -> handleClick(button.text.toString())
                }
                stopContinuousDelete()
                return true
            }
        }
        return false
    }

    /* ───────── CLICK ───────── */

    private fun handleClick(text: String) {
        when (text) {
            "⇧" -> { isShifted = !isShifted; redrawKeyboard() }
            "123" -> { isNumericMode = true; redrawKeyboard() }
            "ABC" -> { isNumericMode = false; redrawKeyboard() }
            "⌫","<" -> currentInputConnection?.deleteSurroundingText(1,0)
            "↵" -> currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            )
            " " -> currentInputConnection?.commitText(" ",1)
            else -> {
                val out =
                    if (text.length == 1 && text[0].isLetter())
                        if (isShifted) text.uppercase() else text.lowercase()
                    else text
                currentInputConnection?.commitText(out, 1)
            }
        }
    }

    /* ───────── DELETE / RESTORE ───────── */

    private fun startContinuousDelete() {
        if (isContinuousDelete) return
        isContinuousDelete = true

        continuousDeleteRunnable = object : Runnable {
            override fun run() {
                if (!isContinuousDelete) return
                currentInputConnection?.deleteSurroundingText(1,0)
                mainHandler.postDelayed(this, 40)
            }
        }
        mainHandler.post(continuousDeleteRunnable!!)
    }

    private fun stopContinuousDelete() {
        isContinuousDelete = false
        continuousDeleteRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    private fun restoreText() {
        if (lastDeletedText.isEmpty()) return
        restoreIndex = lastDeletedText.length

        mainHandler.post(object : Runnable {
            override fun run() {
                if (restoreIndex <= 0) return
                currentInputConnection?.commitText(
                    lastDeletedText[--restoreIndex].toString(), 1
                )
                mainHandler.postDelayed(this, 50)
            }
        })
    }

    /* ───────── ROWS ───────── */

    private fun addLetterRow(letters: String, center: Boolean, h: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (center) row.addView(View(this), LinearLayout.LayoutParams(0,0,1f))
        letters.forEach {
            row.addView(createButton(it.toString()),
                LinearLayout.LayoutParams(0,h,1f))
        }
        keyboardContainer.addView(row)
    }

    private fun addSpecialRow(h: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("⇧","."," ","f","h"," ","?","⌫").forEach {
            row.addView(createButton(it),
                LinearLayout.LayoutParams(0,h,
                    if (it=="⇧"||it=="⌫") 0.65f else 1f))
        }
        keyboardContainer.addView(row)
    }

    private fun addBottomRow(h: Int) {
        val row = LinearLayout(this)
        row.addView(View(this), LinearLayout.LayoutParams(0,0,0.5f))
        row5.forEach {
            row.addView(createButton(it.toString()),
                LinearLayout.LayoutParams(0,h,1f))
        }
        row.addView(createButton("123"), LinearLayout.LayoutParams(0,h,2f))
        row.addView(createButton("↵"), LinearLayout.LayoutParams(0,h,1f))
        row.addView(View(this), LinearLayout.LayoutParams(0,0,0.5f))
        keyboardContainer.addView(row)
    }

    private fun addNumericRow(keys: Array<String>, side: Float, h: Int) {
        val row = LinearLayout(this)
        row.addView(View(this), LinearLayout.LayoutParams(0,0,side))
        keys.forEach {
            row.addView(createButton(it),
                LinearLayout.LayoutParams(0,h,1f))
        }
        row.addView(View(this), LinearLayout.LayoutParams(0,0,side))
        keyboardContainer.addView(row)
    }

    private fun addNumericBottomRow(h: Int) {
        val row = LinearLayout(this)
        row.addView(View(this), LinearLayout.LayoutParams(0,0,0.5f))
        listOf("&","%","@","#").forEach {
            row.addView(createButton(it),
                LinearLayout.LayoutParams(0,h,1f))
        }
        row.addView(createButton("ABC"), LinearLayout.LayoutParams(0,h,2f))
        keyboardContainer.addView(row)
    }
}
