package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ChartPreferencesStore
import com.example.data.OandaRepository
import com.example.data.StreamFeedEvent
import com.example.indicators.CustomPineIndicator
import com.example.indicators.DccMasterEngine
import com.example.indicators.DccMasterResult
import com.example.indicators.DccSettings
import com.example.indicators.MAX_CUSTOM_PINE_INDICATORS
import com.example.indicators.PineEvaluationResult
import com.example.indicators.PineScriptEngine
import com.example.model.Candle
import com.example.model.ChartAnchor
import com.example.model.ChartAppearance
import com.example.model.ChartDrawing
import com.example.model.DefaultWatchlist
import com.example.model.DrawingTool
import com.example.model.LiveQuote
import com.example.model.MarketInstrument
import com.example.model.StreamStatus
import com.example.model.ThemePreset
import com.example.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ChartUiState(
    val watchlist: List<MarketInstrument> = DefaultWatchlist,
    val selectedInstrument: MarketInstrument = DefaultWatchlist.first(),
    val selectedTimeframe: Timeframe = Timeframe.M5,
    val candles: List<Candle> = emptyList(),
    val dccSettings: DccSettings = DccSettings(),
    val dccResult: DccMasterResult = DccMasterResult(),
    val customIndicators: List<CustomPineIndicator> = emptyList(),
    val customResults: List<PineEvaluationResult> = emptyList(),
    val appearance: ChartAppearance = ChartAppearance(),
    val liveQuote: LiveQuote? = null,
    val streamStatus: StreamStatus = StreamStatus.OFFLINE,
    val isLoading: Boolean = true,
    val isApiKeyMissing: Boolean = false,
    val errorMessage: String? = null,
    val drawings: List<ChartDrawing> = emptyList(),
    val activeTool: DrawingTool = DrawingTool.CURSOR,
    val pendingAnchor: ChartAnchor? = null,
    val selectedDrawingId: String? = null,
    val lastClearedDrawings: List<ChartDrawing>? = null,
    val crosshairBarIndex: Int? = null,
    val viewportResetSignal: Int = 0
)

