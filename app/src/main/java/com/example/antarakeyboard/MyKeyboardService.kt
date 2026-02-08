package com.example.antarakeyboard

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.*
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.antarakeyboard.KeyboardConfig
import com.example.antarakeyboard.myDefaultKeyboardConfig
import com.example.antarakeyboard.myDefaultNumericConfig


class MyKeyboardService : InputMethodService() {

    /* ───────── STATE ───────── */

    private var isShifted = false
    private var isNumericMode = false
    private var isDrawing = false
    private var currentKeyboardConfig: KeyboardConfig = myDefaultKeyboardConfig


    private val mainHandler = Handler(Looper.getMainLooper())

    private var isContinuousDelete = false
    private var continuousDeleteRunnable: Runnable? = null

    private var lastDeletedText = ""
    private var restoreIndex = 0

    private var swipeStartX = 0f
    private var swipeStartY = 0f

    private var previewPopup: PopupWindow? = null

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

    /* ───────── PREVIEW ───────── */

    private fun showKeyPreview(button: Button) {
        hideKeyPreview()

        val tv = TextView(this).apply {
            text = button.text
            textSize = 28f
            setPadding(24, 16, 24, 16)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }

        tv.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )

        previewPopup = PopupWindow(
            tv,
            tv.measuredWidth,
            tv.measuredHeight,
            false
        )

        val loc = IntArray(2)
        button.getLocationOnScreen(loc)

