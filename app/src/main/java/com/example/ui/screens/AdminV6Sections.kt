package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.PhoneNumberHelper
import com.example.data.model.AdminAuditLog
import com.example.data.model.AppealRecord
import com.example.data.model.AppealStatus
import com.example.data.model.AuthSecurityEvent
import com.example.data.model.DlAccountStatus
import com.example.data.model.DlFeatureRestriction
import com.example.data.model.DlRestrictionDuration
import com.example.data.model.DlStaffRole
import com.example.data.model.HelpArticle
import com.example.data.model.ModerationActionRecord
import com.example.data.model.ModerationCase
import com.example.data.model.ModerationReport
import com.example.data.model.PhoneAuthProfile
import com.example.data.model.PlatformRuntimeSettings
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
import com.example.ui.components.DlFocalCard
import com.example.ui.components.DlPrimaryButton
import com.example.ui.components.DlSecondaryButton
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.components.formatRelativeTimeLocalized
import com.example.ui.theme.DlCrimson
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDlTokens

enum class AdminDashboardSection(val label: String) {
    OVERVIEW("Overview"),
    USERS("Users"),
    REPORTS("Reports"),
    MODERATION_CASES("Moderation Cases"),
    RESTRICTIONS_WARNINGS("Restrictions & Warnings"),
    APPEALS_REVIEW("Appeals Review"),
    SUPPORT_TICKETS("Support Tickets"),
    SUPPORT_AGENTS_RBAC("Support Agents & RBAC"),
    HELP_CENTER_CMS("Help Center CMS"),
    PHONE_AUTH_HEALTH("Phone Auth Health"),
    WHATSAPP_BUSINESS("WhatsApp Business"),
    TEXT_LK_GATEWAY("Text.lk SMS Gateway"),
    SECURITY_EVENTS("Security Events"),
    AUDIT_LOGS("Audit Logs"),
    PLATFORM_SETTINGS("Platform Settings")
}

class AdminEnforcementFormState(
    initialStatus: DlAccountStatus,
    initialFeatures: List<DlFeatureRestriction>
) {
    var selectedStatus by mutableStateOf(initialStatus)
    val selectedFeatures = mutableStateListOf<DlFeatureRestriction>().apply { addAll(initialFeatures) }
    var selectedDuration by mutableStateOf(DlRestrictionDuration.H24)
    var customHoursText by mutableStateOf("48")
    var reasonCode by mutableStateOf("POLICY_ABUSE")
    var reasonSummary by mutableStateOf("")
    var showPermanentConfirmDialog by mutableStateOf(false)
    var permanentConfirmChecked by mutableStateOf(false)
    var warnTitle by mutableStateOf("")
    var warnDesc by mutableStateOf("")
    var warnSeverity by mutableStateOf("MEDIUM")
}

@Composable
fun rememberAdminEnforcementFormState(
    targetUid: String,
    currentEnforcement: UserEnforcement?
): AdminEnforcementFormState = remember(targetUid) {
    AdminEnforcementFormState(
        initialStatus = DlAccountStatus.fromRaw(currentEnforcement?.accountStatus),
        initialFeatures = currentEnforcement?.activeBlockedFeatures().orEmpty()
    )
}

