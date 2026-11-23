package com.example.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.indicators.DccSettings
import com.example.indicators.DccTableData
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.toComposeColor
import com.example.ui.viewmodel.formatPrice
import java.util.Locale

private fun alignmentForTablePosition(pos: String): Alignment = when (pos) {
    "Top Left" -> Alignment.TopStart
    "Top Center" -> Alignment.TopCenter
    "Top Right" -> Alignment.TopEnd
    "Middle Left" -> Alignment.CenterStart
    "Middle Right" -> Alignment.CenterEnd
    "Bottom Left" -> Alignment.BottomStart
    "Bottom Center" -> Alignment.BottomCenter
    else -> Alignment.BottomEnd
}

private fun fontSizeForTable(sz: String) = when (sz) {
    "Tiny" -> 10.sp
    "Small" -> 11.5.sp
    else -> 13.sp
}

@Composable
fun BoxScope.DccTablesOverlay(
    tableData: DccTableData,
    settings: DccSettings,
    decimals: Int
) {
    if (!settings.masterEnabled) return

    val fontSize = fontSizeForTable(settings.tblTextInput)
    val tblLabelBg = settings.cuTblLabel.toComposeColor()
    val tblValueBg = settings.cuTblValue.toComposeColor()
    val tblLabelText = settings.cuTblText.toComposeColor()
    val tblValueText = settings.cuTblVal.toComposeColor()
    val colBull = settings.cuBull.toComposeColor()
    val colBear = settings.cuBear.toComposeColor()

    // 1. Main DCC Table (BIAS, TSM compact row, SL, SQZ)
    if (settings.showTable) {
        val rows = mutableListOf<Triple<String, String, Color>>()

        if (settings.enableHTFEMA || settings.enableADX) {
            val adxStr = if (!tableData.adxValue.isNaN()) {
                String.format(Locale.US, "%.1f", tableData.adxValue)
            } else "--"
            val valStr = if (settings.enableHTFEMA && settings.enableADX) {
                "${tableData.biasText} · $adxStr"
            } else if (settings.enableHTFEMA) {
                tableData.biasText
            } else {
                adxStr
            }
            val rowLbl = if (settings.enableHTFEMA) "BIAS" else "ADX"
            val rowCol = if (settings.enableHTFEMA) {
                when {
                    tableData.biasLong -> colBull
                    tableData.biasShort -> colBear
                    else -> tblLabelText
                }
            } else tblValueText
            rows.add(Triple(rowLbl, valStr, rowCol))
        }

        if (settings.enableTSM && settings.tsmDisplayMode == "Compact DCC Table Row") {
            val tsmCol = when {
                tableData.tsmMtfScore > 0.2 -> colBull
                tableData.tsmMtfScore < -0.2 -> colBear
                else -> tblValueText
            }
            rows.add(Triple("TSM", tableData.tsmStripText, tsmCol))
        }

        if (settings.enableATR) {
            val livePart = if (settings.enableHTFEMA && tableData.liveSL != null) {
                formatPrice(tableData.liveSL, decimals)
            } else "FLAT"
            val slVal = if (settings.showLiveSL) "$livePart · ${tableData.slDistStr}" else tableData.slDistStr
            rows.add(Triple("SL", slVal, tblValueText))
        }

        if (settings.enableSqz) {
            val psiStr = if (!tableData.sqzPsi.isNaN()) {
                String.format(Locale.US, "%.1f", tableData.sqzPsi)
            } else "--"
            val sqzColor = when (tableData.sqzState) {
                "COMP" -> settings.cuSqzComp.toComposeColor()
                "REL" -> settings.cuSqzRel.toComposeColor()
                else -> tblValueText
            }
            rows.add(Triple("SQZ", "$psiStr · ${tableData.sqzState}", sqzColor))
        }

        if (rows.isNotEmpty()) {
            val align = alignmentForTablePosition(settings.tablePosInput)
            Column(
                modifier = Modifier
                    .align(align)
                    .padding(start = 10.dp, end = 82.dp, top = 46.dp, bottom = 32.dp)
                    .width(IntrinsicSize.Max)
                    .clip(RoundedCornerShape(6.dp))
                    .background(tblValueBg.copy(alpha = 0.92f))
                    .border(1.dp, tblLabelText.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .testTag("dcc_master_table")
            ) {
                rows.forEachIndexed { idx, (label, value, valColor) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = label,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSize,
                            color = tblLabelText,
                            modifier = Modifier
                                .background(tblLabelBg.copy(alpha = 0.95f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                .width(38.dp)
                        )
                        Text(
                            text = value,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fontSize,
                            color = valColor,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (idx < rows.lastIndex) {
                        HorizontalDivider(color = tblLabelText.copy(alpha = 0.22f), thickness = 0.7.dp)
                    }
                }
            }
        }
    }

    // 2. Full Mobile TSM Table (6 columns x 5 rows)
    if (settings.enableTSM && settings.tsmDisplayMode == "Full Mobile TSM Table" && tableData.tsmRows.size == 3) {
        val align = alignmentForTablePosition(settings.tsmDashPos)

        fun scoreBg(score: Double): Color = when {
            score > 0.5 -> colBull
            score > 0.2 -> colBull.copy(alpha = 0.6f)
            score < -0.5 -> colBear
            score < -0.2 -> colBear.copy(alpha = 0.6f)
            else -> tblValueText
        }

        Column(
            modifier = Modifier
                .align(align)
                .padding(start = 10.dp, end = 82.dp, top = 46.dp, bottom = 32.dp)
                .width(214.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xE61E1E1E))
                .border(1.dp, tblLabelBg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .testTag("dcc_full_tsm_table")
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tblLabelBg)
                    .padding(vertical = 3.dp)
            ) {
                listOf("TF", "RSI", "MCD", "ADX", "MA", "%").forEach { h ->
                    Text(
                        text = h,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                        color = tblLabelText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            // 3 HTF Rows
            tableData.tsmRows.forEach { r ->
                HorizontalDivider(color = tblLabelBg.copy(alpha = 0.2f), thickness = 0.6.dp)
                Row(modifier = Modifier.fillMaxWidth()) {
                    TsmCell(r.tfLabel, tblLabelText, tblValueBg, fontSize, Modifier.weight(1f))
                    TsmCell(String.format(Locale.US, "%.0f", r.rsi), Color.White, scoreBg(r.rsiScore), fontSize, Modifier.weight(1f))
                    TsmCell(if (r.macdScore > 0) "▲" else "▼", Color.White, scoreBg(r.macdScore), fontSize, Modifier.weight(1f))
                    TsmCell(String.format(Locale.US, "%.0f", r.adx), Color.White, scoreBg(r.adxScore), fontSize, Modifier.weight(1f))
                    TsmCell(if (r.maScore > 0) "▲" else "▼", Color.White, scoreBg(r.maScore), fontSize, Modifier.weight(1f))
                    TsmCell(String.format(Locale.US, "%.0f%%", r.composite * 100.0), Color.White, scoreBg(r.composite), fontSize, Modifier.weight(1f))
                }
            }
            // MTF Summary Row
            HorizontalDivider(color = tblLabelBg.copy(alpha = 0.2f), thickness = 0.6.dp)
            val overallText = when {
                tableData.tsmMtfScore > 0.5 -> "STR BULL"
                tableData.tsmMtfScore > 0.2 -> "BULL"
                tableData.tsmMtfScore < -0.5 -> "STR BEAR"
                tableData.tsmMtfScore < -0.2 -> "BEAR"
                else -> "NEUT"
            }
            val ovColor = when {
                tableData.tsmMtfScore > 0.2 -> colBull
                tableData.tsmMtfScore < -0.2 -> colBear
                else -> tblValueText
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TsmCell("MTF", tblLabelText, tblLabelBg, fontSize, Modifier.weight(1f))
                TsmCell(overallText, Color.White, ovColor.copy(alpha = 0.85f), fontSize, Modifier.weight(4f))
                TsmCell(String.format(Locale.US, "%.0f%%", tableData.tsmScorePct), Color.White, ovColor.copy(alpha = 0.85f), fontSize, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TsmCell(
    text: String,
    textColor: Color,
    bgColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize,
        color = textColor,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .background(bgColor)
            .padding(vertical = 3.dp, horizontal = 2.dp)
    )
}
