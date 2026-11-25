package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.core.CountryDialCode
import com.example.core.DeathLegionOAuthClient
import com.example.core.LocalStorageManager
import com.example.core.PhoneNumberHelper
import com.example.core.QrCodeUtils
import com.example.core.SecurityPrivacyManager
import com.example.core.StorageBreakdown
import com.example.core.findActivity
import com.example.data.model.BackupRecord
import com.example.data.model.DeviceKeyBundle
import com.example.data.model.DeviceNotification
import com.example.data.model.DevicePairingSession
import com.example.data.model.DeviceSession
import com.example.data.model.DlAccountStatus
import com.example.data.model.DlStaffRole
import com.example.data.model.OnlineStatus
import com.example.data.model.PhoneAuthProfile
import com.example.data.model.SavedMessage
import com.example.data.model.SettingsCategory
import com.example.data.model.SyncEvent
import com.example.data.model.UserEnforcement
import com.example.data.model.UserProfile
import com.example.data.model.UserSetting
import com.example.data.repository.AppearancePrefs
import com.example.data.repository.CreatedPairingChallenge
import com.example.data.repository.FirebaseSmsOtpProvider
import com.example.data.repository.OtpSendResult
import com.example.ui.auth.CountryPickerDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlDangerButton
import com.example.ui.components.DlFocalCard
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlInlineToggleRow
import com.example.ui.components.DlLanguageSelector
import com.example.ui.components.DlPrimaryButton
import com.example.ui.components.DlSecondaryButton
import com.example.ui.components.DlSectionHeader
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.components.DlTopBar
import com.example.ui.components.QrDisplayDialog
import com.example.ui.components.formatRelativeTimeLocalized
import com.example.ui.components.localizedLabel
import com.example.ui.components.localizedTitle
import com.example.ui.theme.AccentColorOptions
import com.example.ui.theme.DlCrimson
import com.example.ui.theme.DlRadius
import com.example.ui.theme.DlSizing
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDlTokens
import com.example.ui.theme.contrastInkFor
import com.example.ui.theme.resolveAccessibleAccent

