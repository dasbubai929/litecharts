package com.example.ui.chart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.indicators.PineEvaluationResult
import com.example.model.ChartAppearance
import com.example.ui.theme.MonoPriceSmallStyle
import com.example.ui.theme.toComposeColor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Synchronized lower oscillator pane for user-pasted custom Pine Script
 * indicators declared with overlay=false (e.g., RSI, MACD, Stochastic).
 */
@Composable
fun OscillatorSubPane(
    result: PineEvaluationResult,
    candleCount: Int,
    barSpacingPx: Float,
    rightOffsetBars: Float,
    rightAxisWidthPx: Float,
    appearance: ChartAppearance,
    monoTypeface: Typeface,
    modifier: Modifier = Modifier
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(appearance.backgroundArgb.toComposeColor())
            .testTag("oscillator_pane_${result.indicatorId}")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val plotW = (size.width - rightAxisWidthPx).coerceAtLeast(1f)
            val plotH = size.height
            val padY = 12f
            val usableH = (plotH - padY * 2f).coerceAtLeast(1f)
            val lastIdx = (candleCount - 1).coerceAtLeast(0)

            fun indexToX(idx: Float): Float = plotW - (lastIdx - idx + rightOffsetBars) * barSpacingPx

            val leftIdx = floor(lastIdx + rightOffsetBars - plotW / barSpacingPx).toInt().coerceIn(0, lastIdx)
            val rightIdx = ceil(lastIdx + rightOffsetBars).toInt().coerceIn(0, lastIdx)

            var minV = Double.POSITIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            result.hlines.forEach {
                minV = min(minV, it.price)
                maxV = max(maxV, it.price)
            }
            for (p in result.plots) {
                for (i in leftIdx..rightIdx) {
                    p.values.getOrNull(i)?.let { v ->
                        minV = min(minV, v)
                        maxV = max(maxV, v)
                    }
                }
            }
            if (!minV.isFinite() || !maxV.isFinite() || minV >= maxV) {
                minV = 0.0
                maxV = 100.0
            } else {
                val pad = (maxV - minV) * 0.12
                minV -= pad
                maxV += pad
            }
            val span = (maxV - minV).coerceAtLeast(1e-8)
            fun valToY(v: Double): Float = padY + ((maxV - v) / span).toFloat() * usableH

            // Top divider & right axis hairline
            val borderCol = appearance.borderArgb.toComposeColor()
            drawLine(borderCol, Offset(0f, 0f), Offset(size.width, 0f), 1.5f)
            drawLine(borderCol, Offset(plotW, 0f), Offset(plotW, plotH), 1.5f)

            val axisPaint = Paint().apply {
                isAntiAlias = true
                textSize = 23f
                typeface = monoTypeface
                color = appearance.textSecondaryArgb.toComposeColor().toArgb()
                textAlign = Paint.Align.CENTER
            }

            // Right axis high / mid / low ticks
            listOf(maxV, (maxV + minV) * 0.5, minV).forEach { tick ->
                val y = valToY(tick)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        String.format(Locale.US, "%.1f", tick),
                        plotW + rightAxisWidthPx / 2f,
                        y + 7f,
                        axisPaint
                    )
                }
            }

            clipRect(0f, 0f, plotW, plotH) {
                // HLines (e.g., RSI 70/30, MACD 0)
                result.hlines.forEach { hl ->
                    val y = valToY(hl.price)
                    drawLine(
                        color = hl.colorArgb.toComposeColor().copy(alpha = 0.65f),
                        start = Offset(0f, y),
                        end = Offset(plotW, y),
                        strokeWidth = 1.4f,
                        pathEffect = dash
                    )
                }

                // Plots (histograms & lines)
                for (plot in result.plots) {
                    val col = plot.colorArgb.toComposeColor()
                    if (plot.isHistogram) {
                        val zeroY = valToY(0.0).coerceIn(padY, plotH - padY)
                        val barW = (barSpacingPx * 0.6f).coerceIn(2f, 20f)
                        for (i in leftIdx..rightIdx) {
                            val v = plot.values.getOrNull(i) ?: continue
                            val x = indexToX(i.toFloat())
                            val y = valToY(v)
                            val c = if (v >= 0.0) appearance.bullishCandleArgb.toComposeColor() else appearance.bearishCandleArgb.toComposeColor()
                            drawRect(
                                color = c.copy(alpha = 0.72f),
                                topLeft = Offset(x - barW / 2f, min(y, zeroY)),
                                size = Size(barW, max(abs(y - zeroY), 1.5f))
                            )
                        }
                    } else {
                        val path = Path()
                        var started = false
                        val s = (leftIdx - 1).coerceAtLeast(0)
                        val e = (rightIdx + 1).coerceAtMost(plot.values.lastIndex)
                        for (i in s..e) {
                            val v = plot.values.getOrNull(i)
                            if (v == null || v.isNaN()) {
                                started = false
                                continue
                            }
                            val x = indexToX(i.toFloat())
                            val y = valToY(v)
                            if (!started) {
                                path.moveTo(x, y)
                                started = true
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = col,
                            style = Stroke(width = plot.lineWidth * 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
        }

        // Title & latest value badge
        val latestSummary = buildString {
            append(result.title)
            result.plots.firstOrNull()?.values?.lastOrNull { it != null }?.let { v ->
                append("  ")
                append(String.format(Locale.US, "%.2f", v))
            }
        }
        Text(
            text = latestSummary,
            style = MonoPriceSmallStyle,
            color = appearance.textPrimaryArgb.toComposeColor(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 4.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(appearance.surfaceArgb.toComposeColor().copy(alpha = 0.78f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
