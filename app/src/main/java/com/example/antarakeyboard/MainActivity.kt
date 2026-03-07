package com.example.antarakeyboard

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.ui.ShapePreviewView
import com.example.antarakeyboard.ui.LongPressKeyPickerDialog
import androidx.appcompat.app.AppCompatDelegate
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.SpecialChars
import android.content.ClipData
import android.content.ClipDescription
import android.view.DragEvent

class MainActivity : AppCompatActivity() {
    private lateinit var preview: ShapePreviewView
    private lateinit var hex: RadioButton
    private lateinit var tri: RadioButton
    private lateinit var circle: RadioButton
    private lateinit var cube: RadioButton
    private lateinit var bindLPButton: Button
    private lateinit var resetLayoutButton: Button
    // --- Edge key UI ---


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", true)

        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        // 🔥 OVO MORA BITI PRIJE findViewById
        setContentView(R.layout.activity_main)

        // 🔥 SAD je sigurno
        val themeBtn: Button = findViewById(R.id.btnThemeToggle)

        fun applyThemeButtonText(isDark: Boolean) {
            themeBtn.text = if (isDark) "Switch to Lightmode" else "Switch to Darkmode"
        }

        applyThemeButtonText(isDark)
        val btnSetSideButtons: Button = findViewById(R.id.btnSetSideButtons)

        btnSetSideButtons.setOnClickListener {
            showEdgeSlotsDialog()
        }

