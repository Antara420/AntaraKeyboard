package com.example.antarakeyboard

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.antarakeyboard.data.EdgeKeyPrefs
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.addLongPress
import com.example.antarakeyboard.ui.BindLongPressDialog
import com.example.antarakeyboard.ui.ShapePreviewView
import kotlin.math.roundToInt

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

    // --- key height preview dialog state ---
    private var sizePreviewDialog: Dialog? = null
    private var previewKeyboardContainer: LinearLayout? = null
    private var okBtn: Button? = null
    private var cancelBtn: Button? = null

    private var previewBuilt = false

    private var savedKeyHeightPx = 0
    private var pendingKeyHeightPx = 0

    // range u dp
    private val minKeyDp = 36
    private val maxKeyDp = 90
    private val defaultKeyDp = 52

    // jednostavna paleta (možeš proširiti)
    private val palette: List<Int> = listOf(
        0xFF000000.toInt(), 0xFF222222.toInt(), 0xFF3E3E3E.toInt(), 0xFF585858.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFF5252.toInt(), 0xFFFF9800.toInt(), 0xFFFFEB3B.toInt(),
        0xFF4CAF50.toInt(), 0xFF00BCD4.toInt(), 0xFF2196F3.toInt(), 0xFF3F51B5.toInt(),
        0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF795548.toInt(), 0xFF607D8B.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnEnableKeyboard: Button = findViewById(R.id.btnEnableKeyboard)
        val btnChooseKeyboard: Button = findViewById(R.id.btnChooseKeyboard)
        val btnSpaceColor: Button = findViewById(R.id.btnSpaceColor)
        val btnEnterColor: Button = findViewById(R.id.btnEnterColor)

        btnSpaceColor.setOnClickListener { showSpaceColorDialog() }
        btnEnterColor.setOnClickListener { showEnterColorDialog() }

        btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        btnChooseKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }

        seek = findViewById(R.id.sizeSeek)
        preview = findViewById(R.id.preview)

        hex = findViewById(R.id.hexBtn)
        tri = findViewById(R.id.triBtn)
        circle = findViewById(R.id.circleBtn)
        cube = findViewById(R.id.cubeBtn)

        bindLPButton = findViewById(R.id.bindLPButton)
        resetLayoutButton = findViewById(R.id.resetLayoutButton)

        // Edge controls (shift/bksp)
        addEdgeKeyControls()

        // Shape init
        val savedShape = KeyboardPrefs.getShape(this)
        preview.shape = savedShape
        setCheckedForShape(savedShape)

        hex.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.HEX) }
        tri.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.TRIANGLE) }
        circle.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.CIRCLE) }
        cube.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.CUBE) }

        // Bind long press
        bindLPButton.setOnClickListener {
            val cfg = KeyboardPrefs.loadLayout(this)
            BindLongPressDialog(this, cfg) { bind ->
                cfg.addLongPress(bind.keyLabel, bind.charValue)
                KeyboardPrefs.saveLayout(this, cfg)
                Toast.makeText(this, "Bind saved: ${bind.keyLabel} → ${bind.charValue}", Toast.LENGTH_SHORT).show()
            }.show()
        }

        // Reset
        resetLayoutButton.setOnClickListener {
            KeyboardPrefs.clearLayout(this)
            KeyboardPrefs.setShape(this, KeyShape.HEX)
            KeyboardPrefs.clearKeyHeightPx(this)

            // reset colors
            KeyboardPrefs.setSpaceColors(this, 0xFF3E3E3E.toInt(), 0xFF3E3E3E.toInt(), true)
            KeyboardPrefs.setEnterColors(this, 0xFF2E55E7.toInt(), 0xFFFFFFFF.toInt())

            EdgeKeyPrefs.setShift(this, EdgePos(3, EdgePos.Side.LEFT))
            EdgeKeyPrefs.setBackspace(this, EdgePos(3, EdgePos.Side.RIGHT))
            updateEdgeLabels()

            setupKeyHeightSlider()
            Toast.makeText(this, "Sve postavke resetirane", Toast.LENGTH_SHORT).show()
        }

        // Slider: key height only
        setupKeyHeightSlider()
    }

    /* =========================
       SPACE / ENTER COLOR DIALOGS
       ========================= */

    private fun showSpaceColorDialog() {
        var linked = KeyboardPrefs.isSpaceLinked(this)
        var c1 = KeyboardPrefs.getSpace1Bg(this)
        var c2 = KeyboardPrefs.getSpace2Bg(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }

        val cb = CheckBox(this).apply {
            text = "Both space keys same color"
            isChecked = linked
        }
        root.addView(cb)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }

        fun colorPreviewButton(label: String, getColor: () -> Int, onPick: (Int) -> Unit): Button {
            return Button(this).apply {
                text = label
                isAllCaps = false
                setBackgroundColor(getColor())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    showPalettePicker("Pick $label", getColor()) { picked ->
                        onPick(picked)
                        setBackgroundColor(getColor())
                    }
                }
            }
        }

        val b1 = colorPreviewButton("Space 1", { c1 }) { picked ->
            c1 = picked
            if (linked) {
                c2 = picked
            }
        }

        val b2 = colorPreviewButton("Space 2", { c2 }) { picked ->
            c2 = picked
            if (linked) {
                c1 = picked
            }
        }

        row.addView(
            b1,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        )
        row.addView(
            b2,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) }
        )

        root.addView(row)

        cb.setOnCheckedChangeListener { _, isChecked ->
            linked = isChecked
            if (linked) {
                c2 = c1
                b2.setBackgroundColor(c2)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Space color")
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                if (linked) c2 = c1
                KeyboardPrefs.setSpaceColors(this, c1, c2, linked)
                Toast.makeText(this, "Space colors saved", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showEnterColorDialog() {
        var bg = KeyboardPrefs.getEnterBg(this)
        var icon = KeyboardPrefs.getEnterIcon(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }

        val bgBtn = Button(this).apply {
            text = "Background"
            isAllCaps = false
            setBackgroundColor(bg)
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                showPalettePicker("Enter background", bg) { picked ->
                    bg = picked
                    setBackgroundColor(bg)
                }
            }
        }

        val iconBtn = Button(this).apply {
            text = "Icon color"
            isAllCaps = false
            setBackgroundColor(0xFF222222.toInt())
            setTextColor(icon)
            setOnClickListener {
                showPalettePicker("Enter icon color", icon) { picked ->
                    icon = picked
                    setTextColor(icon)
                }
            }
        }

        root.addView(bgBtn)
        root.addView(
            iconBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        )

        AlertDialog.Builder(this)
            .setTitle("Enter color")
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                KeyboardPrefs.setEnterColors(this, bg, icon)
                Toast.makeText(this, "Enter colors saved", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showPalettePicker(title: String, current: Int, onPicked: (Int) -> Unit) {
        val grid = GridLayout(this).apply {
            columnCount = 4
            setPadding(dp(12), dp(12), dp(12), dp(4))
        }

        palette.forEach { c ->
            val v = TextView(this).apply {
                setBackgroundColor(c)
                width = dp(48)
                height = dp(48)
                setOnClickListener {
                    onPicked(c)
                }
            }
            val lp = ViewGroup.MarginLayoutParams(dp(48), dp(48)).apply {
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }
            grid.addView(v, lp)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(grid)
            .setNegativeButton("Close", null)
            .show()
    }

    /* =========================
       KEY HEIGHT SLIDER (only)
       ========================= */

    private fun setupKeyHeightSlider() {
        val minPx = dpToPx(minKeyDp)
        val maxPx = dpToPx(maxKeyDp)
        val defPx = dpToPx(defaultKeyDp)

        val stored = KeyboardPrefs.getKeyHeightPx(this)
        savedKeyHeightPx = if (stored > 0) stored else defPx
        pendingKeyHeightPx = savedKeyHeightPx

        seek.max = 100

        val p = ((savedKeyHeightPx - minPx).toFloat() / (maxPx - minPx).toFloat() * 100f)
            .roundToInt()
            .coerceIn(0, 100)
        seek.progress = p

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar?) {
                showKeyboardKeyHeightPreview(minPx, maxPx)
                pendingKeyHeightPx = pxFromProgress(sb?.progress ?: 0, minPx, maxPx)
                updatePreviewKeyHeights(pendingKeyHeightPx)
            }

            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                pendingKeyHeightPx = pxFromProgress(progress, minPx, maxPx)
                if (sizePreviewDialog?.isShowing == true) {
                    updatePreviewKeyHeights(pendingKeyHeightPx)
                }
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                // ništa dok se ne klikne OK
            }
        })
    }

    private fun pxFromProgress(progress: Int, minPx: Int, maxPx: Int): Int {
        val t = progress.coerceIn(0, 100) / 100f
        return (minPx + t * (maxPx - minPx)).roundToInt().coerceIn(minPx, maxPx)
    }

    private fun showKeyboardKeyHeightPreview(minPx: Int, maxPx: Int) {
        if (sizePreviewDialog?.isShowing == true) return

        val defPx = dpToPx(defaultKeyDp)
        val stored = KeyboardPrefs.getKeyHeightPx(this)
        savedKeyHeightPx = if (stored > 0) stored else defPx
        pendingKeyHeightPx = savedKeyHeightPx.coerceIn(minPx, maxPx)

        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_keyboard_preview)
        d.setCancelable(false)
        d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        previewKeyboardContainer = d.findViewById(R.id.previewKeyboardContainer)
        okBtn = d.findViewById(R.id.okBtn)
        cancelBtn = d.findViewById(R.id.cancelBtn)

        previewBuilt = false

        val cfg = KeyboardPrefs.loadLayout(this)
        buildKeyboardPreview(cfg, pendingKeyHeightPx)

        d.window?.decorView?.post {
            updatePreviewKeyHeights(pendingKeyHeightPx)
        }

        cancelBtn?.setOnClickListener {
            pendingKeyHeightPx = savedKeyHeightPx
            d.dismiss()
            clearPreviewDialogRefs()
        }

        okBtn?.setOnClickListener {
            KeyboardPrefs.setKeyHeightPx(this, pendingKeyHeightPx.coerceIn(minPx, maxPx))
            Toast.makeText(this, "Key height saved: ${pxToDp(pendingKeyHeightPx)}dp", Toast.LENGTH_SHORT).show()
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
    }

    private fun buildKeyboardPreview(config: KeyboardConfig, keyHeightPx: Int) {
        val container = previewKeyboardContainer ?: return
        if (previewBuilt) return

        container.removeAllViews()

        fun addRow(labels: List<String>) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            labels.forEach { label ->
                row.addView(
                    createPreviewKey(label),
                    LinearLayout.LayoutParams(0, keyHeightPx, 1f)
                )
            }
            container.addView(row)
        }

        if (config.specialLeft.isNotEmpty()) addRow(config.specialLeft.map { it.label })
        config.rows.forEach { rowCfg -> addRow(rowCfg.keys.map { it.label }) }
        if (config.specialRight.isNotEmpty()) addRow(config.specialRight.map { it.label })

        previewBuilt = true
    }

    private fun updatePreviewKeyHeights(keyHeightPx: Int) {
        val container = previewKeyboardContainer ?: return
        if (!previewBuilt) return

        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                val child = row.getChildAt(j)
                val lp = child.layoutParams
                lp.height = keyHeightPx
                child.layoutParams = lp
            }
        }
        container.requestLayout()
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

    /* =========================
       EDGE KEYS UI (SHIFT / BKSP)
       ========================= */

    private fun addEdgeKeyControls() {
        val root = findViewById<ViewGroup>(android.R.id.content)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(14))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

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
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, rows)
            setSelection(rows.indexOf(current.row).coerceAtLeast(0))
        }

        val sideSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, sides)
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
       UTIL
       ========================= */

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private fun pxToDp(px: Int): Int = (px / resources.displayMetrics.density).toInt()
}
