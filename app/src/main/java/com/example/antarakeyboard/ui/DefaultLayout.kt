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
        RowConfig(
            mutableListOf(
                KeyConfig("w"), KeyConfig("e"), KeyConfig("t"),
                KeyConfig("z"), KeyConfig("i"), KeyConfig("o")
            )
        ),

        // 2) q a r g u l p
        RowConfig(
            mutableListOf(
                KeyConfig("q"), KeyConfig("a"), KeyConfig("r"),
                KeyConfig("g"), KeyConfig("u"), KeyConfig("l"), KeyConfig("p")
            )
        ),

        // 3) side . space f h space ? side
        RowConfig(
            mutableListOf(
                KeyConfig("⇧"), KeyConfig("."), KeyConfig(" "),
                KeyConfig("f"), KeyConfig("h"), KeyConfig(" "),
                KeyConfig("?"), KeyConfig("⌫")
            )
        ),

        // 4) y s d n m j k
        RowConfig(
            mutableListOf(
                KeyConfig("y"), KeyConfig("s"), KeyConfig("d"),
                KeyConfig("n"), KeyConfig("m"), KeyConfig("j"), KeyConfig("k")
            )
        ),

        // 5) x c v b 123 ↵
        RowConfig(
            mutableListOf(
                KeyConfig("x"), KeyConfig("c"), KeyConfig("v"),
                KeyConfig("b"), KeyConfig("123"), KeyConfig("↵")
            )
        )
    )
).also { cfg ->
    listOf("\"", ",", ":", ";").forEach { ch ->
        cfg.addLongPress(".", ch)
    }

    listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch ->
        cfg.addLongPress("?", ch)
    }
}

val defaultFourRowKeyboardLayout: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(
        // 1) w e t z i o
        RowConfig(
            mutableListOf(
                KeyConfig("w"), KeyConfig("e"), KeyConfig("t"),
                KeyConfig("z"), KeyConfig("i"), KeyConfig("o")
            )
        ),

        // 2) q a r g u l p
        RowConfig(
            mutableListOf(
                KeyConfig("q"), KeyConfig("a"), KeyConfig("r"),
                KeyConfig("g"), KeyConfig("u"), KeyConfig("l"), KeyConfig("p")
            )
        ),

        // 3) y s d n m j k
        RowConfig(
            mutableListOf(
                KeyConfig("y"), KeyConfig("s"), KeyConfig("d"),
                KeyConfig("n"), KeyConfig("m"), KeyConfig("j"), KeyConfig("k")
            )
        ),

        // 4) ⇧ . f h ? ⌫ 123 ↵
        RowConfig(
            mutableListOf(
                KeyConfig("⇧"), KeyConfig("."), KeyConfig("f"), KeyConfig("h"),
                KeyConfig("?"), KeyConfig("⌫"), KeyConfig("123"), KeyConfig("↵")
            )
        )
    )
).also { cfg ->
    listOf("\"", ",", ":", ";").forEach { ch ->
        cfg.addLongPress(".", ch)
    }

    listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch ->
        cfg.addLongPress("?", ch)
    }
}

val defaultThreeRowKeyboardLayoutQwertz: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(
        // 1) q w e r t z u i o p
        RowConfig(
            mutableListOf(
                KeyConfig("q"), KeyConfig("w"), KeyConfig("e"), KeyConfig("r"), KeyConfig("t"),
                KeyConfig("z"), KeyConfig("u"), KeyConfig("i"), KeyConfig("o"), KeyConfig("p")
            )
        ),

        // 2) a s d f g h j k l .
        RowConfig(
            mutableListOf(
                KeyConfig("a"), KeyConfig("s"), KeyConfig("d"), KeyConfig("f"), KeyConfig("g"),
                KeyConfig("h"), KeyConfig("j"), KeyConfig("k"), KeyConfig("l"), KeyConfig(".")
            )
        ),

        // 3) ⇧ y x c v b n m ? 123 ↵ ⌫
        RowConfig(
            mutableListOf(
                KeyConfig("⇧"), KeyConfig("y"), KeyConfig("x"), KeyConfig("c"),
                KeyConfig("v"), KeyConfig("b"), KeyConfig("n"), KeyConfig("m"),
                KeyConfig("?"), KeyConfig("123"), KeyConfig("↵"), KeyConfig("⌫")
            )
        )
    )
).also { cfg ->
    listOf("\"", ",", ":", ";").forEach { ch ->
        cfg.addLongPress(".", ch)
    }

    listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch ->
        cfg.addLongPress("?", ch)
    }
}

// =========================
// NUMERIC (5 rows)
// =========================
val defaultNumericLayout: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(

        // 1) ~ 1 2 3 4 <
        RowConfig(
            mutableListOf(
                KeyConfig("~"), KeyConfig("1"), KeyConfig("2"),
                KeyConfig("3"), KeyConfig("4"), KeyConfig("<")
            )
        ),

        // 2) € + 5 6 7 ( )
        RowConfig(
            mutableListOf(
                KeyConfig("€"), KeyConfig("+"), KeyConfig("5"),
                KeyConfig("6"), KeyConfig("7"), KeyConfig("("), KeyConfig(")")
            )
        ),

        // 3) side . space 8 9 space ? side
        RowConfig(
            mutableListOf(
                KeyConfig("⇧"), KeyConfig("."), KeyConfig(" "),
                KeyConfig("8"), KeyConfig("9"), KeyConfig(" "),
                KeyConfig("?"), KeyConfig("⌫")
            )
        ),

        // 4) > - _ 0 * / !
        RowConfig(
            mutableListOf(
                KeyConfig(">"), KeyConfig("-"), KeyConfig("_"),
                KeyConfig("0"), KeyConfig("*"), KeyConfig("/"), KeyConfig("!")
            )
        ),

        // 5) & % @ # abc ↵
        RowConfig(
            mutableListOf(
                KeyConfig("&"), KeyConfig("%"), KeyConfig("@"),
                KeyConfig("#"), KeyConfig("abc"), KeyConfig("↵")
            )
        )
    )
).also { cfg ->
    listOf("\"", ",", ":", ";").forEach { ch ->
        cfg.addLongPress(".", ch)
    }
    listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch ->
        cfg.addLongPress("?", ch)
    }
}

