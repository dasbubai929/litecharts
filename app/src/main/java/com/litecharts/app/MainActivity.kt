package com.litecharts.app

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.litecharts.app.chart.ChartManager
import com.litecharts.app.data.*
import com.litecharts.app.databinding.ActivityMainBinding
import com.litecharts.app.drawing.*
import com.litecharts.app.indicator.DccIndicatorEngine
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var chart: ChartManager

    private var currentMarket = MARKETS[0]
    private var currentTf     = TIMEFRAMES[5]           // H1 default
    private var currentCandles: List<OandaCandle> = emptyList()
    private var dccEnabled = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val tfButtons = mutableListOf<Button>()

    // ── Lifecycle ──────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        setupChart()
        setupTopBar()
        setupDrawingToolbar()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        chart.destroy()
    }

    // ── Chart ──────────────────────────────────────────────────

    private fun setupChart() {
        chart = ChartManager(b.chartsView)

        // Wire coordinate mapping updates to the drawing overlay
        chart.onMappingUpdated = { mapping ->
            b.drawingOverlay.updateCoordinateMapping(mapping)
        }

        b.drawingOverlay.chartManager = chart

        b.drawingOverlay.onDrawingCompleted = { saveDrawings() }
        b.drawingOverlay.onToolReset = {
            updateToolbarHighlight(DrawingToolType.NONE)
            b.tvDrawingHint.visibility = View.GONE
        }

        // The chart initializes asynchronously via its internal WebView;
        // loadData() fires once the bridge is ready.
        chart.initialize { loadData() }
    }

    // ── Top bar: market selector + timeframe strip + DCC ──────

    private fun setupTopBar() {
        b.btnMarketSelector.text = currentMarket.displayName
        b.btnMarketSelector.setOnClickListener { showMarketPicker() }

        TIMEFRAMES.forEach { tf ->
            val btn = Button(this).apply {
                text = tf.label
                textSize = 11f
                setTextColor(if (tf == currentTf) c("#58A6FF") else c("#8B949E"))
                setBackgroundColor(Color.TRANSPARENT)
                isAllCaps = false
                minimumWidth = 0; minWidth = 0
                setPadding(dp(6), dp(2), dp(6), dp(2))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(1), 0, dp(1), 0) }
                setOnClickListener { selectTf(tf) }
            }
            tfButtons += btn
            b.timeframeContainer.addView(btn)
        }

        b.switchDcc.setOnCheckedChangeListener { _, on ->
            dccEnabled = on
            if (on && currentCandles.isNotEmpty()) applyDcc()
            else chart.clearIndicators()
        }
    }

    // ── Drawing toolbar ────────────────────────────────────────

    private fun setupDrawingToolbar() {
        data class ToolDef(val label: String, val tool: DrawingToolType?)
        val defs = listOf(
            ToolDef("Pan",   DrawingToolType.NONE),
            ToolDef("H—",    DrawingToolType.HORIZONTAL_LINE),
            ToolDef("Trend", DrawingToolType.TRENDLINE),
            ToolDef("Fib",   DrawingToolType.FIB_RETRACEMENT),
            ToolDef("Short", DrawingToolType.SHORT_POSITION),
            ToolDef("Long",  DrawingToolType.LONG_POSITION),
            ToolDef("A.Vol", DrawingToolType.ANCHORED_VOLUME_PROFILE),
            ToolDef("F.Vol", DrawingToolType.FIXED_RANGE_VOLUME),
            ToolDef("Range", DrawingToolType.PRICE_RANGE),
            ToolDef("Rect",  DrawingToolType.RECTANGLE),
            ToolDef("Clear", null),
        )

        defs.forEach { def ->
            val btn = Button(this).apply {
                text = def.label
                textSize = 10f
                isAllCaps = false
                setTextColor(if (def.tool == DrawingToolType.NONE) c("#58A6FF") else c("#8B949E"))
                setBackgroundColor(Color.TRANSPARENT)
                minimumWidth = 0; minWidth = 0
                setPadding(dp(6), dp(2), dp(6), dp(2))
                tag = def.tool
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(1), 0, dp(1), 0) }
                setOnClickListener {
                    if (def.tool == null) clearDrawings()
                    else selectTool(def.tool)
                }
            }
            b.drawingToolsContainer.addView(btn)
        }
    }

    private fun selectTool(tool: DrawingToolType) {
        b.drawingOverlay.currentTool = tool
        updateToolbarHighlight(tool)

        val hint = when (tool) {
            DrawingToolType.HORIZONTAL_LINE         -> "Tap to place line"
            DrawingToolType.TRENDLINE               -> "Tap 2 points"
            DrawingToolType.FIB_RETRACEMENT         -> "Tap high, then low"
            DrawingToolType.SHORT_POSITION          -> "Tap entry → stop → target"
            DrawingToolType.LONG_POSITION           -> "Tap entry → stop → target"
            DrawingToolType.ANCHORED_VOLUME_PROFILE -> "Tap anchor bar"
            DrawingToolType.FIXED_RANGE_VOLUME      -> "Tap start → end"
            DrawingToolType.PRICE_RANGE             -> "Tap start → end"
            DrawingToolType.RECTANGLE               -> "Tap 2 corners"
            DrawingToolType.NONE                    -> null
        }
        b.tvDrawingHint.apply {
            if (hint != null) { text = hint; visibility = View.VISIBLE }
            else visibility = View.GONE
        }
    }

    private fun updateToolbarHighlight(active: DrawingToolType) {
        for (i in 0 until b.drawingToolsContainer.childCount) {
            val btn = b.drawingToolsContainer.getChildAt(i) as? Button ?: continue
            val t = btn.tag as? DrawingToolType
            btn.setTextColor(if (t == active) c("#58A6FF") else c("#8B949E"))
        }
    }

    private fun clearDrawings() {
        b.drawingOverlay.clearDrawings()
        DrawingPersistence.clear(this, currentMarket.instrument, currentTf.granularity)
        Toast.makeText(this, "Drawings cleared", Toast.LENGTH_SHORT).show()
    }

    private fun saveDrawings() =
        DrawingPersistence.save(this, currentMarket.instrument, currentTf.granularity, b.drawingOverlay.getDrawings())

    private fun loadDrawings() =
        b.drawingOverlay.setDrawings(DrawingPersistence.load(this, currentMarket.instrument, currentTf.granularity))

    // ── Market picker dialog ───────────────────────────────────

    private fun showMarketPicker() {
        val names = MARKETS.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(this, R.style.Theme_LiteCharts_Dialog)
            .setTitle("Select Market")
            .setSingleChoiceItems(names, MARKETS.indexOf(currentMarket)) { dlg, idx ->
                currentMarket = MARKETS[idx]
                b.btnMarketSelector.text = currentMarket.displayName
                dlg.dismiss()
                loadData()
            }.show()
    }

    private fun selectTf(tf: Timeframe) {
        currentTf = tf
        tfButtons.forEach { it.setTextColor(if (it.text == tf.label) c("#58A6FF") else c("#8B949E")) }
        loadData()
    }

    // ── Data loading ───────────────────────────────────────────

    private fun loadData() {
        scope.coroutineContext.cancelChildren()
        b.loadingIndicator.visibility = View.VISIBLE
        b.tvError.visibility = View.GONE

        scope.launch {
            val res = OandaApiService.fetchCandles(
                currentMarket.instrument,
                currentTf.granularity,
                currentTf.candleCount
            )
            b.loadingIndicator.visibility = View.GONE

            res.fold(
                onSuccess = { candles ->
                    currentCandles = candles
                    b.drawingOverlay.candles = candles
                    chart.setCandleData(candles)
                    if (dccEnabled) applyDcc()
                    loadDrawings()
                },
                onFailure = { err ->
                    b.tvError.text = err.message ?: "Failed to load data"
                    b.tvError.visibility = View.VISIBLE
                }
            )
        }
    }

    private fun applyDcc() =
        chart.setIndicator(DccIndicatorEngine.calculate(currentCandles))

    // ── Helpers ────────────────────────────────────────────────
    private fun c(hex: String) = Color.parseColor(hex)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
