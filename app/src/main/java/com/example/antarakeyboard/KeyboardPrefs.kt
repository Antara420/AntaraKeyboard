package com.example.antarakeyboard.prefs


import android.content.Context


object KeyboardPrefs {
    private const val PREF = "antara_keyboard_prefs"


    private const val KEY_SCALE = "keyboard_scale"
    private const val KEY_SHAPE = "key_shape"


    fun setScale(ctx: Context, scale: Float) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SCALE, scale).apply()
    }


    fun getScale(ctx: Context): Float =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getFloat(KEY_SCALE, 1.0f)


    fun setShape(ctx: Context, shape: KeyShape) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_SHAPE, shape.name).apply()
    }


    fun getShape(ctx: Context): KeyShape {
        val name = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_SHAPE, KeyShape.HEX.name)!!
        return KeyShape.valueOf(name)
    }
}


enum class KeyShape {
    HEX, TRIANGLE
}