        previewPopup?.showAtLocation(
            rootView,
            Gravity.NO_GRAVITY,
            loc[0] + button.width / 2 - tv.measuredWidth / 2,
            loc[1] - tv.measuredHeight - 20
        )
    }

    private fun hideKeyPreview() {
        previewPopup?.dismiss()
        previewPopup = null
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

            // Dodaj lijeve specijalne tipke (fiksirane lijevo)
            val specialLeftRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            currentKeyboardConfig.specialLeft.forEach { keyConfig ->
                specialLeftRow.addView(createButton(keyConfig.label),
                    LinearLayout.LayoutParams(0, keyHeight(), 1f))
            }
            keyboardContainer.addView(specialLeftRow)

            // Dodaj redove tipki
            currentKeyboardConfig.rows.forEach { rowConfig ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                rowConfig.keys.forEach { keyConfig ->
                    row.addView(createButton(keyConfig.label),
                        LinearLayout.LayoutParams(0, keyHeight(), 1f))
                }
                keyboardContainer.addView(row)
            }

            // Dodaj desne specijalne tipke (fiksirane desno)
            val specialRightRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            currentKeyboardConfig.specialRight.forEach { keyConfig ->
                specialRightRow.addView(createButton(keyConfig.label),
                    LinearLayout.LayoutParams(0, keyHeight(), 1f))
            }
            keyboardContainer.addView(specialRightRow)

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
                val key = text.toString()
                val bindings = KeyBindingStore.getBindings(context, key)

                if (bindings.isEmpty()) {
                    // Nema bindinga, otvori bind dijalog za dodavanje
                    openBindLongPressDialog(key)
                } else {
                    // Ima bindinga, otvori popup s izborom za unošenje
                    SpecialCharsDialog(context, bindings) { selectedChar ->
                        currentInputConnection?.commitText(selectedChar, 1)
                    }.show()
                }
                true
            }
        }

    private fun openBindLongPressDialog(key: String) {
        val dialog = BindLongPressDialog(this, key) { selectedKey, selectedChar ->
            // Spremi vezu između key i selectedChar u svoj storage
            Toast.makeText(this, "Bind added: $selectedKey -> $selectedChar", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }



    /* ───────── TOUCH ───────── */

    private fun handleTouch(button: Button, e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = e.x
                swipeStartY = e.y

                val text = button.text.toString()
                if (text != "⇧" && text != " " && text != "123" && text != "↵" && text != "⌫" && text != "<") {
                    showKeyPreview(button)
                }

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
                hideKeyPreview()
                stopContinuousDelete()
                return true
            }
        }
        return false
    }

    /* ───────── CLICK ───────── */

    private fun handleClick(text: String) {
        when (text) {
            "123" -> {
                currentKeyboardConfig = myDefaultNumericConfig
                redrawKeyboard()
            }
            "ABC" -> {
                currentKeyboardConfig = myDefaultKeyboardConfig
                redrawKeyboard()
            }
            // ... ostali slučajevi (Shift, Backspace, slova, space, enter)
            "⇧" -> {
                isShifted = !isShifted
                redrawKeyboard()
            }
            "⌫" -> currentInputConnection?.deleteSurroundingText(1, 0)
            "↵" -> currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            " " -> currentInputConnection?.commitText(" ", 1)
            else -> {
                val out = if (text.length == 1 && text[0].isLetter())
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
                currentInputConnection?.deleteSurroundingText(1, 0)
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
        if (center) row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        letters.forEach {
            row.addView(createButton(it.toString()),
                LinearLayout.LayoutParams(0, h, 1f))
        }
        keyboardContainer.addView(row)
    }

    private fun addSpecialRow(h: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("⇧", ".", " ", "f", "h", " ", "?", "⌫").forEach {
            row.addView(createButton(it),
                LinearLayout.LayoutParams(0, h,
                    if (it == "⇧" || it == "⌫") 0.65f else 1f))
        }
        keyboardContainer.addView(row)
    }

    private fun addBottomRow(h: Int) {
        val row = LinearLayout(this)
        row.addView(View(this), LinearLayout.LayoutParams(0, 0, 0.5f))
        row5.forEach {
            row.addView(createButton(it.toString()),
                LinearLayout.LayoutParams(0, h, 1f))
        }
        row.addView(createButton("123"), LinearLayout.LayoutParams(0, h, 2f))
        row.addView(createButton("↵"), LinearLayout.LayoutParams(0, h, 1f))
        row.addView(createButton("Bind LP"), LinearLayout.LayoutParams(0, h, 2f)) // Tipka za bind long press
        row.addView(View(this), LinearLayout.LayoutParams(0, 0, 0.5f))
        keyboardContainer.addView(row)
    }

    private fun addNumericRow(keys: Array<String>, side: Float, h: Int) {
        val row = LinearLayout(this)
        row.addView(View(this), LinearLayout.LayoutParams(0, 0, side))
        keys.forEach {
            row.addView(createButton(it),
                LinearLayout.LayoutParams(0, h, 1f))
        }
        row.addView(View(this), LinearLayout.LayoutParams(0, 0, side))
        keyboardContainer.addView(row)
    }

    private fun addNumericBottomRow(h: Int) {
        val row = LinearLayout(this)
        row.addView(View(this), LinearLayout.LayoutParams(0, 0, 0.5f))
        listOf("&", "%", "@", "#").forEach {
            row.addView(createButton(it),
                LinearLayout.LayoutParams(0, h, 1f))
        }
        row.addView(createButton("ABC"), LinearLayout.LayoutParams(0, h, 2f))
        keyboardContainer.addView(row)
    }

    /* ───────── INNER CLASSES ───────── */

    class BindLongPressDialog(
        context: Context,
        private val currentKey: String,
        private val onBindSelected: (String, String) -> Unit
    ) : Dialog(context) {

        private lateinit var specialCharRecycler: RecyclerView
        private lateinit var saveButton: Button

        private val specialChars = SpecialChars.ALL
        private var selectedSpecialChar: String? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.dialog_bind_long_press)

            saveButton = findViewById(R.id.saveBindButton)
            saveButton.isEnabled = false

            specialCharRecycler = findViewById(R.id.specialCharRecycler)
            specialCharRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            specialCharRecycler.adapter = CharSelectorAdapter(specialChars) { char ->
                selectedSpecialChar = char
                saveButton.isEnabled = true
            }

            saveButton.setOnClickListener {
                selectedSpecialChar?.let { char ->
                    onBindSelected(currentKey, char)
                    dismiss()
                }
            }
        }

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
                        selectedPos = adapterPosition
                        notifyItemChanged(old)
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

            override fun getItemCount() = items.size
        }
    }



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
                    selectedPos = adapterPosition
                    notifyItemChanged(old)
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

        override fun getItemCount() = items.size
    }
}
