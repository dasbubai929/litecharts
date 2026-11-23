package com.litecharts.app.data

/** Parsed OHLCV candle from OANDA's /v3/instruments/{instrument}/candles response. */
data class OandaCandle(
    val time: Long,       // Unix timestamp in seconds
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Int
)