fun LazyListScope.adminRestrictionsWarningsItems(
    users: List<UserProfile>,
    enforcements: List<UserEnforcement>,
    staffRole: DlStaffRole,
    preselectedTargetUid: String,
    state: AdminEnforcementFormState,
    onSelectTargetUid: (String) -> Unit,
    onApplyEnforcement: (
        targetUid: String,
        targetStatus: DlAccountStatus,
        selectedFeatures: Set<DlFeatureRestriction>,
        duration: DlRestrictionDuration,
        customHours: Int,
        reasonCode: String,
        reasonSummary: String,
        humanApprovedPermanent: Boolean
    ) -> Unit,
    onIssueWarning: (
        targetUid: String,
        reasonCode: String,
        title: String,
        description: String,
        severity: String
    ) -> Unit
) {
    val targetUser = users.firstOrNull { it.userId == preselectedTargetUid } ?: users.firstOrNull()
    val effectiveTargetUid = targetUser?.userId.orEmpty()
    val currentEnforcement = enforcements.firstOrNull { it.userId == effectiveTargetUid }

    item(key = "enf_target_user") {
        val tokens = LocalDlTokens.current
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Target User", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                users.forEach { u ->
                    FilterChip(
                        selected = u.userId == effectiveTargetUid,
                        onClick = { onSelectTargetUid(u.userId) },
                        label = { Text("@${u.username.ifBlank { u.displayName }}") }
                    )
                }
            }

            if (targetUser != null) {
                DlFocalCard(accentBorder = MaterialTheme.colorScheme.primary) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DlAvatar(name = targetUser.displayName, colorHex = targetUser.avatarColor, size = 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(targetUser.displayName, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Text("@${targetUser.username} · ${targetUser.userId.take(8)}", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                        }
                        DlStatusPill(
                            text = DlAccountStatus.fromRaw(currentEnforcement?.accountStatus).label,
                            color = if (currentEnforcement?.isGloballySuspended() == true) DlCrimson else tokens.statusAway
                        )
                    }
                }
            }
        }
    }

    item(key = "enf_status_and_features") {
        val tokens = LocalDlTokens.current
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tokens.surfaceElevated,
            border = BorderStroke(1.dp, tokens.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Account Enforcement Status", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DlAccountStatus.entries.forEach { st ->
                        val allowed = !st.isSevereSuspension || st == DlAccountStatus.TEMP_SUSPENDED || staffRole.canPermanentBan
                        FilterChip(
                            selected = state.selectedStatus == st,
                            enabled = allowed,
                            onClick = { state.selectedStatus = st },
                            label = { Text(st.label) },
                            modifier = Modifier.testTag("enforcement_status_${st.name.lowercase()}")
                        )
                    }
                }

                Text("14 Feature Restrictions", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                DlFeatureRestriction.entries.chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { feat ->
                            val checked = feat in state.selectedFeatures
                            FilterChip(
                                selected = checked,
                                onClick = {
                                    if (checked) state.selectedFeatures.remove(feat) else state.selectedFeatures.add(feat)
                                },
                                label = { Text(feat.label, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    item(key = "enf_duration_and_apply") {
        val tokens = LocalDlTokens.current
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tokens.surfaceElevated,
            border = BorderStroke(1.dp, tokens.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Duration", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DlRestrictionDuration.entries.forEach { dur ->
                        val isPermDur = dur == DlRestrictionDuration.PERMANENT || dur == DlRestrictionDuration.TERMINATION
                        FilterChip(
                            selected = state.selectedDuration == dur,
                            enabled = !isPermDur || staffRole.canPermanentBan,
                            onClick = { state.selectedDuration = dur },
                            label = { Text(dur.label) }
                        )
                    }
                }

                if (state.selectedDuration == DlRestrictionDuration.CUSTOM) {
                    DlTextField(
                        value = state.customHoursText,
                        onValueChange = { state.customHoursText = it.filter(Char::isDigit).take(4) },
                        label = "Custom duration (hours)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                DlTextField(value = state.reasonCode, onValueChange = { state.reasonCode = it }, label = "Reason Code")
                DlTextField(
                    value = state.reasonSummary,
                    onValueChange = { state.reasonSummary = it },
                    label = "User-Facing Summary & Evidence Note",
                    singleLine = false,
                    maxLines = 3
                )

                val needsHumanConfirm = state.selectedStatus == DlAccountStatus.PERM_SUSPENDED ||
                    state.selectedStatus == DlAccountStatus.DELETION_PENDING ||
                    state.selectedDuration == DlRestrictionDuration.PERMANENT ||
                    state.selectedDuration == DlRestrictionDuration.TERMINATION

                DlPrimaryButton(
                    text = if (needsHumanConfirm) "Review & Confirm Permanent Action" else "Apply Enforcement",
                    enabled = effectiveTargetUid.isNotBlank() && staffRole.canModerateUsers,
                    onClick = {
                        if (needsHumanConfirm) {
                            state.permanentConfirmChecked = false
                            state.showPermanentConfirmDialog = true
                        } else {
                            onApplyEnforcement(
                                effectiveTargetUid,
                                state.selectedStatus,
                                state.selectedFeatures.toSet(),
                                state.selectedDuration,
                                state.customHoursText.toIntOrNull() ?: 24,
                                state.reasonCode.ifBlank { "POLICY_ABUSE" },
                                state.reasonSummary.trim(),
                                true
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_apply_enforcement_button")
                )
            }
        }
    }

    item(key = "enf_formal_warning") {
        val tokens = LocalDlTokens.current
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tokens.surfaceElevated,
            border = BorderStroke(1.dp, tokens.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Issue Formal Warning", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LOW", "MEDIUM", "HIGH").forEach { sev ->
                        FilterChip(
                            selected = state.warnSeverity == sev,
                            onClick = { state.warnSeverity = sev },
                            label = { Text(sev) }
                        )
                    }
                }
                DlTextField(value = state.warnTitle, onValueChange = { state.warnTitle = it }, label = "Warning Title")
                DlTextField(
                    value = state.warnDesc,
                    onValueChange = { state.warnDesc = it },
                    label = "Warning Description",
                    singleLine = false,
                    maxLines = 3
                )
                DlSecondaryButton(
                    text = "Send Official Warning",
                    icon = Icons.Filled.Warning,
                    enabled = effectiveTargetUid.isNotBlank() && state.warnTitle.isNotBlank() && staffRole.canModerateUsers,
                    onClick = {
                        onIssueWarning(
                            effectiveTargetUid,
                            state.reasonCode.ifBlank { "COMMUNITY_GUIDELINE" },
                            state.warnTitle.trim(),
                            state.warnDesc.trim(),
                            state.warnSeverity
                        )
                        state.warnTitle = ""
                        state.warnDesc = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PermanentEnforcementConfirmDialog(
    effectiveTargetUid: String,
    state: AdminEnforcementFormState,
    onApplyEnforcement: (
        targetUid: String,
        targetStatus: DlAccountStatus,
        selectedFeatures: Set<DlFeatureRestriction>,
        duration: DlRestrictionDuration,
        customHours: Int,
        reasonCode: String,
        reasonSummary: String,
        humanApprovedPermanent: Boolean
    ) -> Unit
) {
    val tokens = LocalDlTokens.current
    if (state.showPermanentConfirmDialog) {
        AlertDialog(
            onDismissRequest = { state.showPermanentConfirmDialog = false },
            containerColor = tokens.surfaceElevated,
            title = { Text("Confirm Permanent Enforcement", color = DlCrimson) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Permanent suspension and account termination cannot proceed automatically from automated scoring. Confirm a human Senior Moderator or Admin has inspected the evidence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.permanentConfirmChecked = !state.permanentConfirmChecked },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.permanentConfirmChecked,
                            onCheckedChange = { state.permanentConfirmChecked = it },
                            modifier = Modifier.testTag("confirm_permanent_checkbox")
                        )
                        Text("I confirm human review is complete", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = state.permanentConfirmChecked,
                    onClick = {
                        state.showPermanentConfirmDialog = false
                        onApplyEnforcement(
                            effectiveTargetUid,
                            state.selectedStatus,
                            state.selectedFeatures.toSet(),
                            state.selectedDuration,
                            state.customHoursText.toIntOrNull() ?: 24,
                            state.reasonCode.ifBlank { "SEVERE_ABUSE" },
                            state.reasonSummary.trim(),
                            true
                        )
                    },
                    modifier = Modifier.testTag("confirm_permanent_action_button")
                ) { Text("Confirm Permanent Action", color = DlCrimson) }
            },
            dismissButton = {
                TextButton(onClick = { state.showPermanentConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AdminAppealsSection(
    currentStaffUid: String,
    staffRole: DlStaffRole,
    appeals: List<AppealRecord>,
    onReviewAppeal: (AppealRecord, AppealStatus, String) -> Unit
) {
    val tokens = LocalDlTokens.current
    var reviewing by remember { mutableStateOf<Pair<AppealRecord, AppealStatus>?>(null) }
    var decisionNote by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (appeals.isEmpty()) {
            Text("No user appeals in queue.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        } else {
            appeals.forEach { appeal ->
                val st = AppealStatus.fromRaw(appeal.status)
                val sameEnforcerBlocked = appeal.originalEnforcerUid.isNotBlank() &&
                    appeal.originalEnforcerUid == currentStaffUid &&
                    staffRole.rank < DlStaffRole.ADMIN.rank
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
                            Text("User ${appeal.userId.take(8)} · Attempt #${appeal.attemptNumber}/3", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            DlStatusPill(text = st.label, color = if (st == AppealStatus.APPROVED) tokens.statusOnline else tokens.statusAway)
                        }
                        Text(appeal.reason, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        if (appeal.additionalInfo.isNotBlank()) {
                            Text("Additional info: ${appeal.additionalInfo}", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                        }
                        if (sameEnforcerBlocked) {
                            Text(
                                "Distinct reviewer rule: You issued the original restriction, so another moderator must decide this appeal.",
                                style = MaterialTheme.typography.labelSmall,
                                color = DlCrimson
                            )
                        } else if (staffRole.canModerateUsers && st != AppealStatus.APPROVED && st != AppealStatus.REJECTED) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(onClick = { reviewing = appeal to AppealStatus.UNDER_REVIEW; decisionNote = "" }) {
                                    Text("Mark Reviewing")
                                }
                                OutlinedButton(onClick = { reviewing = appeal to AppealStatus.INFO_REQUESTED; decisionNote = "" }) {
                                    Text("Request Info")
                                }
                                Button(onClick = { reviewing = appeal to AppealStatus.APPROVED; decisionNote = "" }) {
                                    Text("Approve & Lift")
                                }
                                OutlinedButton(onClick = { reviewing = appeal to AppealStatus.REJECTED; decisionNote = "" }) {
                                    Text("Reject", color = DlCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingReview = reviewing
    if (pendingReview != null) {
        AlertDialog(
            onDismissRequest = { reviewing = null },
            containerColor = tokens.surfaceElevated,
            title = { Text("Decision: ${pendingReview.second.label}", color = tokens.textPrimary) },
            text = {
                DlTextField(
                    value = decisionNote,
                    onValueChange = { decisionNote = it },
                    label = "Decision explanation for user & audit log",
                    singleLine = false,
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onReviewAppeal(pendingReview.first, pendingReview.second, decisionNote.trim())
                    reviewing = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { reviewing = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AdminSupportTicketsSection(
    currentStaffUid: String,
    currentStaffName: String,
    tickets: List<SupportTicket>,
    activeTicketId: String?,
    activeMessages: List<SupportTicketMessage>,
    activeInternalNotes: List<SupportInternalNote>,
    whatsappConfig: WhatsAppConfiguration?,
    onSelectTicket: (String?) -> Unit,
    onSendStaffReply: (ticket: SupportTicket, body: String, notifyWhatsApp: Boolean) -> Unit,
    onAddInternalNote: (ticketId: String, note: String) -> Unit,
    onUpdateTicketMetadata: (
        ticketId: String,
        status: SupportTicketStatus?,
        priority: SupportPriority?,
        assignedUid: String?,
        assignedName: String?
    ) -> Unit
) {
    val tokens = LocalDlTokens.current
    val selected = tickets.firstOrNull { it.id == activeTicketId }

    if (selected == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tickets.isEmpty()) {
                Text("No support tickets yet.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            } else {
                tickets.forEach { ticket ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.surfaceElevated,
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectTicket(ticket.id) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(ticket.subject, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                                DlStatusPill(text = SupportTicketStatus.fromRaw(ticket.status).label, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("${ticket.userName} · ${SupportCategory.fromRaw(ticket.category).label}", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                            Text(ticket.lastMessageText, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 2)
                        }
                    }
                }
            }
        }
    } else {
        var replyDraft by remember(selected.id) { mutableStateOf("") }
        var notifyWhatsApp by remember(selected.id) { mutableStateOf(false) }
        var internalNoteDraft by remember(selected.id) { mutableStateOf("") }
        val waReady = whatsappConfig?.enabled == true && selected.whatsappOptIn

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSelectTicket(null) }) { Text("← Back to tickets") }
                OutlinedButton(onClick = {
                    onUpdateTicketMetadata(selected.id, SupportTicketStatus.ASSIGNED, null, currentStaffUid, currentStaffName)
                }) { Text("Assign to me") }
            }

            Text(selected.subject, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SupportTicketStatus.entries.forEach { st ->
                    FilterChip(
                        selected = selected.status == st.name,
                        onClick = { onUpdateTicketMetadata(selected.id, st, null, null, null) },
                        label = { Text(st.label) }
                    )
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SupportPriority.entries.forEach { pr ->
                    FilterChip(
                        selected = selected.priority == pr.name,
                        onClick = { onUpdateTicketMetadata(selected.id, null, pr, null, null) },
                        label = { Text(pr.label) }
                    )
                }
            }

            activeMessages.forEach { m ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (m.senderRole == "SUPPORT_AGENT") MaterialTheme.colorScheme.primaryContainer else tokens.surfaceCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${m.senderName} (${m.senderRole})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(m.body, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                    }
                }
            }

            DlTextField(
                value = replyDraft,
                onValueChange = { replyDraft = it },
                label = "Reply to user",
                singleLine = false,
                maxLines = 3
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = notifyWhatsApp, enabled = waReady, onCheckedChange = { notifyWhatsApp = it })
                Text(
                    text = if (waReady) "Also notify via WhatsApp Business" else "WhatsApp notify unavailable (user opt-in & Cloud API required)",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
            DlPrimaryButton(
                text = "Send Official Support Reply",
                enabled = replyDraft.isNotBlank(),
                onClick = {
                    onSendStaffReply(selected, replyDraft.trim(), notifyWhatsApp)
                    replyDraft = ""
                },
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tokens.statusAway.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, tokens.statusAway.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Staff-Only Internal Notes (never visible to users)", style = MaterialTheme.typography.titleSmall, color = tokens.statusAway)
                    activeInternalNotes.forEach { note ->
                        Text("${note.authorName}: ${note.note}", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                    }
                    DlTextField(
                        value = internalNoteDraft,
                        onValueChange = { internalNoteDraft = it },
                        label = "Add internal triage note",
                        modifier = Modifier.testTag("admin_internal_note_input")
                    )
                    OutlinedButton(
                        enabled = internalNoteDraft.isNotBlank(),
                        onClick = {
                            onAddInternalNote(selected.id, internalNoteDraft.trim())
                            internalNoteDraft = ""
                        }
                    ) { Text("Save Internal Note") }
                }
            }
        }
    }
}

@Composable
fun AdminRbacAndHelpSection(
    isRbacMode: Boolean,
    users: List<UserProfile>,
    staffRoles: List<StaffRoleRecord>,
    supportAgents: List<SupportAgentRecord>,
    helpArticles: List<HelpArticle>,
    actorRole: DlStaffRole,
    onAssignStaffRole: (targetUid: String, displayName: String, email: String, role: DlStaffRole) -> Unit,
    onUpsertSupportAgent: (userId: String, displayName: String, specialty: String, availability: String) -> Unit,
    onUpsertHelpArticle: (articleId: String?, category: SupportCategory, title: String, summary: String, body: String, published: Boolean) -> Unit
) {
    val tokens = LocalDlTokens.current
    if (isRbacMode) {
        var selectedUser by remember { mutableStateOf(users.firstOrNull()) }
        var chosenRole by remember { mutableStateOf(DlStaffRole.SUPPORT_AGENT) }
        var specialty by remember { mutableStateOf("Trust & Account Recovery") }
        var availability by remember { mutableStateOf("ONLINE") }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Assign Staff Role & Support Roster", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                users.forEach { u ->
                    FilterChip(
                        selected = selectedUser?.userId == u.userId,
                        onClick = { selectedUser = u },
                        label = { Text("@${u.username.ifBlank { u.displayName }}") }
                    )
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DlStaffRole.entries.forEach { role ->
                    FilterChip(selected = chosenRole == role, onClick = { chosenRole = role }, label = { Text(role.label) })
                }
            }
            DlPrimaryButton(
                text = "Assign Role",
                enabled = actorRole.canManageRolesAndPlatform && selectedUser != null,
                onClick = {
                    selectedUser?.let { u ->
                        onAssignStaffRole(u.userId, u.displayName, "", chosenRole)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            DlTextField(value = specialty, onValueChange = { specialty = it }, label = "Support Specialty")
            DlSecondaryButton(
                text = "Register / Update Support Agent",
                icon = Icons.Filled.SupportAgent,
                enabled = actorRole.canManageRolesAndPlatform && selectedUser != null,
                onClick = {
                    selectedUser?.let { u -> onUpsertSupportAgent(u.userId, u.displayName, specialty, availability) }
                },
                modifier = Modifier.fillMaxWidth()
            )
            staffRoles.forEach { rec ->
                Surface(shape = RoundedCornerShape(12.dp), color = tokens.surfaceElevated, border = BorderStroke(1.dp, tokens.hairline), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rec.displayName.ifBlank { rec.userId.take(8) }, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                        DlStatusPill(text = DlStaffRole.fromRaw(rec.role).label, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            supportAgents.forEach { ag ->
                Text("Agent: ${ag.displayName} · ${ag.specialty} (${ag.availabilityStatus})", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
        }
    } else {
        var editingArticle by remember { mutableStateOf<HelpArticle?>(null) }
        var showEditor by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DlPrimaryButton(
                text = "Add Help Center Article",
                icon = Icons.Filled.Add,
                onClick = { editingArticle = null; showEditor = true },
                modifier = Modifier.fillMaxWidth()
            )
            helpArticles.forEach { art ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.surfaceElevated,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.fillMaxWidth().clickable { editingArticle = art; showEditor = true }
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(art.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                            DlStatusPill(text = if (art.published) "PUBLISHED" else "DRAFT", color = if (art.published) tokens.statusOnline else tokens.statusAway)
                        }
                        Text(art.summary, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    }
                }
            }
        }

        if (showEditor) {
            var cat by remember(editingArticle) { mutableStateOf(SupportCategory.fromRaw(editingArticle?.category)) }
            var title by remember(editingArticle) { mutableStateOf(editingArticle?.title.orEmpty()) }
            var summary by remember(editingArticle) { mutableStateOf(editingArticle?.summary.orEmpty()) }
            var body by remember(editingArticle) { mutableStateOf(editingArticle?.bodyMarkdown.orEmpty()) }
            var published by remember(editingArticle) { mutableStateOf(editingArticle?.published ?: true) }

            AlertDialog(
                onDismissRequest = { showEditor = false },
                containerColor = tokens.surfaceElevated,
                title = { Text(if (editingArticle == null) "New Help Article" else "Edit Help Article", color = tokens.textPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SupportCategory.entries.forEach { c ->
                                FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c.label) })
                            }
                        }
                        DlTextField(value = title, onValueChange = { title = it }, label = "Title")
                        DlTextField(value = summary, onValueChange = { summary = it }, label = "Summary")
                        DlTextField(value = body, onValueChange = { body = it }, label = "Body", singleLine = false, maxLines = 5)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = published, onCheckedChange = { published = it })
                            Spacer(Modifier.width(8.dp))
                            Text("Published", color = tokens.textPrimary)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (title.isNotBlank() && body.isNotBlank()) {
                            onUpsertHelpArticle(editingArticle?.id?.takeIf { !it.startsWith("help_") }, cat, title.trim(), summary.trim(), body.trim(), published)
                            showEditor = false
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showEditor = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
fun AdminInfraHealthSections(
    section: AdminDashboardSection,
    phoneProfiles: List<PhoneAuthProfile>,
    whatsappConfig: WhatsAppConfiguration?,
    whatsappEvents: List<WhatsAppMessageEvent>,
    securityEvents: List<AuthSecurityEvent>,
    auditLogs: List<AdminAuditLog>,
    platformSettings: PlatformRuntimeSettings,
    actorRole: DlStaffRole,
    onRefreshWhatsAppHealth: () -> Unit,
    onSavePlatformSettings: (PlatformRuntimeSettings) -> Unit
) {
    val tokens = LocalDlTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (section) {
            AdminDashboardSection.PHONE_AUTH_HEALTH -> {
                val lkCount = phoneProfiles.count { it.countryCode.equals("LK", ignoreCase = true) || it.phoneE164.startsWith("+94") }
                DlFocalCard(accentBorder = tokens.statusOnline) {
                    Text("Phone OTP Provider: Firebase SMS Primary", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                    Text("Verified phone profiles: ${phoneProfiles.size} (Sri Lanka +94: $lkCount)", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                }
                phoneProfiles.forEach { p ->
                    Surface(shape = RoundedCornerShape(12.dp), color = tokens.surfaceElevated, border = BorderStroke(1.dp, tokens.hairline), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(PhoneNumberHelper.maskPhone(p.phoneE164), fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                                Text("${p.countryCode} (${p.countryDialCode}) · ${p.otpProvider}", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                            }
                            DlStatusPill(text = p.verificationStatus, color = tokens.statusOnline)
                        }
                    }
                }
            }

            AdminDashboardSection.WHATSAPP_BUSINESS -> {
                val cfg = whatsappConfig ?: WhatsAppConfiguration(id = "primary")
                DlFocalCard(accentBorder = if (cfg.enabled) tokens.statusOnline else tokens.statusAway) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("WhatsApp Business Cloud API", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                        DlStatusPill(text = cfg.connectionStatus, color = if (cfg.enabled) tokens.statusOnline else tokens.statusAway)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Display Number: ${cfg.displayPhoneNumber.ifBlank { "Not connected yet" }}", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    Text("Webhook Configured: ${cfg.webhookConfigured} · OTP Provider Ready: ${cfg.otpProviderEnabled} · Auth Template Approved: ${cfg.otpTemplateApproved}", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                    if (cfg.lastErrorSummary.isNotBlank()) {
                        Text(cfg.lastErrorSummary, style = MaterialTheme.typography.labelSmall, color = DlCrimson)
                    }
                    Spacer(Modifier.height(8.dp))
                    DlSecondaryButton(
                        text = "Refresh Health",
                        icon = Icons.Filled.Refresh,
                        onClick = onRefreshWhatsAppHealth,
                        modifier = Modifier.fillMaxWidth().testTag("admin_wa_refresh_button")
                    )
                }
                Text("Recent WhatsApp Message Events (${whatsappEvents.size})", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                whatsappEvents.forEach { ev ->
                    Surface(shape = RoundedCornerShape(12.dp), color = tokens.surfaceElevated, border = BorderStroke(1.dp, tokens.hairline), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("${ev.direction} · ${ev.recipientMasked}", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                                if (ev.errorSummary.isNotBlank()) Text(ev.errorSummary, style = MaterialTheme.typography.labelSmall, color = DlCrimson)
                            }
                            DlStatusPill(text = ev.deliveryStatus, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            AdminDashboardSection.TEXT_LK_GATEWAY -> {
                TextLkContactsPanel()
            }

            AdminDashboardSection.SECURITY_EVENTS -> {
                securityEvents.forEach { ev ->
                    Surface(shape = RoundedCornerShape(12.dp), color = tokens.surfaceElevated, border = BorderStroke(1.dp, tokens.hairline), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("${ev.eventType} · ${ev.channel}", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                                Text("${ev.maskedTarget} · ${ev.detail}", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            }
                            DlStatusPill(text = if (ev.success) "OK" else "FAIL", color = if (ev.success) tokens.statusOnline else DlCrimson)
                        }
                    }
                }
            }

            AdminDashboardSection.AUDIT_LOGS -> {
                auditLogs.forEach { log ->
                    Surface(shape = RoundedCornerShape(12.dp), color = tokens.surfaceElevated, border = BorderStroke(1.dp, tokens.hairline), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(log.action, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Text(formatRelativeTimeLocalized(log.createdAt), style = MaterialTheme.typography.labelSmall, color = tokens.textTertiary)
                            }
                            Text(log.summary, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                            Text("Actor: ${log.actorUid.take(8)} (${log.actorRole})", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                        }
                    }
                }
            }

            AdminDashboardSection.PLATFORM_SETTINGS -> {
                var waOtp by remember(platformSettings) { mutableStateOf(platformSettings.whatsappOtpEnabled) }
                var waSupport by remember(platformSettings) { mutableStateOf(platformSettings.whatsappSupportEnabled) }
                var antiSpam by remember(platformSettings) { mutableStateOf(platformSettings.antiSpamEnabled) }
                var maintenance by remember(platformSettings) { mutableStateOf(platformSettings.maintenanceMode) }
                var rateMax by remember(platformSettings) { mutableStateOf(platformSettings.rateLimitMaxPer10s.toString()) }
                var autoThreshold by remember(platformSettings) { mutableStateOf(platformSettings.autoEscalationReportThreshold.toString()) }

                Surface(shape = RoundedCornerShape(14.dp), color = tokens.surfaceElevated, border = BorderStroke(1.dp, tokens.hairline), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingSwitchRow("Anti-Spam Pre-Send Protection", antiSpam) { antiSpam = it }
                        SettingSwitchRow("WhatsApp Business Support Messaging", waSupport) { waSupport = it }
                        SettingSwitchRow("WhatsApp Auth OTP (requires approved template)", waOtp) { waOtp = it }
                        SettingSwitchRow("Maintenance Mode", maintenance) { maintenance = it }
                        DlTextField(value = rateMax, onValueChange = { rateMax = it.filter(Char::isDigit).take(3) }, label = "Max messages per 10 seconds")
                        DlTextField(value = autoThreshold, onValueChange = { autoThreshold = it.filter(Char::isDigit).take(2) }, label = "Reports threshold for priority triage")
                        DlPrimaryButton(
                            text = "Save Platform Settings",
                            enabled = actorRole.canManageRolesAndPlatform,
                            onClick = {
                                onSavePlatformSettings(
                                    platformSettings.copy(
                                        whatsappOtpEnabled = waOtp,
                                        whatsappSupportEnabled = waSupport,
                                        antiSpamEnabled = antiSpam,
                                        maintenanceMode = maintenance,
                                        rateLimitMaxPer10s = rateMax.toIntOrNull() ?: 12,
                                        autoEscalationReportThreshold = autoThreshold.toIntOrNull() ?: 3
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val tokens = LocalDlTokens.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
