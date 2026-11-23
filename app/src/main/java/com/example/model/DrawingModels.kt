package com.example.model

import com.squareup.moshi.JsonClass

enum class DrawingToolCategory(val title: String) {
    NAVIGATION("Cursor"),
    TREND("Trend Tools"),
    GANN_FIB("Gann & Fibonacci"),
    FORECAST_MEASURE("Forecasting & Measurement"),
    GEOMETRIC("Geometric Shapes")
}

enum class DrawingTool(
    val label: String,
    val shortLabel: String,
    val category: DrawingToolCategory,
    val requiredTaps: Int,
    val firstTapHint: String,
    val secondTapHint: String = ""
) {
    CURSOR(
        label = "Crosshair & Pan",
        shortLabel = "Pan",
        category = DrawingToolCategory.NAVIGATION,
        requiredTaps = 0,
        firstTapHint = ""
    ),
    HORIZONTAL_LINE(
        label = "Horizontal Line",
        shortLabel = "H-Line",
        category = DrawingToolCategory.TREND,
        requiredTaps = 1,
        firstTapHint = "Tap price level to place Horizontal Line"
    ),
    TRENDLINE(
        label = "Trendline",
        shortLabel = "Trend",
        category = DrawingToolCategory.TREND,
        requiredTaps = 2,
        firstTapHint = "Tap start point (1/2) for Trendline",
        secondTapHint = "Tap end point (2/2) for Trendline"
    ),
    FIB_RETRACEMENT(
        label = "Fib Retracement",
        shortLabel = "Fib",
        category = DrawingToolCategory.GANN_FIB,
        requiredTaps = 2,
        firstTapHint = "Tap swing anchor (1/2) for Fib Retracement",
        secondTapHint = "Tap second swing anchor (2/2) for Fib levels"
    ),
    LONG_POSITION(
        label = "Long Position",
        shortLabel = "Long",
        category = DrawingToolCategory.FORECAST_MEASURE,
        requiredTaps = 2,
        firstTapHint = "Tap entry point (1/2) for Long Position",
        secondTapHint = "Tap target / width (2/2) for Long Position"
    ),
    SHORT_POSITION(
        label = "Short Position",
        shortLabel = "Short",
        category = DrawingToolCategory.FORECAST_MEASURE,
        requiredTaps = 2,
        firstTapHint = "Tap entry point (1/2) for Short Position",
        secondTapHint = "Tap target / width (2/2) for Short Position"
    ),
    ANCHORED_VOLUME_PROFILE(
        label = "Anchored Volume Profile",
        shortLabel = "AVP",
        category = DrawingToolCategory.FORECAST_MEASURE,
        requiredTaps = 1,
        firstTapHint = "Tap starting bar for Anchored Volume Profile"
    ),
    FIXED_RANGE_VOLUME_PROFILE(
        label = "Fixed Range Volume Profile",
        shortLabel = "FRVP",
        category = DrawingToolCategory.FORECAST_MEASURE,
        requiredTaps = 2,
        firstTapHint = "Tap range start bar (1/2) for Volume Profile",
        secondTapHint = "Tap range end bar (2/2) for Volume Profile"
    ),
    PRICE_RANGE(
        label = "Price Range",
        shortLabel = "Range",
        category = DrawingToolCategory.FORECAST_MEASURE,
        requiredTaps = 2,
        firstTapHint = "Tap start anchor (1/2) to measure Price Range",
        secondTapHint = "Tap end anchor (2/2) to measure delta & bars"
    ),
    RECTANGLE(
        label = "Rectangle",
        shortLabel = "Rect",
        category = DrawingToolCategory.GEOMETRIC,
        requiredTaps = 2,
        firstTapHint = "Tap first corner (1/2) for Rectangle",
        secondTapHint = "Tap opposite corner (2/2) for Rectangle"
    );

    companion object {
        fun fromNameOrNull(name: String): DrawingTool? =
            entries.firstOrNull { it.name == name }
    }
}

data class ChartAnchor(
    val timeEpochSec: Long,
    val price: Double
)

@JsonClass(generateAdapter = true)
data class ChartDrawing(
    val id: String,
    val type: String,
    val startTime: Long,
    val startPrice: Double,
    val endTime: Long = 0L,
    val endPrice: Double = 0.0,
    val stopPrice: Double = 0.0,
    val targetPrice: Double = 0.0,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    val tool: DrawingTool?
        get() = DrawingTool.fromNameOrNull(type)
}

@JsonClass(generateAdapter = true)
data class StoredDrawingsMap(
    val byChartKey: Map<String, List<ChartDrawing>> = emptyMap()
)

val FibLevels: List<Double> = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0)
