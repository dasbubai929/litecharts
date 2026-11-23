package com.litecharts.app.data

/**
 * Timeframe definitions.
 * [label] is what the button shows; [granularity] is the OANDA API value.
 */
data class Timeframe(
    val label: String,
    val granularity: String,
    val candleCount: Int = 300
)

val TIMEFRAMES: List<Timeframe> = listOf(
    Timeframe("M1",  "M1",  500),
    Timeframe("M3",  "M3",  500),
    Timeframe("M5",  "M5",  500),
    Timeframe("M15", "M15", 300),
    Timeframe("M30", "M30", 300),
    Timeframe("H1",  "H1",  300),
    Timeframe("H4",  "H4",  200),
    Timeframe("1D",  "D",   200),
    Timeframe("1W",  "W",   100),
)
