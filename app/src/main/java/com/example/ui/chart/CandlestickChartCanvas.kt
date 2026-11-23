package com.example.ui.chart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.indicators.DccMasterResult
import com.example.indicators.DccSettings
import com.example.indicators.PineEvaluationResult
import com.example.model.Candle
import com.example.model.ChartAnchor
import com.example.model.ChartAppearance
import com.example.model.ChartDrawing
import com.example.model.DrawingTool
import com.example.model.LiveQuote
import com.example.model.MarketInstrument
import com.example.model.Timeframe
import com.example.ui.theme.MonoPriceSmallStyle
import com.example.ui.theme.toComposeColor
import com.example.ui.viewmodel.formatPrice
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun CandlestickChartCanvas(
    instrument: MarketInstrument,
    timeframe: Timeframe,
    candles: List<Candle>,
    dccResult: DccMasterResult,
    dccSettings: DccSettings,
    customResults: List<PineEvaluationResult>,
    appearance: ChartAppearance,
    liveQuote: LiveQuote?,
    drawings: List<ChartDrawing>,
    activeTool: DrawingTool,
    pendingAnchor: ChartAnchor?,
    crosshairBarIndex: Int?,
    viewportResetSignal: Int,
    onTapChartForDrawing: (timeEpochSec: Long, price: Double) -> Unit,
    onCrosshairChanged: (Int?) -> Unit,
    onCancelDrawing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val monoTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
    }

    val defaultBarSpacingPx = with(density) { 9.dp.toPx() }
    val minBarSpacingPx = with(density) { 3.5.dp.toPx() }
    val maxBarSpacingPx = with(density) { 36.dp.toPx() }
    val rightAxisWidthPx = with(density) { 74.dp.toPx() }
    val bottomAxisHeightPx = with(density) { 26.dp.toPx() }
    val plotTopPadPx = with(density) { 24.dp.toPx() }
    val plotBotPadPx = with(density) { 16.dp.toPx() }

    var isAutoScale by remember { mutableStateOf(false) }
    var userPriceCenter by remember { mutableDoubleStateOf(Double.NaN) }
    var userPriceSpan by remember { mutableDoubleStateOf(Double.NaN) }
    var barSpacingPx by remember { mutableFloatStateOf(defaultBarSpacingPx) }
    var rightOffsetBars by remember { mutableFloatStateOf(14f) }

    LaunchedEffect(viewportResetSignal, instrument.id, timeframe) {
        barSpacingPx = defaultBarSpacingPx
        rightOffsetBars = if (dccSettings.masterEnabled && dccSettings.enableVP) 16f else 8f
        userPriceCenter = Double.NaN
        userPriceSpan = Double.NaN
        isAutoScale = false
    }

    val customOverlays = remember(customResults) {
        customResults.filter { it.isOverlay && it.error == null }
    }
    val customOscillators = remember(customResults) {
        customResults.filter { !it.isOverlay && it.error == null && (it.plots.isNotEmpty() || it.hlines.isNotEmpty()) }
    }

    val latestMapperHolder = remember { arrayOfNulls<ChartCoordinateMapper>(1) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appearance.backgroundArgb.toComposeColor())
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("candlestick_chart_canvas")
                    .pointerInput(candles, activeTool, timeframe, isAutoScale) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            if (candles.isEmpty()) return@detectTransformGestures
                            val plotW = (size.width - rightAxisWidthPx).coerceAtLeast(1f)
                            val plotH = (size.height - bottomAxisHeightPx).coerceAtLeast(1f)

                            if (centroid.x >= plotW) {
                                // Touch is on the right price scale: stretch or compress candle height!
                                if (pan.y != 0f) {
                                    val currentSpan = if (!userPriceSpan.isNaN() && userPriceSpan > 0.0) userPriceSpan else 0.01
                                    val factor = 1.0 + (pan.y / 200f).toDouble()
                                    userPriceSpan = (currentSpan * factor).coerceIn(1e-7, 1e8)
                                    isAutoScale = false
                                }
                            } else if (centroid.y >= plotH) {
                                // Touch is on the bottom time scale: stretch or compress bar spacing!
                                if (pan.x != 0f) {
                                    val factor = 1f + (pan.x / 180f)
                                    barSpacingPx = (barSpacingPx * factor).coerceIn(minBarSpacingPx, maxBarSpacingPx)
                                }
                            } else {
                                // Touch is on the main chart area: free 2D pan and zoom!
                                if (zoom != 1f) {
                                    barSpacingPx = (barSpacingPx * zoom).coerceIn(minBarSpacingPx, maxBarSpacingPx)
                                }
                                if (pan.x != 0f) {
                                    val deltaBars = pan.x / barSpacingPx
                                    val minRightOffset = -(candles.size - 5).coerceAtLeast(0).toFloat()
                                    val maxRightOffset = 70f
                                    rightOffsetBars = (rightOffsetBars - deltaBars).coerceIn(minRightOffset, maxRightOffset)
                                }
                                if (pan.y != 0f) {
                                    val currentSpan = if (!userPriceSpan.isNaN() && userPriceSpan > 0.0) userPriceSpan else 0.01
                                    val usableHeight = (plotH - plotTopPadPx - plotBotPadPx).coerceAtLeast(1f)
                                    val deltaPrice = (pan.y / usableHeight).toDouble() * currentSpan
                                    userPriceCenter += deltaPrice
                                    isAutoScale = false
                                }
                            }
                        }
                    }
                    .pointerInput(candles, activeTool, pendingAnchor, timeframe, isAutoScale) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val plotW = (size.width - rightAxisWidthPx).coerceAtLeast(1f)
                                val plotH = (size.height - bottomAxisHeightPx).coerceAtLeast(1f)
                                if (offset.x >= plotW) {
                                    // Double tap on price scale resets to auto fit
                                    isAutoScale = true
                                } else if (offset.y >= plotH) {
                                    // Double tap on time scale resets horizontal zoom
                                    barSpacingPx = defaultBarSpacingPx
                                } else {
                                    // Double tap on chart resets all
                                    isAutoScale = true
                                    barSpacingPx = defaultBarSpacingPx
                                    rightOffsetBars = if (dccSettings.masterEnabled && dccSettings.enableVP) 16f else 8f
                                }
                            },
                            onTap = { offset ->
                                val plotW = (size.width - rightAxisWidthPx).coerceAtLeast(1f)
                                val plotH = (size.height - bottomAxisHeightPx).coerceAtLeast(1f)
                                // Tap on the [A] auto badge at bottom of right axis
                                if (offset.x >= plotW && offset.y in (plotH - 36f)..plotH) {
                                    isAutoScale = !isAutoScale
                                    return@detectTapGestures
                                }

                                val mapper = latestMapperHolder[0] ?: return@detectTapGestures
                                if (offset.x > mapper.plotWidth || offset.y > mapper.plotHeight) return@detectTapGestures
                                if (activeTool != DrawingTool.CURSOR) {
                                    val t = mapper.xToTime(offset.x)
                                    val p = mapper.yToPrice(offset.y)
                                    onTapChartForDrawing(t, p)
                                } else {
                                    onCrosshairChanged(null)
                                }
                            },
                            onLongPress = { offset ->
                                val mapper = latestMapperHolder[0] ?: return@detectTapGestures
                                if (offset.x <= mapper.plotWidth && offset.y <= mapper.plotHeight) {
                                    onCrosshairChanged(mapper.nearestCandleIndexForX(offset.x))
                                }
                            }
                        )
                    }
            ) {
                val plotWidth = (size.width - rightAxisWidthPx).coerceAtLeast(1f)
                val plotHeight = (size.height - bottomAxisHeightPx).coerceAtLeast(1f)

                val autoBounds = ChartCoordinateMapper.computeAutoBounds(
                    candles = candles,
                    dccResult = dccResult,
                    includeDccBounds = dccSettings.masterEnabled && (dccSettings.enableEMA || dccSettings.enableVwap || dccSettings.enableHTFEMA),
                    customOverlays = customOverlays,
                    plotWidth = plotWidth,
                    barSpacingPx = barSpacingPx,
                    rightOffsetBars = rightOffsetBars
                )

                if (userPriceCenter.isNaN() || userPriceSpan.isNaN()) {
                    userPriceCenter = (autoBounds.second + autoBounds.first) / 2.0
                    userPriceSpan = (autoBounds.second - autoBounds.first).coerceAtLeast(1e-8)
                } else if (isAutoScale) {
                    userPriceCenter = (autoBounds.second + autoBounds.first) / 2.0
                    userPriceSpan = (autoBounds.second - autoBounds.first).coerceAtLeast(1e-8)
                }

                val effMinPrice = userPriceCenter - userPriceSpan / 2.0
                val effMaxPrice = userPriceCenter + userPriceSpan / 2.0

                val mapper = ChartCoordinateMapper.build(
                    candles = candles,
                    dccResult = dccResult,
                    includeDccBounds = dccSettings.masterEnabled && (dccSettings.enableEMA || dccSettings.enableVwap || dccSettings.enableHTFEMA),
                    customOverlays = customOverlays,
                    timeframe = timeframe,
                    plotWidth = plotWidth,
                    plotHeight = plotHeight,
                    topPadding = plotTopPadPx,
                    bottomPadding = plotBotPadPx,
                    barSpacingPx = barSpacingPx,
                    rightOffsetBars = rightOffsetBars,
                    customMinPrice = effMinPrice,
                    customMaxPrice = effMaxPrice
                )
                latestMapperHolder[0] = mapper

                val axisPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 27f
                    typeface = monoTypeface
                    color = appearance.textSecondaryArgb.toComposeColor().toArgb()
                }

                // 1. Gridlines & Price/Time Axes with TradingView styling & [A] badge
                drawGridAndAxes(
                    mapper = mapper,
                    appearance = appearance,
                    decimals = instrument.decimals,
                    timeframe = timeframe,
                    axisPaint = axisPaint,
                    rightAxisWidthPx = rightAxisWidthPx,
                    bottomAxisHeightPx = bottomAxisHeightPx,
                    isAutoScale = isAutoScale,
                    quoteCurrency = instrument.id.substringAfter('_', "USD")
                )

                // 2. Clipped plot layers
                clipRect(left = 0f, top = 0f, right = plotWidth, bottom = plotHeight) {
                    // DCC Master background layers (Session boxes, Rolling VP, Profile S/R, DO/PDH/PDL)
                    if (candles.isNotEmpty()) {
                        DccMasterRenderer.drawBackgroundLayers(
                            scope = this,
                            mapper = mapper,
                            result = dccResult,
                            settings = dccSettings,
                            appearance = appearance,
                            decimals = instrument.decimals,
                            monoTypeface = monoTypeface
                        )
                    }

                    // Candlesticks
                    if (candles.isNotEmpty()) {
                        drawCandlesticks(
                            mapper = mapper,
                            candles = candles,
                            barSpacingPx = barSpacingPx,
                            appearance = appearance
                        )
                    }

                    // DCC Master foreground layers (Fast/Slow EMA, VWAP, HTF EMA 1, PB lines, Cross/PB/P3 markers) + Custom Pine overlays
                    if (candles.isNotEmpty()) {
                        DccMasterRenderer.drawForegroundLayers(
                            scope = this,
                            mapper = mapper,
                            result = dccResult,
                            settings = dccSettings,
                            customOverlays = customOverlays,
                            monoTypeface = monoTypeface
                        )
                    }

                    // User drawings inside plot clip
                    DrawingOverlayRenderer.drawAll(
                        scope = this,
                        mapper = mapper,
                        drawings = drawings.filter { it.tool != DrawingTool.HORIZONTAL_LINE },
                        pendingAnchor = pendingAnchor,
                        appearance = appearance,
                        decimals = instrument.decimals,
                        monoTypeface = monoTypeface,
                        rightAxisWidthPx = rightAxisWidthPx
                    )

                    // Crosshair guide and axis tags
                    if (crosshairBarIndex != null && crosshairBarIndex in candles.indices) {
                        val c = candles[crosshairBarIndex]
                        val cx = mapper.indexToX(crosshairBarIndex.toFloat())
                        val cy = mapper.priceToY(c.close)
                        val crossColor = appearance.textSecondaryArgb.toComposeColor().copy(alpha = 0.7f)
                        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        drawLine(crossColor, Offset(cx, 0f), Offset(cx, plotHeight), strokeWidth = 1.6f, pathEffect = dash)
                        drawLine(crossColor, Offset(0f, cy), Offset(plotWidth, cy), strokeWidth = 1.6f, pathEffect = dash)
                        drawCircle(appearance.primaryAccentArgb.toComposeColor(), radius = 5.5f, center = Offset(cx, cy))

                        // Crosshair tag on right price axis
                        val pillH = 32f
                        val pillW = rightAxisWidthPx - 6f
                        drawRoundRect(
                            color = Color(0xFF141416),
                            topLeft = Offset(plotWidth + 3f, cy - pillH / 2f),
                            size = Size(pillW, pillH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        drawIntoCanvas { canvas ->
                            val chPaint = Paint().apply {
                                isAntiAlias = true
                                textSize = 25f
                                typeface = monoTypeface
                                color = 0xFFFFFFFF.toInt()
                                textAlign = Paint.Align.CENTER
                            }
                            canvas.nativeCanvas.drawText(
                                "+ " + formatPrice(c.close, instrument.decimals),
                                plotWidth + rightAxisWidthPx / 2f,
                                cy + 8f,
                                chPaint
                            )
                        }

                        // Crosshair tag on bottom time axis
                        val timePillW = 150f
                        val timePillH = 26f
                        val timePillLeft = (cx - timePillW / 2f).coerceIn(4f, plotWidth - timePillW - 4f)
                        drawRoundRect(
                            color = Color(0xFF141416),
                            topLeft = Offset(timePillLeft, plotHeight + 1f),
                            size = Size(timePillW, timePillH),
                            cornerRadius = CornerRadius(5f, 5f)
                        )
                        drawIntoCanvas { canvas ->
                            val chTimePaint = Paint().apply {
                                isAntiAlias = true
                                textSize = 22f
                                typeface = monoTypeface
                                color = 0xFFFFFFFF.toInt()
                                textAlign = Paint.Align.CENTER
                            }
                            canvas.nativeCanvas.drawText(
                                ChartTimeFormatter.formatHudTimestamp(c.timeEpochSec),
                                timePillLeft + timePillW / 2f,
                                plotHeight + 19f,
                                chTimePaint
                            )
                        }
                    }
                }

                // 3. Horizontal line drawings spanning right axis
                val hLines = drawings.filter { it.tool == DrawingTool.HORIZONTAL_LINE }
                if (hLines.isNotEmpty()) {
                    DrawingOverlayRenderer.drawAll(
                        scope = this,
                        mapper = mapper,
                        drawings = hLines,
                        pendingAnchor = null,
                        appearance = appearance,
                        decimals = instrument.decimals,
                        monoTypeface = monoTypeface,
                        rightAxisWidthPx = rightAxisWidthPx
                    )
                }

                // 4. Indicator level price pills on right axis
                if (dccSettings.masterEnabled && candles.isNotEmpty()) {
                    val lastIdx = candles.size - 1
                    val fastEma = dccResult.emaFast.getOrNull(lastIdx)
                    if (dccSettings.enableEMA && dccSettings.showFastEma && fastEma != null && fastEma.isFinite()) {
                        drawAxisIndicatorPill(mapper, fastEma, dccSettings.cuFast.toComposeColor(), instrument.decimals, plotWidth, rightAxisWidthPx, monoTypeface)
                    }
                    val slowEma = dccResult.emaSlow.getOrNull(lastIdx)
                    if (dccSettings.enableEMA && dccSettings.showSlowEma && slowEma != null && slowEma.isFinite()) {
                        drawAxisIndicatorPill(mapper, slowEma, dccSettings.cuSlow.toComposeColor(), instrument.decimals, plotWidth, rightAxisWidthPx, monoTypeface)
                    }
                    val vwap = dccResult.vwap.getOrNull(lastIdx)
                    if (dccSettings.enableVwap && vwap != null && vwap.isFinite()) {
                        drawAxisIndicatorPill(mapper, vwap, dccSettings.cuVwap.toComposeColor(), instrument.decimals, plotWidth, rightAxisWidthPx, monoTypeface)
                    }
                    val htfEma1 = dccResult.htfEma1.getOrNull(lastIdx)
                    if (dccSettings.enableHTFEMA && htfEma1 != null && htfEma1.isFinite()) {
                        drawAxisIndicatorPill(mapper, htfEma1, dccSettings.cuLevel1.toComposeColor(), instrument.decimals, plotWidth, rightAxisWidthPx, monoTypeface)
                    }
                }

                // 5. Live price line & right-axis pill with countdown timer
                val activePrice = liveQuote?.mid ?: candles.lastOrNull()?.close
                if (activePrice != null) {
                    val py = mapper.priceToY(activePrice).coerceIn(16f, plotHeight - 16f)
                    val lastCandle = candles.lastOrNull()
                    val pillColor = if (lastCandle != null && lastCandle.close >= lastCandle.open) {
                        appearance.bullishCandleArgb.toComposeColor()
                    } else {
                        appearance.bearishCandleArgb.toComposeColor()
                    }
                    drawLine(
                        color = pillColor.copy(alpha = 0.75f),
                        start = Offset(0f, py),
                        end = Offset(plotWidth, py),
                        strokeWidth = 1.8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    val pillH = 42f
                    drawRoundRect(
                        color = pillColor,
                        topLeft = Offset(plotWidth + 3f, py - pillH / 2f),
                        size = Size(rightAxisWidthPx - 6f, pillH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )

                    val nowSec = System.currentTimeMillis() / 1000L
                    val lastCandleTime = candles.lastOrNull()?.timeEpochSec ?: nowSec
                    val candleCloseTime = lastCandleTime + timeframe.durationSec
                    val remainingSec = (candleCloseTime - nowSec).coerceAtLeast(0L)
                    val countdownStr = String.format(java.util.Locale.US, "%02d:%02d", remainingSec / 60, remainingSec % 60)

                    drawIntoCanvas { canvas ->
                        axisPaint.color = 0xFFFFFFFF.toInt()
                        axisPaint.textAlign = Paint.Align.CENTER
                        axisPaint.textSize = 23f
                        canvas.nativeCanvas.drawText(
                            formatPrice(activePrice, instrument.decimals),
                            plotWidth + rightAxisWidthPx / 2f,
                            py - 2f,
                            axisPaint
                        )
                        val cdPaint = Paint().apply {
                            isAntiAlias = true
                            textSize = 19f
                            typeface = monoTypeface
                            color = 0xEEFFFFFF.toInt()
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.nativeCanvas.drawText(
                            countdownStr,
                            plotWidth + rightAxisWidthPx / 2f,
                            py + 16f,
                            cdPaint
                        )
                    }
                }
            }

            // Top-left OHLCV + DCC Master HUD Strip
            val inspectedIndex = crosshairBarIndex?.takeIf { it in candles.indices }
                ?: (candles.size - 1).takeIf { it >= 0 }
            if (inspectedIndex != null) {
                val c = candles[inspectedIndex]
                OhlcHudOverlay(
                    candle = c,
                    emaFast = dccResult.emaFast.getOrNull(inspectedIndex),
                    emaSlow = dccResult.emaSlow.getOrNull(inspectedIndex),
                    vwap = dccResult.vwap.getOrNull(inspectedIndex),
                    dccSettings = dccSettings,
                    decimals = instrument.decimals,
                    appearance = appearance,
                    isCrosshairActive = crosshairBarIndex != null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 6.dp, end = 82.dp)
                )
            }

            // Exact DCC Table & Full Mobile TSM Table overlays
            if (candles.isNotEmpty()) {
                DccTablesOverlay(
                    tableData = dccResult.tableData,
                    settings = dccSettings,
                    decimals = instrument.decimals
                )
            }

            // Active Drawing Tool Step Hint Banner
            androidx.compose.animation.AnimatedVisibility(
                visible = activeTool != DrawingTool.CURSOR,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            ) {
                val hintText = if (pendingAnchor == null) activeTool.firstTapHint else activeTool.secondTapHint
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(appearance.surfaceElevatedArgb.toComposeColor().copy(alpha = 0.94f))
                        .border(
                            width = 1.dp,
                            color = appearance.primaryAccentArgb.toComposeColor().copy(alpha = 0.7f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.labelMedium,
                        color = appearance.textPrimaryArgb.toComposeColor()
                    )
                    IconButton(
                        onClick = onCancelDrawing,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("cancel_active_drawing_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel_drawing_action),
                            tint = appearance.textSecondaryArgb.toComposeColor(),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Floating Reset Viewport button
            androidx.compose.animation.AnimatedVisibility(
                visible = abs(rightOffsetBars - 14f) > 14f || abs(barSpacingPx - defaultBarSpacingPx) > 12f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 82.dp, bottom = 36.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        barSpacingPx = defaultBarSpacingPx
                        rightOffsetBars = if (dccSettings.masterEnabled && dccSettings.enableVP) 16f else 8f
                    },
                    containerColor = appearance.surfaceElevatedArgb.toComposeColor(),
                    contentColor = appearance.primaryAccentArgb.toComposeColor(),
                    modifier = Modifier.testTag("reset_viewport_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = stringResource(R.string.reset_zoom_cd),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Synchronized Lower Oscillator Sub-Panes for overlay=false custom Pine indicators
        customOscillators.forEach { osc ->
            OscillatorSubPane(
                result = osc,
                candleCount = candles.size,
                barSpacingPx = barSpacingPx,
                rightOffsetBars = rightOffsetBars,
                rightAxisWidthPx = rightAxisWidthPx,
                appearance = appearance,
                monoTypeface = monoTypeface
            )
        }
    }
}

private fun DrawScope.drawAxisIndicatorPill(
    mapper: ChartCoordinateMapper,
    price: Double,
    pillBgColor: Color,
    decimals: Int,
    plotWidth: Float,
    rightAxisWidthPx: Float,
    monoTypeface: Typeface
) {
    val y = mapper.priceToY(price)
    if (y < 30f || y > mapper.plotHeight - 28f) return
    val pillH = 24f
    val pillW = rightAxisWidthPx - 6f
    drawRoundRect(
        color = pillBgColor,
        topLeft = Offset(plotWidth + 3f, y - pillH / 2f),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(5f, 5f)
    )
    drawIntoCanvas { canvas ->
        val pillPaint = Paint().apply {
            isAntiAlias = true
            textSize = 20f
            typeface = monoTypeface
            this.color = 0xFFFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
        }
        canvas.nativeCanvas.drawText(
            formatPrice(price, decimals),
            plotWidth + rightAxisWidthPx / 2f,
            y + 6.5f,
            pillPaint
        )
    }
}

private fun DrawScope.drawGridAndAxes(
    mapper: ChartCoordinateMapper,
    appearance: ChartAppearance,
    decimals: Int,
    timeframe: Timeframe,
    axisPaint: Paint,
    rightAxisWidthPx: Float,
    bottomAxisHeightPx: Float,
    isAutoScale: Boolean,
    quoteCurrency: String
) {
    val borderColor = appearance.borderArgb.toComposeColor()
    val gridColor = appearance.gridColorArgb.toComposeColor().copy(alpha = appearance.gridAlpha)

    drawLine(borderColor, Offset(mapper.plotWidth, 0f), Offset(mapper.plotWidth, mapper.plotHeight), 1.5f)
    drawLine(borderColor, Offset(0f, mapper.plotHeight), Offset(size.width, mapper.plotHeight), 1.5f)

    // TradingView subtle watermark at bottom left
    drawIntoCanvas { canvas ->
        val wmPaint = Paint().apply {
            isAntiAlias = true
            textSize = 28f
            typeface = axisPaint.typeface
            color = appearance.textSecondaryArgb.toComposeColor().toArgb()
            alpha = 55
        }
        canvas.nativeCanvas.drawText("17 TradingView", 20f, mapper.plotHeight - 14f, wmPaint)
    }

    // Currency badge at top of right price scale
    val currPillW = rightAxisWidthPx - 16f
    val currPillH = 22f
    val currTop = 6f
    val currLeft = mapper.plotWidth + 8f
    drawRoundRect(
        color = appearance.surfaceElevatedArgb.toComposeColor(),
        topLeft = Offset(currLeft, currTop),
        size = Size(currPillW, currPillH),
        cornerRadius = CornerRadius(5f, 5f)
    )
    drawIntoCanvas { canvas ->
        val pillTextPaint = Paint().apply {
            isAntiAlias = true
            textSize = 20f
            typeface = axisPaint.typeface
            textAlign = Paint.Align.CENTER
            color = appearance.textSecondaryArgb.toComposeColor().toArgb()
        }
        canvas.nativeCanvas.drawText(
            quoteCurrency,
            currLeft + currPillW / 2f,
            currTop + 16f,
            pillTextPaint
        )
    }

    // [A] Auto-fit button badge at bottom of right price scale
    val autoPillW = rightAxisWidthPx - 16f
    val autoPillH = 22f
    val autoTop = mapper.plotHeight - 26f
    val autoLeft = mapper.plotWidth + 8f
    val autoBg = if (isAutoScale) {
        appearance.primaryAccentArgb.toComposeColor()
    } else {
        appearance.surfaceElevatedArgb.toComposeColor().copy(alpha = 0.85f)
    }
    val autoTextColor = if (isAutoScale) {
        0xFFFFFFFF.toInt()
    } else {
        appearance.textSecondaryArgb.toComposeColor().toArgb()
    }
    drawRoundRect(
        color = autoBg,
        topLeft = Offset(autoLeft, autoTop),
        size = Size(autoPillW, autoPillH),
        cornerRadius = CornerRadius(5f, 5f)
    )
    drawIntoCanvas { canvas ->
        val autoPaint = Paint().apply {
            isAntiAlias = true
            textSize = 21f
            typeface = axisPaint.typeface
            textAlign = Paint.Align.CENTER
            color = autoTextColor
            isFakeBoldText = isAutoScale
        }
        canvas.nativeCanvas.drawText(
            "A",
            autoLeft + autoPillW / 2f,
            autoTop + 16f,
            autoPaint
        )
    }

    val horizontalSteps = 6
    val priceStep = (mapper.maxPrice - mapper.minPrice) / horizontalSteps
    for (i in 0..horizontalSteps) {
        val price = mapper.maxPrice - i * priceStep
        val y = mapper.priceToY(price)
        if (y < 28f || y > mapper.plotHeight - 28f) continue

        if (appearance.showGrid) {
            drawLine(gridColor, Offset(0f, y), Offset(mapper.plotWidth, y), 1f)
        }
        drawIntoCanvas { canvas ->
            axisPaint.textAlign = Paint.Align.CENTER
            canvas.nativeCanvas.drawText(
                formatPrice(price, decimals),
                mapper.plotWidth + rightAxisWidthPx / 2f,
                y + 9f,
                axisPaint
            )
        }
    }

    val visibleRange = mapper.visibleIndexRange()
    if (!visibleRange.isEmpty()) {
        val visibleCount = (visibleRange.last - visibleRange.first + 1).coerceAtLeast(1)
        val desiredLabels = (mapper.plotWidth / 190f).toInt().coerceIn(3, 7)
        val step = (visibleCount / desiredLabels).coerceAtLeast(1)

        var idx = visibleRange.first
        while (idx <= visibleRange.last) {
            val x = mapper.indexToX(idx.toFloat())
            if (x in 30f..(mapper.plotWidth - 30f)) {
                if (appearance.showGrid) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, mapper.plotHeight), 1f)
                }
                val timeStr = ChartTimeFormatter.formatAxisLabel(
                    mapper.candles[idx].timeEpochSec,
                    timeframe
                )
                drawIntoCanvas { canvas ->
                    axisPaint.textAlign = Paint.Align.CENTER
                    canvas.nativeCanvas.drawText(
                        timeStr,
                        x,
                        mapper.plotHeight + bottomAxisHeightPx * 0.72f,
                        axisPaint
                    )
                }
            }
            idx += step
        }
    }
}

private fun DrawScope.drawCandlesticks(
    mapper: ChartCoordinateMapper,
    candles: List<Candle>,
    barSpacingPx: Float,
    appearance: ChartAppearance
) {
    val visibleRange = mapper.visibleIndexRange()
    if (visibleRange.isEmpty()) return

    val bullColor = appearance.bullishCandleArgb.toComposeColor()
    val bearColor = appearance.bearishCandleArgb.toComposeColor()
    val bodyWidth = (barSpacingPx * 0.68f).coerceIn(2f, 26f)
    val wickWidth = (barSpacingPx * 0.13f).coerceIn(1.5f, 3.2f)

    for (i in visibleRange) {
        val c = candles[i]
        val x = mapper.indexToX(i.toFloat())
        val isBull = c.close >= c.open
        val color = if (isBull) bullColor else bearColor

        val highY = mapper.priceToY(c.high)
        val lowY = mapper.priceToY(c.low)
        val openY = mapper.priceToY(c.open)
        val closeY = mapper.priceToY(c.close)

        drawLine(color, Offset(x, highY), Offset(x, lowY), wickWidth)
        val bodyTop = min(openY, closeY)
        val bodyH = max(abs(closeY - openY), 2f)
        drawRect(color, Offset(x - bodyWidth / 2f, bodyTop), Size(bodyWidth, bodyH))
    }
}

@Composable
private fun OhlcHudOverlay(
    candle: Candle,
    emaFast: Double?,
    emaSlow: Double?,
    vwap: Double?,
    dccSettings: DccSettings,
    decimals: Int,
    appearance: ChartAppearance,
    isCrosshairActive: Boolean,
    modifier: Modifier = Modifier
) {
    val isBull = candle.close >= candle.open
    val valueColor = if (isBull) {
        appearance.bullishCandleArgb.toComposeColor()
    } else {
        appearance.bearishCandleArgb.toComposeColor()
    }
    val secondary = appearance.textSecondaryArgb.toComposeColor()

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(appearance.surfaceArgb.toComposeColor().copy(alpha = 0.78f))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("O ${formatPrice(candle.open, decimals)}", style = MonoPriceSmallStyle, color = valueColor)
            Text("H ${formatPrice(candle.high, decimals)}", style = MonoPriceSmallStyle, color = valueColor)
            Text("L ${formatPrice(candle.low, decimals)}", style = MonoPriceSmallStyle, color = valueColor)
            Text("C ${formatPrice(candle.close, decimals)}", style = MonoPriceSmallStyle, color = valueColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isCrosshairActive) {
                Text(ChartTimeFormatter.formatHudTimestamp(candle.timeEpochSec), style = MonoPriceSmallStyle, color = secondary)
            }
            if (dccSettings.masterEnabled) {
                if (dccSettings.enableEMA && dccSettings.showFastEma && emaFast != null) {
                    Text("E${dccSettings.fastLen} ${formatPrice(emaFast, decimals)}", style = MonoPriceSmallStyle, color = dccSettings.cuFast.toComposeColor())
                }
                if (dccSettings.enableEMA && dccSettings.showSlowEma && emaSlow != null) {
                    Text("E${dccSettings.slowLen} ${formatPrice(emaSlow, decimals)}", style = MonoPriceSmallStyle, color = dccSettings.cuSlow.toComposeColor())
                }
                if (dccSettings.enableVwap && vwap != null) {
                    Text("VWAP ${formatPrice(vwap, decimals)}", style = MonoPriceSmallStyle, color = dccSettings.cuVwap.toComposeColor())
                }
            }
        }
    }
}
