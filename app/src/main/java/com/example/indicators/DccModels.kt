package com.example.indicators

import com.squareup.moshi.JsonClass

/**
 * Exact parameters matching DCC+TSM+VPSR+SQZ (v2.5.5) Pine Script inputs (Groups 01 - 16).
 */
@JsonClass(generateAdapter = true)
data class DccSettings(
    // Master toggle for the entire DCC indicator
    val masterEnabled: Boolean = true,

    // 01 • EMA / TREND
    val enableEMA: Boolean = true,
    val showFastEma: Boolean = true,
    val fastLen: Int = 9,
    val fastSource: String = "close",
    val showSlowEma: Boolean = true,
    val slowLen: Int = 21,
    val slowSource: String = "close",

    // 02 • VWAP
    val enableVwap: Boolean = true,
    val vwapMode: String = "Daily", // "Daily" or "Sessions"
    val vwapSource: String = "hlc3",

    // 03 • HTF EMA LEVEL & BIAS
    val enableHTFEMA: Boolean = true,
    val htfEmaTF: String = "60", // "15", "30", "60", "240", "1D"
    val htfEmaLen1: Int = 21,
    val htfEmaDisplayMode: String = "Smoothed Approximation", // "Actual HTF EMA" or "Smoothed Approximation"

    // 04 • ADX
    val enableADX: Boolean = true,
    val adxTF: String = "60",
    val adxLen: Int = 14,
    val adxSmooth: Int = 14,

    // 05 • ATR / RISK (STOP LOSS)
    val enableATR: Boolean = true,
    val atrTF: String = "60",
    val atrLen: Int = 14,
    val atrMult: Double = 0.9,
    val showLiveSL: Boolean = true,

    // 06 • DISPLAY & TABLE
    val showTable: Boolean = true,
    val tablePosInput: String = "Bottom Center",
    val tblTextInput: String = "Tiny", // "Tiny", "Small", "Normal"

    // 07 • TRADING SESSIONS
    val enableSessions: Boolean = true,
    val enableTokyo: Boolean = true,
    val tokyoName: String = "Tokyo",
    val enableLondon: Boolean = true,
    val londonName: String = "London",
    val londonAutoDST: Boolean = true,
    val enableNY: Boolean = true,
    val nyName: String = "New York",
    val nyAutoDST: Boolean = true,
    val enableSydney: Boolean = true,
    val sydneyName: String = "Sydney",
    val sydneyAutoDST: Boolean = true,

    // 08 • SESSION RANGE BOXES
    val enableRangeBoxes: Boolean = true,
    val rangeTransparency: Int = 95,
    val showRangeLabel: Boolean = true,
    val showRangeOutline: Boolean = true,
    val outlineTransp: Int = 75,
    val sessHistLimit: Int = 15,

    // 09 • KEY LEVELS — PDH / PDL / DAILY OPEN
    val enableLevels: Boolean = true,
    val showDailyOpen: Boolean = true,
    val styDO: String = "Dotted", // "Solid", "Dashed", "Dotted"
    val widDO: Int = 1,
    val showPDLvl: Boolean = true,
    val styPDLvl: String = "Dotted",
    val widPDLvl: Int = 1,

    // 10 • TREND STRENGTH MATRIX (TSM)
    val enableTSM: Boolean = true,
    val tsmDisplayMode: String = "Compact DCC Table Row", // "Compact DCC Table Row", "Full Mobile TSM Table"
    val tsmDashPos: String = "Top Right",
    val tsmConfirmMode: String = "Live HTF", // "Live HTF", "Last Closed HTF"
    val tsmTf1: String = "60",
    val tsmTf2: String = "240",
    val tsmTf3: String = "1D",
    val tsmMinStrength: Double = 50.0,
    val tsmRsiLength: Int = 14,
    val tsmMacdFast: Int = 12,
    val tsmMacdSlow: Int = 26,
    val tsmMacdSignal: Int = 9,
    val tsmAdxLength: Int = 14,
    val tsmMaLength: Int = 50,

    // 11 • EMA CROSSOVER MARKERS (❌) & CONFLUENCE FILTERS
    val showCrossMarkers: Boolean = true,
    val crossUseVwap: Boolean = false,
    val crossUseHtfEma: Boolean = false,
    val crossUseAdx: Boolean = false,
    val crossUseTsm: Boolean = false,
    val crossUseTsmAllAlign: Boolean = false,

    // 12 • PULLBACK & P3 RE-ENTRY SIGNALS
    val enablePullbackEntry: Boolean = true,
    val tiePbToValidCross: Boolean = true,
    val pbTargetEma: String = "Fast EMA", // "Fast EMA", "Slow EMA"
    val pbTouchMode: String = "Wick or Close Touch", // "Wick or Close Touch", "Close Touch / Cross"
    val pbBreakTrigger: String = "Candle Close", // "Candle Close", "Intrabar Wick/Price"
    val showPbLines: Boolean = true,
    val showHistPbLines: Boolean = false,

    val enableP3ReEntry: Boolean = false,
    val tieP3ToValidCross: Boolean = true,
    val p3BreakoutBars: Int = 3,
    val p3MinDistBars: Int = 10,

    val sigUseVwap: Boolean = true,
    val sigUseHtfEma: Boolean = false,
    val sigUseAdx: Boolean = false,
    val sigMinAdx: Double = 20.0,
    val sigUseTsm: Boolean = false,
    val sigMinTsmScore: Double = 20.0,
    val sigTsmAllAligned: Boolean = false,

    // 13 • LARGE ROLLING VOLUME PROFILE
    val enableVP: Boolean = true,
    val profLb: Int = 360,
    val profRows: Int = 72,
    val profW: Int = 45,
    val profOff: Int = 8,
    val showPoc: Boolean = true,
    val showLvlRows: Boolean = true,

    // 14 • PROFILE-GUIDED S/R
    val enableSR: Boolean = true,
    val swingLen: Int = 4,
    val srchAtr: Double = 0.95,
    val nodeMin: Double = 0.12,
    val evidMin: Double = 0.30,
    val pullMax: Double = 0.70,
    val scoreMin: Double = 40.0,
    val maxLvl: Int = 10,
    val mergeAtr: Double = 0.25,
    val breakAtr: Double = 0.12,
    val srLvlStyle: String = "Dashed", // "Solid", "Dotted", "Dashed"
    val widSR: Int = 1,
    val showRaw: Boolean = false,
    val showEdge: Boolean = true,

    // 15 • SQUEEZE INDEX (TABLE ONLY)
    val enableSqz: Boolean = true,
    val sqzConv: Int = 40,
    val sqzLen: Int = 20,
    val sqzThreshold: Double = 80.0,

    // 16 • COLORS (MASTER THEME — Exact DCC Pine Defaults)
    val cuFast: Long = 0xFFB37D13L,
    val cuSlow: Long = 0xFF1E4D6BL,
    val cuVwap: Long = 0xFF6D4C7DL,
    val cuLevel1: Long = 0xFFB37D13L,
    val cuDaily: Long = 0xFF707A7BL,
    val cuPD: Long = 0xFF802C44L,
    val cuTokyo: Long = 0xFFC15912L,
    val cuLondon: Long = 0xFF1E4D6BL,
    val cuNY: Long = 0xFFB37D13L,
    val cuSydney: Long = 0xFF4B524AL,
    val cuTblLabel: Long = 0xFFE0D2B8L,
    val cuTblValue: Long = 0xFFE0D2B8L,
    val cuTblText: Long = 0xFF2F3D46L,
    val cuTblVal: Long = 0xFF6D7A80L,
    val cuBull: Long = 0xFF4F7965L,
    val cuBear: Long = 0xFFA35C62L,
    val cuPbBull: Long = 0xFF00B0FFL,
    val cuPbBear: Long = 0xFFFF6D00L,
    val cuSqzComp: Long = 0xFFC15912L,
    val cuSqzRel: Long = 0xFF4F7965L
)

