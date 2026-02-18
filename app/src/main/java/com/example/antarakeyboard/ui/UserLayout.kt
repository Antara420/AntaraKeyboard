package com.example.antarakeyboard.ui

import android.app.Dialog
import android.content.ClipData
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyShape

class UserLayoutDialog(
    private val context: Context,
    initial: KeyboardConfig,
    private val onSaved: (KeyboardConfig) -> Unit

) {
    private val lockedLabels = setOf("⇧", "⌫")
    private fun isLocked(key: KeyConfig) = key.label in lockedLabels
    // radimo na kopiji da ne dira original dok ne stisneš Save
    private val cfg: KeyboardConfig = deepCopy(initial)
    private val TAG_SHAKE = 987654321

    private var dialog: Dialog? = null

    private var keyboardContainer: LinearLayout? = null

    private var selectedA: KeyConfig? = null
    private var selectedB: KeyConfig? = null

    // map KeyConfig -> View
    private val keyToView = linkedMapOf<KeyConfig, KeyView>()

    // animacije
    private val shakingViews = mutableListOf<View>()
    private val bounceViews = mutableSetOf<View>()

    fun show() {
        val d = Dialog(context)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(10))
        }

        // naslov
        root.addView(TextView(context).apply {
            text = "Set Layout"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        })

        // scroll + container za tipkovnicu (da ne ode previsoko)
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

        // donji gumbi
        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }

        val btnSwap = Button(context).apply {
            text = "Zamijeni"
            isAllCaps = false
            setOnClickListener { swapSelected() }
        }

        val spacer = Space(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }

        val btnSave = Button(context).apply {
            text = "Spremi"
            isAllCaps = false
            setOnClickListener {
                onSaved(cfg)
                d.dismiss()
            }
        }

        bottom.addView(btnSwap)
        bottom.addView(spacer)
        bottom.addView(btnSave)
        root.addView(bottom)

        d.setContentView(root)
        d.setCancelable(true)

        dialog = d

        buildKeyboardUI()
        startIdleShake()

        d.setOnDismissListener { stopAllAnims() }

        // tek sad show
        d.show()

        // i tek nakon show možemo sigurno setLayout
        d.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        d.window?.setGravity(Gravity.CENTER)
    }


    /* -----------------------------
       BUILD UI
       ----------------------------- */

    private fun buildKeyboardUI() {
        val userShape = KeyboardPrefs.getShape(context)

        val container = keyboardContainer ?: return
        container.removeAllViews()

        keyToView.clear()
        shakingViews.clear()
        bounceViews.clear()
        selectedA = null
        selectedB = null

        fun buildRow(keys: List<KeyConfig>) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }

            // ✅ NE prikazuj ⇧ i ⌫ u Set Layout popup-u
            val editableKeys = keys.filterNot { isLocked(it) }

            editableKeys.forEach { key ->
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


        if (cfg.specialLeft.isNotEmpty()) buildRow(cfg.specialLeft)
        cfg.rows.forEach { buildRow(it.keys) }
        if (cfg.specialRight.isNotEmpty()) buildRow(cfg.specialRight)
    }

    private fun createKeyView(key: KeyConfig, userShape: KeyShape): KeyView {
        return KeyView(context).apply {
            text = key.label
            gravity = Gravity.CENTER
            textSize = 16f
            includeFontPadding = false
            isAllCaps = false

            shape = userShape
            isSpecial = false
            setTextColor(0xFFFFFFFF.toInt())

            val locked = this@UserLayoutDialog.isLocked(key)

            // (opcionalno) vizualno označi locked tipke u editoru
            if (locked) {
                alpha = 0.45f
            } else {
                alpha = 1f
            }

            setOnClickListener {
                if (!locked) this@UserLayoutDialog.onKeyClicked(key)
            }

            setOnLongClickListener {
                if (locked) return@setOnLongClickListener false
                this@UserLayoutDialog.startDragForKey(this, key)
                true
            }

            setOnDragListener { v, e ->
                if (locked) return@setOnDragListener false
                this@UserLayoutDialog.handleDrop(v as KeyView, key, e)
            }
        }
    }


    /* -----------------------------
       SELECT + SWAP
       ----------------------------- */

    private fun onKeyClicked(key: KeyConfig) {
        if (isLocked(key)) return

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

    private fun swapSelected() {
        val a = selectedA
        val b = selectedB
        if (a == null || b == null) {
            Toast.makeText(context, "Odaberi 2 tipke", Toast.LENGTH_SHORT).show()
            return
        }
        swapKeys(a, b)
        afterLayoutChanged() // ✅ refresh + odznači
    }

    private fun afterLayoutChanged() {
        // nakon swap/drag: rebuild + odznači + idle shake
        buildKeyboardUI()
        clearSelection()
    }

    private fun clearSelection() {
        selectedA = null
        selectedB = null
        applySelectionUI()
    }

    private fun swapKeys(a: KeyConfig, b: KeyConfig) {
        val tmp = a.label
        a.label = b.label
        b.label = tmp

        keyToView[a]?.text = a.label
        keyToView[b]?.text = b.label
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

    /* -----------------------------
       DRAG & DROP (swap on drop)
       ----------------------------- */

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
                if (isLocked(srcKey) || isLocked(targetKey)) return true


                if (srcKey != targetKey) {
                    swapKeys(srcKey, targetKey)
                    afterLayoutChanged() // ✅ rebuild + clear selection
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

    /* -----------------------------
       ANIM: idle shake + bounce
       ----------------------------- */

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

    private fun stopAllAnims() {
        stopIdleShake()
        keyToView.values.forEach { stopBounce(it) }
        bounceViews.clear()
    }

    /* -----------------------------
       Utils
       ----------------------------- */

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private fun deepCopy(src: KeyboardConfig): KeyboardConfig {
        return KeyboardConfig(
            rows = src.rows.map { row ->
                row.copy(
                    keys = row.keys.map {
                        it.copy(longPressBindings = it.longPressBindings.toMutableList())
                    }.toMutableList()
                )
            }.toMutableList(),
            specialLeft = src.specialLeft.map { it.copy(longPressBindings = it.longPressBindings.toMutableList()) }.toMutableList(),
            specialRight = src.specialRight.map { it.copy(longPressBindings = it.longPressBindings.toMutableList()) }.toMutableList()
        )
    }
}
