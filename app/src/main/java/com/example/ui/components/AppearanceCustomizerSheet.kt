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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ChartAppearance
import com.example.model.ChartColorSwatches
import com.example.model.ThemePreset
import com.example.ui.theme.toComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceCustomizerSheet(
    appearance: ChartAppearance,
    onApplyPreset: (ThemePreset) -> Unit,
    onUpdateAppearance: ((ChartAppearance) -> ChartAppearance) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .testTag("appearance_customizer_sheet")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.appearance_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Customize workspace theme, canvas, candles & DCC",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_appearance_sheet_button")
                ) {
                    Text(stringResource(R.string.done_action))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Theme Presets
            Text(
                text = "Theme Presets",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                ThemePreset.entries.forEach { preset ->
                    val presetData = ChartAppearance.fromPreset(preset)
                    val isSelected = appearance.presetId == preset.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onApplyPreset(preset) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("theme_preset_${preset.id}")
                    ) {
                        // Mini color preview bar
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(presetData.backgroundArgb.toComposeColor())
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(presetData.bullishCandleArgb.toComposeColor())
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(presetData.bearishCandleArgb.toComposeColor())
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(presetData.primaryAccentArgb.toComposeColor())
                            )
                        }
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

            // Canvas Background Swatches
            ColorSwatchRow(
                title = "Chart Background",
                swatches = ChartColorSwatches.backgrounds,
                selectedArgb = appearance.backgroundArgb,
                onSelectColor = { argb ->
                    val isLightBg = argb == 0xFFF5F7FAL
                    onUpdateAppearance {
                        it.copy(
                            backgroundArgb = argb,
                            isLightTheme = isLightBg,
                            surfaceArgb = if (isLightBg) 0xFFFFFFFFL else it.surfaceArgb,
                            textPrimaryArgb = if (isLightBg) 0xFF142030L else 0xFFE6EDF5L,
                            textSecondaryArgb = if (isLightBg) 0xFF5A6B82L else 0xFF8E9EB3L
                        )
                    }
                },
                tagPrefix = "bg_swatch"
            )

            // Bullish Candle Swatches
            ColorSwatchRow(
                title = "Bullish Candles",
                swatches = ChartColorSwatches.bullishCandles,
                selectedArgb = appearance.bullishCandleArgb,
                onSelectColor = { argb ->
                    onUpdateAppearance { it.copy(bullishCandleArgb = argb, dccBullSignalArgb = argb) }
                },
                tagPrefix = "bull_swatch"
            )

            // Bearish Candle Swatches
            ColorSwatchRow(
                title = "Bearish Candles",
                swatches = ChartColorSwatches.bearishCandles,
                selectedArgb = appearance.bearishCandleArgb,
                onSelectColor = { argb ->
                    onUpdateAppearance { it.copy(bearishCandleArgb = argb, dccBearSignalArgb = argb) }
                },
                tagPrefix = "bear_swatch"
            )

            // DCC Midline Color
            ColorSwatchRow(
                title = "DCC Midline & Accent",
                swatches = ChartColorSwatches.dccLineColors,
                selectedArgb = appearance.dccMidArgb,
                onSelectColor = { argb ->
                    onUpdateAppearance { it.copy(dccMidArgb = argb, primaryAccentArgb = argb) }
                },
                tagPrefix = "dcc_mid_swatch"
            )

            // DCC Band Color
            ColorSwatchRow(
                title = "DCC Channel Bands",
                swatches = ChartColorSwatches.dccLineColors,
                selectedArgb = appearance.dccUpperArgb,
                onSelectColor = { argb ->
                    onUpdateAppearance { it.copy(dccUpperArgb = argb, dccLowerArgb = argb) }
                },
                tagPrefix = "dcc_band_swatch"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

            // Gridlines Toggle & Opacity
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Chart Gridlines",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Toggle price and time reference grid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = appearance.showGrid,
                    onCheckedChange = { show -> onUpdateAppearance { it.copy(showGrid = show) } },
                    modifier = Modifier.testTag("grid_visibility_switch")
                )
            }

            if (appearance.showGrid) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Grid Opacity (${(appearance.gridAlpha * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = appearance.gridAlpha,
                        onValueChange = { alpha ->
                            onUpdateAppearance { it.copy(gridAlpha = alpha.coerceIn(0.15f, 1.0f)) }
                        },
                        valueRange = 0.15f..1.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grid_opacity_slider")
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = onResetDefault,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_appearance_button")
            ) {
                Text("Reset to Midnight Slate")
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    title: String,
    swatches: List<Pair<String, Long>>,
    selectedArgb: Long,
    onSelectColor: (Long) -> Unit,
    tagPrefix: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            swatches.forEachIndexed { idx, (name, argb) ->
                val isSelected = selectedArgb == argb
                val color = argb.toComposeColor()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clickable { onSelectColor(argb) }
                        .testTag("${tagPrefix}_$idx")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "$name selected",
                                tint = if (argb == 0xFFF5F7FAL) Color(0xFF142030) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
