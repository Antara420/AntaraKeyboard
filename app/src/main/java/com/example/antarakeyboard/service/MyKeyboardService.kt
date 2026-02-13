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
import androidx.recyclerview.widget.RecyclerView
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.addLongPress
import com.example.antarakeyboard.model.findKey
import com.example.antarakeyboard.model.myDefaultKeyboardConfig
import com.example.antarakeyboard.ui.BindLongPressDialog
import com.example.antarakeyboard.ui.KeyView
import com.example.antarakeyboard.ui.SpecialCharsDialog
import kotlin.math.abs

class MyKeyboardService : InputMethodService() {

    /* ───────── STATE ───────── */
    private var isShifted = false
    private var isDrawing = false

    private var currentKeyboardConfig: KeyboardConfig = myDefaultKeyboardConfig
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

    /* ───────── PLACEHOLDER FOR NUMERIC CONFIG ───────── */
    private val myDefaultNumericConfig: KeyboardConfig
        get() = myDefaultKeyboardConfig // TODO: zamijeni stvarnim numeric layoutom

    /* ───────── LIFECYCLE ───────── */
    override fun onCreateInputView(): View {
        rootView = layoutInflater.inflate(R.layout.keyboard_view, null)

        val container = rootView.findViewById<LinearLayout?>(R.id.keyboardContainer)
            ?: throw IllegalStateException("keyboard_view.xml nema LinearLayout s id=keyboardContainer")
        keyboardContainer = container

        // load prefs
        currentKeyboardConfig = KeyboardPrefs.loadLayout(this)
        alphabetLayout = currentKeyboardConfig
        currentShape = KeyboardPrefs.getShape(this)

        redrawKeyboard()
        return rootView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        setExtractViewShown(false)
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onCreateExtractTextView(): View? = null

    /* ───────── HELPERS ───────── */
    private fun isPortrait() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    // visina tipke = baza * user scale
    private fun keyHeight(): Int {
        val base = (resources.displayMetrics.heightPixels * if (isPortrait()) 0.07 else 0.05).toInt()
        val scale = KeyboardPrefs.getScale(this).coerceIn(0.7f, 1.7f)
        return (base * scale).toInt().coerceAtLeast(dp(36))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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

    /* ───────── REDRAW ───────── */
    private fun redrawKeyboard() {
        if (isDrawing) return
        isDrawing = true

        mainHandler.post {
            keyboardContainer.removeAllViews()

            // HEX honeycomb: overlap + stagger
            val overlap = if (currentShape == KeyShape.HEX) dp(12) else 0

            // Left special row (bez staggera)
            val specialLeftRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            currentKeyboardConfig.specialLeft.forEach { key ->
                specialLeftRow.addView(
                    createKey(key.label),
                    LinearLayout.LayoutParams(0, keyHeight(), 1f)
                )
            }
            keyboardContainer.addView(specialLeftRow)

            // Rows
            currentKeyboardConfig.rows.forEachIndexed { idx, rowConfig ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

                // HEX: svaki drugi red offset “pola tipke”
                val stagger = (currentShape == KeyShape.HEX && idx % 2 == 1)
                if (stagger) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, 0, 0.5f))
                }

                rowConfig.keys.forEach { key ->
                    row.addView(
                        createKey(key.label),
                        LinearLayout.LayoutParams(0, keyHeight(), 1f)
                    )
                }

                if (stagger) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, 0, 0.5f))
                }

                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                if (idx > 0 && overlap > 0) lp.topMargin = -overlap

                keyboardContainer.addView(row, lp)
            }

            // Right special row (bez staggera)
            val specialRightRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            currentKeyboardConfig.specialRight.forEach { key ->
                specialRightRow.addView(
                    createKey(key.label),
                    LinearLayout.LayoutParams(0, keyHeight(), 1f)
                )
            }
            keyboardContainer.addView(specialRightRow)

            isDrawing = false
        }
    }

    /* ───────── KEY VIEW ───────── */
    private fun createKey(text: String): KeyView = KeyView(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(0xFFFFFFFF.toInt())
        textSize = if (isPortrait()) 18f else 16f
        gravity = android.view.Gravity.CENTER

        // shape (HEX/TRI/CIRCLE/CUBE)
        shape = currentShape

        // “special” naglasi (po želji)
        isSpecial = text == "↵"

        // touch
        setOnTouchListener { v, e -> handleTouch(v as TextView, e) }

        // long press: samo za normalne tipke iz rows
        setOnLongClickListener {
            val keyConfig = currentKeyboardConfig.findKey(text) ?: return@setOnLongClickListener false
            val binds = keyConfig.longPressBindings
            if (binds.isEmpty()) openBindLongPressDialog()
            else SpecialCharsDialog(context, binds) { currentInputConnection?.commitText(it, 1) }.show()
            true
        }
    }

    private fun openBindLongPressDialog() {
        BindLongPressDialog(this, currentKeyboardConfig) { key, char ->
            currentKeyboardConfig.addLongPress(key, char)
            KeyboardPrefs.saveLayout(this, currentKeyboardConfig)
            Toast.makeText(this, "Bind: $key → $char", Toast.LENGTH_SHORT).show()
        }.show()
    }

    /* ───────── TOUCH / CLICK ───────── */
    private fun handleTouch(view: TextView, e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = e.x
                swipeStartY = e.y

                val t = view.text.toString()
                // ne pokazuj preview za specijalne
                if (t !in listOf("⇧", " ", "123", "↵", "⌫", "ABC")) {
                    showKeyPreview(view)
                }
                // radi pressed state (KeyView će promijenit boju)
                view.isPressed = true
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = e.x - swipeStartX
                val dy = e.y - swipeStartY

                view.isPressed = false
                hideKeyPreview()

                // swipe up: force uppercase (samo za slova)
                val text = view.text.toString()
                when {
                    dy < -60 && text.length == 1 && text[0].isLetter() -> {
                        currentInputConnection?.commitText(text.uppercase(), 1)
                        return true
                    }
                    else -> {
                        handleClick(text)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun handleClick(text: String) {
        when (text) {
            "123" -> {
                alphabetLayout = currentKeyboardConfig
                currentKeyboardConfig = myDefaultNumericConfig
                redrawKeyboard()
            }
            "ABC" -> {
                currentKeyboardConfig = alphabetLayout ?: KeyboardPrefs.loadLayout(this)
                redrawKeyboard()
            }
            "⇧" -> {
                isShifted = !isShifted
                // (ako želiš da se tipke vizualno promijene, možemo to kasnije dodati)
            }
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

    /* ───────── INNER CLASSES (ako ti treba negdje) ───────── */
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
