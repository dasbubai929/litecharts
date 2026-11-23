package com.example.indicators

import com.example.model.Candle
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Exact Kotlin translation of Sections 13 (Large Rolling Volume Profile)
 * and 14 (Profile-Guided Support/Resistance) from DCC+TSM+VPSR+SQZ v2.5.5.
 */
object DccVpSrCalculator {

    private data class MutableSrLvl(
        var price: Double,
        var score: Double,
        var rawPrice: Double,
        var node: Double,
        var evid: Double,
        var nStr: Double,
        val isSupport: Boolean,
        var bornTimeEpochSec: Long
    )

    private data class ProfileSlice(
        val bot: Double,
        val step: Double,
        val tot: DoubleArray,
        val buy: DoubleArray
    )

    private fun clip01(x: Double): Double = max(0.0, min(1.0, x))

    private fun rowId(price: Double, bot: Double, step: Double, rows: Int): Int {
        return max(0, min(rows - 1, floor((price - bot) / step).toInt()))
    }

    private fun computeProfile(
        candles: List<Candle>,
        newestBarIdx: Int,
        oldestBarIdx: Int,
        rows: Int,
        minTick: Double
    ): ProfileSlice {
        val loIdx = max(0, oldestBarIdx)
        val hiIdx = min(candles.lastIndex, newestBarIdx)
        var lo = candles[hiIdx].low
        var hi = candles[hiIdx].high
        for (idx in loIdx..hiIdx) {
            lo = min(lo, candles[idx].low)
            hi = max(hi, candles[idx].high)
        }
        val span = max(hi - lo, minTick * rows)
        val bot = lo - (span - (hi - lo)) * 0.5
        val step = span / rows
        val tot = DoubleArray(rows)
        val buy = DoubleArray(rows)

        for (idx in loIdx..hiIdx) {
            val c = candles[idx]
            val l = c.low
            val h = c.high
            val r = h - l
            val v = max(c.volume.toDouble(), 0.0)
            val bf = if (r > 0.0) clip01((c.close - l) / r) else 0.5
            val r0 = rowId(l, bot, step, rows)
            val r1 = rowId(h, bot, step, rows)
            for (j in r0..r1) {
                val rl = bot + j * step
                val ov = if (r > 0.0) max(0.0, min(h, rl + step) - max(l, rl)) else step
                val frac = if (r > 0.0) ov / r else 1.0
                if (frac > 0.0) {
                    val av = v * frac
                    tot[j] += av
                    buy[j] += av * bf
                }
            }
        }
        return ProfileSlice(bot, step, tot, buy)
    }