// =========================
// NUMERIC (4 rows)
// =========================
val defaultFourRowNumericLayout: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(

        // 1) ~ 1 2 3 4 <
        RowConfig(
            mutableListOf(
                KeyConfig("~"), KeyConfig("1"), KeyConfig("2"),
                KeyConfig("3"), KeyConfig("4"), KeyConfig("<")
            )
        ),

        // 2) € + 5 6 7 ( )
        RowConfig(
            mutableListOf(
                KeyConfig("€"), KeyConfig("+"), KeyConfig("5"),
                KeyConfig("6"), KeyConfig("7"), KeyConfig("("), KeyConfig(")")
            )
        ),

        // 3) > - _ 0 * / !
        RowConfig(
            mutableListOf(
                KeyConfig(">"), KeyConfig("-"), KeyConfig("_"),
                KeyConfig("0"), KeyConfig("*"), KeyConfig("/"), KeyConfig("!")
            )
        ),

        // 4) ⇧ . 8 9 ? ⌫ abc ↵
        RowConfig(
            mutableListOf(
                KeyConfig("⇧"), KeyConfig("."), KeyConfig("8"), KeyConfig("9"),
                KeyConfig("?"), KeyConfig("⌫"), KeyConfig("abc"), KeyConfig("↵")
            )
        )
    )
).also { cfg ->
    listOf("\"", ",", ":", ";").forEach { ch ->
        cfg.addLongPress(".", ch)
    }
    listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch ->
        cfg.addLongPress("?", ch)
    }
}

// =========================
// NUMERIC (3 rows)
// =========================
val defaultThreeRowNumericLayout: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(

        // 1) 1 2 3 4 5 6 7 8 9 0
        RowConfig(
            mutableListOf(
                KeyConfig("1"), KeyConfig("2"), KeyConfig("3"), KeyConfig("4"), KeyConfig("5"),
                KeyConfig("6"), KeyConfig("7"), KeyConfig("8"), KeyConfig("9"), KeyConfig("0")
            )
        ),

        // 2) ~ + - * / = ( ) . ?
        RowConfig(
            mutableListOf(
                KeyConfig("~"), KeyConfig("+"), KeyConfig("-"), KeyConfig("*"), KeyConfig("/"),
                KeyConfig("="), KeyConfig("("), KeyConfig(")"), KeyConfig("."), KeyConfig("?")
            )
        ),

        // 3) ⇧ € % @ # & _ ! abc ↵ ⌫
        RowConfig(
            mutableListOf(
                KeyConfig("⇧"), KeyConfig("€"), KeyConfig("%"), KeyConfig("@"),
                KeyConfig("#"), KeyConfig("&"), KeyConfig("_"), KeyConfig("!"),
                KeyConfig("abc"), KeyConfig("↵"), KeyConfig("⌫")
            )
        )
    )
).also { cfg ->
    listOf("\"", ",", ":", ";").forEach { ch ->
        cfg.addLongPress(".", ch)
    }
    listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch ->
        cfg.addLongPress("?", ch)
    }
}

// velika slova
private fun KeyboardConfig.toUppercaseLetters(): KeyboardConfig {
    fun up(k: KeyConfig): KeyConfig {
        val lbl = k.label
        val newLbl =
            if (lbl.length == 1 && lbl[0].isLetter()) lbl.uppercase()
            else lbl

        return k.copy(
            label = newLbl,
            longPressBindings = k.longPressBindings.toMutableList()
        )
    }

    return copy(
        rows = rows.map { row ->
            row.copy(keys = row.keys.map(::up).toMutableList())
        }.toMutableList(),
        specialLeft = specialLeft.map(::up).toMutableList(),
        specialRight = specialRight.map(::up).toMutableList()
    )
}

val defaultKeyboardLayoutUpper: KeyboardConfig =
    defaultKeyboardLayout.toUppercaseLetters().also { cfg ->
        listOf("\"", ",", ":", ";").forEach { ch -> cfg.addLongPress(".", ch) }
        listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch -> cfg.addLongPress("?", ch) }
    }

val defaultFourRowKeyboardLayoutUpper: KeyboardConfig =
    defaultFourRowKeyboardLayout.toUppercaseLetters().also { cfg ->
        listOf("\"", ",", ":", ";").forEach { ch -> cfg.addLongPress(".", ch) }
        listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch -> cfg.addLongPress("?", ch) }
    }

val defaultThreeRowKeyboardLayoutQwertzUpper: KeyboardConfig =
    defaultThreeRowKeyboardLayoutQwertz.toUppercaseLetters().also { cfg ->
        listOf("\"", ",", ":", ";").forEach { ch -> cfg.addLongPress(".", ch) }
        listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch -> cfg.addLongPress("?", ch) }
    }