package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AdminAuditLog
import com.example.data.model.AppealRecord
import com.example.data.model.AppealStatus
import com.example.data.model.AuthSecurityEvent
import com.example.data.model.ChatRoom
import com.example.data.model.DlAccountStatus
import com.example.data.model.DlFeatureRestriction
import com.example.data.model.DlRestrictionDuration
import com.example.data.model.DlStaffRole
import com.example.data.model.HelpArticle
import com.example.data.model.ModerationActionRecord
import com.example.data.model.ModerationCase
import com.example.data.model.ModerationReport
import com.example.data.model.OnlineStatus
import com.example.data.model.PhoneAuthProfile
import com.example.data.model.PlatformRuntimeSettings
import com.example.data.model.ReportCategory
import com.example.data.model.StaffRoleRecord
import com.example.data.model.SupportAgentRecord
import com.example.data.model.SupportCategory
import com.example.data.model.SupportInternalNote
import com.example.data.model.SupportPriority
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketMessage
import com.example.data.model.SupportTicketStatus
import com.example.data.model.UserEnforcement
import com.example.data.model.UserProfile
import com.example.data.model.WhatsAppConfiguration
import com.example.data.model.WhatsAppMessageEvent
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlSearchField
import com.example.ui.components.DlSectionHeader
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTopBar
import com.example.ui.components.localizedLabel
import com.example.ui.theme.DlCrimson
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@Composable
fun AdminDashboardScreen(
    users: List<UserProfile>,
    chats: List<ChatRoom>,
    reports: List<ModerationReport>,
    onBack: () -> Unit,
    onToggleSuspend: (String, Boolean) -> Unit,
    onUpdateReport: (String, String) -> Unit,
    currentStaffUid: String = "",
    currentStaffName: String = "DL Staff",
    staffRole: DlStaffRole = DlStaffRole.SUPER_ADMIN,
    enforcements: List<UserEnforcement> = emptyList(),
    moderationCases: List<ModerationCase> = emptyList(),
    moderationActions: List<ModerationActionRecord> = emptyList(),
    appeals: List<AppealRecord> = emptyList(),
    tickets: List<SupportTicket> = emptyList(),
    activeTicketId: String? = null,
    activeTicketMessages: List<SupportTicketMessage> = emptyList(),
    activeTicketInternalNotes: List<SupportInternalNote> = emptyList(),
    staffRoles: List<StaffRoleRecord> = emptyList(),
    supportAgents: List<SupportAgentRecord> = emptyList(),
    helpArticles: List<HelpArticle> = emptyList(),
    phoneProfiles: List<PhoneAuthProfile> = emptyList(),
    securityEvents: List<AuthSecurityEvent> = emptyList(),
    auditLogs: List<AdminAuditLog> = emptyList(),
    whatsappConfig: WhatsAppConfiguration? = null,
    whatsappEvents: List<WhatsAppMessageEvent> = emptyList(),
    platformSettings: PlatformRuntimeSettings = PlatformRuntimeSettings(id = "runtime"),
    onOpenCaseFromReport: (ModerationReport) -> Unit = {},
    onApplyEnforcement: (
        targetUid: String,
        targetStatus: DlAccountStatus,
        selectedFeatures: Set<DlFeatureRestriction>,
        duration: DlRestrictionDuration,
        customHours: Int,
        reasonCode: String,
        reasonSummary: String,
        humanApprovedPermanent: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onIssueWarning: (targetUid: String, reasonCode: String, title: String, description: String, severity: String) -> Unit = { _, _, _, _, _ -> },
    onReviewAppeal: (AppealRecord, AppealStatus, String) -> Unit = { _, _, _ -> },
    onSelectTicket: (String?) -> Unit = {},
    onSendStaffReply: (ticket: SupportTicket, body: String, notifyWhatsApp: Boolean) -> Unit = { _, _, _ -> },
    onAddInternalNote: (ticketId: String, note: String) -> Unit = { _, _ -> },
    onUpdateTicketMetadata: (
        ticketId: String,
        status: SupportTicketStatus?,
        priority: SupportPriority?,
        assignedUid: String?,
        assignedName: String?
    ) -> Unit = { _, _, _, _, _ -> },
    onAssignStaffRole: (targetUid: String, displayName: String, email: String, role: DlStaffRole) -> Unit = { _, _, _, _ -> },
    onUpsertSupportAgent: (userId: String, displayName: String, specialty: String, availability: String) -> Unit = { _, _, _, _ -> },
    onUpsertHelpArticle: (articleId: String?, category: SupportCategory, title: String, summary: String, body: String, published: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onRefreshWhatsAppHealth: () -> Unit = {},
    onSavePlatformSettings: (PlatformRuntimeSettings) -> Unit = {}
) {
    val tokens = LocalDlTokens.current
    BackHandler(onBack = onBack)

    var activeSection by remember { mutableStateOf(AdminDashboardSection.OVERVIEW) }
    var userSearch by remember { mutableStateOf("") }
    var restrictionTargetUid by remember { mutableStateOf(users.firstOrNull()?.userId.orEmpty()) }

    val onlineCount = users.count { it.onlineStatus == "ONLINE" }
    val openReports = reports.count { it.status == "OPEN" }
    val activeRestrictionsCount = enforcements.count {
        it.isGloballySuspended() || it.activeBlockedFeatures().isNotEmpty()
    }
    val openAppealsCount = appeals.count {
        it.status == AppealStatus.SUBMITTED.name || it.status == AppealStatus.UNDER_REVIEW.name
    }
    val openTicketsCount = tickets.count {
        it.status != SupportTicketStatus.RESOLVED.name && it.status != SupportTicketStatus.CLOSED.name
    }

    val enforcementByUid = remember(enforcements) { enforcements.associateBy { it.userId } }
    val effectiveRestrictionTargetUid = (users.firstOrNull { it.userId == restrictionTargetUid } ?: users.firstOrNull())?.userId.orEmpty()
    val enforcementFormState = rememberAdminEnforcementFormState(
        targetUid = effectiveRestrictionTargetUid,
        currentEnforcement = enforcementByUid[effectiveRestrictionTargetUid]
    )
    val filteredUsers = remember(users, userSearch) {
        val q = userSearch.trim().lowercase()
        if (q.isEmpty()) users
        else users.filter {
            it.displayName.lowercase().contains(q) ||
                it.username.lowercase().contains(q) ||
                it.userId.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("admin_dashboard_screen")
    ) {
        DlTopBar(
            title = stringResource(R.string.admin_title),
            subtitle = "${staffRole.label} · ${stringResource(R.string.admin_subtitle)}",
            onBack = onBack
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = DlSpacing.base, vertical = DlSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdminDashboardSection.entries.forEach { sec ->
                FilterChip(
                    selected = activeSection == sec,
                    onClick = { activeSection = sec },
                    label = { Text(sec.label) },
                    modifier = Modifier.testTag("admin_section_${sec.name.lowercase()}")
                )
            }
        }
        DlHairlineDivider()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("admin_dashboard_list"),
            contentPadding = PaddingValues(DlSpacing.base),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeSection) {
                AdminDashboardSection.OVERVIEW -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AdminMetricCard(stringResource(R.string.admin_metric_users), users.size.toString(), Modifier.weight(1f))
                                AdminMetricCard(stringResource(R.string.admin_metric_online), onlineCount.toString(), Modifier.weight(1f))
                                AdminMetricCard(stringResource(R.string.admin_metric_chats), chats.size.toString(), Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AdminMetricCard(stringResource(R.string.admin_metric_reports), openReports.toString(), Modifier.weight(1f))
                                AdminMetricCard("Restricted", activeRestrictionsCount.toString(), Modifier.weight(1f))
                                AdminMetricCard("Appeals / Tickets", "$openAppealsCount / $openTicketsCount", Modifier.weight(1f))
                            }
                        }
                    }

                    item { DlSectionHeader(title = stringResource(R.string.admin_reports_header, reports.size)) }
                    if (reports.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.admin_reports_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    } else {
                        items(reports.take(5), key = { it.id }) { report ->
                            AdminReportCard(
                                report = report,
                                onUpdateReport = onUpdateReport,
                                onOpenCase = {
                                    onOpenCaseFromReport(report)
                                    activeSection = AdminDashboardSection.MODERATION_CASES
                                },
                                onEnforceTarget = {
                                    restrictionTargetUid = report.targetUid
                                    activeSection = AdminDashboardSection.RESTRICTIONS_WARNINGS
                                }
                            )
                        }
                    }

                    item { DlSectionHeader(title = stringResource(R.string.admin_users_header, users.size)) }
                    items(users.take(6), key = { it.userId }) { user ->
                        AdminUserRowCard(
                            user = user,
                            enforcement = enforcementByUid[user.userId],
                            onToggleSuspend = onToggleSuspend,
                            onManageRestriction = {
                                restrictionTargetUid = user.userId
                                activeSection = AdminDashboardSection.RESTRICTIONS_WARNINGS
                            }
                        )
                    }
                }

                AdminDashboardSection.USERS -> {
                    item {
                        DlSearchField(
                            value = userSearch,
                            onValueChange = { userSearch = it },
                            placeholder = "Search users by name, @handle, or ID",
                            modifier = Modifier.testTag("admin_user_search_input")
                        )
                    }
                    items(filteredUsers, key = { it.userId }) { user ->
                        AdminUserRowCard(
                            user = user,
                            enforcement = enforcementByUid[user.userId],
                            onToggleSuspend = onToggleSuspend,
                            onManageRestriction = {
                                restrictionTargetUid = user.userId
                                activeSection = AdminDashboardSection.RESTRICTIONS_WARNINGS
                            }
                        )
                    }
                }

                AdminDashboardSection.REPORTS -> {
                    if (reports.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.admin_reports_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    } else {
                        items(reports, key = { it.id }) { report ->
                            AdminReportCard(
                                report = report,
                                onUpdateReport = onUpdateReport,
                                onOpenCase = {
                                    onOpenCaseFromReport(report)
                                    activeSection = AdminDashboardSection.MODERATION_CASES
                                },
                                onEnforceTarget = {
                                    restrictionTargetUid = report.targetUid
                                    activeSection = AdminDashboardSection.RESTRICTIONS_WARNINGS
                                }
                            )
                        }
                    }
                }

                AdminDashboardSection.MODERATION_CASES -> {
                    if (moderationCases.isEmpty()) {
                        item {
                            Text("No moderation cases opened yet. Escalate a report to create a case.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        }
                    } else {
                        items(moderationCases, key = { it.id }) { modCase ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = tokens.surfaceElevated,
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${modCase.category} · ${modCase.targetUid.take(8)}", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                                        DlStatusPill(text = modCase.priority, color = if (modCase.priority == "HIGH") DlCrimson else tokens.statusAway)
                                    }
                                    if (modCase.aiTriageSummary.isNotBlank()) {
                                        Text(modCase.aiTriageSummary, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                    }
                                    if (modCase.recommendedAction.isNotBlank()) {
                                        Text("Recommended: ${modCase.recommendedAction}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    OutlinedButton(onClick = {
                                        restrictionTargetUid = modCase.targetUid
                                        activeSection = AdminDashboardSection.RESTRICTIONS_WARNINGS
                                    }) { Text("Inspect & Enforce Target") }
                                }
                            }
                        }
                    }
                    if (moderationActions.isNotEmpty()) {
                        item { DlSectionHeader(title = "Recent Enforcement Actions (${moderationActions.size})") }
                        items(moderationActions.take(10), key = { it.id }) { act ->
                            Text("${act.actionType} on ${act.targetUid.take(8)} by ${act.actorRole}: ${act.notes}", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                        }
                    }
                }

                AdminDashboardSection.RESTRICTIONS_WARNINGS -> {
                    adminRestrictionsWarningsItems(
                        users = users,
                        enforcements = enforcements,
                        staffRole = staffRole,
                        preselectedTargetUid = restrictionTargetUid,
                        state = enforcementFormState,
                        onSelectTargetUid = { restrictionTargetUid = it },
                        onApplyEnforcement = onApplyEnforcement,
                        onIssueWarning = onIssueWarning
                    )
                }

                AdminDashboardSection.APPEALS_REVIEW -> {
                    item {
                        AdminAppealsSection(
                            currentStaffUid = currentStaffUid,
                            staffRole = staffRole,
                            appeals = appeals,
                            onReviewAppeal = onReviewAppeal
                        )
                    }
                }

                AdminDashboardSection.SUPPORT_TICKETS -> {
                    item {
                        AdminSupportTicketsSection(
                            currentStaffUid = currentStaffUid,
                            currentStaffName = currentStaffName,
                            tickets = tickets,
                            activeTicketId = activeTicketId,
                            activeMessages = activeTicketMessages,
                            activeInternalNotes = activeTicketInternalNotes,
                            whatsappConfig = whatsappConfig,
                            onSelectTicket = onSelectTicket,
                            onSendStaffReply = onSendStaffReply,
                            onAddInternalNote = onAddInternalNote,
                            onUpdateTicketMetadata = onUpdateTicketMetadata
                        )
                    }
                }

                AdminDashboardSection.SUPPORT_AGENTS_RBAC -> {
                    item {
                        AdminRbacAndHelpSection(
                            isRbacMode = true,
                            users = users,
                            staffRoles = staffRoles,
                            supportAgents = supportAgents,
                            helpArticles = helpArticles,
                            actorRole = staffRole,
                            onAssignStaffRole = onAssignStaffRole,
                            onUpsertSupportAgent = onUpsertSupportAgent,
                            onUpsertHelpArticle = onUpsertHelpArticle
                        )
                    }
                }

                AdminDashboardSection.HELP_CENTER_CMS -> {
                    item {
                        AdminRbacAndHelpSection(
                            isRbacMode = false,
                            users = users,
                            staffRoles = staffRoles,
                            supportAgents = supportAgents,
                            helpArticles = helpArticles,
                            actorRole = staffRole,
                            onAssignStaffRole = onAssignStaffRole,
                            onUpsertSupportAgent = onUpsertSupportAgent,
                            onUpsertHelpArticle = onUpsertHelpArticle
                        )
                    }
                }

                AdminDashboardSection.PHONE_AUTH_HEALTH,
                AdminDashboardSection.WHATSAPP_BUSINESS,
                AdminDashboardSection.TEXT_LK_GATEWAY,
                AdminDashboardSection.SECURITY_EVENTS,
                AdminDashboardSection.AUDIT_LOGS,
                AdminDashboardSection.PLATFORM_SETTINGS -> {
                    item {
                        AdminInfraHealthSections(
                            section = activeSection,
                            phoneProfiles = phoneProfiles,
                            whatsappConfig = whatsappConfig,
                            whatsappEvents = whatsappEvents,
                            securityEvents = securityEvents,
                            auditLogs = auditLogs,
                            platformSettings = platformSettings,
                            actorRole = staffRole,
                            onRefreshWhatsAppHealth = onRefreshWhatsAppHealth,
                            onSavePlatformSettings = onSavePlatformSettings
                        )
                    }
                }
            }
        }
    }

    PermanentEnforcementConfirmDialog(
        effectiveTargetUid = effectiveRestrictionTargetUid,
        state = enforcementFormState,
        onApplyEnforcement = onApplyEnforcement
    )
}

