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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.LiveQuote
import com.example.model.MarketInstrument
import com.example.model.StreamStatus
import com.example.model.Timeframe
import com.example.ui.theme.LocalChartAppearance
import com.example.ui.theme.MonoPriceLargeStyle
import com.example.ui.theme.MonoPriceSmallStyle
import com.example.ui.theme.SlateAmberAccent
import com.example.ui.theme.toComposeColor
import com.example.ui.viewmodel.formatPrice
import java.util.Locale
import kotlin.math.abs

@Composable
fun TopTradingBar(
    instrument: MarketInstrument,
    selectedTimeframe: Timeframe,
    liveQuote: LiveQuote?,
    lastCandleClose: Double?,
    streamStatus: StreamStatus,
    dccEnabled: Boolean,
    customIndicatorCount: Int,
    showWatchlistMenuButton: Boolean,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onOpenWatchlist: () -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onToggleDcc: () -> Unit,
    onOpenDccSettings: () -> Unit,
    onOpenCustomIndicators: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearance = LocalChartAppearance.current
    val bullishColor = appearance.bullishCandleArgb.toComposeColor()
    val bearishColor = appearance.bearishCandleArgb.toComposeColor()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Row 1: Watchlist Menu + Symbol & Live TradingView Order Strip + Actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            if (showWatchlistMenuButton) {
                IconButton(
                    onClick = onOpenWatchlist,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("open_watchlist_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.open_watchlist_cd),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(Modifier.width(6.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${instrument.name} • ${selectedTimeframe.uiLabel} • OANDA",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    MarketOpenBadge(status = streamStatus)
                    UpdatesFrequencyBadge()
                }

                val displayPrice = liveQuote?.mid ?: lastCandleClose
                if (displayPrice != null) {
                    val priceColor = when (liveQuote?.isUpTick) {
                        true -> bullishColor
                        false -> bearishColor
                        null -> MaterialTheme.colorScheme.onSurface
                    }
                    val diff = if (lastCandleClose != null && liveQuote != null) liveQuote.mid - lastCandleClose else 0.0
                    val pct = if (lastCandleClose != null && lastCandleClose > 0.0) (diff / lastCandleClose) * 100.0 else 0.0
                    val diffSign = if (diff >= 0) "+" else ""
                    val ohlcSummary = "C ${formatPrice(displayPrice, instrument.decimals)}  $diffSign${formatPrice(diff, instrument.decimals)} ($diffSign${String.format(Locale.US, "%.2f", pct)}%)"

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = ohlcSummary,
                            style = MonoPriceSmallStyle,
                            color = priceColor,
                            maxLines = 1
                        )
                    }
                }

                QuickOrderStrip(
                    liveQuote = liveQuote,
                    lastCandleClose = lastCandleClose,
                    decimals = instrument.decimals,
                    bullishColor = bullishColor,
                    bearishColor = bearishColor
                )
            }

            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("toggle_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Toggle Fullscreen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onOpenDccSettings,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("open_dcc_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.dcc_settings_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onOpenAppearanceSettings,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("open_appearance_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = stringResource(R.string.appearance_settings_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("refresh_chart_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.refresh_chart_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), thickness = 1.dp)

        // Row 2: DCC Master Toggle + Custom Pine (+3) Pill + 9 Timeframe Chips
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // DCC Master Toggle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (dccEnabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = 1.dp,
                        color = if (dccEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(7.dp)
                    )
                    .clickable(onClick = onToggleDcc)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("toggle_dcc_button")
            ) {
                Text(
                    text = "DCC Master",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (dccEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Custom Pine Script Indicators (+3 slots)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (customIndicatorCount > 0) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = 1.dp,
                        color = if (customIndicatorCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(7.dp)
                    )
                    .clickable(onClick = onOpenCustomIndicators)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("open_custom_pine_button")
            ) {
                Text(
                    text = "+Pine ($customIndicatorCount/3)",
                    style = MonoPriceSmallStyle,
                    color = if (customIndicatorCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Hairline separator
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(1.dp)
                    .height(22.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )

            // Timeframe Switcher Strip (M1..1W)
            Timeframe.entries.forEach { tf ->
                val isSelected = tf == selectedTimeframe
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(7.dp)
                        )
                        .clickable { onSelectTimeframe(tf) }
                        .padding(horizontal = 11.dp)
                        .testTag("timeframe_${tf.name.lowercase()}")
                ) {
                    Text(
                        text = tf.uiLabel,
                        style = MonoPriceSmallStyle,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    }
}



@Composable
private fun StreamStatusBadge(status: StreamStatus) {
    val appearance = LocalChartAppearance.current
    val (dotColor, label) = when (status) {
        StreamStatus.LIVE -> appearance.bullishCandleArgb.toComposeColor() to stringResource(R.string.stream_live)
        StreamStatus.CONNECTING -> MaterialTheme.colorScheme.primary to stringResource(R.string.stream_connecting)
        StreamStatus.RECONNECTING -> SlateAmberAccent to stringResource(R.string.stream_reconnecting)
        StreamStatus.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.stream_offline)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(dotColor.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .testTag("stream_status_badge")
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = dotColor
        )
    }
}

@Composable
private fun MarketOpenBadge(status: StreamStatus) {
    val isOpen = status == StreamStatus.LIVE || status == StreamStatus.CONNECTING
    val dotColor = if (isOpen) Color(0xFF2E7D32) else Color(0xFF757575)
    val bgColor = if (isOpen) Color(0xFFE8F5E9) else Color(0xFFEEEEEE)
    val textColor = if (isOpen) Color(0xFF2E7D32) else Color(0xFF757575)
    val textLabel = if (isOpen) "Open" else "Closed"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = textLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 9.sp),
            color = textColor
        )
    }
}

@Composable
private fun UpdatesFrequencyBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFFFEBEE))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "≈ 1s updates",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 9.sp),
            color = Color(0xFFC62828)
        )
    }
}

@Composable
private fun QuickOrderStrip(
    liveQuote: LiveQuote?,
    lastCandleClose: Double?,
    decimals: Int,
    bullishColor: Color,
    bearishColor: Color
) {
    if (liveQuote == null) return
    val bidStr = formatPrice(liveQuote.bid, decimals)
    val askStr = formatPrice(liveQuote.ask, decimals)
    val mult = if (decimals >= 4) 10000.0 else if (decimals == 3) 100.0 else 1.0
    val spreadVal = abs(liveQuote.ask - liveQuote.bid) * mult
    val spreadStr = String.format(Locale.US, "%.1f", spreadVal)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        // SELL Box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .border(1.dp, bearishColor.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                .background(bearishColor.copy(alpha = 0.08f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = bidStr,
                style = MonoPriceSmallStyle.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = bearishColor
            )
            Text(
                text = "SELL",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 8.5.sp),
                color = bearishColor
            )
        }

        // Spread pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = spreadStr,
                style = MonoPriceSmallStyle.copy(fontSize = 9.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // BUY Box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .border(1.dp, Color(0xFF1E88E5).copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                .background(Color(0xFF1E88E5).copy(alpha = 0.08f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = askStr,
                style = MonoPriceSmallStyle.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = Color(0xFF1E88E5)
            )
            Text(
                text = "BUY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 8.5.sp),
                color = Color(0xFF1E88E5)
            )
        }
    }
}


