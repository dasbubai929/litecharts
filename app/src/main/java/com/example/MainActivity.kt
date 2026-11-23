package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ChartPreferencesStore
import com.example.data.OandaRepository
import com.example.ui.ChartingTerminalScreen
import com.example.ui.theme.DccChartingTheme
import com.example.ui.viewmodel.ChartViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            val repository = remember { OandaRepository() }
            val preferencesStore = remember { ChartPreferencesStore(applicationContext) }
            val chartViewModel: ChartViewModel = viewModel(
                factory = ChartViewModel.Factory(repository, preferencesStore)
            )
            val uiState by chartViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.appearance.isLightTheme) {
                val style = if (uiState.appearance.isLightTheme) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            DccChartingTheme(appearance = uiState.appearance) {
                ChartingTerminalScreen(
                    uiState = uiState,
                    viewModel = chartViewModel
                )
            }
        }
    }
}
