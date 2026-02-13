package com.example.antarakeyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.myDefaultKeyboardConfig
import com.example.antarakeyboard.model.KeyShape
import com.google.gson.Gson

object KeyboardPrefs {

    private const val PREFS_NAME = "keyboard_prefs"
    private const val KEY_SCALE = "key_scale"
    private const val KEY_SHAPE = "key_shape"
    private const val KEY_LAYOUT_JSON = "layout_json"

    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /* ───────── SCALE ───────── */

    fun getScale(context: Context): Float =
        prefs(context).getFloat(KEY_SCALE, 1.0f)

    fun setScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_SCALE, scale).apply()
    }

    /* ───────── SHAPE ───────── */

    fun getShape(context: Context): KeyShape {
        val name = prefs(context).getString(KEY_SHAPE, KeyShape.HEX.name)
        return KeyShape.valueOf(name!!)
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
        return if (json != null) {
            gson.fromJson(json, KeyboardConfig::class.java)
        } else {
            myDefaultKeyboardConfig
        }
    }

    fun clearLayout(context: Context) {
        prefs(context).edit().remove(KEY_LAYOUT_JSON).apply()
    }
}
