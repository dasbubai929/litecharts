package com.example.indicators

import com.example.model.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure Kotlin implementations of Pine Script v6 `ta.*` functions,
 * shared by both DccMasterEngine and the custom PineScriptEngine.
 */
object PineTa {

    fun sourceSeries(candles: List<Candle>, sourceName: String): DoubleArray {
        val n = candles.size
        val out = DoubleArray(n)
        for (i in 0 until n) {
            val c = candles[i]
            out[i] = when (sourceName.lowercase()) {
                "open" -> c.open
                "high" -> c.high
                "low" -> c.low
                "hl2" -> (c.high + c.low) * 0.5
                "hlc3" -> (c.high + c.low + c.close) / 3.0
                "ohlc4" -> (c.open + c.high + c.low + c.close) * 0.25
                "hlcc4" -> (c.high + c.low + c.close + c.close) * 0.25
                "volume" -> c.volume.toDouble()
                else -> c.close
            }
        }
        return out
    }

    fun sma(src: DoubleArray, length: Int): DoubleArray {
        val n = src.size
        val len = length.coerceAtLeast(1)
        val out = DoubleArray(n) { Double.NaN }
        var sum = 0.0
        var validCount = 0
        for (i in 0 until n) {
            val v = src[i]
            if (!v.isNaN()) {
                sum += v
                validCount++
            }
            if (i >= len) {
                val old = src[i - len]
                if (!old.isNaN()) {
                    sum -= old
                    validCount--
                }
            }
            if (i >= len - 1 && validCount == len) {
                out[i] = sum / len
            }
        }
        return out
    }

    fun ema(src: DoubleArray, length: Int): DoubleArray {
        val n = src.size
        val len = length.coerceAtLeast(1)
        val out = DoubleArray(n) { Double.NaN }
        val alpha = 2.0 / (len + 1.0)
        var seeded = false
        var prev = Double.NaN
        var seedSum = 0.0
        var seedCount = 0

        for (i in 0 until n) {
            val v = src[i]
            if (v.isNaN()) continue
            if (!seeded) {
                seedSum += v
                seedCount++
                if (seedCount == len) {
                    prev = seedSum / len
                    out[i] = prev
                    seeded = true
                }
            } else {
                prev = alpha * v + (1.0 - alpha) * prev
                out[i] = prev
            }
        }
        return out
    }

    fun rma(src: DoubleArray, length: Int): DoubleArray {
        val n = src.size
        val len = length.coerceAtLeast(1)
        val out = DoubleArray(n) { Double.NaN }
        val alpha = 1.0 / len
        var seeded = false
        var prev = Double.NaN
        var seedSum = 0.0
        var seedCount = 0

        for (i in 0 until n) {
            val v = src[i]
            if (v.isNaN()) continue
            if (!seeded) {
                seedSum += v
                seedCount++
                if (seedCount == len) {
                    prev = seedSum / len
                    out[i] = prev
                    seeded = true
                }
            } else {
                prev = alpha * v + (1.0 - alpha) * prev
                out[i] = prev
            }
        }
        return out
    }

    fun wma(src: DoubleArray, length: Int): DoubleArray {
        val n = src.size
        val len = length.coerceAtLeast(1)
        val out = DoubleArray(n) { Double.NaN }
        val denom = len * (len + 1) / 2.0
        for (i in len - 1 until n) {
            var sum = 0.0
            var ok = true
            for (k in 0 until len) {
                val v = src[i - k]
                if (v.isNaN()) {
                    ok = false
                    break
                }
                sum += v * (len - k)
            }
            if (ok) out[i] = sum / denom
        }
        return out
    }

    fun trueRange(candles: List<Candle>): DoubleArray {
        val n = candles.size
        val tr = DoubleArray(n)
        for (i in 0 until n) {
            val c = candles[i]
            tr[i] = if (i == 0) {
                c.high - c.low
            } else {
                val pc = candles[i - 1].close
                max(c.high - c.low, max(abs(c.high - pc), abs(c.low - pc)))
            }
        }
        return tr
    }

    fun atr(candles: List<Candle>, length: Int): DoubleArray =
        rma(trueRange(candles), length)

    data class DmiResult(
        val plusDi: DoubleArray,
        val minusDi: DoubleArray,
        val adx: DoubleArray
    )

