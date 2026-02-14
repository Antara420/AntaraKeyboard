package com.example.antarakeyboard

import android.app.Dialog
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.ViewTreeObserver
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.antarakeyboard.data.EdgeKeyPrefs
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.ui.BindLongPressDialog
import com.example.antarakeyboard.ui.ShapePreviewView
import com.example.antarakeyboard.model.addLongPress
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var preview: ShapePreviewView

    private lateinit var hex: RadioButton
    private lateinit var tri: RadioButton
    private lateinit var circle: RadioButton
    private lateinit var cube: RadioButton

    private lateinit var seek: SeekBar
    private lateinit var bindLPButton: Button
    private lateinit var resetLayoutButton: Button

    // --- Edge key UI ---
    private lateinit var setShiftPosButton: Button
    private lateinit var setBkspPosButton: Button
    private lateinit var shiftPosLabel: TextView
    private lateinit var bkspPosLabel: TextView

    // --- size preview dialog state ---
    private var sizePreviewDialog: Dialog? = null
    private var previewKeyboardContainer: LinearLayout? = null
    private var okBtn: Button? = null
    private var cancelBtn: Button? = null

    private var previewBuilt = false
    private var basePreviewHeightPx = 0

    // saved vs pending
    private var savedScale = 1.0f
    private var pendingScale = 1.0f

    // px range
    private val minKeyboardHeightPx = 250
    private val maxKeyboardHeightPx = 550

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- UI References ---
        seek = findViewById(R.id.sizeSeek)
        preview = findViewById(R.id.preview)

        hex = findViewById(R.id.hexBtn)
        tri = findViewById(R.id.triBtn)
        circle = findViewById(R.id.circleBtn)
        cube = findViewById(R.id.cubeBtn)

        bindLPButton = findViewById(R.id.bindLPButton)
        resetLayoutButton = findViewById(R.id.resetLayoutButton)

        // ✅ dodaj Edge key controls na dno ekrana
        addEdgeKeyControls()

        // reset logika
        resetLayoutButton.setOnClickListener {
            KeyboardPrefs.clearLayout(this)
            KeyboardPrefs.setScale(this, 1.0f)
            KeyboardPrefs.setShape(this, KeyShape.HEX)

            // resetiraj i edge key pozicije na default
            EdgeKeyPrefs.setShift(this, EdgePos(3, EdgePos.Side.LEFT))
            EdgeKeyPrefs.setBackspace(this, EdgePos(3, EdgePos.Side.RIGHT))
            updateEdgeLabels()

            Toast.makeText(this, "Sve postavke resetirane", Toast.LENGTH_SHORT).show()
        }

        // --- Load saved scale & shape ---
        savedScale = KeyboardPrefs.getScale(this)
        pendingScale = savedScale

        // slider progress 0..300 => 250..550px
        seek.max = 300
        seek.progress = 150

        val savedShape = KeyboardPrefs.getShape(this)
        preview.shape = savedShape
        setCheckedForShape(savedShape)

        // --- SeekBar Listener (preview + OK save) ---
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar?) {
                showKeyboardSizePreview()
                val targetH = minKeyboardHeightPx + (seek.progress.coerceIn(0, 300))
                updateKeyboardSizePreviewByHeight(targetH)
            }

            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (sizePreviewDialog?.isShowing != true) return
                val targetH = minKeyboardHeightPx + progress.coerceIn(0, 300)
                updateKeyboardSizePreviewByHeight(targetH)
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                // ne spremamo ništa dok se ne klikne OK
            }
        })

        // --- Shape RadioButtons ---
        hex.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.HEX) }
        tri.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.TRIANGLE) }
        circle.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.CIRCLE) }
        cube.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.CUBE) }

        // --- Bind Long Press Button ---
        bindLPButton.setOnClickListener {
            val cfg = KeyboardPrefs.loadLayout(this)

            BindLongPressDialog(this, cfg) { bind ->
                cfg.addLongPress(bind.keyLabel, bind.charValue)
                KeyboardPrefs.saveLayout(this, cfg)

                Toast.makeText(
                    this,
                    "Bind saved: ${bind.keyLabel} → ${bind.charValue}",
                    Toast.LENGTH_SHORT
                ).show()
            }.show()
        }
    }

    /* =========================
       EDGE KEYS UI (SHIFT / BKSP)
       ========================= */

    private fun addEdgeKeyControls() {
        val root = findViewById<ViewGroup>(android.R.id.content)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(14))
            // malo razdvoji od ostatka
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            layoutParams = lp
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        setShiftPosButton = Button(this).apply {
            text = "Set SHIFT position"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { showEdgeKeyDialog(isShift = true) }
        }

        setBkspPosButton = Button(this).apply {
            text = "Set BACKSPACE position"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            setOnClickListener { showEdgeKeyDialog(isShift = false) }
        }

        row1.addView(setShiftPosButton)
        row1.addView(setBkspPosButton)

        shiftPosLabel = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(6), 0, 0)
        }

        bkspPosLabel = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(2), 0, 0)
        }

        panel.addView(row1)
        panel.addView(shiftPosLabel)
        panel.addView(bkspPosLabel)

        root.addView(panel)
        updateEdgeLabels()
    }

    private fun updateEdgeLabels() {
        val s = EdgeKeyPrefs.getShift(this)
        val b = EdgeKeyPrefs.getBackspace(this)
        shiftPosLabel.text = "SHIFT: Row ${s.row} ${s.side.name}"
        bkspPosLabel.text = "BKSP:  Row ${b.row} ${b.side.name}"
    }

    private fun showEdgeKeyDialog(isShift: Boolean) {
        val rows = listOf(1, 3, 5)
        val sides = listOf("LEFT", "RIGHT")

        val currentShift = EdgeKeyPrefs.getShift(this)
        val currentBksp = EdgeKeyPrefs.getBackspace(this)
        val current = if (isShift) currentShift else currentBksp
        val other = if (isShift) currentBksp else currentShift

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }

        val rowSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                rows
            )
            setSelection(rows.indexOf(current.row).coerceAtLeast(0))
        }

        val sideSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                sides
            )
            setSelection(sides.indexOf(current.side.name).coerceAtLeast(0))
        }

        container.addView(TextView(this).apply { text = "Row (1, 3, 5)" })
        container.addView(rowSpinner)
        container.addView(TextView(this).apply { text = "Side (LEFT / RIGHT)" })
        container.addView(sideSpinner)

        AlertDialog.Builder(this)
            .setTitle(if (isShift) "Set SHIFT position" else "Set BACKSPACE position")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val row = rows[rowSpinner.selectedItemPosition]
                val side = EdgePos.Side.valueOf(sides[sideSpinner.selectedItemPosition])
                val newPos = EdgePos(row, side)

                // 1) ne smiju biti na istom mjestu
                if (newPos.row == other.row && newPos.side == other.side) {
                    Toast.makeText(this, "SHIFT i BACKSPACE ne mogu biti na istom mjestu.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }



                if (isShift) EdgeKeyPrefs.setShift(this, newPos) else EdgeKeyPrefs.setBackspace(this, newPos)
                updateEdgeLabels()
                Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /* =========================
       SHAPE
       ========================= */

    private fun applyShape(shape: KeyShape) {
        preview.shape = shape
        KeyboardPrefs.setShape(this, shape)
        setCheckedForShape(shape)
    }

    private fun setCheckedForShape(shape: KeyShape) {
        when (shape) {
            KeyShape.HEX -> hex.isChecked = true
            KeyShape.TRIANGLE -> tri.isChecked = true
            KeyShape.CIRCLE -> circle.isChecked = true
            KeyShape.CUBE -> cube.isChecked = true
        }
    }

    /* =========================
       KEYBOARD SIZE PREVIEW DIALOG
       ========================= */

    private fun showKeyboardSizePreview() {
        if (sizePreviewDialog?.isShowing == true) return

        savedScale = KeyboardPrefs.getScale(this)
        pendingScale = savedScale

        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_keyboard_preview)
        d.setCancelable(false)

        d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        previewKeyboardContainer = d.findViewById(R.id.previewKeyboardContainer)
        okBtn = d.findViewById(R.id.okBtn)
        cancelBtn = d.findViewById(R.id.cancelBtn)

        previewBuilt = false
        basePreviewHeightPx = 0

        val cfg = KeyboardPrefs.loadLayout(this)
        buildKeyboardPreview(cfg)

        previewKeyboardContainer?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val container = previewKeyboardContainer ?: return
                if (container.height > 0 && basePreviewHeightPx == 0) {
                    basePreviewHeightPx = container.height
                    val targetH = minKeyboardHeightPx + seek.progress.coerceIn(0, 300)
                    updateKeyboardSizePreviewByHeight(targetH)
                }
                container.viewTreeObserver?.removeOnGlobalLayoutListener(this)
            }
        })

        cancelBtn?.setOnClickListener {
            pendingScale = savedScale
            KeyboardPrefs.setScale(this, savedScale)
            d.dismiss()
            clearPreviewDialogRefs()
        }

        okBtn?.setOnClickListener {
            KeyboardPrefs.setScale(this, pendingScale)
            d.dismiss()
            clearPreviewDialogRefs()
        }

        sizePreviewDialog = d
        d.show()
    }

    private fun clearPreviewDialogRefs() {
        sizePreviewDialog = null
        previewKeyboardContainer = null
        okBtn = null
        cancelBtn = null
        previewBuilt = false
        basePreviewHeightPx = 0
    }

    private fun buildKeyboardPreview(config: KeyboardConfig) {
        val container = previewKeyboardContainer ?: return
        if (previewBuilt) return

        container.removeAllViews()

        // LEFT special row samo ako postoji
        if (config.specialLeft.isNotEmpty()) {
            val left = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            config.specialLeft.forEach { key ->
                left.addView(createPreviewKey(key.label), LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            container.addView(left)
        }

        // rows
        config.rows.forEach { rowCfg ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowCfg.keys.forEach { key ->
                row.addView(createPreviewKey(key.label), LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            container.addView(row)
        }

        // RIGHT special row samo ako postoji
        if (config.specialRight.isNotEmpty()) {
            val right = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            config.specialRight.forEach { key ->
                right.addView(createPreviewKey(key.label), LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            container.addView(right)
        }

        previewBuilt = true
    }

    private fun updateKeyboardSizePreviewByHeight(targetHeightPx: Int) {
        val container = previewKeyboardContainer ?: return
        if (!previewBuilt) return
        if (basePreviewHeightPx <= 0) return

        val target = targetHeightPx.coerceIn(minKeyboardHeightPx, maxKeyboardHeightPx)
        val scale = target.toFloat() / basePreviewHeightPx.toFloat()

        if (abs(scale - pendingScale) < 0.005f) return
        pendingScale = scale

        container.pivotX = container.width / 2f
        container.pivotY = 0f
        container.scaleX = scale
        container.scaleY = scale
    }

    private fun createPreviewKey(text: String): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            isClickable = false
            isFocusable = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
