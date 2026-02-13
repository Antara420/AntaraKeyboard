package com.example.antarakeyboard.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import com.example.antarakeyboard.R

class SpecialCharsDialog(
    context: Context,
    private val chars: List<String>,
    private val onCharSelected: (String) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_special_chars)

        val grid = findViewById<GridLayout>(R.id.specialCharsGrid)

        chars.forEach { char ->
            val btn = Button(context).apply {
                text = char
                textSize = 18f
                setOnClickListener {
                    onCharSelected(char)
                    dismiss()
                }
            }
            grid.addView(btn)
        }
    }
}