package com.example.antarakeyboard.model

/* =========================
   DATA MODELS
   ========================= */

data class KeyConfig(
    var label: String,
    var longPressBindings: MutableList<String> = mutableListOf()
)

data class RowConfig(
    var keys: MutableList<KeyConfig>
)

data class KeyboardConfig(
    var rows: MutableList<RowConfig>,
    var specialLeft: MutableList<KeyConfig> = mutableListOf(),
    var specialRight: MutableList<KeyConfig> = mutableListOf()
)

/* =========================
   HELPERS
   ========================= */

fun KeyboardConfig.findKey(label: String): KeyConfig? {
    rows.forEach { row ->
        row.keys.firstOrNull { it.label == label }?.let { return it }
    }
    return null
}

fun KeyboardConfig.addLongPress(keyLabel: String, char: String) {
    val key = findKey(keyLabel) ?: return
    if (!key.longPressBindings.contains(char)) {
        key.longPressBindings.add(char)
    }
}
