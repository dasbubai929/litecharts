package com.example.indicators

import com.example.model.AssetCategory
import com.example.model.Candle
import com.example.model.MarketInstrument
import com.example.model.Timeframe
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Exact Kotlin translation of DCC+TSM+VPSR+SQZ (DCC Master v2.5.5).
 * Executes all 16 sections faithfully using live chart candles + HTF candle bundles.
 */
object DccMasterEngine {

    fun timeframeCodeToOandaTimeframe(tfCode: String): Timeframe = when (tfCode.uppercase()) {
        "1", "M1" -> Timeframe.M1
        "3", "M3" -> Timeframe.M3
        "5", "M5" -> Timeframe.M5
        "15", "M15" -> Timeframe.M15
        "30", "M30" -> Timeframe.M30
        "60", "H1", "1H" -> Timeframe.H1
        "240", "H4", "4H" -> Timeframe.H4
        "D", "1D" -> Timeframe.D1
        "W", "1W" -> Timeframe.W1
        else -> Timeframe.H1
    }

    fun formatTfLabel(tfCode: String): String = when (tfCode.uppercase()) {
        "1" -> "1m"
        "3" -> "3m"
        "5" -> "5m"
        "15" -> "15m"
        "30" -> "30m"
        "60" -> "1H"
        "240" -> "4H"
        "D", "1D" -> "1D"
        "W", "1W" -> "1W"
        else -> tfCode
    }

