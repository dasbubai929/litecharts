package com.example.ui.chart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.model.ChartAnchor
import com.example.model.ChartAppearance
import com.example.model.ChartDrawing
import com.example.model.DrawingTool
import com.example.model.FibLevels
import com.example.ui.theme.SlateAmberAccent
import com.example.ui.theme.toComposeColor
import com.example.ui.viewmodel.formatPrice
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Renders all 9 interactive drawing & measurement tools synced to chart
 * timestamp/price coordinates via [ChartCoordinateMapper].
 */
object DrawingOverlayRenderer {

    private val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
    private val subtleDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

    fun drawAll(
        scope: DrawScope,
        mapper: ChartCoordinateMapper,
        drawings: List<ChartDrawing>,
        pendingAnchor: ChartAnchor?,
        appearance: ChartAppearance,
        decimals: Int,
        monoTypeface: Typeface,
        rightAxisWidthPx: Float
    ) {
        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = 27f
            typeface = monoTypeface
        }

        drawings.forEach { drawing ->
            when (drawing.tool) {
                DrawingTool.HORIZONTAL_LINE -> drawHorizontalLine(
                    scope, mapper, drawing, appearance, decimals, labelPaint, rightAxisWidthPx
                )
                DrawingTool.TRENDLINE -> drawTrendline(scope, mapper, drawing, appearance)
                DrawingTool.FIB_RETRACEMENT -> drawFibRetracement(
                    scope, mapper, drawing, appearance, decimals, labelPaint
                )
                DrawingTool.LONG_POSITION -> drawPositionTool(
                    scope, mapper, drawing, isLong = true, appearance, decimals, labelPaint
                )
                DrawingTool.SHORT_POSITION -> drawPositionTool(
                    scope, mapper, drawing, isLong = false, appearance, decimals, labelPaint
                )
                DrawingTool.ANCHORED_VOLUME_PROFILE -> drawVolumeProfile(
                    scope, mapper, startTime = drawing.startTime, endTime = null,
                    appearance = appearance, decimals = decimals, labelPaint = labelPaint
                )
                DrawingTool.FIXED_RANGE_VOLUME_PROFILE -> drawVolumeProfile(
                    scope, mapper, startTime = drawing.startTime, endTime = drawing.endTime,
                    appearance = appearance, decimals = decimals, labelPaint = labelPaint
                )
                DrawingTool.PRICE_RANGE -> drawPriceRange(
                    scope, mapper, drawing, appearance, decimals, labelPaint
                )
                DrawingTool.RECTANGLE -> drawRectangle(scope, mapper, drawing, appearance)
                else -> Unit
            }
        }

