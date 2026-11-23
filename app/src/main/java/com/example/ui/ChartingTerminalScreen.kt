package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DrawingTool
import com.example.ui.chart.CandlestickChartCanvas
import com.example.ui.components.AppearanceCustomizerSheet
import com.example.ui.components.CustomIndicatorsSheet
import com.example.ui.components.DccSettingsSheet
import com.example.ui.components.DrawingToolsRail
import com.example.ui.components.TopTradingBar
import com.example.ui.components.WatchlistPanel
import com.example.ui.theme.toComposeColor
import com.example.ui.viewmodel.ChartUiState
import com.example.ui.viewmodel.ChartViewModel
import kotlinx.coroutines.launch

@Composable
fun ChartingTerminalScreen(
    uiState: ChartUiState,
    viewModel: ChartViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showDccSettings by rememberSaveable { mutableStateOf(false) }
    var showCustomIndicators by rememberSaveable { mutableStateOf(false) }
    var showAppearanceSettings by rememberSaveable { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    val clearedMessage = stringResource(R.string.drawings_cleared_message)
    val undoLabel = stringResource(R.string.undo_action)

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = uiState.activeTool != DrawingTool.CURSOR || uiState.pendingAnchor != null) {
        viewModel.cancelPendingDrawing()
    }

    val handleClearDrawings: () -> Unit = {
        val clearedAny = viewModel.clearDrawings()
        if (clearedAny) {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val res = snackbarHostState.showSnackbar(
                    message = clearedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) {
                    viewModel.restoreClearedDrawings()
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isExpandedLayout = maxWidth >= 760.dp

        val mainTerminalContent: @Composable () -> Unit = {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedLayout) {
                        WatchlistPanel(
                            instruments = uiState.watchlist,
                            selectedInstrument = uiState.selectedInstrument,
                            liveQuote = uiState.liveQuote,
                            onSelectInstrument = viewModel::selectInstrument,
                            modifier = Modifier.width(248.dp)
                        )
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 1.dp
                        )
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (!isFullscreen) {
                            TopTradingBar(
                                instrument = uiState.selectedInstrument,
                                selectedTimeframe = uiState.selectedTimeframe,
                                liveQuote = uiState.liveQuote,
                                lastCandleClose = uiState.candles.lastOrNull()?.close,
                                streamStatus = uiState.streamStatus,
                                dccEnabled = uiState.dccSettings.masterEnabled,
                                customIndicatorCount = uiState.customIndicators.size,
                                showWatchlistMenuButton = !isExpandedLayout,
                                isFullscreen = isFullscreen,
                                onToggleFullscreen = { isFullscreen = !isFullscreen },
                                onOpenWatchlist = { scope.launch { drawerState.open() } },
                                onSelectTimeframe = viewModel::selectTimeframe,
                                onToggleDcc = viewModel::toggleDccOverlay,
                                onOpenDccSettings = { showDccSettings = true },
                                onOpenCustomIndicators = { showCustomIndicators = true },
                                onOpenAppearanceSettings = { showAppearanceSettings = true },
                                onRefresh = viewModel::reloadChart
                            )
                        }

                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (!isFullscreen) {
                                DrawingToolsRail(
                                    activeTool = uiState.activeTool,
                                    drawingCount = uiState.drawings.size,
                                    hasPendingAnchor = uiState.pendingAnchor != null,
                                    onSelectTool = viewModel::selectDrawingTool,
                                    onUndo = viewModel::undoLastDrawing,
                                    onClearDrawings = handleClearDrawings
                                )

                                VerticalDivider(
                                    color = MaterialTheme.colorScheme.outline,
                                    thickness = 1.dp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                when {
                                    uiState.isApiKeyMissing -> {
                                        ApiKeySetupState(
                                            onRetry = viewModel::reloadChart,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                    uiState.isLoading && uiState.candles.isEmpty() -> {
                                        LoadingChartState(
                                            symbol = uiState.selectedInstrument.displaySymbol,
                                            timeframe = uiState.selectedTimeframe.uiLabel,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                    uiState.errorMessage != null && uiState.candles.isEmpty() -> {
                                        ErrorChartState(
                                            message = uiState.errorMessage,
                                            onRetry = viewModel::reloadChart,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                    else -> {
                                        CandlestickChartCanvas(
                                            instrument = uiState.selectedInstrument,
                                            timeframe = uiState.selectedTimeframe,
                                            candles = uiState.candles,
                                            dccResult = uiState.dccResult,
                                            dccSettings = uiState.dccSettings,
                                            customResults = uiState.customResults,
                                            appearance = uiState.appearance,
                                            liveQuote = uiState.liveQuote,
                                            drawings = uiState.drawings,
                                            activeTool = uiState.activeTool,
                                            pendingAnchor = uiState.pendingAnchor,
                                            crosshairBarIndex = uiState.crosshairBarIndex,
                                            viewportResetSignal = uiState.viewportResetSignal,
                                            onTapChartForDrawing = viewModel::onChartTapForDrawing,
                                            onCrosshairChanged = viewModel::setCrosshairBarIndex,
                                            onCancelDrawing = viewModel::cancelPendingDrawing
                                        )

                                        if (uiState.isLoading) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.5.dp,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(top = 12.dp, end = 86.dp)
                                                    .size(20.dp)
                                            )
                                        }

                                        if (isFullscreen) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(top = 10.dp, end = 84.dp)
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(uiState.appearance.surfaceElevatedArgb.toComposeColor().copy(alpha = 0.90f))
                                                    .border(
                                                        width = 1.dp,
                                                        color = uiState.appearance.borderArgb.toComposeColor(),
                                                        shape = RoundedCornerShape(20.dp)
                                                    )
                                                    .clickable { isFullscreen = false }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                                    .testTag("exit_fullscreen_button")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FullscreenExit,
                                                        contentDescription = "Exit Fullscreen",
                                                        tint = uiState.appearance.textPrimaryArgb.toComposeColor(),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "Exit",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = uiState.appearance.textPrimaryArgb.toComposeColor()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isExpandedLayout) {
            mainTerminalContent()
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .width(284.dp)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        WatchlistPanel(
                            instruments = uiState.watchlist,
                            selectedInstrument = uiState.selectedInstrument,
                            liveQuote = uiState.liveQuote,
                            onSelectInstrument = { market ->
                                viewModel.selectInstrument(market)
                                scope.launch { drawerState.close() }
                            },
                            onCloseDrawer = { scope.launch { drawerState.close() } }
                        )
                    }
                },
                content = mainTerminalContent
            )
        }
    }

    if (showDccSettings) {
        DccSettingsSheet(
            settings = uiState.dccSettings,
            onUpdateSettings = viewModel::updateDccSettings,
            onResetDefaults = viewModel::resetDccSettings,
            onDismiss = { showDccSettings = false }
        )
    }

    if (showCustomIndicators) {
        CustomIndicatorsSheet(
            indicators = uiState.customIndicators,
            results = uiState.customResults,
            candles = uiState.candles,
            onAddIndicator = { name, code -> viewModel.addCustomIndicator(name, code) },
            onUpdateIndicator = viewModel::updateCustomIndicator,
            onToggleIndicator = viewModel::toggleCustomIndicator,
            onDeleteIndicator = viewModel::deleteCustomIndicator,
            onDismiss = { showCustomIndicators = false }
        )
    }

    if (showAppearanceSettings) {
        AppearanceCustomizerSheet(
            appearance = uiState.appearance,
            onApplyPreset = viewModel::applyThemePreset,
            onUpdateAppearance = viewModel::updateAppearance,
            onResetDefault = viewModel::resetAppearanceToDefault,
            onDismiss = { showAppearanceSettings = false }
        )
    }
}

@Composable
private fun ApiKeySetupState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .padding(24.dp)
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(24.dp)
            .testTag("api_key_missing_card")
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = stringResource(R.string.api_key_missing_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.api_key_missing_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("retry_api_key_button")
        ) {
            Text(stringResource(R.string.retry_action))
        }
    }
}

@Composable
private fun LoadingChartState(
    symbol: String,
    timeframe: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(24.dp)
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
        )
        Text(
            text = "Loading $symbol ($timeframe) from OANDA...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorChartState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .padding(24.dp)
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(24.dp)
            .testTag("chart_error_card")
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("retry_chart_button")
        ) {
            Text(stringResource(R.string.retry_action))
        }
    }
}
