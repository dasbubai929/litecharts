package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.indicators.DccSettings
import com.example.ui.theme.MonoPriceSmallStyle
import java.util.Locale
import kotlin.math.roundToInt

private val settingsTabs = listOf(
    "Core & Risk",
    "Signals & P3",
    "Sessions & Levels",
    "VP & S/R",
    "TSM & Table"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DccSettingsSheet(
    settings: DccSettings,
    onUpdateSettings: ((DccSettings) -> DccSettings) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .testTag("dcc_settings_sheet")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "DCC Master Settings (v2.5.5)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "DCC+TSM+VPSR+SQZ — recalculates live",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_dcc_settings_button")
                ) {
                    Text(stringResource(R.string.done_action))
                }
            }

            // Category Tabs
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                settingsTabs.forEachIndexed { idx, title ->
                    val active = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedTab = idx }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("dcc_tab_$idx")
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> CoreAndRiskSection(settings, onUpdateSettings)
                    1 -> SignalsAndP3Section(settings, onUpdateSettings)
                    2 -> SessionsAndLevelsSection(settings, onUpdateSettings)
                    3 -> VpAndSrSection(settings, onUpdateSettings)
                    4 -> TsmAndTableSection(settings, onUpdateSettings)
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onResetDefaults,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dcc_reset_defaults_button")
                ) {
                    Text("Reset DCC Master Defaults")
                }
            }
        }
    }
}

@Composable
private fun CoreAndRiskSection(
    s: DccSettings,
    onUpdate: ((DccSettings) -> DccSettings) -> Unit
) {
    SectionHeader("01 • EMA / Trend")
    DccSwitch("Enable Chart EMAs", s.enableEMA) { v -> onUpdate { it.copy(enableEMA = v) } }
    DccSwitch("Show Fast EMA", s.showFastEma) { v -> onUpdate { it.copy(showFastEma = v) } }
    DccIntSlider("Fast EMA Length", s.fastLen, 2, 100) { v -> onUpdate { it.copy(fastLen = v) } }
    DccSwitch("Show Slow EMA", s.showSlowEma) { v -> onUpdate { it.copy(showSlowEma = v) } }
    DccIntSlider("Slow EMA Length", s.slowLen, 5, 200) { v -> onUpdate { it.copy(slowLen = v) } }

    SectionHeader("02 • VWAP")
    DccSwitch("Enable VWAP", s.enableVwap) { v -> onUpdate { it.copy(enableVwap = v) } }
    DccChoiceChips("VWAP Anchor Mode", listOf("Daily", "Sessions"), s.vwapMode) { v ->
        onUpdate { it.copy(vwapMode = v) }
    }

    SectionHeader("03 • HTF EMA Level & Bias")
    DccSwitch("Enable HTF EMA 1", s.enableHTFEMA) { v -> onUpdate { it.copy(enableHTFEMA = v) } }
    DccChoiceChips("HTF EMA Timeframe", listOf("15", "30", "60", "240", "1D"), s.htfEmaTF) { v ->
        onUpdate { it.copy(htfEmaTF = v) }
    }
    DccIntSlider("HTF EMA 1 Length", s.htfEmaLen1, 5, 200) { v -> onUpdate { it.copy(htfEmaLen1 = v) } }
    DccChoiceChips(
        "HTF EMA Mode",
        listOf("Smoothed Approximation", "Actual HTF EMA"),
        s.htfEmaDisplayMode
    ) { v -> onUpdate { it.copy(htfEmaDisplayMode = v) } }

    SectionHeader("04 • ADX & 05 • ATR Stop Loss")
    DccSwitch("Enable ADX", s.enableADX) { v -> onUpdate { it.copy(enableADX = v) } }
    DccIntSlider("ADX DI Length", s.adxLen, 5, 30) { v -> onUpdate { it.copy(adxLen = v) } }
    DccSwitch("Enable ATR Stop Loss", s.enableATR) { v -> onUpdate { it.copy(enableATR = v) } }
    DccIntSlider("ATR Length", s.atrLen, 5, 30) { v -> onUpdate { it.copy(atrLen = v) } }
    DccFloatSlider("ATR × Stop Loss", s.atrMult, 0.2, 3.0, 0.1) { v -> onUpdate { it.copy(atrMult = v) } }
    DccSwitch("Show Live SL Price", s.showLiveSL) { v -> onUpdate { it.copy(showLiveSL = v) } }
}

