package com.litecharts.app.drawing

import android.content.Context
import org.json.JSONArray

/**
 * Persist drawings per (instrument, granularity) in SharedPreferences as JSON.
 */
object DrawingPersistence {

    private const val PREFS = "litecharts_drawings"

    private fun key(instrument: String, granularity: String) = "${instrument}_$granularity"

    fun save(ctx: Context, instrument: String, granularity: String, drawings: List<Drawing>) {
        val arr = JSONArray()
        drawings.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(instrument, granularity), arr.toString()).apply()
    }

    fun load(ctx: Context, instrument: String, granularity: String): List<Drawing> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(instrument, granularity), null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { Drawing.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    fun clear(ctx: Context, instrument: String, granularity: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(key(instrument, granularity)).apply()
    }
}
