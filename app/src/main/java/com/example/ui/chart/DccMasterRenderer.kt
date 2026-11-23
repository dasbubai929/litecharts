package com.example.ui.chart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.indicators.DccMarkerKind
import com.example.indicators.DccMasterResult
import com.example.indicators.DccSettings
import com.example.indicators.PineEvaluationResult
import com.example.model.ChartAppearance
import com.example.ui.theme.toComposeColor
import com.example.ui.viewmodel.formatPrice
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Renders every visual component of DCC+TSM+VPSR+SQZ (v2.5.5) and
 * custom user-pasted overlay Pine Script indicators on the chart canvas.
 */
object DccMasterRenderer {

    private val dottedEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
    private val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

    private fun styleEffect(style: String): PathEffect? = when (style) {
        "Dotted" -> dottedEffect
        "Dashed" -> dashedEffect
        else -> null
    }

    /**
     * Background layers drawn behind candlesticks:
     * Session Range Boxes, Large Rolling Volume Profile, Profile-Guided S/R, and Key Levels (DO/PDH/PDL).
     */
    fun drawBackgroundLayers(
        scope: DrawScope,
        mapper: ChartCoordinateMapper,
        result: DccMasterResult,
        settings: DccSettings,
        appearance: ChartAppearance,
        decimals: Int,
        monoTypeface: Typeface
    ) {
        if (!settings.masterEnabled) return

        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = 24f
            typeface = monoTypeface
        }

        // 08 • Session Range Boxes
        if (settings.enableSessions && settings.enableRangeBoxes && result.sessionBoxes.isNotEmpty()) {
            val fillAlpha = ((100 - settings.rangeTransparency).coerceIn(0, 100) / 100f)
            val outlineAlpha = ((100 - settings.outlineTransp).coerceIn(0, 100) / 100f)
            for (box in result.sessionBoxes) {
                val xL = mapper.indexToX(box.startBarIndex.toFloat())
                val xR = mapper.indexToX(box.endBarIndex.toFloat())
                if (xR < -20f || xL > mapper.plotWidth + 20f) continue
                val topY = mapper.priceToY(box.high)
                val botY = mapper.priceToY(box.low)
                val baseCol = box.colorArgb.toComposeColor()

                scope.drawRect(
                    color = baseCol.copy(alpha = fillAlpha),
                    topLeft = Offset(xL, topY),
                    size = Size(max(xR - xL, 2f), max(botY - topY, 2f))
                )

                if (settings.showRangeOutline) {
                    val edgeCol = baseCol.copy(alpha = outlineAlpha)
                    scope.drawLine(edgeCol, Offset(xL, topY), Offset(xR, topY), 1.5f, pathEffect = dottedEffect)
                    scope.drawLine(edgeCol, Offset(xL, botY), Offset(xR, botY), 1.5f, pathEffect = dottedEffect)
                    scope.drawLine(edgeCol, Offset(xL, mapper.priceToY(box.startHigh)), Offset(xL, topY), 1.5f, pathEffect = dottedEffect)
                    scope.drawLine(edgeCol, Offset(xL, botY), Offset(xL, mapper.priceToY(box.startLow)), 1.5f, pathEffect = dottedEffect)
                    scope.drawLine(edgeCol, Offset(xR, mapper.priceToY(box.endHigh)), Offset(xR, topY), 1.5f, pathEffect = dottedEffect)
                    scope.drawLine(edgeCol, Offset(xR, botY), Offset(xR, mapper.priceToY(box.endLow)), 1.5f, pathEffect = dottedEffect)
                }

                if (settings.showRangeLabel) {
                    val midX = (xL + xR) * 0.5f
                    scope.drawIntoCanvas { canvas ->
                        labelPaint.color = baseCol.toArgb()
                        labelPaint.textAlign = Paint.Align.CENTER
                        canvas.nativeCanvas.drawText(box.name, midX, topY - 8f, labelPaint)
                    }
                }
            }
        }