@Composable
private fun SignalsAndP3Section(
    s: DccSettings,
    onUpdate: ((DccSettings) -> DccSettings) -> Unit
) {
    SectionHeader("11 • EMA Crossover Markers (❌)")
    DccSwitch("Show EMA Crossover Markers", s.showCrossMarkers) { v -> onUpdate { it.copy(showCrossMarkers = v) } }
    DccSwitch("Filter ❌ by VWAP Confluence", s.crossUseVwap) { v -> onUpdate { it.copy(crossUseVwap = v) } }
    DccSwitch("Filter ❌ by HTF EMA 1 Bias", s.crossUseHtfEma) { v -> onUpdate { it.copy(crossUseHtfEma = v) } }
    DccSwitch("Filter ❌ by ADX Threshold", s.crossUseAdx) { v -> onUpdate { it.copy(crossUseAdx = v) } }
    DccSwitch("Filter ❌ by TSM Min Score", s.crossUseTsm) { v -> onUpdate { it.copy(crossUseTsm = v) } }

    SectionHeader("12 • Pullback & P3 Re-Entry Signals")
    DccSwitch("Enable Pullback Entry Signals", s.enablePullbackEntry) { v -> onUpdate { it.copy(enablePullbackEntry = v) } }
    DccSwitch("Tie Pullback to Valid Cross (❌)", s.tiePbToValidCross) { v -> onUpdate { it.copy(tiePbToValidCross = v) } }
    DccChoiceChips("Pullback Target EMA", listOf("Fast EMA", "Slow EMA"), s.pbTargetEma) { v ->
        onUpdate { it.copy(pbTargetEma = v) }
    }
    DccChoiceChips("Touch Mode", listOf("Wick or Close Touch", "Close Touch / Cross"), s.pbTouchMode) { v ->
        onUpdate { it.copy(pbTouchMode = v) }
    }
    DccChoiceChips("Breakout Trigger", listOf("Candle Close", "Intrabar Wick/Price"), s.pbBreakTrigger) { v ->
        onUpdate { it.copy(pbBreakTrigger = v) }
    }
    DccSwitch("Show Pullback Swing Lines", s.showPbLines) { v -> onUpdate { it.copy(showPbLines = v) } }
    DccSwitch("Show Historical Pullback Lines", s.showHistPbLines) { v -> onUpdate { it.copy(showHistPbLines = v) } }

    DccSwitch("Enable P3 Re-Entry Setup", s.enableP3ReEntry) { v -> onUpdate { it.copy(enableP3ReEntry = v) } }
    DccIntSlider("P3 Max Breakout Bars", s.p3BreakoutBars, 1, 10) { v -> onUpdate { it.copy(p3BreakoutBars = v) } }
    DccIntSlider("P3 Min Distance (bars)", s.p3MinDistBars, 1, 50) { v -> onUpdate { it.copy(p3MinDistBars = v) } }

    SectionHeader("Entry Confluence Filters")
    DccSwitch("Entry: Require VWAP Confluence", s.sigUseVwap) { v -> onUpdate { it.copy(sigUseVwap = v) } }
    DccSwitch("Entry: Require HTF EMA 1 Bias", s.sigUseHtfEma) { v -> onUpdate { it.copy(sigUseHtfEma = v) } }
    DccSwitch("Entry: Require ADX Threshold", s.sigUseAdx) { v -> onUpdate { it.copy(sigUseAdx = v) } }
    DccFloatSlider("Min ADX Threshold", s.sigMinAdx, 5.0, 50.0, 1.0) { v -> onUpdate { it.copy(sigMinAdx = v) } }
    DccSwitch("Entry: Require TSM Min Score", s.sigUseTsm) { v -> onUpdate { it.copy(sigUseTsm = v) } }
    DccFloatSlider("Min TSM Score (%)", s.sigMinTsmScore, 5.0, 80.0, 5.0) { v -> onUpdate { it.copy(sigMinTsmScore = v) } }
}

