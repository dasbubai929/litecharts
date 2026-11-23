package com.litecharts.app.indicator

import com.litecharts.app.data.OandaCandle
import kotlin.math.*

/**
 * ════════════════════════════════════════════════════════════════
 *  DCC Indicator Engine — Translated from Pine Script v2.5.5
 *  DCC+TSM+VPSR+SQZ Strategy
 * ════════════════════════════════════════════════════════════════
 *
 * Implements:
 *  01 • Fast EMA (9) & Slow EMA (21)
 *  02 • Daily Anchored VWAP
 *  03 • HTF EMA 1 (H1 smoothed approximation) & Bias (LONG/SHORT/FLAT)
 *  04 • DMI / ADX (14)
 *  05 • ATR (14) & Stop Loss Calculator (0.9 × ATR)
 *  09 • Key Levels (Daily Open, PDH, PDL)
 *  10 • Trend Strength Matrix (TSM Composite Score)
 *  11 • EMA Crossover Markers (❌)
 *  12 • Pullback Entry & P3 Re-Entry Signals
 *  15 • Squeeze Index (SQZ) with COMP / REL / IDLE states
 *  16 • DCC Table Data / HUD Metrics
 */
object DccIndicatorEngine {

    // ── Output Types ───────────────────────────────────────────

    data class LinePoint(val time: Long, val value: Double)

    data class LineSeries(
        val name: String,
        val color: String,
        val lineWidth: Int = 1,
        val points: List<LinePoint>
    )

    enum class MarkerPosition { ABOVE, BELOW }
    enum class MarkerShape   { CIRCLE, ARROW_UP, ARROW_DOWN, SQUARE }

    data class SignalMarker(
        val time: Long,
        val position: MarkerPosition,
        val color: String,
        val shape: MarkerShape,
        val text: String
    )

    /** Live status metrics matching Pine Script Group 06 / Table display */
    data class DccTableData(
        val bias: String,          // "LONG", "SHORT", or "FLAT"
        val biasColor: String,     // Hex color
        val adx: Double,           // Live ADX value
        val tsmScorePct: Double,   // -100% to +100%
        val tsmStripText: String,  // e.g. "TSM +65% ★"
        val liveSL: Double?,       // Stop loss price
        val slText: String,        // Formatted SL text
        val sqzState: String,      // "COMP", "REL", or "IDLE"
        val sqzValue: Double,      // PSI value (0..100)
        val sqzColor: String       // Hex color for SQZ state
    )

    data class IndicatorResult(
        val lines: List<LineSeries>,
        val markers: List<SignalMarker>,
        val tableData: DccTableData? = null
    )

    // ── Colors (Master Theme from Pine Script Group 16) ────────
    const val COL_FAST       = "#B37D13"  // Fast EMA (Golden Brown)
    const val COL_SLOW       = "#1E4D6B"  // Slow EMA (Navy / Deep Blue)
    const val COL_VWAP       = "#6D4C7D"  // VWAP (Purple)
    const val COL_HTF_EMA    = "#D29922"  // HTF EMA 1
    const val COL_DAILY_OPEN = "#707A7B"  // Daily Open
    const val COL_PD_LVL     = "#802C44"  // PDH / PDL (Wine Red)
    const val COL_BULL       = "#4F7965"  // Bullish (Green)
    const val COL_BEAR       = "#A35C62"  // Bearish (Muted Red)
    const val COL_P3_BULL    = "#00B0FF"  // P3 Re-Entry Buy (Light Blue)
    const val COL_P3_BEAR    = "#FF6D00"  // P3 Re-Entry Sell (Orange)
    const val COL_SQZ_COMP   = "#C15912"  // Squeeze Compression (Orange)
    const val COL_SQZ_REL    = "#4F7965"  // Squeeze Release (Green)
    const val COL_NEUTRAL    = "#8B949E"  // Neutral Gray

    // ── Main Calculation ───────────────────────────────────────

