package com.example.antarakeyboard.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.antarakeyboard.SpecialChars
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyShape

class LongPressEditorBinder(
    private val context: Context,
    initial: KeyboardConfig,
    private val titleText: String,
    private val lockedLabels: Set<String> = setOf("⇧", "⌫", "↵", "123", "ABC", "abc", " ")
) {
    private val cfg: KeyboardConfig = deepCopy(initial)
    private var keyboardContainer: LinearLayout? = null

    fun bindInto(container: ViewGroup) {
        container.removeAllViews()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(10))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(TextView(context).apply {
            text = titleText
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        })

        val hint = TextView(context).apply {
            text = "Tap na tipku za uređivanje long press znakova"
            textSize = 13f
            alpha = 0.75f
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(hint)

        val scroll = ScrollView(context).apply {
            isFillViewport = true
        }

        keyboardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(
            keyboardContainer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.50f).toInt()
            )
        )

        container.addView(root)
        buildKeyboardUI()
    }

    fun getUpdatedConfig(): KeyboardConfig = cfg

    private fun buildKeyboardUI() {
        val userShape = KeyboardPrefs.getShape(context)
        val container = keyboardContainer ?: return
        container.removeAllViews()

        fun buildRow(keys: List<KeyConfig>) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }

            keys.forEach { key ->
                val kv = createKeyView(key, userShape)
                row.addView(
                    kv,
                    LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                        marginStart = dp(1)
                        marginEnd = dp(1)
                    }
                )
            }

            container.addView(row)
        }

        cfg.rows.forEach { buildRow(it.keys) }
    }

    private fun createKeyView(key: KeyConfig, userShape: KeyShape): KeyView {
        val locked = key.label in lockedLabels

        return KeyView(context).apply {
            text = key.label
            gravity = Gravity.CENTER
            textSize = 16f
            includeFontPadding = false
            isAllCaps = false
            shape = userShape
            isSpecial = (key.label == "↵")
            setTextColor(0xFFFFFFFF.toInt())

            alpha = if (locked) 0.55f else 1f

            setOnClickListener {
                if (!locked) {
                    showLongPressPickerForKey(key)
                }
            }
        }
    }

    private fun showLongPressPickerForKey(key: KeyConfig) {
        val allChars = SpecialChars.ALL
        val selected = BooleanArray(allChars.size) { i ->
            key.longPressBindings.contains(allChars[i])
        }

        AlertDialog.Builder(context)
            .setTitle("Long press za: ${key.label}")
            .setMultiChoiceItems(allChars.toTypedArray(), selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                key.longPressBindings.clear()
                allChars.forEachIndexed { index, ch ->
                    if (selected[index]) {
                        key.longPressBindings.add(ch)
                    }
                }
                buildKeyboardUI()
            }
            .show()
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

    private fun deepCopy(src: KeyboardConfig): KeyboardConfig {
        return KeyboardConfig(
            rows = src.rows.map { row ->
                row.copy(
                    keys = row.keys.map {
                        it.copy(longPressBindings = it.longPressBindings.toMutableList())
                    }.toMutableList()
                )
            }.toMutableList(),
            specialLeft = src.specialLeft.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList(),
            specialRight = src.specialRight.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList()
        )
    }
}