@Composable
private fun SessionsAndLevelsSection(
    s: DccSettings,
    onUpdate: ((DccSettings) -> DccSettings) -> Unit
) {
    SectionHeader("07 • Trading Sessions & 08 • Range Boxes")
    DccSwitch("Enable Trading Sessions", s.enableSessions) { v -> onUpdate { it.copy(enableSessions = v) } }
    DccSwitch("Tokyo (09:00-18:00 JST)", s.enableTokyo) { v -> onUpdate { it.copy(enableTokyo = v) } }
    DccSwitch("London (08:00-17:00 LDN)", s.enableLondon) { v -> onUpdate { it.copy(enableLondon = v) } }
    DccSwitch("New York (08:00-17:00 EST)", s.enableNY) { v -> onUpdate { it.copy(enableNY = v) } }
    DccSwitch("Sydney (08:00-17:00 SYD)", s.enableSydney) { v -> onUpdate { it.copy(enableSydney = v) } }
    DccSwitch("Enable Session Range Boxes", s.enableRangeBoxes) { v -> onUpdate { it.copy(enableRangeBoxes = v) } }
    DccSwitch("Show Range Outline", s.showRangeOutline) { v -> onUpdate { it.copy(showRangeOutline = v) } }
    DccSwitch("Show Session Labels", s.showRangeLabel) { v -> onUpdate { it.copy(showRangeLabel = v) } }
    DccIntSlider("Range Fill Transparency", s.rangeTransparency, 60, 98) { v -> onUpdate { it.copy(rangeTransparency = v) } }

    SectionHeader("09 • Key Levels (DO / PDH / PDL)")
    DccSwitch("Enable Key Levels", s.enableLevels) { v -> onUpdate { it.copy(enableLevels = v) } }
    DccSwitch("Show Daily Open (DO)", s.showDailyOpen) { v -> onUpdate { it.copy(showDailyOpen = v) } }
    DccChoiceChips("DO Style", listOf("Solid", "Dashed", "Dotted"), s.styDO) { v -> onUpdate { it.copy(styDO = v) } }
    DccSwitch("Show Prev Day High/Low (PDH/PDL)", s.showPDLvl) { v -> onUpdate { it.copy(showPDLvl = v) } }
    DccChoiceChips("PDH/PDL Style", listOf("Solid", "Dashed", "Dotted"), s.styPDLvl) { v -> onUpdate { it.copy(styPDLvl = v) } }
}

