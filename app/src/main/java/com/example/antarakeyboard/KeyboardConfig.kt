package com.example.antarakeyboard

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
    var specialLeft: MutableList<KeyConfig>,
    var specialRight: MutableList<KeyConfig>
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

/* =========================
   DEFAULT LAYOUT
   ========================= */

val myDefaultKeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(KeyConfig("⇧")),
    specialRight = mutableListOf(KeyConfig("⌫")),
    rows = mutableListOf(
        RowConfig(mutableListOf(
            KeyConfig("W"), KeyConfig("E"), KeyConfig("T"),
            KeyConfig("Z"), KeyConfig("I"), KeyConfig("O")
        )),
        RowConfig(mutableListOf(
            KeyConfig("Q"), KeyConfig("A"), KeyConfig("R"),
            KeyConfig("G"), KeyConfig("U"), KeyConfig("L"), KeyConfig("P")
        )),
        RowConfig(mutableListOf(
            KeyConfig("Y"), KeyConfig("S"), KeyConfig("D"),
            KeyConfig("N"), KeyConfig("M"), KeyConfig("J"), KeyConfig("K")
        )),
        RowConfig(mutableListOf(
            KeyConfig("X"), KeyConfig("C"), KeyConfig("V"), KeyConfig("B")
        )),
        RowConfig(mutableListOf(
            KeyConfig("123"), KeyConfig(" "), KeyConfig("↵")
        ))
    )
)