    fun calculate(candles: List<OandaCandle>): IndicatorResult {
        val n = candles.size
        if (n < 25) return IndicatorResult(emptyList(), emptyList(), null)

        val closes = candles.map { it.close }
        val highs  = candles.map { it.high }
        val lows   = candles.map { it.low }
        val opens  = candles.map { it.open }
        val times  = candles.map { it.time }
        val volumes = candles.map { max(it.volume.toDouble(), 1.0) }

        // ═══════════════════════════════════════════════════════
        // 01 • Fast EMA (9) & Slow EMA (21)
        // ═══════════════════════════════════════════════════════
        val emaFast = ema(closes, 9)
        val emaSlow = ema(closes, 21)

        // ═══════════════════════════════════════════════════════
        // 02 • VWAP (Daily Anchored)
        // ═══════════════════════════════════════════════════════
        val hlc3 = List(n) { (highs[it] + lows[it] + closes[it]) / 3.0 }
        val vwap = mutableListOf<Double>()
        var cumPv = 0.0
        var cumVol = 0.0
        var currentDay = -1L

        for (i in 0 until n) {
            val day = times[i] / 86400L
            if (day != currentDay) {
                currentDay = day
                cumPv = 0.0
                cumVol = 0.0
            }
            cumPv += hlc3[i] * volumes[i]
            cumVol += volumes[i]
            vwap += if (cumVol > 0.0) cumPv / cumVol else closes[i]
        }

        // ═══════════════════════════════════════════════════════
        // 03 • HTF EMA 1 (Smoothed H1 Approximation) & Bias
        // ═══════════════════════════════════════════════════════
        val barSec = if (n > 1) (times[1] - times[0]).coerceAtLeast(60L) else 3600L
        val htfRatio = (3600.0 / barSec).coerceAtLeast(1.0)
        val smoothEmaLen = (21 * htfRatio).roundToInt().coerceAtLeast(1)
        val htfEma = ema(closes, smoothEmaLen)

        val lastClose = closes.last()
        val lastHtfEma = htfEma.last()
        val biasLong = lastClose > lastHtfEma
        val biasShort = lastClose < lastHtfEma
        val biasText = when {
            biasLong -> "LONG"
            biasShort -> "SHORT"
            else -> "FLAT"
        }
        val biasColor = when {
            biasLong -> COL_BULL
            biasShort -> COL_BEAR
            else -> COL_NEUTRAL
        }

        // ═══════════════════════════════════════════════════════
        // 04 • ADX / DMI (14)
        // ═══════════════════════════════════════════════════════
        val tr = MutableList(n) { 0.0 }
        for (i in 0 until n) {
            tr[i] = if (i == 0) highs[i] - lows[i] else maxOf(
                highs[i] - lows[i],
                abs(highs[i] - closes[i - 1]),
                abs(lows[i] - closes[i - 1])
            )
        }
        val atr = rma(tr, 14)

        val plusDM = MutableList(n) { 0.0 }
        val minusDM = MutableList(n) { 0.0 }
        for (i in 1 until n) {
            val upMove = highs[i] - highs[i - 1]
            val downMove = lows[i - 1] - lows[i]
            plusDM[i] = if (upMove > downMove && upMove > 0) upMove else 0.0
            minusDM[i] = if (downMove > upMove && downMove > 0) downMove else 0.0
        }
        val smoothPlusDM = rma(plusDM, 14)
        val smoothMinusDM = rma(minusDM, 14)

        val plusDI = MutableList(n) { 0.0 }
        val minusDI = MutableList(n) { 0.0 }
        val dx = MutableList(n) { 0.0 }
        for (i in 0 until n) {
            val a = if (atr[i] > 0.0) atr[i] else 1.0
            plusDI[i] = 100.0 * smoothPlusDM[i] / a
            minusDI[i] = 100.0 * smoothMinusDM[i] / a
            val diSum = plusDI[i] + minusDI[i]
            dx[i] = if (diSum > 0.0) abs(plusDI[i] - minusDI[i]) / diSum * 100.0 else 0.0
        }
        val adx = rma(dx, 14)

        // ═══════════════════════════════════════════════════════
        // 05 • ATR / Risk Stop Loss
        // ═══════════════════════════════════════════════════════
        val lastAtr = atr.last()
        val atrSLDist = lastAtr * 0.9
        val liveSL = when {
            biasLong -> lastClose - atrSLDist
            biasShort -> lastClose + atrSLDist
            else -> null
        }
        val slText = when {
            liveSL != null -> "SL %.4f (±%.1f pts)".format(liveSL, atrSLDist)
            else -> "FLAT"
        }

        // ═══════════════════════════════════════════════════════
        // 10 • Trend Strength Matrix (TSM) Composite Score
        // ═══════════════════════════════════════════════════════
        val rsi14 = rsi(closes, 14)
        val macdFast = ema(closes, 12)
        val macdSlow = ema(closes, 26)
        val macdLine = List(n) { macdFast[it] - macdSlow[it] }
        val macdStdev = stdev(macdLine, 100)
        val ma50 = sma(closes, 50)

        var tsmScorePct = 0.0
        if (n > 50) {
            val lastRsi = rsi14.last()
            val rsiScore = (lastRsi - 50.0) / 50.0

            val lastMacd = macdLine.last()
            val lastMacdNorm = macdStdev.last()
            val macdScore = if (lastMacdNorm > 0.0) (lastMacd / (lastMacdNorm * 2.0)).coerceIn(-1.0, 1.0) else 0.0

            val lastAdx = adx.last()
            val adxStrength = min(lastAdx / 50.0, 1.0)
            val adxDir = if (plusDI.last() > minusDI.last()) 1.0 else -1.0
            val adxScore = adxStrength * adxDir

            val lastMa = ma50.last()
            val maScore = if (lastClose > lastMa) 1.0 else -1.0
            val maStrength = if (lastMa > 0.0) abs(lastClose - lastMa) / lastMa * 10.0 else 0.0
            val maScoreWeighted = (maScore * min(maStrength, 1.0)).coerceIn(-1.0, 1.0)

            val composite = (rsiScore * 0.25 + macdScore * 0.25 + adxScore * 0.25 + maScoreWeighted * 0.25)
            tsmScorePct = composite * 100.0
        }

        val star = if (abs(tsmScorePct) >= 50.0) " ★" else ""
        val tsmStripText = "TSM %+.0f%%%s".format(tsmScorePct, star)

        // ═══════════════════════════════════════════════════════
        // 15 • Squeeze Index (SQZ) Calculation
        // ═══════════════════════════════════════════════════════
        var maxSqz = closes[0]
        var minSqz = closes[0]
        val diffSqz = MutableList(n) { 0.0 }
        val sqzConv = 40.0

        for (i in 0 until n) {
            val c = closes[i]
            maxSqz = max(c, maxSqz - (maxSqz - c) / sqzConv)
            minSqz = min(c, minSqz + (c - minSqz) / sqzConv)
            val diff = max(maxSqz - minSqz, 1e-8)
            diffSqz[i] = ln(diff)
        }

        val sqzLen = 20
        val barIndices = List(n) { it.toDouble() }
        val psi = MutableList(n) { 50.0 }
        for (i in sqzLen until n) {
            val subDiff = diffSqz.subList(i - sqzLen + 1, i + 1)
            val subBar = barIndices.subList(i - sqzLen + 1, i + 1)
            val corr = correlation(subDiff, subBar)
            psi[i] = -50.0 * corr + 50.0
        }

        val lastPsi = psi.last()
        val prevPsi = if (n > 1) psi[n - 2] else lastPsi
        val sqzState = when {
            lastPsi >= 80.0 -> "COMP"
            lastPsi < prevPsi -> "REL"
            else -> "IDLE"
        }
        val sqzColor = when (sqzState) {
            "COMP" -> COL_SQZ_COMP
            "REL" -> COL_SQZ_REL
            else -> COL_NEUTRAL
        }

        // ═══════════════════════════════════════════════════════
        // 11 & 12 • EMA Crossover (❌) & Pullback / P3 Signals
        // ═══════════════════════════════════════════════════════
        val markers = mutableListOf<SignalMarker>()

        var trendDir = 0
        var pbStage = 0
        var pbSwingExtreme = 0.0
        var crossBarIdx = 0
        var pbTouchBar = 0

        var p3State = 0
        var p3Dir = 0
        var p3TouchBar = 0
        var p3BreakLevel = 0.0
        var lastP3SignalBar = -9999

        for (i in 1 until n) {
            val crossBull = emaFast[i] > emaSlow[i] && emaFast[i - 1] <= emaSlow[i - 1]
            val crossBear = emaFast[i] < emaSlow[i] && emaFast[i - 1] >= emaSlow[i - 1]

            // 11 • EMA Crossover Markers (❌)
            if (crossBull) {
                markers += SignalMarker(times[i], MarkerPosition.BELOW, COL_BULL, MarkerShape.SQUARE, "❌")
                trendDir = 1
                pbStage = 1
                crossBarIdx = i
                pbSwingExtreme = highs[i]
                p3State = 0
            } else if (crossBear) {
                markers += SignalMarker(times[i], MarkerPosition.ABOVE, COL_BEAR, MarkerShape.SQUARE, "❌")
                trendDir = -1
                pbStage = 1
                crossBarIdx = i
                pbSwingExtreme = lows[i]
                p3State = 0
            }

            // 12 • Pullback Entry Signals
            val bullEmaTouch = lows[i] <= emaFast[i] && closes[i] >= emaSlow[i]
            val bearEmaTouch = highs[i] >= emaFast[i] && closes[i] <= emaSlow[i]

            if (trendDir != 0) {
                if (pbStage == 1 && i > crossBarIdx) {
                    if (trendDir == 1) {
                        if (highs[i] >= pbSwingExtreme) pbSwingExtreme = highs[i]
                        if (bullEmaTouch) {
                            pbStage = 2
                            pbTouchBar = i
                        }
                    } else if (trendDir == -1) {
                        if (lows[i] <= pbSwingExtreme) pbSwingExtreme = lows[i]
                        if (bearEmaTouch) {
                            pbStage = 2
                            pbTouchBar = i
                        }
                    }
                } else if (pbStage == 2 && i > pbTouchBar) {
                    if (trendDir == 1) {
                        if (closes[i] > pbSwingExtreme && closes[i] > vwap[i]) {
                            markers += SignalMarker(times[i], MarkerPosition.BELOW, COL_BULL, MarkerShape.ARROW_UP, "PB BUY")
                            pbStage = 3
                        }
                    } else if (trendDir == -1) {
                        if (closes[i] < pbSwingExtreme && closes[i] < vwap[i]) {
                            markers += SignalMarker(times[i], MarkerPosition.ABOVE, COL_BEAR, MarkerShape.ARROW_DOWN, "PB SELL")
                            pbStage = 3
                        }
                    }
                }
            }

            // P3 Re-Entry Signals
            val trendStackBull = emaFast[i] > emaSlow[i] && closes[i] > emaSlow[i]
            val trendStackBear = emaFast[i] < emaSlow[i] && closes[i] < emaSlow[i]
            val distOk = (i - lastP3SignalBar) >= 10

            if (p3State == 1 && i > p3TouchBar) {
                val barsSinceTouch = i - p3TouchBar
                if (p3Dir == 1 && closes[i] > p3BreakLevel && barsSinceTouch <= 3 && distOk) {
                    markers += SignalMarker(times[i], MarkerPosition.BELOW, COL_P3_BULL, MarkerShape.ARROW_UP, "P3 BUY")
                    lastP3SignalBar = i
                    p3State = 0
                } else if (p3Dir == -1 && closes[i] < p3BreakLevel && barsSinceTouch <= 3 && distOk) {
                    markers += SignalMarker(times[i], MarkerPosition.ABOVE, COL_P3_BEAR, MarkerShape.ARROW_DOWN, "P3 SELL")
                    lastP3SignalBar = i
                    p3State = 0
                } else if (barsSinceTouch >= 3) {
                    p3State = 0
                }
            }

            if (p3State == 0 && (pbStage == 0 || pbStage == 3) && distOk) {
                if (trendStackBull && bullEmaTouch) {
                    p3State = 1
                    p3Dir = 1
                    p3TouchBar = i
                    p3BreakLevel = highs[i]
                } else if (trendStackBear && bearEmaTouch) {
                    p3State = 1
                    p3Dir = -1
                    p3TouchBar = i
                    p3BreakLevel = lows[i]
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        // Chart Line Series
        // ═══════════════════════════════════════════════════════
        val lines = listOf(
            LineSeries("Fast EMA (9)",   COL_FAST,    1, times.zip(emaFast) { t, v -> LinePoint(t, v) }),
            LineSeries("Slow EMA (21)",  COL_SLOW,    1, times.zip(emaSlow) { t, v -> LinePoint(t, v) }),
            LineSeries("VWAP (Daily)",   COL_VWAP,    1, times.zip(vwap)    { t, v -> LinePoint(t, v) }),
            LineSeries("HTF EMA 1 (H1)", COL_HTF_EMA, 1, times.zip(htfEma)  { t, v -> LinePoint(t, v) }),
        )

        val tableData = DccTableData(
            bias = biasText,
            biasColor = biasColor,
            adx = adx.last(),
            tsmScorePct = tsmScorePct,
            tsmStripText = tsmStripText,
            liveSL = liveSL,
            slText = slText,
            sqzState = sqzState,
            sqzValue = lastPsi,
            sqzColor = sqzColor
        )

        return IndicatorResult(lines, markers, tableData)
    }

    // ── Reusable Technical Analysis Math Helpers ───────────────

    fun ema(data: List<Double>, period: Int): List<Double> {
        if (data.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1.0)
        val out = ArrayList<Double>(data.size)
        var prev = data[0]
        for (i in data.indices) {
            prev = if (i == 0) data[0] else data[i] * k + prev * (1.0 - k)
            out.add(prev)
        }
        return out
    }

    fun sma(data: List<Double>, period: Int): List<Double> {
        val out = ArrayList<Double>(data.size)
        var sum = 0.0
        for (i in data.indices) {
            sum += data[i]
            if (i >= period) sum -= data[i - period]
            val count = min(i + 1, period)
            out.add(sum / count)
        }
        return out
    }

    /** Wilder's RMA (used in Pine Script for ATR, ADX) */
    fun rma(data: List<Double>, period: Int): List<Double> {
        if (data.isEmpty()) return emptyList()
        val alpha = 1.0 / period.toDouble()
        val out = ArrayList<Double>(data.size)
        var prev = 0.0
        for (i in data.indices) {
            prev = if (i == 0) data[0] else alpha * data[i] + (1.0 - alpha) * prev
            out.add(prev)
        }
        return out
    }

    fun rsi(data: List<Double>, period: Int = 14): List<Double> {
        val n = data.size
        if (n < 2) return List(n) { 50.0 }
        val gains = MutableList(n) { 0.0 }
        val losses = MutableList(n) { 0.0 }
        for (i in 1 until n) {
            val d = data[i] - data[i - 1]
            if (d >= 0) gains[i] = d else losses[i] = -d
        }
        val avgGains = rma(gains, period)
        val avgLosses = rma(losses, period)
        return List(n) { i ->
            val ag = avgGains[i]
            val al = avgLosses[i]
            if (al == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + ag / al))
        }
    }

    fun stdev(data: List<Double>, period: Int): List<Double> {
        val out = ArrayList<Double>(data.size)
        for (i in data.indices) {
            val start = max(0, i - period + 1)
            val sub = data.subList(start, i + 1)
            val mean = sub.average()
            val variance = sub.sumOf { (it - mean) * (it - mean) } / sub.size
            out.add(sqrt(variance))
        }
        return out
    }

    fun correlation(x: List<Double>, y: List<Double>): Double {
        val n = min(x.size, y.size)
        if (n < 2) return 0.0
        val meanX = x.average()
        val meanY = y.average()
        var cov = 0.0
        var varX = 0.0
        var varY = 0.0
        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            cov += dx * dy
            varX += dx * dx
            varY += dy * dy
        }
        val denom = sqrt(varX * varY)
        return if (denom > 0.0) (cov / denom).coerceIn(-1.0, 1.0) else 0.0
    }
}