    fun dmi(candles: List<Candle>, diLength: Int, adxSmoothing: Int): DmiResult {
        val n = candles.size
        val plusDm = DoubleArray(n)
        val minusDm = DoubleArray(n)
        for (i in 1 until n) {
            val up = candles[i].high - candles[i - 1].high
            val down = candles[i - 1].low - candles[i].low
            plusDm[i] = if (up > down && up > 0.0) up else 0.0
            minusDm[i] = if (down > up && down > 0.0) down else 0.0
        }
        val smoothTr = rma(trueRange(candles), diLength)
        val smoothPlus = rma(plusDm, diLength)
        val smoothMinus = rma(minusDm, diLength)

        val plusDi = DoubleArray(n) { Double.NaN }
        val minusDi = DoubleArray(n) { Double.NaN }
        val dx = DoubleArray(n) { Double.NaN }

        for (i in 0 until n) {
            val tr = smoothTr[i]
            if (!tr.isNaN() && tr > 0.0 && !smoothPlus[i].isNaN() && !smoothMinus[i].isNaN()) {
                val p = 100.0 * smoothPlus[i] / tr
                val m = 100.0 * smoothMinus[i] / tr
                plusDi[i] = p
                minusDi[i] = m
                val sum = p + m
                dx[i] = if (sum > 0.0) 100.0 * abs(p - m) / sum else 0.0
            }
        }
        val adx = rma(dx, adxSmoothing)
        return DmiResult(plusDi, minusDi, adx)
    }

    fun rsi(src: DoubleArray, length: Int): DoubleArray {
        val n = src.size
        val gains = DoubleArray(n)
        val losses = DoubleArray(n)
        for (i in 1 until n) {
            val ch = src[i] - src[i - 1]
            if (!ch.isNaN()) {
                gains[i] = if (ch > 0.0) ch else 0.0
                losses[i] = if (ch < 0.0) -ch else 0.0
            }
        }
        val avgGain = rma(gains, length)
        val avgLoss = rma(losses, length)
        val out = DoubleArray(n) { Double.NaN }
        for (i in 0 until n) {
            val g = avgGain[i]
            val l = avgLoss[i]
            if (!g.isNaN() && !l.isNaN()) {
                out[i] = when {
                    l == 0.0 -> 100.0
                    g == 0.0 -> 0.0
                    else -> 100.0 - (100.0 / (1.0 + g / l))
                }
            }
        }
        return out
    }

    data class MacdResult(
        val macdLine: DoubleArray,
        val signalLine: DoubleArray,
        val histogram: DoubleArray
    )

    fun macd(src: DoubleArray, fastLen: Int, slowLen: Int, sigLen: Int): MacdResult {
        val fast = ema(src, fastLen)
        val slow = ema(src, slowLen)
        val n = src.size
        val line = DoubleArray(n) { i ->
            if (fast[i].isNaN() || slow[i].isNaN()) Double.NaN else fast[i] - slow[i]
        }
        val sig = ema(line, sigLen)
        val hist = DoubleArray(n) { i ->
            if (line[i].isNaN() || sig[i].isNaN()) Double.NaN else line[i] - sig[i]
        }
        return MacdResult(line, sig, hist)
    }

    fun stdev(src: DoubleArray, length: Int): DoubleArray {
        val n = src.size
        val len = length.coerceAtLeast(1)
        val out = DoubleArray(n) { Double.NaN }
        for (i in len - 1 until n) {
            var sum = 0.0
            var count = 0
            for (k in (i - len + 1)..i) {
                val v = src[k]
                if (!v.isNaN()) {
                    sum += v
                    count++
                }
            }
            if (count == len) {
                val mean = sum / len
                var sq = 0.0
                for (k in (i - len + 1)..i) {
                    val d = src[k] - mean
                    sq += d * d
                }
                out[i] = sqrt(sq / len)
            }
        }
        return out
    }

    fun correlation(x: DoubleArray, y: DoubleArray, length: Int): DoubleArray {
        val n = min(x.size, y.size)
        val len = length.coerceAtLeast(2)
        val out = DoubleArray(n) { Double.NaN }
        for (i in len - 1 until n) {
            var sx = 0.0
            var sy = 0.0
            var ok = true
            for (k in (i - len + 1)..i) {
                if (x[k].isNaN() || y[k].isNaN()) {
                    ok = false
                    break
                }
                sx += x[k]
                sy += y[k]
            }
            if (!ok) continue
            val mx = sx / len
            val my = sy / len
            var num = 0.0
            var dx2 = 0.0
            var dy2 = 0.0
            for (k in (i - len + 1)..i) {
                val dx = x[k] - mx
                val dy = y[k] - my
                num += dx * dy
                dx2 += dx * dx
                dy2 += dy * dy
            }
            val denom = sqrt(dx2 * dy2)
            out[i] = if (denom > 1e-12) num / denom else 0.0
        }
        return out
    }

    fun highest(src: DoubleArray, length: Int): DoubleArray {
        val n = src.size
        val len = length.coerceAtLeast(1)
        val out = DoubleArray(n) { Double.NaN }
        for (i in len - 1 until n) {
            var mx = Double.NEGATIVE_INFINITY
            var ok = true
            for (k in (i - len + 1)..i) {
                if (src[k].isNaN()) {
                    ok = false
                    break
                }
                mx = max(mx, src[k])
            }
            if (ok) out[i] = mx
        }
        return out
    }

