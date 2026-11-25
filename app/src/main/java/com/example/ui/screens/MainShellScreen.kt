package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.SecurityPrivacyManager
import com.example.core.StorageBreakdown
import com.example.core.findActivity
import com.example.ui.components.localizedTitle
import com.example.data.model.AiAssistantMessage
import com.example.data.model.BackupRecord
import com.example.data.model.CallStatus
import com.example.data.model.ChatDraft
import com.example.data.model.ChatRoom
import com.example.data.model.ChatTopic
import com.example.data.model.ContactRequest
import com.example.data.model.DeviceKeyBundle
import com.example.data.model.DeviceNotification
import com.example.data.model.DevicePairingSession
import com.example.data.model.DlAccountStatus
import com.example.data.model.DlFeatureRestriction
import com.example.data.model.DlStaffRole
import com.example.data.model.GroupEvent
import com.example.data.model.PrivateChatSetting
import com.example.data.model.ReportCategory
import com.example.data.model.SupportTicketStatus
import com.example.data.model.SyncEvent
import com.example.data.model.UiState
import com.example.data.repository.AppearancePrefs
import com.example.data.repository.CreatedPairingChallenge
import com.example.data.repository.PreferencesRepository
import com.example.ui.components.DlEmptyState
import com.example.ui.components.QrScannerDialog
import com.example.ui.theme.LocalDlTokens
import com.example.ui.viewmodel.DlChatViewModel
import kotlinx.coroutines.launch

