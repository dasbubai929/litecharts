package com.litecharts.app.indicator

import com.litecharts.app.data.OandaCandle

/**
 * ════════════════════════════════════════════════════════════════
 *  DCC Indicator Engine
 * ════════════════════════════════════════════════════════════════
 *
 * Modular, well-typed indicator engine.
 *
 *  HOW TO ADD YOUR DCC PINE SCRIPT LOGIC
 *  ──────────────────────────────────────
 *  1.  Open this file.
 *  2.  Replace the placeholder code inside `calculateIndicators()`
 *      with your translated DCC Pine Script math.
 *  3.  Build & run — the chart and UI code stays untouched.
 *
 *  The placeholder below renders a simple EMA channel + ATR bands
 *  so the chart has something to display immediately.
 */
object DccIndicatorEngine {

    // ── Output types ───────────────────────────────────────────

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

    data class IndicatorResult(
        val lines: List<LineSeries>,
        val markers: List<SignalMarker>
    )

    // ── Main entry point ───────────────────────────────────────

    /**
     * Calculate DCC indicator values aligned with [candles] timestamps.
     *
     * @return [IndicatorResult] containing named line series and signal markers.
     */
    fun calculate(candles: List<OandaCandle>): IndicatorResult {
        if (candles.size < 21) return IndicatorResult(emptyList(), emptyList())

        // ═══════════════════════════════════════════════════════
        //  PLACEHOLDER — replace with your DCC Pine Script math
        // ═══════════════════════════════════════════════════════

        val closes = candles.map { it.close }
        val highs  = candles.map { it.high }
        val lows   = candles.map { it.low }
        val times  = candles.map { it.time }

        val emaFast = ema(closes, 9)
        val emaSlow = ema(closes, 21)

        // Simple ATR-based channel
        val upperBand = mutableListOf<Double>()
        val lowerBand = mutableListOf<Double>()
        for (i in closes.indices) {
            val tr = if (i > 0) maxOf(
                highs[i] - lows[i],
                Math.abs(highs[i] - closes[i - 1]),
                Math.abs(lows[i] - closes[i - 1])
            ) else highs[i] - lows[i]
            upperBand += emaSlow[i] + tr * 1.5
            lowerBand += emaSlow[i] - tr * 1.5
        }

        // EMA crossover markers
        val markers = mutableListOf<SignalMarker>()
        for (i in 1 until closes.size) {
            if (emaFast[i] > emaSlow[i] && emaFast[i - 1] <= emaSlow[i - 1]) {
                markers += SignalMarker(times[i], MarkerPosition.BELOW, "#26A69A", MarkerShape.ARROW_UP, "▲")
            } else if (emaFast[i] < emaSlow[i] && emaFast[i - 1] >= emaSlow[i - 1]) {
                markers += SignalMarker(times[i], MarkerPosition.ABOVE, "#EF5350", MarkerShape.ARROW_DOWN, "▼")
            }
        }

        val lines = listOf(
            LineSeries("DCC Fast",  "#58A6FF", 1, times.zip(emaFast) .map { (t, v) -> LinePoint(t, v) }),
            LineSeries("DCC Slow",  "#D29922", 1, times.zip(emaSlow) .map { (t, v) -> LinePoint(t, v) }),
            LineSeries("DCC Upper", "#3FB950", 1, times.zip(upperBand).map { (t, v) -> LinePoint(t, v) }),
            LineSeries("DCC Lower", "#F85149", 1, times.zip(lowerBand).map { (t, v) -> LinePoint(t, v) }),
        )

        // ═══════════════════════════════════════════════════════
        //  END PLACEHOLDER
        // ═══════════════════════════════════════════════════════

        return IndicatorResult(lines, markers)
    }

    // ── Reusable TA helpers (keep these when you paste your logic) ──

    fun ema(data: List<Double>, period: Int): List<Double> {
        if (data.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1)
        val out = mutableListOf<Double>()
        var prev = data.take(period).average()
        for (i in data.indices) {
            if (i < period) {
                out += data.take(i + 1).average()
            } else {
                prev = (data[i] - prev) * k + prev
                out += prev
            }
        }
        return out
    }

    fun sma(data: List<Double>, period: Int): List<Double> =
        data.indices.map { i ->
            if (i < period - 1) data.take(i + 1).average()
            else data.subList(i - period + 1, i + 1).average()
        }

    fun rsi(data: List<Double>, period: Int = 14): List<Double> {
        if (data.size < 2) return data.map { 50.0 }
        val result = mutableListOf(50.0)
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        for (i in 1 until data.size) {
            val d = data[i] - data[i - 1]
            gains += maxOf(d, 0.0); losses += maxOf(-d, 0.0)
        }
        if (gains.size < period) return data.map { 50.0 }
        var ag = gains.take(period).average()
        var al = losses.take(period).average()
        for (i in gains.indices) {
            if (i < period) { result += 50.0; continue }
            ag = (ag * (period - 1) + gains[i]) / period
            al = (al * (period - 1) + losses[i]) / period
            result += if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al)
        }
        return result
    }

    fun atr(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): List<Double> {
        val tr = highs.indices.map { i ->
            if (i == 0) highs[i] - lows[i]
            else maxOf(highs[i] - lows[i], Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1]))
        }
        return sma(tr, period)
    }
}
