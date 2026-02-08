package com.example.antarakeyboard

import android.content.Context

object KeyBindingStore {

    private const val PREFS = "key_bindings"

    fun getBindings(context: Context, key: String): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(key, emptySet())?.toList() ?: emptyList()
    }

    fun addBinding(context: Context, key: String, char: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(char)
        prefs.edit().putStringSet(key, set).apply()
    }

    fun removeBinding(context: Context, key: String, char: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.remove(char)
        prefs.edit().putStringSet(key, set).apply()
    }
}
