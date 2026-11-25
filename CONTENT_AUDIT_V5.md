# DL Chat V5 — Content Humanization & Localization Audit

## 1. Humanization Principles Applied

Every user-facing string across DL Chat was reviewed and rewritten so the interface reads like clear, calm, natural product writing rather than internal tooling or marketing hype:

- **Plain, direct language**: Replaced internal/military jargon (`Operator`, `Ops Room`, `Cipher Matrix`, `Vault`, `Node`, `Signal Telemetry`) with familiar everyday messaging terms (`Member`, `Saved Notes`, `Private Chats`, `Devices`, `Security`).
- **Helpful error & empty states**: Rewrote generic failures (`Authentication Failed`, `No Data Found`, `Invalid QR Payload`) into actionable guidance explaining what happened and what to try next (`We couldn't sign you in. Check your details and try again.`, `Nothing here yet.`, `This QR code isn't valid for DL Chat.`).
- **No false guarantees or hype**: Removed exclamation-heavy cheerleading, fake marketing superlatives, and vague security claims.
- **Preserved stability**: All existing `Modifier.testTag(...)` identifiers, Firebase backend hooks, and navigation routes were preserved unchanged.

---

## 2. Copy Transformation Summary (Before → After)

| Area | Earlier Copy (V4) | Humanized Copy (V5) |
| :--- | :--- | :--- |
| **Auth — Tabs & Sign-In** | `Login` / `Sign-In Failed` / `Initialize Profile` | `Sign in` / `We couldn't sign you in. Check your details and try again.` / `Create your profile` |
| **Auth — Errors** | `Missing or empty handle` / `Authentication Failed` | `Choose a username between 3 and 24 characters.` / `We couldn't sign you in right now. Try again in a moment.` |
| **Main Navigation** | `DL AI` / `Status` / `Ops Hub` | `Assistant` / `Stories` / `Chats`, `Contacts`, `Stories`, `Calls`, `Settings` |
| **Chats & Empty States** | `No Active Transmissions` / `No Data Found` | `No chats yet` / `Nothing here yet.` |
| **Member Fallbacks** | `Operator` / `Unknown Operator` | `Member` / `DL Chat member` |
| **Saved Notes & Hidden** | `Encrypted Notes Vault` / `Hidden Vault` | `Saved Notes` / `Private Chats` |
| **Calls** | `Encrypted Voice Node` / `Signaling Bridge` | `Voice Call` / `Call setup is ready; live audio and video depend on your network connection.` |
| **Contacts** | `Add Operator` / `Broadcast Identity` | `Add contact` / `Share your profile` |
| **Stories** | `Tactical Status` / `No Intel Updates` | `Stories` / `No stories from your contacts yet.` |
| **Linked Devices** | `Device Successfully Connected` / `Revoke Session Token` | `Your device is now connected.` / `Log out this device` |
| **Settings — Appearance** | `Reduced Motion (Minimize Transitions)` | `Reduce animations` |
| **Settings — Storage** | `Wi-Fi Only Media Auto-Download` | `Only download media over Wi-Fi` |
| **DL Assistant** | `Draft announcement` / `AI Tactical Core` | `Draft an update` / `DL Assistant` |
| **DL Pro** | `DL Pro Matrix` / `Unlock Maximum Velocity` | `DL Pro` / `Extra tools for power users` |

---

## 3. Multilingual Localization Architecture (`en` / `si` / `ta`)

1. **Three Complete String Resource Tables (954 keys each)**:
   - English: `app/src/main/res/values/strings.xml`
   - Sinhala (`si`): `app/src/main/res/values-si/strings.xml`
   - Tamil (`ta`): `app/src/main/res/values-ta/strings.xml`
   - Identical resource names, format placeholders (`%1$s`, `%1$d`), and escaped XML entities across all three locales.

2. **Bundled Indic Typography (`res/font/`)**:
   - `noto_sans_sinhala.ttf` (`NotoSansSinhalaFontFamily`) for `si`
   - `noto_sans_tamil.ttf` (`NotoSansTamilFontFamily`) for `ta`
   - `space_grotesk.ttf`, `plus_jakarta_sans.ttf`, `jetbrains_mono.ttf` for `en`
   - `createDlTypography(scale, language)` applies `0.sp` letter spacing and `1.12x` line-height boost for Sinhala and Tamil so complex ligatures and vowel signs never clip or overlap.

3. **Instant In-App Language Switching**:
   - `LocaleHelper.localizedContext(base, languageCode)` wraps `Resources` and `Assets` via `CompositionLocalProvider(LocalContext provides localizedContext)` inside `MainActivity` without forcing an intrusive activity recreation loop.
   - `Context.findActivity()` and `Context.findFragmentActivity()` unwrap `ContextWrapper` chains safely for BiometricPrompt (`AppLockOverlay`), Credential Manager (`MainShellScreen`), and system volume controls (`CallsScreen`).
   - Language choice (`en`, `si`, `ta`) persists immediately in Jetpack DataStore (`PreferencesRepository.saveLocalUiPreferences(appLanguage = ...)`) and is selectable on both the **Auth screen** and **Settings → Appearance**.

---

## 4. Honest Disclosures for Unprovisioned Infrastructure

Where server-side or store infrastructure is outside the client runtime, DL Chat discloses the exact boundary clearly in plain language:

1. **Voice & Video Calls (`CallsScreen` / `CallAudioRouter`)**: Call invitations, ringing state, and call history sync in real time via Firestore, while disclosing that live peer-to-peer WebRTC media relay (TURN/SFU) is not provisioned on this standalone build.
2. **Inline Media & File Attachments (`ConversationScreen` / `ChatRepository`)**: Images and attachments are compressed and stored up to **280 KB** inline; larger files require external Cloud Storage provisioning and explain this clearly if a user picks an oversized file.
3. **DL Pro Membership (`DlPremiumSheet`)**: Features and toggles can be previewed cleanly, while disclosing that Google Play Billing is not connected in this preview build (no fake payment button or simulated charge).
4. **Closed-App Push Notifications (`DlMessagingService` / `SettingsScreen`)**: Discloses that live alerts work while connected to Firebase and that background wakeups when the app is closed require an external FCM server relay.
5. **Death Legion Account Linking (`DeathLegionOAuthClient` / `SettingsScreen`)**: Launches the real OAuth authorize URL and validates state/callback URIs, while clearly noting when a backend token-exchange proxy (`CLIENT_SECRET`) is not configured in the build environment.
