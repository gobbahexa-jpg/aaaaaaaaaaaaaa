package com.example.data.repository

import android.content.Context
import com.example.core.BackendGatewayClient
import com.example.core.BackendGatewayResult
import com.example.core.OperationType
import com.example.core.PhoneNumberHelper
import com.example.core.handleFirestoreError
import com.example.data.model.AdminAuditLog
import com.example.data.model.AppealRecord
import com.example.data.model.AppealStatus
import com.example.data.model.AuthSecurityEvent
import com.example.data.model.DlAccountStatus
import com.example.data.model.DlFeatureRestriction
import com.example.data.model.DlRestrictionDuration
import com.example.data.model.DlStaffRole
import com.example.data.model.ModerationActionRecord
import com.example.data.model.ModerationCase
import com.example.data.model.ModerationReport
import com.example.data.model.PhoneAuthProfile
import com.example.data.model.PlatformRuntimeSettings
import com.example.data.model.ReportCategory
import com.example.data.model.StaffRoleRecord
import com.example.data.model.UserEnforcement
import com.example.data.model.UserRestrictionRecord
import com.example.data.model.UserWarning
import com.example.data.model.WhatsAppConfiguration
import com.example.data.model.WhatsAppMessageEvent
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class ReportTriageAssessment(
    val label: String,
    val severityScore: Int,
    val recommendedAction: String,
    val shouldOpenCase: Boolean
)

object AntiSpamEngine {
    private val recentMessageTimestamps = mutableListOf<Long>()
    private val suspiciousUrlRegex = Regex("(free-nitro|claim-reward|verify-wallet|dl-gift|bit\\.ly|tinyurl)", RegexOption.IGNORE_CASE)

    @Synchronized
    fun checkAndRecordOutgoingMessage(
        text: String,
        maxPerWindow: Int = 12,
        windowMs: Long = 10_000L,
        nowMs: Long = System.currentTimeMillis()
    ): Pair<Boolean, String?> {
        recentMessageTimestamps.removeAll { nowMs - it > windowMs }
        if (recentMessageTimestamps.size >= maxPerWindow) {
            return false to "You're sending messages too quickly. Pause for a few seconds."
        }
        recentMessageTimestamps.add(nowMs)
        if (text.length > 2000 && suspiciousUrlRegex.containsMatchIn(text)) {
            return false to "Message blocked for containing high-risk phishing patterns."
        }
        return true to null
    }

    fun assessReportRisk(
        category: ReportCategory,
        reason: String,
        evidenceSnippet: String?
    ): ReportTriageAssessment {
        val combined = "$reason ${evidenceSnippet.orEmpty()}"
        var score = when (category) {
            ReportCategory.SCAM -> 72
            ReportCategory.IMPERSONATION -> 66
            ReportCategory.HARASSMENT -> 64
            ReportCategory.INAPPROPRIATE_CONTENT -> 58
            ReportCategory.SPAM -> 50
            ReportCategory.OTHER -> 35
        }
        if (suspiciousUrlRegex.containsMatchIn(combined)) score += 20
        if (combined.contains("otp", ignoreCase = true) || combined.contains("password", ignoreCase = true)) score += 15
        val clamped = score.coerceIn(10, 99)
        val label = when {
            clamped >= 80 -> "HIGH_RISK_HUMAN_REVIEW"
            clamped >= 55 -> "MODERATE_RISK_QUEUE"
            else -> "LOW_RISK_TRIAGE"
        }
        val recommended = when {
            clamped >= 80 -> "Temporary messaging hold pending human moderator review"
            clamped >= 55 -> "Moderator verification & potential warning"
            else -> "Log and monitor"
        }
        return ReportTriageAssessment(
            label = label,
            severityScore = clamped,
            recommendedAction = recommended,
            shouldOpenCase = clamped >= 50
        )
    }
}

class TrustSafetyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Authentication required.")

    fun observeMyEnforcement(uid: String): Flow<UserEnforcement?> =
        db.collection("userEnforcements").document(uid)
            .snapshots()
            .map { it.toObject(UserEnforcement::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeAllEnforcements(): Flow<List<UserEnforcement>> =
        db.collection("userEnforcements")
            .snapshots()
            .map { snap -> snap.toObjects(UserEnforcement::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeMyWarnings(uid: String): Flow<List<UserWarning>> =
        db.collection("userWarnings")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(UserWarning::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeMyRestrictions(uid: String): Flow<List<UserRestrictionRecord>> =
        db.collection("userRestrictions")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(UserRestrictionRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeMyAppeals(uid: String, isModerator: Boolean): Flow<List<AppealRecord>> {
        val query = if (isModerator) db.collection("appeals")
        else db.collection("appeals").whereEqualTo("userId", uid)
        return query.snapshots().map { snap ->
            snap.toObjects(AppealRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }
    }

    fun observeStaffRole(uid: String): Flow<StaffRoleRecord?> =
        db.collection("staffRoles").document(uid)
            .snapshots()
            .map { it.toObject(StaffRoleRecord::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeAllStaffRoles(): Flow<List<StaffRoleRecord>> =
        db.collection("staffRoles")
            .snapshots()
            .map { snap -> snap.toObjects(StaffRoleRecord::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeModerationCases(): Flow<List<ModerationCase>> =
        db.collection("moderationCases")
            .snapshots()
            .map { snap ->
                snap.toObjects(ModerationCase::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeModerationActions(): Flow<List<ModerationActionRecord>> =
        db.collection("moderationActions")
            .snapshots()
            .map { snap ->
                snap.toObjects(ModerationActionRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeAuditLogs(): Flow<List<AdminAuditLog>> =
        db.collection("adminAuditLogs")
            .snapshots()
            .map { snap ->
                snap.toObjects(AdminAuditLog::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observePhoneAuthProfile(uid: String): Flow<PhoneAuthProfile?> =
        db.collection("phoneAuthProfiles").document(uid)
            .snapshots()
            .map { it.toObject(PhoneAuthProfile::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeAllPhoneAuthProfiles(): Flow<List<PhoneAuthProfile>> =
        db.collection("phoneAuthProfiles")
            .snapshots()
            .map { snap -> snap.toObjects(PhoneAuthProfile::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeAuthSecurityEvents(uid: String, isStaff: Boolean): Flow<List<AuthSecurityEvent>> {
        val query = if (isStaff) db.collection("authSecurityEvents")
        else db.collection("authSecurityEvents").whereEqualTo("userId", uid)
        return query.snapshots().map { snap ->
            snap.toObjects(AuthSecurityEvent::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }
    }

    fun observeWhatsAppConfiguration(): Flow<WhatsAppConfiguration?> =
        db.collection("whatsappConfigurations").document("primary")
            .snapshots()
            .map { it.toObject(WhatsAppConfiguration::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeWhatsAppMessageEvents(): Flow<List<WhatsAppMessageEvent>> =
        db.collection("whatsappMessageEvents")
            .snapshots()
            .map { snap ->
                snap.toObjects(WhatsAppMessageEvent::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAtEpochMs }
            }

    fun observePlatformSettings(): Flow<PlatformRuntimeSettings> =
        db.collection("platformSettings").document("runtime")
            .snapshots()
            .map { snap ->
                snap.toObject(PlatformRuntimeSettings::class.java, ServerTimestampBehavior.ESTIMATE)
                    ?: PlatformRuntimeSettings(id = "runtime")
            }

    suspend fun submitDetailedReport(
        targetUid: String,
        targetType: String,
        category: ReportCategory,
        reason: String,
        chatId: String? = null,
        messageId: String? = null,
        storyId: String? = null,
        targetName: String? = null,
        evidenceSnippet: String? = null
    ): Result<ReportTriageAssessment> {
        var path = "reports"
        return try {
            val uid = requireUid()
            val cleanReason = reason.trim().take(500)
            require(cleanReason.length >= 3) { "Please add a short reason for your report." }
            val triage = AntiSpamEngine.assessReportRisk(category, cleanReason, evidenceSnippet)
            val repRef = db.collection("reports").document()
            path = repRef.path
            val payload = mutableMapOf<String, Any>(
                "reporterId" to uid,
                "targetUid" to targetUid.ifBlank { "unknown_target" },
                "targetType" to targetType,
                "category" to category.name,
                "reason" to cleanReason,
                "status" to if (triage.shouldOpenCase) "TRIAGED" else "OPEN",
                "triageLabel" to triage.label,
                "severityScore" to triage.severityScore,
                "aiTriaged" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!chatId.isNullOrBlank()) payload["chatId"] = chatId.take(128)
            if (!messageId.isNullOrBlank()) payload["messageId"] = messageId.take(128)
            if (!storyId.isNullOrBlank()) payload["storyId"] = storyId.take(128)
            if (!targetName.isNullOrBlank()) payload["targetName"] = targetName.take(120)
            if (!evidenceSnippet.isNullOrBlank()) payload["evidenceSnippet"] = evidenceSnippet.take(550)
            repRef.set(payload).await()
            Result.success(triage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createModerationCaseFromReport(
        report: ModerationReport,
        actorRole: DlStaffRole
    ): Result<String> {
        if (!actorRole.canModerateUsers) return Result.failure(SecurityException("Moderator permission required."))
        var path = "moderationCases"
        return try {
            val actorUid = requireUid()
            val caseRef = db.collection("moderationCases").document()
            path = caseRef.path
            val priority = when {
                (report.severityScore ?: 0) >= 80 -> "URGENT"
                (report.severityScore ?: 0) >= 60 -> "HIGH"
                else -> "NORMAL"
            }
            caseRef.set(
                mapOf(
                    "targetUid" to report.targetUid,
                    "targetType" to report.targetType,
                    "category" to report.category,
                    "priority" to priority,
                    "status" to "OPEN",
                    "reportIds" to listOf(report.id),
                    "assignedModeratorUid" to actorUid,
                    "aiTriageSummary" to (report.triageLabel ?: "Manual moderation case"),
                    "recommendedAction" to "Review evidence before any restriction",
                    "resolutionSummary" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("reports").document(report.id).update(
                mapOf(
                    "status" to "IN_REVIEW",
                    "caseId" to caseRef.id,
                    "reviewedByUid" to actorUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(actorUid, actorRole.name, "OPEN_MODERATION_CASE", "CASE", caseRef.id, "Opened case for report ${report.id}")
            Result.success(caseRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateReportStatus(
        reportId: String,
        status: String,
        resolutionNote: String,
        actorRole: DlStaffRole
    ): Result<Unit> {
        if (!actorRole.canModerateUsers) return Result.failure(SecurityException("Moderator permission required."))
        val path = "reports/$reportId"
        return try {
            val uid = requireUid()
            db.collection("reports").document(reportId).update(
                mapOf(
                    "status" to status,
                    "reviewedByUid" to uid,
                    "resolutionNote" to resolutionNote.trim().take(480),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(uid, actorRole.name, "UPDATE_REPORT", "REPORT", reportId, "Set status=$status")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun issueWarning(
        targetUid: String,
        reasonCode: String,
        title: String,
        description: String,
        severity: String,
        actorName: String,
        actorRole: DlStaffRole
    ): Result<Unit> {
        if (!actorRole.canModerateUsers) return Result.failure(SecurityException("Moderator permission required."))
        var path = "userWarnings"
        return try {
            val actorUid = requireUid()
            val warnRef = db.collection("userWarnings").document()
            path = warnRef.path
            warnRef.set(
                mapOf(
                    "userId" to targetUid,
                    "reasonCode" to reasonCode.trim().take(64).ifBlank { "POLICY_NOTICE" },
                    "title" to title.trim().take(120).ifBlank { "Community policy warning" },
                    "description" to description.trim().take(500),
                    "policySection" to "Community Guidelines",
                    "issuedByUid" to actorUid,
                    "issuedByName" to actorName.take(60),
                    "severity" to severity,
                    "acknowledged" to false,
                    "acknowledgedAtEpochMs" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val enfRef = db.collection("userEnforcements").document(targetUid)
            val existingSnap = enfRef.get().await()
            val existing = existingSnap.toObject(UserEnforcement::class.java, ServerTimestampBehavior.ESTIMATE)
            val nextWarnings = (existing?.warningCount ?: 0) + 1
            val currentStatus = existing?.accountStatus ?: DlAccountStatus.ACTIVE.name
            val nextStatus = if (currentStatus == DlAccountStatus.ACTIVE.name) {
                DlAccountStatus.WARNING_ISSUED.name
            } else currentStatus

            val enfPayload = mapOf(
                "userId" to targetUid,
                "accountStatus" to nextStatus,
                "featureExpiries" to (existing?.featureExpiries ?: emptyMap()),
                "globalExpiryEpochMs" to (existing?.globalExpiryEpochMs ?: 0L),
                "reasonCode" to reasonCode.take(80),
                "reasonSummary" to title.trim().take(480),
                "warningCount" to nextWarnings,
                "activeRestrictionCount" to (existing?.activeRestrictionCount ?: 0),
                "lastEnforcedBy" to actorUid,
                "lastEnforcedRole" to actorRole.name,
                "lastEnforcementId" to warnRef.id,
                "appealEligible" to true,
                "createdAt" to (existing?.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            enfRef.set(enfPayload).await()
            recordModerationAction(targetUid, actorUid, actorRole.name, "ISSUE_WARNING", "$title ($severity)")
            logAudit(actorUid, actorRole.name, "ISSUE_WARNING", "USER", targetUid, title)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeWarning(warningId: String): Result<Unit> {
        val path = "userWarnings/$warningId"
        return try {
            requireUid()
            db.collection("userWarnings").document(warningId).update(
                mapOf(
                    "acknowledged" to true,
                    "acknowledgedAtEpochMs" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun applyEnforcement(
        targetUid: String,
        targetStatus: DlAccountStatus,
        selectedFeatures: Set<DlFeatureRestriction>,
        duration: DlRestrictionDuration,
        customHours: Int,
        reasonCode: String,
        reasonSummary: String,
        actorRole: DlStaffRole,
        humanApprovedPermanent: Boolean
    ): Result<String> {
        if (!actorRole.canModerateUsers) return Result.failure(SecurityException("Moderator permission required."))
        if (targetStatus in setOf(DlAccountStatus.PERM_SUSPENDED, DlAccountStatus.DELETION_PENDING) ||
            duration in setOf(DlRestrictionDuration.PERMANENT, DlRestrictionDuration.TERMINATION)
        ) {
            if (!actorRole.canPermanentBan) {
                return Result.failure(SecurityException("Senior Moderator or Admin role required for permanent suspension."))
            }
            if (!humanApprovedPermanent) {
                return Result.failure(IllegalArgumentException("Explicit human confirmation is required for permanent suspension."))
            }
        }
        var path = "userEnforcements/$targetUid"
        return try {
            val actorUid = requireUid()
            val nowMs = System.currentTimeMillis()
            val expiresAtEpochMs = when (duration) {
                DlRestrictionDuration.PERMANENT, DlRestrictionDuration.TERMINATION -> -1L
                DlRestrictionDuration.CUSTOM -> nowMs + customHours.coerceIn(1, 8760) * 3_600_000L
                else -> nowMs + (duration.durationMs ?: 3_600_000L)
            }

            val enfRef = db.collection("userEnforcements").document(targetUid)
            val existing = enfRef.get().await().toObject(UserEnforcement::class.java, ServerTimestampBehavior.ESTIMATE)
            val nextExpiries = (existing?.featureExpiries ?: emptyMap()).toMutableMap()

            if (targetStatus == DlAccountStatus.ACTIVE) {
                nextExpiries.clear()
            } else {
                selectedFeatures.forEach { f -> nextExpiries[f.key] = expiresAtEpochMs }
            }

            val globalExp = when {
                targetStatus == DlAccountStatus.ACTIVE -> 0L
                targetStatus.isSevereSuspension -> expiresAtEpochMs
                else -> existing?.globalExpiryEpochMs ?: 0L
            }

            val restRef = db.collection("userRestrictions").document()
            restRef.set(
                mapOf(
                    "userId" to targetUid,
                    "status" to targetStatus.name,
                    "features" to selectedFeatures.map { it.key },
                    "durationLabel" to if (duration == DlRestrictionDuration.CUSTOM) "${customHours}h" else duration.label,
                    "expiresAtEpochMs" to expiresAtEpochMs,
                    "reasonCode" to reasonCode.ifBlank { "POLICY_ENFORCEMENT" }.take(64),
                    "reason" to reasonSummary.trim().take(500),
                    "enforcedByUid" to actorUid,
                    "enforcedByRole" to actorRole.name,
                    "humanApproved" to true,
                    "active" to (targetStatus != DlAccountStatus.ACTIVE),
                    "appealEligible" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val activeCount = if (targetStatus == DlAccountStatus.ACTIVE) 0 else {
                nextExpiries.count { (_, exp) -> exp == -1L || exp > nowMs }.coerceAtLeast(
                    if (targetStatus.isSevereSuspension) 1 else 0
                )
            }

            enfRef.set(
                mapOf(
                    "userId" to targetUid,
                    "accountStatus" to targetStatus.name,
                    "featureExpiries" to nextExpiries,
                    "globalExpiryEpochMs" to globalExp,
                    "reasonCode" to reasonCode.ifBlank { "POLICY_ENFORCEMENT" }.take(80),
                    "reasonSummary" to reasonSummary.trim().take(500),
                    "warningCount" to (existing?.warningCount ?: 0),
                    "activeRestrictionCount" to activeCount,
                    "lastEnforcedBy" to actorUid,
                    "lastEnforcedRole" to actorRole.name,
                    "lastEnforcementId" to restRef.id,
                    "appealEligible" to true,
                    "createdAt" to (existing?.createdAt ?: FieldValue.serverTimestamp()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("users").document(targetUid).update(
                mapOf(
                    "suspended" to (targetStatus.isSevereSuspension && (globalExp == -1L || globalExp > nowMs)),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            recordModerationAction(
                targetUid = targetUid,
                actorUid = actorUid,
                actorRole = actorRole.name,
                actionType = "ENFORCE_${targetStatus.name}",
                notes = "$reasonSummary (${duration.label})"
            )
            logAudit(actorUid, actorRole.name, "ENFORCE_${targetStatus.name}", "USER", targetUid, reasonSummary)
            Result.success(restRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun submitAppeal(
        restrictionId: String,
        originalEnforcerUid: String,
        reason: String,
        existingAppealsForRestriction: Int
    ): Result<String> {
        var path = "appeals"
        return try {
            val uid = requireUid()
            val clean = reason.trim().take(1500)
            require(clean.length >= 10) { "Please explain your appeal in at least 10 characters." }
            require(existingAppealsForRestriction < 3) { "Maximum 3 appeal attempts reached for this action." }
            val ref = db.collection("appeals").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "restrictionId" to restrictionId.ifBlank { "current_enforcement" },
                    "originalEnforcerUid" to originalEnforcerUid.ifBlank { "system_mod" },
                    "status" to AppealStatus.SUBMITTED.name,
                    "reason" to clean,
                    "additionalInfo" to "",
                    "attemptNumber" to (existingAppealsForRestriction + 1),
                    "reviewerUid" to "",
                    "reviewerDecisionNote" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun provideAppealAdditionalInfo(appealId: String, info: String): Result<Unit> {
        val path = "appeals/$appealId"
        return try {
            requireUid()
            val clean = info.trim().take(1200)
            require(clean.length >= 5) { "Please enter your additional details." }
            db.collection("appeals").document(appealId).update(
                mapOf(
                    "additionalInfo" to clean,
                    "status" to AppealStatus.SUBMITTED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun reviewAppeal(
        appeal: AppealRecord,
        nextStatus: AppealStatus,
        decisionNote: String,
        actorRole: DlStaffRole
    ): Result<Unit> {
        if (!actorRole.canModerateUsers) return Result.failure(SecurityException("Moderator permission required."))
        val reviewerUid = requireUid()
        if (reviewerUid == appeal.originalEnforcerUid && !actorRole.canManageRolesAndPlatform) {
            return Result.failure(SecurityException("A different moderator must review this appeal."))
        }
        val path = "appeals/${appeal.id}"
        return try {
            db.collection("appeals").document(appeal.id).update(
                mapOf(
                    "status" to nextStatus.name,
                    "reviewerUid" to reviewerUid,
                    "reviewerDecisionNote" to decisionNote.trim().take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (nextStatus == AppealStatus.APPROVED) {
                applyEnforcement(
                    targetUid = appeal.userId,
                    targetStatus = DlAccountStatus.ACTIVE,
                    selectedFeatures = emptySet(),
                    duration = DlRestrictionDuration.H1,
                    customHours = 0,
                    reasonCode = "APPEAL_APPROVED",
                    reasonSummary = "Appeal ${appeal.id} approved: ${decisionNote.trim()}",
                    actorRole = actorRole,
                    humanApprovedPermanent = true
                )
            }
            logAudit(reviewerUid, actorRole.name, "REVIEW_APPEAL_${nextStatus.name}", "APPEAL", appeal.id, decisionNote)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun assignStaffRole(
        targetUid: String,
        displayName: String,
        email: String,
        role: DlStaffRole,
        actorRole: DlStaffRole
    ): Result<Unit> {
        if (!actorRole.canManageRolesAndPlatform) return Result.failure(SecurityException("Admin permission required."))
        val path = "staffRoles/$targetUid"
        return try {
            val actorUid = requireUid()
            db.collection("staffRoles").document(targetUid).set(
                mapOf(
                    "userId" to targetUid,
                    "role" to role.name,
                    "displayName" to displayName.take(60),
                    "email" to email.take(180),
                    "active" to (role != DlStaffRole.USER),
                    "assignedBy" to actorUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(actorUid, actorRole.name, "ASSIGN_ROLE", "USER", targetUid, "Assigned role=${role.name}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun savePhoneAuthProfile(
        phoneE164: String,
        countryCode: String,
        dialCode: String,
        otpProvider: String,
        verificationStatus: String,
        whatsappOptIn: Boolean
    ): Result<Unit> {
        val uid = requireUid()
        val path = "phoneAuthProfiles/$uid"
        return try {
            require(PhoneNumberHelper.isValidE164(phoneE164)) { "Phone number must be valid E.164 format." }
            val ref = db.collection("phoneAuthProfiles").document(uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "phoneE164" to phoneE164,
                        "countryCode" to countryCode.take(4),
                        "countryDialCode" to dialCode.take(8),
                        "otpProvider" to otpProvider,
                        "verificationStatus" to verificationStatus,
                        "whatsappOptIn" to whatsappOptIn,
                        "verifiedAtEpochMs" to System.currentTimeMillis(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "phoneE164" to phoneE164,
                        "countryCode" to countryCode.take(4),
                        "countryDialCode" to dialCode.take(8),
                        "otpProvider" to otpProvider,
                        "verificationStatus" to verificationStatus,
                        "whatsappOptIn" to whatsappOptIn,
                        "verifiedAtEpochMs" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            recordAuthSecurityEvent(
                eventType = "PHONE_${verificationStatus}",
                channel = otpProvider,
                maskedTarget = PhoneNumberHelper.maskPhone(phoneE164),
                success = true,
                detail = "Phone profile synced ($countryCode $dialCode)"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateWhatsAppOptIn(enabled: Boolean): Result<Unit> {
        val uid = requireUid()
        val path = "phoneAuthProfiles/$uid"
        return try {
            val ref = db.collection("phoneAuthProfiles").document(uid)
            if (ref.get().await().exists()) {
                ref.update(
                    mapOf(
                        "whatsappOptIn" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordAuthSecurityEvent(
        eventType: String,
        channel: String,
        maskedTarget: String,
        success: Boolean,
        detail: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("authSecurityEvents").document().set(
                mapOf(
                    "userId" to uid,
                    "eventType" to eventType.take(64),
                    "channel" to channel.take(40),
                    "maskedTarget" to maskedTarget.take(40),
                    "success" to success,
                    "detail" to detail.take(280),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun refreshWhatsAppHealthFromBackend(actorRole: DlStaffRole): Result<WhatsAppConfiguration> {
        if (!actorRole.canManageRolesAndPlatform) return Result.failure(SecurityException("Admin permission required."))
        return when (val res = BackendGatewayClient.getWhatsAppHealth()) {
            is BackendGatewayResult.Success -> {
                val snap = res.data
                val status = if (snap.configured) "CONFIGURED" else "UNCONFIGURED"
                val ref = db.collection("whatsappConfigurations").document("primary")
                val existing = ref.get().await()
                val payload = mapOf(
                    "enabled" to snap.enabled,
                    "connectionStatus" to status,
                    "displayPhoneNumber" to snap.displayPhoneNumber,
                    "phoneNumberIdMasked" to "",
                    "wabaIdMasked" to "",
                    "webhookConfigured" to snap.webhookConfigured,
                    "otpProviderEnabled" to snap.otpProviderEnabled,
                    "otpTemplateApproved" to snap.otpTemplateApproved,
                    "lastErrorSummary" to if (snap.missingVariables.isNotEmpty()) {
                        "Missing backend secrets: ${snap.missingVariables.joinToString(", ")}"
                    } else "",
                    "lastHealthCheckAtEpochMs" to System.currentTimeMillis(),
                    "createdAt" to (existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
                Result.success(
                    WhatsAppConfiguration(
                        id = "default",
                        enabled = snap.enabled,
                        connectionStatus = status,
                        displayPhoneNumber = snap.displayPhoneNumber,
                        webhookConfigured = snap.webhookConfigured,
                        otpProviderEnabled = snap.otpProviderEnabled,
                        otpTemplateApproved = snap.otpTemplateApproved,
                        lastErrorSummary = payload["lastErrorSummary"] as String,
                        lastHealthCheckAtEpochMs = System.currentTimeMillis()
                    )
                )
            }
            is BackendGatewayResult.NotConfigured -> Result.failure(IllegalStateException(res.reason))
            is BackendGatewayResult.Failure -> Result.failure(IllegalStateException(res.message))
        }
    }

    suspend fun savePlatformSettings(
        settings: PlatformRuntimeSettings,
        actorRole: DlStaffRole
    ): Result<Unit> {
        if (!actorRole.canManageRolesAndPlatform) return Result.failure(SecurityException("Admin permission required."))
        val path = "platformSettings/runtime"
        return try {
            val uid = requireUid()
            val ref = db.collection("platformSettings").document("runtime")
            val snap = ref.get().await()
            ref.set(
                mapOf(
                    "whatsappOtpEnabled" to settings.whatsappOtpEnabled,
                    "whatsappSupportEnabled" to settings.whatsappSupportEnabled,
                    "antiSpamEnabled" to settings.antiSpamEnabled,
                    "rateLimitMaxPer10s" to settings.rateLimitMaxPer10s.coerceIn(1, 120),
                    "autoEscalationReportThreshold" to settings.autoEscalationReportThreshold.coerceIn(1, 100),
                    "maintenanceMode" to settings.maintenanceMode,
                    "createdAt" to (snap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(uid, actorRole.name, "UPDATE_PLATFORM_SETTINGS", "SETTINGS", "runtime", "Updated runtime safety & WhatsApp flags")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun recordModerationAction(
        targetUid: String,
        actorUid: String,
        actorRole: String,
        actionType: String,
        notes: String
    ) {
        try {
            db.collection("moderationActions").document().set(
                mapOf(
                    "caseId" to "",
                    "targetUid" to targetUid,
                    "actorUid" to actorUid,
                    "actorRole" to actorRole,
                    "actionType" to actionType,
                    "notes" to notes.take(400),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun logAudit(
        actorUid: String,
        actorRole: String,
        action: String,
        targetType: String,
        targetId: String,
        summary: String
    ) {
        try {
            db.collection("adminAuditLogs").document().set(
                mapOf(
                    "actorUid" to actorUid,
                    "actorRole" to actorRole,
                    "action" to action.take(80),
                    "targetType" to targetType.take(40),
                    "targetId" to targetId.take(128),
                    "summary" to summary.take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }
}
