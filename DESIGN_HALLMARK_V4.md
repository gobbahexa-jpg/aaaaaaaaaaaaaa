# DL Chat V4 — Hallmark UI/UX Redesign Specification

## 1. Design Methodology & Hallmark Alignment
This document records the native Android / Jetpack Compose redesign of **DL Chat** (Death Legion — *Connect Beyond Limits*), applying the editorial restraint, visual hierarchy, and anti-AI-slop methodology of [Hallmark](https://github.com/nutlope/hallmark).

> Note on environment status: Hallmark guidelines and references were studied directly and adapted natively for Jetpack Compose Material 3. No third-party CLI plugin or external skill package is claimed as installed locally.

### Core Hallmark Principles Adapted to Android Compose
1. **Subjective Clarity Over Generic Card-Soup**: Replace repetitive translucent rounded rectangle cards (`surfaceVariant.copy(alpha = 0.45f)`) across lists, settings, contacts, calls, and dashboards with clean matte surface planes separated by crisp `1.dp` hairline dividers (`LocalDlTokens.current.hairline`). Reserve elevated cards strictly for genuine focal moments (active call, live pairing QR challenge, active identity header, unread summary).
2. **Hero First, Zero Eyebrow Badge Spam**: Remove repetitive tracked uppercase eyebrow labels (`"OPERATOR LOGIN"`, `"DISCOVERABLE OPERATORS"`, `"QUICK DIAL"`) above standard content. Lead entry screens with a confident, atmospheric hero header and grounded typography (`Space Grotesk` display/headings + `Plus Jakarta Sans` UI/body + `JetBrains Mono` cryptographic fingerprints & metadata).
3. **Real Contrast & Luminance-Aware Accents**: Eliminate low-contrast white-on-neon-cyan light mode buttons by mapping every accent swatch (`Cyan`, `Indigo`, `Emerald`, `Crimson`, `Amber`) to a WCAG AA compliant light-mode primary token and luminance-aware `onPrimary` ink color.
4. **Controlled Spacing & Uncrowded Headers**: Standardize spacing on an `8.dp` grid (`DlSpacing`: `xs = 4.dp`, `sm = 8.dp`, `md = 12.dp`, `base = 16.dp`, `lg = 24.dp`, `xl = 32.dp`). Consolidate overcrowded 6-button toolbars into clean action clusters + accessible overflow actions so compact `360.dp` phones never clip or wrap labels.
5. **Respectful Motion & Reduced Motion Support**: Pair purposeful transitions with a user-configurable **Reduced Motion** preference stored in DataStore and exposed through `LocalDlTokens.current.reducedMotion`.

## 2. Visual Direction — *Obsidian Architectural Precision*
- **Dark Mode (Default)**: Deep matte carbon-obsidian canvas (`#090C11`), subtle slate surfaces (`#0F141D` / `#161D2A`), hairline borders (`#263145`), high-legibility slate-white ink (`#F2F5FA`), cool muted secondary copy (`#94A3B8`).
- **Light Mode**: Crisp alabaster daylight canvas (`#F5F7FA`), pure white foreground sheets (`#FFFFFF`), soft slate elevated wells (`#ECF1F7`), ink slate (`#0F172A`), and darkened high-contrast primary brand ink (`#007791`).
- **Brand Identity & Semantic Status**:
  - Primary Signal: Electric Cyan (`#00E5FF` dark / `#007791` light)
  - Secondary Authority: Death Legion Indigo (`#6C5CE7` / `#4F42B5`)
  - Verified / Online / E2EE Lock: Emerald (`#12B886` dark / `#058057` light)
  - Warning / Away / Expiring QR: Amber (`#F59F00` dark / `#9E6300` light)
  - Destructive / DND / Threat Alert: Crimson (`#FF4D6D` dark / `#C92A46` light)

## 3. Information Architecture & Navigation Map
### Primary Top-Level Navigation (5 Core Tabs + Persistent Header Settings)
- **Bottom Navigation Bar (Compact `< 840.dp`) / Navigation Rail (Expanded `>= 840.dp`)**:
  1. `tab_chats` — **Chats**: Unified direct, group, and subscribed channel inbox, smart folder pills, draft badges, unread divider, quick QR/Vault/Channels/Saved shortcuts, and primary compose FAB.
  2. `tab_calls` — **Calls**: Active call hero banner, favorite quick-dial strip, call log rows with inbound/outbound/missed indicators, and instant voice/video call initiation.
  3. `tab_contacts` — **Contacts**: Pending inbound request action queue, searchable Death Legion directory, favorites, blocked list, and Share My Card QR dialog.
  4. `tab_stories` — **Stories**: Status ring strip, create text/photo story sheet, stealth visibility filters, and full-screen interactive story viewer with emoji reactions & direct replies.
  5. `tab_ai` — **DL AI**: Dedicated full-screen Firebase AI Logic (Gemini) assistant with reusable prompt starter chips, streaming responses, copy action, privacy notice (skipping E2EE content), and session history reset — alongside the modal `DlAiAssistantSheet` for quick mid-chat access.
- **Persistent Settings Route (`tab_settings`)**:
  - Single-tap settings button in primary top headers (and bottom of tablet `NavigationRail`), opening `SettingsScreen` with native `BackHandler` return navigation and full category organization (`Account`, `Privacy`, `Appearance`, `Chats`, `Notifications`, `Storage`, `Devices`, `Security`, `About`).

## 4. Shared Design System Components (`DlDesignSystem.kt` & `DlCommonComponents.kt`)
- `DlTopBar`: Clean editorial header supporting avatar/back control, primary title + single concise status line, and up to 3 high-priority actions + optional overflow menu.
- `DlSearchField`: Pill/architectural search field with clear button and optional AI search trigger.
- `DlPrimaryButton`, `DlSecondaryButton`, `DlDangerButton`: Minimum `48.dp` height, consistent `12.dp` radius, built-in loading spinner state, WCAG contrast ink.
- `DlSectionHeader`, `DlListRow`, `DlSettingToggleRow`, `DlHairlineDivider`: Clean list architecture eliminating stacked card borders.
- `DlStatusPill`: Crisp status pills for `ONLINE`, `E2EE`, `ADMIN`, `DRAFT`, `EXPIRED`, `LIVE`.
- `DlAvatar`: Tonal single-hue identity circle with fine `1.dp` border and optional presence indicator dot with surface cutout ring (removing generic multi-color gradient repetition).
- `DlEmptyState`, `DlErrorState`, `DlLoadingState`: Calm, purposeful feedback surfaces with retry/action CTAs.

## 5. Preserved Integrity & Honest Boundaries
- Preserves 100% of Firebase Auth, Google Credential Manager, Death Legion OAuth2 (`DeathLegionOAuthClient`), App Check (`configureAppCheck`), Firestore security-rule whitelisted models (`DlModels.kt`), `DlChatViewModel`, `DlChatV3Coordinator`, `CryptoIdentityHelper`, `SecurityPrivacyManager`, and `DlAiRepository`.
- Stores V4 client-only UI/notification preferences (`reducedMotion`, granular notification channel toggles, Wi-Fi auto-download, compact density) cleanly in Jetpack DataStore (`PreferencesRepository`) so synced Firestore `UserSetting` writes always conform strictly to `firestore.rules` field whitelists.
- Maintains honest disclosure of unprovisioned infrastructure (WebRTC TURN/SFU relay for calls across networks, Cloud Storage >280KB media upload, confidential OAuth backend proxy, Play Billing, offline push FCM relay).
