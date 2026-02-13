package com.example.antarakeyboard

import android.app.Dialog
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.ui.BindLongPressDialog
import com.example.antarakeyboard.ui.ShapePreviewView
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var preview: ShapePreviewView

    private lateinit var hex: RadioButton
    private lateinit var tri: RadioButton
    private lateinit var circle: RadioButton
    private lateinit var cube: RadioButton

    private lateinit var seek: SeekBar
    private lateinit var bindLPButton: Button

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

        // --- Load saved scale & shape ---
        savedScale = KeyboardPrefs.getScale(this)
        pendingScale = savedScale

        // SeekBar: map scale -> px slider (we'll map via height directly)
        // slider progress 0..300 => 250..550px
        // start from middle based on savedScale by approximating: assume 400px base -> convert
        // real mapping will happen when dialog measures; for now put something sensible:
        seek.max = 300
        seek.progress = 150 // sredina (400px)
        // (kad otvoriš preview, on će točno prikazat; kasnije možemo spremit i "zadnju px vrijednost")

        val savedShape = KeyboardPrefs.getShape(this)
        preview.shape = savedShape
        setCheckedForShape(savedShape)

        // --- SeekBar Listener (preview + OK save) ---
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar?) {
                showKeyboardSizePreview()
                // čim se otvori, odmah primijeni trenutni slider
                val targetH = minKeyboardHeightPx + (seek.progress.coerceIn(0, 300))
                updateKeyboardSizePreviewByHeight(targetH)
            }

            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (sizePreviewDialog?.isShowing != true) return
                val targetH = minKeyboardHeightPx + progress.coerceIn(0, 300)
                updateKeyboardSizePreviewByHeight(targetH)
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                // ništa ne spremamo ovdje — čeka se OK
            }
        })

        // --- Shape RadioButtons ---
        hex.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.HEX) }
        tri.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.TRIANGLE) }
        circle.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.CIRCLE) }
        cube.setOnCheckedChangeListener { _, checked -> if (checked) applyShape(KeyShape.CUBE) }

        // --- Bind Long Press Button ---
        bindLPButton.setOnClickListener {
            BindLongPressDialog(this, KeyboardPrefs.loadLayout(this)) { key, selectedChar ->
                Toast.makeText(this, "Bind added: $key → $selectedChar", Toast.LENGTH_SHORT).show()
            }.show()
        }
    }

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

        // zapamti trenutno spremljeno (za Cancel)
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

        // napravi "pravu tipkovnicu" iz configa (isti raspored kao IME)
        val cfg = KeyboardPrefs.loadLayout(this)
        buildKeyboardPreview(cfg)

        // kad se izmjeri visina, postavi base i primijeni trenutni slider
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
            // vrati spremljeno (na tipkovnici to znači scale nazad) i zatvori
            pendingScale = savedScale
            KeyboardPrefs.setScale(this, savedScale)
            d.dismiss()
            clearPreviewDialogRefs()
        }

        okBtn?.setOnClickListener {
            // spremi pendingScale i zatvori
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

        // LEFT special row
        val left = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        config.specialLeft.forEach { key ->
            left.addView(createPreviewKey(key.label), LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        container.addView(left)

        // rows
        config.rows.forEach { rowCfg ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowCfg.keys.forEach { key ->
                row.addView(createPreviewKey(key.label), LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            container.addView(row)
        }

        // RIGHT special row
        val right = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        config.specialRight.forEach { key ->
            right.addView(createPreviewKey(key.label), LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        container.addView(right)

        previewBuilt = true
    }

    private fun updateKeyboardSizePreviewByHeight(targetHeightPx: Int) {
        val container = previewKeyboardContainer ?: return
        if (!previewBuilt) return

        // ako još nemamo izmjerenu base visinu, probaj kasnije
        if (basePreviewHeightPx <= 0) return

        val target = targetHeightPx.coerceIn(minKeyboardHeightPx, maxKeyboardHeightPx)

        // skala koja daje približno target px visinu
        val scale = target.toFloat() / basePreviewHeightPx.toFloat()

        // izbjegni trzanje
        if (abs(scale - pendingScale) < 0.005f) return

        pendingScale = scale

        // skaliraj preview (real-time)
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

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