private data class NavDestinationSpec(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShellScreen(
    viewModel: DlChatViewModel,
    prefsRepo: PreferencesRepository? = null,
    incomingQrOrInvite: String? = null,
    onConsumeIncomingUri: () -> Unit = {}
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    val scope = rememberCoroutineScope()
    val localAppearance by (prefsRepo?.appearanceFlow
        ?.collectAsStateWithLifecycle(initialValue = AppearancePrefs())
        ?: remember { mutableStateOf(AppearancePrefs()) })

    val myProfileState by viewModel.myProfileState.collectAsStateWithLifecycle()
    val usersState by viewModel.discoverableUsersState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val devicesState by viewModel.devicesState.collectAsStateWithLifecycle()
    val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
    val contactRequestsState by viewModel.contactRequestsState.collectAsStateWithLifecycle()
    val foldersState by viewModel.foldersState.collectAsStateWithLifecycle()
    val savedState by viewModel.savedMessagesState.collectAsStateWithLifecycle()
    val backupsState by viewModel.backupsState.collectAsStateWithLifecycle()
    val storageBreakdown by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val myChatsState by viewModel.myChatsState.collectAsStateWithLifecycle()
    val publicChannelsState by viewModel.publicChannelsState.collectAsStateWithLifecycle()
    val storiesState by viewModel.storiesState.collectAsStateWithLifecycle()
    val callsState by viewModel.callsState.collectAsStateWithLifecycle()
    val reportsState by viewModel.reportsState.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
    val activeMessagesState by viewModel.activeMessagesState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val aiMessages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val aiBusy by viewModel.aiBusy.collectAsStateWithLifecycle()
    val smartReplies by viewModel.smartReplies.collectAsStateWithLifecycle()
    val summaryResult by viewModel.summaryResult.collectAsStateWithLifecycle()
    val translationResult by viewModel.translationResult.collectAsStateWithLifecycle()
    val aiExplainResult by viewModel.aiExplainResult.collectAsStateWithLifecycle()
    val aiSearchResult by viewModel.aiSearchResult.collectAsStateWithLifecycle()

    // V3 Coordinator Flows
    val pairingSessionsState by viewModel.v3.pairingSessionsState.collectAsStateWithLifecycle()
    val deviceNotificationsState by viewModel.v3.deviceNotificationsState.collectAsStateWithLifecycle()
    val syncEventsState by viewModel.v3.syncEventsState.collectAsStateWithLifecycle()
    val deviceKeyBundlesState by viewModel.v3.deviceKeyBundlesState.collectAsStateWithLifecycle()
    val accountSessionsState by viewModel.v3.accountSessionsState.collectAsStateWithLifecycle()
    val privateChatSettingsState by viewModel.v3.privateChatSettingsState.collectAsStateWithLifecycle()
    val chatDraftsState by viewModel.v3.chatDraftsState.collectAsStateWithLifecycle()
    val activeTopicsState by viewModel.v3.activeTopicsState.collectAsStateWithLifecycle()
    val activeEventsState by viewModel.v3.activeEventsState.collectAsStateWithLifecycle()
    val activeChallenge by viewModel.v3.activeChallenge.collectAsStateWithLifecycle()
    val appLocked by viewModel.v3.appLocked.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.v3.pendingSyncCount.collectAsStateWithLifecycle()
    val localAccounts by viewModel.v3.localAccounts.collectAsStateWithLifecycle()
    val hiddenUnlocked by viewModel.v3.hiddenUnlocked.collectAsStateWithLifecycle()

    // V6 Coordinator Flows
    val staffRole by viewModel.v6.staffRoleState.collectAsStateWithLifecycle()
    val myEnforcement by viewModel.v6.myEnforcementState.collectAsStateWithLifecycle()
    val myWarnings by viewModel.v6.myWarningsState.collectAsStateWithLifecycle()
    val myRestrictions by viewModel.v6.myRestrictionsState.collectAsStateWithLifecycle()
    val myAppeals by viewModel.v6.myAppealsState.collectAsStateWithLifecycle()
    val myTickets by viewModel.v6.myTicketsState.collectAsStateWithLifecycle()
    val helpArticles by viewModel.v6.helpArticlesState.collectAsStateWithLifecycle()
    val phoneAuthProfile by viewModel.v6.phoneAuthProfileState.collectAsStateWithLifecycle()
    val activeTicketId by viewModel.v6.activeTicketId.collectAsStateWithLifecycle()
    val activeTicketMessages by viewModel.v6.activeTicketMessagesState.collectAsStateWithLifecycle()
    val activeTicketInternalNotes by viewModel.v6.activeTicketNotesState.collectAsStateWithLifecycle()
    val allEnforcements by viewModel.v6.allEnforcementsState.collectAsStateWithLifecycle()
    val moderationCases by viewModel.v6.moderationCasesState.collectAsStateWithLifecycle()
    val moderationActions by viewModel.v6.moderationActionsState.collectAsStateWithLifecycle()
    val allAppeals by viewModel.v6.allAppealsState.collectAsStateWithLifecycle()
    val allTickets by viewModel.v6.allTicketsState.collectAsStateWithLifecycle()
    val allStaffRoles by viewModel.v6.allStaffRolesState.collectAsStateWithLifecycle()
    val supportAgents by viewModel.v6.supportAgentsState.collectAsStateWithLifecycle()
    val allPhoneProfiles by viewModel.v6.allPhoneProfilesState.collectAsStateWithLifecycle()
    val allSecurityEvents by viewModel.v6.allSecurityEventsState.collectAsStateWithLifecycle()
    val auditLogs by viewModel.v6.auditLogsState.collectAsStateWithLifecycle()
    val whatsappConfig by viewModel.v6.whatsappConfigState.collectAsStateWithLifecycle()
    val whatsappEvents by viewModel.v6.whatsappEventsState.collectAsStateWithLifecycle()
    val platformSettings by viewModel.v6.platformSettingsState.collectAsStateWithLifecycle()

    val myProfile = (myProfileState as? UiState.Success)?.data
    val allUsers = (usersState as? UiState.Success)?.data.orEmpty()
    val settings = (settingsState as? UiState.Success)?.data
    val devices = (devicesState as? UiState.Success)?.data.orEmpty()
    val contacts = (contactsState as? UiState.Success)?.data.orEmpty()
    val contactRequests = (contactRequestsState as? UiState.Success)?.data.orEmpty()
    val folders = (foldersState as? UiState.Success)?.data.orEmpty()
    val savedMessages = (savedState as? UiState.Success)?.data.orEmpty()
    val backups = (backupsState as? UiState.Success)?.data.orEmpty()
    val allChats = (myChatsState as? UiState.Success)?.data.orEmpty()
    val reports = (reportsState as? UiState.Success)?.data.orEmpty()
    val pairingSessions = (pairingSessionsState as? UiState.Success)?.data.orEmpty()
    val deviceNotifications = (deviceNotificationsState as? UiState.Success)?.data.orEmpty()
    val syncEvents = (syncEventsState as? UiState.Success)?.data.orEmpty()
    val deviceKeyBundles = (deviceKeyBundlesState as? UiState.Success)?.data.orEmpty()
    val accountSessions = (accountSessionsState as? UiState.Success)?.data.orEmpty()
    val privateSettingsList = (privateChatSettingsState as? UiState.Success)?.data.orEmpty()
    val privateSettingsMap = remember(privateSettingsList) { privateSettingsList.associateBy { it.chatId } }
    val chatDraftsList = (chatDraftsState as? UiState.Success)?.data.orEmpty()
    val draftsByChatIdMap = remember(chatDraftsList) { chatDraftsList.associate { it.chatId to it.draftText } }
    val chatDraftsByChatId = remember(chatDraftsList) { chatDraftsList.associateBy { it.chatId } }
    val activeTopics = (activeTopicsState as? UiState.Success)?.data.orEmpty()
    val activeEvents = (activeEventsState as? UiState.Success)?.data.orEmpty()
    val activeCall = (callsState as? UiState.Success)?.data.orEmpty()
        .firstOrNull { it.status == CallStatus.RINGING.name || it.status == CallStatus.ACTIVE.name }

    val unreadWarningsCount = remember(myWarnings) { myWarnings.count { !it.acknowledged } }
    val openMyTicketsCount = remember(myTickets) {
        myTickets.count {
            it.status != SupportTicketStatus.RESOLVED.name && it.status != SupportTicketStatus.CLOSED.name
        }
    }
    val globalOrChatsNotice = remember(myEnforcement) {
        viewModel.v6.restrictionNoticeFor(
            DlFeatureRestriction.SEND_DM,
            DlFeatureRestriction.SEND_GROUP,
            DlFeatureRestriction.CREATE_GROUPS,
            DlFeatureRestriction.CREATE_CHANNELS
        )
    }
    val callsRestrictionNotice = remember(myEnforcement) {
        viewModel.v6.restrictionNoticeFor(DlFeatureRestriction.START_CALLS)
    }
    val contactsRestrictionNotice = remember(myEnforcement) {
        viewModel.v6.restrictionNoticeFor(DlFeatureRestriction.SEND_INVITES)
    }
    val storiesRestrictionNotice = remember(myEnforcement) {
        viewModel.v6.restrictionNoticeFor(DlFeatureRestriction.POST_STORIES)
    }

    val totalUnreadChats = remember(allChats, privateSettingsMap, viewModel.currentUserId) {
        allChats.count { c ->
            privateSettingsMap[c.id]?.hidden != true && c.hasUnreadFor(viewModel.currentUserId)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var showSavedNotes by remember { mutableStateOf(false) }
    var showAdminDashboard by remember { mutableStateOf(false) }
    var showAccountStatusScreen by remember { mutableStateOf(false) }
    var showSupportCenterScreen by remember { mutableStateOf(false) }
    var showPoliciesScreen by remember { mutableStateOf(false) }
    var showChannelsSheet by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var showHiddenVault by remember { mutableStateOf(false) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var unlockForHiddenVaultPending by remember { mutableStateOf(false) }
    var lockedChatPendingUnlockId by remember { mutableStateOf<String?>(null) }
    val unlockedChatIds = remember { mutableStateMapOf<String, Boolean>() }
    var initialAppLockChecked by remember(viewModel.currentUserId) { mutableStateOf(false) }

    BackHandler(enabled = showPoliciesScreen) {
        showPoliciesScreen = false
    }
    BackHandler(enabled = showAccountStatusScreen) {
        showAccountStatusScreen = false
    }
    BackHandler(enabled = showSupportCenterScreen) {
        showSupportCenterScreen = false
    }
    BackHandler(enabled = showSettingsScreen && !showAccountStatusScreen && !showSupportCenterScreen && !showPoliciesScreen) {
        showSettingsScreen = false
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val chat = activeChat
        val msgs = (activeMessagesState as? UiState.Success)?.data.orEmpty()
        if (uri != null && chat != null) {
            viewModel.v3.exportSingleChat(context, uri, chat, msgs, "JSON")
        }
    }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val chat = activeChat
        val msgs = (activeMessagesState as? UiState.Success)?.data.orEmpty()
        if (uri != null && chat != null) {
            viewModel.v3.exportSingleChat(context, uri, chat, msgs, "TXT")
        }
    }

    val selectChatGuarded: (String) -> Unit = { chatId ->
        val isLocked = privateSettingsMap[chatId]?.locked == true
        val hasPin = SecurityPrivacyManager.hasPinSet(context, viewModel.currentUserId)
        if (isLocked && hasPin && unlockedChatIds[chatId] != true) {
            lockedChatPendingUnlockId = chatId
        } else {
            showSettingsScreen = false
            viewModel.selectChat(chatId)
        }
    }

    LaunchedEffect(settings, viewModel.currentUserId) {
        if (!initialAppLockChecked && settings != null) {
            initialAppLockChecked = true
            if (settings.appLockEnabled && SecurityPrivacyManager.hasPinSet(context, viewModel.currentUserId)) {
                viewModel.v3.setAppLocked(true)
            }
        }
    }

    LaunchedEffect(incomingQrOrInvite) {
        val raw = incomingQrOrInvite ?: return@LaunchedEffect
        viewModel.v3.confirmScannedPairingQr(raw) { openedChatId, _ ->
            if (!openedChatId.isNullOrBlank()) {
                selectChatGuarded(openedChatId)
            }
        }
        onConsumeIncomingUri()
    }

    LaunchedEffect(activeChat?.id) {
        val ch = activeChat ?: return@LaunchedEffect
        if (ch.type == "CHANNEL") {
            viewModel.v3.incrementChannelView(ch)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val navItems = listOf(
        NavDestinationSpec(stringResource(R.string.nav_chats), Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "tab_chats"),
        NavDestinationSpec(stringResource(R.string.nav_calls), Icons.Filled.Call, Icons.Outlined.Call, "tab_calls"),
        NavDestinationSpec(stringResource(R.string.nav_contacts), Icons.Filled.Contacts, Icons.Outlined.Contacts, "tab_contacts"),
        NavDestinationSpec(stringResource(R.string.nav_stories), Icons.Filled.AutoStories, Icons.Outlined.AutoStories, "tab_stories")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp
        val hasV6Overlay = showAdminDashboard || showAccountStatusScreen || showSupportCenterScreen || showPoliciesScreen
        val showFullConversation = !isExpanded && activeChat != null && !showSettingsScreen && !hasV6Overlay

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!isExpanded && !showFullConversation && !hasV6Overlay && !showSettingsScreen) {
                    Column {
                        com.example.ui.components.DlHairlineDivider()
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            navItems.forEachIndexed { idx, spec ->
                                val selected = selectedTab == idx
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        showSettingsScreen = false
                                        selectedTab = idx
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = tokens.textSecondary,
                                        unselectedTextColor = tokens.textSecondary
                                    ),
                                    icon = {
                                        val iconVector = if (selected) spec.selectedIcon else spec.unselectedIcon
                                        if (idx == 0 && totalUnreadChats > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(
                                                        containerColor = tokens.badgeUnread,
                                                        contentColor = tokens.onBadgeUnread
                                                    ) {
                                                        Text(if (totalUnreadChats > 99) "99+" else "$totalUnreadChats")
                                                    }
                                                }
                                            ) {
                                                Icon(iconVector, contentDescription = spec.label)
                                            }
                                        } else {
                                            Icon(iconVector, contentDescription = spec.label)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = spec.label,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag(spec.tag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val openAccountStatusAction = {
                    showSettingsScreen = false
                    showSupportCenterScreen = false
                    showPoliciesScreen = false
                    showAccountStatusScreen = true
                }
                if (showPoliciesScreen) {
                    CommunityPoliciesScreen(
                        onBack = { showPoliciesScreen = false }
                    )
                } else if (showAccountStatusScreen) {
                    AccountStatusScreen(
                        myEnforcement = myEnforcement,
                        warnings = myWarnings,
                        restrictions = myRestrictions,
                        appeals = myAppeals,
                        onBack = { showAccountStatusScreen = false },
                        onAcknowledgeWarning = { wid -> viewModel.v6.acknowledgeWarning(wid) },
                        onSubmitAppeal = { restId, enforcerUid, reason ->
                            viewModel.v6.submitAppeal(restId, enforcerUid, reason)
                        },
                        onProvideAppealInfo = { appealId, info ->
                            viewModel.v6.provideAppealAdditionalInfo(appealId, info)
                        },
                        onOpenPolicies = { showPoliciesScreen = true }
                    )
                } else if (showSupportCenterScreen) {
                    SupportCenterScreen(
                        myUid = viewModel.currentUserId,
                        myProfile = myProfile,
                        phoneProfile = phoneAuthProfile,
                        helpArticles = helpArticles,
                        tickets = myTickets,
                        activeTicketId = activeTicketId,
                        activeMessages = activeTicketMessages,
                        onSelectTicket = { tid -> viewModel.v6.selectTicket(tid) },
                        onCreateTicket = { cat, prio, subj, desc, waOptIn ->
                            viewModel.v6.createSupportTicket(
                                userName = myProfile?.displayName ?: context.getString(R.string.common_member_fallback),
                                userPhoneOrEmail = phoneAuthProfile?.phoneE164?.ifBlank { viewModel.currentUserEmail.orEmpty() }
                                    ?: viewModel.currentUserEmail.orEmpty(),
                                category = cat,
                                subject = subj,
                                description = desc,
                                priority = prio,
                                whatsappOptIn = waOptIn
                            )
                        },
                        onSendTicketMessage = { ticket, body ->
                            viewModel.v6.sendSupportMessage(
                                ticket = ticket,
                                senderName = myProfile?.displayName ?: context.getString(R.string.common_member_fallback),
                                body = body,
                                asSupportStaff = false
                            )
                        },
                        onBack = { showSupportCenterScreen = false }
                    )
                } else if (showAdminDashboard && (viewModel.isAdminUser || staffRole.canOpenStaffConsole)) {
                    AdminDashboardScreen(
                        users = allUsers,
                        chats = allChats,
                        reports = reports,
                        onBack = { showAdminDashboard = false },
                        onToggleSuspend = { uid, susp -> viewModel.adminToggleSuspendUser(uid, susp) },
                        onUpdateReport = { id, status -> viewModel.adminReviewReport(id, status) },
                        currentStaffUid = viewModel.currentUserId,
                        currentStaffName = myProfile?.displayName ?: "DL Staff",
                        staffRole = staffRole,
                        enforcements = allEnforcements,
                        moderationCases = moderationCases,
                        moderationActions = moderationActions,
                        appeals = allAppeals,
                        tickets = allTickets,
                        activeTicketId = activeTicketId,
                        activeTicketMessages = activeTicketMessages,
                        activeTicketInternalNotes = activeTicketInternalNotes,
                        staffRoles = allStaffRoles,
                        supportAgents = supportAgents,
                        helpArticles = helpArticles,
                        phoneProfiles = allPhoneProfiles,
                        securityEvents = allSecurityEvents,
                        auditLogs = auditLogs,
                        whatsappConfig = whatsappConfig,
                        whatsappEvents = whatsappEvents,
                        platformSettings = platformSettings,
                        onOpenCaseFromReport = { report -> viewModel.v6.openModerationCaseFromReport(report) },
                        onApplyEnforcement = { uid, st, feats, dur, hours, code, summary, approved ->
                            viewModel.v6.applyEnforcement(uid, st, feats, dur, hours, code, summary, approved)
                        },
                        onIssueWarning = { uid, code, title, desc, sev ->
                            viewModel.v6.issueWarning(
                                targetUid = uid,
                                reasonCode = code,
                                title = title,
                                description = desc,
                                severity = sev,
                                actorName = myProfile?.displayName ?: "Moderator"
                            )
                        },
                        onReviewAppeal = { appeal, nextStatus, note ->
                            viewModel.v6.reviewAppeal(appeal, nextStatus, note)
                        },
                        onSelectTicket = { tid -> viewModel.v6.selectTicket(tid) },
                        onSendStaffReply = { ticket, body, notifyWa ->
                            val targetPhone = allPhoneProfiles.firstOrNull { it.userId == ticket.userId }?.phoneE164
                            viewModel.v6.sendSupportMessage(
                                ticket = ticket,
                                senderName = myProfile?.displayName ?: "DL Support",
                                body = body,
                                asSupportStaff = true,
                                alsoNotifyWhatsApp = notifyWa,
                                whatsappRecipientE164 = targetPhone
                            )
                        },
                        onAddInternalNote = { tid, note ->
                            viewModel.v6.addSupportInternalNote(tid, myProfile?.displayName ?: "DL Staff", note)
                        },
                        onUpdateTicketMetadata = { tid, st, pr, aUid, aName ->
                            viewModel.v6.updateTicketMetadata(tid, st, pr, aUid, aName)
                        },
                        onAssignStaffRole = { uid, name, email, role ->
                            viewModel.v6.assignStaffRole(uid, name, email, role)
                        },
                        onUpsertSupportAgent = { uid, name, spec, avail ->
                            viewModel.v6.upsertSupportAgent(uid, name, spec, avail)
                        },
                        onUpsertHelpArticle = { aid, cat, title, summ, body, pub ->
                            viewModel.v6.upsertHelpArticle(aid, cat, title, summ, body, pub)
                        },
                        onRefreshWhatsAppHealth = { viewModel.v6.refreshWhatsAppHealth() },
                        onSavePlatformSettings = { next -> viewModel.v6.savePlatformSettings(next) }
                    )
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isExpanded) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                navItems.forEachIndexed { idx, spec ->
                                    val selected = !showSettingsScreen && selectedTab == idx
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            showSettingsScreen = false
                                            selectedTab = idx
                                            if (idx != 0 && idx != 1) viewModel.selectChat(null)
                                        },
                                        icon = {
                                            Icon(
                                                if (selected) spec.selectedIcon else spec.unselectedIcon,
                                                contentDescription = spec.label
                                            )
                                        },
                                        label = { Text(spec.label) },
                                        modifier = Modifier.testTag(spec.tag)
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        viewModel.selectChat(null)
                                        showSettingsScreen = true
                                    },
                                    modifier = Modifier
                                        .padding(bottom = 16.dp)
                                        .testTag("tab_settings")
                                ) {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = stringResource(R.string.nav_settings),
                                        tint = if (showSettingsScreen) MaterialTheme.colorScheme.primary else tokens.textSecondary
                                    )
                                }
                            }
                            VerticalDivider(color = tokens.hairline)
                        }

                        if (showSettingsScreen) {
                            SettingsScreen(
                                myProfile = myProfile,
                                settings = settings,
                                devices = devices,
                                savedMessages = savedMessages,
                                backups = backups,
                                storageBreakdown = storageBreakdown,
                                isAdminUser = viewModel.isAdminUser,
                                onUpdateProfile = { username, name, bio, color, uri, status, sls, so, rr, disc, twoFa, photoVis ->
                                    viewModel.updateProfile(
                                        context, username, name, bio, color, uri, status,
                                        sls, so, rr, disc, twoFa, photoVis, myProfile?.statusMessage
                                    )
                                },
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onRevokeDevice = { viewModel.revokeDevice(it) },
                                onAddPersonalNote = {
                                    viewModel.saveMessageOrNote(it, context.getString(R.string.shell_saved_notes_cd), null, "NOTE")
                                },
                                onDeleteSaved = { viewModel.deleteSavedNote(it) },
                                onRefreshStorage = { viewModel.refreshStorageStats() },
                                onClearLocalCache = { viewModel.clearLocalCache() },
                                onRotateKeys = { viewModel.rotateIdentityKeys(context) },
                                onExportBackup = { uri, pass -> viewModel.exportEncryptedBackup(context, uri, pass) },
                                onRestoreBackup = { uri, pass -> viewModel.restoreEncryptedBackup(context, uri, pass) },
                                onOpenAdminDashboard = {
                                    showSettingsScreen = false
                                    showAdminDashboard = true
                                },
                                onSignOut = { viewModel.signOut(context) },
                                onDeleteAccount = { viewModel.deleteAccount(context) },
                                currentDeviceId = viewModel.v3.currentDeviceId,
                                pairingSessions = pairingSessions,
                                deviceKeyBundles = deviceKeyBundles,
                                deviceNotifications = deviceNotifications,
                                activeChallenge = activeChallenge,
                                pendingSyncCount = pendingSyncCount,
                                syncEvents = syncEvents,
                                localAccountsCount = localAccounts.size,
                                onGeneratePairingQr = { name, type, os, browser ->
                                    viewModel.v3.generatePairingQr(name, type, os, browser)
                                },
                                onCancelPairing = { id -> viewModel.v3.cancelActivePairing(id) },
                                onConfirmScannedQr = { raw ->
                                    viewModel.v3.confirmScannedPairingQr(raw) { openedId, _ ->
                                        if (!openedId.isNullOrBlank()) selectChatGuarded(openedId)
                                    }
                                },
                                onConfirmManualPairing = { sid, tok -> viewModel.v3.confirmManualPairing(sid, tok) },
                                onRenameDevice = { id, newName -> viewModel.v3.renameDevice(id, newName) },
                                onRotateDeviceToken = { dev -> viewModel.v3.rotateDeviceToken(dev) },
                                onRevokeAllOtherDevices = { viewModel.v3.revokeAllOtherDevices() },
                                onMarkNotificationRead = { nid -> viewModel.v3.markNotificationRead(nid) },
                                onFlushOfflineSync = { viewModel.v3.flushOfflineSyncQueue() },
                                onLinkDeathLegionToken = { token -> viewModel.v3.linkDeathLegionWithAccessToken(context, token) },
                                onCompletePendingDeathLegionCode = { viewModel.v3.completePendingDeathLegionAuthCode(context) },
                                onRefreshDeathLegionSession = { viewModel.v3.refreshDeathLegionSession(context) },
                                onIntrospectDeathLegionSession = { viewModel.v3.introspectDeathLegionSession(context) },
                                onDisconnectDeathLegion = { viewModel.v3.disconnectDeathLegion(context) },
                                onOpenAccountSwitcher = { showAccountSwitcher = true },
                                onUpdateStatusMessage = { statusMsg ->
                                    val p = myProfile
                                    if (p != null) {
                                        viewModel.updateProfile(
                                            context, p.username, p.displayName, p.bio, p.avatarColor, null,
                                            p.onlineStatus, p.showLastSeen, p.showOnline, p.readReceipts, p.discoverable,
                                            p.twoFactorEnabled, p.profilePhotoVisibility, statusMsg
                                        )
                                    }
                                },
                                localAppearancePrefs = localAppearance,
                                onUpdateLocalUiPrefs = { redMotion, msgN, grpN, callN, vib, wifi, compact ->
                                    scope.launch {
                                        prefsRepo?.saveLocalUiPreferences(
                                            reducedMotion = redMotion,
                                            messageNotifications = msgN,
                                            groupNotifications = grpN,
                                            callNotifications = callN,
                                            vibrationEnabled = vib,
                                            wifiOnlyMedia = wifi,
                                            compactDensity = compact
                                        )
                                    }
                                },
                                onSelectLanguage = { code -> viewModel.setAppLanguage(code) },
                                onBack = { showSettingsScreen = false },
                                staffRole = staffRole,
                                myEnforcement = myEnforcement,
                                phoneAuthProfile = phoneAuthProfile,
                                unreadWarningsCount = unreadWarningsCount,
                                openTicketsCount = openMyTicketsCount,
                                onOpenAccountStatus = openAccountStatusAction,
                                onOpenSupportCenter = {
                                    showSettingsScreen = false
                                    showSupportCenterScreen = true
                                },
                                onOpenPolicies = { showPoliciesScreen = true },
                                onLinkPhoneCredential = { cred, country, optIn, done ->
                                    viewModel.linkPhoneCredential(context, cred, country, optIn, done)
                                },
                                onUpdateWhatsAppOptIn = { optIn ->
                                    viewModel.v6.updateWhatsAppOptIn(optIn)
                                }
                            )
                        } else if (isExpanded) {
                            val leftModifier = if (selectedTab == 0 || selectedTab == 1) {
                                Modifier
                                    .width(380.dp)
                                    .fillMaxHeight()
                            } else {
                                Modifier.fillMaxSize()
                            }

                            Box(modifier = leftModifier) {
                                TabContent(
                                    selectedTab = selectedTab,
                                    viewModel = viewModel,
                                    myProfile = myProfile,
                                    myChatsState = myChatsState,
                                    publicChannelsState = publicChannelsState,
                                    usersState = usersState,
                                    contacts = contacts,
                                    contactRequests = contactRequests,
                                    storiesState = storiesState,
                                    callsState = callsState,
                                    folders = folders,
                                    settings = settings,
                                    selectedChatId = selectedChatId,
                                    aiSearchResult = aiSearchResult,
                                    aiMessages = aiMessages,
                                    aiBusy = aiBusy,
                                    onOpenAi = { selectedTab = 4 },
                                    onOpenSaved = { showSavedNotes = true },
                                    onOpenChannels = { showChannelsSheet = true },
                                    onOpenSettings = { showSettingsScreen = true },
                                    onSelectTabAndChat = selectChatGuarded,
                                    localAccountsCount = localAccounts.size,
                                    draftsByChatId = draftsByChatIdMap,
                                    privateSettingsMap = privateSettingsMap,
                                    onOpenHiddenVault = {
                                        if (SecurityPrivacyManager.hasPinSet(context, viewModel.currentUserId) && !hiddenUnlocked) {
                                            unlockForHiddenVaultPending = true
                                        } else {
                                            showHiddenVault = true
                                        }
                                    },
                                    onOpenAccountSwitcher = { showAccountSwitcher = true },
                                    onOpenQrScanner = { showQrScannerDialog = true },
                                    showSettingsActionInHeader = false
                                )
                            }

                            if (selectedTab == 0 || selectedTab == 1) {
                                VerticalDivider(color = tokens.hairline)
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    val chat = activeChat
                                    if (chat != null) {
                                        ActiveConversationHost(
                                            viewModel = viewModel,
                                            myProfile = myProfile,
                                            chat = chat,
                                            messagesState = activeMessagesState,
                                            allChats = allChats,
                                            allUsers = allUsers,
                                            contacts = contacts,
                                            bubbleStyle = settings?.bubbleStyle ?: "MODERN",
                                            wallpaperId = settings?.wallpaperId ?: "nebula",
                                            aiEnabled = settings?.aiEnabled ?: true,
                                            aiBusy = aiBusy,
                                            smartReplies = smartReplies,
                                            summaryResult = summaryResult,
                                            translationResult = translationResult,
                                            explainResult = aiExplainResult,
                                            showBackButton = false,
                                            topics = activeTopics,
                                            events = activeEvents,
                                            syncedDraft = chatDraftsByChatId[chat.id],
                                            privateSetting = privateSettingsMap[chat.id],
                                            onExportChat = { format ->
                                                val safeName = chat.resolveTitle(viewModel.currentUserId)
                                                    .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                                                if (format == "TXT") {
                                                    exportTxtLauncher.launch("${safeName}_export.txt")
                                                } else {
                                                    exportJsonLauncher.launch("${safeName}_export.json")
                                                }
                                            },
                                            restrictionNotice = viewModel.v6.restrictionNoticeFor(
                                                DlFeatureRestriction.SEND_DM,
                                                DlFeatureRestriction.SEND_GROUP,
                                                DlFeatureRestriction.SEND_MEDIA,
                                                DlFeatureRestriction.SEND_VOICE
                                            ),
                                            onOpenAccountStatus = openAccountStatusAction
                                        )
                                    } else {
                                        DlEmptyState(
                                            icon = Icons.Filled.ChatBubble,
                                            title = stringResource(R.string.shell_desktop_empty_title),
                                            subtitle = stringResource(R.string.shell_desktop_empty_subtitle)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Compact Handheld Layout
                            val chat = activeChat
                            if (chat != null) {
                                ActiveConversationHost(
                                    viewModel = viewModel,
                                    myProfile = myProfile,
                                    chat = chat,
                                    messagesState = activeMessagesState,
                                    allChats = allChats,
                                    allUsers = allUsers,
                                    contacts = contacts,
                                    bubbleStyle = settings?.bubbleStyle ?: "MODERN",
                                    wallpaperId = settings?.wallpaperId ?: "nebula",
                                    aiEnabled = settings?.aiEnabled ?: true,
                                    aiBusy = aiBusy,
                                    smartReplies = smartReplies,
                                    summaryResult = summaryResult,
                                    translationResult = translationResult,
                                    explainResult = aiExplainResult,
                                    showBackButton = true,
                                    topics = activeTopics,
                                    events = activeEvents,
                                    syncedDraft = chatDraftsByChatId[chat.id],
                                    privateSetting = privateSettingsMap[chat.id],
                                    onExportChat = { format ->
                                        val safeName = chat.resolveTitle(viewModel.currentUserId)
                                            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                                        if (format == "TXT") {
                                            exportTxtLauncher.launch("${safeName}_export.txt")
                                        } else {
                                            exportJsonLauncher.launch("${safeName}_export.json")
                                        }
                                    },
                                    restrictionNotice = viewModel.v6.restrictionNoticeFor(
                                        DlFeatureRestriction.SEND_DM,
                                        DlFeatureRestriction.SEND_GROUP,
                                        DlFeatureRestriction.SEND_MEDIA,
                                        DlFeatureRestriction.SEND_VOICE
                                    ),
                                    onOpenAccountStatus = openAccountStatusAction
                                )
                            } else {
                                AnimatedContent(
                                    targetState = selectedTab,
                                    transitionSpec = {
                                        if (tokens.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                                        else fadeIn() togetherWith fadeOut()
                                    },
                                    label = "main_tab_transition"
                                ) { tab ->
                                    TabContent(
                                        selectedTab = tab,
                                        viewModel = viewModel,
                                        myProfile = myProfile,
                                        myChatsState = myChatsState,
                                        publicChannelsState = publicChannelsState,
                                        usersState = usersState,
                                        contacts = contacts,
                                        contactRequests = contactRequests,
                                        storiesState = storiesState,
                                        callsState = callsState,
                                        folders = folders,
                                        settings = settings,
                                        selectedChatId = selectedChatId,
                                        aiSearchResult = aiSearchResult,
                                        aiMessages = aiMessages,
                                        aiBusy = aiBusy,
                                        onOpenAi = { selectedTab = 4 },
                                        onOpenSaved = { showSavedNotes = true },
                                        onOpenChannels = { showChannelsSheet = true },
                                        onOpenSettings = { showSettingsScreen = true },
                                        onSelectTabAndChat = selectChatGuarded,
                                        localAccountsCount = localAccounts.size,
                                        draftsByChatId = draftsByChatIdMap,
                                        privateSettingsMap = privateSettingsMap,
                                        onOpenHiddenVault = {
                                            if (SecurityPrivacyManager.hasPinSet(context, viewModel.currentUserId) && !hiddenUnlocked) {
                                                unlockForHiddenVaultPending = true
                                            } else {
                                                showHiddenVault = true
                                            }
                                        },
                                        onOpenAccountSwitcher = { showAccountSwitcher = true },
                                        onOpenQrScanner = { showQrScannerDialog = true },
                                        showSettingsActionInHeader = true,
                                        chatsRestrictionNotice = globalOrChatsNotice,
                                        callsRestrictionNotice = callsRestrictionNotice,
                                        contactsRestrictionNotice = contactsRestrictionNotice,
                                        storiesRestrictionNotice = storiesRestrictionNotice,
                                        onOpenAccountStatus = openAccountStatusAction
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeCall != null) {
            ActiveCallOverlay(
                myUid = viewModel.currentUserId,
                call = activeCall,
                onAccept = { viewModel.updateCallStatus(activeCall.id, "ACTIVE", 0L) },
                onDecline = { viewModel.updateCallStatus(activeCall.id, "DECLINED", 0L) },
                onEnd = { duration -> viewModel.updateCallStatus(activeCall.id, "ENDED", duration) }
            )
        }

        val showLockGate = appLocked || unlockForHiddenVaultPending || lockedChatPendingUnlockId != null
        if (showLockGate) {
            val fallbackName = stringResource(R.string.common_member_fallback)
            AppLockOverlay(
                uid = viewModel.currentUserId,
                displayName = myProfile?.displayName?.ifBlank { fallbackName } ?: fallbackName,
                biometricEnabled = settings?.biometricLockEnabled ?: false,
                onUnlocked = {
                    if (appLocked) viewModel.v3.setAppLocked(false)
                    if (unlockForHiddenVaultPending) {
                        unlockForHiddenVaultPending = false
                        viewModel.v3.setHiddenVaultUnlocked(true)
                        showHiddenVault = true
                    }
                    val targetChat = lockedChatPendingUnlockId
                    if (targetChat != null) {
                        unlockedChatIds[targetChat] = true
                        lockedChatPendingUnlockId = null
                        viewModel.selectChat(targetChat)
                    }
                },
                onSignOut = {
                    unlockForHiddenVaultPending = false
                    lockedChatPendingUnlockId = null
                    viewModel.v3.setAppLocked(false)
                    viewModel.signOut(context)
                }
            )
        }
    }

    if (showChannelsSheet) {
        ModalBottomSheet(onDismissRequest = { showChannelsSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
                ChannelsScreen(
                    myUid = viewModel.currentUserId,
                    myChatsState = myChatsState,
                    publicChannelsState = publicChannelsState,
                    onOpenChannel = { id ->
                        showChannelsSheet = false
                        viewModel.selectChat(id)
                    },
                    onJoinChannel = { ch ->
                        showChannelsSheet = false
                        viewModel.joinChannel(ch)
                    },
                    onCreateChannel = { title, desc, handle, pub ->
                        showChannelsSheet = false
                        viewModel.createGroupOrChannel("CHANNEL", title, desc, emptyList(), pub, handle, true)
                    }
                )
            }
        }
    }

    if (showAiSheet) {
        DlAiAssistantSheet(
            messages = aiMessages,
            busy = aiBusy,
            onAsk = { viewModel.askDlAi(it) },
            onDismiss = { showAiSheet = false }
        )
    }

    if (showSavedNotes) {
        SavedNotesVaultDialog(
            savedMessages = savedMessages,
            onDismiss = { showSavedNotes = false },
            onAddNote = { viewModel.saveMessageOrNote(it, context.getString(R.string.shell_saved_notes_cd), null, "NOTE") },
            onDelete = { viewModel.deleteSavedNote(it) }
        )
    }

    if (showQrScannerDialog) {
        QrScannerDialog(
            title = stringResource(R.string.qr_scanner_default_title),
            hint = stringResource(R.string.qr_scanner_default_hint),
            onDismiss = { showQrScannerDialog = false },
            onQrScanned = { raw ->
                viewModel.v3.confirmScannedPairingQr(raw) { openedChatId, _ ->
                    if (!openedChatId.isNullOrBlank()) selectChatGuarded(openedChatId)
                }
            }
        )
    }

    if (showHiddenVault) {
        HiddenChatsVaultSheet(
            myUid = viewModel.currentUserId,
            chats = allChats,
            privateSettings = privateSettingsMap,
            onOpenChat = selectChatGuarded,
            onUnhideChat = { st -> viewModel.v3.toggleChatHidden(st.chatId, currentlyHidden = st.hidden, currentlyLocked = st.locked) },
            onDismiss = { showHiddenVault = false }
        )
    }

    if (showAccountSwitcher) {
        MultiAccountSwitcherSheet(
            currentUid = viewModel.currentUserId,
            localAccounts = localAccounts,
            remoteSessions = accountSessions,
            onAddOrSwitchAccount = {
                context.findActivity()?.let { viewModel.v3.switchAccountInteractive(it) }
            },
            onRemoveLocalSlot = { uid -> viewModel.v3.removeLocalAccountSlot(uid) },
            onDismiss = { showAccountSwitcher = false }
        )
    }
}

@Composable
private fun TabContent(
    selectedTab: Int,
    viewModel: DlChatViewModel,
    myProfile: com.example.data.model.UserProfile?,
    myChatsState: UiState<List<com.example.data.model.ChatRoom>>,
    publicChannelsState: UiState<List<com.example.data.model.ChatRoom>>,
    usersState: UiState<List<com.example.data.model.UserProfile>>,
    contacts: List<com.example.data.model.ContactEntry>,
    contactRequests: List<ContactRequest>,
    storiesState: UiState<List<com.example.data.model.StoryItem>>,
    callsState: UiState<List<com.example.data.model.CallSession>>,
    folders: List<com.example.data.model.ChatFolder>,
    settings: com.example.data.model.UserSetting?,
    selectedChatId: String?,
    aiSearchResult: String?,
    aiMessages: List<AiAssistantMessage>,
    aiBusy: Boolean,
    onOpenAi: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTabAndChat: (String) -> Unit,
    localAccountsCount: Int = 1,
    draftsByChatId: Map<String, String> = emptyMap(),
    privateSettingsMap: Map<String, PrivateChatSetting> = emptyMap(),
    onOpenHiddenVault: () -> Unit = {},
    onOpenAccountSwitcher: () -> Unit = {},
    onOpenQrScanner: () -> Unit = {},
    showSettingsActionInHeader: Boolean = true,
    chatsRestrictionNotice: String? = null,
    callsRestrictionNotice: String? = null,
    contactsRestrictionNotice: String? = null,
    storiesRestrictionNotice: String? = null,
    onOpenAccountStatus: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val allUsers = (usersState as? UiState.Success)?.data.orEmpty()
    val allChats = (myChatsState as? UiState.Success)?.data.orEmpty()

    when (selectedTab) {
        0 -> ChatsListPane(
            myUid = viewModel.currentUserId,
            myProfile = myProfile,
            chatsState = myChatsState,
            publicChannelsState = publicChannelsState,
            usersState = usersState,
            contacts = contacts,
            folders = folders,
            selectedChatId = selectedChatId,
            aiSearchResult = aiSearchResult,
            aiBusy = aiBusy,
            onSelectChat = onSelectTabAndChat,
            onOpenDirectChat = { viewModel.openDirectChat(it) },
            onJoinChannel = { viewModel.joinChannel(it) },
            onCreateGroupOrChannel = { type, title, desc, members, pub, handle, adminOnly ->
                viewModel.createGroupOrChannel(type, title, desc, members, pub, handle, adminOnly)
            },
            onCreateFolder = { name, ft, inc, exc -> viewModel.createFolder(name, ft, inc, exc) },
            onDeleteFolder = { id -> viewModel.deleteFolder(id) },
            onAiSearchAssist = { query -> viewModel.aiSearchAssist(query, allChats) },
            onClearAiSearch = { viewModel.clearAiSearch() },
            onOpenChannelsExplorer = onOpenChannels,
            onOpenAiAssistant = onOpenAi,
            onOpenSavedNotes = onOpenSaved,
            draftsByChatId = draftsByChatId,
            privateChatSettings = privateSettingsMap,
            localAccountsCount = localAccountsCount,
            onOpenHiddenVault = onOpenHiddenVault,
            onOpenAccountSwitcher = onOpenAccountSwitcher,
            onOpenQrScanner = onOpenQrScanner,
            onOpenSettings = if (showSettingsActionInHeader) onOpenSettings else null,
            restrictionNotice = chatsRestrictionNotice,
            onOpenAccountStatus = onOpenAccountStatus
        )
        1 -> CallsScreen(
            myUid = viewModel.currentUserId,
            callsState = callsState,
            allUsers = allUsers,
            onStartCall = { peer, callType -> viewModel.startCall(peer, callType) },
            onOpenSettings = if (showSettingsActionInHeader) onOpenSettings else null,
            restrictionNotice = callsRestrictionNotice,
            onOpenAccountStatus = onOpenAccountStatus
        )
        2 -> ContactsScreen(
            myUid = viewModel.currentUserId,
            myProfile = myProfile,
            usersState = usersState,
            contacts = contacts,
            contactRequests = contactRequests,
            onOpenDirectChat = { viewModel.openDirectChat(it) },
            onSendContactRequest = { viewModel.sendContactRequest(it) },
            onRespondContactRequest = { req, accept -> viewModel.respondContactRequest(req, accept) },
            onToggleFavorite = { u, c -> viewModel.toggleContactFavorite(u, c) },
            onToggleBlock = { u, c -> viewModel.toggleBlockUser(u, c) },
            onOpenSettings = if (showSettingsActionInHeader) onOpenSettings else null,
            restrictionNotice = contactsRestrictionNotice,
            onOpenAccountStatus = onOpenAccountStatus
        )
        3 -> StoriesScreen(
            myUid = viewModel.currentUserId,
            storiesState = storiesState,
            allUsers = allUsers,
            onCreateStory = { text, hex, uri, hidden ->
                viewModel.createStory(context, text, hex, uri, hidden)
            },
            onViewOrReactStory = { story, emoji ->
                viewModel.viewOrReactStory(story, emoji)
            },
            onReplyToStory = { author, replyText ->
                viewModel.openDirectChat(author)
                viewModel.saveMessageOrNote(
                    replyText,
                    context.getString(R.string.stories_reply_source, author.displayName),
                    null,
                    "SAVED"
                )
            },
            onOpenSettings = if (showSettingsActionInHeader) onOpenSettings else null,
            restrictionNotice = storiesRestrictionNotice,
            onOpenAccountStatus = onOpenAccountStatus
        )
        else -> DlAiScreen(
            messages = aiMessages,
            busy = aiBusy,
            aiEnabled = settings?.aiEnabled ?: true,
            onAsk = { viewModel.askDlAi(it) },
            onClearChat = { viewModel.resetAiSession() },
            onOpenSettings = if (showSettingsActionInHeader) onOpenSettings else null
        )
    }
}

@Composable
private fun ActiveConversationHost(
    viewModel: DlChatViewModel,
    myProfile: com.example.data.model.UserProfile?,
    chat: com.example.data.model.ChatRoom,
    messagesState: UiState<List<com.example.data.model.ChatMessage>>,
    allChats: List<com.example.data.model.ChatRoom>,
    allUsers: List<com.example.data.model.UserProfile>,
    contacts: List<com.example.data.model.ContactEntry>,
    bubbleStyle: String,
    wallpaperId: String,
    aiEnabled: Boolean,
    aiBusy: Boolean,
    smartReplies: List<String>,
    summaryResult: String?,
    translationResult: Pair<String, String>?,
    explainResult: String?,
    showBackButton: Boolean,
    topics: List<ChatTopic> = emptyList(),
    events: List<GroupEvent> = emptyList(),
    syncedDraft: ChatDraft? = null,
    privateSetting: PrivateChatSetting? = null,
    onExportChat: (String) -> Unit = {},
    restrictionNotice: String? = null,
    onOpenAccountStatus: (() -> Unit)? = null
) {
    val context = LocalContext.current
    ConversationScreen(
        myUid = viewModel.currentUserId,
        myProfile = myProfile,
        chat = chat,
        messagesState = messagesState,
        allChats = allChats,
        allUsers = allUsers,
        contacts = contacts,
        bubbleStyle = bubbleStyle,
        wallpaperId = wallpaperId,
        aiEnabled = aiEnabled,
        aiBusy = aiBusy,
        smartReplies = smartReplies,
        summaryResult = summaryResult,
        translationResult = translationResult,
        explainResult = explainResult,
        showBackButton = showBackButton,
        onBack = { viewModel.selectChat(null) },
        onLoadOlderMessages = { viewModel.loadOlderMessages() },
        onMarkRead = { msgs -> viewModel.markActiveChatRead(chat, msgs) },
        onTypingChanged = { typing -> viewModel.updateTyping(chat, typing) },
        onSendText = { type, text, replyId, replyPrev, fwdFrom, sched ->
            viewModel.sendTextMessage(chat, type, text, replyId, replyPrev, fwdFrom, sched)
        },
        onSendImage = { uri, caption -> viewModel.sendImageMessage(context, chat, uri, caption) },
        onSendFileAttachment = { uri, msgType -> viewModel.sendFileAttachment(context, chat, uri, msgType) },
        onSendVoiceNote = { clip -> viewModel.sendVoiceNote(chat, clip) },
        onSendPoll = { q, opts -> viewModel.sendPollMessage(chat, q, opts) },
        onEditMessage = { msgId, newText -> viewModel.editMessage(chat, msgId, newText) },
        onDeleteForEveryone = { msgId -> viewModel.deleteMessageForEveryone(chat.id, msgId) },
        onToggleStar = { msgId -> viewModel.toggleStarMessage(chat.id, msgId) },
        onToggleReaction = { msg, emoji -> viewModel.toggleReaction(chat.id, msg, emoji) },
        onVotePoll = { msg, idx -> viewModel.votePoll(chat.id, msg, idx) },
        onPinMessage = { msg -> viewModel.pinMessage(chat, msg) },
        onSaveMessage = { msg ->
            viewModel.saveMessageOrNote(msg.text, chat.resolveTitle(viewModel.currentUserId), msg.id, "SAVED")
        },
        onForwardMessage = { dest, msg ->
            viewModel.sendTextMessage(dest, "TEXT", msg.text, forwardedFrom = msg.senderName)
        },
        onJoinChannel = { viewModel.joinChannel(chat) },
        onRequestJoinGroup = { viewModel.requestJoinGroup(chat.id) },
        onApproveJoinRequest = { applicant, approve -> viewModel.approveJoinRequest(chat.id, applicant, approve) },
        onStartCall = { peer, callType -> viewModel.startCall(peer, callType) },
        onSummarizeChat = { msgs -> viewModel.summarizeChat(chat, msgs) },
        onSuggestSmartReplies = { msgs -> viewModel.suggestSmartReplies(chat, msgs) },
        onRewriteDraft = { draft, tone, cb -> viewModel.rewriteDraft(draft, tone, cb) },
        onTranslateMessage = { msg, lang -> viewModel.translateMessage(msg, lang) },
        onExplainMessage = { msg -> viewModel.explainMessage(msg) },
        onToggleMute = { viewModel.toggleChatMute(chat) },
        onToggleArchive = { viewModel.toggleChatArchive(chat) },
        onSetDisappearing = { secs -> viewModel.setDisappearingSeconds(chat, secs) },
        onToggleAdminOnly = { enabled -> viewModel.toggleAdminOnlyPost(chat, enabled) },
        onUpdatePermissions = { pin, inv -> viewModel.updateGroupPermissions(chat, pin, inv) },
        onRegenerateInvite = { viewModel.regenerateInviteCode(chat) },
        onRevokeInvite = { revoke -> viewModel.revokeInviteCode(chat, revoke) },
        onUpdateChatTheme = { hex -> viewModel.updateChatThemeAccent(chat, hex) },
        onAddMember = { u -> viewModel.addMemberToChat(chat, u) },
        onPromoteAdmin = { uid -> viewModel.promoteMemberAdmin(chat, uid) },
        onRemoveMember = { uid -> viewModel.removeMemberFromChat(chat, uid) },
        onToggleFavorite = { u, c -> viewModel.toggleContactFavorite(u, c) },
        onToggleBlock = { u, c -> viewModel.toggleBlockUser(u, c) },
        onSubmitReport = { targetUid, targetType, reason ->
            viewModel.submitReport(targetUid, targetType, reason)
        },
        onDismissSummary = { viewModel.clearSummary() },
        onDismissTranslation = { viewModel.clearTranslation() },
        onDismissExplain = { viewModel.clearAiExplain() },
        topics = topics,
        events = events,
        syncedDraft = syncedDraft,
        privateSetting = privateSetting,
        allGroupChats = allChats.filter { it.type == "GROUP" },
        onSaveDraft = { text, replyToId -> viewModel.v3.saveDraft(chat.id, text, replyToId) },
        onSendQuiz = { q, opts, correctIdx, expl, topicId ->
            viewModel.sendQuizMessage(chat, q, opts, correctIdx, expl, topicId)
        },
        onSendSticker = { sticker, topicId ->
            viewModel.sendStickerMessage(chat, sticker, topicId)
        },
        onSendTopicText = { type, text, replyId, replyPrev, fwdFrom, sched, topicId ->
            viewModel.sendTextMessage(chat, type, text, replyId, replyPrev, fwdFrom, sched, topicId)
        },
        onCreateTopic = { title, emoji -> viewModel.v3.createTopic(chat, title, emoji) },
        onCreateGroupEvent = { title, desc, hours -> viewModel.v3.createGroupEvent(chat.id, title, desc, hours) },
        onToggleEventRsvp = { evId -> viewModel.v3.toggleEventRsvp(chat.id, evId) },
        onToggleHidden = {
            viewModel.v3.toggleChatHidden(
                chat.id,
                currentlyHidden = privateSetting?.hidden ?: false,
                currentlyLocked = privateSetting?.locked ?: false
            )
        },
        onToggleLocked = {
            viewModel.v3.toggleChatLocked(
                chat.id,
                currentlyHidden = privateSetting?.hidden ?: false,
                currentlyLocked = privateSetting?.locked ?: false
            )
        },
        onExportChat = onExportChat,
        onSetSlowMode = { secs -> viewModel.v3.updateGroupModeration(chat, slowModeSeconds = secs) },
        onToggleRestrictedUid = { uid -> viewModel.v3.updateGroupModeration(chat, toggleRestrictedUid = uid) },
        onToggleAntiSpam = { enabled -> viewModel.v3.updateGroupModeration(chat, antiSpamEnabled = enabled) },
        onLinkDiscussionGroup = { groupId -> viewModel.v3.updateGroupModeration(chat, linkedGroupId = groupId) },
        onSummarizeVoiceTranscript = { msg, transcript ->
            viewModel.summarizeVoiceMessageTranscript(chat.id, msg.id, transcript)
        },
        restrictionNotice = restrictionNotice,
        onOpenAccountStatus = onOpenAccountStatus,
        onSubmitDetailedReport = { targetUid, targetType, category, reason, messageId, evidenceSnippet ->
            viewModel.v6.submitDetailedReport(
                targetUid = targetUid,
                targetType = targetType,
                category = category,
                reason = reason,
                chatId = chat.id,
                messageId = messageId,
                targetName = chat.resolveTitle(viewModel.currentUserId),
                evidenceSnippet = evidenceSnippet
            )
        }
    )
}
