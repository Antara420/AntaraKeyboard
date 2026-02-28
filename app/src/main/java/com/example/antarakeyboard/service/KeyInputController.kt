package com.example.antarakeyboard.service.input

import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.TextView
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.service.MyKeyboardService

class KeyInputController(
    private val service: MyKeyboardService
) {

    private var swipeStartX = 0f
    private var swipeStartY = 0f

    fun handleTouch(view: TextView, e: MotionEvent): Boolean {
        when (e.action) {

            MotionEvent.ACTION_DOWN -> {
                swipeStartX = e.x
                swipeStartY = e.y

                val t = view.text.toString()
                if (t !in listOf("⇧", " ", "123", "↵", "⌫", "ABC", "abc")) {
                    service.showPreview(view)
                }

                view.isPressed = true
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                service.hidePreview()

                val dy = e.y - swipeStartY
                val text = view.text.toString()

                if (dy < -60 && text.length == 1 && text[0].isLetter()) {
                    service.currentInputConnection?.commitText(text.uppercase(), 1)
                    return true
                }

                handleClick(text)
                return true
            }
        }
        return false
    }

    fun handleClick(text: String) {
        when (text) {
            "⇧" -> service.toggleShift()
            "⌫" -> service.currentInputConnection?.deleteSurroundingText(1, 0)
            "↵" -> service.currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            )
            " " -> service.currentInputConnection?.commitText(" ", 1)

            else -> service.commitText(text)
        }
    }
}