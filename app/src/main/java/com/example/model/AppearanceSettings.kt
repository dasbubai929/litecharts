package com.example.model

import com.squareup.moshi.JsonClass

enum class ThemePreset(val id: String, val label: String) {
    TRADINGVIEW_CREAM("tradingview_cream", "TradingView Parchment"),
    MIDNIGHT_SLATE("midnight_slate", "Midnight Slate"),
    OBSIDIAN_TERMINAL("obsidian_terminal", "Obsidian Pro"),
    AMOLED_BLACK("amoled_black", "AMOLED Black"),
    LIGHT_DESK("light_desk", "Light Desk")
}

@JsonClass(generateAdapter = true)
data class ChartAppearance(
    val presetId: String = ThemePreset.TRADINGVIEW_CREAM.id,
    val isLightTheme: Boolean = true,
    val backgroundArgb: Long = 0xFFF2EAE0L,
    val surfaceArgb: Long = 0xFFECE3D6L,
    val surfaceElevatedArgb: Long = 0xFFE2D7C7L,
    val borderArgb: Long = 0xFFD8CCBBL,
    val primaryAccentArgb: Long = 0xFF1E5384L,
    val textPrimaryArgb: Long = 0xFF1A1816L,
    val textSecondaryArgb: Long = 0xFF766E65L,
    val gridColorArgb: Long = 0xFFE2D7C7L,
    val showGrid: Boolean = true,
    val gridAlpha: Float = 0.55f,
    val bullishCandleArgb: Long = 0xFF26A69AL,
    val bearishCandleArgb: Long = 0xFFEF5350L,
    val dccUpperArgb: Long = 0xFF1E5384L,
    val dccMidArgb: Long = 0xFFD49A3BL,
    val dccLowerArgb: Long = 0xFF1E5384L,
    val dccBullSignalArgb: Long = 0xFF26A69AL,
    val dccBearSignalArgb: Long = 0xFFEF5350L
) {
    companion object {
        fun fromPreset(preset: ThemePreset): ChartAppearance = when (preset) {
            ThemePreset.TRADINGVIEW_CREAM -> ChartAppearance()
            ThemePreset.MIDNIGHT_SLATE -> ChartAppearance(
                presetId = preset.id,
                isLightTheme = false,
                backgroundArgb = 0xFF0F141CL,
                surfaceArgb = 0xFF161D28L,
                surfaceElevatedArgb = 0xFF1E2736L,
                borderArgb = 0xFF263244L,
                primaryAccentArgb = 0xFF5EA8D8L,
                textPrimaryArgb = 0xFFE6EDF5L,
                textSecondaryArgb = 0xFF8E9EB3L,
                gridColorArgb = 0xFF2A3648L,
                showGrid = true,
                gridAlpha = 0.55f,
                bullishCandleArgb = 0xFF38B28EL,
                bearishCandleArgb = 0xFFD96B70L,
                dccUpperArgb = 0xFF7C90DBL,
                dccMidArgb = 0xFF5EA8D8L,
                dccLowerArgb = 0xFF7C90DBL,
                dccBullSignalArgb = 0xFF38B28EL,
                dccBearSignalArgb = 0xFFD96B70L
            )
            ThemePreset.OBSIDIAN_TERMINAL -> ChartAppearance(
                presetId = preset.id,
                isLightTheme = false,
                backgroundArgb = 0xFF0A0D12L,
                surfaceArgb = 0xFF12161FL,
                surfaceElevatedArgb = 0xFF19202DL,
                borderArgb = 0xFF232C3CL,
                primaryAccentArgb = 0xFF38BDF8L,
                textPrimaryArgb = 0xFFF1F5F9L,
                textSecondaryArgb = 0xFF8B99AFL,
                gridColorArgb = 0xFF222B3AL,
                showGrid = true,
                gridAlpha = 0.5f,
                bullishCandleArgb = 0xFF10B981L,
                bearishCandleArgb = 0xFFEF4444L,
                dccUpperArgb = 0xFF60A5FAL,
                dccMidArgb = 0xFFF59E0BL,
                dccLowerArgb = 0xFF60A5FAL,
                dccBullSignalArgb = 0xFF10B981L,
                dccBearSignalArgb = 0xFFEF4444L
            )
            ThemePreset.AMOLED_BLACK -> ChartAppearance(
                presetId = preset.id,
                isLightTheme = false,
                backgroundArgb = 0xFF000000L,
                surfaceArgb = 0xFF0B0E14L,
                surfaceElevatedArgb = 0xFF141923L,
                borderArgb = 0xFF1F2735L,
                primaryAccentArgb = 0xFF56B6C2L,
                textPrimaryArgb = 0xFFEAEFF7L,
                textSecondaryArgb = 0xFF8694A8L,
                gridColorArgb = 0xFF1E2634L,
                showGrid = true,
                gridAlpha = 0.45f,
                bullishCandleArgb = 0xFF26A69AL,
                bearishCandleArgb = 0xFFE05D64L,
                dccUpperArgb = 0xFF8A9CE8L,
                dccMidArgb = 0xFF56B6C2L,
                dccLowerArgb = 0xFF8A9CE8L,
                dccBullSignalArgb = 0xFF26A69AL,
                dccBearSignalArgb = 0xFFE05D64L
            )
            ThemePreset.LIGHT_DESK -> ChartAppearance(
                presetId = preset.id,
                isLightTheme = true,
                backgroundArgb = 0xFFF5F7FAL,
                surfaceArgb = 0xFFFFFFFFL,
                surfaceElevatedArgb = 0xFFEBF0F6L,
                borderArgb = 0xFFD4DEE9L,
                primaryAccentArgb = 0xFF2563EBL,
                textPrimaryArgb = 0xFF142030L,
                textSecondaryArgb = 0xFF5A6B82L,
                gridColorArgb = 0xFFCBD5E1L,
                showGrid = true,
                gridAlpha = 0.65f,
                bullishCandleArgb = 0xFF1B8A6BL,
                bearishCandleArgb = 0xFFC9444AL,
                dccUpperArgb = 0xFF5B6FD1L,
                dccMidArgb = 0xFF2563EBL,
                dccLowerArgb = 0xFF5B6FD1L,
                dccBullSignalArgb = 0xFF1B8A6BL,
                dccBearSignalArgb = 0xFFC9444AL
            )
        }
    }
}

