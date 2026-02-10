object SpecialChars {

    val DIACRITICS = listOf(
        "á","à","ä","â","ã","å",
        "é","è","ë","ê",
        "í","ì","ï","î",
        "ó","ò","ö","ô","õ",
        "ú","ù","ü","û"
    )

    val CURRENCY = listOf("€","£","¥","¢")
    val SYMBOLS = listOf("@","#","&","%","*","+","=")
    val ARROWS = listOf("←","↑","→","↓","↵")

    val ALL = DIACRITICS + CURRENCY + SYMBOLS + ARROWS
}