    fun calculate(
        candles: List<Candle>,
        settings: DccSettings,
        minTick: Double
    ): Pair<List<DccSrLevel>, DccVolumeProfileData?> {
        if (candles.isEmpty()) return emptyList<DccSrLevel>() to null
        if (!settings.enableSR && !settings.enableVP) return emptyList<DccSrLevel>() to null

        val n = candles.size
        val volumes = DoubleArray(n) { candles[it].volume.toDouble() }
        val highs = DoubleArray(n) { candles[it].high }
        val lows = DoubleArray(n) { candles[it].low }

        val vpAvgVol = PineTa.sma(volumes, 40)
        val vpAtr = PineTa.atr(candles, 14)
        val swingLen = settings.swingLen.coerceIn(2, 25)
        val profRows = settings.profRows.coerceIn(16, 100)
        val profLb = settings.profLb.coerceIn(50, 1500)

        val ph = PineTa.pivotHigh(highs, swingLen, swingLen)
        val pl = PineTa.pivotLow(lows, swingLen, swingLen)

        val srLvls = mutableListOf<MutableSrLvl>()

        if (settings.enableSR) {
            for (barIdx in 0 until n) {
                val vpReady = barIdx > 40 + swingLen * 2 + 5
                if (vpReady && !pl[barIdx].isNaN()) {
                    evalPivot(
                        candles = candles,
                        barIdx = barIdx,
                        raw = pl[barIdx],
                        isSupport = true,
                        vAvg = vpAvgVol,
                        atrArr = vpAtr,
                        srLvls = srLvls,
                        settings = settings,
                        swingLen = swingLen,
                        profLb = profLb,
                        profRows = profRows,
                        minTick = minTick
                    )
                }
                if (vpReady && !ph[barIdx].isNaN()) {
                    evalPivot(
                        candles = candles,
                        barIdx = barIdx,
                        raw = ph[barIdx],
                        isSupport = false,
                        vAvg = vpAvgVol,
                        atrArr = vpAtr,
                        srLvls = srLvls,
                        settings = settings,
                        swingLen = swingLen,
                        profLb = profLb,
                        profRows = profRows,
                        minTick = minTick
                    )
                }

                // Manage breaks on confirmed bars
                val curAtr = if (vpAtr[barIdx].isNaN()) 0.0 else vpAtr[barIdx]
                val buf = max(curAtr * settings.breakAtr, 0.0)
                val close = candles[barIdx].close
                val isConfirmed = candles[barIdx].isComplete || barIdx < n - 1
                if (isConfirmed && srLvls.isNotEmpty()) {
                    for (idx in srLvls.lastIndex downTo 0) {
                        val lv = srLvls[idx]
                        val broken = if (lv.isSupport) close < lv.price - buf else close > lv.price + buf
                        if (broken) srLvls.removeAt(idx)
                    }
                }

                // Trim weakest levels above maxLvl
                while (srLvls.size > settings.maxLvl) {
                    var weakIdx = 0
                    var weakScore = srLvls[0].score
                    for (k in 1 until srLvls.size) {
                        if (srLvls[k].score < weakScore) {
                            weakScore = srLvls[k].score
                            weakIdx = k
                        }
                    }
                    srLvls.removeAt(weakIdx)
                }
            }
        }

        val finalSr = srLvls.map {
            DccSrLevel(
                price = it.price,
                rawPrice = it.rawPrice,
                score = it.score,
                isSupport = it.isSupport,
                bornTimeEpochSec = it.bornTimeEpochSec
            )
        }

        val vpData = if (settings.enableVP) {
            val bars = max(1, min(profLb, n))
            val oldestIdx = max(0, n - bars)
            val slice = computeProfile(candles, n - 1, oldestIdx, profRows, minTick)
            var mx = 0.0
            var pocIdx = 0
            for (r in 0 until profRows) {
                if (slice.tot[r] > mx) {
                    mx = slice.tot[r]
                    pocIdx = r
                }
            }
            if (mx > 0.0) {
                val rowsOut = ArrayList<DccVpRow>(profRows)
                for (r in 0 until profRows) {
                    val rv = slice.tot[r]
                    if (rv <= 0.0) continue
                    val str = rv / mx
                    val bSh = clip01(slice.buy[r] / rv)
                    val y0 = slice.bot + (r + 0.08) * slice.step
                    val y1 = slice.bot + (r + 0.92) * slice.step
                    var side = 0
                    if (settings.showLvlRows) {
                        for (lv in finalSr) {
                            if (lv.price >= y0 && lv.price <= y1) {
                                side = if (lv.isSupport) 1 else -1
                                break
                            }
                        }
                    }
                    rowsOut.add(
                        DccVpRow(
                            priceLow = y0,
                            priceHigh = y1,
                            strength = str,
                            buyShare = bSh,
                            isPoc = (r == pocIdx),
                            srSide = side
                        )
                    )
                }
                val pocPrice = slice.bot + (pocIdx + 0.5) * slice.step
                DccVolumeProfileData(
                    rows = rowsOut,
                    pocPrice = pocPrice,
                    widthBars = settings.profW.coerceIn(15, 110),
                    offsetBars = settings.profOff.coerceIn(2, 50)
                )
            } else {
                null
            }
        } else {
            null
        }

        return finalSr to vpData
    }

