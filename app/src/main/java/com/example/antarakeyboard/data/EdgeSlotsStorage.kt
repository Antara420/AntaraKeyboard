package com.example.antarakeyboard.data

import android.content.Context
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import org.json.JSONArray
import org.json.JSONObject

object EdgeSlotsStorage {
    private const val SP_NAME = "edge_slots"
    private const val KEY_JSON = "edge_slots_config"

    fun defaultSlots(): List<EdgeSlot> = listOf(
        EdgeSlot(0, EdgePos.Side.LEFT,  EdgeActionType.SHIFT),
        EdgeSlot(1, EdgePos.Side.RIGHT, EdgeActionType.BACKSPACE),
        EdgeSlot(2, EdgePos.Side.LEFT,  EdgeActionType.NONE),
        EdgeSlot(3, EdgePos.Side.RIGHT, EdgeActionType.NONE),
        EdgeSlot(4, EdgePos.Side.LEFT,  EdgeActionType.NONE),
        EdgeSlot(5, EdgePos.Side.RIGHT, EdgeActionType.NONE),
    )

    fun load(ctx: Context): List<EdgeSlot> {
        val sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_JSON, null) ?: return defaultSlots()

        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<EdgeSlot>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val index = o.optInt("index", -1)
                val side = EdgePos.Side.valueOf(o.getString("side"))
                val type = EdgeActionType.valueOf(o.getString("type"))
                val value = if (o.has("value") && !o.isNull("value")) o.getString("value") else null
                if (index !in 0..5) continue
                out.add(EdgeSlot(index, side, type, value))
            }
            if (out.size != 6) defaultSlots() else out.sortedBy { it.index }
        } catch (_: Throwable) {
            defaultSlots()
        }
    }

    fun save(ctx: Context, slots: List<EdgeSlot>) {
        val fixed = (if (slots.size == 6) slots else defaultSlots()).sortedBy { it.index }

        val arr = JSONArray()
        fixed.forEach { s ->
            val o = JSONObject()
            o.put("index", s.index)
            o.put("side", s.side.name)
            o.put("type", s.type.name)
            if (s.value != null) o.put("value", s.value) else o.put("value", JSONObject.NULL)
            arr.put(o)
        }

        ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, arr.toString())
            .apply()
    }

    fun reset(ctx: Context) = save(ctx, defaultSlots())
}