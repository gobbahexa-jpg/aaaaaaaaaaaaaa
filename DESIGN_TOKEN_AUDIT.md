# DL Chat — Compose UI Component Audit & Unified Design Token System

## 1. Comprehensive UI Component Audit

An audit of all 26 UI files across `ui/theme/`, `ui/components/`, `ui/auth/`, and `ui/screens/` identified five structural gaps prior to unification:

| Finding | Locations Identified | Remediation Implemented |
| :--- | :--- | :--- |
| **Hardcoded overlay, brand & code colors** | `LoginScreen.kt`, `CallsScreen.kt`, `StoriesScreen.kt`, `LinkedDevicesScreen.kt`, `QrScannerDialog.kt`, `RichMessageText.kt`, `SettingsScreen.kt` (`0xFF070A10`, `0xFF101827`, `0xFF00E5FF`, `0xFFB3312C`, `0xFF0A0E16`, `0xFF8BE9FD`, `Color.White`, `Color.Black`) | Named palette tokens in `Color.kt` + semantic `DlTokens` fields (`overlayBackdrop`, `overlayInk`, `overlaySurface`, `overlayAccent`, `qrCanvas`, `brandLegion`, `codeSurface`, `codeSyntax`, etc.). |
| **Duplicate toggle component** | Private `SettingToggleRow` inside `SettingsScreen.kt` duplicating `DlDesignSystem.kt` row behavior | Consolidated into shared `DlInlineToggleRow` inside `DlDesignSystem.kt`; removed the private duplicate. |
| **Inconsistent corner radii & touch/avatar sizing** | Inline `2.dp`–`26.dp` radii and `36.dp`–`104.dp` sizes across `DlDesignSystem.kt`, `DlCommonComponents.kt`, `ConversationScreen.kt`, `LoginScreen.kt`, `CallsScreen.kt` | Formalized `DlRadius` and `DlSizing` token objects exposed via `DlTheme.radius` and `DlTheme.sizing`. |
| **Missing Material 3 & messaging typography roles** | `Type.kt` omitted `titleSmall` and lacked dedicated styles for message bubbles, senders, tabular timestamps, previews, unread counters, and code blocks | Added `titleSmall` to `createDlTypography()` and created `DlMessagingTypography` (`createDlMessagingTypography`) attached to `DlTokens.messagingType`. |
| **Inline message bubble styling** | `ConversationScreen.kt` & `ChatsListPane.kt` mixed raw corner radii, padding, and surface fallbacks | Migrated bubbles, reply quotes, chat rows, and timestamps to `DlSpacing`, `DlRadius`, `DlSizing`, and `DlTokens` messaging tokens. |

---

## 2. Unified Design Token Architecture (`DlTheme`)

Access all tokens inside any `@Composable` via **`DlTheme.tokens`** (`LocalDlTokens.current`), **`DlTheme.spacing`**, **`DlTheme.radius`**, **`DlTheme.sizing`**, **`DlTheme.elevation`**, **`MaterialTheme.colorScheme`**, and **`MaterialTheme.typography`**.

### A. Color Palettes (`ui/theme/Color.kt` & `Theme.kt`)

| Token Category | Dark ("Obsidian Architectural") | Light ("Alabaster Daylight") | Usage |
| :--- | :--- | :--- | :--- |
| **Canvas / Background** | `#090C11` (`DlObsidianBg`) | `#F5F7FA` (`DlLightBg`) | Root scaffold & backdrop |
| **Primary Surface** | `#0F141D` (`DlSurfaceDark`) | `#FFFFFF` (`DlLightSurface`) | Top bars, bottom nav, list rows |
| **Elevated Surface** | `#161D2A` (`DlSurfaceElevated`) | `#ECF1F7` (`DlLightElevated`) | Cards, search fields, incoming bubbles |
| **Card / Code Container** | `#1D2636` (`DlSurfaceCard`) | `#E3EAF3` (`DlLightCard`) | Nested surfaces, selected containers |
| **Hairline Outline** | `#263145` (`DlOutlineDark`) | `#D5DFEC` (`DlLightOutline`) | 1.dp dividers & card borders |
| **Text Ink Hierarchy** | `#F2F5FA` / `#94A3B8` / `#64748B` | `#0F172A` / `#475569` / `#64748B` | `textPrimary`, `textSecondary`, `textTertiary` |
| **Semantic Status** | Online `#12B886`, Away `#F59F00`, Busy `#FF4D6D` | Online `#058057`, Away `#9E6300`, Busy `#C92A46` | Presence dots, active states, errors (WCAG AA) |
| **Brand & Accents** | Cyan `#00E5FF`, Indigo `#6C5CE7`, Legion `#B3312C` | Cyan `#007791`, Indigo `#4F42B5`, Legion `#B3312C` | Adaptive primary accent (`resolveAccessibleAccent`) + `contrastInkFor()` |
| **Immersive Overlays** | Backdrop `#070A10` → `#101827`, Ink `#FFFFFF`, Accent `#00E5FF` | Backdrop `#070A10` → `#101827`, Ink `#FFFFFF`, Accent `#00E5FF` | Active calls, full-screen stories, auth hero, camera scrim |
| **Code & QR Surfaces** | Code `#0A0E16` / `#121826`, Syntax `#8BE9FD`, QR `#FFFFFF` | Code `#0A0E16` / `#121826`, Syntax `#8BE9FD`, QR `#FFFFFF` | Monospace code blocks & high-contrast scannable QR matrices |

