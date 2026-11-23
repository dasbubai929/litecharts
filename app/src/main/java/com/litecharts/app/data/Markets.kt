package com.litecharts.app.data

/**
 * Single, top-level list of the 13 OANDA instruments.
 * Edit this one list to change which markets appear everywhere in the app.
 */
data class Market(
    val instrument: String,   // OANDA instrument ID, e.g. "EUR_USD"
    val displayName: String   // Human-readable label
)

val MARKETS: List<Market> = listOf(
    Market("EUR_USD",    "EUR/USD"),
    Market("GBP_USD",    "GBP/USD"),
    Market("USD_JPY",    "USD/JPY"),
    Market("AUD_USD",    "AUD/USD"),
    Market("USD_CAD",    "USD/CAD"),
    Market("USD_CHF",    "USD/CHF"),
    Market("AUD_JPY",    "AUD/JPY"),
    Market("XAU_USD",    "XAU/USD"),
    Market("WTICO_USD",  "WTI Oil"),
    Market("SPX500_USD", "SPX 500"),
    Market("NAS100_USD", "NAS 100"),
    Market("US30_USD",   "US 30"),
    Market("BTC_USD",    "BTC/USD"),
)
