package com.example.antarakeyboard.prefs

import android.content.Context
import android.content.SharedPreferences

object KeyboardPrefs {

    private const val PREFS_NAME = "keyboard_prefs"
    private const val KEY_SCALE = "key_scale"
    private const val KEY_SHAPE = "key_shape"
    private const val KEY_CUSTOM_LAYOUT = "custom_layout"  // <-- dodaj ovu konstantu

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getScale(context: Context): Float =
        prefs(context).getFloat(KEY_SCALE, 1.0f)

    fun setScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_SCALE, scale).apply()
    }

    fun getShape(context: Context): KeyShape {
        val name = prefs(context).getString(KEY_SHAPE, KeyShape.HEX.name)
        return KeyShape.valueOf(name!!)
    }

    fun setShape(context: Context, shape: KeyShape) {
        prefs(context).edit().putString(KEY_SHAPE, shape.name).apply()
    }

    // ───── OVDE DODAJ ─────

    fun hasCustomLayout(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CUSTOM_LAYOUT, false)

    fun setCustomLayout(context: Context, value: Boolean = true) {
        prefs(context).edit().putBoolean(KEY_CUSTOM_LAYOUT, value).apply()
    }
}