    fun lowest(src: DoubleArray, length: Int): DoubleArray {
        val n = src.size
        val len = length.coerceAtLeast(1)
        val out = DoubleArray(n) { Double.NaN }
        for (i in len - 1 until n) {
            var mn = Double.POSITIVE_INFINITY
            var ok = true
            for (k in (i - len + 1)..i) {
                if (src[k].isNaN()) {
                    ok = false
                    break
                }
                mn = min(mn, src[k])
            }
            if (ok) out[i] = mn
        }
        return out
    }

    fun pivotHigh(highs: DoubleArray, leftBars: Int, rightBars: Int): DoubleArray {
        val n = highs.size
        val out = DoubleArray(n) { Double.NaN }
        for (i in (leftBars + rightBars) until n) {
            val pivotIdx = i - rightBars
            val candidate = highs[pivotIdx]
            var isPivot = true
            for (k in (pivotIdx - leftBars) until pivotIdx) {
                if (highs[k] > candidate) {
                    isPivot = false
                    break
                }
            }
            if (isPivot) {
                for (k in (pivotIdx + 1)..(pivotIdx + rightBars)) {
                    if (highs[k] >= candidate) {
                        isPivot = false
                        break
                    }
                }
            }
            if (isPivot) out[i] = candidate
        }
        return out
    }

    fun pivotLow(lows: DoubleArray, leftBars: Int, rightBars: Int): DoubleArray {
        val n = lows.size
        val out = DoubleArray(n) { Double.NaN }
        for (i in (leftBars + rightBars) until n) {
            val pivotIdx = i - rightBars
            val candidate = lows[pivotIdx]
            var isPivot = true
            for (k in (pivotIdx - leftBars) until pivotIdx) {
                if (lows[k] < candidate) {
                    isPivot = false
                    break
                }
            }
            if (isPivot) {
                for (k in (pivotIdx + 1)..(pivotIdx + rightBars)) {
                    if (lows[k] <= candidate) {
                        isPivot = false
                        break
                    }
                }
            }
            if (isPivot) out[i] = candidate
        }
        return out
    }

    // Compatibility overload: retained for custom Pine indicators.
    // Without native D1 candles, this keeps the previous UTC-day behavior.
    fun dailyVwap(
        candles: List<Candle>,
        src: DoubleArray
    ): Pair<DoubleArray, BooleanArray> = dailyVwap(candles, src, emptyList())

    /**
     * Daily VWAP anchored to native OANDA D1 candle start timestamps.
     * D1 candles are aligned to 17:00 America/New_York by the repository.
     */
    fun dailyVwap(
        candles: List<Candle>,
        src: DoubleArray,
        dailyCandles: List<Candle>
    ): Pair<DoubleArray, BooleanArray> {
        val n = candles.size
        val out = DoubleArray(n) { Double.NaN }
        val breaks = BooleanArray(n)

        if (dailyCandles.isEmpty()) {
            return dailyVwapUtcFallback(candles, src)
        }

        var cumPV = 0.0
        var cumV = 0.0
        var dailyIndex = -1
        var activeBucket = Long.MIN_VALUE

        for (i in 0 until n) {
            val candleTime = candles[i].timeEpochSec

            while (
                dailyIndex + 1 < dailyCandles.size &&
                dailyCandles[dailyIndex + 1].timeEpochSec <= candleTime
            ) {
                dailyIndex++
            }

            // Native D1 start time is the bucket identity. For chart history
            // older than the supplied D1 series, use the legacy UTC fallback.
            val bucket = if (dailyIndex >= 0) {
                dailyCandles[dailyIndex].timeEpochSec
            } else {
                -(candleTime / 86_400L + 1L)
            }

            val isNewDay = activeBucket != Long.MIN_VALUE && bucket != activeBucket
            if (activeBucket == Long.MIN_VALUE || isNewDay) {
                cumPV = 0.0
                cumV = 0.0
                if (isNewDay) breaks[i] = true
            }
            activeBucket = bucket

            val volume = max(candles[i].volume.toDouble(), 1.0)
            cumPV += src[i] * volume
            cumV += volume
            out[i] = if (cumV > 0.0) cumPV / cumV else candles[i].close
        }
        return out to breaks
    }

    private fun dailyVwapUtcFallback(
        candles: List<Candle>,
        src: DoubleArray
    ): Pair<DoubleArray, BooleanArray> {
        val n = candles.size
        val out = DoubleArray(n) { Double.NaN }
        val breaks = BooleanArray(n)
        var cumPV = 0.0
        var cumV = 0.0
        var previousDay = Long.MIN_VALUE

        for (i in 0 until n) {
            val day = candles[i].timeEpochSec / 86_400L
            val isNewDay = previousDay != Long.MIN_VALUE && day != previousDay
            if (previousDay == Long.MIN_VALUE || isNewDay) {
                cumPV = 0.0
                cumV = 0.0
                if (isNewDay) breaks[i] = true
            }
            previousDay = day
            val volume = max(candles[i].volume.toDouble(), 1.0)
            cumPV += src[i] * volume
            cumV += volume
            out[i] = if (cumV > 0.0) cumPV / cumV else candles[i].close
        }
        return out to breaks
    }
}
