package com.example.ui.chart

import com.example.model.Candle
import com.example.model.Timeframe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bidirectional coordinate mapper syncing chart timestamps and prices
 * to Canvas pixel coordinates across arbitrary zoom and pan offsets.
 */
class ChartCoordinateMapper(
    val candles: List<Candle>,
    val timeframe: Timeframe,
    val plotWidth: Float,
    val plotHeight: Float,
    val topPadding: Float,
    val bottomPadding: Float,
    val barSpacingPx: Float,
    val rightOffsetBars: Float,
    val minPrice: Double,
    val maxPrice: Double
) {
    val lastIndex: Int = (candles.size - 1).coerceAtLeast(0)
    val usableHeight: Float = (plotHeight - topPadding - bottomPadding).coerceAtLeast(1f)
    val priceSpan: Double = (maxPrice - minPrice).coerceAtLeast(1e-8)
    val centerPrice: Double get() = (maxPrice + minPrice) / 2.0
    val priceSpanValue: Double get() = priceSpan
    val usableHeightPx: Float get() = usableHeight

    fun indexToX(barIndex: Float): Float {
        return plotWidth - (lastIndex - barIndex + rightOffsetBars) * barSpacingPx
    }

    fun xToIndex(x: Float): Float {
        return lastIndex + rightOffsetBars - (plotWidth - x) / barSpacingPx
    }

    fun visibleIndexRange(): IntRange {
        if (candles.isEmpty()) return IntRange.EMPTY
        val left = floor(xToIndex(0f)).toInt().coerceIn(0, lastIndex)
        val right = ceil(xToIndex(plotWidth)).toInt().coerceIn(0, lastIndex)
        return left..right
    }

    fun priceToY(price: Double): Float {
        val ratio = ((maxPrice - price) / priceSpan).toFloat()
        return topPadding + ratio * usableHeight
    }

    fun yToPrice(y: Float): Double {
        val ratio = ((y - topPadding) / usableHeight).toDouble()
        return maxPrice - ratio * priceSpan
    }

    fun timeToBarIndexFloat(timeEpochSec: Long): Float {
        if (candles.isEmpty()) return 0f
        val first = candles.first().timeEpochSec
        val last = candles.last().timeEpochSec
        val dur = timeframe.durationSec.toDouble().coerceAtLeast(1.0)

        if (timeEpochSec <= first) {
            return -((first - timeEpochSec) / dur).toFloat()
        }
        if (timeEpochSec >= last) {
            return lastIndex + ((timeEpochSec - last) / dur).toFloat()
        }

        // Binary search for bracketing candles
        var low = 0
        var high = lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val t = candles[mid].timeEpochSec
            when {
                t == timeEpochSec -> return mid.toFloat()
                t < timeEpochSec -> low = mid + 1
                else -> high = mid - 1
            }
        }
        val leftIdx = high.coerceIn(0, lastIndex)
        val rightIdx = low.coerceIn(0, lastIndex)
        if (leftIdx == rightIdx) return leftIdx.toFloat()
        val t0 = candles[leftIdx].timeEpochSec
        val t1 = candles[rightIdx].timeEpochSec
        val frac = if (t1 > t0) (timeEpochSec - t0).toFloat() / (t1 - t0).toFloat() else 0f
        return leftIdx + frac
    }

    fun timeToX(timeEpochSec: Long): Float = indexToX(timeToBarIndexFloat(timeEpochSec))

    fun xToTime(x: Float): Long {
        if (candles.isEmpty()) return System.currentTimeMillis() / 1000L
        val idxF = xToIndex(x)
        val dur = timeframe.durationSec
        if (idxF <= 0f) {
            return candles.first().timeEpochSec + (idxF * dur).toLong()
        }
        if (idxF >= lastIndex) {
            return candles.last().timeEpochSec + ((idxF - lastIndex) * dur).toLong()
        }
        val snapped = idxF.roundToInt().coerceIn(0, lastIndex)
        return candles[snapped].timeEpochSec
    }

    fun nearestCandleIndexForX(x: Float): Int? {
        if (candles.isEmpty()) return null
        return xToIndex(x).roundToInt().coerceIn(0, lastIndex)
    }

    fun barCountBetween(t1: Long, t2: Long): Int {
        val i1 = timeToBarIndexFloat(t1)
        val i2 = timeToBarIndexFloat(t2)
        return kotlin.math.abs(i2 - i1).roundToInt()
    }

    companion object {
        fun computeAutoBounds(
            candles: List<Candle>,
            dccResult: com.example.indicators.DccMasterResult,
            includeDccBounds: Boolean,
            customOverlays: List<com.example.indicators.PineEvaluationResult>,
            plotWidth: Float,
            barSpacingPx: Float,
            rightOffsetBars: Float
        ): Pair<Double, Double> {
            val lastIdx = (candles.size - 1).coerceAtLeast(0)
            val approxLeft = floor(lastIdx + rightOffsetBars - plotWidth / barSpacingPx)
                .toInt().coerceIn(0, lastIdx)
            val approxRight = ceil(lastIdx + rightOffsetBars)
                .toInt().coerceIn(0, lastIdx)

            var minP = Double.POSITIVE_INFINITY
            var maxP = Double.NEGATIVE_INFINITY

            if (candles.isNotEmpty()) {
                for (i in approxLeft..approxRight) {
                    val c = candles[i]
                    minP = min(minP, c.low)
                    maxP = max(maxP, c.high)
                    if (includeDccBounds) {
                        dccResult.emaFast.getOrNull(i)?.let { if (it.isFinite()) { minP = min(minP, it); maxP = max(maxP, it) } }
                        dccResult.emaSlow.getOrNull(i)?.let { if (it.isFinite()) { minP = min(minP, it); maxP = max(maxP, it) } }
                        dccResult.vwap.getOrNull(i)?.let { if (it.isFinite()) { minP = min(minP, it); maxP = max(maxP, it) } }
                        dccResult.htfEma1.getOrNull(i)?.let { if (it.isFinite()) { minP = min(minP, it); maxP = max(maxP, it) } }
                    }
                    for (ov in customOverlays) {
                        for (plot in ov.plots) {
                            plot.values.getOrNull(i)?.let {
                                if (it.isFinite()) {
                                    minP = min(minP, it)
                                    maxP = max(maxP, it)
                                }
                            }
                        }
                    }
                }
            }

            if (!minP.isFinite() || !maxP.isFinite() || minP >= maxP) {
                minP = 0.0
                maxP = 1.0
            } else {
                val pad = (maxP - minP) * 0.10
                minP -= pad
                maxP += pad
            }
            return Pair(minP, maxP)
        }

        fun build(
            candles: List<Candle>,
            dccResult: com.example.indicators.DccMasterResult,
            includeDccBounds: Boolean,
            customOverlays: List<com.example.indicators.PineEvaluationResult>,
            timeframe: Timeframe,
            plotWidth: Float,
            plotHeight: Float,
            topPadding: Float,
            bottomPadding: Float,
            barSpacingPx: Float,
            rightOffsetBars: Float,
            customMinPrice: Double? = null,
            customMaxPrice: Double? = null
        ): ChartCoordinateMapper {
            val minP: Double
            val maxP: Double

            if (customMinPrice != null && customMaxPrice != null &&
                customMinPrice.isFinite() && customMaxPrice.isFinite() &&
                customMaxPrice > customMinPrice
            ) {
                minP = customMinPrice
                maxP = customMaxPrice
            } else {
                val autoBounds = computeAutoBounds(
                    candles = candles,
                    dccResult = dccResult,
                    includeDccBounds = includeDccBounds,
                    customOverlays = customOverlays,
                    plotWidth = plotWidth,
                    barSpacingPx = barSpacingPx,
                    rightOffsetBars = rightOffsetBars
                )
                minP = autoBounds.first
                maxP = autoBounds.second
            }

            return ChartCoordinateMapper(
                candles = candles,
                timeframe = timeframe,
                plotWidth = plotWidth,
                plotHeight = plotHeight,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
                barSpacingPx = barSpacingPx,
                rightOffsetBars = rightOffsetBars,
                minPrice = minP,
                maxPrice = maxP
            )
        }
    }
}

object ChartTimeFormatter {
    private val intradayFmt = SimpleDateFormat("HH:mm", Locale.US)
    private val multiDayFmt = SimpleDateFormat("MMM dd HH:mm", Locale.US)
    private val dateOnlyFmt = SimpleDateFormat("MMM dd", Locale.US)
    private val fullHudFmt = SimpleDateFormat("MMM dd, HH:mm", Locale.US)

    fun formatAxisLabel(epochSec: Long, timeframe: Timeframe): String {
        val date = Date(epochSec * 1000L)
        return when (timeframe) {
            Timeframe.M1, Timeframe.M3, Timeframe.M5, Timeframe.M15, Timeframe.M30 ->
                intradayFmt.format(date)
            Timeframe.H1, Timeframe.H4 ->
                multiDayFmt.format(date)
            Timeframe.D1, Timeframe.W1 ->
                dateOnlyFmt.format(date)
        }
    }

    fun formatHudTimestamp(epochSec: Long): String =
        fullHudFmt.format(Date(epochSec * 1000L))
}
