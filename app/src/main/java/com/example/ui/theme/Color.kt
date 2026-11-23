package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Midnight Slate Default Palette Tokens
val SlateCanvas = Color(0xFF0F141C)
val SlateSurface = Color(0xFF161D28)
val SlateSurfaceElevated = Color(0xFF1E2736)
val SlateBorder = Color(0xFF263244)
val SlatePrimaryAccent = Color(0xFF5EA8D8)
val SlateSecondaryAccent = Color(0xFF7C90DB)
val SlateAmberAccent = Color(0xFFE0B463)
val SlateBullish = Color(0xFF38B28E)
val SlateBearish = Color(0xFFD96B70)
val SlateTextPrimary = Color(0xFFE6EDF5)
val SlateTextSecondary = Color(0xFF8E9EB3)

fun Long.toComposeColor(): Color = Color(this.toInt())
