package com.example.antarakeyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import com.google.gson.Gson

object KeyboardPrefs {

    private const val PREFS_NAME = "keyboard_prefs"
    private const val KEY_SCALE = "key_scale"
    private const val KEY_SHAPE = "key_shape"
    private const val KEY_LAYOUT_JSON = "layout_json"
    private const val KEY_HEIGHT_PX = "key_height_px"

    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /* ───────── KEY HEIGHT (px) ───────── */

    fun getKeyHeightPx(context: Context): Int =
        prefs(context).getInt(KEY_HEIGHT_PX, 0) // 0 = auto

    fun setKeyHeightPx(context: Context, px: Int) {
        prefs(context).edit().putInt(KEY_HEIGHT_PX, px).apply()
    }

    fun clearKeyHeightPx(context: Context) {
        prefs(context).edit().remove(KEY_HEIGHT_PX).apply()
    }

    /* ───────── SCALE ───────── */

    fun getScale(context: Context): Float =
        prefs(context).getFloat(KEY_SCALE, 1.0f)

    fun setScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_SCALE, scale).apply()
    }

    /* ───────── SHAPE ───────── */

    fun getShape(context: Context): KeyShape {
        val name = prefs(context).getString(KEY_SHAPE, KeyShape.HEX.name) ?: KeyShape.HEX.name
        return KeyShape.valueOf(name)
    }

    fun setShape(context: Context, shape: KeyShape) {
        prefs(context).edit().putString(KEY_SHAPE, shape.name).apply()
    }

    /* ───────── LAYOUT ───────── */

    fun saveLayout(context: Context, layout: KeyboardConfig) {
        val json = gson.toJson(layout)
        prefs(context).edit().putString(KEY_LAYOUT_JSON, json).apply()
    }

    fun loadLayout(context: Context): KeyboardConfig {
        val json = prefs(context).getString(KEY_LAYOUT_JSON, null)
        return if (!json.isNullOrBlank()) {
            gson.fromJson(json, KeyboardConfig::class.java)
        } else {
            defaultKeyboardLayout
        }
    }

    fun clearLayout(context: Context) {
        prefs(context).edit().remove(KEY_LAYOUT_JSON).apply()
    }
}