@Composable
private fun AdminReportCard(
    report: ModerationReport,
    onUpdateReport: (String, String) -> Unit,
    onOpenCase: () -> Unit,
    onEnforceTarget: () -> Unit
) {
    val tokens = LocalDlTokens.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tokens.surfaceElevated,
        border = BorderStroke(1.dp, tokens.hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${ReportCategory.fromRaw(report.category).label} · ${report.targetType}",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary
                )
                DlStatusPill(
                    text = report.status,
                    color = if (report.status == "OPEN") tokens.statusAway else tokens.statusOnline
                )
            }
            Text(report.reason, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
            if (!report.evidenceSnippet.isNullOrBlank()) {
                Text(
                    text = "Evidence: \"${report.evidenceSnippet}\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textTertiary
                )
            }
            if (!report.triageLabel.isNullOrBlank()) {
                Text(
                    text = "Risk ${report.severityScore ?: 0}/100 · ${report.triageLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (report.status == "OPEN") {
                    Button(onClick = { onUpdateReport(report.id, "REVIEWED") }) {
                        Text(stringResource(R.string.admin_mark_reviewed))
                    }
                    OutlinedButton(onClick = { onUpdateReport(report.id, "DISMISSED") }) {
                        Text(stringResource(R.string.admin_dismiss))
                    }
                }
                OutlinedButton(onClick = onOpenCase) { Text("Open Case") }
                TextButton(onClick = onEnforceTarget) { Text("Restrict Target") }
            }
        }
    }
}

