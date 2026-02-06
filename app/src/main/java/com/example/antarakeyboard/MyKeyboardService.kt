package com.example.antarakeyboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout

class MyKeyboardService : InputMethodService() {

    private var isShifted = false
    private var isNumericMode = false
    private var isHexagonMode = false // Opcija za heksagone

    private val backspaceHandler = Handler()
    private var backspaceRunnable: Runnable? = null

    private lateinit var rootView: View
    private lateinit var keyboardContainer: LinearLayout

    // Redovi slova prema tvom zahtjevu
    private val row1Letters = "WETZIO"
    private val row2Letters = "QARGULP"
    private val row4Letters = "YSDNMJK"
    private val row5Letters = "XCVB"

    override fun onCreateInputView(): View {
        rootView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardContainer = rootView.findViewById(R.id.keyboardContainer)

        rootView.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            v.setPadding(0, 0, 0, bottom)
            insets
        }

        redrawKeyboard()
        return rootView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        setExtractViewShown(false)
        Log.d("ANTARA_KB", "Input started")
    }

    override fun onCreateExtractTextView(): View? = null

    override fun onEvaluateFullscreenMode() = false

    private fun redrawKeyboard() {
        keyboardContainer.removeAllViews()

        if (isNumericMode) {
            drawNumericKeyboard()
        } else {
            drawAlphabetKeyboard()
        }
    }

    private fun drawAlphabetKeyboard() {
        // Prvi red: W E T Z I O
        addLetterRow(row1Letters, center = true)

        // Drugi red: Q A R G U L P
        addLetterRow(row2Letters, center = false)

        // Treći red: shift . space f h ? backspace
        addSpecialRow()

        // Četvrti red: Y S D N M J K
        addLetterRow(row4Letters, center = false)

        // Peti red: X C V B tipka za brojeve + enter
        addBottomRow()
    }

    private fun drawNumericKeyboard() {
        // Numerička tipkovnica
        val numericRows = arrayOf(
            "123",
            "456",
            "789",
            "0.,"
        )

        for (row in numericRows) {
            addNumericRow(row)
        }

        // Dodaj nazad na alfabet
        val backToAlphaBtn = createButton("ABC").apply {
            setOnClickListener {
                isNumericMode = false
                redrawKeyboard()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 5, 0, 5)
                height = 80 // Veća visina
            }
        }
        keyboardContainer.addView(backToAlphaBtn)
    }

    private fun createButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f // Manji tekst
            setPadding(0, 0, 0, 0)

            // Postavi background ovisno o modu
            if (isHexagonMode && text.length == 1 && text[0].isLetter()) {
                // Za heksagone možemo koristiti custom drawable
                // Za sada koristimo obični zaobljeni oblik
                setBackgroundResource(R.drawable.button_background)
            } else {
                setBackgroundResource(R.drawable.button_background)
            }
        }
    }

    private fun addLetterRow(letters: String, center: Boolean) {
        val rowLayout = LinearLayout(this)
        rowLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowLayout.orientation = LinearLayout.HORIZONTAL

        // Ako treba centrirati, dodaj prazni prostor
        if (center) {
            val spacer = View(this)
            spacer.layoutParams = LinearLayout.LayoutParams(
                0,
                0,
                1f
            )
            rowLayout.addView(spacer)
        }

        for (c in letters) {
            val btn = createButton(if (isShifted) c.toString() else c.lowercase()).apply {
                setOnClickListener {
                    currentInputConnection?.commitText(text.toString(), 1)
                    // Automatski isključi shift nakon tipkanja jednog slova
                    if (isShifted) {
                        isShifted = false
                        redrawKeyboard()
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(3, 3, 3, 3)
                    height = 70 // Veća visina za tipke
                    minimumHeight = 70
                }
            }
            rowLayout.addView(btn)
        }

        keyboardContainer.addView(rowLayout)
    }

    private fun addSpecialRow() {
        val rowLayout = LinearLayout(this)
        rowLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowLayout.orientation = LinearLayout.HORIZONTAL

        // Shift tipka (1.5x širine)
        val shiftBtn = createButton("⇧").apply {
            setOnClickListener {
                isShifted = !isShifted
                redrawKeyboard()
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.5f
            ).apply {
                setMargins(3, 3, 3, 3)
                height = 70
                minimumHeight = 70
            }
        }
        rowLayout.addView(shiftBtn)

        // Točka (.)
        val dotBtn = createButton(".").apply {
            setOnClickListener {
                currentInputConnection?.commitText(".", 1)
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(3, 3, 3, 3)
                height = 70
                minimumHeight = 70
            }
        }
        rowLayout.addView(dotBtn)

        // Space (2x širine)
        val spaceBtn = createButton(" ").apply {
            setOnClickListener {
                currentInputConnection?.commitText(" ", 1)
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                2f
            ).apply {
                setMargins(3, 3, 3, 3)
                height = 70
                minimumHeight = 70
            }
        }
        rowLayout.addView(spaceBtn)

        // F
        val fBtn = createButton(if (isShifted) "F" else "f").apply {
            setOnClickListener {
                currentInputConnection?.commitText(text.toString(), 1)
                if (isShifted) {
                    isShifted = false
                    redrawKeyboard()
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(3, 3, 3, 3)
                height = 70
                minimumHeight = 70
            }
        }
        rowLayout.addView(fBtn)

        // H
        val hBtn = createButton(if (isShifted) "H" else "h").apply {
            setOnClickListener {
                currentInputConnection?.commitText(text.toString(), 1)
                if (isShifted) {
                    isShifted = false
                    redrawKeyboard()
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(3, 3, 3, 3)
                height = 70
                minimumHeight = 70
            }
        }
        rowLayout.addView(hBtn)

        // Space (1x širine)
        val spaceBtn2 = createButton(" ").apply {
            setOnClickListener {
                currentInputConnection?.commitText(" ", 1)
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(3, 3, 3, 3)
                height = 70
                minimumHeight = 70
            }
        }
        rowLayout.addView(spaceBtn2)

        // Upitnik (?)
        val questionBtn = createButton("?").apply {
            setOnClickListener {
                currentInputConnection?.commitText("?", 1)
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(3, 3, 3, 3)
                height = 70
                minimumHeight = 70
            }
        }
        rowLayout.addView(questionBtn)

        // Backspace
        val backspaceBtn = createButton("⌫").apply {
            setOnClickListener {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }

            setOnLongClickListener {
                backspaceRunnable = object : Runnable {
                    override fun run() {
                        currentInputConnection?.deleteSurroundingText(1, 0)
                        backspaceHandler.postDelayed(this, 50)
                    }
                }
                backspaceHandler.post(backspaceRunnable!!)
                true
            }

            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                    backspaceRunnable?.let { backspaceHandler.removeCallbacks(it) }
                    backspaceRunnable = null
                }
                false
            }

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(3, 3, 3, 3)
                height = 70
                minimumHeight = 70
            }
        }
        rowLayout.addView(backspaceBtn)

        keyboardContainer.addView(rowLayout)
    }

    private fun addBottomRow() {
        val rowLayout = LinearLayout(this)
        rowLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowLayout.orientation = LinearLayout.HORIZONTAL

        // Prazan prostor na početku
        val spacer = View(this)
        spacer.layoutParams = LinearLayout.LayoutParams(
            0,
            0,
            0.5f
        )
        rowLayout.addView(spacer)

        // X C V B (4 tipke)
        for (c in row5Letters) {
            val btn = createButton(if (isShifted) c.toString() else c.lowercase()).apply {
                setOnClickListener {
                    currentInputConnection?.commitText(text.toString(), 1)
                    if (isShifted) {
                        isShifted = false
                        redrawKeyboard()
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(3, 3, 3, 3)
                    height = 70
                    minimumHeight = 70
                }
            }
            rowLayout.addView(btn)
        }

        // Tipka za brojeve (3.5 širine)
        val numericBtn = createButton("123").apply {
            setOnClickListener {
                isNumericMode = true
                redrawKeyboard()
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                3.5f
            ).apply {
                setMargins(3, 3, 3, 3)
                height = 70
                minimumHeight = 70
            }
        }
        rowLayout.addView(numericBtn)

        // Enter tipka (1.5 širine)
        val enterBtn = createButton("↵").apply {
            setOnClickListener {
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
                )
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
                )
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.5f
            ).apply {
                setMargins(3, 3, 3, 3)
                height = 70
                minimumHeight = 70
            }
        }
        rowLayout.addView(enterBtn)

        // Prazan prostor na kraju
        val spacerEnd = View(this)
        spacerEnd.layoutParams = LinearLayout.LayoutParams(
            0,
            0,
            0.5f
        )
        rowLayout.addView(spacerEnd)

        keyboardContainer.addView(rowLayout)
    }

    private fun addNumericRow(numbers: String) {
        val rowLayout = LinearLayout(this)
        rowLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowLayout.orientation = LinearLayout.HORIZONTAL

        // Dodaj praznine s obje strane za centriranje
        for (i in 0..2) {
            val spacer = View(this)
            spacer.layoutParams = LinearLayout.LayoutParams(
                0,
                0,
                1f
            )
            rowLayout.addView(spacer)
        }

        for (c in numbers) {
            val btn = createButton(c.toString()).apply {
                setOnClickListener {
                    currentInputConnection?.commitText(text.toString(), 1)
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(3, 3, 3, 3)
                    height = 70
                    minimumHeight = 70
                }
            }
            rowLayout.addView(btn)
        }

        keyboardContainer.addView(rowLayout)
    }
}