        // 13 • Large Rolling Volume Profile
        val vp = result.volumeProfile
        if (settings.enableVP && vp != null && vp.rows.isNotEmpty() && mapper.candles.isNotEmpty()) {
            val lastIdx = mapper.candles.lastIndex
            val startX = mapper.indexToX((lastIdx + vp.offsetBars).toFloat())
                .coerceAtMost(mapper.plotWidth - 88f)
            val maxProfW = (vp.widthBars * mapper.barSpacingPx * 0.55f).coerceIn(44f, mapper.plotWidth * 0.28f)
            val buyCol = settings.cuBull.toComposeColor()
            val sellCol = settings.cuBear.toComposeColor()
            val pocCol = settings.cuFast.toComposeColor()

            for (row in vp.rows) {
                val yTop = mapper.priceToY(row.priceHigh)
                val yBot = mapper.priceToY(row.priceLow)
                if (yBot < 0f || yTop > mapper.plotHeight) continue
                val rowH = max(abs(yBot - yTop), 2f)
                val tw = max(2f, (maxProfW * row.strength).toFloat())
                val bw = (tw * row.buyShare).toFloat()
                val sw = tw - bw
                val fadeTransp = (76.0 - 46.0 * row.strength).coerceIn(20.0, 82.0)
                val alpha = ((100.0 - fadeTransp) / 100.0).toFloat()

                if (bw > 0.5f) {
                    scope.drawRect(
                        color = buyCol.copy(alpha = alpha),
                        topLeft = Offset(startX, yTop),
                        size = Size(bw, rowH)
                    )
                }
                if (sw > 0.5f) {
                    scope.drawRect(
                        color = sellCol.copy(alpha = alpha),
                        topLeft = Offset(startX + bw, yTop),
                        size = Size(sw, rowH)
                    )
                }
                if (settings.showPoc && row.isPoc) {
                    scope.drawRect(
                        color = pocCol.copy(alpha = 0.14f),
                        topLeft = Offset(startX, yTop),
                        size = Size(tw, rowH)
                    )
                    scope.drawRect(
                        color = pocCol,
                        topLeft = Offset(startX, yTop),
                        size = Size(tw, rowH),
                        style = Stroke(width = 2.2f)
                    )
                }
                if (settings.showLvlRows && row.srSide != 0) {
                    val lvlC = if (row.srSide > 0) buyCol else sellCol
                    scope.drawRect(
                        color = lvlC,
                        topLeft = Offset(startX, yTop),
                        size = Size(tw, rowH),
                        style = Stroke(width = 1.8f)
                    )
                }
            }

            if (settings.showPoc && vp.pocPrice != null) {
                val pocY = mapper.priceToY(vp.pocPrice)
                val leftPocX = mapper.indexToX(max(0, lastIdx - 18).toFloat())
                scope.drawLine(
                    color = pocCol.copy(alpha = 0.78f),
                    start = Offset(leftPocX, pocY),
                    end = Offset(startX, pocY),
                    strokeWidth = 2.2f
                )
                scope.drawIntoCanvas { canvas ->
                    labelPaint.color = pocCol.toArgb()
                    labelPaint.textAlign = Paint.Align.RIGHT
                    canvas.nativeCanvas.drawText(
                        "POC ${formatPrice(vp.pocPrice, decimals)}",
                        mapper.plotWidth - 6f,
                        pocY - 6f,
                        labelPaint
                    )
                }
            }
        }