### B. Typography Scale (`ui/theme/Type.kt`)

Latin screens pair **Space Grotesk** (headings/labels) with **Plus Jakarta Sans** (body) and **JetBrains Mono** (tabular metadata/code). Sinhala (`si`) and Tamil (`ta`) automatically switch to bundled **Noto Sans Sinhala** / **Noto Sans Tamil** with `0.sp` letter spacing and `1.12x` line-height boost.

| Token | Weight | Size / Line Height | Purpose |
| :--- | :--- | :--- | :--- |
| `displaySmall` | Bold | `28.sp` / `34.sp` | Auth hero & active call peer title |
| `headlineMedium` | Bold | `22.sp` / `28.sp` | Story overlays & sheet titles |
| `titleLarge` | SemiBold | `19.sp` / `25.sp` | Top bars & empty state headlines |
| `titleMedium` | SemiBold | `16.sp` / `22.sp` | List titles, dialogs, poll questions |
| `titleSmall` | SemiBold | `14.sp` / `20.sp` | File headers & compact card titles |
| `bodyLarge` | Normal | `15.sp` / `22.sp` | Primary reading copy |
| `bodyMedium` | Normal | `14.sp` / `20.sp` | Standard inputs, toggles, empty-state copy |
| `bodySmall` | Normal | `12.sp` / `17.sp` | Supporting hints & error messages |
| `messagingType.messageBody` | Normal | `15.sp` / `21.sp` | Conversation message text |
| `messagingType.messageSender` | SemiBold | `12.sp` / `16.sp` | Group/channel sender attribution |
| `messagingType.messageTimestamp` | Medium (Tabular) | `11.sp` / `14.sp` | Chat list & bubble timestamps (`tnum`) |
| `messagingType.chatTitle` | SemiBold | `16.sp` / `22.sp` | Conversation list row title |
| `messagingType.chatPreview` | Normal | `13.sp` / `18.sp` | Last message, draft, & typing preview |
| `messagingType.unreadCount` | Bold (Tabular) | `11.sp` / `14.sp` | Unread pill & badge counters |
| `messagingType.sectionOverline` | SemiBold | `12.sp` / `16.sp` | Section dividers (`DlSectionHeader`) |
| `messagingType.codeBlock` | Normal | `12.sp` / `17.sp` | Monospace code snippets |

### C. Spacing, Radius, Sizing, Elevation & Motion (`ui/theme/Theme.kt`)

- **Spacing (`DlSpacing`)**: `xxs (2.dp)`, `xs (4.dp)`, `sm (8.dp)`, `md (12.dp)`, `base (16.dp)`, `lg (24.dp)`, `xl (32.dp)`, `xxl (48.dp)`, plus messaging layout constraints (`bubblePaddingH = 12.dp`, `bubblePaddingV = 9.dp`, `bubbleMaxWidth = 320.dp`, `screenMaxWidth = 600.dp`, `rowMinHeight = 52.dp`, `touchTargetMin = 48.dp`, `rowVertical(compact)`).
- **Corner Radii (`DlRadius`)**: `xxs (2.dp)`, `bubbleTail (4.dp)`, `bubbleGrouped (6.dp)`, `xs (6.dp)`, `sm (10.dp)`, `md (12.dp)`, `pillInput (14.dp)`, `lg (16.dp)`, `sheet (18.dp)`, `xl (20.dp)`, `hero (22.dp)`, `xxl (26.dp)`.
- **Component Sizing (`DlSizing`)**: `touchTarget (48.dp)`, `topBarHeight (60.dp)`, `divider (1.dp)`, icons (`iconXs 14.dp` → `iconLg 28.dp`), avatars (`avatarSm 36.dp`, `avatarMd 46.dp`, `avatarChat 48.dp`, `avatarLg 56.dp`, `avatarHero 104.dp`).
- **Elevation & Motion (`DlElevation`, `DlMotion`)**: Flat architectural surfaces (`none 0.dp` + `1.dp` hairline borders) for list/card hierarchy; `sheet (6.dp)` and `modal (12.dp)` for overlays; `DlMotion.duration(baseMs, reducedMotion)` collapses transitions cleanly when reduced motion is enabled.
