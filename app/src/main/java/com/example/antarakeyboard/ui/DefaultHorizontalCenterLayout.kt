package com.example.antarakeyboard.ui

import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.RowConfig

val defaultHorizontalCenterLayout = KeyboardConfig(
    rows = mutableListOf(
        RowConfig(
            mutableListOf(
                KeyConfig("."), KeyConfig("|"), KeyConfig(","), KeyConfig("÷"),
                KeyConfig("{"), KeyConfig("}"), KeyConfig("0")
            )
        ),
        RowConfig(
            mutableListOf(
                KeyConfig("#"), KeyConfig("?"), KeyConfig("!"), KeyConfig("@"),
                KeyConfig("1"), KeyConfig("2"), KeyConfig("3")
            )
        ),
        RowConfig(
            mutableListOf(
                KeyConfig("+"), KeyConfig("-"), KeyConfig("%"), KeyConfig("&"),
                KeyConfig("4"), KeyConfig("5"), KeyConfig("6")
            )
        ),
        RowConfig(
            mutableListOf(
                KeyConfig("~"), KeyConfig("*"), KeyConfig("€"), KeyConfig("$"),
                KeyConfig("7"), KeyConfig("8"), KeyConfig("9")
            )
        )
    ),
    specialLeft = mutableListOf(),
    specialRight = mutableListOf()
)