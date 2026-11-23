package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.LiveQuote
import com.example.model.MarketInstrument
import com.example.ui.theme.LocalChartAppearance
import com.example.ui.theme.MonoPriceSmallStyle
import com.example.ui.theme.toComposeColor
import com.example.ui.viewmodel.formatPrice

@Composable
fun WatchlistPanel(
    instruments: List<MarketInstrument>,
    selectedInstrument: MarketInstrument,
    liveQuote: LiveQuote?,
    onSelectInstrument: (MarketInstrument) -> Unit,
    onCloseDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appearance = LocalChartAppearance.current
    val surface = MaterialTheme.colorScheme.surface
    val border = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(surface)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.watchlist_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.watchlist_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onCloseDrawer != null) {
                IconButton(
                    onClick = onCloseDrawer,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_watchlist_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close_watchlist_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(color = border, thickness = 1.dp)

        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("watchlist_list")
        ) {
            items(instruments, key = { it.id }) { item ->
                val isSelected = item.id == selectedInstrument.id
                val itemBg = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    surface
                }
                val itemBorder = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    border.copy(alpha = 0.35f)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(itemBg)
                        .border(1.dp, itemBorder, RoundedCornerShape(10.dp))
                        .clickable { onSelectInstrument(item) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("watchlist_item_${item.id.lowercase()}")
                ) {
                    // Active left accent bar
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                    )
                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.displaySymbol,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.category.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isSelected && liveQuote != null && liveQuote.instrument == item.id) {
                        val tickColor = when (liveQuote.isUpTick) {
                            true -> appearance.bullishCandleArgb.toComposeColor()
                            false -> appearance.bearishCandleArgb.toComposeColor()
                            null -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = formatPrice(liveQuote.mid, item.decimals),
                            style = MonoPriceSmallStyle,
                            color = tickColor
                        )
                    }
                }
            }
        }
    }
}
