package com.example.antarakeyboard

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.widget.TextView
class BindLongPressDialog(
    context: Context,
    private val currentKey: String,
    private val onBindSelected: (String, String) -> Unit
) : Dialog(context) {

    private lateinit var specialCharRecycler: RecyclerView
    private lateinit var saveButton: Button

    private val specialChars = SpecialChars.ALL // Pretpostavljam da je ovo lista stringova sa svim posebnim znakovima
    private var selectedSpecialChar: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_bind_long_press)

        saveButton = findViewById(R.id.saveBindButton)
        saveButton.isEnabled = false

        saveButton.setOnClickListener {
            selectedSpecialChar?.let { char ->
                onBindSelected(currentKey, char)
                dismiss()
            }
        }

        specialCharRecycler = findViewById(R.id.specialCharRecycler)
        specialCharRecycler.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

        specialCharRecycler.adapter = CharSelectorAdapter(specialChars) { char ->
            selectedSpecialChar = char
            saveButton.isEnabled = true
        }
    }

    class CharSelectorAdapter(
        private val items: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<CharSelectorAdapter.ViewHolder>() {

        private var selectedPos = RecyclerView.NO_POSITION

        inner class ViewHolder(val button: Button) : RecyclerView.ViewHolder(button) {
            fun bind(char: String, isSelected: Boolean) {
                button.text = char
                button.setBackgroundColor(
                    if (isSelected) 0xFFFFCC80.toInt() else 0x00000000
                )
                button.setOnClickListener {
                    val old = selectedPos
                    selectedPos = adapterPosition
                    notifyItemChanged(old)
                    notifyItemChanged(selectedPos)
                    onItemClick(char)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val btn = Button(parent.context).apply {
                isAllCaps = false
                textSize = 18f
                setPadding(16, 16, 16, 16)
            }
            return ViewHolder(btn)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], position == selectedPos)
        }

        override fun getItemCount() = items.size
    }
}
