package com.example.antarakeyboard

import android.content.Context

object KeyBindingStore {

    private const val PREFS = "key_bindings"

    fun bind(context: Context, key: String, char: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(key, mutableSetOf())!!.toMutableSet()
        set.add(char)
        prefs.edit().putStringSet(key, set).apply()
    }

    fun getBindings(context: Context, key: String): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(key, emptySet())!!.toList()
    }

    fun clear(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(key).apply()
    }
}
