package com.example.model

enum class AssetCategory(val label: String) {
    FOREX("FX"),
    METALS("METAL"),
    ENERGY("OIL"),
    INDICES("INDEX"),
    CRYPTO("CRYPTO")
}

data class MarketInstrument(
    val id: String,
    val displaySymbol: String,
    val name: String,
    val category: AssetCategory,
    val decimals: Int
)

/**
 * Single top-level watchlist configuration array.
 * Edit this list to add, remove, or reorder default OANDA markets.
 */
val DefaultWatchlist: List<MarketInstrument> = listOf(
    MarketInstrument("EUR_USD", "EUR/USD", "Euro / US Dollar", AssetCategory.FOREX, 5),
    MarketInstrument("GBP_USD", "GBP/USD", "British Pound / USD", AssetCategory.FOREX, 5),
    MarketInstrument("USD_JPY", "USD/JPY", "US Dollar / Yen", AssetCategory.FOREX, 3),
    MarketInstrument("AUD_USD", "AUD/USD", "Australian Dollar / USD", AssetCategory.FOREX, 5),
    MarketInstrument("USD_CAD", "USD/CAD", "US Dollar / CAD", AssetCategory.FOREX, 5),
    MarketInstrument("USD_CHF", "USD/CHF", "US Dollar / Swiss Franc", AssetCategory.FOREX, 5),
    MarketInstrument("AUD_JPY", "AUD/JPY", "Australian Dollar / Yen", AssetCategory.FOREX, 3),
    MarketInstrument("XAU_USD", "XAU/USD", "Gold Spot / USD", AssetCategory.METALS, 2),
    MarketInstrument("WTICO_USD", "WTI/USD", "West Texas Oil", AssetCategory.ENERGY, 3),
    MarketInstrument("SPX500_USD", "SPX500", "S&P 500 Index", AssetCategory.INDICES, 1),
    MarketInstrument("NAS100_USD", "NAS100", "Nasdaq 100 Index", AssetCategory.INDICES, 1),
    MarketInstrument("US30_USD", "US30", "Dow Jones 30", AssetCategory.INDICES, 1),
    MarketInstrument("BTC_USD", "BTC/USD", "Bitcoin / US Dollar", AssetCategory.CRYPTO, 1)
)

enum class Timeframe(
    val uiLabel: String,
    val oandaGranularity: String,
    val durationSec: Long
) {
    M1("M1", "M1", 60L),
    M3("M3", "M3", 180L),
    M5("M5", "M5", 300L),
    M15("M15", "M15", 900L),
    M30("M30", "M30", 1_800L),
    H1("H1", "H1", 3_600L),
    H4("H4", "H4", 14_400L),
    D1("1D", "D", 86_400L),
    W1("1W", "W", 604_800L);

    companion object {
        fun fromNameOrDefault(name: String?): Timeframe =
            entries.firstOrNull { it.name == name } ?: M15
    }
}

data class Candle(
    val timeEpochSec: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val isComplete: Boolean = true
)

data class LiveQuote(
    val instrument: String,
    val bid: Double,
    val ask: Double,
    val mid: Double,
    val spread: Double,
    val timeEpochSec: Long,
    val isUpTick: Boolean? = null
)

enum class StreamStatus {
    LIVE,
    CONNECTING,
    RECONNECTING,
    OFFLINE
}
