package com.example.antarakeyboard.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.antarakeyboard.R
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.SpecialChars


data class LongPressBind(val keyLabel: String, val charValue: String)

class BindLongPressDialog(
    context: Context,
    private val keyboardConfig: KeyboardConfig,
    private val onBindSelected: (LongPressBind) -> Unit
) : Dialog(context) {

    private lateinit var keyRecycler: RecyclerView
    private lateinit var charRecycler: RecyclerView
    private lateinit var saveButton: Button

    private var selectedKeyLabel: String? = null
    private var selectedCharValue: String? = null

    // ✅ samo "normalne" tipke kao key (bez specijalnih i bez razmaka)
    private val nonBindableKeys = setOf("⇧", "⌫", "↵", "123", "ABC", " ")
    private val allKeyLabels: List<String> = keyboardConfig.rows
        .flatMap { row -> row.keys.map { it.label } }
        .filter { it !in nonBindableKeys }
        .distinct()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_bind_long_press)

        keyRecycler = findViewById(R.id.keyListRecycler)
        charRecycler = findViewById(R.id.specialCharRecycler)
        saveButton = findViewById(R.id.saveBindButton)

        saveButton.isEnabled = false

        keyRecycler.layoutManager = LinearLayoutManager(context)
        keyRecycler.adapter = SelectorAdapter(allKeyLabels) { pickedKey ->
            selectedKeyLabel = pickedKey
            updateSaveState()
        }

        charRecycler.layoutManager = LinearLayoutManager(context)
        charRecycler.adapter = SelectorAdapter(SpecialChars.ALL) { pickedChar ->
            selectedCharValue = pickedChar
            updateSaveState()
        }

        saveButton.setOnClickListener {
            val key = selectedKeyLabel
            val ch = selectedCharValue
            if (key != null && ch != null) {
                // ✅ ne može se zamijeniti redoslijed
                onBindSelected(LongPressBind(keyLabel = key, charValue = ch))
                dismiss()
            }
        }
    }

    private fun updateSaveState() {
        saveButton.isEnabled = selectedKeyLabel != null && selectedCharValue != null
    }

    /* ───────── ADAPTER ───────── */

    class SelectorAdapter(
        private val items: List<String>,
        private val onSelect: (String) -> Unit
    ) : RecyclerView.Adapter<SelectorAdapter.VH>() {

        private var selected = RecyclerView.NO_POSITION

        inner class VH(val btn: Button) : RecyclerView.ViewHolder(btn)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = Button(parent.context).apply {
                isAllCaps = false
                textSize = 18f
            }
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, pos: Int) {
            holder.btn.text = items[pos]
            holder.btn.setBackgroundColor(
                if (pos == selected) 0xFFFFCC80.toInt() else 0x00000000
            )
            holder.btn.setOnClickListener {
                val old = selected
                selected = pos
                if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
                notifyItemChanged(selected)
                onSelect(items[pos])
            }
        }

        override fun getItemCount() = items.size
    }
}
