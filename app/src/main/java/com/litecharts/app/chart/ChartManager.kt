package com.litecharts.app.chart

import android.graphics.Color
import com.tradingview.lightweightcharts.api.interfaces.ChartApi
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.options.models.*
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor
import com.tradingview.lightweightcharts.api.series.enums.CrosshairMode
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.enums.SeriesMarkerPosition
import com.tradingview.lightweightcharts.api.series.enums.SeriesMarkerShape
import com.tradingview.lightweightcharts.api.series.models.*
import com.tradingview.lightweightcharts.view.ChartsView
import com.litecharts.app.data.OandaCandle
import com.litecharts.app.indicator.DccIndicatorEngine

/**
 * Manages the TradingView Lightweight Charts Android [ChartsView].
 *
 * Provides:
 *  • Candle data loading
 *  • DCC indicator line/marker overlay
 *  • Coordinate mapping for the drawing overlay (via async WebView bridge)
 */
class ChartManager(private val chartsView: ChartsView) {

    // ── Coordinate mapping shared with DrawingOverlayView ──────
    data class CoordinateMapping(
        val timeFrom: Long, val timeTo: Long,
        val priceTop: Double, val priceBottom: Double,
        val chartWidth: Int, val chartHeight: Int
    )

    @Volatile var currentMapping: CoordinateMapping? = null
        private set

    var onMappingUpdated: ((CoordinateMapping?) -> Unit)? = null

    // ── Internal refs ──────────────────────────────────────────
    private var candleSeries: SeriesApi? = null
    private val indicatorSeries = mutableListOf<SeriesApi>()

    // ── Init ───────────────────────────────────────────────────

    fun initialize(onReady: () -> Unit) {
        // Wait for the internal WebView / chart bridge to be ready
        chartsView.subscribeOnChartStateChange { state ->
            if (state == ChartsView.State.READY) {
                applyDarkTheme()
                createCandleSeries(onReady)
            }
        }
    }

    private fun applyDarkTheme() {
        chartsView.api.applyOptions {
            layout = layoutOptions {
                background = SolidColor(IntColor(Color.parseColor("#0D1117")))
                textColor = IntColor(Color.parseColor("#8B949E"))
                fontSize = 11f
            }
            grid = gridOptions {
                vertLines = gridLineOptions { color = IntColor(Color.parseColor("#161B22")) }
                horzLines = gridLineOptions { color = IntColor(Color.parseColor("#161B22")) }
            }
            crosshair = crosshairOptions {
                mode = CrosshairMode.NORMAL
            }
            timeScale = timeScaleOptions {
                borderColor = IntColor(Color.parseColor("#21262D"))
                timeVisible = true
                secondsVisible = false
                rightOffset = 5f
            }
            rightPriceScale = priceScaleOptions {
                borderColor = IntColor(Color.parseColor("#21262D"))
                scaleMargins = priceScaleMargins {
                    top = 0.1f
                    bottom = 0.1f
                }
            }
            handleScroll = handleScrollOptions {
                mouseWheel = true
                pressedMouseMove = true
                horzTouchDrag = true
                vertTouchDrag = true
            }
            handleScale = handleScaleOptions {
                mouseWheel = true
                pinch = true
            }
        }
    }

    private fun createCandleSeries(onReady: () -> Unit) {
        chartsView.api.addCandlestickSeries(
            options = CandlestickSeriesOptions().apply {
                upColor       = IntColor(Color.parseColor("#26A69A"))
                downColor     = IntColor(Color.parseColor("#EF5350"))
                borderUpColor = IntColor(Color.parseColor("#26A69A"))
                borderDownColor = IntColor(Color.parseColor("#EF5350"))
                wickUpColor   = IntColor(Color.parseColor("#26A69A"))
                wickDownColor = IntColor(Color.parseColor("#EF5350"))
            },
            onSeriesCreated = { api ->
                candleSeries = api
                subscribeVisibleRange()
                onReady()
            }
        )
    }

    // ── Candle data ────────────────────────────────────────────

    fun setCandleData(candles: List<OandaCandle>) {
        val series = candleSeries ?: return
        val data = candles.map { c ->
            CandlestickData(
                time  = Time.Utc(c.time),
                open  = c.open.toFloat(),
                high  = c.high.toFloat(),
                low   = c.low.toFloat(),
                close = c.close.toFloat()
            )
        }
        series.setData(data)
        chartsView.api.timeScale.fitContent()
    }

    // ── DCC Indicator overlay ──────────────────────────────────

