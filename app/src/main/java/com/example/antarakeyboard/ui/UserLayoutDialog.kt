package com.example.antarakeyboard.ui

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import com.example.antarakeyboard.model.KeyboardConfig

class UserLayoutDialog(
    private val context: Context,
    initial: KeyboardConfig,
    private val onSaved: (KeyboardConfig) -> Unit
) {
    private val binder = LayoutEditorBinder(context, initial, onSaved)

    fun show() {
        val d = Dialog(context)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = FrameLayout(context)
        d.setContentView(root)
        d.setCancelable(true)

        binder.bindInto(root)

        d.setOnDismissListener {
            binder.stopAllAnims()
        }

        d.show()

        d.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        d.window?.setGravity(Gravity.CENTER)
    }
}