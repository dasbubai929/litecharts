@file:SuppressLint("ClickableViewAccessibility")
package com.litecharts.app.drawing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.litecharts.app.chart.ChartManager
import com.litecharts.app.data.OandaCandle

/**
 * Transparent overlay placed directly on top of ChartsView.
 *
 *  • In [DrawingToolType.NONE] mode → all touches pass through to the chart.
 *  • In drawing mode → taps are captured, converted to (time, price) via
 *    the current [CoordinateMapping], and assembled into [Drawing] objects.
 *  • Drawings are rendered via Canvas using the mapping for screen positions.
 */
class DrawingOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── State ──────────────────────────────────────────────────
    var currentTool: DrawingToolType = DrawingToolType.NONE
        set(value) { field = value; pendingPts.clear(); invalidate() }

    var chartManager: ChartManager? = null
    var candles: List<OandaCandle> = emptyList()

    private val drawings = mutableListOf<Drawing>()
    private val pendingPts = mutableListOf<AnchorPoint>()
    private var mapping: ChartManager.CoordinateMapping? = null

    var onDrawingCompleted: ((Drawing) -> Unit)? = null
    var onToolReset: (() -> Unit)? = null

    // ── Paints ─────────────────────────────────────────────────
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = c("#58A6FF") }
    private val pDash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = c("#58A6FF"); pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f) }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c("#E6EDF3"); textSize = 24f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL) }
    private val pDot  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = c("#58A6FF") }
    private val greenF = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL;   color = c("#1A3FB950") }
    private val greenS = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = c("#3FB950") }
    private val redF   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL;   color = c("#1AF85149") }
    private val redS   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = c("#F85149") }
    private val volBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL;   color = c("#4058A6FF") }

    private val fibColors = listOf("#787B86","#F44336","#FF9800","#FFEB3B","#4CAF50","#00BCD4","#787B86")

    private fun c(hex: String) = Color.parseColor(hex)

    // ── Touch ──────────────────────────────────────────────────
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (currentTool == DrawingToolType.NONE) return false          // pass-through
        return when (ev.action) {
            MotionEvent.ACTION_DOWN -> true                            // claim sequence
            MotionEvent.ACTION_UP   -> { handleTap(ev.x, ev.y); true }
            else -> true
        }
    }

    private fun handleTap(x: Float, y: Float) {
        val m = mapping ?: return
        val cm = chartManager ?: return
        val price = cm.yToPrice(y, m)
        val time  = cm.xToTime(x, m)
        pendingPts += AnchorPoint(time, price)

        val drawing: Drawing? = when (currentTool) {
            DrawingToolType.HORIZONTAL_LINE          -> if (pendingPts.size >= 1) Drawing.HorizontalLine(price, time) else null
            DrawingToolType.TRENDLINE                -> if (pendingPts.size >= 2) Drawing.Trendline(pendingPts[0], pendingPts[1]) else null
            DrawingToolType.FIB_RETRACEMENT          -> if (pendingPts.size >= 2) Drawing.FibRetracement(pendingPts[0], pendingPts[1]) else null
            DrawingToolType.SHORT_POSITION           -> if (pendingPts.size >= 3) Drawing.PositionBox(false, pendingPts[0].price, pendingPts[1].price, pendingPts[2].price, pendingPts[0].time, pendingPts[2].time) else null
            DrawingToolType.LONG_POSITION            -> if (pendingPts.size >= 3) Drawing.PositionBox(true,  pendingPts[0].price, pendingPts[1].price, pendingPts[2].price, pendingPts[0].time, pendingPts[2].time) else null
            DrawingToolType.ANCHORED_VOLUME_PROFILE  -> if (pendingPts.size >= 1) Drawing.AnchoredVolumeProfile(time, price) else null
            DrawingToolType.FIXED_RANGE_VOLUME       -> if (pendingPts.size >= 2) Drawing.FixedRangeVolume(pendingPts[0], pendingPts[1]) else null
            DrawingToolType.PRICE_RANGE              -> if (pendingPts.size >= 2) Drawing.PriceRange(pendingPts[0], pendingPts[1]) else null
            DrawingToolType.RECTANGLE                -> if (pendingPts.size >= 2) Drawing.Rectangle(pendingPts[0], pendingPts[1]) else null
            DrawingToolType.NONE                     -> null
        }

        if (drawing != null) {
            drawings += drawing
            pendingPts.clear()
            onDrawingCompleted?.invoke(drawing)
            currentTool = DrawingToolType.NONE
            onToolReset?.invoke()
        }
        invalidate()
    }

    // ── Coordinate sync ────────────────────────────────────────
    fun updateCoordinateMapping(m: ChartManager.CoordinateMapping?) { mapping = m; invalidate() }

    private fun p2y(price: Double, m: ChartManager.CoordinateMapping) = chartManager?.priceToY(price, m) ?: 0f
    private fun t2x(time: Long, m: ChartManager.CoordinateMapping)   = chartManager?.timeToX(time, m)   ?: 0f

    // ── Canvas ─────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val m = mapping ?: return
        drawings.forEach { drawOne(canvas, it, m) }
        // pending dots
        pendingPts.forEach { canvas.drawCircle(t2x(it.time, m), p2y(it.price, m), 8f, pDot) }
    }

    private fun drawOne(c: Canvas, d: Drawing, m: ChartManager.CoordinateMapping) = when (d) {
        is Drawing.HorizontalLine       -> drawHL(c, d, m)
        is Drawing.Trendline            -> drawTL(c, d, m)
        is Drawing.FibRetracement       -> drawFib(c, d, m)
        is Drawing.PositionBox          -> drawPos(c, d, m)
        is Drawing.AnchoredVolumeProfile -> drawAVP(c, d, m)
        is Drawing.FixedRangeVolume     -> drawFRV(c, d, m)
        is Drawing.PriceRange           -> drawPR(c, d, m)
        is Drawing.Rectangle            -> drawRect(c, d, m)
    }

    // ── Individual drawing renderers ───────────────────────────

    private fun drawHL(c: Canvas, d: Drawing.HorizontalLine, m: ChartManager.CoordinateMapping) {
        val y = p2y(d.price, m)
        pDash.color = c("#58A6FF")
        c.drawLine(0f, y, width.toFloat(), y, pDash)
        // label
        pText.textSize = 22f
        val lbl = fmtP(d.price); val tw = pText.measureText(lbl)
        pFill.color = c("#58A6FF")
        c.drawRect(width - tw - 16f, y - 13f, width.toFloat(), y + 13f, pFill)
        pText.color = Color.WHITE; c.drawText(lbl, width - tw - 8f, y + 6f, pText)
        pText.color = c("#E6EDF3")
    }

    private fun drawTL(c: Canvas, d: Drawing.Trendline, m: ChartManager.CoordinateMapping) {
        val x1 = t2x(d.start.time, m); val y1 = p2y(d.start.price, m)
        val x2 = t2x(d.end.time, m);   val y2 = p2y(d.end.price, m)
        pLine.color = c("#58A6FF")
        c.drawLine(x1, y1, x2, y2, pLine)
        c.drawCircle(x1, y1, 5f, pDot); c.drawCircle(x2, y2, 5f, pDot)
    }

    private fun drawFib(c: Canvas, d: Drawing.FibRetracement, m: ChartManager.CoordinateMapping) {
        val hi = maxOf(d.start.price, d.end.price)
        val lo = minOf(d.start.price, d.end.price)
        val range = hi - lo; if (range == 0.0) return
        pText.textSize = 20f
        d.levels.forEachIndexed { idx, lvl ->
            val price = hi - range * lvl; val y = p2y(price, m)
            val col = c(fibColors[idx % fibColors.size])
            pDash.color = col; c.drawLine(0f, y, width.toFloat(), y, pDash)
            pText.color = col; c.drawText("${(lvl * 100).toInt()}%  ${fmtP(price)}", 8f, y - 4f, pText)
        }
        // subtle fills
        for (i in 0 until d.levels.size - 1) {
            val y1 = p2y(hi - range * d.levels[i], m)
            val y2 = p2y(hi - range * d.levels[i + 1], m)
            pFill.color = c("#0858A6FF"); c.drawRect(0f, minOf(y1, y2), width.toFloat(), maxOf(y1, y2), pFill)
        }
    }

    private fun drawPos(c: Canvas, d: Drawing.PositionBox, m: ChartManager.CoordinateMapping) {
        val x1 = t2x(d.startTime, m); val x2 = t2x(d.endTime, m)
        val eY = p2y(d.entry, m); val sY = p2y(d.stop, m); val tY = p2y(d.target, m)
        val l = minOf(x1, x2); val r = maxOf(x1, x2)
        // target zone
        c.drawRect(l, minOf(eY, tY), r, maxOf(eY, tY), greenF)
        c.drawRect(l, minOf(eY, tY), r, maxOf(eY, tY), greenS)
        // stop zone
        c.drawRect(l, minOf(eY, sY), r, maxOf(eY, sY), redF)
        c.drawRect(l, minOf(eY, sY), r, maxOf(eY, sY), redS)
        // entry
        pLine.color = c("#E6EDF3"); c.drawLine(l, eY, r, eY, pLine)
        // labels
        pText.textSize = 20f
        pText.color = c("#3FB950"); c.drawText("T: ${fmtP(d.target)}", l + 4, tY + if (d.isLong) -4f else 16f, pText)
        pText.color = c("#E6EDF3"); c.drawText("E: ${fmtP(d.entry)}",  l + 4, eY - 4f, pText)
        pText.color = c("#F85149"); c.drawText("S: ${fmtP(d.stop)}",   l + 4, sY + if (d.isLong) 16f else -4f, pText)
        // R:R
        val risk = Math.abs(d.entry - d.stop); val reward = Math.abs(d.target - d.entry)
        val rr = if (risk > 0) reward / risk else 0.0
        pText.color = c("#BC8CFF"); c.drawText("R:R %.1f".format(rr), l + 4, (eY + tY) / 2, pText)
    }

    private fun drawAVP(c: Canvas, d: Drawing.AnchoredVolumeProfile, m: ChartManager.CoordinateMapping) {
        val idx = candles.indexOfFirst { it.time >= d.anchorTime }
        if (idx < 0) return
        renderVP(c, candles.subList(idx, candles.size), m)
    }

    private fun drawFRV(c: Canvas, d: Drawing.FixedRangeVolume, m: ChartManager.CoordinateMapping) {
        val t0 = minOf(d.start.time, d.end.time); val t1 = maxOf(d.start.time, d.end.time)
        val sub = candles.filter { it.time in t0..t1 }
        if (sub.isEmpty()) return
        pDash.color = c("#BC8CFF")
        val lx = t2x(t0, m); val rx = t2x(t1, m)
        c.drawLine(lx, 0f, lx, height.toFloat(), pDash)
        c.drawLine(rx, 0f, rx, height.toFloat(), pDash)
        renderVP(c, sub, m)
    }

    private fun renderVP(c: Canvas, data: List<OandaCandle>, m: ChartManager.CoordinateMapping) {
        if (data.isEmpty()) return
        val lo = data.minOf { it.low }; val hi = data.maxOf { it.high }
        val range = hi - lo; if (range == 0.0) return
        val n = 30; val bs = range / n; val buckets = IntArray(n)
        data.forEach { buckets[((it.close - lo) / bs).toInt().coerceIn(0, n - 1)] += it.volume }
        val mx = buckets.maxOrNull() ?: return; if (mx == 0) return
        val pw = width * 0.15f
        for (i in 0 until n) {
            val p = lo + i * bs; val y1 = p2y(p + bs, m); val y2 = p2y(p, m)
            val bw = buckets[i].toFloat() / mx * pw
            volBar.color = if (buckets[i] == mx) c("#6058A6FF") else c("#3058A6FF")
            c.drawRect(0f, minOf(y1, y2), bw, maxOf(y1, y2), volBar)
        }
    }

    private fun drawPR(c: Canvas, d: Drawing.PriceRange, m: ChartManager.CoordinateMapping) {
        val x1 = t2x(d.start.time, m); val y1 = p2y(d.start.price, m)
        val x2 = t2x(d.end.time, m);   val y2 = p2y(d.end.price, m)
        pDash.color = c("#58A6FF"); c.drawLine(x1, y1, x2, y2, pDash)

        val delta = d.end.price - d.start.price
        val pct = if (d.start.price != 0.0) delta / d.start.price * 100 else 0.0
        val si = candles.indexOfFirst { it.time >= minOf(d.start.time, d.end.time) }
        val ei = candles.indexOfFirst { it.time >= maxOf(d.start.time, d.end.time) }
        val bars = if (si >= 0 && ei >= 0) Math.abs(ei - si) else 0

        val mx = (x1 + x2) / 2; val my = (y1 + y2) / 2
        pText.textSize = 22f
        val up = delta >= 0; val col = if (up) "#3FB950" else "#F85149"
        val l1 = "${if (up) "+" else ""}${fmtP(delta)}"
        val l2 = "${if (up) "+" else ""}${"%.2f".format(pct)}%"
        val l3 = "$bars bars"
        val tw = maxOf(pText.measureText(l1), pText.measureText(l2), pText.measureText(l3))
        pFill.color = c("#CC161B22")
        c.drawRoundRect(mx - tw / 2 - 8, my - 42f, mx + tw / 2 + 8, my + 36f, 6f, 6f, pFill)
        pText.textAlign = Paint.Align.CENTER
        pText.color = c(col); c.drawText(l1, mx, my - 20f, pText); c.drawText(l2, mx, my + 4f, pText)
        pText.color = c("#8B949E"); c.drawText(l3, mx, my + 28f, pText)
        pText.textAlign = Paint.Align.LEFT; pText.color = c("#E6EDF3")
    }

    private fun drawRect(c: Canvas, d: Drawing.Rectangle, m: ChartManager.CoordinateMapping) {
        val x1 = t2x(d.corner1.time, m); val y1 = p2y(d.corner1.price, m)
        val x2 = t2x(d.corner2.time, m); val y2 = p2y(d.corner2.price, m)
        val l = minOf(x1, x2); val r = maxOf(x1, x2); val t = minOf(y1, y2); val b = maxOf(y1, y2)
        pFill.color = c("#1A58A6FF"); c.drawRect(l, t, r, b, pFill)
        pLine.color = c("#58A6FF");   c.drawRect(l, t, r, b, pLine)
    }

    // ── Public API ─────────────────────────────────────────────
    fun setDrawings(list: List<Drawing>) { drawings.clear(); drawings += list; invalidate() }
    fun getDrawings(): List<Drawing> = drawings.toList()
    fun clearDrawings() { drawings.clear(); pendingPts.clear(); invalidate() }

    private fun fmtP(p: Double) = when {
        Math.abs(p) >= 1000 -> "%.1f".format(p)
        Math.abs(p) >= 1    -> "%.4f".format(p)
        else                -> "%.5f".format(p)
    }
}