@Composable
fun SettingsScreen(
    myProfile: UserProfile?,
    settings: UserSetting?,
    devices: List<DeviceSession>,
    savedMessages: List<SavedMessage>,
    backups: List<BackupRecord>,
    storageBreakdown: StorageBreakdown,
    isAdminUser: Boolean,
    onUpdateProfile: (
        username: String,
        displayName: String,
        bio: String,
        avatarColor: String,
        avatarUri: Uri?,
        onlineStatus: String,
        showLastSeen: Boolean,
        showOnline: Boolean,
        readReceipts: Boolean,
        discoverable: Boolean,
        twoFactorEnabled: Boolean,
        profilePhotoVisibility: String
    ) -> Unit,
    onUpdateSettings: (UserSetting) -> Unit,
    onRevokeDevice: (String) -> Unit,
    onAddPersonalNote: (String) -> Unit,
    onDeleteSaved: (String) -> Unit,
    onRefreshStorage: () -> Unit,
    onClearLocalCache: () -> Unit,
    onRotateKeys: () -> Unit,
    onExportBackup: (Uri, String) -> Unit,
    onRestoreBackup: (Uri, String) -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
    currentDeviceId: String = "",
    pairingSessions: List<DevicePairingSession> = emptyList(),
    deviceKeyBundles: List<DeviceKeyBundle> = emptyList(),
    deviceNotifications: List<DeviceNotification> = emptyList(),
    activeChallenge: CreatedPairingChallenge? = null,
    pendingSyncCount: Int = 0,
    syncEvents: List<SyncEvent> = emptyList(),
    localAccountsCount: Int = 1,
    onGeneratePairingQr: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onCancelPairing: (String) -> Unit = {},
    onConfirmScannedQr: (String) -> Unit = {},
    onConfirmManualPairing: (String, String) -> Unit = { _, _ -> },
    onRenameDevice: (String, String) -> Unit = { _, _ -> },
    onRotateDeviceToken: (DeviceSession) -> Unit = {},
    onRevokeAllOtherDevices: () -> Unit = {},
    onMarkNotificationRead: (String) -> Unit = {},
    onFlushOfflineSync: () -> Unit = {},
    onLinkDeathLegionToken: (String) -> Unit = {},
    onCompletePendingDeathLegionCode: () -> Unit = {},
    onRefreshDeathLegionSession: () -> Unit = {},
    onIntrospectDeathLegionSession: () -> Unit = {},
    onDisconnectDeathLegion: () -> Unit = {},
    onOpenAccountSwitcher: () -> Unit = {},
    onUpdateStatusMessage: (String) -> Unit = {},
    localAppearancePrefs: AppearancePrefs = AppearancePrefs(),
    onUpdateLocalUiPrefs: (
        reducedMotion: Boolean?,
        messageNotifications: Boolean?,
        groupNotifications: Boolean?,
        callNotifications: Boolean?,
        vibrationEnabled: Boolean?,
        wifiOnlyMedia: Boolean?,
        compactDensity: Boolean?
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onSelectLanguage: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
    staffRole: DlStaffRole = DlStaffRole.USER,
    myEnforcement: UserEnforcement? = null,
    phoneAuthProfile: PhoneAuthProfile? = null,
    unreadWarningsCount: Int = 0,
    openTicketsCount: Int = 0,
    onOpenAccountStatus: () -> Unit = {},
    onOpenSupportCenter: () -> Unit = {},
    onOpenPolicies: () -> Unit = {},
    onLinkPhoneCredential: ((PhoneAuthCredential, CountryDialCode, Boolean, (Boolean, String?) -> Unit) -> Unit)? = null,
    onUpdateWhatsAppOptIn: (Boolean) -> Unit = {}
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    val profile = myProfile ?: UserProfile()
    val currentSettings = settings ?: UserSetting(userId = profile.userId)

    var selectedCategory by remember { mutableStateOf(SettingsCategory.ACCOUNT) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showSavedVault by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showPremiumSheet by remember { mutableStateOf(false) }
    var showLegionLinkDialog by remember { mutableStateOf(false) }
    var showLinkPhoneDialog by remember { mutableStateOf(false) }
    var hasPin by remember(profile.userId) {
        mutableStateOf(
            profile.userId.isNotBlank() && SecurityPrivacyManager.hasPinSet(context, profile.userId)
        )
    }

    var pendingBackupUri by remember { mutableStateOf<Uri?>(null) }
    var backupMode by remember { mutableStateOf("EXPORT") }
    var showPassphraseDialog by remember { mutableStateOf(false) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            pendingBackupUri = uri
            backupMode = "EXPORT"
            showPassphraseDialog = true
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingBackupUri = uri
            backupMode = "RESTORE"
            showPassphraseDialog = true
        }
    }

    val apkScope = rememberCoroutineScope()
    val apkInfo = remember(context) { LocalStorageManager.inspectInstalledApk(context) }
    var isExportingApk by remember { mutableStateOf(false) }
    var apkExportStatus by remember { mutableStateOf<String?>(null) }
    var apkExportError by remember { mutableStateOf(false) }

    val createApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        if (uri != null) {
            apkScope.launch {
                isExportingApk = true
                apkExportError = false
                apkExportStatus = context.getString(R.string.settings_apk_exporting)
                val result = LocalStorageManager.exportInstalledApk(
                    context = context,
                    destinationUri = uri,
                    sourcePathOverride = apkInfo.sourcePath
                )
                result.onSuccess { bytes ->
                    apkExportError = false
                    apkExportStatus = context.getString(
                        R.string.settings_apk_export_success,
                        LocalStorageManager.formatByteCount(bytes)
                    )
                }.onFailure {
                    apkExportError = true
                    apkExportStatus = if (apkInfo.sourcePath == null) {
                        context.getString(R.string.settings_apk_export_unavailable)
                    } else {
                        context.getString(R.string.settings_apk_export_failed)
                    }
                }
                isExportingApk = false
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(selectedCategory) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DlTopBar(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
            onBack = onBack
        )

        // Sticky Category Pill Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = DlSpacing.base, vertical = DlSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category.localizedTitle()) },
                    modifier = Modifier.testTag("settings_cat_${category.name.lowercase()}")
                )
            }
        }
        DlHairlineDivider()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("settings_lazy_column"),
            contentPadding = PaddingValues(DlSpacing.base),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedCategory) {
                SettingsCategory.ACCOUNT -> {
                    item {
                        val resolvedName = profile.displayName.ifBlank { stringResource(R.string.common_member_fallback) }
                        DlFocalCard(
                            accentBorder = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { showProfileDialog = true }
                                .testTag("edit_profile_card")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DlAvatar(
                                    name = resolvedName,
                                    colorHex = profile.avatarColor,
                                    photoBase64 = profile.photoDataBase64,
                                    onlineStatus = profile.onlineStatus,
                                    size = 58.dp
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = resolvedName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = tokens.textPrimary
                                    )
                                    Text(
                                        text = "@${profile.username.ifBlank { "member" }} · ${OnlineStatus.fromRaw(profile.onlineStatus).localizedLabel()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (profile.bio.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = profile.bio,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.textSecondary
                                        )
                                    }
                                    if (profile.statusMessage.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        DlStatusPill(
                                            text = profile.statusMessage,
                                            color = tokens.statusOnline
                                        )
                                    }
                                }
                                IconButton(onClick = { showQrDialog = true }) {
                                    Icon(Icons.Filled.QrCode2, contentDescription = stringResource(R.string.contacts_share_cd))
                                }
                            }
                        }
                    }

                    item {
                        var statusDraft by remember(profile.statusMessage) { mutableStateOf(profile.statusMessage) }
                        val focusPreset = stringResource(R.string.settings_status_preset_focus)
                        val meetingPreset = stringResource(R.string.settings_status_preset_meeting)
                        val gamingPreset = stringResource(R.string.settings_status_preset_gaming)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_presence_quick_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.textSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OnlineStatus.entries.forEach { st ->
                                    FilterChip(
                                        selected = profile.onlineStatus.equals(st.name, ignoreCase = true),
                                        onClick = {
                                            onUpdateProfile(
                                                profile.username, profile.displayName, profile.bio, profile.avatarColor,
                                                null, st.name, profile.showLastSeen, profile.showOnline,
                                                profile.readReceipts, profile.discoverable, profile.twoFactorEnabled, profile.profilePhotoVisibility
                                            )
                                        },
                                        label = { Text(st.localizedLabel()) },
                                        modifier = Modifier.testTag("presence_chip_${st.name.lowercase()}")
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DlTextField(
                                    value = statusDraft,
                                    onValueChange = { statusDraft = it },
                                    label = stringResource(R.string.settings_custom_status_label),
                                    modifier = Modifier.weight(1f)
                                )
                                DlPrimaryButton(
                                    text = stringResource(R.string.settings_set_btn),
                                    onClick = { onUpdateStatusMessage(statusDraft) }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(focusPreset, meetingPreset, gamingPreset).forEach { preset ->
                                    AssistChip(
                                        onClick = {
                                            statusDraft = preset
                                            onUpdateStatusMessage(preset)
                                        },
                                        label = { Text(preset) }
                                    )
                                }
                                if (profile.statusMessage.isNotBlank() || statusDraft.isNotBlank()) {
                                    AssistChip(
                                        onClick = {
                                            statusDraft = ""
                                            onUpdateStatusMessage("")
                                        },
                                        label = { Text(stringResource(R.string.settings_status_preset_clear)) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        val tierLabel = stringResource(
                            if (profile.premiumTier.equals("PRO", ignoreCase = true)) {
                                R.string.settings_pro_tier_pro
                            } else {
                                R.string.settings_pro_tier_free
                            }
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DlSecondaryButton(
                                    text = stringResource(R.string.settings_notes_vault_header, savedMessages.size),
                                    onClick = { showSavedVault = true },
                                    icon = Icons.Filled.Bookmark,
                                    modifier = Modifier.weight(1f)
                                )
                                DlSecondaryButton(
                                    text = "${stringResource(R.string.settings_cat_account)} ($localAccountsCount)",
                                    onClick = onOpenAccountSwitcher,
                                    icon = Icons.Filled.SwitchAccount,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_account_switcher_button")
                                )
                            }

                            DlSecondaryButton(
                                text = stringResource(R.string.settings_pro_tier_btn, tierLabel),
                                onClick = { showPremiumSheet = true },
                                icon = Icons.Filled.WorkspacePremium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_premium_sheet_button")
                            )

                            if (isAdminUser || staffRole.canOpenStaffConsole) {
                                DlPrimaryButton(
                                    text = stringResource(R.string.settings_open_admin),
                                    onClick = onOpenAdminDashboard,
                                    icon = Icons.Filled.AdminPanelSettings,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("open_admin_dashboard_button")
                                )
                            }
                        }
                    }

                    item {
                        val enforcementStatus = DlAccountStatus.fromRaw(myEnforcement?.accountStatus)
                        val linkedE164 = phoneAuthProfile?.phoneE164.orEmpty()
                        SettingsGroupBlock(
                            icon = Icons.Filled.Phone,
                            title = stringResource(R.string.settings_trust_support_card_title)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (linkedE164.isNotBlank()) {
                                            PhoneNumberHelper.formatReadable(linkedE164)
                                        } else {
                                            stringResource(R.string.settings_phone_unlinked)
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = tokens.textPrimary
                                    )
                                    Text(
                                        text = if (linkedE164.isNotBlank()) {
                                            stringResource(R.string.settings_phone_verified_sub)
                                        } else {
                                            stringResource(R.string.settings_phone_unlinked_sub)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary
                                    )
                                }
                                if (linkedE164.isNotBlank()) {
                                    DlStatusPill(
                                        text = stringResource(R.string.common_verified),
                                        color = tokens.statusOnline
                                    )
                                }
                            }
                            DlSecondaryButton(
                                text = stringResource(
                                    if (linkedE164.isBlank()) R.string.settings_link_phone_cta
                                    else R.string.settings_change_phone_cta
                                ),
                                onClick = { showLinkPhoneDialog = true },
                                icon = Icons.Filled.Phone,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_link_phone_button")
                            )
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_whatsapp_optin_label),
                                checked = phoneAuthProfile?.whatsappOptIn ?: false,
                                onCheckedChange = onUpdateWhatsAppOptIn
                            )
                            DlHairlineDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DlSecondaryButton(
                                    text = stringResource(
                                        R.string.settings_account_status_btn,
                                        if (unreadWarningsCount > 0) " ($unreadWarningsCount)" else ""
                                    ),
                                    onClick = onOpenAccountStatus,
                                    icon = Icons.Filled.Gavel,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_account_status_button")
                                )
                                DlSecondaryButton(
                                    text = stringResource(
                                        R.string.settings_support_center_btn,
                                        if (openTicketsCount > 0) " ($openTicketsCount)" else ""
                                    ),
                                    onClick = onOpenSupportCenter,
                                    icon = Icons.Filled.SupportAgent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_support_center_button")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.account_status_standing_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.textSecondary
                                )
                                DlStatusPill(
                                    text = enforcementStatus.label,
                                    color = if (myEnforcement?.isGloballySuspended() == true) DlCrimson
                                    else if (enforcementStatus == DlAccountStatus.ACTIVE) tokens.statusOnline
                                    else tokens.statusAway
                                )
                            }
                            DlSecondaryButton(
                                text = stringResource(R.string.settings_policies_btn),
                                onClick = onOpenPolicies,
                                icon = Icons.Filled.Policy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_policies_button")
                            )
                        }
                    }

                    item {
                        val legionCfg = remember(showLegionLinkDialog, profile.legionUserId) {
                            DeathLegionOAuthClient.getConfig(context)
                        }
                        val cachedLegion = remember(profile.legionUserId, profile.legionUsername, showLegionLinkDialog) {
                            DeathLegionOAuthClient.getCachedUserInfo(context)
                        }
                        val tokenSession = remember(profile.legionUserId, showLegionLinkDialog) {
                            DeathLegionOAuthClient.getTokenSession(context)
                        }
                        val pendingCode = remember(profile.legionUserId, showLegionLinkDialog) {
                            DeathLegionOAuthClient.getPendingAuthCode(context)
                        }

                        SettingsGroupBlock(
                            icon = Icons.Filled.Shield,
                            title = stringResource(R.string.settings_legion_card_title)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DlStatusPill(
                                    text = stringResource(R.string.legion_app_badge),
                                    color = tokens.brandLegion
                                )
                                if (!profile.legionUsername.isNullOrBlank()) {
                                    DlStatusPill(
                                        text = stringResource(R.string.settings_legion_connected),
                                        color = tokens.statusOnline
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.legion_callback_label, legionCfg.redirectUri),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = tokens.textSecondary
                            )

                            if (!profile.legionUsername.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(DlRadius.md),
                                    color = tokens.surfaceCard,
                                    border = BorderStroke(1.dp, tokens.hairline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.settings_legion_linked_sub,
                                                profile.legionUsername.orEmpty(),
                                                profile.legionMemberNumber.orEmpty().ifBlank { cachedLegion?.memberNumber.orEmpty() }
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = tokens.statusOnline
                                        )
                                        val resolvedUnit = cachedLegion?.primaryUnitName.orEmpty()
                                            .ifBlank { profile.legionPrimaryUnit.orEmpty() }
                                        val resolvedRole = cachedLegion?.currentRole.orEmpty()
                                            .ifBlank { cachedLegion?.role.orEmpty() }
                                        val resolvedStatus = cachedLegion?.verificationStatus.orEmpty()
                                            .ifBlank { profile.legionVerificationStatus.orEmpty() }
                                        if (resolvedUnit.isNotBlank()) {
                                            Text(
                                                text = stringResource(R.string.legion_dossier_unit, resolvedUnit),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.textPrimary
                                            )
                                        }
                                        if (resolvedRole.isNotBlank()) {
                                            Text(
                                                text = stringResource(R.string.legion_dossier_role, resolvedRole),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.textSecondary
                                            )
                                        }
                                        if (resolvedStatus.isNotBlank()) {
                                            Text(
                                                text = stringResource(R.string.legion_dossier_status, resolvedStatus),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (tokenSession != null) {
                                            val formattedExpiry = if (tokenSession.expiresAtEpochMs > 0L) {
                                                android.text.format.DateFormat.getTimeFormat(context)
                                                    .format(java.util.Date(tokenSession.expiresAtEpochMs))
                                            } else ""
                                            Text(
                                                text = if (tokenSession.isExpired) {
                                                    stringResource(R.string.legion_session_expired)
                                                } else {
                                                    stringResource(R.string.legion_session_active_until, formattedExpiry.ifBlank { "1h" })
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (tokenSession.isExpired) DlCrimson else tokens.textSecondary
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DlSecondaryButton(
                                        text = stringResource(R.string.legion_refresh_session_btn),
                                        onClick = onRefreshDeathLegionSession,
                                        icon = Icons.Filled.Refresh,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("legion_refresh_button")
                                    )
                                    DlSecondaryButton(
                                        text = stringResource(R.string.legion_verify_token_btn),
                                        onClick = onIntrospectDeathLegionSession,
                                        icon = Icons.Filled.Verified,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("legion_introspect_button")
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.settings_legion_unlinked_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                            }

                            if (!pendingCode.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(DlRadius.md),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.legion_pending_code_banner, pendingCode.take(8)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        DlPrimaryButton(
                                            text = stringResource(R.string.legion_complete_pending_btn),
                                            onClick = onCompletePendingDeathLegionCode,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("legion_complete_pending_button")
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { showLegionLinkDialog = true },
                                shape = RoundedCornerShape(DlRadius.md),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.brandLegion,
                                    contentColor = tokens.onBrandLegion
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = DlSizing.touchTarget)
                                    .testTag("link_death_legion_button")
                            ) {
                                Text(
                                    stringResource(
                                        if (profile.legionUsername.isNullOrBlank()) {
                                            R.string.settings_legion_connect
                                        } else {
                                            R.string.settings_legion_relink
                                        }
                                    )
                                )
                            }

                            if (!profile.legionUsername.isNullOrBlank()) {
                                DlDangerButton(
                                    text = stringResource(R.string.legion_disconnect_btn),
                                    onClick = onDisconnectDeathLegion,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("legion_disconnect_button")
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DlSecondaryButton(
                                text = stringResource(R.string.settings_sign_out),
                                onClick = onSignOut,
                                icon = Icons.AutoMirrored.Filled.Logout,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sign_out_button")
                            )
                            DlDangerButton(
                                text = stringResource(R.string.settings_delete_account),
                                onClick = { showDeleteConfirm = true },
                                icon = Icons.Filled.Delete,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                SettingsCategory.PRIVACY -> {
                    item {
                        SettingsGroupBlock(
                            icon = Icons.Filled.Lock,
                            title = stringResource(R.string.settings_section_privacy)
                        ) {
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_privacy_online),
                                checked = profile.showOnline,
                                onCheckedChange = {
                                    onUpdateProfile(
                                        profile.username, profile.displayName, profile.bio, profile.avatarColor,
                                        null, profile.onlineStatus, profile.showLastSeen, it,
                                        profile.readReceipts, profile.discoverable, profile.twoFactorEnabled, profile.profilePhotoVisibility
                                    )
                                }
                            )
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_privacy_last_seen),
                                checked = profile.showLastSeen,
                                onCheckedChange = {
                                    onUpdateProfile(
                                        profile.username, profile.displayName, profile.bio, profile.avatarColor,
                                        null, profile.onlineStatus, it, profile.showOnline,
                                        profile.readReceipts, profile.discoverable, profile.twoFactorEnabled, profile.profilePhotoVisibility
                                    )
                                }
                            )
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_privacy_read_receipts),
                                checked = profile.readReceipts,
                                onCheckedChange = {
                                    onUpdateProfile(
                                        profile.username, profile.displayName, profile.bio, profile.avatarColor,
                                        null, profile.onlineStatus, profile.showLastSeen, profile.showOnline,
                                        it, profile.discoverable, profile.twoFactorEnabled, profile.profilePhotoVisibility
                                    )
                                }
                            )
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_privacy_discoverable),
                                checked = profile.discoverable,
                                onCheckedChange = {
                                    onUpdateProfile(
                                        profile.username, profile.displayName, profile.bio, profile.avatarColor,
                                        null, profile.onlineStatus, profile.showLastSeen, profile.showOnline,
                                        profile.readReceipts, it, profile.twoFactorEnabled, profile.profilePhotoVisibility
                                    )
                                }
                            )
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_privacy_two_step),
                                checked = profile.twoFactorEnabled,
                                onCheckedChange = {
                                    onUpdateProfile(
                                        profile.username, profile.displayName, profile.bio, profile.avatarColor,
                                        null, profile.onlineStatus, profile.showLastSeen, profile.showOnline,
                                        profile.readReceipts, profile.discoverable, it, profile.profilePhotoVisibility
                                    )
                                }
                            )
                            DlHairlineDivider()
                            Text(
                                stringResource(R.string.settings_photo_vis_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.textSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "EVERYONE" to stringResource(R.string.settings_vis_everyone),
                                    "CONTACTS" to stringResource(R.string.settings_vis_contacts),
                                    "NOBODY" to stringResource(R.string.settings_vis_nobody)
                                ).forEach { (k, label) ->
                                    FilterChip(
                                        selected = profile.profilePhotoVisibility == k,
                                        onClick = {
                                            onUpdateProfile(
                                                profile.username, profile.displayName, profile.bio, profile.avatarColor,
                                                null, profile.onlineStatus, profile.showLastSeen, profile.showOnline,
                                                profile.readReceipts, profile.discoverable, profile.twoFactorEnabled, k
                                            )
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SettingsGroupBlock(
                            icon = Icons.Filled.Shield,
                            title = stringResource(R.string.settings_privacy_app_lock)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(if (hasPin) R.string.settings_pin_status_ready else R.string.settings_pin_status_none),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (hasPin) tokens.statusOnline else tokens.textSecondary
                                )
                                DlStatusPill(
                                    text = stringResource(if (hasPin) R.string.common_active else R.string.common_off),
                                    color = if (hasPin) tokens.statusOnline else tokens.textTertiary
                                )
                            }
                            DlSecondaryButton(
                                text = stringResource(if (hasPin) R.string.settings_pin_set_btn else R.string.settings_pin_create_btn),
                                onClick = { showPinDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("configure_pin_button")
                            )
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_privacy_app_lock),
                                checked = currentSettings.appLockEnabled,
                                switchModifier = Modifier.testTag("app_lock_toggle"),
                                onCheckedChange = {
                                    onUpdateSettings(currentSettings.copy(appLockEnabled = it))
                                }
                            )
                            Text(
                                text = stringResource(R.string.settings_privacy_app_lock_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                            Text(
                                stringResource(R.string.settings_autolock_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.textSecondary
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    0 to stringResource(R.string.settings_autolock_immediately),
                                    30 to stringResource(R.string.settings_autolock_30s),
                                    60 to stringResource(R.string.settings_autolock_1m),
                                    300 to stringResource(R.string.settings_autolock_5m)
                                ).forEach { (sec, label) ->
                                    FilterChip(
                                        selected = currentSettings.autoLockSeconds == sec,
                                        onClick = { onUpdateSettings(currentSettings.copy(autoLockSeconds = sec)) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_privacy_biometrics),
                                checked = currentSettings.biometricLockEnabled,
                                onCheckedChange = {
                                    onUpdateSettings(currentSettings.copy(biometricLockEnabled = it))
                                }
                            )
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_privacy_block_screenshots),
                                checked = currentSettings.screenshotProtectionEnabled,
                                switchModifier = Modifier.testTag("screenshot_protection_toggle"),
                                onCheckedChange = {
                                    onUpdateSettings(currentSettings.copy(screenshotProtectionEnabled = it))
                                }
                            )
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_privacy_hide_previews),
                                checked = currentSettings.hideNotificationContent,
                                onCheckedChange = {
                                    onUpdateSettings(currentSettings.copy(hideNotificationContent = it))
                                }
                            )
                        }
                    }
                }

                SettingsCategory.APPEARANCE -> {
                    item {
                        SettingsGroupBlock(
                            icon = Icons.Filled.Palette,
                            title = stringResource(R.string.settings_section_appearance)
                        ) {
                            Text(
                                stringResource(R.string.language_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.textSecondary
                            )
                            Text(
                                stringResource(R.string.language_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textTertiary
                            )
                            DlLanguageSelector(
                                selectedLanguage = localAppearancePrefs.appLanguage,
                                onSelectLanguage = onSelectLanguage
                            )
                            DlHairlineDivider()

                            Text(
                                stringResource(R.string.settings_theme_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.textSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "DARK" to stringResource(R.string.settings_theme_dark),
                                    "LIGHT" to stringResource(R.string.settings_theme_light),
                                    "SYSTEM" to stringResource(R.string.settings_theme_system)
                                ).forEach { (k, label) ->
                                    FilterChip(
                                        selected = currentSettings.themeMode == k,
                                        onClick = { onUpdateSettings(currentSettings.copy(themeMode = k)) },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            Text(
                                stringResource(R.string.settings_accent_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.textSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AccentColorOptions.forEach { (name, hex) ->
                                    val localizedName = when (hex.uppercase()) {
                                        "#00E5FF" -> stringResource(R.string.color_cyan)
                                        "#6C5CE7" -> stringResource(R.string.color_indigo)
                                        "#00D68F", "#12B886" -> stringResource(R.string.color_emerald)
                                        "#FF4D6D" -> stringResource(R.string.color_crimson)
                                        "#FFB800", "#F59F00" -> stringResource(R.string.color_amber)
                                        else -> name
                                    }
                                    val accentCd = stringResource(R.string.info_accent_cd, localizedName)
                                    val swatchColor = resolveAccessibleAccent(hex, tokens.isDark)
                                    val selected = currentSettings.accentColorHex.equals(hex, ignoreCase = true)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                                .border(
                                                    width = if (selected) 2.5.dp else 1.dp,
                                                    color = if (selected) tokens.textPrimary else tokens.hairline,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    onUpdateSettings(currentSettings.copy(accentColorHex = hex))
                                                }
                                                .semantics {
                                                    contentDescription = accentCd
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selected) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = contrastInkFor(swatchColor),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = localizedName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.primary else tokens.textSecondary
                                        )
                                    }
                                }
                            }

                            Text(
                                stringResource(R.string.settings_wallpaper_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.textSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "nebula" to stringResource(R.string.settings_wallpaper_constellation),
                                    "solid" to stringResource(R.string.settings_wallpaper_obsidian)
                                ).forEach { (id, label) ->
                                    FilterChip(
                                        selected = currentSettings.wallpaperId == id,
                                        onClick = { onUpdateSettings(currentSettings.copy(wallpaperId = id)) },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            // Live Message Bubble Preview
                            val previewAccent = resolveAccessibleAccent(currentSettings.accentColorHex, tokens.isDark)
                            val previewRadius = when (currentSettings.bubbleStyle) {
                                "COMPACT" -> 10.dp
                                "CLASSIC" -> 6.dp
                                else -> 16.dp
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_live_preview_header),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textTertiary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(previewRadius),
                                        color = tokens.surfaceCard
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_live_preview_msg1),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.textPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                        Surface(
                                            shape = RoundedCornerShape(previewRadius),
                                            color = previewAccent.copy(alpha = if (tokens.isDark) 0.22f else 0.14f),
                                            border = BorderStroke(1.dp, previewAccent.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_live_preview_msg2),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.textPrimary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            var sliderScale by remember(currentSettings.fontScale) {
                                mutableFloatStateOf(currentSettings.fontScale.toFloat())
                            }
                            Text(
                                text = stringResource(R.string.settings_font_scale_label, (sliderScale * 100).toInt()),
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.textSecondary
                            )
                            Slider(
                                value = sliderScale,
                                onValueChange = { sliderScale = it },
                                onValueChangeFinished = {
                                    onUpdateSettings(currentSettings.copy(fontScale = sliderScale.toDouble()))
                                },
                                valueRange = 0.85f..1.30f
                            )

                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_reduced_motion),
                                checked = localAppearancePrefs.reducedMotion,
                                onCheckedChange = { enabled ->
                                    onUpdateLocalUiPrefs(enabled, null, null, null, null, null, null)
                                }
                            )
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_compact_density),
                                checked = localAppearancePrefs.compactDensity,
                                onCheckedChange = { enabled ->
                                    onUpdateLocalUiPrefs(null, null, null, null, null, null, enabled)
                                }
                            )
                        }
                    }
                }

                SettingsCategory.CHATS -> {
                    item {
                        SettingsGroupBlock(
                            icon = Icons.Filled.Palette,
                            title = stringResource(R.string.settings_section_chats)
                        ) {
                            Text(
                                stringResource(R.string.settings_bubble_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.textSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "MODERN" to stringResource(R.string.settings_bubble_modern),
                                    "COMPACT" to stringResource(R.string.settings_bubble_compact),
                                    "CLASSIC" to stringResource(R.string.settings_bubble_classic)
                                ).forEach { (style, label) ->
                                    FilterChip(
                                        selected = currentSettings.bubbleStyle == style,
                                        onClick = { onUpdateSettings(currentSettings.copy(bubbleStyle = style)) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsCategory.NOTIFICATIONS -> {
                    item {
                        SettingsGroupBlock(
                            icon = Icons.Filled.Notifications,
                            title = stringResource(R.string.settings_section_notifications)
                        ) {
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_notif_all),
                                checked = currentSettings.notificationsEnabled,
                                onCheckedChange = { onUpdateSettings(currentSettings.copy(notificationsEnabled = it)) }
                            )
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_notif_sound),
                                checked = currentSettings.soundEnabled,
                                onCheckedChange = { onUpdateSettings(currentSettings.copy(soundEnabled = it)) }
                            )
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_notif_dnd),
                                checked = currentSettings.dndEnabled,
                                onCheckedChange = { onUpdateSettings(currentSettings.copy(dndEnabled = it)) }
                            )
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_notif_messages),
                                checked = localAppearancePrefs.messageNotifications,
                                onCheckedChange = {
                                    onUpdateLocalUiPrefs(null, it, null, null, null, null, null)
                                }
                            )
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_notif_groups),
                                checked = localAppearancePrefs.groupNotifications,
                                onCheckedChange = {
                                    onUpdateLocalUiPrefs(null, null, it, null, null, null, null)
                                }
                            )
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_notif_calls),
                                checked = localAppearancePrefs.callNotifications,
                                onCheckedChange = {
                                    onUpdateLocalUiPrefs(null, null, null, it, null, null, null)
                                }
                            )
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_notif_vibration),
                                checked = localAppearancePrefs.vibrationEnabled,
                                onCheckedChange = {
                                    onUpdateLocalUiPrefs(null, null, null, null, it, null, null)
                                }
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = tokens.surfaceCard,
                                border = BorderStroke(1.dp, tokens.hairline)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_notif_disclosure),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textSecondary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }

                SettingsCategory.STORAGE -> {
                    item {
                        SettingsGroupBlock(
                            icon = Icons.Filled.Storage,
                            title = stringResource(R.string.settings_section_storage)
                        ) {
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_auto_download),
                                checked = currentSettings.autoDownloadMedia,
                                onCheckedChange = { onUpdateSettings(currentSettings.copy(autoDownloadMedia = it)) }
                            )
                            DlHairlineDivider()
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_wifi_only),
                                checked = localAppearancePrefs.wifiOnlyMedia,
                                onCheckedChange = {
                                    onUpdateLocalUiPrefs(null, null, null, null, null, it, null)
                                }
                            )
                            DlHairlineDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_local_cache_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = LocalStorageManager.formatByteCount(storageBreakdown.totalBytes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            val totalSafe = storageBreakdown.totalBytes.coerceAtLeast(1L).toFloat()
                            val voiceFrac = (storageBreakdown.voiceCacheBytes / totalSafe).coerceIn(0f, 1f)
                            val imageFrac = (storageBreakdown.imageCacheBytes / totalSafe).coerceIn(0f, 1f)
                            val otherFrac = (storageBreakdown.otherCacheBytes / totalSafe).coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(tokens.surfaceCard)
                            ) {
                                if (voiceFrac > 0f) {
                                    Box(Modifier.weight(voiceFrac.coerceAtLeast(0.02f)).height(8.dp).background(MaterialTheme.colorScheme.primary))
                                }
                                if (imageFrac > 0f) {
                                    Box(Modifier.weight(imageFrac.coerceAtLeast(0.02f)).height(8.dp).background(tokens.statusOnline))
                                }
                                if (otherFrac > 0f) {
                                    Box(Modifier.weight(otherFrac.coerceAtLeast(0.02f)).height(8.dp).background(MaterialTheme.colorScheme.secondary))
                                }
                            }
                            Text(
                                text = stringResource(
                                    R.string.settings_storage_usage,
                                    LocalStorageManager.formatByteCount(storageBreakdown.voiceCacheBytes),
                                    LocalStorageManager.formatByteCount(storageBreakdown.imageCacheBytes),
                                    LocalStorageManager.formatByteCount(storageBreakdown.otherCacheBytes)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DlSecondaryButton(
                                    text = stringResource(R.string.settings_refresh),
                                    onClick = onRefreshStorage,
                                    modifier = Modifier.weight(1f)
                                )
                                DlPrimaryButton(
                                    text = stringResource(R.string.settings_clear_cache),
                                    onClick = onClearLocalCache,
                                    icon = Icons.Filled.CleaningServices,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("clear_cache_button")
                                )
                            }
                            DlHairlineDivider()
                            val apkExportCd = stringResource(R.string.settings_apk_export_cd)
                            val apkSizeLabel = apkInfo.apkSizeBytes?.let {
                                LocalStorageManager.formatByteCount(it)
                            } ?: stringResource(R.string.settings_apk_size_unavailable)
                            Text(
                                text = stringResource(
                                    R.string.settings_apk_meta_label,
                                    apkInfo.versionName,
                                    apkInfo.versionCode,
                                    apkSizeLabel
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = tokens.textSecondary
                            )
                            DlSecondaryButton(
                                text = stringResource(
                                    if (isExportingApk) R.string.settings_apk_exporting else R.string.settings_apk_export_btn
                                ),
                                onClick = { createApkLauncher.launch(apkInfo.suggestedFileName) },
                                enabled = !isExportingApk,
                                icon = Icons.Filled.Download,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("storage_export_apk_button")
                                    .semantics { contentDescription = apkExportCd }
                            )
                            apkExportStatus?.let { status ->
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (apkExportError) DlCrimson else tokens.statusOnline
                                )
                            }
                        }
                    }
                }

                SettingsCategory.DEVICES -> {
                    item {
                        LinkedDevicesSection(
                            currentDeviceId = currentDeviceId,
                            devices = devices,
                            pairingSessions = pairingSessions,
                            deviceKeyBundles = deviceKeyBundles,
                            deviceNotifications = deviceNotifications,
                            activeChallenge = activeChallenge,
                            onGeneratePairingQr = onGeneratePairingQr,
                            onCancelPairing = onCancelPairing,
                            onConfirmScannedQr = onConfirmScannedQr,
                            onConfirmManualPairing = onConfirmManualPairing,
                            onRenameDevice = onRenameDevice,
                            onRotateDeviceToken = onRotateDeviceToken,
                            onRevokeDevice = onRevokeDevice,
                            onRevokeAllOtherDevices = onRevokeAllOtherDevices,
                            onMarkNotificationRead = onMarkNotificationRead
                        )
                    }
                }

                SettingsCategory.SECURITY -> {
                    item {
                        SettingsGroupBlock(
                            icon = Icons.Filled.Key,
                            title = stringResource(R.string.settings_section_security)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_identity_key_header, profile.keyVersion),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.textPrimary
                                )
                                DlStatusPill("v${profile.keyVersion}", color = MaterialTheme.colorScheme.primary)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = tokens.surfaceCard,
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = profile.publicKeyFingerprint,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            DlSecondaryButton(
                                text = stringResource(R.string.settings_rotate_keys),
                                onClick = onRotateKeys,
                                icon = Icons.Filled.Key,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rotate_keys_button")
                            )
                            DlHairlineDivider()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DlPrimaryButton(
                                    text = stringResource(R.string.settings_export_backup),
                                    onClick = { createBackupLauncher.launch("dlchat_backup.dlbkp") },
                                    icon = Icons.Filled.Backup,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("export_backup_button")
                                )
                                DlSecondaryButton(
                                    text = stringResource(R.string.settings_restore_backup),
                                    onClick = { restoreBackupLauncher.launch(arrayOf("*/*")) },
                                    icon = Icons.Filled.Restore,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("restore_backup_button")
                                )
                            }
                            if (backups.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.settings_backup_history, backups.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.textSecondary
                                )
                                backups.take(4).forEach { b ->
                                    val meta = stringResource(
                                        R.string.settings_backup_item_meta,
                                        b.itemCount,
                                        LocalStorageManager.formatByteCount(b.sizeBytes)
                                    )
                                    Text(
                                        text = "• ${b.fileName} · $meta (${formatRelativeTimeLocalized(b.createdAt)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SettingsGroupBlock(
                            icon = Icons.Filled.Sync,
                            title = stringResource(R.string.settings_sync_queue_title)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_sync_stats, pendingSyncCount, syncEvents.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            DlInlineToggleRow(
                                label = stringResource(R.string.settings_auto_sync),
                                checked = currentSettings.autoSyncOfflineQueue,
                                onCheckedChange = {
                                    onUpdateSettings(currentSettings.copy(autoSyncOfflineQueue = it))
                                }
                            )
                            DlSecondaryButton(
                                text = stringResource(R.string.settings_flush_sync),
                                onClick = onFlushOfflineSync,
                                icon = Icons.Filled.Sync,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("flush_sync_queue_button")
                            )
                            if (syncEvents.isNotEmpty()) {
                                syncEvents.take(5).forEach { ev ->
                                    Text(
                                        text = "• ${ev.payloadSummary}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsCategory.ABOUT -> {
                    item {
                        DlFocalCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.settings_about_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = tokens.textPrimary
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.settings_about_tagline),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.settings_about_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.settings_about_highlights),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                            Spacer(Modifier.height(10.dp))
                            DlSecondaryButton(
                                text = stringResource(R.string.settings_policies_btn),
                                onClick = onOpenPolicies,
                                icon = Icons.Filled.Policy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("about_open_policies_button")
                            )
                        }
                    }

                    item {
                        val apkExportCd = stringResource(R.string.settings_apk_export_cd)
                        val apkSizeText = apkInfo.apkSizeBytes?.let {
                            LocalStorageManager.formatByteCount(it)
                        } ?: stringResource(R.string.settings_apk_size_unavailable)
                        SettingsGroupBlock(
                            icon = Icons.Filled.Download,
                            title = stringResource(R.string.settings_apk_section_title)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_apk_section_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = tokens.surfaceCard,
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_apk_meta_label,
                                        apkInfo.versionName,
                                        apkInfo.versionCode,
                                        apkSizeText
                                    ),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .testTag("about_apk_meta_text")
                                )
                            }
                            DlPrimaryButton(
                                text = stringResource(
                                    if (isExportingApk) R.string.settings_apk_exporting else R.string.settings_apk_export_btn
                                ),
                                onClick = { createApkLauncher.launch(apkInfo.suggestedFileName) },
                                enabled = !isExportingApk,
                                icon = Icons.Filled.Download,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("export_apk_button")
                                    .semantics { contentDescription = apkExportCd }
                            )
                            apkExportStatus?.let { status ->
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (apkExportError) DlCrimson else tokens.statusOnline,
                                    modifier = Modifier.testTag("export_apk_status")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { showProfileDialog = false },
            onSave = { username, name, bio, color, uri, status ->
                onUpdateProfile(
                    username, name, bio, color, uri, status,
                    profile.showLastSeen, profile.showOnline, profile.readReceipts,
                    profile.discoverable, profile.twoFactorEnabled, profile.profilePhotoVisibility
                )
                showProfileDialog = false
            }
        )
    }

    if (showQrDialog) {
        QrDisplayDialog(
            title = stringResource(R.string.settings_identity_card_title),
            subtitle = stringResource(
                R.string.settings_identity_card_sub,
                profile.username,
                profile.keyVersion,
                profile.publicKeyFingerprint
            ),
            qrContent = QrCodeUtils.buildProfileUri(profile.username),
            onDismiss = { showQrDialog = false }
        )
    }

    if (showPinDialog && profile.userId.isNotBlank()) {
        PinSetupDialog(
            uid = profile.userId,
            hasExistingPin = hasPin,
            onDismiss = { showPinDialog = false },
            onPinSaved = { hasPin = true },
            onPinCleared = { hasPin = false }
        )
    }

    if (showPremiumSheet) {
        DlPremiumSheet(
            currentTier = profile.premiumTier,
            onDismiss = { showPremiumSheet = false }
        )
    }

    if (showLinkPhoneDialog) {
        LinkPhoneNumberDialog(
            initialCountry = phoneAuthProfile?.countryCode?.let { PhoneNumberHelper.findCountry(it) }
                ?: PhoneNumberHelper.defaultCountry,
            initialOptIn = phoneAuthProfile?.whatsappOptIn ?: false,
            onDismiss = { showLinkPhoneDialog = false },
            onVerifyCredential = { cred, country, optIn, done ->
                if (onLinkPhoneCredential != null) {
                    onLinkPhoneCredential(cred, country, optIn, done)
                } else {
                    done(false, "Phone linking unavailable in preview mode.")
                }
            }
        )
    }

    if (showLegionLinkDialog) {
        val existingConfig = remember { DeathLegionOAuthClient.getConfig(context) }
        var clientId by remember { mutableStateOf(existingConfig.clientId) }
        var proxyUrl by remember { mutableStateOf(existingConfig.tokenExchangeProxyUrl) }
        var accessToken by remember { mutableStateOf("") }
        var oauthError by remember { mutableStateOf<String?>(null) }
        val exchangeReady = clientId.isNotBlank() && (proxyUrl.isNotBlank() || existingConfig.hasConfiguredClientSecret)

        AlertDialog(
            onDismissRequest = { showLegionLinkDialog = false },
            title = { Text(stringResource(R.string.auth_legion_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DlStatusPill(
                        text = stringResource(R.string.legion_app_badge),
                        color = tokens.brandLegion
                    )
                    Text(
                        stringResource(R.string.legion_callback_label, existingConfig.redirectUri),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(
                            if (exchangeReady) R.string.legion_secret_configured
                            else R.string.legion_secret_missing_hint
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (exchangeReady) tokens.statusOnline else tokens.textSecondary
                    )
                    Text(
                        stringResource(R.string.auth_legion_dialog_desc, DeathLegionOAuthClient.BASE_URL),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text(stringResource(R.string.auth_legion_client_id_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = proxyUrl,
                        onValueChange = { proxyUrl = it },
                        label = { Text(stringResource(R.string.auth_legion_proxy_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            DeathLegionOAuthClient.saveConfig(context, clientId, proxyUrl)
                            DeathLegionOAuthClient.launchAuthorizeInBrowser(context)
                                .onSuccess { showLegionLinkDialog = false }
                                .onFailure { oauthError = context.getString(R.string.auth_legion_launch_error) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.auth_legion_authorize_cta))
                    }
                    DlHairlineDivider()
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text(stringResource(R.string.settings_legion_token_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (oauthError != null) {
                        Text(oauthError.orEmpty(), color = DlCrimson, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = accessToken.isNotBlank(),
                    onClick = {
                        onLinkDeathLegionToken(accessToken.trim())
                        showLegionLinkDialog = false
                    }
                ) { Text(stringResource(R.string.settings_legion_token_btn)) }
            },
            dismissButton = {
                TextButton(onClick = { showLegionLinkDialog = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    if (showSavedVault) {
        SavedNotesVaultDialog(
            savedMessages = savedMessages,
            onDismiss = { showSavedVault = false },
            onAddNote = onAddPersonalNote,
            onDelete = onDeleteSaved
        )
    }

    if (showPassphraseDialog && pendingBackupUri != null) {
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPassphraseDialog = false },
            title = {
                Text(
                    stringResource(
                        if (backupMode == "EXPORT") R.string.settings_export_backup else R.string.settings_restore_backup
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_backup_dialog_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.settings_backup_passphrase_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = passphrase.length >= 6,
                    onClick = {
                        val targetUri = pendingBackupUri!!
                        if (backupMode == "EXPORT") onExportBackup(targetUri, passphrase)
                        else onRestoreBackup(targetUri, passphrase)
                        showPassphraseDialog = false
                    }
                ) {
                    Text(
                        stringResource(
                            if (backupMode == "EXPORT") R.string.settings_export_backup else R.string.settings_restore_backup
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPassphraseDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.settings_delete_dialog_title)) },
            text = { Text(stringResource(R.string.settings_delete_dialog_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteAccount()
                }) { Text(stringResource(R.string.settings_delete_confirm), color = DlCrimson) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun SettingsGroupBlock(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalDlTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DlSectionHeader(title = title)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = tokens.surfaceElevated,
            border = BorderStroke(1.dp, tokens.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(DlSpacing.base),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Uri?, String) -> Unit
) {
    var username by remember { mutableStateOf(profile.username) }
    var displayName by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }
    val avatarColor by remember { mutableStateOf(profile.avatarColor) }
    var status by remember { mutableStateOf(profile.onlineStatus) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> pickedUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_edit_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Photo, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (pickedUri != null) R.string.stories_photo_selected else R.string.settings_change_photo
                        )
                    )
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.settings_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.settings_username_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(stringResource(R.string.settings_bio_label)) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OnlineStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st.name,
                            onClick = { status = st.name },
                            label = { Text(st.localizedLabel()) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(username, displayName, bio, avatarColor, pickedUri, status)
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
fun SavedNotesVaultDialog(
    savedMessages: List<SavedMessage>,
    onDismiss: () -> Unit,
    onAddNote: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val tokens = LocalDlTokens.current
    var noteDraft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notes_vault_header, savedMessages.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        label = { Text(stringResource(R.string.settings_note_placeholder)) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = {
                        if (noteDraft.isNotBlank()) {
                            onAddNote(noteDraft)
                            noteDraft = ""
                        }
                    }) { Text(stringResource(R.string.common_save)) }
                }

                if (savedMessages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_note_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(savedMessages.size, key = { savedMessages[it].id }) { idx ->
                            val item = savedMessages[idx]
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = tokens.surfaceCard,
                                border = BorderStroke(1.dp, tokens.hairline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.sourceChatName.ifBlank { stringResource(R.string.settings_vault_badge) },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(item.text, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { onDelete(item.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

@Composable
private fun LinkPhoneNumberDialog(
    initialCountry: CountryDialCode,
    initialOptIn: Boolean,
    onDismiss: () -> Unit,
    onVerifyCredential: (PhoneAuthCredential, CountryDialCode, Boolean, (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    val scope = rememberCoroutineScope()
    var country by remember { mutableStateOf(initialCountry) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var phoneInput by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var otpCode by remember { mutableStateOf("") }
    var whatsappOptIn by remember { mutableStateOf(initialOptIn) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val normalized = PhoneNumberHelper.normalizeToE164(country, phoneInput)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = { Text(stringResource(R.string.settings_link_phone_dialog_title), color = tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_link_phone_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                if (verificationId == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showCountryPicker = true }) {
                            Text("${country.flagEmoji} ${country.dialCode}")
                        }
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it; errorMsg = null },
                            label = { Text(stringResource(R.string.auth_phone_input_label)) },
                            placeholder = { Text(country.exampleLocal) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("settings_link_phone_input")
                        )
                    }
                    if (normalized != null) {
                        Text(
                            text = stringResource(R.string.auth_phone_e164_preview, normalized),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = whatsappOptIn, onCheckedChange = { whatsappOptIn = it })
                        Text(
                            text = stringResource(R.string.auth_whatsapp_support_optin),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.auth_phone_verify_hint, normalized.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textPrimary
                    )
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it.filter(Char::isDigit).take(6); errorMsg = null },
                        label = { Text(stringResource(R.string.auth_otp_code_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_link_otp_input")
                    )
                }
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg.orEmpty(), style = MaterialTheme.typography.labelSmall, color = DlCrimson)
                }
            }
        },
        confirmButton = {
            if (verificationId == null) {
                TextButton(
                    enabled = !busy && normalized != null,
                    onClick = {
                        val activity = context.findActivity()
                        val target = normalized
                        if (activity == null || target == null) {
                            errorMsg = "Enter a valid phone number."
                            return@TextButton
                        }
                        busy = true
                        errorMsg = null
                        scope.launch {
                            when (val res = FirebaseSmsOtpProvider(Firebase.auth).requestOtp(activity, target)) {
                                is OtpSendResult.CodeSent -> {
                                    verificationId = res.verificationId
                                    busy = false
                                }
                                is OtpSendResult.InstantVerified -> {
                                    onVerifyCredential(res.credential, country, whatsappOptIn) { ok, err ->
                                        busy = false
                                        if (ok) onDismiss() else errorMsg = err
                                    }
                                }
                                is OtpSendResult.Error -> {
                                    busy = false
                                    errorMsg = res.message
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("settings_send_link_otp_button")
                ) {
                    Text(stringResource(if (busy) R.string.auth_phone_sending_otp else R.string.auth_phone_send_otp_cta))
                }
            } else {
                TextButton(
                    enabled = !busy && otpCode.length == 6,
                    onClick = {
                        val vId = verificationId ?: return@TextButton
                        busy = true
                        errorMsg = null
                        val cred = PhoneAuthProvider.getCredential(vId, otpCode)
                        onVerifyCredential(cred, country, whatsappOptIn) { ok, err ->
                            busy = false
                            if (ok) onDismiss() else errorMsg = err
                        }
                    },
                    modifier = Modifier.testTag("settings_verify_link_otp_button")
                ) {
                    Text(stringResource(R.string.auth_phone_verify_cta))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )

    if (showCountryPicker) {
        CountryPickerDialog(
            selectedCountry = country,
            onSelect = {
                country = it
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false }
        )
    }
}