        // 14 • Profile-Guided S/R Levels
        if (settings.enableSR && result.srLevels.isNotEmpty() && mapper.candles.isNotEmpty()) {
            val lastX = mapper.indexToX(mapper.candles.lastIndex.toFloat())
            val srEffect = styleEffect(settings.srLvlStyle)
            val srStroke = (settings.widSR * 1.4f).coerceIn(1.2f, 5f)
            val supCol = settings.cuBull.toComposeColor()
            val resCol = settings.cuBear.toComposeColor()
            val rawCol = settings.cuDaily.toComposeColor().copy(alpha = 0.55f)

            for (lv in result.srLevels) {
                val y = mapper.priceToY(lv.price)
                if (y < -10f || y > mapper.plotHeight + 10f) continue
                val x1 = mapper.timeToX(lv.bornTimeEpochSec).coerceAtLeast(0f)
                val alpha = when {
                    lv.score >= 82.0 -> 0.92f
                    lv.score >= 62.0 -> 0.78f
                    lv.score >= 45.0 -> 0.62f
                    else -> 0.48f
                }
                val baseC = if (lv.isSupport) supCol else resCol

                scope.drawLine(
                    color = baseC.copy(alpha = alpha),
                    start = Offset(x1, y),
                    end = Offset(lastX, y),
                    strokeWidth = srStroke,
                    pathEffect = srEffect
                )

                if (settings.showRaw) {
                    val rawY = mapper.priceToY(lv.rawPrice)
                    scope.drawCircle(color = rawCol, radius = 4f, center = Offset(x1, rawY))
                    scope.drawLine(
                        color = rawCol,
                        start = Offset(x1, rawY),
                        end = Offset(x1, y),
                        strokeWidth = 1.2f,
                        pathEffect = dottedEffect
                    )
                }

                if (settings.showEdge) {
                    scope.drawIntoCanvas { canvas ->
                        labelPaint.color = baseC.toArgb()
                        labelPaint.textAlign = Paint.Align.LEFT
                        canvas.nativeCanvas.drawText(
                            String.format(java.util.Locale.US, "%.0f", lv.score),
                            (lastX + 6f).coerceAtMost(mapper.plotWidth - 28f),
                            y + 8f,
                            labelPaint
                        )
                    }
                }
            }
        }

