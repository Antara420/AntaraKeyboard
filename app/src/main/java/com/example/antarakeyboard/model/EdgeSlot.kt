package com.example.antarakeyboard.model

import com.example.antarakeyboard.data.EdgePos

enum class EdgeActionType {
    SHIFT, BACKSPACE, ENTER, SPACE, CHAR, NONE, EMOJI_PICKER
}

data class EdgeSlot(
    val index: Int,
    val side: EdgePos.Side,
    val type: EdgeActionType,
    val value: String? = null
) {
    val label: String
        get() =
            when (type) {
                EdgeActionType.SHIFT -> "⇧"
                EdgeActionType.BACKSPACE -> "⌫"
                EdgeActionType.ENTER -> "↵"
                EdgeActionType.SPACE -> "␣"
                EdgeActionType.CHAR -> value ?: "?"
                EdgeActionType.EMOJI_PICKER -> "😊"
                EdgeActionType.NONE -> ""
            }
}