class ChartViewModel(
    private val repository: OandaRepository,
    private val preferencesStore: ChartPreferencesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChartUiState())
    val uiState: StateFlow<ChartUiState> = _uiState.asStateFlow()

    private var allDrawingsByKey: Map<String, List<ChartDrawing>> = emptyMap()
    private var htfCandlesByTf: Map<Timeframe, List<Candle>> = emptyMap()
    private var dailyCandlesForLevels: List<Candle> = emptyList()
    private var candlesJob: Job? = null
    private var streamJob: Job? = null
    private var recomputeJob: Job? = null

    init {
        viewModelScope.launch {
            val savedDcc = preferencesStore.dccSettingsFlow.first()
            val savedAppearance = preferencesStore.appearanceFlow.first()
            val savedDrawingsMap = preferencesStore.drawingsMapFlow.first()
            val savedCustomIndicators = preferencesStore.customIndicatorsFlow.first()
            val savedInstrumentId = preferencesStore.selectedInstrumentFlow.first()
            val savedTimeframe = preferencesStore.selectedTimeframeFlow.first()

            allDrawingsByKey = savedDrawingsMap
            val initialInstrument = DefaultWatchlist.firstOrNull { it.id == savedInstrumentId }
                ?: DefaultWatchlist.first()
            val initialKey = ChartPreferencesStore.chartStorageKey(initialInstrument.id, savedTimeframe)
            val keyMissing = !repository.isApiKeyConfigured()

            _uiState.update {
                it.copy(
                    selectedInstrument = initialInstrument,
                    selectedTimeframe = savedTimeframe,
                    dccSettings = savedDcc,
                    customIndicators = savedCustomIndicators,
                    appearance = savedAppearance,
                    drawings = savedDrawingsMap[initialKey].orEmpty(),
                    isApiKeyMissing = keyMissing,
                    isLoading = !keyMissing,
                    streamStatus = if (keyMissing) StreamStatus.OFFLINE else StreamStatus.CONNECTING
                )
            }

            if (!keyMissing) {
                loadChartAndStream(initialInstrument, savedTimeframe)
            }
        }
    }

    fun selectInstrument(instrument: MarketInstrument) {
        val current = _uiState.value
        if (current.selectedInstrument.id == instrument.id) return

        val key = ChartPreferencesStore.chartStorageKey(instrument.id, current.selectedTimeframe)
        htfCandlesByTf = emptyMap()
        dailyCandlesForLevels = emptyList()
        _uiState.update {
            it.copy(
                selectedInstrument = instrument,
                drawings = allDrawingsByKey[key].orEmpty(),
                pendingAnchor = null,
                selectedDrawingId = null,
                crosshairBarIndex = null,
                liveQuote = null,
                errorMessage = null,
                lastClearedDrawings = null,
                viewportResetSignal = it.viewportResetSignal + 1
            )
        }
        viewModelScope.launch {
            preferencesStore.saveSelection(instrument.id, current.selectedTimeframe)
        }
        loadChartAndStream(instrument, current.selectedTimeframe)
    }

    fun selectTimeframe(timeframe: Timeframe) {
        val current = _uiState.value
        if (current.selectedTimeframe == timeframe) return

        val key = ChartPreferencesStore.chartStorageKey(current.selectedInstrument.id, timeframe)
        _uiState.update {
            it.copy(
                selectedTimeframe = timeframe,
                drawings = allDrawingsByKey[key].orEmpty(),
                pendingAnchor = null,
                selectedDrawingId = null,
                crosshairBarIndex = null,
                errorMessage = null,
                lastClearedDrawings = null,
                viewportResetSignal = it.viewportResetSignal + 1
            )
        }
        viewModelScope.launch {
            preferencesStore.saveSelection(current.selectedInstrument.id, timeframe)
        }
        loadCandlesAndHtf(current.selectedInstrument, timeframe, refreshHtf = htfCandlesByTf.isEmpty())
    }

    fun reloadChart() {
        val current = _uiState.value
        val keyMissing = !repository.isApiKeyConfigured()
        _uiState.update { it.copy(isApiKeyMissing = keyMissing, errorMessage = null) }
        if (!keyMissing) {
            loadChartAndStream(current.selectedInstrument, current.selectedTimeframe)
        }
    }

    private fun requiredHtfTimeframes(settings: DccSettings): Set<Timeframe> = setOf(
        Timeframe.D1,
        DccMasterEngine.timeframeCodeToOandaTimeframe(settings.htfEmaTF),
        DccMasterEngine.timeframeCodeToOandaTimeframe(settings.adxTF),
        DccMasterEngine.timeframeCodeToOandaTimeframe(settings.atrTF),
        DccMasterEngine.timeframeCodeToOandaTimeframe(settings.tsmTf1),
        DccMasterEngine.timeframeCodeToOandaTimeframe(settings.tsmTf2),
        DccMasterEngine.timeframeCodeToOandaTimeframe(settings.tsmTf3)
    )

    private fun loadChartAndStream(instrument: MarketInstrument, timeframe: Timeframe) {
        loadCandlesAndHtf(instrument, timeframe, refreshHtf = true)
        startPricingStream(instrument)
    }

    private fun loadCandlesAndHtf(
        instrument: MarketInstrument,
        timeframe: Timeframe,
        refreshHtf: Boolean
    ) {
        candlesJob?.cancel()
        candlesJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val settings = _uiState.value.dccSettings
            val neededHtfs = requiredHtfTimeframes(settings)

            val mainDeferred = async {
                repository.fetchCompletedCandles(instrument.id, timeframe, count = 420)
            }
            val htfDeferred = async {
                if (refreshHtf || !htfCandlesByTf.keys.containsAll(neededHtfs)) {
                    repository.fetchHtfCandlesBundle(instrument.id, neededHtfs, count = 180)
                } else {
                    htfCandlesByTf
                }
            }
            val dailyDeferred = async {
                repository.fetchDailyCandlesForLevels(instrument.id, count = 180)
            }

            val mainResult = mainDeferred.await()
            val htfBundle = htfDeferred.await()
            val dailyResult = dailyDeferred.await()

            if (htfBundle.isNotEmpty()) {
                htfCandlesByTf = htfBundle
            }
            dailyResult.onSuccess { fetched ->
                dailyCandlesForLevels = fetched
            }

            mainResult.fold(
                onSuccess = { candles ->
                    val (dcc, customRes) = computeAllIndicators(
                        candles = candles,
                        timeframe = timeframe,
                        instrument = instrument,
                        dccSettings = _uiState.value.dccSettings,
                        customIndicators = _uiState.value.customIndicators,
                        htfMap = htfCandlesByTf,
                        dailyCandles = dailyCandlesForLevels
                    )
                    _uiState.update { state ->
                        state.copy(
                            candles = candles,
                            dccResult = dcc,
                            customResults = customRes,
                            isLoading = false,
                            errorMessage = null,
                            viewportResetSignal = state.viewportResetSignal + 1
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Unable to fetch OANDA candles."
                        )
                    }
                }
            )
        }
    }

    private suspend fun computeAllIndicators(
        candles: List<Candle>,
        timeframe: Timeframe,
        instrument: MarketInstrument,
        dccSettings: DccSettings,
        customIndicators: List<CustomPineIndicator>,
        htfMap: Map<Timeframe, List<Candle>>,
        dailyCandles: List<Candle>
    ): Pair<DccMasterResult, List<PineEvaluationResult>> = withContext(Dispatchers.Default) {
        val dcc = DccMasterEngine.calculate(
            candles = candles,
            chartTimeframe = timeframe,
            instrument = instrument,
            settings = dccSettings,
            htfCandles = htfMap,
            dailyCandlesForLevels = dailyCandles
        )
        val customResults = customIndicators
            .filter { it.enabled }
            .map { PineScriptEngine.evaluate(it, candles) }
        dcc to customResults
    }

    private fun scheduleRecompute() {
        recomputeJob?.cancel()
        recomputeJob = viewModelScope.launch {
            val st = _uiState.value
            if (st.candles.isEmpty()) return@launch
            val (dcc, customRes) = computeAllIndicators(
                candles = st.candles,
                timeframe = st.selectedTimeframe,
                instrument = st.selectedInstrument,
                dccSettings = st.dccSettings,
                customIndicators = st.customIndicators,
                htfMap = htfCandlesByTf,
                dailyCandles = dailyCandlesForLevels
            )
            _uiState.update { it.copy(dccResult = dcc, customResults = customRes) }
        }
    }

    private fun startPricingStream(instrument: MarketInstrument) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            repository.observeLivePricing(instrument.id).collect { event ->
                when (event) {
                    is StreamFeedEvent.Connecting -> {
                        _uiState.update { it.copy(streamStatus = StreamStatus.CONNECTING) }
                    }
                    is StreamFeedEvent.Connected -> {
                        _uiState.update { it.copy(streamStatus = StreamStatus.LIVE) }
                    }
                    is StreamFeedEvent.Reconnecting -> {
                        _uiState.update { it.copy(streamStatus = StreamStatus.RECONNECTING) }
                    }
                    is StreamFeedEvent.Tick -> {
                        applyLiveTick(event)
                    }
                }
            }
        }
    }

    private fun applyLiveTick(tick: StreamFeedEvent.Tick) {
        val current = _uiState.value
        if (tick.instrument != current.selectedInstrument.id) return

        val mid = (tick.bid + tick.ask) * 0.5
        val spread = max(0.0, tick.ask - tick.bid)
        val prevMid = current.liveQuote?.mid
        val isUp = if (prevMid != null && mid != prevMid) mid > prevMid else current.liveQuote?.isUpTick

        val updatedQuote = LiveQuote(
            instrument = tick.instrument,
            bid = tick.bid,
            ask = tick.ask,
            mid = mid,
            spread = spread,
            timeEpochSec = tick.timeEpochSec,
            isUpTick = isUp
        )

        if (current.candles.isEmpty()) {
            _uiState.update { it.copy(liveQuote = updatedQuote, streamStatus = StreamStatus.LIVE) }
            return
        }

        val duration = current.selectedTimeframe.durationSec
        val bucketStart = (tick.timeEpochSec / duration) * duration
        val mutableCandles = current.candles.toMutableList()
        val last = mutableCandles.last()

        if (!last.isComplete && last.timeEpochSec == bucketStart) {
            mutableCandles[mutableCandles.lastIndex] = last.copy(
                high = max(last.high, mid),
                low = min(last.low, mid),
                close = mid,
                volume = last.volume + 1L
            )
        } else if (bucketStart > last.timeEpochSec) {
            if (!last.isComplete) {
                mutableCandles[mutableCandles.lastIndex] = last.copy(isComplete = true)
            }
            mutableCandles.add(
                Candle(
                    timeEpochSec = bucketStart,
                    open = mid,
                    high = mid,
                    low = mid,
                    close = mid,
                    volume = 1L,
                    isComplete = false
                )
            )
            if (mutableCandles.size > 460) {
                mutableCandles.removeAt(0)
            }
        } else if (last.timeEpochSec == bucketStart) {
            mutableCandles[mutableCandles.lastIndex] = last.copy(
                high = max(last.high, mid),
                low = min(last.low, mid),
                close = mid,
                volume = last.volume + 1L,
                isComplete = false
            )
        }

        _uiState.update {
            it.copy(
                candles = mutableCandles,
                liveQuote = updatedQuote,
                streamStatus = StreamStatus.LIVE
            )
        }
        scheduleRecompute()
    }

    // --- DCC Master Indicator Settings ---

    fun toggleDccOverlay() {
        updateDccSettings { it.copy(masterEnabled = !it.masterEnabled) }
    }

    fun updateDccSettings(transform: (DccSettings) -> DccSettings) {
        val prev = _uiState.value.dccSettings
        val updated = transform(prev)
        _uiState.update { it.copy(dccSettings = updated) }
        viewModelScope.launch {
            preferencesStore.saveDccSettings(updated)
            val neededHtfs = requiredHtfTimeframes(updated)
            if (!htfCandlesByTf.keys.containsAll(neededHtfs) && repository.isApiKeyConfigured()) {
                val extra = repository.fetchHtfCandlesBundle(
                    _uiState.value.selectedInstrument.id,
                    neededHtfs - htfCandlesByTf.keys,
                    count = 180
                )
                if (extra.isNotEmpty()) {
                    htfCandlesByTf = htfCandlesByTf + extra
                }
            }
            scheduleRecompute()
        }
    }

    fun resetDccSettings() {
        updateDccSettings { DccSettings() }
    }

    // --- Custom Pine Script Indicators (Up to 3 Extra Slots) ---

    fun addCustomIndicator(rawName: String, rawCode: String): Boolean {
        val current = _uiState.value.customIndicators
        if (current.size >= MAX_CUSTOM_PINE_INDICATORS) return false
        val header = PineScriptEngine.parseHeader(rawCode, rawName.ifBlank { "Pine ${current.size + 1}" })
        val item = CustomPineIndicator(
            id = UUID.randomUUID().toString(),
            name = rawName.trim().ifBlank { header.title },
            code = rawCode.trim(),
            enabled = true,
            isOverlay = header.isOverlay
        )
        val updated = current + item
        _uiState.update { it.copy(customIndicators = updated) }
        viewModelScope.launch {
            preferencesStore.saveCustomIndicators(updated)
        }
        scheduleRecompute()
        return true
    }

    fun updateCustomIndicator(id: String, rawName: String, rawCode: String) {
        val current = _uiState.value.customIndicators
        val header = PineScriptEngine.parseHeader(rawCode, rawName.ifBlank { "Custom Pine" })
        val updated = current.map { item ->
            if (item.id == id) {
                item.copy(
                    name = rawName.trim().ifBlank { header.title },
                    code = rawCode.trim(),
                    isOverlay = header.isOverlay
                )
            } else item
        }
        _uiState.update { it.copy(customIndicators = updated) }
        viewModelScope.launch {
            preferencesStore.saveCustomIndicators(updated)
        }
        scheduleRecompute()
    }

    fun toggleCustomIndicator(id: String) {
        val updated = _uiState.value.customIndicators.map { item ->
            if (item.id == id) item.copy(enabled = !item.enabled) else item
        }
        _uiState.update { it.copy(customIndicators = updated) }
        viewModelScope.launch {
            preferencesStore.saveCustomIndicators(updated)
        }
        scheduleRecompute()
    }

    fun deleteCustomIndicator(id: String) {
        val updated = _uiState.value.customIndicators.filterNot { it.id == id }
        _uiState.update { it.copy(customIndicators = updated) }
        viewModelScope.launch {
            preferencesStore.saveCustomIndicators(updated)
        }
        scheduleRecompute()
    }

    // --- Appearance Customization ---

    fun applyThemePreset(preset: ThemePreset) {
        val next = ChartAppearance.fromPreset(preset)
        _uiState.update { it.copy(appearance = next) }
        viewModelScope.launch {
            preferencesStore.saveAppearance(next)
        }
    }

    fun updateAppearance(transform: (ChartAppearance) -> ChartAppearance) {
        val next = transform(_uiState.value.appearance)
        _uiState.update { it.copy(appearance = next) }
        viewModelScope.launch {
            preferencesStore.saveAppearance(next)
        }
    }

    fun resetAppearanceToDefault() {
        applyThemePreset(ThemePreset.MIDNIGHT_SLATE)
    }

    // --- Drawing & Measurement Tools ---

    fun selectDrawingTool(tool: DrawingTool) {
        _uiState.update {
            it.copy(
                activeTool = if (it.activeTool == tool && tool != DrawingTool.CURSOR) {
                    DrawingTool.CURSOR
                } else {
                    tool
                },
                pendingAnchor = null,
                crosshairBarIndex = null
            )
        }
    }

    fun cancelPendingDrawing() {
        _uiState.update {
            it.copy(
                pendingAnchor = null,
                activeTool = DrawingTool.CURSOR
            )
        }
    }

    fun onChartTapForDrawing(timeEpochSec: Long, price: Double) {
        val state = _uiState.value
        val tool = state.activeTool
        if (tool == DrawingTool.CURSOR) {
            _uiState.update { it.copy(crosshairBarIndex = null) }
            return
        }

        if (tool.requiredTaps == 1) {
            val drawing = ChartDrawing(
                id = UUID.randomUUID().toString(),
                type = tool.name,
                startTime = timeEpochSec,
                startPrice = price
            )
            addDrawingAndPersist(drawing)
            return
        }

        val first = state.pendingAnchor
        if (first == null) {
            _uiState.update { it.copy(pendingAnchor = ChartAnchor(timeEpochSec, price)) }
        } else {
            val duration = state.selectedTimeframe.durationSec
            val effectiveEndTime = if (abs(timeEpochSec - first.timeEpochSec) < duration * 2L &&
                (tool == DrawingTool.LONG_POSITION || tool == DrawingTool.SHORT_POSITION)
            ) {
                first.timeEpochSec + duration * 14L
            } else {
                timeEpochSec
            }

            val drawing = when (tool) {
                DrawingTool.LONG_POSITION -> {
                    val entry = first.price
                    val delta = abs(price - entry).coerceAtLeast(entry * 0.0015)
                    val target = if (price >= entry) price.coerceAtLeast(entry + delta) else entry + delta * 2.0
                    val stop = if (price >= entry) entry - (target - entry) * 0.5 else price
                    ChartDrawing(
                        id = UUID.randomUUID().toString(),
                        type = tool.name,
                        startTime = first.timeEpochSec,
                        startPrice = entry,
                        endTime = effectiveEndTime,
                        endPrice = target,
                        targetPrice = target,
                        stopPrice = stop
                    )
                }
                DrawingTool.SHORT_POSITION -> {
                    val entry = first.price
                    val delta = abs(price - entry).coerceAtLeast(entry * 0.0015)
                    val target = if (price <= entry) price.coerceAtMost(entry - delta) else entry - delta * 2.0
                    val stop = if (price <= entry) entry + (entry - target) * 0.5 else price
                    ChartDrawing(
                        id = UUID.randomUUID().toString(),
                        type = tool.name,
                        startTime = first.timeEpochSec,
                        startPrice = entry,
                        endTime = effectiveEndTime,
                        endPrice = target,
                        targetPrice = target,
                        stopPrice = stop
                    )
                }
                else -> {
                    ChartDrawing(
                        id = UUID.randomUUID().toString(),
                        type = tool.name,
                        startTime = first.timeEpochSec,
                        startPrice = first.price,
                        endTime = effectiveEndTime,
                        endPrice = price
                    )
                }
            }
            addDrawingAndPersist(drawing)
        }
    }

    private fun addDrawingAndPersist(drawing: ChartDrawing) {
        val state = _uiState.value
        val updated = state.drawings + drawing
        persistDrawings(state.selectedInstrument.id, state.selectedTimeframe, updated)
        _uiState.update {
            it.copy(
                drawings = updated,
                pendingAnchor = null,
                selectedDrawingId = drawing.id,
                activeTool = DrawingTool.CURSOR
            )
        }
    }

    fun undoLastDrawing() {
        val state = _uiState.value
        if (state.pendingAnchor != null) {
            _uiState.update { it.copy(pendingAnchor = null) }
            return
        }
        if (state.drawings.isEmpty()) return
        val updated = state.drawings.dropLast(1)
        persistDrawings(state.selectedInstrument.id, state.selectedTimeframe, updated)
        _uiState.update {
            it.copy(
                drawings = updated,
                selectedDrawingId = updated.lastOrNull()?.id
            )
        }
    }

    fun clearDrawings(): Boolean {
        val state = _uiState.value
        if (state.drawings.isEmpty() && state.pendingAnchor == null) return false
        val backup = state.drawings
        persistDrawings(state.selectedInstrument.id, state.selectedTimeframe, emptyList())
        _uiState.update {
            it.copy(
                drawings = emptyList(),
                pendingAnchor = null,
                selectedDrawingId = null,
                lastClearedDrawings = backup.takeIf { list -> list.isNotEmpty() }
            )
        }
        return backup.isNotEmpty()
    }

    fun restoreClearedDrawings() {
        val state = _uiState.value
        val backup = state.lastClearedDrawings ?: return
        persistDrawings(state.selectedInstrument.id, state.selectedTimeframe, backup)
        _uiState.update {
            it.copy(
                drawings = backup,
                lastClearedDrawings = null
            )
        }
    }

    fun setCrosshairBarIndex(index: Int?) {
        _uiState.update { it.copy(crosshairBarIndex = index) }
    }

    private fun persistDrawings(
        instrumentId: String,
        timeframe: Timeframe,
        drawings: List<ChartDrawing>
    ) {
        val key = ChartPreferencesStore.chartStorageKey(instrumentId, timeframe)
        allDrawingsByKey = allDrawingsByKey.toMutableMap().apply {
            if (drawings.isEmpty()) remove(key) else put(key, drawings)
        }
        viewModelScope.launch {
            preferencesStore.saveDrawingsForChart(instrumentId, timeframe, drawings)
        }
    }

    class Factory(
        private val repository: OandaRepository,
        private val preferencesStore: ChartPreferencesStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChartViewModel(repository, preferencesStore) as T
        }
    }
}

fun formatPrice(value: Double, decimals: Int): String =
    String.format(java.util.Locale.US, "%.${decimals}f", value)
