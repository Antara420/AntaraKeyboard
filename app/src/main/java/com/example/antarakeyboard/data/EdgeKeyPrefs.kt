package com.example.antarakeyboard.data

import android.content.Context

data class EdgePos(
    val row: Int,
    val side: Side
) {
    enum class Side { LEFT, RIGHT }
}

object EdgeKeyPrefs {

    private const val PREF = "edge_keys"
    private const val SHIFT_ROW = "shift_row"
    private const val SHIFT_SIDE = "shift_side"
    private const val BKSP_ROW = "bksp_row"
    private const val BKSP_SIDE = "bksp_side"

    fun getShift(ctx: Context): EdgePos {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val row = sp.getInt(SHIFT_ROW, 3)
        val side = sp.getString(SHIFT_SIDE, EdgePos.Side.LEFT.name) ?: EdgePos.Side.LEFT.name
        return EdgePos(row, EdgePos.Side.valueOf(side))
    }

    fun getBackspace(ctx: Context): EdgePos {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val row = sp.getInt(BKSP_ROW, 3)
        val side = sp.getString(BKSP_SIDE, EdgePos.Side.RIGHT.name) ?: EdgePos.Side.RIGHT.name
        return EdgePos(row, EdgePos.Side.valueOf(side))
    }

    fun setShift(ctx: Context, pos: EdgePos) {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit()
            .putInt(SHIFT_ROW, pos.row)
            .putString(SHIFT_SIDE, pos.side.name)
            .apply()
    }

    fun setBackspace(ctx: Context, pos: EdgePos) {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit()
            .putInt(BKSP_ROW, pos.row)
            .putString(BKSP_SIDE, pos.side.name)
            .apply()
    }
}