enum class DccMarkerKind {
    CROSS_BULL,
    CROSS_BEAR,
    PULLBACK_BUY,
    PULLBACK_SELL,
    P3_BUY,
    P3_SELL
}

data class DccMarker(
    val barIndex: Int,
    val timeEpochSec: Long,
    val price: Double,
    val kind: DccMarkerKind
)

data class DccPullbackLine(
    val startBarIndex: Int,
    val endBarIndex: Int,
    val price: Double,
    val isBullish: Boolean,
    val isActive: Boolean
)

data class DccSessionBox(
    val name: String,
    val startBarIndex: Int,
    val endBarIndex: Int,
    val high: Double,
    val low: Double,
    val startHigh: Double,
    val startLow: Double,
    val endHigh: Double,
    val endLow: Double,
    val colorArgb: Long
)

data class DccKeyLevel(
    val name: String, // "DO", "PDH", "PDL"
    val price: Double,
    val startTimeEpochSec: Long,
    val colorArgb: Long,
    val style: String,
    val width: Int
)

data class DccSrLevel(
    val price: Double,
    val rawPrice: Double,
    val score: Double,
    val isSupport: Boolean,
    val bornTimeEpochSec: Long
)

data class DccVpRow(
    val priceLow: Double,
    val priceHigh: Double,
    val strength: Double,
    val buyShare: Double,
    val isPoc: Boolean,
    val srSide: Int // +1 support, -1 resistance, 0 none
)

data class DccVolumeProfileData(
    val rows: List<DccVpRow>,
    val pocPrice: Double?,
    val widthBars: Int,
    val offsetBars: Int
)

data class TsmRowData(
    val tfLabel: String,
    val rsi: Double,
    val rsiScore: Double,
    val macdScore: Double,
    val adx: Double,
    val adxScore: Double,
    val maScore: Double,
    val composite: Double
)

data class DccTableData(
    val biasText: String = "FLAT",
    val biasLong: Boolean = false,
    val biasShort: Boolean = false,
    val adxValue: Double = Double.NaN,
    val liveSL: Double? = null,
    val slDistStr: String = "",
    val sqzPsi: Double = Double.NaN,
    val sqzState: String = "IDLE", // "COMP", "REL", "IDLE"
    val tsmStripText: String = "",
    val tsmMtfScore: Double = 0.0,
    val tsmScorePct: Double = 0.0,
    val tsmRows: List<TsmRowData> = emptyList()
)

data class DccMasterResult(
    val emaFast: List<Double?> = emptyList(),
    val emaSlow: List<Double?> = emptyList(),
    val vwap: List<Double?> = emptyList(),
    val vwapBreaks: List<Boolean> = emptyList(),
    val htfEma1: List<Double?> = emptyList(),
    val markers: List<DccMarker> = emptyList(),
    val pullbackLines: List<DccPullbackLine> = emptyList(),
    val sessionBoxes: List<DccSessionBox> = emptyList(),
    val keyLevels: List<DccKeyLevel> = emptyList(),
    val srLevels: List<DccSrLevel> = emptyList(),
    val volumeProfile: DccVolumeProfileData? = null,
    val tableData: DccTableData = DccTableData()
)