        // 09 • Key Levels (DO / PDH / PDL) with non-overlapping label stagger
        if (settings.enableLevels && result.keyLevels.isNotEmpty() && mapper.candles.isNotEmpty()) {
            val lastX = mapper.indexToX(mapper.candles.lastIndex.toFloat())
            val sorted = result.keyLevels.sortedBy { it.price }
            var prevY = Float.NaN
            var slot = 0

            for (kl in sorted) {
                val y = mapper.priceToY(kl.price)
                if (y < -10f || y > mapper.plotHeight + 10f) continue
                val startX = mapper.timeToX(kl.startTimeEpochSec).coerceAtLeast(0f)
                val col = kl.colorArgb.toComposeColor()
                val eff = styleEffect(kl.style)

                scope.drawLine(
                    color = col,
                    start = Offset(startX, y),
                    end = Offset(lastX, y),
                    strokeWidth = (kl.width * 1.4f).coerceIn(1.2f, 5f),
                    pathEffect = eff
                )

                if (!prevY.isNaN() && abs(y - prevY) < 28f) slot++ else slot = 0
                prevY = y

                val lblText = "${kl.name} ${formatPrice(kl.price, decimals)}"
                val textW = labelPaint.measureText(lblText)
                val lblX = (lastX + 12f + slot * (textW + 12f))
                    .coerceAtMost((mapper.plotWidth - textW - 8f).coerceAtLeast(8f))

                scope.drawRect(
                    color = appearance.backgroundArgb.toComposeColor().copy(alpha = 0.85f),
                    topLeft = Offset(lblX - 4f, y - 16f),
                    size = Size(textW + 8f, 24f)
                )
                scope.drawIntoCanvas { canvas ->
                    labelPaint.color = col.toArgb()
                    labelPaint.textAlign = Paint.Align.LEFT
                    canvas.nativeCanvas.drawText(lblText, lblX, y + 4f, labelPaint)
                }
            }
        }
    }

    /**
     * Foreground layers drawn over candlesticks:
     * EMAs, VWAP, HTF EMA 1, Pullback lines, Cross/Pullback/P3 markers,
     * and user-pasted Custom Pine Script overlay indicators.
     */
    fun drawForegroundLayers(
        scope: DrawScope,
        mapper: ChartCoordinateMapper,
        result: DccMasterResult,
        settings: DccSettings,
        customOverlays: List<PineEvaluationResult>,
        monoTypeface: Typeface
    ) {
        val visibleRange = mapper.visibleIndexRange()
        if (visibleRange.isEmpty()) return

        if (settings.masterEnabled) {
            // 01 • Fast & Slow EMA
            if (settings.enableEMA && settings.showFastEma) {
                drawSeriesLine(scope, mapper, result.emaFast, null, settings.cuFast.toComposeColor(), 2.0f)
            }
            if (settings.enableEMA && settings.showSlowEma) {
                drawSeriesLine(scope, mapper, result.emaSlow, null, settings.cuSlow.toComposeColor(), 2.0f)
            }

            // 02 • VWAP (with break support)
            if (settings.enableVwap) {
                drawSeriesLine(scope, mapper, result.vwap, result.vwapBreaks, settings.cuVwap.toComposeColor(), 2.2f)
            }

            // 03 • HTF EMA 1
            if (settings.enableHTFEMA) {
                drawSeriesLine(scope, mapper, result.htfEma1, null, settings.cuLevel1.toComposeColor(), 2.4f)
            }

            // 12 • Pullback Swing High/Low Dotted Lines
            if (settings.showPbLines && result.pullbackLines.isNotEmpty()) {
                for (ln in result.pullbackLines) {
                    val x1 = mapper.indexToX(ln.startBarIndex.toFloat())
                    val x2 = mapper.indexToX(ln.endBarIndex.toFloat())
                    if (x2 < 0f || x1 > mapper.plotWidth) continue
                    val y = mapper.priceToY(ln.price)
                    val col = if (ln.isBullish) settings.cuPbBull.toComposeColor() else settings.cuPbBear.toComposeColor()
                    scope.drawLine(
                        color = col,
                        start = Offset(x1, y),
                        end = Offset(x2, y),
                        strokeWidth = 3.0f,
                        pathEffect = dottedEffect
                    )
                }
            }

            // 11 & 12 • EMA Cross (❌), Pullback Triangles, P3 Re-Entry Triangles
            val colBull = settings.cuBull.toComposeColor()
            val colBear = settings.cuBear.toComposeColor()
            val colPbBull = settings.cuPbBull.toComposeColor()
            val colPbBear = settings.cuPbBear.toComposeColor()

            for (m in result.markers) {
                if (m.barIndex < visibleRange.first - 2 || m.barIndex > visibleRange.last + 2) continue
                val x = mapper.indexToX(m.barIndex.toFloat())
                val baseY = mapper.priceToY(m.price)

                when (m.kind) {
                    DccMarkerKind.CROSS_BULL -> drawXCross(scope, x, baseY + 18f, 7f, colBull)
                    DccMarkerKind.CROSS_BEAR -> drawXCross(scope, x, baseY - 18f, 7f, colBear)
                    DccMarkerKind.PULLBACK_BUY -> drawTriangle(scope, x, baseY + 14f, 11f, pointingUp = true, colBull)
                    DccMarkerKind.PULLBACK_SELL -> drawTriangle(scope, x, baseY - 14f, 11f, pointingUp = false, colBear)
                    DccMarkerKind.P3_BUY -> drawTriangle(scope, x, baseY + 16f, 8f, pointingUp = true, colPbBull)
                    DccMarkerKind.P3_SELL -> drawTriangle(scope, x, baseY - 16f, 8f, pointingUp = false, colPbBear)
                }
            }
        }

        // Custom User-Pasted Pine Script Overlay Indicators
        for (ov in customOverlays) {
            if (ov.error != null) continue
            for (hline in ov.hlines) {
                val y = mapper.priceToY(hline.price)
                if (y in 0f..mapper.plotHeight) {
                    scope.drawLine(
                        color = hline.colorArgb.toComposeColor().copy(alpha = 0.75f),
                        start = Offset(0f, y),
                        end = Offset(mapper.plotWidth, y),
                        strokeWidth = 1.6f,
                        pathEffect = dashedEffect
                    )
                }
            }
            for (plot in ov.plots) {
                drawSeriesLine(
                    scope = scope,
                    mapper = mapper,
                    values = plot.values,
                    breaks = null,
                    color = plot.colorArgb.toComposeColor(),
                    strokeWidth = plot.lineWidth * 1.2f
                )
            }
            val shapePaint = Paint().apply {
                isAntiAlias = true
                textSize = 21f
                typeface = monoTypeface
                textAlign = Paint.Align.CENTER
            }
            for (sh in ov.shapes) {
                if (sh.barIndex < visibleRange.first - 1 || sh.barIndex > visibleRange.last + 1) continue
                val x = mapper.indexToX(sh.barIndex.toFloat())
                val y = mapper.priceToY(sh.price)
                val c = sh.colorArgb.toComposeColor()
                drawTriangle(scope, x, if (sh.isBelowBar) y + 14f else y - 14f, 8f, sh.isBelowBar, c)
                if (sh.label.isNotBlank()) {
                    scope.drawIntoCanvas { canvas ->
                        shapePaint.color = c.toArgb()
                        canvas.nativeCanvas.drawText(sh.label, x, if (sh.isBelowBar) y + 36f else y - 26f, shapePaint)
                    }
                }
            }
        }
    }

    private fun drawSeriesLine(
        scope: DrawScope,
        mapper: ChartCoordinateMapper,
        values: List<Double?>,
        breaks: List<Boolean>?,
        color: Color,
        strokeWidth: Float
    ) {
        if (values.isEmpty()) return
        val visible = mapper.visibleIndexRange()
        if (visible.isEmpty()) return
        val start = (visible.first - 2).coerceAtLeast(0)
        val end = (visible.last + 2).coerceAtMost(values.lastIndex)

        val path = Path()
        var drawing = false
        for (i in start..end) {
            val v = values[i]
            val isBreak = breaks?.getOrNull(i) == true
            if (v == null || v.isNaN() || isBreak) {
                drawing = false
                continue
            }
            val x = mapper.indexToX(i.toFloat())
            val y = mapper.priceToY(v)
            if (!drawing) {
                path.moveTo(x, y)
                drawing = true
            } else {
                path.lineTo(x, y)
            }
        }
        scope.drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    private fun drawXCross(scope: DrawScope, cx: Float, cy: Float, r: Float, color: Color) {
        scope.drawLine(color, Offset(cx - r, cy - r), Offset(cx + r, cy + r), strokeWidth = 3.2f, cap = StrokeCap.Round)
        scope.drawLine(color, Offset(cx - r, cy + r), Offset(cx + r, cy - r), strokeWidth = 3.2f, cap = StrokeCap.Round)
    }

    private fun drawTriangle(
        scope: DrawScope,
        cx: Float,
        tipY: Float,
        size: Float,
        pointingUp: Boolean,
        color: Color
    ) {
        val tri = Path()
        if (pointingUp) {
            tri.moveTo(cx, tipY)
            tri.lineTo(cx - size, tipY + size * 1.35f)
            tri.lineTo(cx + size, tipY + size * 1.35f)
        } else {
            tri.moveTo(cx, tipY)
            tri.lineTo(cx - size, tipY - size * 1.35f)
            tri.lineTo(cx + size, tipY - size * 1.35f)
        }
        tri.close()
        scope.drawPath(tri, color = color)
    }
}
