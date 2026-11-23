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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.indicators.CustomPineIndicator
import com.example.indicators.MAX_CUSTOM_PINE_INDICATORS
import com.example.indicators.PineEvaluationResult
import com.example.indicators.PineScriptEngine
import com.example.model.Candle
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomIndicatorsSheet(
    indicators: List<CustomPineIndicator>,
    results: List<PineEvaluationResult>,
    candles: List<Candle>,
    onAddIndicator: (name: String, code: String) -> Unit,
    onUpdateIndicator: (id: String, name: String, code: String) -> Unit,
    onToggleIndicator: (id: String) -> Unit,
    onDeleteIndicator: (id: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var isEditorOpen by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var draftCode by rememberSaveable { mutableStateOf("") }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .testTag("custom_pine_indicators_sheet")
        ) {
            if (!isEditorOpen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Custom Pine Indicators (${indicators.size}/$MAX_CUSTOM_PINE_INDICATORS)",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Add up to 3 extra Pine Script indicators alongside DCC Master",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_pine_sheet_button")
                    ) {
                        Text("Done")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Built-in primary DCC Master notice
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DCC Master (DCC+TSM+VPSR+SQZ v2.5.5)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Built-in primary indicator · Configure via DCC Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (indicators.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "No extra Pine Script indicators added yet. Paste any Pine Script (EMA/BB/SuperTrend overlay or RSI/MACD oscillator) below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    indicators.forEach { item ->
                        val eval = results.firstOrNull { it.indicatorId == item.id }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("pine_slot_${item.id}")
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (item.isOverlay) "OVERLAY" else "PANE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                val statusMsg = when {
                                    !item.enabled -> "Disabled"
                                    eval?.error != null -> "Warning: ${eval.error}"
                                    eval != null -> "${eval.plots.size} plot(s), ${eval.hlines.size} hline(s), ${eval.shapes.size} marker(s)"
                                    else -> "Ready"
                                }
                                Text(
                                    text = statusMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (eval?.error != null && item.enabled) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = item.enabled,
                                onCheckedChange = { onToggleIndicator(item.id) }
                            )
                            IconButton(
                                onClick = {
                                    editingId = item.id
                                    draftName = item.name
                                    draftCode = item.code
                                    isEditorOpen = true
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Pine Script",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeleteIndicator(item.id) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Indicator",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        editingId = null
                        draftName = ""
                        draftCode = ""
                        isEditorOpen = true
                    },
                    enabled = indicators.size < MAX_CUSTOM_PINE_INDICATORS,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_pine_indicator_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (indicators.size < MAX_CUSTOM_PINE_INDICATORS) {
                            "  Paste New Pine Script Indicator"
                        } else {
                            "  Maximum 3 Custom Indicators Reached"
                        }
                    )
                }
            } else {
                // Pine Script Paste & Edit View
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (editingId == null) "Paste Pine Script Indicator" else "Edit Pine Script",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { isEditorOpen = false }) {
                        Text("Back")
                    }
                }

                // Quick Starter Templates row
                Text(
                    text = "Quick Templates (or paste your own Pine Script below)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    PineScriptEngine.starterTemplates.forEach { (tplLabel, tplCode) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable {
                                    draftCode = tplCode
                                    val header = PineScriptEngine.parseHeader(tplCode)
                                    draftName = header.title
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tplLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text("Indicator Name (auto-detected from script if blank)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pine_name_input")
                )

                OutlinedTextField(
                    value = draftCode,
                    onValueChange = { draftCode = it },
                    label = { Text("Pine Script Code (v4 / v5 / v6)") },
                    placeholder = { Text("//@version=6\nindicator(\"My Indicator\", overlay=true)\nplot(ta.ema(close, 50), color=#5EA8D8)") },
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 280.dp)
                        .testTag("pine_code_input")
                )

                // Live parse preview
                val preview = remember(draftCode, candles) {
                    if (draftCode.isBlank()) null
                    else PineScriptEngine.evaluate(
                        CustomPineIndicator("preview", draftName, draftCode, true, true),
                        candles
                    )
                }
                if (preview != null) {
                    val isErr = preview.error != null
                    Text(
                        text = if (isErr) {
                            "Parser notice: ${preview.error}"
                        } else {
                            "Detected: ${preview.title} (${if (preview.isOverlay) "Chart Overlay" else "Lower Oscillator Pane"}) · ${preview.plots.size} plot(s), ${preview.hlines.size} hline(s)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { isEditorOpen = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val id = editingId
                            if (id == null) {
                                onAddIndicator(draftName, draftCode)
                            } else {
                                onUpdateIndicator(id, draftName, draftCode)
                            }
                            isEditorOpen = false
                        },
                        enabled = draftCode.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_pine_indicator_button")
                    ) {
                        Text(if (editingId == null) "Add to Chart" else "Save Changes")
                    }
                }
            }
        }
    }
}
