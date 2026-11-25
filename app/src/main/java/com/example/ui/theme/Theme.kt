package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object DlSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val base: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp

    // Messaging & responsive layout spacing tokens
    val bubblePaddingH: Dp = 12.dp
    val bubblePaddingV: Dp = 9.dp
    val bubbleMaxWidth: Dp = 320.dp
    val screenMaxWidth: Dp = 600.dp
    val rowMinHeight: Dp = 52.dp
    val touchTargetMin: Dp = 48.dp

    fun rowVertical(compact: Boolean): Dp = if (compact) 9.dp else 13.dp
}

object DlRadius {
    val xxs: Dp = 2.dp
    val bubbleTail: Dp = 4.dp
    val bubbleGrouped: Dp = 6.dp
    val xs: Dp = 6.dp
    val sm: Dp = 10.dp
    val md: Dp = 12.dp
    val pillInput: Dp = 14.dp
    val lg: Dp = 16.dp
    val sheet: Dp = 18.dp
    val xl: Dp = 20.dp
    val hero: Dp = 22.dp
    val xxl: Dp = 26.dp
}

object DlSizing {
    val touchTarget: Dp = 48.dp
    val topBarHeight: Dp = 60.dp
    val divider: Dp = 1.dp

    val iconXs: Dp = 14.dp
    val iconSm: Dp = 18.dp
    val iconMd: Dp = 22.dp
    val iconLg: Dp = 28.dp

    val avatarSm: Dp = 36.dp
    val avatarMd: Dp = 46.dp
    val avatarChat: Dp = 48.dp
    val avatarLg: Dp = 56.dp
    val avatarHero: Dp = 104.dp
}

object DlElevation {
    val none: Dp = 0.dp
    val subtle: Dp = 1.dp
    val card: Dp = 2.dp
    val sheet: Dp = 6.dp
    val modal: Dp = 12.dp
}

object DlMotion {
    const val fastMs: Int = 150
    const val standardMs: Int = 240
    fun duration(baseMs: Int, reducedMotion: Boolean): Int = if (reducedMotion) 0 else baseMs
}

@Immutable
data class DlTokens(
    val isDark: Boolean = true,
    val hairline: Color = DlOutlineDark,
    val surfaceSubtle: Color = DlSurfaceDark,
    val surfaceElevated: Color = DlSurfaceElevated,
    val surfaceCard: Color = DlSurfaceCard,
    val textPrimary: Color = DlTextPrimaryDark,
    val textSecondary: Color = DlTextSecondaryDark,
    val textTertiary: Color = DlTextTertiaryDark,
    val statusOnline: Color = DlEmerald,
    val statusAway: Color = DlAmber,
    val statusBusy: Color = DlCrimson,
    val badgeUnread: Color = DlCyan,
    val onBadgeUnread: Color = Color(0xFF070B12),
    val reducedMotion: Boolean = false,
    val compactDensity: Boolean = false,
    // Brand & Code Tokens
    val brandLegion: Color = DlLegionRed,
    val onBrandLegion: Color = DlLegionOnRed,
    val codeSurface: Color = DlCodeSurface,
    val codeHeaderSurface: Color = DlCodeHeaderSurface,
    val codeBorder: Color = DlOutlineDark,
    val codeSyntax: Color = DlCodeSyntaxText,
    val codeMutedInk: Color = DlCodeMutedInk,
    // Immersive Overlay & QR Tokens (Calls, Stories, Auth Hero, QR Matrix)
    val overlayBackdrop: Color = DlOverlayBackdrop,
    val overlayBackdropMid: Color = DlOverlayBackdropMid,
    val overlayInk: Color = DlOverlayInk,
    val overlayInkMuted: Color = DlOverlayInk.copy(alpha = 0.78f),
    val overlaySurface: Color = DlOverlayInk.copy(alpha = 0.14f),
    val overlayBorder: Color = DlOverlayInk.copy(alpha = 0.16f),
    val overlayAccent: Color = DlCyan,
    val qrCanvas: Color = DlQrCanvasLight,
    // Messaging Surfaces & Bubbles
    val bubbleIncoming: Color = DlSurfaceElevated,
    val bubbleIncomingBorder: Color = DlOutlineDark,
    val replySurface: Color = DlSurfaceDark.copy(alpha = 0.85f),
    val messagingType: DlMessagingTypography = DefaultMessagingTypography
)

val LocalDlTokens = staticCompositionLocalOf { DlTokens() }

object DlTheme {
    val tokens: DlTokens
        @Composable get() = LocalDlTokens.current
    val spacing: DlSpacing = DlSpacing
    val radius: DlRadius = DlRadius
    val sizing: DlSizing = DlSizing
    val elevation: DlElevation = DlElevation
}

val DlShapes = Shapes(
    small = RoundedCornerShape(DlRadius.sm),
    medium = RoundedCornerShape(DlRadius.pillInput),
    large = RoundedCornerShape(DlRadius.xl),
    extraLarge = RoundedCornerShape(DlRadius.xxl)
)

