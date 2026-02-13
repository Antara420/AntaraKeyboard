package com.example.antarakeyboard

import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.ui.BindLongPressDialog
import com.example.antarakeyboard.ui.ShapePreviewView

class MainActivity : AppCompatActivity() {

    private lateinit var preview: ShapePreviewView

    private lateinit var hex: RadioButton
    private lateinit var tri: RadioButton
    private lateinit var circle: RadioButton
    private lateinit var cube: RadioButton

    private lateinit var seek: SeekBar
    private lateinit var bindLPButton: Button

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

        // --- Load saved scale ---
        val scale = KeyboardPrefs.getScale(this)
        seek.progress = ((scale - 0.7f) * 100f).toInt().coerceIn(0, 100)

        // --- Load saved shape ---
        val savedShape = KeyboardPrefs.getShape(this)
        preview.shape = savedShape
        setCheckedForShape(savedShape)

        // --- SeekBar Listener ---
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                KeyboardPrefs.setScale(this@MainActivity, 0.7f + progress / 100f)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // --- Shape RadioButtons ---
        hex.setOnCheckedChangeListener { _, checked ->
            if (checked) applyShape(KeyShape.HEX)
        }
        tri.setOnCheckedChangeListener { _, checked ->
            if (checked) applyShape(KeyShape.TRIANGLE)
        }
        circle.setOnCheckedChangeListener { _, checked ->
            if (checked) applyShape(KeyShape.CIRCLE)
        }
        cube.setOnCheckedChangeListener { _, checked ->
            if (checked) applyShape(KeyShape.CUBE)
        }

        // --- Bind Long Press Button ---
        bindLPButton.setOnClickListener {
            BindLongPressDialog(this, KeyboardPrefs.loadLayout(this)) { key, selectedChar ->
                Toast.makeText(
                    this,
                    "Bind added: $key → $selectedChar",
                    Toast.LENGTH_SHORT
                ).show()
            }.show()
        }
    }

    private fun applyShape(shape: KeyShape) {
        preview.shape = shape
        KeyboardPrefs.setShape(this, shape)
        // radio se već sam checka, ali ovo drži stanje konzistentnim ako budeš mijenjao programatski
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
}
