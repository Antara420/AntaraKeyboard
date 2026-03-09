package com.example.antarakeyboard.ui

import android.content.ClipData
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyShape

class LayoutEditorBinder(
    private val context: Context,
    initial: KeyboardConfig,
    private val onSaved: (KeyboardConfig) -> Unit,
    private val lockedLabels: Set<String> = setOf("⇧", "⌫"),
    private val onEmptyKeyClick: ((KeyConfig) -> Unit)? = null,
    private val includeSpecialRows: Boolean = true
) {
    private fun isLocked(key: KeyConfig): Boolean = key.label in lockedLabels
    private fun isEmptyKey(key: KeyConfig): Boolean = key.label.isEmpty()
    private val cfg: KeyboardConfig = deepCopy(initial)
    private val TAG_SHAKE = 987654321

    private var keyboardContainer: LinearLayout? = null

    private var selectedA: KeyConfig? = null
    private var selectedB: KeyConfig? = null

    private val keyToView = linkedMapOf<KeyConfig, KeyView>()
    private val shakingViews = mutableListOf<View>()
    private val bounceViews = mutableSetOf<View>()

    fun bindInto(container: ViewGroup) {
        stopAllAnims()
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
            text = "Set Layout"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        })

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
                (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
            )
        )

        container.addView(root)

        buildKeyboardUI()
        startIdleShake()
    }

    private fun buildKeyboardUI() {
        val userShape = KeyboardPrefs.getShape(context)

        val container = keyboardContainer ?: return
        container.removeAllViews()

        keyToView.clear()
        shakingViews.clear()
        bounceViews.clear()
        selectedA = null
        selectedB = null

        fun shouldHideInEditor(key: KeyConfig): Boolean {
            val label = key.label
            return label == "⇧" || label == "⌫"
        }

        fun buildRow(keys: MutableList<KeyConfig>) {
            val visibleKeys = keys.filterNot { shouldHideInEditor(it) }
            if (visibleKeys.isEmpty()) return

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }

            visibleKeys.forEach { key ->
                val kv = createKeyView(key, userShape)
                row.addView(
                    kv,
                    LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                        marginStart = dp(1)
                        marginEnd = dp(1)
                    }
                )
                keyToView[key] = kv
                shakingViews.add(kv)
            }

            container.addView(row)
        }

        if (includeSpecialRows && cfg.specialLeft.isNotEmpty()) {
            buildRow(cfg.specialLeft)
        }

        cfg.rows.forEach { buildRow(it.keys) }

        if (includeSpecialRows && cfg.specialRight.isNotEmpty()) {
            buildRow(cfg.specialRight)
        }
    }

    private fun createKeyView(key: KeyConfig, userShape: KeyShape): KeyView {
        return KeyView(context).apply {
            text = when {
                key.label.isEmpty() -> ""
                key.label == " " -> "␣"
                else -> key.label
            }

            gravity = Gravity.CENTER
            textSize = 16f
            includeFontPadding = false
            isAllCaps = false
            shape = userShape
            isSpecial = (key.label == "↵")

            val locked = isLocked(key)
            val empty = isEmptyKey(key)

            setTextColor(0xFFFFFFFF.toInt())

            alpha = when {
                locked -> 0.75f
                empty -> 0.30f
                else -> 1f
            }

            setOnClickListener {
                when {
                    empty -> onEmptyKeyClick?.invoke(key)
                    else -> onKeyClicked(key)
                }
            }

            setOnLongClickListener {
                if (empty) return@setOnLongClickListener false
                startDragForKey(this, key)
                true
            }

            setOnDragListener { v, e ->
                handleDrop(v as KeyView, key, e)
            }
        }
    }

    private fun onKeyClicked(key: KeyConfig) {
        if (isEmptyKey(key)) return

        if (selectedA == null || selectedA == key) {
            selectedA = key
        } else if (selectedB == null || selectedB == key) {
            selectedB = key
        } else {
            clearSelection()
            selectedA = key
        }

        applySelectionUI()
    }
    private data class KeyLocation(
        val row: MutableList<KeyConfig>,
        val index: Int
    )

    private fun findKeyLocation(target: KeyConfig): KeyLocation? {
        if (includeSpecialRows && cfg.specialLeft.isNotEmpty()) {
            val i = cfg.specialLeft.indexOf(target)
            if (i != -1) return KeyLocation(cfg.specialLeft, i)
        }

        cfg.rows.forEach { rowCfg ->
            val i = rowCfg.keys.indexOf(target)
            if (i != -1) return KeyLocation(rowCfg.keys, i)
        }

        if (includeSpecialRows && cfg.specialRight.isNotEmpty()) {
            val i = cfg.specialRight.indexOf(target)
            if (i != -1) return KeyLocation(cfg.specialRight, i)
        }

        return null
    }

    private fun afterLayoutChanged() {
        buildKeyboardUI()
        clearSelection()
    }

    private fun clearSelection() {
        selectedA = null
        selectedB = null
        applySelectionUI()
    }

    private fun swapPositions(a: KeyConfig, b: KeyConfig) {
        val locA = findKeyLocation(a) ?: return
        val locB = findKeyLocation(b) ?: return

        val tmp = locA.row[locA.index]
        locA.row[locA.index] = locB.row[locB.index]
        locB.row[locB.index] = tmp
    }

    private fun applySelectionUI() {
        val hasSelection = selectedA != null || selectedB != null
        if (hasSelection) stopIdleShake() else startIdleShake()

        keyToView.forEach { (key, view) ->
            val isSel = (key == selectedA || key == selectedB)

            if (isSel) {
                view.customBgColor = 0xFFFFFFFF.toInt()
                view.setTextColor(0xFF000000.toInt())
                startBounce(view)
            } else {
                view.customBgColor = 0xFF3E3E3E.toInt()
                view.setTextColor(0xFFFFFFFF.toInt())
                stopBounce(view)
            }

            view.invalidate()
        }
    }

    private fun startDragForKey(view: View, key: KeyConfig) {
        val data = ClipData.newPlainText("key_ref", key.hashCode().toString())
        val shadow = View.DragShadowBuilder(view)

        view.tag = key

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(data, shadow, view, 0)
        } else {
            @Suppress("DEPRECATION")
            view.startDrag(data, shadow, view, 0)
        }
    }

    private fun handleDrop(targetView: KeyView, targetKey: KeyConfig, e: DragEvent): Boolean {
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true

            DragEvent.ACTION_DRAG_ENTERED -> {
                targetView.alpha = 0.7f
                return true
            }

            DragEvent.ACTION_DRAG_EXITED -> {
                targetView.alpha = 1f
                return true
            }

            DragEvent.ACTION_DROP -> {
                targetView.alpha = 1f

                val srcView = e.localState as? View ?: return true
                val srcKey = srcView.tag as? KeyConfig ?: return true

                if (isEmptyKey(srcKey) || isEmptyKey(targetKey)) return true

                if (srcKey != targetKey) {
                    swapPositions(srcKey, targetKey)
                    afterLayoutChanged()
                }
                return true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                targetView.alpha = 1f
                return true
            }
        }
        return false
    }

    private fun startIdleShake() {
        if (selectedA != null || selectedB != null) return

        val deg = 8f

        shakingViews.forEach { v ->
            if (v.getTag(TAG_SHAKE) == true) return@forEach
            v.setTag(TAG_SHAKE, true)

            fun loop() {
                if (selectedA != null || selectedB != null) {
                    v.setTag(TAG_SHAKE, false)
                    v.rotation = 0f
                    return
                }

                v.animate().cancel()
                v.animate()
                    .rotation(deg)
                    .setDuration(90)
                    .withEndAction {
                        v.animate()
                            .rotation(-deg)
                            .setDuration(180)
                            .withEndAction {
                                v.animate()
                                    .rotation(0f)
                                    .setDuration(90)
                                    .withEndAction { loop() }
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }

            loop()
        }
    }

    private fun stopIdleShake() {
        shakingViews.forEach { v ->
            v.setTag(TAG_SHAKE, false)
            v.animate().cancel()
            v.rotation = 0f
        }
    }

    private fun startBounce(v: View) {
        if (bounceViews.contains(v)) return
        bounceViews.add(v)

        fun loop() {
            if (!bounceViews.contains(v)) return
            v.animate().cancel()
            v.animate()
                .translationY(-dp(3).toFloat())
                .setDuration(120)
                .withEndAction {
                    if (!bounceViews.contains(v)) return@withEndAction
                    v.animate()
                        .translationY(0f)
                        .setDuration(120)
                        .withEndAction { loop() }
                        .start()
                }
                .start()
        }

        loop()
    }

    private fun stopBounce(v: View) {
        bounceViews.remove(v)
        v.animate().cancel()
        v.translationY = 0f
    }

    fun stopAllAnims() {
        stopIdleShake()
        keyToView.values.forEach { stopBounce(it) }
        bounceViews.clear()
    }

    fun swapSelectedExternally(): Boolean {
        val a = selectedA
        val b = selectedB
        if (a == null || b == null) return false

        swapPositions(a, b)
        afterLayoutChanged()
        return true
    }

    fun saveExternally() {
        onSaved(cfg)
    }

    fun hasTwoSelected(): Boolean {
        return selectedA != null && selectedB != null
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