package com.example.antarakeyboard

object SpecialChars {

    val DIACRITICS = listOf(
        "á","à","ä","â","ã","å",
        "é","è","ë","ê",
        "í","ì","ï","î",
        "ó","ò","ö","ô","õ",
        "ú","ù","ü","û",
        "č","ć","š","ž","đ"
    )

    val CURRENCY = listOf("€","£","¥","¢","₿","₹")

    val SYMBOLS = listOf(
        "@","#","&","%","*","+","=",
        "!","?","~","^","|","\\"
    )

    val BRACKETS = listOf(
        "(",")","[","]","{","}"
    )

    val PUNCTUATION = listOf(
        ".",",",";",":","'","\"","…"
    )

    val MATH = listOf(
        "±","÷","×","√","∞","≈","≠"
    )

    val ARROWS = listOf("←","↑","→","↓","↵")

    val ALL =
        DIACRITICS +
                CURRENCY +
                SYMBOLS +
                BRACKETS +
                PUNCTUATION +
                MATH +
                ARROWS
}