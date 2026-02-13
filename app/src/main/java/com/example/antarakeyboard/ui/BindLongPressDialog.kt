package com.example.antarakeyboard.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.antarakeyboard.R
import com.example.antarakeyboard.model.KeyboardConfig

class BindLongPressDialog(
    context: Context,
    private val keyboardConfig: KeyboardConfig,
    private val onBindSelected: (String, String) -> Unit
) : Dialog(context) {

    private lateinit var keyRecycler: RecyclerView
    private lateinit var charRecycler: RecyclerView
    private lateinit var saveButton: Button

    private var selectedKey: String? = null
    private var selectedChar: String? = null

    private val allKeys = keyboardConfig.rows.flatMap { row ->
        row.keys.map { it.label }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_bind_long_press)

        keyRecycler = findViewById(R.id.keyListRecycler)
        charRecycler = findViewById(R.id.specialCharRecycler)
        saveButton = findViewById(R.id.saveBindButton)

        saveButton.isEnabled = false

        keyRecycler.layoutManager = LinearLayoutManager(context)
        keyRecycler.adapter = SelectorAdapter(allKeys) {
            selectedKey = it
            updateSaveState()
        }

        charRecycler.layoutManager = LinearLayoutManager(context)
        charRecycler.adapter = SelectorAdapter(SpecialChars.ALL) {
            selectedChar = it
            updateSaveState()
        }

        saveButton.setOnClickListener {
            val k = selectedKey
            val c = selectedChar
            if (k != null && c != null) {
                onBindSelected(k, c)
                dismiss()
            }
        }
    }

    private fun updateSaveState() {
        saveButton.isEnabled = selectedKey != null && selectedChar != null
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
                notifyItemChanged(old)
                notifyItemChanged(selected)
                onSelect(items[pos])
            }
        }

        override fun getItemCount() = items.size
    }
}