        // Draw pending first-tap anchor preview
        if (pendingAnchor != null) {
            val px = mapper.timeToX(pendingAnchor.timeEpochSec)
            val py = mapper.priceToY(pendingAnchor.price)
            val accent = appearance.primaryAccentArgb.toComposeColor()
            scope.drawCircle(
                color = accent.copy(alpha = 0.28f),
                radius = 18f,
                center = Offset(px, py)
            )
            scope.drawCircle(
                color = accent,
                radius = 7f,
                center = Offset(px, py)
            )
            scope.drawLine(
                color = accent.copy(alpha = 0.45f),
                start = Offset(0f, py),
                end = Offset(mapper.plotWidth, py),
                strokeWidth = 1.5f,
                pathEffect = subtleDash
            )
        }
    }

    private fun drawHorizontalLine(
        scope: DrawScope,
        mapper: ChartCoordinateMapper,
        drawing: ChartDrawing,
        appearance: ChartAppearance,
        decimals: Int,
        labelPaint: Paint,
        rightAxisWidthPx: Float
    ) {
        val y = mapper.priceToY(drawing.startPrice)
        if (y < -20f || y > mapper.plotHeight + 20f) return
        val accent = appearance.primaryAccentArgb.toComposeColor()

        scope.drawLine(
            color = accent,
            start = Offset(0f, y),
            end = Offset(mapper.plotWidth, y),
            strokeWidth = 2.6f,
            pathEffect = dashedEffect
        )

        // Right-axis price pill
        val tagText = formatPrice(drawing.startPrice, decimals)
        val pillH = 34f
        scope.drawRoundRect(
            color = accent,
            topLeft = Offset(mapper.plotWidth + 3f, y - pillH / 2f),
            size = Size(rightAxisWidthPx - 6f, pillH),
            cornerRadius = CornerRadius(6f, 6f)
        )
        scope.drawIntoCanvas { canvas ->
            labelPaint.color = if (appearance.isLightTheme) android.graphics.Color.WHITE else 0xFF0A1018.toInt()
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.nativeCanvas.drawText(
                tagText,
                mapper.plotWidth + rightAxisWidthPx / 2f,
                y + 9f,
                labelPaint
            )
        }
    }

    private fun drawTrendline(
        scope: DrawScope,
        mapper: ChartCoordinateMapper,
        drawing: ChartDrawing,
        appearance: ChartAppearance
    ) {
        val x1 = mapper.timeToX(drawing.startTime)
        val y1 = mapper.priceToY(drawing.startPrice)
        val x2 = mapper.timeToX(drawing.endTime)
        val y2 = mapper.priceToY(drawing.endPrice)
        val accent = appearance.primaryAccentArgb.toComposeColor()

        scope.drawLine(
            color = accent,
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = 3f
        )
        scope.drawCircle(color = accent, radius = 6f, center = Offset(x1, y1))
        scope.drawCircle(color = accent, radius = 6f, center = Offset(x2, y2))
    }

    private fun drawFibRetracement(
        scope: DrawScope,
        mapper: ChartCoordinateMapper,
        drawing: ChartDrawing,
        appearance: ChartAppearance,
        decimals: Int,
        labelPaint: Paint
    ) {
        val x1 = mapper.timeToX(drawing.startTime)
        val x2 = mapper.timeToX(drawing.endTime)
        val leftX = min(x1, x2)
        val rightX = max(x1, x2).coerceAtLeast(leftX + 120f)
        val p1 = drawing.startPrice
        val p2 = drawing.endPrice
        val diff = p1 - p2

        // Diagonal swing reference
        scope.drawLine(
            color = appearance.textSecondaryArgb.toComposeColor().copy(alpha = 0.55f),
            start = Offset(x1, mapper.priceToY(p1)),
            end = Offset(x2, mapper.priceToY(p2)),
            strokeWidth = 1.8f,
            pathEffect = subtleDash
        )

        // Golden zone fill between 0.382 and 0.618
        val y382 = mapper.priceToY(p2 + diff * 0.382)
        val y618 = mapper.priceToY(p2 + diff * 0.618)
        scope.drawRect(
            color = SlateAmberAccent.copy(alpha = 0.11f),
            topLeft = Offset(leftX, min(y382, y618)),
            size = Size(rightX - leftX, abs(y618 - y382))
        )

        val fibColors = listOf(
            appearance.textSecondaryArgb.toComposeColor(),
            Color(0xFF5EA8D8),
            Color(0xFF38B28E),
            SlateAmberAccent,
            Color(0xFFE09F5A),
            Color(0xFF7C90DB),
            appearance.textSecondaryArgb.toComposeColor()
        )

        FibLevels.forEachIndexed { idx, level ->
            val levelPrice = p2 + diff * level
            val y = mapper.priceToY(levelPrice)
            val lineColor = fibColors[idx % fibColors.size]

            scope.drawLine(
                color = lineColor.copy(alpha = 0.85f),
                start = Offset(leftX, y),
                end = Offset(rightX, y),
                strokeWidth = if (level == 0.5 || level == 0.618) 2.5f else 1.8f
            )

            scope.drawIntoCanvas { canvas ->
                labelPaint.color = lineColor.toArgb()
                labelPaint.textAlign = Paint.Align.LEFT
                val text = "${String.format(java.util.Locale.US, "%.3f", level).trimEnd('0').trimEnd('.')} (${formatPrice(levelPrice, decimals)})"
                canvas.nativeCanvas.drawText(text, leftX + 6f, y - 6f, labelPaint)
            }
        }
    }

    private fun drawPositionTool(
        scope: DrawScope,
        mapper: ChartCoordinateMapper,
        drawing: ChartDrawing,
        isLong: Boolean,
        appearance: ChartAppearance,
        decimals: Int,
        labelPaint: Paint
    ) {
        val x1 = mapper.timeToX(drawing.startTime)
        val x2 = mapper.timeToX(drawing.endTime)
        val leftX = min(x1, x2)
        val width = max(abs(x2 - x1), 130f)

        val entryY = mapper.priceToY(drawing.startPrice)
        val targetPrice = if (drawing.targetPrice != 0.0) drawing.targetPrice else drawing.endPrice
        val stopPrice = if (drawing.stopPrice != 0.0) drawing.stopPrice else {
            val delta = abs(targetPrice - drawing.startPrice) * 0.5
            if (isLong) drawing.startPrice - delta else drawing.startPrice + delta
        }
        val targetY = mapper.priceToY(targetPrice)
        val stopY = mapper.priceToY(stopPrice)

        val profitColor = appearance.bullishCandleArgb.toComposeColor()
        val lossColor = appearance.bearishCandleArgb.toComposeColor()

        // Profit zone
        scope.drawRect(
            color = profitColor.copy(alpha = 0.22f),
            topLeft = Offset(leftX, min(entryY, targetY)),
            size = Size(width, abs(targetY - entryY).coerceAtLeast(4f))
        )
        scope.drawRect(
            color = profitColor.copy(alpha = 0.75f),
            topLeft = Offset(leftX, min(entryY, targetY)),
            size = Size(width, abs(targetY - entryY).coerceAtLeast(4f)),
            style = Stroke(width = 1.6f)
        )

        // Risk / Stop zone
        scope.drawRect(
            color = lossColor.copy(alpha = 0.22f),
            topLeft = Offset(leftX, min(entryY, stopY)),
            size = Size(width, abs(stopY - entryY).coerceAtLeast(4f))
        )
        scope.drawRect(
            color = lossColor.copy(alpha = 0.75f),
            topLeft = Offset(leftX, min(entryY, stopY)),
            size = Size(width, abs(stopY - entryY).coerceAtLeast(4f)),
            style = Stroke(width = 1.6f)
        )

        // Entry divider line
        scope.drawLine(
            color = appearance.textPrimaryArgb.toComposeColor(),
            start = Offset(leftX, entryY),
            end = Offset(leftX + width, entryY),
            strokeWidth = 2.2f
        )

        val reward = abs(targetPrice - drawing.startPrice)
        val risk = abs(drawing.startPrice - stopPrice).coerceAtLeast(1e-8)
        val rr = reward / risk
        val badgeText = "${if (isLong) "LONG" else "SHORT"}  R:R ${String.format(java.util.Locale.US, "%.2f", rr)}"
        val badgeWidth = labelPaint.measureText(badgeText) + 24f

        scope.drawRoundRect(
            color = appearance.surfaceElevatedArgb.toComposeColor().copy(alpha = 0.94f),
            topLeft = Offset(leftX + 6f, entryY - 17f),
            size = Size(badgeWidth, 34f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        scope.drawIntoCanvas { canvas ->
            labelPaint.color = appearance.textPrimaryArgb.toComposeColor().toArgb()
            labelPaint.textAlign = Paint.Align.LEFT
            canvas.nativeCanvas.drawText(badgeText, leftX + 18f, entryY + 8f, labelPaint)

            labelPaint.color = profitColor.toArgb()
            canvas.nativeCanvas.drawText("TP ${formatPrice(targetPrice, decimals)}", leftX + 8f, targetY + if (isLong) -8f else 26f, labelPaint)

            labelPaint.color = lossColor.toArgb()
            canvas.nativeCanvas.drawText("SL ${formatPrice(stopPrice, decimals)}", leftX + 8f, stopY + if (isLong) 26f else -8f, labelPaint)
        }
    }

    private fun drawVolumeProfile(
        scope: DrawScope,
        mapper: ChartCoordinateMapper,
        startTime: Long,
        endTime: Long?,
        appearance: ChartAppearance,
        decimals: Int,
        labelPaint: Paint
    ) {
        val candles = mapper.candles
        if (candles.isEmpty()) return

        val tStart = if (endTime != null) min(startTime, endTime) else startTime
        val tEnd = if (endTime != null) max(startTime, endTime) else candles.last().timeEpochSec

        val slice = candles.filter { it.timeEpochSec in tStart..tEnd }
        if (slice.isEmpty()) return

        var rangeLow = Double.POSITIVE_INFINITY
        var rangeHigh = Double.NEGATIVE_INFINITY
        slice.forEach { c ->
            rangeLow = min(rangeLow, c.low)
            rangeHigh = max(rangeHigh, c.high)
        }
        if (rangeHigh <= rangeLow) return

        val binCount = 24
        val binStep = (rangeHigh - rangeLow) / binCount
        val buyVol = DoubleArray(binCount)
        val sellVol = DoubleArray(binCount)

        slice.forEach { c ->
            val vol = c.volume.coerceAtLeast(1L).toDouble()
            val startBin = ((c.low - rangeLow) / binStep).toInt().coerceIn(0, binCount - 1)
            val endBin = ((c.high - rangeLow) / binStep).toInt().coerceIn(0, binCount - 1)
            val spanned = (endBin - startBin + 1).coerceAtLeast(1)
            val perBin = vol / spanned
            val isBull = c.close >= c.open
            for (b in startBin..endBin) {
                if (isBull) buyVol[b] += perBin else sellVol[b] += perBin
            }
        }

        var maxBinVol = 0.0
        var pocBin = 0
        for (b in 0 until binCount) {
            val total = buyVol[b] + sellVol[b]
            if (total > maxBinVol) {
                maxBinVol = total
                pocBin = b
            }
        }
        if (maxBinVol <= 0.0) return

        val leftX = mapper.timeToX(tStart)
        val rightX = if (endTime != null) mapper.timeToX(tEnd) else mapper.plotWidth
        val boxWidth = max(abs(rightX - leftX), 60f)
        val maxHistogramWidth = (boxWidth * 0.45f).coerceIn(48f, mapper.plotWidth * 0.36f)

        val topY = mapper.priceToY(rangeHigh)
        val bottomY = mapper.priceToY(rangeLow)

        // Subtle range bounds shading
        scope.drawRect(
            color = appearance.primaryAccentArgb.toComposeColor().copy(alpha = 0.05f),
            topLeft = Offset(leftX, topY),
            size = Size(boxWidth, bottomY - topY)
        )
        scope.drawLine(
            color = appearance.primaryAccentArgb.toComposeColor().copy(alpha = 0.55f),
            start = Offset(leftX, topY),
            end = Offset(leftX, bottomY),
            strokeWidth = 2f,
            pathEffect = subtleDash
        )
        if (endTime != null) {
            scope.drawLine(
                color = appearance.primaryAccentArgb.toComposeColor().copy(alpha = 0.55f),
                start = Offset(leftX + boxWidth, topY),
                end = Offset(leftX + boxWidth, bottomY),
                strokeWidth = 2f,
                pathEffect = subtleDash
            )
        }

        val bullColor = appearance.bullishCandleArgb.toComposeColor().copy(alpha = 0.42f)
        val bearColor = appearance.bearishCandleArgb.toComposeColor().copy(alpha = 0.42f)

        for (b in 0 until binCount) {
            val pLow = rangeLow + b * binStep
            val pHigh = pLow + binStep
            val yTop = mapper.priceToY(pHigh)
            val yBot = mapper.priceToY(pLow)
            val rowH = max(abs(yBot - yTop) - 2f, 2f)

            val buyW = (buyVol[b] / maxBinVol).toFloat() * maxHistogramWidth
            val sellW = (sellVol[b] / maxBinVol).toFloat() * maxHistogramWidth

            if (buyW > 0.5f) {
                scope.drawRect(
                    color = bullColor,
                    topLeft = Offset(leftX, yTop + 1f),
                    size = Size(buyW, rowH)
                )
            }
            if (sellW > 0.5f) {
                scope.drawRect(
                    color = bearColor,
                    topLeft = Offset(leftX + buyW, yTop + 1f),
                    size = Size(sellW, rowH)
                )
            }
        }

        // Point of Control (POC) line
        val pocPrice = rangeLow + (pocBin + 0.5) * binStep
        val pocY = mapper.priceToY(pocPrice)
        scope.drawLine(
            color = SlateAmberAccent,
            start = Offset(leftX, pocY),
            end = Offset(leftX + boxWidth, pocY),
            strokeWidth = 2.6f
        )

        scope.drawIntoCanvas { canvas ->
            labelPaint.color = SlateAmberAccent.toArgb()
            labelPaint.textAlign = Paint.Align.LEFT
            val prefix = if (endTime == null) "AVP POC" else "FRVP POC"
            canvas.nativeCanvas.drawText(
                "$prefix ${formatPrice(pocPrice, decimals)}",
                leftX + 8f,
                pocY - 8f,
                labelPaint
            )
        }
    }

    private fun drawPriceRange(
        scope: DrawScope,
        mapper: ChartCoordinateMapper,
        drawing: ChartDrawing,
        appearance: ChartAppearance,
        decimals: Int,
        labelPaint: Paint
    ) {
        val x1 = mapper.timeToX(drawing.startTime)
        val y1 = mapper.priceToY(drawing.startPrice)
        val x2 = mapper.timeToX(drawing.endTime)
        val y2 = mapper.priceToY(drawing.endPrice)

        val delta = drawing.endPrice - drawing.startPrice
        val pct = if (drawing.startPrice != 0.0) (delta / drawing.startPrice) * 100.0 else 0.0
        val barCount = mapper.barCountBetween(drawing.startTime, drawing.endTime)
        val isPositive = delta >= 0.0
        val tone = if (isPositive) {
            appearance.bullishCandleArgb.toComposeColor()
        } else {
            appearance.bearishCandleArgb.toComposeColor()
        }

        val leftX = min(x1, x2)
        val topY = min(y1, y2)
        val width = max(abs(x2 - x1), 24f)
        val height = max(abs(y2 - y1), 24f)

        scope.drawRect(
            color = tone.copy(alpha = 0.16f),
            topLeft = Offset(leftX, topY),
            size = Size(width, height)
        )
        scope.drawRect(
            color = tone.copy(alpha = 0.8f),
            topLeft = Offset(leftX, topY),
            size = Size(width, height),
            style = Stroke(width = 2f)
        )

        // Vertical measurement arrow along center X
        val midX = leftX + width / 2f
        scope.drawLine(
            color = tone,
            start = Offset(midX, y1),
            end = Offset(midX, y2),
            strokeWidth = 2.4f
        )

        val sign = if (delta >= 0) "+" else ""
        val statsText = "$sign${formatPrice(delta, decimals)} ($sign${String.format(java.util.Locale.US, "%.2f", pct)}%) • $barCount bars"
        val pillW = labelPaint.measureText(statsText) + 26f
        val pillX = (midX - pillW / 2f).coerceIn(8f, (mapper.plotWidth - pillW - 8f).coerceAtLeast(8f))
        val pillY = if (isPositive) (topY - 44f).coerceAtLeast(8f) else (topY + height + 10f).coerceAtMost(mapper.plotHeight - 40f)

        scope.drawRoundRect(
            color = tone,
            topLeft = Offset(pillX, pillY),
            size = Size(pillW, 34f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        scope.drawIntoCanvas { canvas ->
            labelPaint.color = 0xFF0A1018.toInt()
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.nativeCanvas.drawText(statsText, pillX + pillW / 2f, pillY + 24f, labelPaint)
        }
    }

    private fun drawRectangle(
        scope: DrawScope,
        mapper: ChartCoordinateMapper,
        drawing: ChartDrawing,
        appearance: ChartAppearance
    ) {
        val x1 = mapper.timeToX(drawing.startTime)
        val y1 = mapper.priceToY(drawing.startPrice)
        val x2 = mapper.timeToX(drawing.endTime)
        val y2 = mapper.priceToY(drawing.endPrice)
        val accent = appearance.primaryAccentArgb.toComposeColor()

        val leftX = min(x1, x2)
        val topY = min(y1, y2)
        val width = max(abs(x2 - x1), 16f)
        val height = max(abs(y2 - y1), 16f)

        scope.drawRect(
            color = accent.copy(alpha = 0.14f),
            topLeft = Offset(leftX, topY),
            size = Size(width, height)
        )
        scope.drawRect(
            color = accent.copy(alpha = 0.85f),
            topLeft = Offset(leftX, topY),
            size = Size(width, height),
            style = Stroke(width = 2.2f)
        )
        scope.drawCircle(color = accent, radius = 5f, center = Offset(x1, y1))
        scope.drawCircle(color = accent, radius = 5f, center = Offset(x2, y2))
    }
}