        themeBtn.setOnClickListener {
            val prefs2 = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            val current = prefs2.getBoolean("dark_mode", true)
            val newMode = !current

            prefs2.edit().putBoolean("dark_mode", newMode).apply()

            AppCompatDelegate.setDefaultNightMode(
                if (newMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )

            applyThemeButtonText(newMode)
        }


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
        val btnSetLayout: Button = findViewById(R.id.btnSetLayout)

        btnSetLayout.setOnClickListener {
            val cfg = KeyboardPrefs.loadLayout(this)
            com.example.antarakeyboard.ui.UserLayoutDialog(
                context = this,
                initial = cfg
            ) { updated ->
                KeyboardPrefs.saveLayout(this, updated)
                Toast.makeText(this, "Layout saved ✅", Toast.LENGTH_SHORT).show()
                // KeyboardService će ga povuć čim se tipkovnica sljedeći put prikaže,
                // a često i odmah kad se vratiš u polje za unos.
            }.show()
        }


        preview = findViewById(R.id.preview)

        hex = findViewById(R.id.hexBtn)
        tri = findViewById(R.id.triBtn)
        circle = findViewById(R.id.circleBtn)
        cube = findViewById(R.id.cubeBtn)

        bindLPButton = findViewById(R.id.bindLPButton)
        resetLayoutButton = findViewById(R.id.resetLayoutButton)

// Shape init
        val savedShape = KeyboardPrefs.getShape(this)
        preview.shape = savedShape
        setCheckedForShape(savedShape)

        hex.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.HEX) }
        tri.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.TRIANGLE) }
        circle.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.CIRCLE) }
        cube.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.CUBE) }
        //longpress
        bindLPButton.setOnClickListener {
            val cfg = KeyboardPrefs.loadLayout(this)
            LongPressKeyPickerDialog(
                context = this,
                cfg = cfg,
                onSave = { updated ->
                    KeyboardPrefs.saveLayout(this, updated)
                    Toast.makeText(this, "Long press saved ✅", Toast.LENGTH_SHORT).show()
                }
            ).show()
        }

        // Reset
        resetLayoutButton.setOnClickListener {
            KeyboardPrefs.clearLayout(this)
            KeyboardPrefs.setShape(this, KeyShape.HEX)

            val keyFill = themeColor(R.attr.keyFill, getColor(R.color.key_fill_light))
            val specialFill = getColor(R.color.special_fill)
            val specialText = getColor(R.color.special_text)

            KeyboardPrefs.setSpaceColors(this, keyFill, keyFill, true)
            KeyboardPrefs.setEnterColors(this, specialFill, specialText)

            EdgeSlotsStorage.reset(this)

            preview.shape = KeyShape.HEX
            setCheckedForShape(KeyShape.HEX)

            Toast.makeText(this, "Sve postavke resetirane", Toast.LENGTH_SHORT).show()
        }

    }
    private fun showEdgeSlotsDialog() {

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(12))
        }

        val grid = GridLayout(this).apply {
            rowCount = 3
            columnCount = 2
        }

        val slots = EdgeSlotsStorage.load(this).toMutableList()

        fun fixedSideForIndex(i: Int): EdgePos.Side =
            if (i % 2 == 0) EdgePos.Side.LEFT else EdgePos.Side.RIGHT

        fun fixSlotPosition(i: Int, s: EdgeSlot): EdgeSlot =
            s.copy(index = i, side = fixedSideForIndex(i))


        fun buildGrid() {
            grid.removeAllViews()

            fun startDragCompat(v: View, fromIndex: Int) {
                val data = ClipData.newPlainText("fromIndex", fromIndex.toString())
                val shadow = View.DragShadowBuilder(v)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    v.startDragAndDrop(data, shadow, null, 0)
                } else {
                    @Suppress("DEPRECATION")
                    v.startDrag(data, shadow, null, 0)
                }
            }

            fun parseFromIndex(e: DragEvent): Int? {
                val item = e.clipData?.getItemAt(0)?.text?.toString() ?: return null
                return item.toIntOrNull()
            }

            slots.forEachIndexed { index, slot ->
                val btn = Button(this).apply {
                    text = slot.label
                    isAllCaps = false
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                    tag = index
                }

                // klik -> picker
                btn.setOnClickListener {
                    showEdgeTypePicker(slot) { newSlot ->
                        val normalized = fixSlotPosition(index, normalizeSlot(newSlot))
                        enforceNoDuplicates(slots, index, normalized)
                        slots[index] = normalized
                        buildGrid()
                    }
                }

                // long press -> start drag
                btn.setOnLongClickListener { v ->
                    startDragCompat(v, index)
                    true
                }

                // drop target
                btn.setOnDragListener { v, e ->
                    when (e.action) {
                        DragEvent.ACTION_DRAG_STARTED -> {
                            e.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                        }

                        DragEvent.ACTION_DRAG_ENTERED -> {
                            v.alpha = 0.65f
                            true
                        }

                        DragEvent.ACTION_DRAG_EXITED -> {
                            v.alpha = 1f
                            true
                        }

                        DragEvent.ACTION_DROP -> {
                            v.alpha = 1f
                            val from = parseFromIndex(e) ?: return@setOnDragListener true
                            val to = (v.tag as? Int) ?: return@setOnDragListener true
                            if (from == to) return@setOnDragListener true

                            // SWAP
                            val tmp = slots[from]
                            slots[from] = slots[to]
                            slots[to] = tmp

                            // ✅ nakon swap-a zakucaj index+side po poziciji
                            slots[from] = fixSlotPosition(from, slots[from])
                            slots[to] = fixSlotPosition(to, slots[to])

                            buildGrid()
                            true
                        }

                        DragEvent.ACTION_DRAG_ENDED -> {
                            v.alpha = 1f
                            true
                        }

                        else -> true
                    }
                }

                val lp = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(index % 2, 1f)
                    rowSpec = GridLayout.spec(index / 2)
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }

                grid.addView(btn, lp)
            }
        }

        buildGrid()

        root.addView(grid)

        val saveBtn = Button(this).apply {
            text = "Save"
            setOnClickListener {
                EdgeSlotsStorage.save(this@MainActivity, slots)
                Toast.makeText(this@MainActivity, "Side buttons saved", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        root.addView(saveBtn)

        dialog.setContentView(root)
        dialog.show()
    }


    private fun normalizeSlot(s: EdgeSlot): EdgeSlot {
        return when (s.type) {
            EdgeActionType.CHAR -> s.copy(value = s.value?.takeIf { it.isNotBlank() })
            else -> s.copy(value = null)
        }
    }

    private fun enforceNoDuplicates(slots: MutableList<EdgeSlot>, changedIndex: Int, chosen: EdgeSlot) {
        // jedinstvene akcije (smiju postojati samo jednom)
        val uniqueTypes = setOf(
            EdgeActionType.SHIFT,
            EdgeActionType.BACKSPACE,
            EdgeActionType.ENTER,
            EdgeActionType.SPACE
        )

        // ako je SHIFT/BKSP/ENTER/SPACE odabran, makni ga sa starog mjesta
        if (chosen.type in uniqueTypes) {
            for (i in slots.indices) {
                if (i != changedIndex && slots[i].type == chosen.type) {
                    slots[i] = slots[i].copy(type = EdgeActionType.NONE, value = null)
                }
            }
        }

        // ako je CHAR, ne smije se ponavljati isti znak
        if (chosen.type == EdgeActionType.CHAR && !chosen.value.isNullOrBlank()) {
            for (i in slots.indices) {
                if (i != changedIndex &&
                    slots[i].type == EdgeActionType.CHAR &&
                    slots[i].value == chosen.value
                ) {
                    slots[i] = slots[i].copy(type = EdgeActionType.NONE, value = null)
                }
            }
        }
    }
    private fun showSpecialCharPicker(onPicked: (String) -> Unit) {
        // ✅ ovo je jedino mjesto gdje vučeš znakove
        // Ako ti se polje/lista ne zove ALL, promijeni u stvarno ime iz SpecialChars fajla.
        val all = SpecialChars.ALL

        AlertDialog.Builder(this)
            .setTitle("Pick a character")
            .setItems(all.toTypedArray()) { _, which ->
                onPicked(all[which])
            }
            .show()
    }

    /**
     * Pravilo:
     * - ne smije se ponavljati nijedan action (SHIFT/BACKSPACE/ENTER/SPACE) -> premjesti na novo mjesto
     * - ne smije se ponavljati ni CHAR + isti value -> premjesti na novo mjesto
     */
    private fun applyNoDuplicates(slots: MutableList<EdgeSlot>, targetIndex: Int, newSlot: EdgeSlot) {
        // prvo postavi novi slot
        slots[targetIndex] = newSlot

        if (newSlot.type == EdgeActionType.NONE) return

        for (i in slots.indices) {
            if (i == targetIndex) continue

            val s = slots[i]

            val sameType = s.type == newSlot.type && newSlot.type != EdgeActionType.CHAR
            val sameChar = (newSlot.type == EdgeActionType.CHAR
                    && s.type == EdgeActionType.CHAR
                    && !newSlot.value.isNullOrBlank()
                    && s.value == newSlot.value)

            if (sameType || sameChar) {
                // makni duplikat (prebaci na novo mjesto)
                slots[i] = s.copy(type = EdgeActionType.NONE, value = null)
            }
        }
    }
    private fun showEdgeTypePicker(
        oldSlot: EdgeSlot,
        onSelected: (EdgeSlot) -> Unit
    ) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }

        // --- akcije gore ---
        val actions = listOf(
            EdgeActionType.SHIFT to "⇧ Shift",
            EdgeActionType.BACKSPACE to "⌫ Backspace",
            EdgeActionType.ENTER to "↵ Enter",
            EdgeActionType.SPACE to "␣ Space",
            EdgeActionType.NONE to "None"
        )

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        actions.forEach { (type, label) ->
            val b = Button(this).apply {
                text = label
                isAllCaps = false
            }
            b.setOnClickListener {
                onSelected(oldSlot.copy(type = type, value = null))
            }
            actionsRow.addView(b)
        }

        root.addView(actionsRow)

        // --- separator ---
        root.addView(TextView(this).apply {
            text = "Special chars"
            setPadding(0, dp(10), 0, dp(6))
        })

        // --- grid special chars ---
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = 6
        }

        SpecialChars.ALL.forEach { ch ->
            val b = Button(this).apply {
                text = ch
                isAllCaps = false
                minHeight = dp(44)
                minWidth = dp(44)
                setPadding(0, 0, 0, 0)
            }
            b.setOnClickListener {
                onSelected(oldSlot.copy(type = EdgeActionType.CHAR, value = ch))
            }

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(b, lp)
        }

        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(320) // možeš povećat/smanjit
        ))

        AlertDialog.Builder(this)
            .setTitle("Choose side button")
            .setView(root)
            .setNegativeButton("Close", null)
            .show()
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

        lateinit var b1: Button
        lateinit var b2: Button

        fun styleColorButton(btn: Button, color: Int) {
            btn.setBackgroundColor(color)
            btn.setTextColor(0xFFFFFFFF.toInt())
        }

        b1 = Button(this).apply {
            text = "Space 1"
            isAllCaps = false
            styleColorButton(this, c1)
            setOnClickListener {
                showAdvancedColorPicker("Space 1 color", c1) { picked ->
                    c1 = picked
                    styleColorButton(b1, c1)
                    if (linked) {
                        c2 = picked
                        styleColorButton(b2, c2)
                    }
                }
            }
        }

        b2 = Button(this).apply {
            text = "Space 2"
            isAllCaps = false
            styleColorButton(this, c2)
            setOnClickListener {
                showAdvancedColorPicker("Space 2 color", c2) { picked ->
                    c2 = picked
                    styleColorButton(b2, c2)
                    if (linked) {
                        c1 = picked
                        styleColorButton(b1, c1)
                    }
                }
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
                styleColorButton(b2, c2)
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
                showAdvancedColorPicker("Enter background", bg) { picked ->
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
                showAdvancedColorPicker("Enter icon color", icon) { picked ->
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

    /**
     * HSV(A) picker: Hue/Sat/Value + Alpha
     * Važno: onPicked se zove live, ali prefs spremaš tek na "Save" u parent dialogu.
     */
    private fun showAdvancedColorPicker(
        title: String,
        initialColor: Int,
        onPicked: (Int) -> Unit
    ) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor, hsv)
        var alpha = android.graphics.Color.alpha(initialColor)

        var currentColor = initialColor

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }

        val previewBar = View(this).apply {
            setBackgroundColor(initialColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        }
        root.addView(previewBar)

        val hueSeek = SeekBar(this).apply { max = 360; progress = hsv[0].toInt() }
        val satSeek = SeekBar(this).apply { max = 100; progress = (hsv[1] * 100).toInt() }
        val valSeek = SeekBar(this).apply { max = 100; progress = (hsv[2] * 100).toInt() }
        val alphaSeek = SeekBar(this).apply { max = 255; progress = alpha }

        fun recompute() {
            hsv[0] = hueSeek.progress.toFloat()
            hsv[1] = satSeek.progress / 100f
            hsv[2] = valSeek.progress / 100f
            alpha = alphaSeek.progress

            currentColor = android.graphics.Color.HSVToColor(alpha, hsv)
            previewBar.setBackgroundColor(currentColor)
            onPicked(currentColor)
        }

        hueSeek.setOnSeekBarChangeListener(simpleSeek { recompute() })
        satSeek.setOnSeekBarChangeListener(simpleSeek { recompute() })
        valSeek.setOnSeekBarChangeListener(simpleSeek { recompute() })
        alphaSeek.setOnSeekBarChangeListener(simpleSeek { recompute() })

        root.addView(TextView(this).apply { text = "Hue" })
        root.addView(hueSeek)
        root.addView(TextView(this).apply { text = "Saturation" })
        root.addView(satSeek)
        root.addView(TextView(this).apply { text = "Brightness" })
        root.addView(valSeek)
        root.addView(TextView(this).apply { text = "Alpha" })
        root.addView(alphaSeek)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(root)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun simpleSeek(onChange: () -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange()
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
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


    private fun themeColor(attr: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        return if (theme.resolveAttribute(attr, tv, true) &&
            tv.type in android.util.TypedValue.TYPE_FIRST_COLOR_INT..android.util.TypedValue.TYPE_LAST_COLOR_INT
        ) tv.data else fallback
    }
}
