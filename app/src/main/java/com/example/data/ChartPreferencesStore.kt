package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.indicators.CustomPineIndicator
import com.example.indicators.DccSettings
import com.example.indicators.MAX_CUSTOM_PINE_INDICATORS
import com.example.indicators.StoredCustomIndicators
import com.example.model.ChartAppearance
import com.example.model.ChartDrawing
import com.example.model.DefaultWatchlist
import com.example.model.StoredDrawingsMap
import com.example.model.Timeframe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.chartPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dcc_charting_prefs"
)

class ChartPreferencesStore(private val context: Context) {

    private val moshi = OandaNetworkFactory.moshi
    private val dccAdapter = moshi.adapter(DccSettings::class.java)
    private val appearanceAdapter = moshi.adapter(ChartAppearance::class.java)
    private val drawingsMapAdapter = moshi.adapter(StoredDrawingsMap::class.java)
    private val customIndicatorsAdapter = moshi.adapter(StoredCustomIndicators::class.java)

    private object Keys {
        val DCC_SETTINGS = stringPreferencesKey("dcc_master_settings_v255_json")
        val APPEARANCE = stringPreferencesKey("chart_appearance_json")
        val DRAWINGS_MAP = stringPreferencesKey("drawings_by_chart_json")
        val CUSTOM_INDICATORS = stringPreferencesKey("custom_pine_indicators_json")
        val SELECTED_INSTRUMENT = stringPreferencesKey("selected_instrument")
        val SELECTED_TIMEFRAME = stringPreferencesKey("selected_timeframe")
    }

    val dccSettingsFlow: Flow<DccSettings> = context.chartPrefsDataStore.data.map { prefs ->
        prefs[Keys.DCC_SETTINGS]?.let { json ->
            runCatching { dccAdapter.fromJson(json) }.getOrNull()
        } ?: DccSettings()
    }

    val appearanceFlow: Flow<ChartAppearance> = context.chartPrefsDataStore.data.map { prefs ->
        prefs[Keys.APPEARANCE]?.let { json ->
            runCatching { appearanceAdapter.fromJson(json) }.getOrNull()
        } ?: ChartAppearance()
    }

    val drawingsMapFlow: Flow<Map<String, List<ChartDrawing>>> =
        context.chartPrefsDataStore.data.map { prefs ->
            prefs[Keys.DRAWINGS_MAP]?.let { json ->
                runCatching { drawingsMapAdapter.fromJson(json)?.byChartKey }.getOrNull()
            } ?: emptyMap()
        }

    val customIndicatorsFlow: Flow<List<CustomPineIndicator>> =
        context.chartPrefsDataStore.data.map { prefs ->
            prefs[Keys.CUSTOM_INDICATORS]?.let { json ->
                runCatching { customIndicatorsAdapter.fromJson(json)?.items }.getOrNull()
            }.orEmpty().take(MAX_CUSTOM_PINE_INDICATORS)
        }

    val selectedInstrumentFlow: Flow<String> = context.chartPrefsDataStore.data.map { prefs ->
        val stored = prefs[Keys.SELECTED_INSTRUMENT]
        if (stored != null && DefaultWatchlist.any { it.id == stored }) {
            stored
        } else {
            DefaultWatchlist.first().id
        }
    }

    val selectedTimeframeFlow: Flow<Timeframe> = context.chartPrefsDataStore.data.map { prefs ->
        Timeframe.fromNameOrDefault(prefs[Keys.SELECTED_TIMEFRAME])
    }

    suspend fun saveDccSettings(settings: DccSettings) {
        val json = dccAdapter.toJson(settings)
        context.chartPrefsDataStore.edit { prefs ->
            prefs[Keys.DCC_SETTINGS] = json
        }
    }

    suspend fun saveAppearance(appearance: ChartAppearance) {
        val json = appearanceAdapter.toJson(appearance)
        context.chartPrefsDataStore.edit { prefs ->
            prefs[Keys.APPEARANCE] = json
        }
    }

    suspend fun saveCustomIndicators(indicators: List<CustomPineIndicator>) {
        val capped = indicators.take(MAX_CUSTOM_PINE_INDICATORS)
        val json = customIndicatorsAdapter.toJson(StoredCustomIndicators(capped))
        context.chartPrefsDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_INDICATORS] = json
        }
    }

    suspend fun saveDrawingsForChart(
        instrumentId: String,
        timeframe: Timeframe,
        drawings: List<ChartDrawing>
    ) {
        val key = chartStorageKey(instrumentId, timeframe)
        context.chartPrefsDataStore.edit { prefs ->
            val existing = prefs[Keys.DRAWINGS_MAP]?.let { json ->
                runCatching { drawingsMapAdapter.fromJson(json)?.byChartKey }.getOrNull()
            } ?: emptyMap()
            val updated = existing.toMutableMap()
            if (drawings.isEmpty()) {
                updated.remove(key)
            } else {
                updated[key] = drawings
            }
            prefs[Keys.DRAWINGS_MAP] = drawingsMapAdapter.toJson(StoredDrawingsMap(updated))
        }
    }

    suspend fun saveSelection(instrumentId: String, timeframe: Timeframe) {
        context.chartPrefsDataStore.edit { prefs ->
            prefs[Keys.SELECTED_INSTRUMENT] = instrumentId
            prefs[Keys.SELECTED_TIMEFRAME] = timeframe.name
        }
    }

    companion object {
        fun chartStorageKey(instrumentId: String, timeframe: Timeframe): String =
            "${instrumentId}_${timeframe.name}"
    }
}