@Composable
private fun AdminUserRowCard(
    user: UserProfile,
    enforcement: UserEnforcement?,
    onToggleSuspend: (String, Boolean) -> Unit,
    onManageRestriction: () -> Unit
) {
    val tokens = LocalDlTokens.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tokens.hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DlSpacing.base, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DlAvatar(name = user.displayName, colorHex = user.avatarColor, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.displayName, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                val enfStatus = enforcement?.let { DlAccountStatus.fromRaw(it.accountStatus) }
                val statusLabel = when {
                    user.suspended || enforcement?.isGloballySuspended() == true ->
                        stringResource(R.string.admin_status_suspended)
                    enfStatus != null && enfStatus != DlAccountStatus.ACTIVE -> enfStatus.label
                    else -> OnlineStatus.fromRaw(user.onlineStatus).localizedLabel()
                }
                Text(
                    "@${user.username} · $statusLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (user.suspended || enforcement?.isGloballySuspended() == true) DlCrimson else tokens.statusOnline
                )
            }
            TextButton(onClick = onManageRestriction) {
                Text("Enforce")
            }
            TextButton(onClick = { onToggleSuspend(user.userId, !user.suspended) }) {
                Text(
                    stringResource(
                        if (user.suspended) R.string.admin_restore_btn else R.string.admin_suspend_btn
                    ),
                    color = if (user.suspended) tokens.statusOnline else tokens.statusBusy
                )
            }
        }
    }
}

@Composable
private fun AdminMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    val tokens = LocalDlTokens.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = tokens.surfaceElevated,
        border = BorderStroke(1.dp, tokens.hairline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
        }
    }
}
