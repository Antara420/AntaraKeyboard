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
        bindLPButton = findViewById(R.id.bindLPButton)

        // --- Load saved scale & shape ---
        val scale = KeyboardPrefs.getScale(this)
        seek.progress = ((scale - 0.7f) * 100).toInt()

        preview.shape = KeyboardPrefs.getShape(this)
        if (preview.shape == KeyShape.HEX) hex.isChecked = true else tri.isChecked = true

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
            if (checked) {
                preview.shape = KeyShape.HEX
                KeyboardPrefs.setShape(this, KeyShape.HEX)
            }
        }

        tri.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                preview.shape = KeyShape.TRIANGLE
                KeyboardPrefs.setShape(this, KeyShape.TRIANGLE)
            }
        }

        // --- Bind Long Press Button ---
        bindLPButton.setOnClickListener {
            // Otvara dialog sa trenutnim KeyboardConfigom
            BindLongPressDialog(this, KeyboardPrefs.loadLayout(this)) { key, selectedChar ->
                Toast.makeText(
                    this,
                    "Bind added: $key → $selectedChar",
                    Toast.LENGTH_SHORT
                ).show()
            }.show()
        }
    }
}
