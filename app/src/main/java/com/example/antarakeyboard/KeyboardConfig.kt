package com.example.antarakeyboard

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

val myDefaultKeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(
        KeyConfig("⇧")  // Shift lijevo
    ),
    specialRight = mutableListOf(
        KeyConfig("⌫")  // Backspace desno
    ),
    rows = mutableListOf(
        RowConfig(mutableListOf(
            KeyConfig("W"), KeyConfig("E"), KeyConfig("T"), KeyConfig("Z"), KeyConfig("I"), KeyConfig("O")
        )),
        RowConfig(mutableListOf(
            KeyConfig("Q"), KeyConfig("A"), KeyConfig("R"), KeyConfig("G"), KeyConfig("U"), KeyConfig("L"), KeyConfig("P")
        )),
        RowConfig(mutableListOf(
            KeyConfig("Y"), KeyConfig("S"), KeyConfig("D"), KeyConfig("N"), KeyConfig("M"), KeyConfig("J"), KeyConfig("K")
        )),
        RowConfig(mutableListOf(
            KeyConfig("X"), KeyConfig("C"), KeyConfig("V"), KeyConfig("B")
        )),
        RowConfig(mutableListOf(
            KeyConfig("123"), KeyConfig(" "), KeyConfig("↵")  // Switch, space, enter
        ))
    )
)

val myDefaultNumericConfig = KeyboardConfig(
    specialLeft = mutableListOf(
        KeyConfig("⇧")
    ),
    specialRight = mutableListOf(
        KeyConfig("⌫")
    ),
    rows = mutableListOf(
        RowConfig(mutableListOf(
            KeyConfig("^"), KeyConfig("1"), KeyConfig("2"), KeyConfig("3"), KeyConfig("4"), KeyConfig("<")
        )),
        RowConfig(mutableListOf(
            KeyConfig("€"), KeyConfig("+"), KeyConfig("5"), KeyConfig("6"), KeyConfig("7"), KeyConfig("("), KeyConfig(")")
        )),
        RowConfig(mutableListOf(
            KeyConfig("§"), KeyConfig("-"), KeyConfig("_"), KeyConfig("0"), KeyConfig("*"), KeyConfig("/"), KeyConfig("¿")
        )),
        RowConfig(mutableListOf(
            KeyConfig("&"), KeyConfig("%"), KeyConfig("@"), KeyConfig("#")
        )),
        RowConfig(mutableListOf(
            KeyConfig("ABC")
        ))
    )
)

var isUsingUserLayout = false

// Početni user layout je null dok korisnik ne počne uređivati
var userKeyboardConfig: KeyboardConfig? = null

fun initializeUserLayoutIfNeeded() {
    if (!isUsingUserLayout) {
        userKeyboardConfig = deepCopyKeyboardConfig(myDefaultKeyboardConfig)
        isUsingUserLayout = true
    }
}

fun deepCopyKeyboardConfig(original: KeyboardConfig): KeyboardConfig {
    val copiedRows = original.rows.map { row ->
        RowConfig(row.keys.map { key ->
            KeyConfig(key.label, key.longPressBindings.toMutableList())
        }.toMutableList())
    }.toMutableList()

    val copiedSpecialLeft = original.specialLeft.map {
        KeyConfig(it.label, it.longPressBindings.toMutableList())
    }.toMutableList()

    val copiedSpecialRight = original.specialRight.map {
        KeyConfig(it.label, it.longPressBindings.toMutableList())
    }.toMutableList()

    return KeyboardConfig(
        rows = copiedRows,
        specialLeft = copiedSpecialLeft,
        specialRight = copiedSpecialRight
    )
}
