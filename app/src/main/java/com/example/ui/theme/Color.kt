package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Unified Design Token Color Palettes ("Obsidian Architectural" Dark & "Alabaster Daylight" Light)

// Dark Neutral & Surface Hierarchy
val DlObsidianBg = Color(0xFF090C11)
val DlSurfaceDark = Color(0xFF0F141D)
val DlSurfaceElevated = Color(0xFF161D2A)
val DlSurfaceCard = Color(0xFF1D2636)
val DlOutlineDark = Color(0xFF263145)
val DlTextPrimaryDark = Color(0xFFF2F5FA)
val DlTextSecondaryDark = Color(0xFF94A3B8)
val DlTextTertiaryDark = Color(0xFF64748B)

// Light Neutral & Surface Hierarchy
val DlLightBg = Color(0xFFF5F7FA)
val DlLightSurface = Color(0xFFFFFFFF)
val DlLightElevated = Color(0xFFECF1F7)
val DlLightCard = Color(0xFFE3EAF3)
val DlLightOutline = Color(0xFFD5DFEC)
val DlTextPrimaryLight = Color(0xFF0F172A)
val DlTextSecondaryLight = Color(0xFF475569)
val DlTextTertiaryLight = Color(0xFF64748B)
val DlLightPrimary = Color(0xFF007791)
val DlLightSecondary = Color(0xFF4F42B5)

// Brand & Semantic Status Accents (Dark + High-Contrast Light Equivalents)
val DlCyan = Color(0xFF00E5FF)
val DlCyanContainer = Color(0xFF062C36)
val DlIndigo = Color(0xFF6C5CE7)
val DlIndigoContainer = Color(0xFF241E4A)
val DlIndigoOnContainerDark = Color(0xFFDED9FF)
val DlIndigoContainerLight = Color(0xFFEAE6FF)

val DlEmerald = Color(0xFF12B886)
val DlEmeraldDark = Color(0xFF058057)
val DlOnEmeraldDark = Color(0xFF04140D)

val DlAmber = Color(0xFFF59F00)
val DlAmberDark = Color(0xFF9E6300)

val DlCrimson = Color(0xFFFF4D6D)
val DlCrimsonDark = Color(0xFFC92A46)
val DlErrorContainerDark = Color(0xFF471522)
val DlOnErrorContainerDark = Color(0xFFFFB3C0)
val DlErrorContainerLight = Color(0xFFFFE4E8)
val DlOnErrorContainerLight = Color(0xFF8C142B)

val DlLegionRed = Color(0xFFB3312C)
val DlLegionOnRed = Color(0xFFFFFFFF)

// Immersive Overlay, QR Canvas & Monospace Code Surface Tokens
val DlOverlayBackdrop = Color(0xFF070A10)
val DlOverlayBackdropMid = Color(0xFF101827)
val DlOverlayInk = Color(0xFFFFFFFF)
val DlQrCanvasLight = Color(0xFFFFFFFF)

val DlCodeSurface = Color(0xFF0A0E16)
val DlCodeHeaderSurface = Color(0xFF121826)
val DlCodeBorderLight = Color(0xFF283349)
val DlCodeSyntaxText = Color(0xFF8BE9FD)
val DlCodeMutedInk = Color(0xFF94A3B8)

val AccentColorOptions = listOf(
    "Cyan" to "#00E5FF",
    "Indigo" to "#6C5CE7",
    "Emerald" to "#00D68F",
    "Crimson" to "#FF4D6D",
    "Amber" to "#FFB800"
)

fun parseHexColor(hex: String, fallback: Color = DlCyan): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val value = cleaned.toLong(16)
        when (cleaned.length) {
            6 -> Color(0xFF000000L or value)
            8 -> Color(value)
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Resolves a WCAG AA legible primary accent color for Light or Dark backgrounds.
 * Bright neon accents used in Dark Mode are automatically mapped to rich deep equivalents on Light surfaces.
 */
fun resolveAccessibleAccent(hex: String, isDark: Boolean): Color {
    if (isDark) return parseHexColor(hex, DlCyan)
    return when (hex.uppercase()) {
        "#00E5FF" -> Color(0xFF007791)
        "#6C5CE7" -> Color(0xFF4F42B5)
        "#00D68F", "#12B886" -> Color(0xFF058057)
        "#FF4D6D" -> Color(0xFFC92A46)
        "#FFB800", "#F59F00" -> Color(0xFF9E6300)
        else -> {
            val parsed = parseHexColor(hex, DlLightPrimary)
            if (parsed.luminance() > 0.42f) {
                Color(
                    red = parsed.red * 0.55f,
                    green = parsed.green * 0.55f,
                    blue = parsed.blue * 0.55f,
                    alpha = 1f
                )
            } else parsed
        }
    }
}

/**
 * Returns high-contrast ink (`onAccent`) for text/icons rendered directly on top of a colored background.
 */
fun contrastInkFor(background: Color): Color {
    return if (background.luminance() > 0.38f) Color(0xFF070B12) else Color.White
}
