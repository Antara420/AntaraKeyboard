package com.example.antarakeyboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.GridLayout

class MyKeyboardService : InputMethodService() {

    private var isShifted = false
    private val backspaceHandler = android.os.Handler()
    private var backspaceRunnable: Runnable? = null


    override fun onCreateInputView(): View {
        Log.d("ANTARA_KB", "onCreateInputView called")

        // Inflate layout
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)

        // Podešavanje paddinga za navigacijske tipke (API 30+ i fallback za niže)
        view.setOnApplyWindowInsetsListener { v, insets ->
            val navBarHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }

            Log.d("ANTARA_KB", "Nav bar height: $navBarHeight")

            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                navBarHeight
            )
            insets
        }

        val grid = view.findViewById<GridLayout>(R.id.keyboardGrid)

        fun addLetterButtons() {
            grid.removeAllViews()

            val letters = ('A'..'Z')

            // Dodaj slova
            for (letter in letters) {
                val button = Button(this).apply {
                    text = if (isShifted) letter.toString() else letter.lowercase()
                    textSize = 16f
                    isSingleLine = true
                    ellipsize = null
                    isAllCaps = false

                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0

                    setOnClickListener {
                        val ic = currentInputConnection
                        if (ic == null) {
                            Log.w("ANTARA_KB", "InputConnection je null")
                            return@setOnClickListener
                        }
                        val toCommit = if (isShifted) letter.toString() else letter.lowercase()
                        Log.d("ANTARA_KB", "Tipka pritisnuta: $toCommit")
                        ic.commitText(toCommit, 1)
                    }
                }

                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(3, 3, 3, 3)
                }

                button.layoutParams = params
                grid.addView(button)
            }

            // Shift tipka
            val shiftButton = Button(this).apply {
                text = if (isShifted) "⬇\uFE0F" else "⬆\uFE0F  "
                textSize = 14f
                isSingleLine = true
                ellipsize = null
                isAllCaps = false

                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0

                setOnClickListener {
                    isShifted = !isShifted
                    Log.d("ANTARA_KB", "Shift toggled: $isShifted")
                    addLetterButtons() // osvježi tipke s novim stanjem shift-a
                }
            }

            val shiftParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(3, 3, 3, 3)
            }
            shiftButton.layoutParams = shiftParams
            grid.addView(shiftButton)

            // Dvije space tipke iste veličine kao ostali gumbi
            repeat(2) {
                val spaceButton = Button(this).apply {
                    text = " "
                    textSize = 16f
                    isSingleLine = true
                    ellipsize = null
                    isAllCaps = false

                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0

                    setOnClickListener {
                        val ic = currentInputConnection
                        if (ic == null) {
                            Log.w("ANTARA_KB", "InputConnection je null (SPACE)")
                            return@setOnClickListener
                        }
                        Log.d("ANTARA_KB", "SPACE pritisnut")
                        ic.commitText(" ", 1)
                    }
                }

                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4, 4, 4, 4)
                }

                spaceButton.layoutParams = params
                grid.addView(spaceButton)
            }

            // Backspace
            val backspaceButton = Button(this).apply {
                text = "⌫"
                textSize = 18f
                isSingleLine = true
                ellipsize = null
                isAllCaps = false

                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0

                // Jedan klik briše jedno slovo
                setOnClickListener {
                    val ic = currentInputConnection
                    if (ic == null) {
                        Log.w("ANTARA_KB", "InputConnection null (BACKSPACE)")
                        return@setOnClickListener
                    }
                    Log.d("ANTARA_KB", "BACKSPACE pritisnut")
                    ic.deleteSurroundingText(1, 0)
                }

                // Long click započne brisanje ponavljanjem
                setOnLongClickListener {
                    val ic = currentInputConnection ?: return@setOnLongClickListener false
                    Log.d("ANTARA_KB", "BACKSPACE long pressed - start repeating")

                    backspaceRunnable = object : Runnable {
                        override fun run() {
                            ic.deleteSurroundingText(1, 0)
                            backspaceHandler.postDelayed(this, 50) // briši svakih 50ms dok se drži
                        }
                    }
                    backspaceHandler.post(backspaceRunnable!!)
                    true
                }

                // Kad se otpusti pritisak na gumb, zaustavi ponavljanje
                setOnTouchListener { v, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP ||
                        event.action == android.view.MotionEvent.ACTION_CANCEL) {
                        Log.d("ANTARA_KB", "BACKSPACE released - stop repeating")
                        backspaceRunnable?.let { backspaceHandler.removeCallbacks(it) }
                        backspaceRunnable = null
                    }
                    false
                }
            }

            val backspaceParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }

            backspaceButton.layoutParams = backspaceParams
            grid.addView(backspaceButton)


            // Enter
            val enterButton = Button(this).apply {
                text = "↵"
                textSize = 18f
                isSingleLine = true
                ellipsize = null
                isAllCaps = false

                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0

                setOnClickListener {
                    val ic = currentInputConnection
                    if (ic == null) {
                        Log.w("ANTARA_KB", "InputConnection null (ENTER)")
                        return@setOnClickListener
                    }
                    Log.d("ANTARA_KB", "ENTER pritisnut")
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
            }

            val enterParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }

            enterButton.layoutParams = enterParams
            grid.addView(enterButton)
        }

        addLetterButtons()

        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d("ANTARA_KB", "onStartInputView called, restarting=$restarting")
    }
}
