package com.example.antarakeyboard


import android.os.Bundle
import android.widget.RadioButton
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.antarakeyboard.prefs.KeyShape
import com.example.antarakeyboard.prefs.KeyboardPrefs


class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val seek = findViewById<SeekBar>(R.id.sizeSeek)
        val preview = findViewById<ShapePreviewView>(R.id.preview)
        val hex = findViewById<RadioButton>(R.id.hexBtn)
        val tri = findViewById<RadioButton>(R.id.triBtn)


        val scale = KeyboardPrefs.getScale(this)
        seek.progress = ((scale - 0.7f) * 100).toInt()


        preview.shape = KeyboardPrefs.getShape(this)


        if (preview.shape == KeyShape.HEX) hex.isChecked = true else tri.isChecked = true


        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) {
                KeyboardPrefs.setScale(this@MainActivity, 0.7f + v / 100f)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })


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
    }
}