    fun calculate(
        candles: List<Candle>,
        chartTimeframe: Timeframe,
        instrument: MarketInstrument,
        settings: DccSettings,
        htfCandles: Map<Timeframe, List<Candle>>,
        dailyCandlesForLevels: List<Candle> = emptyList()
    ): DccMasterResult {
        if (!settings.masterEnabled || candles.isEmpty()) return DccMasterResult()

        val n = candles.size
        val minTick = 10.0.pow(-instrument.decimals)

        // --- 01 • EMA / TREND ---
        val fastSrc = PineTa.sourceSeries(candles, settings.fastSource)
        val slowSrc = PineTa.sourceSeries(candles, settings.slowSource)
        val emaFastArr = PineTa.ema(fastSrc, settings.fastLen)
        val emaSlowArr = PineTa.ema(slowSrc, settings.slowLen)

        // --- 07 • TRADING SESSIONS ---
        val tokyoActive = BooleanArray(n)
        val londonActive = BooleanArray(n)
        val nyActive = BooleanArray(n)
        val sydneyActive = BooleanArray(n)

        val tkTz = TimeZone.getTimeZone("Asia/Tokyo")
        val lnTz = TimeZone.getTimeZone(if (settings.londonAutoDST) "Europe/London" else "UTC")
        val nyTz = TimeZone.getTimeZone(if (settings.nyAutoDST) "America/New_York" else "GMT-5")
        val syTz = TimeZone.getTimeZone(if (settings.sydneyAutoDST) "Australia/Sydney" else "GMT+10")

        if (settings.enableSessions) {
            val cal = Calendar.getInstance()
            for (i in 0 until n) {
                val ms = candles[i].timeEpochSec * 1000L
                if (settings.enableTokyo) tokyoActive[i] = isInSession(ms, tkTz, 9, 18, cal)
                if (settings.enableLondon) londonActive[i] = isInSession(ms, lnTz, 8, 17, cal)
                if (settings.enableNY) nyActive[i] = isInSession(ms, nyTz, 8, 17, cal)
                if (settings.enableSydney) sydneyActive[i] = isInSession(ms, syTz, 8, 17, cal)
            }
        }

        // --- 02 • VWAP (DAILY OR SESSION ANCHORED — OVERLAP SAFE) ---
        val vwapSrc = PineTa.sourceSeries(candles, settings.vwapSource)
        val (dailyVwapArr, dailyBreaks) = PineTa.dailyVwap(
            candles,
            vwapSrc,
            dailyCandlesForLevels
        )

        val sessionVwapArr = DoubleArray(n) { Double.NaN }
        val sessionBreaks = BooleanArray(n)

        var tkPV = 0.0; var tkV = 0.0; var tkStart = -1
        var lnPV = 0.0; var lnV = 0.0; var lnStart = -1
        var nyPV = 0.0; var nyV = 0.0; var nyStart = -1
        var syPV = 0.0; var syV = 0.0; var syStart = -1
        var lastSessVwap = Double.NaN
        var activeSessId = 0

        for (i in 0 until n) {
            val volSafe = max(candles[i].volume.toDouble(), 1.0)
            val pvPiece = vwapSrc[i] * volSafe

            val newTk = tokyoActive[i] && (i == 0 || !tokyoActive[i - 1])
            val newLn = londonActive[i] && (i == 0 || !londonActive[i - 1])
            val newNy = nyActive[i] && (i == 0 || !nyActive[i - 1])
            val newSy = sydneyActive[i] && (i == 0 || !sydneyActive[i - 1])

            if (tokyoActive[i]) {
                if (newTk) { tkPV = pvPiece; tkV = volSafe; tkStart = i } else { tkPV += pvPiece; tkV += volSafe }
            }
            if (londonActive[i]) {
                if (newLn) { lnPV = pvPiece; lnV = volSafe; lnStart = i } else { lnPV += pvPiece; lnV += volSafe }
            }
            if (nyActive[i]) {
                if (newNy) { nyPV = pvPiece; nyV = volSafe; nyStart = i } else { nyPV += pvPiece; nyV += volSafe }
            }
            if (sydneyActive[i]) {
                if (newSy) { syPV = pvPiece; syV = volSafe; syStart = i } else { syPV += pvPiece; syV += volSafe }
            }

            var bestStart = -1
            var chosenSess = 0
            var curSessVwap = Double.NaN

            if (sydneyActive[i] && syStart >= bestStart) {
                bestStart = syStart; chosenSess = 1
                curSessVwap = if (syV > 0) syPV / syV else candles[i].close
            }
            if (tokyoActive[i] && tkStart >= bestStart) {
                bestStart = tkStart; chosenSess = 2
                curSessVwap = if (tkV > 0) tkPV / tkV else candles[i].close
            }
            if (londonActive[i] && lnStart >= bestStart) {
                bestStart = lnStart; chosenSess = 3
                curSessVwap = if (lnV > 0) lnPV / lnV else candles[i].close
            }
            if (nyActive[i] && nyStart >= bestStart) {
                bestStart = nyStart; chosenSess = 4
                curSessVwap = if (nyV > 0) nyPV / nyV else candles[i].close
            }

            if (chosenSess != 0) lastSessVwap = curSessVwap
            val sessChanged = chosenSess != activeSessId
            activeSessId = chosenSess
            sessionVwapArr[i] = if (chosenSess != 0) curSessVwap else lastSessVwap
            sessionBreaks[i] = sessChanged || chosenSess == 0
        }

        val useDailyVwap = settings.vwapMode == "Daily"
        val activeVwapArr = if (useDailyVwap) dailyVwapArr else sessionVwapArr
        val activeVwapBreaks = if (useDailyVwap) {
            BooleanArray(n) { i -> chartTimeframe.durationSec < 86_400L && dailyBreaks[i] }
        } else {
            sessionBreaks
        }

        // --- 03 • HTF EMA LEVEL & BIAS ---
        val htfEmaTf = timeframeCodeToOandaTimeframe(settings.htfEmaTF)
        val htfRatio = if (chartTimeframe.durationSec > 0L) {
            htfEmaTf.durationSec.toDouble() / chartTimeframe.durationSec.toDouble()
        } else 1.0
        val smoothEmaLen = max(1, (settings.htfEmaLen1 * htfRatio).roundToInt())
        val closeArr = PineTa.sourceSeries(candles, "close")
        val emaLevelSmooth = PineTa.ema(closeArr, smoothEmaLen)

        val htfEmaPlotArr = if (settings.htfEmaDisplayMode == "Actual HTF EMA") {
            val htfList = htfCandles[htfEmaTf].orEmpty()
            if (htfList.isNotEmpty()) {
                val htfClose = PineTa.sourceSeries(htfList, "close")
                val htfEma = PineTa.ema(htfClose, settings.htfEmaLen1)
                alignHtfSeriesToChart(candles, htfList, htfEma)
            } else {
                emaLevelSmooth
            }
        } else {
            emaLevelSmooth
        }

        // --- 04 • ADX (HTF) ---
        val adxTf = timeframeCodeToOandaTimeframe(settings.adxTF)
        val adxSourceCandles = htfCandles[adxTf]?.takeIf { it.size >= settings.adxLen + 2 } ?: candles
        val adxDmi = PineTa.dmi(adxSourceCandles, settings.adxLen, settings.adxSmooth)
        val adxAligned = if (adxSourceCandles === candles) {
            adxDmi.adx
        } else {
            alignHtfSeriesToChart(candles, adxSourceCandles, adxDmi.adx)
        }

        // --- 05 • ATR / RISK (STOP LOSS) ---
        val atrTf = timeframeCodeToOandaTimeframe(settings.atrTF)
        val atrSourceCandles = htfCandles[atrTf]?.takeIf { it.size >= settings.atrLen + 2 } ?: candles
        val atrHtfArr = PineTa.atr(atrSourceCandles, settings.atrLen)
        val latestAtrHtf = atrHtfArr.lastOrNull { !it.isNaN() } ?: Double.NaN
        val atrSLDist = if (!latestAtrHtf.isNaN()) latestAtrHtf * settings.atrMult else Double.NaN

        val latestClose = candles.last().close
        val latestHtfEma = htfEmaPlotArr.last()
        val latestBiasLong = !latestHtfEma.isNaN() && latestClose > latestHtfEma
        val latestBiasShort = !latestHtfEma.isNaN() && latestClose < latestHtfEma
        val biasText = when {
            latestBiasLong -> "LONG"
            latestBiasShort -> "SHORT"
            else -> "FLAT"
        }

        val liveSL = when {
            atrSLDist.isNaN() -> null
            latestBiasLong -> latestClose - atrSLDist
            latestBiasShort -> latestClose + atrSLDist
            else -> null
        }

        val isForex = instrument.category == AssetCategory.FOREX
        val isJpyQuote = instrument.id.endsWith("_JPY")
        val pipSize = if (isForex) (if (isJpyQuote) 0.01 else 0.0001) else Double.NaN
        val slDistStr = when {
            atrSLDist.isNaN() -> "--"
            isForex -> String.format(Locale.US, "%.1f pips", atrSLDist / pipSize)
            else -> String.format(Locale.US, "%.${instrument.decimals}f pts", atrSLDist)
        }

        // --- 15 • SQUEEZE INDEX ---
        val psiArr = computeSqueezeIndex(closeArr, settings.sqzConv, settings.sqzLen)
        val latestPsi = psiArr.last()
        val prevPsi = if (n >= 2) psiArr[n - 2] else latestPsi
        val sqzState = when {
            latestPsi.isNaN() -> "IDLE"
            latestPsi >= settings.sqzThreshold -> "COMP"
            !prevPsi.isNaN() && latestPsi < prevPsi -> "REL"
            else -> "IDLE"
        }

        // --- 10 • TREND STRENGTH MATRIX (TSM — HTF ONLY) ---
        val useClosed = settings.tsmConfirmMode == "Last Closed HTF"
        val tsmRow1 = computeTsmForTimeframe(
            tfCode = settings.tsmTf1,
            candles = htfCandles[timeframeCodeToOandaTimeframe(settings.tsmTf1)].orEmpty().ifEmpty { candles },
            settings = settings,
            useClosed = useClosed
        )
        val tsmRow2 = computeTsmForTimeframe(
            tfCode = settings.tsmTf2,
            candles = htfCandles[timeframeCodeToOandaTimeframe(settings.tsmTf2)].orEmpty().ifEmpty { candles },
            settings = settings,
            useClosed = useClosed
        )
        val tsmRow3 = computeTsmForTimeframe(
            tfCode = settings.tsmTf3,
            candles = htfCandles[timeframeCodeToOandaTimeframe(settings.tsmTf3)].orEmpty().ifEmpty { candles },
            settings = settings,
            useClosed = useClosed
        )
        val tsmMtfScore = (tsmRow1.composite * 0.25 + tsmRow2.composite * 0.20 + tsmRow3.composite * 0.15) / 0.60
        val tsmScorePct = tsmMtfScore * 100.0
        val tsmAllBull = tsmRow1.composite > 0.0 && tsmRow2.composite > 0.0 && tsmRow3.composite > 0.0
        val tsmAllBear = tsmRow1.composite < 0.0 && tsmRow2.composite < 0.0 && tsmRow3.composite < 0.0

        val tsmFlag = if (abs(tsmScorePct) >= settings.tsmMinStrength) " ★" else ""
        val tsmStripText = buildString {
            append(tsmRow1.tfLabel).append(tfArrow(tsmRow1.composite)).append(' ')
            append(tsmRow2.tfLabel).append(tfArrow(tsmRow2.composite)).append(' ')
            append(tsmRow3.tfLabel).append(tfArrow(tsmRow3.composite))
            append(" · ")
            append(String.format(Locale.US, "%.0f%%", tsmScorePct))
            append(tsmFlag)
        }

        // --- 11 & 12 • EMA CROSSOVER MARKERS (❌) + PULLBACK & P3 STATE MACHINE ---
        val markers = mutableListOf<DccMarker>()
        val pullbackLines = mutableListOf<DccPullbackLine>()

        var trendDir = 0
        var validCrossDir = 0
        var pbStage = 0
        var crossBarIdx = -1
        var pbTouchBar = -1
        var pbSwingExtreme = Double.NaN
        var pbExtremeBar = -1
        var activePbStartBar = -1
        var activePbPrice = Double.NaN
        var activePbBull = true
        var hasActivePbLine = false

        var p3State = 0
        var p3Dir = 0
        var p3TouchBar = -1
        var p3BreakLevel = Double.NaN
        var lastP3SignalBar = -9999

        for (i in 1 until n) {
            val c = candles[i]
            val fNow = emaFastArr[i]; val fPrev = emaFastArr[i - 1]
            val sNow = emaSlowArr[i]; val sPrev = emaSlowArr[i - 1]
            if (fNow.isNaN() || fPrev.isNaN() || sNow.isNaN() || sPrev.isNaN()) continue

            val crossBull = fPrev <= sPrev && fNow > sNow
            val crossBear = fPrev >= sPrev && fNow < sNow

            val vwapNow = activeVwapArr[i]
            val vwapOkBull = !vwapNow.isNaN() && c.close > vwapNow
            val vwapOkBear = !vwapNow.isNaN() && c.close < vwapNow
            val htfNow = htfEmaPlotArr[i]
            val barBiasLong = !htfNow.isNaN() && c.close > htfNow
            val barBiasShort = !htfNow.isNaN() && c.close < htfNow
            val adxNow = adxAligned[i]
            val adxBaseOk = !adxNow.isNaN() && adxNow >= settings.sigMinAdx

            val crossConflBull = (!settings.crossUseVwap || vwapOkBull) &&
                (!settings.crossUseHtfEma || barBiasLong) &&
                (!settings.crossUseAdx || adxBaseOk) &&
                (!settings.crossUseTsm || tsmScorePct >= settings.sigMinTsmScore) &&
                (!settings.crossUseTsmAllAlign || tsmAllBull)

            val crossConflBear = (!settings.crossUseVwap || vwapOkBear) &&
                (!settings.crossUseHtfEma || barBiasShort) &&
                (!settings.crossUseAdx || adxBaseOk) &&
                (!settings.crossUseTsm || tsmScorePct <= -settings.sigMinTsmScore) &&
                (!settings.crossUseTsmAllAlign || tsmAllBear)

            val crossQualBull = crossBull && crossConflBull
            val crossQualBear = crossBear && crossConflBear

            if (settings.showCrossMarkers && crossQualBull) {
                markers.add(DccMarker(i, c.timeEpochSec, c.low, DccMarkerKind.CROSS_BULL))
            }
            if (settings.showCrossMarkers && crossQualBear) {
                markers.add(DccMarker(i, c.timeEpochSec, c.high, DccMarkerKind.CROSS_BEAR))
            }

            val entryConflBull = (!settings.sigUseVwap || vwapOkBull) &&
                (!settings.sigUseHtfEma || barBiasLong) &&
                (!settings.sigUseAdx || adxBaseOk) &&
                (!settings.sigUseTsm || tsmScorePct >= settings.sigMinTsmScore) &&
                (!settings.sigTsmAllAligned || tsmAllBull)

            val entryConflBear = (!settings.sigUseVwap || vwapOkBear) &&
                (!settings.sigUseHtfEma || barBiasShort) &&
                (!settings.sigUseAdx || adxBaseOk) &&
                (!settings.sigUseTsm || tsmScorePct <= -settings.sigMinTsmScore) &&
                (!settings.sigTsmAllAligned || tsmAllBear)

            val targetEmaVal = if (settings.pbTargetEma == "Fast EMA") fNow else sNow
            val bullEmaTouch = if (settings.pbTouchMode == "Wick or Close Touch") {
                c.low <= targetEmaVal && c.close >= sNow
            } else {
                c.close <= targetEmaVal && c.close >= sNow
            }
            val bearEmaTouch = if (settings.pbTouchMode == "Wick or Close Touch") {
                c.high >= targetEmaVal && c.close <= sNow
            } else {
                c.close >= targetEmaVal && c.close <= sNow
            }

            if (crossBull) {
                if (hasActivePbLine && settings.showPbLines && settings.showHistPbLines) {
                    pullbackLines.add(DccPullbackLine(activePbStartBar, i, activePbPrice, activePbBull, false))
                }
                hasActivePbLine = false
                trendDir = 1
                validCrossDir = if (crossQualBull) 1 else 0
                pbStage = 1
                crossBarIdx = i
                pbTouchBar = -1
                pbSwingExtreme = c.high
                pbExtremeBar = i
            } else if (crossBear) {
                if (hasActivePbLine && settings.showPbLines && settings.showHistPbLines) {
                    pullbackLines.add(DccPullbackLine(activePbStartBar, i, activePbPrice, activePbBull, false))
                }
                hasActivePbLine = false
                trendDir = -1
                validCrossDir = if (crossQualBear) -1 else 0
                pbStage = 1
                crossBarIdx = i
                pbTouchBar = -1
                pbSwingExtreme = c.low
                pbExtremeBar = i
            }

            var buyPbSignal = false
            var sellPbSignal = false

            if (settings.enablePullbackEntry && trendDir != 0) {
                if (pbStage == 1 && i > crossBarIdx) {
                    if (trendDir == 1) {
                        if (c.high >= pbSwingExtreme) {
                            pbSwingExtreme = c.high
                            pbExtremeBar = i
                        }
                        if (bullEmaTouch) {
                            pbStage = 2
                            pbTouchBar = i
                            if (settings.showPbLines && (!settings.tiePbToValidCross || validCrossDir == 1)) {
                                hasActivePbLine = true
                                activePbStartBar = pbExtremeBar
                                activePbPrice = pbSwingExtreme
                                activePbBull = true
                            }
                        }
                    } else if (trendDir == -1) {
                        if (c.low <= pbSwingExtreme) {
                            pbSwingExtreme = c.low
                            pbExtremeBar = i
                        }
                        if (bearEmaTouch) {
                            pbStage = 2
                            pbTouchBar = i
                            if (settings.showPbLines && (!settings.tiePbToValidCross || validCrossDir == -1)) {
                                hasActivePbLine = true
                                activePbStartBar = pbExtremeBar
                                activePbPrice = pbSwingExtreme
                                activePbBull = false
                            }
                        }
                    }
                } else if (pbStage == 2 && i > pbTouchBar) {
                    val bullBreakHit = if (settings.pbBreakTrigger == "Candle Close") {
                        c.close > pbSwingExtreme
                    } else {
                        c.high > pbSwingExtreme
                    }
                    val bearBreakHit = if (settings.pbBreakTrigger == "Candle Close") {
                        c.close < pbSwingExtreme
                    } else {
                        c.low < pbSwingExtreme
                    }
                    val pbCrossAllowedBull = !settings.tiePbToValidCross || validCrossDir == 1
                    val pbCrossAllowedBear = !settings.tiePbToValidCross || validCrossDir == -1

                    if (trendDir == 1 && bullBreakHit) {
                        if (pbCrossAllowedBull && entryConflBull) {
                            buyPbSignal = true
                            markers.add(DccMarker(i, c.timeEpochSec, c.low, DccMarkerKind.PULLBACK_BUY))
                        }
                        pbStage = 3
                        if (hasActivePbLine && settings.showPbLines && settings.showHistPbLines) {
                            pullbackLines.add(DccPullbackLine(activePbStartBar, i, activePbPrice, activePbBull, false))
                        }
                        hasActivePbLine = false
                    } else if (trendDir == -1 && bearBreakHit) {
                        if (pbCrossAllowedBear && entryConflBear) {
                            sellPbSignal = true
                            markers.add(DccMarker(i, c.timeEpochSec, c.high, DccMarkerKind.PULLBACK_SELL))
                        }
                        pbStage = 3
                        if (hasActivePbLine && settings.showPbLines && settings.showHistPbLines) {
                            pullbackLines.add(DccPullbackLine(activePbStartBar, i, activePbPrice, activePbBull, false))
                        }
                        hasActivePbLine = false
                    }
                }
            }

            // P3 Re-Entry Engine
            if (crossBull || crossBear) {
                p3State = 0
                p3Dir = 0
            }

            if (settings.enableP3ReEntry && !buyPbSignal && !sellPbSignal) {
                val trendStackBull = fNow > sNow && c.close > sNow
                val trendStackBear = fNow < sNow && c.close < sNow
                val distOk = (i - lastP3SignalBar) >= settings.p3MinDistBars
                val p3CrossAllowedBull = !settings.tieP3ToValidCross || validCrossDir == 1
                val p3CrossAllowedBear = !settings.tieP3ToValidCross || validCrossDir == -1

                if (p3State == 1 && i > p3TouchBar) {
                    val barsSinceP3 = i - p3TouchBar
                    val p3BullBreak = if (settings.pbBreakTrigger == "Candle Close") {
                        c.close > p3BreakLevel
                    } else {
                        c.high > p3BreakLevel
                    }
                    val p3BearBreak = if (settings.pbBreakTrigger == "Candle Close") {
                        c.close < p3BreakLevel
                    } else {
                        c.low < p3BreakLevel
                    }

                    if (p3Dir == 1 && p3BullBreak && barsSinceP3 <= settings.p3BreakoutBars && distOk && p3CrossAllowedBull && entryConflBull) {
                        markers.add(DccMarker(i, c.timeEpochSec, c.low, DccMarkerKind.P3_BUY))
                        lastP3SignalBar = i
                        p3State = 0
                    } else if (p3Dir == -1 && p3BearBreak && barsSinceP3 <= settings.p3BreakoutBars && distOk && p3CrossAllowedBear && entryConflBear) {
                        markers.add(DccMarker(i, c.timeEpochSec, c.high, DccMarkerKind.P3_SELL))
                        lastP3SignalBar = i
                        p3State = 0
                    } else if (barsSinceP3 >= settings.p3BreakoutBars) {
                        p3State = 0
                    }
                }

                if (p3State == 0 && (pbStage == 0 || pbStage == 3) && distOk) {
                    if (trendStackBull && bullEmaTouch && p3CrossAllowedBull) {
                        p3State = 1
                        p3Dir = 1
                        p3TouchBar = i
                        p3BreakLevel = c.high
                    } else if (trendStackBear && bearEmaTouch && p3CrossAllowedBear) {
                        p3State = 1
                        p3Dir = -1
                        p3TouchBar = i
                        p3BreakLevel = c.low
                    }
                }
            }
        }

        if (hasActivePbLine && settings.showPbLines) {
            pullbackLines.add(
                DccPullbackLine(
                    startBarIndex = activePbStartBar,
                    endBarIndex = n,
                    price = activePbPrice,
                    isBullish = activePbBull,
                    isActive = true
                )
            )
        }

        // --- 08 • SESSION RANGE BOXES ---
        val sessionBoxes = mutableListOf<DccSessionBox>()
        if (settings.enableSessions && settings.enableRangeBoxes) {
            sessionBoxes += buildSessionBoxes(candles, tokyoActive, settings.tokyoName, settings.cuTokyo, settings.sessHistLimit)
            sessionBoxes += buildSessionBoxes(candles, londonActive, settings.londonName, settings.cuLondon, settings.sessHistLimit)
            sessionBoxes += buildSessionBoxes(candles, nyActive, settings.nyName, settings.cuNY, settings.sessHistLimit)
            sessionBoxes += buildSessionBoxes(candles, sydneyActive, settings.sydneyName, settings.cuSydney, settings.sessHistLimit)
        }

        // --- 09 • KEY LEVELS (DO / PDH / PDL) ---
        val keyLevels = mutableListOf<DccKeyLevel>()
        if (settings.enableLevels) {
            val dailyCandles = dailyCandlesForLevels
                .ifEmpty { htfCandles[Timeframe.D1].orEmpty() }
                .sortedBy { it.timeEpochSec }

            // Select the daily candle active at the last chart candle.
            // This includes today's incomplete D1 candle when available.
            val chartLastTime = candles.last().timeEpochSec
            val currentDayIndex = dailyCandles.indexOfLast {
                it.timeEpochSec <= chartLastTime
            }

            if (currentDayIndex > 0) {
                val curDay = dailyCandles[currentDayIndex]
                val prevDay = dailyCandles[currentDayIndex - 1]

                if (settings.showDailyOpen) {
                    keyLevels.add(
                        DccKeyLevel(
                            name = "DO",
                            price = curDay.open,
                            startTimeEpochSec = curDay.timeEpochSec,
                            colorArgb = settings.cuDaily,
                            style = settings.styDO,
                            width = settings.widDO
                        )
                    )
                }
                if (settings.showPDLvl) {
                    keyLevels.add(
                        DccKeyLevel(
                            name = "PDH",
                            price = prevDay.high,
                            startTimeEpochSec = curDay.timeEpochSec,
                            colorArgb = settings.cuPD,
                            style = settings.styPDLvl,
                            width = settings.widPDLvl
                        )
                    )
                    keyLevels.add(
                        DccKeyLevel(
                            name = "PDL",
                            price = prevDay.low,
                            startTimeEpochSec = curDay.timeEpochSec,
                            colorArgb = settings.cuPD,
                            style = settings.styPDLvl,
                            width = settings.widPDLvl
                        )
                    )
                }
            }
        }

        // --- 13 & 14 • VOLUME PROFILE & PROFILE-GUIDED S/R ---
        val (srLevels, volumeProfile) = DccVpSrCalculator.calculate(candles, settings, minTick)

        val tableData = DccTableData(
            biasText = biasText,
            biasLong = latestBiasLong,
            biasShort = latestBiasShort,
            adxValue = adxAligned.lastOrNull { !it.isNaN() } ?: Double.NaN,
            liveSL = liveSL,
            slDistStr = slDistStr,
            sqzPsi = latestPsi,
            sqzState = sqzState,
            tsmStripText = tsmStripText,
            tsmMtfScore = tsmMtfScore,
            tsmScorePct = tsmScorePct,
            tsmRows = listOf(tsmRow1, tsmRow2, tsmRow3)
        )

        return DccMasterResult(
            emaFast = emaFastArr.map { if (it.isNaN()) null else it },
            emaSlow = emaSlowArr.map { if (it.isNaN()) null else it },
            vwap = activeVwapArr.map { if (it.isNaN()) null else it },
            vwapBreaks = activeVwapBreaks.toList(),
            htfEma1 = htfEmaPlotArr.map { if (it.isNaN()) null else it },
            markers = markers,
            pullbackLines = pullbackLines,
            sessionBoxes = sessionBoxes,
            keyLevels = keyLevels,
            srLevels = srLevels,
            volumeProfile = volumeProfile,
            tableData = tableData
        )
    }