object ChartColorSwatches {
    val backgrounds: List<Pair<String, Long>> = listOf(
        "TradingView Parchment" to 0xFFF2EAE0L,
        "Midnight Slate" to 0xFF0F141CL,
        "Deep Navy" to 0xFF0C1524L,
        "Obsidian" to 0xFF0A0D12L,
        "Charcoal" to 0xFF15181EL,
        "Pure Black" to 0xFF000000L,
        "Soft Paper" to 0xFFF5F7FAL
    )

    val bullishCandles: List<Pair<String, Long>> = listOf(
        "Trading Teal" to 0xFF26A69AL,
        "Sage Emerald" to 0xFF38B28EL,
        "Mint" to 0xFF10B981L,
        "Steel Blue" to 0xFF5EA8D8L,
        "Warm Gold" to 0xFFE0B463L
    )

    val bearishCandles: List<Pair<String, Long>> = listOf(
        "Coral Red" to 0xFFEF5350L,
        "Terracotta" to 0xFFD96B70L,
        "Crimson" to 0xFFEF4444L,
        "Coral Rose" to 0xFFE05D64L,
        "Amber Orange" to 0xFFEA8A47L
    )

    val dccLineColors: List<Pair<String, Long>> = listOf(
        "Dark Navy Blue" to 0xFF1E5384L,
        "Warm Gold" to 0xFFD49A3BL,
        "Steel Cyan" to 0xFF5EA8D8L,
        "Periwinkle" to 0xFF7C90DBL,
        "Sage" to 0xFF38B28EL,
        "Lavender" to 0xFFA78BFAL,
        "Rose" to 0xFFE87990L
    )
}
