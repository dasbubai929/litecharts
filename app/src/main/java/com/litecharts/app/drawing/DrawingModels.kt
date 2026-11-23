package com.litecharts.app.drawing

import org.json.JSONObject

/**
 * An anchor point in chart-space (price × time).
 * Drawings are stored in this coordinate system and converted to screen
 * pixels using ChartManager's coordinate mapping.
 */
data class AnchorPoint(val time: Long, val price: Double)

/** Which drawing tool is currently selected. */
enum class DrawingToolType {
    NONE,                       // Pan mode — touches pass to chart
    HORIZONTAL_LINE,            // 1 tap
    TRENDLINE,                  // 2 taps
    FIB_RETRACEMENT,            // 2 taps (high ↔ low)
    SHORT_POSITION,             // 3 taps: entry, stop, target
    LONG_POSITION,              // 3 taps: entry, stop, target
    ANCHORED_VOLUME_PROFILE,    // 1 tap (anchor bar)
    FIXED_RANGE_VOLUME,         // 2 taps (start, end)
    PRICE_RANGE,                // 2 taps
    RECTANGLE                   // 2 taps (corners)
}

// ── Drawing sealed hierarchy ──────────────────────────────────

sealed class Drawing(val type: DrawingToolType, val id: String = System.currentTimeMillis().toString()) {
    abstract fun toJson(): JSONObject

    class HorizontalLine(val price: Double, val time: Long, id: String = ts()) : Drawing(DrawingToolType.HORIZONTAL_LINE, id) {
        override fun toJson() = JSONObject().apply {
            put("t", "HL"); put("id", id); put("p", price); put("tm", time)
        }
    }

    class Trendline(val start: AnchorPoint, val end: AnchorPoint, id: String = ts()) : Drawing(DrawingToolType.TRENDLINE, id) {
        override fun toJson() = JSONObject().apply {
            put("t", "TL"); put("id", id)
            put("st", start.time); put("sp", start.price)
            put("et", end.time);   put("ep", end.price)
        }
    }

    class FibRetracement(val start: AnchorPoint, val end: AnchorPoint, id: String = ts()) : Drawing(DrawingToolType.FIB_RETRACEMENT, id) {
        val levels = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0)
        override fun toJson() = JSONObject().apply {
            put("t", "FIB"); put("id", id)
            put("st", start.time); put("sp", start.price)
            put("et", end.time);   put("ep", end.price)
        }
    }

    class PositionBox(
        val isLong: Boolean,
        val entry: Double, val stop: Double, val target: Double,
        val startTime: Long, val endTime: Long,
        id: String = ts()
    ) : Drawing(if (isLong) DrawingToolType.LONG_POSITION else DrawingToolType.SHORT_POSITION, id) {
        override fun toJson() = JSONObject().apply {
            put("t", if (isLong) "LONG" else "SHORT"); put("id", id)
            put("e", entry); put("s", stop); put("tg", target)
            put("st", startTime); put("et", endTime)
        }
    }

    class AnchoredVolumeProfile(val anchorTime: Long, val anchorPrice: Double, id: String = ts()) :
        Drawing(DrawingToolType.ANCHORED_VOLUME_PROFILE, id) {
        override fun toJson() = JSONObject().apply {
            put("t", "AVP"); put("id", id); put("at", anchorTime); put("ap", anchorPrice)
        }
    }

    class FixedRangeVolume(val start: AnchorPoint, val end: AnchorPoint, id: String = ts()) :
        Drawing(DrawingToolType.FIXED_RANGE_VOLUME, id) {
        override fun toJson() = JSONObject().apply {
            put("t", "FRV"); put("id", id)
            put("st", start.time); put("sp", start.price)
            put("et", end.time);   put("ep", end.price)
        }
    }

    class PriceRange(val start: AnchorPoint, val end: AnchorPoint, id: String = ts()) :
        Drawing(DrawingToolType.PRICE_RANGE, id) {
        override fun toJson() = JSONObject().apply {
            put("t", "PR"); put("id", id)
            put("st", start.time); put("sp", start.price)
            put("et", end.time);   put("ep", end.price)
        }
    }

    class Rectangle(val corner1: AnchorPoint, val corner2: AnchorPoint, id: String = ts()) :
        Drawing(DrawingToolType.RECTANGLE, id) {
        override fun toJson() = JSONObject().apply {
            put("t", "RECT"); put("id", id)
            put("st", corner1.time); put("sp", corner1.price)
            put("et", corner2.time); put("ep", corner2.price)
        }
    }

    companion object {
        private fun ts() = System.currentTimeMillis().toString()

        fun fromJson(j: JSONObject): Drawing? = try {
            val id = j.optString("id", ts())
            when (j.getString("t")) {
                "HL"    -> HorizontalLine(j.getDouble("p"), j.getLong("tm"), id)
                "TL"    -> Trendline(ap(j, "st", "sp"), ap(j, "et", "ep"), id)
                "FIB"   -> FibRetracement(ap(j, "st", "sp"), ap(j, "et", "ep"), id)
                "LONG"  -> PositionBox(true,  j.getDouble("e"), j.getDouble("s"), j.getDouble("tg"), j.getLong("st"), j.getLong("et"), id)
                "SHORT" -> PositionBox(false, j.getDouble("e"), j.getDouble("s"), j.getDouble("tg"), j.getLong("st"), j.getLong("et"), id)
                "AVP"   -> AnchoredVolumeProfile(j.getLong("at"), j.getDouble("ap"), id)
                "FRV"   -> FixedRangeVolume(ap(j, "st", "sp"), ap(j, "et", "ep"), id)
                "PR"    -> PriceRange(ap(j, "st", "sp"), ap(j, "et", "ep"), id)
                "RECT"  -> Rectangle(ap(j, "st", "sp"), ap(j, "et", "ep"), id)
                else    -> null
            }
        } catch (_: Exception) { null }

        private fun ap(j: JSONObject, tk: String, pk: String) = AnchorPoint(j.getLong(tk), j.getDouble(pk))
    }
}