    private fun isInSession(
        epochMs: Long,
        tz: TimeZone,
        startHour: Int,
        endHour: Int,
        cal: Calendar
    ): Boolean {
        cal.timeZone = tz
        cal.timeInMillis = epochMs
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in startHour until endHour
    }

    private fun buildSessionBoxes(
        candles: List<Candle>,
        activeFlags: BooleanArray,
        name: String,
        colorArgb: Long,
        limit: Int
    ): List<DccSessionBox> {
        val boxes = mutableListOf<DccSessionBox>()
        var inSess = false
        var startBar = 0
        var hi = Double.NEGATIVE_INFINITY
        var lo = Double.POSITIVE_INFINITY
        var startHi = 0.0
        var startLo = 0.0

        for (i in candles.indices) {
            val active = activeFlags[i]
            if (active && !inSess) {
                inSess = true
                startBar = i
                hi = candles[i].high
                lo = candles[i].low
                startHi = hi
                startLo = lo
            } else if (active && inSess) {
                hi = max(hi, candles[i].high)
                lo = min(lo, candles[i].low)
            } else if (!active && inSess) {
                val endBar = (i - 1).coerceAtLeast(startBar)
                boxes.add(
                    DccSessionBox(
                        name = name,
                        startBarIndex = startBar,
                        endBarIndex = endBar,
                        high = hi,
                        low = lo,
                        startHigh = startHi,
                        startLow = startLo,
                        endHigh = candles[endBar].high,
                        endLow = candles[endBar].low,
                        colorArgb = colorArgb
                    )
                )
                inSess = false
            }
        }
        if (inSess) {
            val endBar = candles.lastIndex
            boxes.add(
                DccSessionBox(
                    name = name,
                    startBarIndex = startBar,
                    endBarIndex = endBar,
                    high = hi,
                    low = lo,
                    startHigh = startHi,
                    startLow = startLo,
                    endHigh = candles[endBar].high,
                    endLow = candles[endBar].low,
                    colorArgb = colorArgb
                )
            )
        }
        return if (boxes.size > limit) boxes.takeLast(limit) else boxes
    }

