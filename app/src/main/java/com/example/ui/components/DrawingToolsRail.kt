package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DrawingTool

@Composable
fun DrawingToolsRail(
    activeTool: DrawingTool,
    drawingCount: Int,
    hasPendingAnchor: Boolean,
    onSelectTool: (DrawingTool) -> Unit,
    onUndo: () -> Unit,
    onClearDrawings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("drawing_tools_rail")
    ) {
        // Cursor / Navigation
        ToolRailButton(
            tool = DrawingTool.CURSOR,
            icon = Icons.Default.NearMe,
            isSelected = activeTool == DrawingTool.CURSOR,
            onClick = { onSelectTool(DrawingTool.CURSOR) }
        )

        RailCategoryDivider()

        // Trend Tools
        ToolRailButton(
            tool = DrawingTool.HORIZONTAL_LINE,
            icon = Icons.Default.HorizontalRule,
            isSelected = activeTool == DrawingTool.HORIZONTAL_LINE,
            onClick = { onSelectTool(DrawingTool.HORIZONTAL_LINE) }
        )
        ToolRailButton(
            tool = DrawingTool.TRENDLINE,
            icon = Icons.AutoMirrored.Filled.ShowChart,
            isSelected = activeTool == DrawingTool.TRENDLINE,
            onClick = { onSelectTool(DrawingTool.TRENDLINE) }
        )

        RailCategoryDivider()

        // Gann & Fibonacci
        ToolRailButton(
            tool = DrawingTool.FIB_RETRACEMENT,
            icon = Icons.Default.FormatAlignJustify,
            isSelected = activeTool == DrawingTool.FIB_RETRACEMENT,
            onClick = { onSelectTool(DrawingTool.FIB_RETRACEMENT) }
        )

        RailCategoryDivider()

        // Forecasting & Measurement
        ToolRailButton(
            tool = DrawingTool.LONG_POSITION,
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            isSelected = activeTool == DrawingTool.LONG_POSITION,
            onClick = { onSelectTool(DrawingTool.LONG_POSITION) }
        )
        ToolRailButton(
            tool = DrawingTool.SHORT_POSITION,
            icon = Icons.AutoMirrored.Filled.TrendingDown,
            isSelected = activeTool == DrawingTool.SHORT_POSITION,
            onClick = { onSelectTool(DrawingTool.SHORT_POSITION) }
        )
        ToolRailButton(
            tool = DrawingTool.ANCHORED_VOLUME_PROFILE,
            icon = Icons.Default.AlignHorizontalLeft,
            isSelected = activeTool == DrawingTool.ANCHORED_VOLUME_PROFILE,
            onClick = { onSelectTool(DrawingTool.ANCHORED_VOLUME_PROFILE) }
        )
        ToolRailButton(
            tool = DrawingTool.FIXED_RANGE_VOLUME_PROFILE,
            icon = Icons.Default.BarChart,
            isSelected = activeTool == DrawingTool.FIXED_RANGE_VOLUME_PROFILE,
            onClick = { onSelectTool(DrawingTool.FIXED_RANGE_VOLUME_PROFILE) }
        )
        ToolRailButton(
            tool = DrawingTool.PRICE_RANGE,
            icon = Icons.Default.Straighten,
            isSelected = activeTool == DrawingTool.PRICE_RANGE,
            onClick = { onSelectTool(DrawingTool.PRICE_RANGE) }
        )

        RailCategoryDivider()

        // Geometric Shapes
        ToolRailButton(
            tool = DrawingTool.RECTANGLE,
            icon = Icons.Default.CropSquare,
            isSelected = activeTool == DrawingTool.RECTANGLE,
            onClick = { onSelectTool(DrawingTool.RECTANGLE) }
        )

        Spacer(Modifier.height(6.dp))
        RailCategoryDivider()

        // Undo Last Drawing
        ActionRailButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            label = "Undo",
            contentDescription = stringResource(R.string.undo_drawing_cd),
            enabled = drawingCount > 0 || hasPendingAnchor,
            isDestructive = false,
            onClick = onUndo,
            testTag = "undo_drawing_button"
        )

        // Clear All Drawings
        ActionRailButton(
            icon = Icons.Default.DeleteSweep,
            label = "Clear",
            contentDescription = stringResource(R.string.clear_drawings_label),
            enabled = drawingCount > 0 || hasPendingAnchor,
            isDestructive = true,
            onClick = onClearDrawings,
            testTag = "clear_drawings_button"
        )
    }
}

@Composable
private fun ToolRailButton(
    tool: DrawingTool,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val fg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("tool_${tool.name.lowercase()}")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tool.label,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = tool.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun ActionRailButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    enabled: Boolean,
    isDestructive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
private fun RailCategoryDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