@Composable
fun DlChatTheme(
    themeMode: String = "DARK",
    accentColorHex: String = "#00E5FF",
    fontScale: Float = 1f,
    reducedMotion: Boolean = false,
    compactDensity: Boolean = false,
    appLanguage: String = "en",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }

    val accent = resolveAccessibleAccent(accentColorHex, isDark)
    val onAccent = contrastInkFor(accent)
    val messagingTypography = createDlMessagingTypography(fontScale, appLanguage)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.16f),
            onPrimaryContainer = accent,
            secondary = DlIndigo,
            onSecondary = DlOverlayInk,
            secondaryContainer = DlIndigoContainer,
            onSecondaryContainer = DlIndigoOnContainerDark,
            tertiary = DlEmerald,
            onTertiary = DlOnEmeraldDark,
            background = DlObsidianBg,
            onBackground = DlTextPrimaryDark,
            surface = DlSurfaceDark,
            onSurface = DlTextPrimaryDark,
            surfaceVariant = DlSurfaceElevated,
            onSurfaceVariant = DlTextSecondaryDark,
            surfaceContainerHigh = DlSurfaceCard,
            outline = DlOutlineDark,
            outlineVariant = DlOutlineDark.copy(alpha = 0.72f),
            error = DlCrimson,
            onError = DlOverlayInk,
            errorContainer = DlErrorContainerDark,
            onErrorContainer = DlOnErrorContainerDark
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.12f),
            onPrimaryContainer = accent,
            secondary = DlLightSecondary,
            onSecondary = DlOverlayInk,
            secondaryContainer = DlIndigoContainerLight,
            onSecondaryContainer = DlLightSecondary,
            tertiary = DlEmeraldDark,
            onTertiary = DlOverlayInk,
            background = DlLightBg,
            onBackground = DlTextPrimaryLight,
            surface = DlLightSurface,
            onSurface = DlTextPrimaryLight,
            surfaceVariant = DlLightElevated,
            onSurfaceVariant = DlTextSecondaryLight,
            surfaceContainerHigh = DlLightCard,
            outline = DlLightOutline,
            outlineVariant = DlLightOutline,
            error = DlCrimsonDark,
            onError = DlOverlayInk,
            errorContainer = DlErrorContainerLight,
            onErrorContainer = DlOnErrorContainerLight
        )
    }

    val dlTokens = if (isDark) {
        DlTokens(
            isDark = true,
            hairline = DlOutlineDark,
            surfaceSubtle = DlSurfaceDark,
            surfaceElevated = DlSurfaceElevated,
            surfaceCard = DlSurfaceCard,
            textPrimary = DlTextPrimaryDark,
            textSecondary = DlTextSecondaryDark,
            textTertiary = DlTextTertiaryDark,
            statusOnline = DlEmerald,
            statusAway = DlAmber,
            statusBusy = DlCrimson,
            badgeUnread = accent,
            onBadgeUnread = onAccent,
            reducedMotion = reducedMotion,
            compactDensity = compactDensity,
            brandLegion = DlLegionRed,
            onBrandLegion = DlLegionOnRed,
            codeSurface = DlCodeSurface,
            codeHeaderSurface = DlCodeHeaderSurface,
            codeBorder = DlOutlineDark,
            codeSyntax = DlCodeSyntaxText,
            codeMutedInk = DlCodeMutedInk,
            overlayBackdrop = DlOverlayBackdrop,
            overlayBackdropMid = DlOverlayBackdropMid,
            overlayInk = DlOverlayInk,
            overlayInkMuted = DlOverlayInk.copy(alpha = 0.78f),
            overlaySurface = DlOverlayInk.copy(alpha = 0.14f),
            overlayBorder = DlOverlayInk.copy(alpha = 0.16f),
            overlayAccent = DlCyan,
            qrCanvas = DlQrCanvasLight,
            bubbleIncoming = DlSurfaceElevated,
            bubbleIncomingBorder = DlOutlineDark,
            replySurface = DlSurfaceDark.copy(alpha = 0.85f),
            messagingType = messagingTypography
        )
    } else {
        DlTokens(
            isDark = false,
            hairline = DlLightOutline,
            surfaceSubtle = DlLightSurface,
            surfaceElevated = DlLightElevated,
            surfaceCard = DlLightCard,
            textPrimary = DlTextPrimaryLight,
            textSecondary = DlTextSecondaryLight,
            textTertiary = DlTextTertiaryLight,
            statusOnline = DlEmeraldDark,
            statusAway = DlAmberDark,
            statusBusy = DlCrimsonDark,
            badgeUnread = accent,
            onBadgeUnread = onAccent,
            reducedMotion = reducedMotion,
            compactDensity = compactDensity,
            brandLegion = DlLegionRed,
            onBrandLegion = DlLegionOnRed,
            codeSurface = DlCodeSurface,
            codeHeaderSurface = DlCodeHeaderSurface,
            codeBorder = DlCodeBorderLight,
            codeSyntax = DlCodeSyntaxText,
            codeMutedInk = DlCodeMutedInk,
            overlayBackdrop = DlOverlayBackdrop,
            overlayBackdropMid = DlOverlayBackdropMid,
            overlayInk = DlOverlayInk,
            overlayInkMuted = DlOverlayInk.copy(alpha = 0.78f),
            overlaySurface = DlOverlayInk.copy(alpha = 0.14f),
            overlayBorder = DlOverlayInk.copy(alpha = 0.16f),
            overlayAccent = DlCyan,
            qrCanvas = DlQrCanvasLight,
            bubbleIncoming = DlLightElevated,
            bubbleIncomingBorder = DlLightOutline,
            replySurface = DlLightSurface.copy(alpha = 0.92f),
            messagingType = messagingTypography
        )
    }

    CompositionLocalProvider(LocalDlTokens provides dlTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = createDlTypography(fontScale, appLanguage),
            shapes = DlShapes,
            content = content
        )
    }
}
