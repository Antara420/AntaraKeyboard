package com.example.antarakeyboard.service

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import android.content.res.Configuration
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.RecyclerView
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.addLongPress
import com.example.antarakeyboard.model.findKey
import com.example.antarakeyboard.model.myDefaultKeyboardConfig
import com.example.antarakeyboard.ui.SpecialCharsDialog
import com.example.antarakeyboard.ui.BindLongPressDialog

class MyKeyboardService : InputMethodService() {

    /* ───────── STATE ───────── */
    private var isShifted = false
    private var isDrawing = false
    private var currentKeyboardConfig: KeyboardConfig = myDefaultKeyboardConfig
    private var alphabetLayout: KeyboardConfig? = null

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

    /* ───────── PLACEHOLDER FOR NUMERIC CONFIG ───────── */
    private val myDefaultNumericConfig: KeyboardConfig
        get() = myDefaultKeyboardConfig // TODO: zamijeni stvarnim numeric layoutom

    /* ───────── LIFECYCLE ───────── */
    override fun onCreateInputView(): View {
        rootView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardContainer = rootView.findViewById(R.id.keyboardContainer)
        currentKeyboardConfig = KeyboardPrefs.loadLayout(this)
        alphabetLayout = currentKeyboardConfig
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
        tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        previewPopup = PopupWindow(tv, tv.measuredWidth, tv.measuredHeight, false)
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
    private fun isPortrait() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    private fun keyHeight() = ((resources.displayMetrics.heightPixels * if (isPortrait()) 0.07 else 0.05) * 1.3).toInt()
    private fun numericKeyHeight() = ((resources.displayMetrics.heightPixels * if (isPortrait()) 0.065 else 0.045) * 1.3).toInt()

    /* ───────── REDRAW ───────── */
    private fun redrawKeyboard() {
        if (isDrawing) return
        isDrawing = true
        mainHandler.post {
            keyboardContainer.removeAllViews()
            // Left special
            val specialLeftRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            currentKeyboardConfig.specialLeft.forEach { key ->
                specialLeftRow.addView(createButton(key.label), LinearLayout.LayoutParams(0, keyHeight(), 1f))
            }
            keyboardContainer.addView(specialLeftRow)
            // Rows
            currentKeyboardConfig.rows.forEach { rowConfig ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                rowConfig.keys.forEach { key -> row.addView(createButton(key.label), LinearLayout.LayoutParams(0, keyHeight(), 1f)) }
                keyboardContainer.addView(row)
            }
            // Right special
            val specialRightRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            currentKeyboardConfig.specialRight.forEach { key ->
                specialRightRow.addView(createButton(key.label), LinearLayout.LayoutParams(0, keyHeight(), 1f))
            }
            keyboardContainer.addView(specialRightRow)
            isDrawing = false
        }
    }

    /* ───────── BUTTON ───────── */
    private fun createButton(text: String) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = if (isPortrait()) 16f else 14f
        setPadding(6, 10, 6, 10)
        setOnTouchListener { v, e -> handleTouch(v as Button, e) }
        setOnLongClickListener {
            val keyConfig = currentKeyboardConfig.findKey(text)
            val binds = keyConfig?.longPressBindings ?: emptyList()
            if (binds.isEmpty()) openBindLongPressDialog()
            else SpecialCharsDialog(context, binds) { currentInputConnection?.commitText(it,1) }.show()
            true
        }

    }

    private fun openBindLongPressDialog() {
        // Otvara BindLongPressDialog sa trenutnim KeyboardConfig
        com.example.antarakeyboard.ui.BindLongPressDialog(this, currentKeyboardConfig) { key, char ->
            currentKeyboardConfig.addLongPress(key, char)           // dodaje long-press bind
            KeyboardPrefs.saveLayout(this, currentKeyboardConfig)  // sprema layout
            Toast.makeText(this, "Bind: $key → $char", Toast.LENGTH_SHORT).show() // feedback
        }.show()
    }



