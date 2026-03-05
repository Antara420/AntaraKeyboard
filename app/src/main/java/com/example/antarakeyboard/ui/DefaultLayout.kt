package com.example.antarakeyboard.ui

import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.RowConfig
import com.example.antarakeyboard.model.addLongPress

val defaultKeyboardLayout: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(
        // 1) w e t z i o
        RowConfig(mutableListOf(
            KeyConfig("w"), KeyConfig("e"), KeyConfig("t"),
            KeyConfig("z"), KeyConfig("i"), KeyConfig("o")
        )),

        // 2) q a r g u l p
        RowConfig(mutableListOf(
            KeyConfig("q"), KeyConfig("a"), KeyConfig("r"),
            KeyConfig("g"), KeyConfig("u"), KeyConfig("l"), KeyConfig("p")
        )),

        // 3) ⇧ . space f h space ? ⌫
        RowConfig(mutableListOf(
            KeyConfig("⇧"), KeyConfig("."), KeyConfig(" "),
            KeyConfig("f"), KeyConfig("h"), KeyConfig(" "),
            KeyConfig("?"), KeyConfig("⌫")
        )),

        // 4) y s d n m j k
        RowConfig(mutableListOf(
            KeyConfig("y"), KeyConfig("s"), KeyConfig("d"),
            KeyConfig("n"), KeyConfig("m"), KeyConfig("j"), KeyConfig("k")
        )),

        // 5) x c v b 123 ↵
        RowConfig(mutableListOf(
            KeyConfig("x"), KeyConfig("c"), KeyConfig("v"),
            KeyConfig("b"), KeyConfig("123"), KeyConfig("↵")
        ))
    )
).also { cfg ->
    // longpress za "." ->  " , : ;
    listOf("\"", ",", ":", ";").forEach { ch ->
        cfg.addLongPress(".", ch)
    }

    // longpress za "?" -> ! ( ) { } [ ]
    listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch ->
        cfg.addLongPress("?", ch)
    }
}


// =========================
// NUMERIC (123) – po tvom layoutu
// =========================
val defaultNumericLayout: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(

        // 1) ~ 1 2 3 4 <
        RowConfig(mutableListOf(
            KeyConfig("~"), KeyConfig("1"), KeyConfig("2"),
            KeyConfig("3"), KeyConfig("4"), KeyConfig("<")
        )),

        // 2) € + 5 6 7 ( )
        RowConfig(mutableListOf(
            KeyConfig("€"), KeyConfig("+"), KeyConfig("5"),
            KeyConfig("6"), KeyConfig("7"), KeyConfig("("), KeyConfig(")")
        )),

        // 3) ⇧ . space 8 9 space ? ⌫
        RowConfig(mutableListOf(
            KeyConfig("⇧"), KeyConfig("."), KeyConfig(" "),
            KeyConfig("8"), KeyConfig("9"), KeyConfig(" "),
            KeyConfig("?"), KeyConfig("⌫")
        )),

        // 4) > - _ 0 * / !
        RowConfig(mutableListOf(
            KeyConfig(">"), KeyConfig("-"), KeyConfig("_"),
            KeyConfig("0"), KeyConfig("*"), KeyConfig("/"), KeyConfig("!")
        )),

        // 5) & % @ # abc ↵
        RowConfig(mutableListOf(
            KeyConfig("&"), KeyConfig("%"), KeyConfig("@"),
            KeyConfig("#"), KeyConfig("abc"), KeyConfig("↵")
        ))
    )
).also { cfg ->
    // da radi i u numeric layoutu
    listOf("\"", ",", ":", ";").forEach { ch ->
        cfg.addLongPress(".", ch)
    }
    listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch ->
        cfg.addLongPress("?", ch)
    }
}


//velika slova
private fun KeyboardConfig.toUppercaseLetters(): KeyboardConfig {
    fun up(k: KeyConfig): KeyConfig {
        val lbl = k.label
        val newLbl =
            if (lbl.length == 1 && lbl[0].isLetter()) lbl.uppercase()
            else lbl

        return k.copy(label = newLbl, longPressBindings = k.longPressBindings.toMutableList())
    }

    return copy(
        rows = rows.map { row -> row.copy(keys = row.keys.map(::up).toMutableList()) }.toMutableList(),
        specialLeft = specialLeft.map(::up).toMutableList(),
        specialRight = specialRight.map(::up).toMutableList()
    )
}

val defaultKeyboardLayoutUpper: KeyboardConfig =
    defaultKeyboardLayout.toUppercaseLetters().also { cfg ->
        // isti longpress kao na loweru
        listOf("\"", ",", ":", ";").forEach { ch -> cfg.addLongPress(".", ch) }
        listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch -> cfg.addLongPress("?", ch) }
    }