    private fun alignHtfSeriesToChart(
        chartCandles: List<Candle>,
        htfCandles: List<Candle>,
        htfValues: DoubleArray
    ): DoubleArray {
        val n = chartCandles.size
        val out = DoubleArray(n) { Double.NaN }
        if (htfCandles.isEmpty()) return out
        var j = 0
        for (i in 0 until n) {
            val t = chartCandles[i].timeEpochSec
            while (j + 1 < htfCandles.size && htfCandles[j + 1].timeEpochSec <= t) {
                j++
            }
            if (htfCandles[j].timeEpochSec <= t) {
                out[i] = htfValues[j]
            }
        }
        return out
    }

    private fun computeSqueezeIndex(src: DoubleArray, conv: Int, len: Int): DoubleArray {
        val n = src.size
        val c = conv.coerceAtLeast(1).toDouble()
        var maxSqz = 0.0
        var minSqz = 0.0
        val diff = DoubleArray(n) { Double.NaN }
        val barIdx = DoubleArray(n) { it.toDouble() }

        for (i in 0 until n) {
            val s = src[i]
            if (i == 0) {
                maxSqz = s
                minSqz = s
            } else {
                maxSqz = max(s, maxSqz - (maxSqz - s) / c)
                minSqz = min(s, minSqz + (s - minSqz) / c)
            }
            val spread = maxSqz - minSqz
            diff[i] = if (spread > 1e-12) ln(spread) else ln(1e-12)
        }
        val corr = PineTa.correlation(diff, barIdx, len)
        return DoubleArray(n) { i ->
            if (corr[i].isNaN()) Double.NaN else -50.0 * corr[i] + 50.0
        }
    }

