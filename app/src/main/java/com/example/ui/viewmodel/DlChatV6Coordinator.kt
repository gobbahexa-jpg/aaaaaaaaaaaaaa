package com.example.ui.viewmodel

import android.util.Log
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
import com.example.data.model.UserRestrictionRecord
import com.example.data.model.UserWarning
import com.example.data.model.WhatsAppConfiguration
import com.example.data.model.WhatsAppMessageEvent
import com.example.data.repository.AntiSpamEngine
import com.example.data.repository.SupportRepository
import com.example.data.repository.TrustSafetyRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DlChatV6Coordinator(
    private val scope: CoroutineScope,
    private val currentUserId: String,
    private val isBootstrapAdmin: Boolean,
    private val trustSafetyRepo: TrustSafetyRepository?,
    private val supportRepo: SupportRepository?,
    private val onBanner: (String) -> Unit
) {
    private fun <T> Flow<T>.safeState(tag: String, initial: T): StateFlow<T> = this
        .catch { err ->
            val ffe = generateSequence(err) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (ffe == null && err is CancellationException) throw err
            Log.w("DlChatV6", "$tag stream error", ffe ?: err)
            emit(initial)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000L), initial)

    val staffRoleState: StateFlow<DlStaffRole> =
        (trustSafetyRepo?.observeStaffRole(currentUserId) ?: flowOf(null))
            .map { rec ->
                val resolved = if (rec != null && rec.active) DlStaffRole.fromRaw(rec.role) else DlStaffRole.USER
                if (isBootstrapAdmin && resolved.rank < DlStaffRole.SUPER_ADMIN.rank) {
                    DlStaffRole.SUPER_ADMIN
                } else {
                    resolved
                }
            }
            .safeState("StaffRole", if (isBootstrapAdmin) DlStaffRole.SUPER_ADMIN else DlStaffRole.USER)

    val myEnforcementState: StateFlow<UserEnforcement?> =
        (trustSafetyRepo?.observeMyEnforcement(currentUserId) ?: flowOf(null))
            .safeState("MyEnforcement", null)

    val myWarningsState: StateFlow<List<UserWarning>> =
        (trustSafetyRepo?.observeMyWarnings(currentUserId) ?: flowOf(emptyList()))
            .safeState("MyWarnings", emptyList())

    val myRestrictionsState: StateFlow<List<UserRestrictionRecord>> =
        (trustSafetyRepo?.observeMyRestrictions(currentUserId) ?: flowOf(emptyList()))
            .safeState("MyRestrictions", emptyList())

    val myAppealsState: StateFlow<List<AppealRecord>> =
        (trustSafetyRepo?.observeMyAppeals(currentUserId, isModerator = false) ?: flowOf(emptyList()))
            .safeState("MyAppeals", emptyList())

    val myTicketsState: StateFlow<List<SupportTicket>> =
        (supportRepo?.observeTickets(currentUserId, isStaff = false) ?: flowOf(emptyList()))
            .safeState("MyTickets", emptyList())

    val helpArticlesState: StateFlow<List<HelpArticle>> =
        combine(
            staffRoleState,
            flowOf(Unit)
        ) { role, _ -> role }
            .flatMapLatest { role ->
                supportRepo?.observeHelpArticles(includeUnpublished = role.canOpenStaffConsole)
                    ?: flowOf(SupportRepository.builtInHelpArticles)
            }
            .safeState("HelpArticles", SupportRepository.builtInHelpArticles)

    val phoneAuthProfileState: StateFlow<PhoneAuthProfile?> =
        (trustSafetyRepo?.observePhoneAuthProfile(currentUserId) ?: flowOf(null))
            .safeState("PhoneAuthProfile", null)

    val mySecurityEventsState: StateFlow<List<AuthSecurityEvent>> =
        (trustSafetyRepo?.observeAuthSecurityEvents(currentUserId, isStaff = false) ?: flowOf(emptyList()))
            .safeState("MyAuthEvents", emptyList())

    val platformSettingsState: StateFlow<PlatformRuntimeSettings> =
        (trustSafetyRepo?.observePlatformSettings() ?: flowOf(PlatformRuntimeSettings(id = "runtime")))
            .safeState("PlatformSettings", PlatformRuntimeSettings(id = "runtime"))

    // --- Staff-gated streams ---

    val allEnforcementsState: StateFlow<List<UserEnforcement>> =
        staffRoleState.flatMapLatest { role ->
            if (role.canModerateUsers && trustSafetyRepo != null) trustSafetyRepo.observeAllEnforcements()
            else flowOf(emptyList())
        }.safeState("AllEnforcements", emptyList())

    val moderationCasesState: StateFlow<List<ModerationCase>> =
        staffRoleState.flatMapLatest { role ->
            if (role.canModerateUsers && trustSafetyRepo != null) trustSafetyRepo.observeModerationCases()
            else flowOf(emptyList())
        }.safeState("ModCases", emptyList())

    val moderationActionsState: StateFlow<List<ModerationActionRecord>> =
        staffRoleState.flatMapLatest { role ->
            if (role.canModerateUsers && trustSafetyRepo != null) trustSafetyRepo.observeModerationActions()
            else flowOf(emptyList())
        }.safeState("ModActions", emptyList())

    val allAppealsState: StateFlow<List<AppealRecord>> =
        staffRoleState.flatMapLatest { role ->
            if (role.canModerateUsers && trustSafetyRepo != null) {
                trustSafetyRepo.observeMyAppeals(currentUserId, isModerator = true)
            } else flowOf(emptyList())
        }.safeState("AllAppeals", emptyList())

    val allTicketsState: StateFlow<List<SupportTicket>> =
        staffRoleState.flatMapLatest { role ->
            if (role.canOpenStaffConsole && supportRepo != null) {
                supportRepo.observeTickets(currentUserId, isStaff = true)
            } else flowOf(emptyList())
        }.safeState("AllTickets", emptyList())

    val allStaffRolesState: StateFlow<List<StaffRoleRecord>> =
        staffRoleState.flatMapLatest { role ->
            if (role.canManageRolesAndPlatform && trustSafetyRepo != null) {
                trustSafetyRepo.observeAllStaffRoles()
            } else flowOf(emptyList())
        }.safeState("AllStaffRoles", emptyList())

    val auditLogsState: StateFlow<List<AdminAuditLog>> =
        staffRoleState.flatMapLatest { role ->
            if (role.canOpenStaffConsole && trustSafetyRepo != null) trustSafetyRepo.observeAuditLogs()
            else flowOf(emptyList())
        }.safeState("AuditLogs", emptyList())

    val supportAgentsState: StateFlow<List<SupportAgentRecord>> =
        staffRoleState.flatMapLatest { role ->
            if (role.canOpenStaffConsole && supportRepo != null) supportRepo.observeSupportAgents()
            else flowOf(emptyList())
        }.safeState("SupportAgents", emptyList())

    val allPhoneProfilesState: StateFlow<List<PhoneAuthProfile>> =
        staffRoleState.flatMapLatest { role ->
            if (role.canOpenStaffConsole && trustSafetyRepo != null) trustSafetyRepo.observeAllPhoneAuthProfiles()
            else flowOf(emptyList())
        }.safeState("AllPhoneProfiles", emptyList())

    val allSecurityEventsState: StateFlow<List<AuthSecurityEvent>> =
        staffRoleState.flatMapLatest { role ->
            if (role.canOpenStaffConsole && trustSafetyRepo != null) {
                trustSafetyRepo.observeAuthSecurityEvents(currentUserId, isStaff = true)
            } else flowOf(emptyList())
        }.safeState("AllSecurityEvents", emptyList())

    val whatsappConfigState: StateFlow<WhatsAppConfiguration?> =
        staffRoleState.flatMapLatest { role ->
            if (role.canOpenStaffConsole && trustSafetyRepo != null) trustSafetyRepo.observeWhatsAppConfiguration()
            else flowOf(null)
        }.safeState("WhatsAppConfig", null)

    val whatsappEventsState: StateFlow<List<WhatsAppMessageEvent>> =
        staffRoleState.flatMapLatest { role ->
            if (role.canOpenStaffConsole && trustSafetyRepo != null) trustSafetyRepo.observeWhatsAppMessageEvents()
            else flowOf(emptyList())
        }.safeState("WhatsAppEvents", emptyList())

    private val _activeTicketId = MutableStateFlow<String?>(null)
    val activeTicketId: StateFlow<String?> = _activeTicketId.asStateFlow()

    val activeTicketMessagesState: StateFlow<List<SupportTicketMessage>> =
        _activeTicketId.flatMapLatest { id ->
            if (id.isNullOrBlank() || supportRepo == null) flowOf(emptyList())
            else supportRepo.observeTicketMessages(id)
        }.safeState("ActiveTicketMsgs", emptyList())

    val activeTicketNotesState: StateFlow<List<SupportInternalNote>> =
        combine(_activeTicketId, staffRoleState) { id, role -> id to role }
            .flatMapLatest { (id, role) ->
                if (id.isNullOrBlank() || !role.canOpenStaffConsole || supportRepo == null) flowOf(emptyList())
                else supportRepo.observeInternalNotes(id)
            }.safeState("ActiveTicketNotes", emptyList())

    fun selectTicket(ticketId: String?) {
        _activeTicketId.value = ticketId
    }

    // --- Enforcement & Anti-Spam Checks ---

    fun checkFeatureAllowed(feature: DlFeatureRestriction, nowMs: Long = System.currentTimeMillis()): String? {
        val enf = myEnforcementState.value ?: return null
        if (!enf.isFeatureBlocked(feature, nowMs)) return null
        val exp = enf.featureExpiry(feature, nowMs)
        val remainingText = when {
            exp == null -> ""
            exp == -1L -> "permanently"
            else -> {
                val mins = ((exp - nowMs).coerceAtLeast(60_000L)) / 60_000L
                val hrs = mins / 60L
                if (hrs >= 24) "for ${hrs / 24}d ${hrs % 24}h"
                else if (hrs >= 1) "for ${hrs}h ${mins % 60}m"
                else "for ${mins}m"
            }
        }
        val status = DlAccountStatus.fromRaw(enf.accountStatus)
        return if (enf.isGloballySuspended(nowMs)) {
            "Your account is ${status.label.lowercase()} ($remainingText). Review details in Settings > Account Status & Appeals."
        } else {
            "${feature.label} is restricted $remainingText. Review details or appeal in Settings > Account Status & Appeals."
        }
    }

    fun checkOutgoingMessageAllowed(feature: DlFeatureRestriction, text: String): String? {
        checkFeatureAllowed(feature)?.let { return it }
        val settings = platformSettingsState.value
        if (settings.antiSpamEnabled) {
            val (allowed, reason) = AntiSpamEngine.checkAndRecordOutgoingMessage(
                text = text,
                maxPerWindow = settings.rateLimitMaxPer10s.coerceIn(3, 120)
            )
            if (!allowed) return reason ?: "Message rate limit reached."
        }
        return null
    }

    fun restrictionNoticeFor(vararg features: DlFeatureRestriction): String? {
        val enf = myEnforcementState.value ?: return null
        val now = System.currentTimeMillis()
        if (enf.isGloballySuspended(now)) return checkFeatureAllowed(features.firstOrNull() ?: DlFeatureRestriction.SEND_DM, now)
        for (f in features) {
            if (enf.isFeatureBlocked(f, now)) return checkFeatureAllowed(f, now)
        }
        return null
    }

    // --- Reporting & Safety ---

    fun submitDetailedReport(
        targetUid: String,
        targetType: String,
        category: ReportCategory,
        reason: String,
        chatId: String? = null,
        messageId: String? = null,
        storyId: String? = null,
        targetName: String? = null,
        evidenceSnippet: String? = null
    ) {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.submitDetailedReport(
                targetUid = targetUid,
                targetType = targetType,
                category = category,
                reason = reason,
                chatId = chatId,
                messageId = messageId,
                storyId = storyId,
                targetName = targetName,
                evidenceSnippet = evidenceSnippet
            ).onSuccess { triage ->
                onBanner("Report submitted (${triage.label.replace('_', ' ').lowercase()}).")
            }.onFailure { err ->
                onBanner(err.message ?: "Couldn't submit report.")
            }
        }
    }

    fun openModerationCaseFromReport(report: ModerationReport) {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.createModerationCaseFromReport(report, staffRoleState.value)
                .onSuccess { onBanner("Moderation case #$it opened.") }
                .onFailure { onBanner(it.message ?: "Couldn't open case.") }
        }
    }

    fun updateReportStatus(reportId: String, status: String, note: String = "") {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.updateReportStatus(reportId, status, note, staffRoleState.value)
                .onSuccess { onBanner("Report updated to $status.") }
                .onFailure { onBanner(it.message ?: "Couldn't update report.") }
        }
    }

    fun issueWarning(
        targetUid: String,
        reasonCode: String,
        title: String,
        description: String,
        severity: String,
        actorName: String
    ) {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.issueWarning(targetUid, reasonCode, title, description, severity, actorName, staffRoleState.value)
                .onSuccess { onBanner("Warning issued to user.") }
                .onFailure { onBanner(it.message ?: "Couldn't issue warning.") }
        }
    }

    fun acknowledgeWarning(warningId: String) {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.acknowledgeWarning(warningId)
                .onSuccess { onBanner("Warning acknowledged.") }
                .onFailure { onBanner(it.message ?: "Couldn't acknowledge warning.") }
        }
    }

    fun applyEnforcement(
        targetUid: String,
        targetStatus: DlAccountStatus,
        selectedFeatures: Set<DlFeatureRestriction>,
        duration: DlRestrictionDuration,
        customHours: Int,
        reasonCode: String,
        reasonSummary: String,
        humanApprovedPermanent: Boolean
    ) {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.applyEnforcement(
                targetUid = targetUid,
                targetStatus = targetStatus,
                selectedFeatures = selectedFeatures,
                duration = duration,
                customHours = customHours,
                reasonCode = reasonCode,
                reasonSummary = reasonSummary,
                actorRole = staffRoleState.value,
                humanApprovedPermanent = humanApprovedPermanent
            ).onSuccess {
                onBanner("Updated enforcement: ${targetStatus.label}.")
            }.onFailure {
                onBanner(it.message ?: "Couldn't apply enforcement.")
            }
        }
    }

    // --- Appeals ---

    fun submitAppeal(restrictionId: String, originalEnforcerUid: String, reason: String) {
        val repo = trustSafetyRepo ?: return
        val existingForRestriction = myAppealsState.value.count {
            it.restrictionId == restrictionId.ifBlank { "current_enforcement" }
        }
        scope.launch {
            repo.submitAppeal(restrictionId, originalEnforcerUid, reason, existingForRestriction)
                .onSuccess { onBanner("Appeal submitted for independent moderator review.") }
                .onFailure { onBanner(it.message ?: "Couldn't submit appeal.") }
        }
    }

    fun provideAppealAdditionalInfo(appealId: String, info: String) {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.provideAppealAdditionalInfo(appealId, info)
                .onSuccess { onBanner("Additional details sent to reviewers.") }
                .onFailure { onBanner(it.message ?: "Couldn't update appeal.") }
        }
    }

    fun reviewAppeal(appeal: AppealRecord, nextStatus: AppealStatus, note: String) {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.reviewAppeal(appeal, nextStatus, note, staffRoleState.value)
                .onSuccess { onBanner("Appeal marked ${nextStatus.label}.") }
                .onFailure { onBanner(it.message ?: "Couldn't review appeal.") }
        }
    }

    // --- Support Tickets, Agents, Help CMS ---

    fun createSupportTicket(
        userName: String,
        userPhoneOrEmail: String,
        category: SupportCategory,
        subject: String,
        description: String,
        priority: SupportPriority,
        whatsappOptIn: Boolean,
        onCreated: (String) -> Unit = {}
    ) {
        val repo = supportRepo ?: return
        scope.launch {
            repo.createTicket(userName, userPhoneOrEmail, category, subject, description, priority, whatsappOptIn)
                .onSuccess { id ->
                    _activeTicketId.value = id
                    onBanner("Support ticket created (#${id.take(6)}).")
                    onCreated(id)
                }
                .onFailure { onBanner(it.message ?: "Couldn't create support ticket.") }
        }
    }

    fun sendSupportMessage(
        ticket: SupportTicket,
        senderName: String,
        body: String,
        asSupportStaff: Boolean,
        alsoNotifyWhatsApp: Boolean = false,
        whatsappRecipientE164: String? = null
    ) {
        val repo = supportRepo ?: return
        scope.launch {
            repo.sendTicketMessage(ticket, senderName, body, asSupportStaff, alsoNotifyWhatsApp, whatsappRecipientE164)
                .onSuccess { waNote ->
                    if (!waNote.isNullOrBlank()) onBanner(waNote)
                }
                .onFailure { onBanner(it.message ?: "Couldn't send support message.") }
        }
    }

    fun addSupportInternalNote(ticketId: String, authorName: String, note: String) {
        val repo = supportRepo ?: return
        scope.launch {
            repo.addInternalNote(ticketId, authorName, note, staffRoleState.value)
                .onSuccess { onBanner("Internal staff note added.") }
                .onFailure { onBanner(it.message ?: "Couldn't save internal note.") }
        }
    }

    fun updateTicketMetadata(
        ticketId: String,
        status: SupportTicketStatus? = null,
        priority: SupportPriority? = null,
        assignedAgentUid: String? = null,
        assignedAgentName: String? = null
    ) {
        val repo = supportRepo ?: return
        scope.launch {
            repo.updateTicketMetadata(ticketId, status, priority, assignedAgentUid, assignedAgentName)
                .onSuccess { onBanner("Ticket updated.") }
                .onFailure { onBanner(it.message ?: "Couldn't update ticket.") }
        }
    }

    fun upsertHelpArticle(
        articleId: String?,
        category: SupportCategory,
        title: String,
        summary: String,
        bodyMarkdown: String,
        published: Boolean
    ) {
        val repo = supportRepo ?: return
        scope.launch {
            repo.upsertHelpArticle(articleId, category, title, summary, bodyMarkdown, published, staffRoleState.value)
                .onSuccess { onBanner("Help article saved.") }
                .onFailure { onBanner(it.message ?: "Couldn't save help article.") }
        }
    }

    fun upsertSupportAgent(userId: String, displayName: String, specialty: String, availability: String) {
        val repo = supportRepo ?: return
        scope.launch {
            repo.upsertSupportAgent(userId, displayName, specialty, availability, staffRoleState.value)
                .onSuccess { onBanner("Support agent updated.") }
                .onFailure { onBanner(it.message ?: "Couldn't update agent.") }
        }
    }

    // --- Admin RBAC, Phone Profile & WhatsApp Health ---

    fun assignStaffRole(targetUid: String, displayName: String, email: String, role: DlStaffRole) {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.assignStaffRole(targetUid, displayName, email, role, staffRoleState.value)
                .onSuccess { onBanner("Assigned ${role.label} role.") }
                .onFailure { onBanner(it.message ?: "Couldn't assign role.") }
        }
    }

    fun updateWhatsAppOptIn(enabled: Boolean) {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.updateWhatsAppOptIn(enabled)
                .onSuccess {
                    onBanner(if (enabled) "WhatsApp support notices enabled." else "WhatsApp support notices opted out.")
                }
                .onFailure { onBanner(it.message ?: "Couldn't update WhatsApp preference.") }
        }
    }

    fun syncLinkedPhoneProfile(phoneE164: String, countryCode: String, dialCode: String, whatsappOptIn: Boolean) {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.savePhoneAuthProfile(
                phoneE164 = phoneE164,
                countryCode = countryCode,
                dialCode = dialCode,
                otpProvider = "FIREBASE_SMS",
                verificationStatus = "LINKED",
                whatsappOptIn = whatsappOptIn
            ).onSuccess { onBanner("Phone number verified & linked to your account.") }
                .onFailure { onBanner(it.message ?: "Couldn't save linked phone profile.") }
        }
    }

    fun refreshWhatsAppHealth() {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.refreshWhatsAppHealthFromBackend(staffRoleState.value)
                .onSuccess { cfg -> onBanner("WhatsApp status: ${cfg.connectionStatus}") }
                .onFailure { onBanner(it.message ?: "Couldn't reach WhatsApp backend health endpoint.") }
        }
    }

    fun savePlatformSettings(settings: PlatformRuntimeSettings) {
        val repo = trustSafetyRepo ?: return
        scope.launch {
            repo.savePlatformSettings(settings, staffRoleState.value)
                .onSuccess { onBanner("Platform settings saved.") }
                .onFailure { onBanner(it.message ?: "Couldn't save platform settings.") }
        }
    }
}