@Composable
private fun VpAndSrSection(
    s: DccSettings,
    onUpdate: ((DccSettings) -> DccSettings) -> Unit
) {
    SectionHeader("13 • Large Rolling Volume Profile")
    DccSwitch("Enable Volume Profile", s.enableVP) { v -> onUpdate { it.copy(enableVP = v) } }
    DccIntSlider("VP Lookback Bars", s.profLb, 100, 400) { v -> onUpdate { it.copy(profLb = v) } }
    DccIntSlider("VP Rows", s.profRows, 24, 96) { v -> onUpdate { it.copy(profRows = v) } }
    DccSwitch("Show POC Line & Label", s.showPoc) { v -> onUpdate { it.copy(showPoc = v) } }
    DccSwitch("Highlight S/R Level Rows", s.showLvlRows) { v -> onUpdate { it.copy(showLvlRows = v) } }

    SectionHeader("14 • Profile-Guided S/R")
    DccSwitch("Enable Profile S/R", s.enableSR) { v -> onUpdate { it.copy(enableSR = v) } }
    DccIntSlider("Swing Length", s.swingLen, 2, 15) { v -> onUpdate { it.copy(swingLen = v) } }
    DccFloatSlider("Min Score", s.scoreMin, 10.0, 90.0, 5.0) { v -> onUpdate { it.copy(scoreMin = v) } }
    DccIntSlider("Max S/R Levels", s.maxLvl, 2, 24) { v -> onUpdate { it.copy(maxLvl = v) } }
    DccChoiceChips("S/R Line Style", listOf("Solid", "Dashed", "Dotted"), s.srLvlStyle) { v ->
        onUpdate { it.copy(srLvlStyle = v) }
    }
    DccSwitch("Show Score at Edge", s.showEdge) { v -> onUpdate { it.copy(showEdge = v) } }
    DccSwitch("Show Raw Pivot Markers", s.showRaw) { v -> onUpdate { it.copy(showRaw = v) } }
}

@Composable
private fun TsmAndTableSection(
    s: DccSettings,
    onUpdate: ((DccSettings) -> DccSettings) -> Unit
) {
    SectionHeader("06 • DCC Table & 15 • Squeeze Index")
    DccSwitch("Enable DCC Table", s.showTable) { v -> onUpdate { it.copy(showTable = v) } }
    DccChoiceChips(
        "Table Position",
        listOf("Bottom Center", "Bottom Left", "Bottom Right", "Top Right", "Top Left"),
        s.tablePosInput
    ) { v -> onUpdate { it.copy(tablePosInput = v) } }
    DccChoiceChips("Table Text Size", listOf("Tiny", "Small", "Normal"), s.tblTextInput) { v ->
        onUpdate { it.copy(tblTextInput = v) }
    }
    DccSwitch("Enable Squeeze Index (SQZ)", s.enableSqz) { v -> onUpdate { it.copy(enableSqz = v) } }
    DccFloatSlider("Compression Threshold", s.sqzThreshold, 50.0, 95.0, 5.0) { v ->
        onUpdate { it.copy(sqzThreshold = v) }
    }

    SectionHeader("10 • Trend Strength Matrix (TSM)")
    DccSwitch("Enable TSM", s.enableTSM) { v -> onUpdate { it.copy(enableTSM = v) } }
    DccChoiceChips(
        "TSM Display Mode",
        listOf("Compact DCC Table Row", "Full Mobile TSM Table"),
        s.tsmDisplayMode
    ) { v -> onUpdate { it.copy(tsmDisplayMode = v) } }
    DccChoiceChips("HTF Confirmation", listOf("Live HTF", "Last Closed HTF"), s.tsmConfirmMode) { v ->
        onUpdate { it.copy(tsmConfirmMode = v) }
    }
    DccFloatSlider("Strong Trend Threshold (%)", s.tsmMinStrength, 20.0, 90.0, 5.0) { v ->
        onUpdate { it.copy(tsmMinStrength = v) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun DccSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DccIntSlider(label: String, value: Int, minVal: Int, maxVal: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("$value", style = MonoPriceSmallStyle, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(minVal, maxVal)) },
            valueRange = minVal.toFloat()..maxVal.toFloat()
        )
    }
}

@Composable
private fun DccFloatSlider(label: String, value: Double, minVal: Double, maxVal: Double, step: Double, onChange: (Double) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(String.format(Locale.US, "%.2f", value), style = MonoPriceSmallStyle, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val snapped = (raw / step).roundToInt() * step
                onChange(snapped.coerceIn(minVal, maxVal))
            },
            valueRange = minVal.toFloat()..maxVal.toFloat()
        )
    }
}

@Composable
private fun DccChoiceChips(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            options.forEach { opt ->
                val active = opt == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(7.dp)
                        )
                        .clickable { onSelect(opt) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = opt,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
