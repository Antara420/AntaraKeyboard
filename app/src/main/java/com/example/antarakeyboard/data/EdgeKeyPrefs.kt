package com.example.antarakeyboard.data  // ili gdje ti već stoji

// ✅ Ovo ide NA VRH ili ispod package/importa, ali IZVAN object-a
data class EdgePos(
    val row: Int,
    val side: Side
) {
    enum class Side {
        LEFT, RIGHT
    }
}

object EdgeKeyPrefs {

    private const val PREF = "edge_keys"
    private const val SHIFT_ROW = "shift_row"
    private const val SHIFT_SIDE = "shift_side"
    private const val BKSP_ROW = "bksp_row"
    private const val BKSP_SIDE = "bksp_side"

    fun getShift(ctx: android.content.Context): EdgePos {
        val sp = ctx.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
        val row = sp.getInt(SHIFT_ROW, 3)
        val side = sp.getString(SHIFT_SIDE, "LEFT") ?: "LEFT"
        return EdgePos(row, EdgePos.Side.valueOf(side))
    }

    fun getBackspace(ctx: android.content.Context): EdgePos {
        val sp = ctx.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
        val row = sp.getInt(BKSP_ROW, 3)
        val side = sp.getString(BKSP_SIDE, "RIGHT") ?: "RIGHT"
        return EdgePos(row, EdgePos.Side.valueOf(side))
    }

    fun setShift(ctx: android.content.Context, pos: EdgePos) {
        val sp = ctx.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
        sp.edit()
            .putInt(SHIFT_ROW, pos.row)
            .putString(SHIFT_SIDE, pos.side.name)
            .apply()
    }

    fun setBackspace(ctx: android.content.Context, pos: EdgePos) {
        val sp = ctx.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
        sp.edit()
            .putInt(BKSP_ROW, pos.row)
            .putString(BKSP_SIDE, pos.side.name)
            .apply()
    }
}