    private fun evalPivot(
        candles: List<Candle>,
        barIdx: Int,
        raw: Double,
        isSupport: Boolean,
        vAvg: DoubleArray,
        atrArr: DoubleArray,
        srLvls: MutableList<MutableSrLvl>,
        settings: DccSettings,
        swingLen: Int,
        profLb: Int,
        profRows: Int,
        minTick: Double
    ) {
        val pivotIdx = barIdx - swingLen
        if (pivotIdx < 0) return
        val avail = pivotIdx + 1
        val bars = max(1, min(profLb, avail))
        val oldestIdx = max(0, pivotIdx - bars + 1)
        val slice = computeProfile(candles, pivotIdx, oldestIdx, profRows, minTick)

        val validAtr = max(
            if (atrArr[pivotIdx].isNaN()) minTick else atrArr[pivotIdx],
            minTick
        )

        // profile_node
        val mx = max(slice.tot.maxOrNull() ?: 0.0, 1e-7)
        val rad = max(validAtr * settings.srchAtr, slice.step * 2.0)
        var bestP = raw
        var bestE = -1.0
        var bestS = 0.0

        for (r in 0 until profRows) {
            val p = slice.bot + (r + 0.5) * slice.step
            val d = abs(p - raw)
            if (d <= rad) {
                val rv = slice.tot[r]
                val str = rv / mx
                val lv = if (r > 0) slice.tot[r - 1] else rv
                val rv2 = if (r < profRows - 1) slice.tot[r + 1] else rv
                val nAvg = max((lv + rv2) * 0.5, mx * 0.01)
                val pk = clip01((rv / nAvg - 0.85) / 0.65)
                val bSh = if (rv > 0.0) clip01(slice.buy[r] / rv) else 0.5
                val dir = if (isSupport) bSh else 1.0 - bSh
                val prox = 1.0 - d / rad
                val e = 0.50 * str + 0.20 * pk + 0.20 * dir + 0.10 * prox
                if (e > bestE) {
                    bestE = e
                    bestP = p
                    bestS = str
                }
            }
        }
        val evid = max(bestE, 0.0)
        val nStr = bestS

        // pivot_features
        val pivotBar = candles[pivotIdx]
        val vb = if (!vAvg[pivotIdx].isNaN()) vAvg[pivotIdx] else pivotBar.volume.toDouble()
        val vr = if (vb > 0.0) pivotBar.volume.toDouble() / vb else 1.0
        val vSc = clip01((vr - 0.8) / (2.2 - 0.8))

        val pr = max(pivotBar.high - pivotBar.low, minTick)
        val wick = if (isSupport) {
            min(pivotBar.open, pivotBar.close) - pivotBar.low
        } else {
            pivotBar.high - max(pivotBar.open, pivotBar.close)
        }
        val wSc = clip01((wick / pr) / 0.50)

        var absSc = 0.0
        val radius = min(3, swingLen)
        for (offset in -radius..radius) {
            val idx = pivotIdx + offset
            if (idx !in candles.indices) continue
            val c = candles[idx]
            val cr = max(c.high - c.low, minTick)
            val v = max(c.volume.toDouble(), 0.0)
            val base = if (!vAvg[idx].isNaN()) vAvg[idx] else v
            val vRat = if (base > 0.0) v / base else 1.0
            val bRat = abs(c.close - c.open) / cr
            val small = clip01((0.40 - bRat) / 0.40)
            val av = clip01((vRat - 1.0) / (1.5 - 1.0))
            absSc = max(absSc, sqrt(small * av))
        }

        val ok = nStr >= settings.nodeMin && evid >= settings.evidMin
        val pull = if (ok) settings.pullMax * clip01(evid / 0.65) else 0.0
        val p = raw + (bestP - raw) * pull
        val sc = 100.0 * (0.50 * evid + 0.20 * absSc + 0.15 * vSc + 0.15 * wSc)

        if (ok && sc >= settings.scoreMin) {
            val tol = max(validAtr * settings.mergeAtr, minTick)
            var nearbyIdx = -1
            var bestDist = Double.POSITIVE_INFINITY
            for (k in srLvls.indices) {
                val lv = srLvls[k]
                if (lv.isSupport == isSupport) {
                    val d = abs(lv.price - p)
                    if (d <= tol && d < bestDist) {
                        bestDist = d
                        nearbyIdx = k
                    }
                }
            }
            val bornTime = pivotBar.timeEpochSec
            if (nearbyIdx >= 0) {
                val existing = srLvls[nearbyIdx]
                if (sc > existing.score) {
                    existing.price = p
                    existing.score = sc
                    existing.rawPrice = raw
                    existing.node = bestP
                    existing.evid = evid
                    existing.nStr = nStr
                    existing.bornTimeEpochSec = bornTime
                }
            } else {
                srLvls.add(
                    MutableSrLvl(
                        price = p,
                        score = sc,
                        rawPrice = raw,
                        node = bestP,
                        evid = evid,
                        nStr = nStr,
                        isSupport = isSupport,
                        bornTimeEpochSec = bornTime
                    )
                )
            }
        }
    }
}
