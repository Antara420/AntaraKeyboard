package com.example.antarakeyboard.prefs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object KeyBindingStore {

    private const val PREFS = "key_bindings"
    private const val KEY_JSON = "bindings_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /* ───────── LOAD ───────── */

    fun loadAll(context: Context): MutableMap<String, MutableList<String>> {
        val jsonString = prefs(context).getString(KEY_JSON, null)
            ?: return mutableMapOf()

        val result = mutableMapOf<String, MutableList<String>>()
        val root = JSONObject(jsonString)

        root.keys().forEach { key ->
            val arr = root.optJSONArray(key) ?: JSONArray()
            val list = mutableListOf<String>()

            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            result[key] = list
        }

        return result
    }

    /* ───────── SAVE ───────── */

    private fun saveAll(context: Context, data: Map<String, List<String>>) {
        val root = JSONObject()
        data.forEach { (key, list) ->
            root.put(key, JSONArray(list))
        }

        prefs(context)
            .edit()
            .putString(KEY_JSON, root.toString())
            .apply()
    }

    /* ───────── PUBLIC API ───────── */

    /** Bindovi za JEDAN key */
    fun getBindings(context: Context, key: String): List<String> =
        loadAll(context)[key] ?: emptyList()

    /** 🔥 SVI user bindovi */
    fun getAllBindings(context: Context): Map<String, List<String>> =
        loadAll(context)

    /** Dodaj bind */
    fun addBinding(context: Context, key: String, char: String) {
        val data = loadAll(context)
        val list = data.getOrPut(key) { mutableListOf() }

        if (!list.contains(char)) {
            list.add(char)
            saveAll(context, data)
        }
    }

    /** Ukloni jedan bind */
    fun removeBinding(context: Context, key: String, char: String) {
        val data = loadAll(context)
        val list = data[key] ?: return

        if (list.remove(char)) {
            if (list.isEmpty()) {
                data.remove(key)
            }
            saveAll(context, data)
        }
    }

    /** Obriši sve bindove za key */
    fun clearKey(context: Context, key: String) {
        val data = loadAll(context)
        if (data.remove(key) != null) {
            saveAll(context, data)
        }
    }

    /** Totalni reset */
    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_JSON).apply()
    }
}