    private fun computeTsmForTimeframe(
        tfCode: String,
        candles: List<Candle>,
        settings: DccSettings,
        useClosed: Boolean
    ): TsmRowData {
        val label = formatTfLabel(tfCode)
        if (candles.size < 30) {
            return TsmRowData(label, 50.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        val close = PineTa.sourceSeries(candles, "close")
        val rsiArr = PineTa.rsi(close, settings.tsmRsiLength)
        val macdRes = PineTa.macd(close, settings.tsmMacdFast, settings.tsmMacdSlow, settings.tsmMacdSignal)
        val macdNormArr = PineTa.stdev(macdRes.macdLine, min(100, candles.size / 2).coerceAtLeast(10))
        val dmiRes = PineTa.dmi(candles, settings.tsmAdxLength, settings.tsmAdxLength)
        val maArr = PineTa.sma(close, settings.tsmMaLength)

        val idx = (if (useClosed && candles.size >= 2) candles.lastIndex - 1 else candles.lastIndex).coerceAtLeast(0)

        val rsi = rsiArr[idx].takeIf { !it.isNaN() } ?: 50.0
        val rsiScore = (rsi - 50.0) / 50.0

        val macdVal = macdRes.macdLine[idx].takeIf { !it.isNaN() } ?: 0.0
        val macdStd = macdNormArr[idx].takeIf { !it.isNaN() && it > 0.0 } ?: 0.0
        val macdScore = if (macdStd != 0.0) max(-1.0, min(1.0, macdVal / (macdStd * 2.0))) else 0.0

        val adx = dmiRes.adx[idx].takeIf { !it.isNaN() } ?: 0.0
        val pDi = dmiRes.plusDi[idx].takeIf { !it.isNaN() } ?: 0.0
        val mDi = dmiRes.minusDi[idx].takeIf { !it.isNaN() } ?: 0.0
        val adxStrength = min(adx / 50.0, 1.0)
        val adxDir = if (pDi > mDi) 1.0 else -1.0
        val adxScore = adxStrength * adxDir

        val ma = maArr[idx].takeIf { !it.isNaN() && it > 0.0 } ?: close[idx]
        val maDir = if (close[idx] > ma) 1.0 else -1.0
        val maStrength = abs(close[idx] - ma) / ma * 10.0
        val maScore = max(-1.0, min(1.0, maDir * min(maStrength, 1.0)))

        val composite = rsiScore * 0.25 + macdScore * 0.25 + adxScore * 0.25 + maScore * 0.25
        return TsmRowData(
            tfLabel = label,
            rsi = rsi,
            rsiScore = rsiScore,
            macdScore = macdScore,
            adx = adx,
            adxScore = adxScore,
            maScore = maScore,
            composite = composite
        )
    }

    private fun tfArrow(score: Double): String = when {
        score > 0.2 -> "▲"
        score < -0.2 -> "▼"
        else -> "•"
    }
}