    fun setIndicator(result: DccIndicatorEngine.IndicatorResult) {
        clearIndicators()

        // Add each named line series
        result.lines.forEach { line ->
            chartsView.api.addLineSeries(
                options = LineSeriesOptions().apply {
                    color = IntColor(Color.parseColor(line.color))
                    lineWidth = when (line.lineWidth) {
                        1 -> LineWidth.ONE; 2 -> LineWidth.TWO; 3 -> LineWidth.THREE
                        else -> LineWidth.ONE
                    }
                    crosshairMarkerVisible = false
                    lastValueVisible = false
                    priceLineVisible = false
                },
                onSeriesCreated = { api ->
                    val pts = line.points.map { p ->
                        LineData(time = Time.Utc(p.time), value = p.value.toFloat())
                    }
                    api.setData(pts)
                    indicatorSeries += api
                }
            )
        }

        // Signal markers on the candle series
        if (result.markers.isNotEmpty()) {
            val markers = result.markers.map { m ->
                SeriesMarker(
                    time = Time.Utc(m.time),
                    position = when (m.position) {
                        DccIndicatorEngine.MarkerPosition.ABOVE -> SeriesMarkerPosition.ABOVE_BAR
                        DccIndicatorEngine.MarkerPosition.BELOW -> SeriesMarkerPosition.BELOW_BAR
                    },
                    color = IntColor(Color.parseColor(m.color)),
                    shape = when (m.shape) {
                        DccIndicatorEngine.MarkerShape.CIRCLE     -> SeriesMarkerShape.CIRCLE
                        DccIndicatorEngine.MarkerShape.ARROW_UP   -> SeriesMarkerShape.ARROW_UP
                        DccIndicatorEngine.MarkerShape.ARROW_DOWN -> SeriesMarkerShape.ARROW_DOWN
                        DccIndicatorEngine.MarkerShape.SQUARE     -> SeriesMarkerShape.SQUARE
                    },
                    text = m.text
                )
            }.sortedBy { (it.time as? Time.Utc)?.timestamp ?: 0L }
            candleSeries?.setMarkers(markers)
        }
    }

    fun clearIndicators() {
        indicatorSeries.forEach { chartsView.api.removeSeries(it) }
        indicatorSeries.clear()
        candleSeries?.setMarkers(emptyList())
    }

    // ── Coordinate mapping for the drawing overlay ─────────────

    /**
     * Subscribe to visible time-range changes.  Each time the user pans/zooms
     * we re-query price bounds via the async WebView bridge and publish a
     * [CoordinateMapping] that the [DrawingOverlayView] uses for all
     * price↔pixel and time↔pixel conversions.
     */
    private fun subscribeVisibleRange() {
        chartsView.api.timeScale.subscribeVisibleTimeRangeChange { range ->
            if (range == null) {
                currentMapping = null
                onMappingUpdated?.invoke(null)
                return@subscribeVisibleTimeRangeChange
            }

            val from = (range.from as? Time.Utc)?.timestamp ?: return@subscribeVisibleTimeRangeChange
            val to   = (range.to   as? Time.Utc)?.timestamp ?: return@subscribeVisibleTimeRangeChange

            val w = chartsView.width
            val h = chartsView.height
            if (w == 0 || h == 0) return@subscribeVisibleTimeRangeChange

            // Async bridge: query the price at top (y=0) and bottom (y=height)
            candleSeries?.coordinateToPrice(0f) { topPrice ->
                candleSeries?.coordinateToPrice(h.toFloat()) { bottomPrice ->
                    if (topPrice != null && bottomPrice != null) {
                        val mapping = CoordinateMapping(
                            timeFrom   = from,
                            timeTo     = to,
                            priceTop   = topPrice.toDouble(),
                            priceBottom = bottomPrice.toDouble(),
                            chartWidth = w,
                            chartHeight = h
                        )
                        currentMapping = mapping
                        onMappingUpdated?.invoke(mapping)
                    }
                }
            }
        }
    }

    // ── Utility coordinate converters (synchronous, linear) ───
    // These use the last published mapping for instant screen↔chart math.
    // They are intentionally linear interpolations — good enough for most
    // chart zoom levels and much faster than round-tripping through JS.

    fun priceToY(price: Double, m: CoordinateMapping): Float {
        val range = m.priceTop - m.priceBottom
        if (range == 0.0) return m.chartHeight / 2f
        return ((m.priceTop - price) / range * m.chartHeight).toFloat()
    }

    fun timeToX(time: Long, m: CoordinateMapping): Float {
        val range = m.timeTo - m.timeFrom
        if (range == 0L) return m.chartWidth / 2f
        return ((time - m.timeFrom).toFloat() / range * m.chartWidth)
    }

    fun yToPrice(y: Float, m: CoordinateMapping): Double {
        val range = m.priceTop - m.priceBottom
        return m.priceTop - (y / m.chartHeight * range)
    }

    fun xToTime(x: Float, m: CoordinateMapping): Long {
        val range = m.timeTo - m.timeFrom
        return m.timeFrom + (x / m.chartWidth * range).toLong()
    }

    fun destroy() { /* ChartsView handles WebView cleanup internally */ }
}