    /* ───────── TOUCH / CLICK ───────── */
    private fun handleTouch(button: Button, e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = e.x
                swipeStartY = e.y
                val text = button.text.toString()
                if (text !in listOf("⇧", " ", "123", "↵", "⌫", "<")) showKeyPreview(button)
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
            "⇧" -> { isShifted = !isShifted; redrawKeyboard() }
            "⌫" -> currentInputConnection?.deleteSurroundingText(1,0)
            "↵" -> currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            " " -> currentInputConnection?.commitText(" ",1)
            else -> {
                val out = if (text.length==1 && text[0].isLetter()) if(isShifted) text.uppercase() else text.lowercase() else text
                currentInputConnection?.commitText(out,1)
            }
        }
    }

    /* ───────── DELETE / RESTORE ───────── */
    private fun startContinuousDelete() {
        if (isContinuousDelete) return
        isContinuousDelete = true
        continuousDeleteRunnable = object : Runnable {
            override fun run() {
                if(!isContinuousDelete) return
                currentInputConnection?.deleteSurroundingText(1,0)
                mainHandler.postDelayed(this,40)
            }
        }
        mainHandler.post(continuousDeleteRunnable!!)
    }

    private fun stopContinuousDelete() {
        isContinuousDelete = false
        continuousDeleteRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    private fun restoreText() {
        if(lastDeletedText.isEmpty()) return
        restoreIndex = lastDeletedText.length
        mainHandler.post(object : Runnable {
            override fun run() {
                if(restoreIndex <= 0) return
                currentInputConnection?.commitText(lastDeletedText[--restoreIndex].toString(),1)
                mainHandler.postDelayed(this,50)
            }
        })
    }

    /* ───────── ROWS ───────── */
    private fun addLetterRow(letters:String, center:Boolean, h:Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if(center) row.addView(View(this), LinearLayout.LayoutParams(0,0,1f))
        letters.forEach { row.addView(createButton(it.toString()), LinearLayout.LayoutParams(0,h,1f)) }
        keyboardContainer.addView(row)
    }

    private fun addSpecialRow(h:Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("⇧","."," ","f","h"," ","?","⌫").forEach {
            row.addView(createButton(it), LinearLayout.LayoutParams(0,h, if(it=="⇧"||it=="⌫") 0.65f else 1f))
        }
        keyboardContainer.addView(row)
    }

    private fun addBottomRow(h:Int) {
        val row = LinearLayout(this)
        row.addView(View(this), LinearLayout.LayoutParams(0,0,0.5f))
        row5.forEach { row.addView(createButton(it.toString()), LinearLayout.LayoutParams(0,h,1f)) }
        row.addView(createButton("123"), LinearLayout.LayoutParams(0,h,2f))
        row.addView(createButton("↵"), LinearLayout.LayoutParams(0,h,1f))
        row.addView(createButton("Bind LP"), LinearLayout.LayoutParams(0,h,2f))
        row.addView(View(this), LinearLayout.LayoutParams(0,0,0.5f))
        keyboardContainer.addView(row)
    }

    private fun addNumericRow(keys:Array<String>, side:Float, h:Int) {
        val row = LinearLayout(this)
        row.addView(View(this), LinearLayout.LayoutParams(0,0,side))
        keys.forEach { row.addView(createButton(it), LinearLayout.LayoutParams(0,h,1f)) }
        row.addView(View(this), LinearLayout.LayoutParams(0,0,side))
        keyboardContainer.addView(row)
    }

    private fun addNumericBottomRow(h:Int) {
        val row = LinearLayout(this)
        row.addView(View(this), LinearLayout.LayoutParams(0,0,0.5f))
        listOf("&","%","@","#").forEach { row.addView(createButton(it), LinearLayout.LayoutParams(0,h,1f)) }
        row.addView(createButton("ABC"), LinearLayout.LayoutParams(0,h,2f))
        keyboardContainer.addView(row)
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
