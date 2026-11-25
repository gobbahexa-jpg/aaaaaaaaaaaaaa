package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class DlAccountStatus(val label: String, val isSevereSuspension: Boolean = false) {
    ACTIVE("Active"),
    WARNING_ISSUED("Warning issued"),
    MESSAGING_RESTRICTED("Messaging restricted"),
    MEDIA_RESTRICTED("Media sharing restricted"),
    GROUP_CREATION_RESTRICTED("Group creation restricted"),
    GROUP_PARTICIPATION_RESTRICTED("Group participation restricted"),
    CHANNEL_CREATION_RESTRICTED("Channel creation restricted"),
    CHANNEL_POSTING_RESTRICTED("Channel posting restricted"),
    CALLING_RESTRICTED("Calling restricted"),
    TEMP_SUSPENDED("Temporary suspension", isSevereSuspension = true),
    PERM_SUSPENDED("Permanent suspension", isSevereSuspension = true),
    UNDER_REVIEW("Under review"),
    DELETION_PENDING("Account deletion pending", isSevereSuspension = true);

    companion object {
        fun fromRaw(raw: String?): DlAccountStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class DlFeatureRestriction(val key: String, val label: String) {
    SEND_DM("dm", "Direct messages"),
    SEND_GROUP("groupSend", "Group messages"),
    SEND_MEDIA("media", "Photos & videos"),
    SEND_FILES("files", "Documents & files"),
    SEND_VOICE("voice", "Voice messages"),
    START_CALLS("calls", "Voice & video calls"),
    CREATE_GROUPS("createGroup", "Create groups"),
    JOIN_GROUPS("joinGroup", "Join groups & channels"),
    CREATE_CHANNELS("createChannel", "Create channels"),
    POST_CHANNELS("channelPost", "Post in channels"),
    COMMENT_POSTS("comment", "Reactions & comments"),
    POST_STORIES("stories", "Status stories"),
    EDIT_PROFILE("editProfile", "Profile editing"),
    SEND_INVITES("invitations", "Contact requests & invites");

    companion object {
        fun fromKey(key: String): DlFeatureRestriction? = entries.firstOrNull { it.key == key }
    }
}

enum class DlRestrictionDuration(val code: String, val label: String, val durationMs: Long?) {
    H1("1h", "1 hour", 3_600_000L),
    H6("6h", "6 hours", 6 * 3_600_000L),
    H12("12h", "12 hours", 12 * 3_600_000L),
    H24("24h", "24 hours", 24 * 3_600_000L),
    D3("3d", "3 days", 3 * 86_400_000L),
    D7("7d", "7 days", 7 * 86_400_000L),
    D30("30d", "30 days", 30 * 86_400_000L),
    CUSTOM("custom", "Custom hours", null),
    PERMANENT("permanent", "Permanent ban", -1L),
    TERMINATION("termination", "Account termination", -1L)
}

enum class DlStaffRole(val label: String, val rank: Int) {
    USER("User", 0),
    SUPPORT_AGENT("Support Agent", 10),
    SENIOR_SUPPORT_AGENT("Senior Support Agent", 20),
    MODERATOR("Moderator", 30),
    SENIOR_MODERATOR("Senior Moderator", 40),
    ADMIN("Admin", 50),
    SUPER_ADMIN("Super Admin", 60);

    val canOpenStaffConsole: Boolean get() = rank >= SUPPORT_AGENT.rank
    val canModerateUsers: Boolean get() = rank >= MODERATOR.rank
    val canPermanentBan: Boolean get() = rank >= SENIOR_MODERATOR.rank
    val canManageRolesAndPlatform: Boolean get() = rank >= ADMIN.rank

    companion object {
        fun fromRaw(raw: String?): DlStaffRole =
            entries.firstOrNull { it.name == raw } ?: USER
    }
}

enum class ReportCategory(val label: String) {
    SPAM("Spam"),
    IMPERSONATION("Impersonation"),
    HARASSMENT("Harassment"),
    SCAM("Scam or phishing"),
    INAPPROPRIATE_CONTENT("Inappropriate content"),
    OTHER("Other safety issue");

    companion object {
        fun fromRaw(raw: String?): ReportCategory =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class AppealStatus(val label: String) {
    SUBMITTED("Submitted"),
    UNDER_REVIEW("Under review"),
    INFO_REQUESTED("More info requested"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CLOSED("Closed");

    companion object {
        fun fromRaw(raw: String?): AppealStatus =
            entries.firstOrNull { it.name == raw } ?: SUBMITTED
    }
}

enum class SupportCategory(val label: String) {
    ACCOUNT_PHONE("Account & Phone Verification"),
    OTP_LOGIN("OTP & Login Issues"),
    CHATS_MESSAGING("Chats & Messaging"),
    GROUPS_CHANNELS("Groups & Channels"),
    CALLS_MEDIA("Calls & Media"),
    LINKED_DEVICES("Linked Devices"),
    RESTRICTIONS_APPEALS("Account Restrictions & Appeals"),
    PRIVACY_SECURITY("Privacy & Security"),
    REPORT_ABUSE("Report Abuse / Scam"),
    WHATSAPP_BUSINESS("WhatsApp Business Notices"),
    TECHNICAL_BUG("Technical Bug"),
    FEATURE_REQUEST("Feature Request"),
    GENERAL_INQUIRY("General Inquiry");

    companion object {
        fun fromRaw(raw: String?): SupportCategory =
            entries.firstOrNull { it.name == raw } ?: GENERAL_INQUIRY
    }
}

enum class SupportTicketStatus(val label: String) {
    OPEN("Open"),
    ASSIGNED("Assigned"),
    WAITING_ON_USER("Waiting on you"),
    WAITING_ON_SUPPORT("Waiting on support"),
    ESCALATED("Escalated"),
    RESOLVED("Resolved"),
    CLOSED("Closed");

    companion object {
        fun fromRaw(raw: String?): SupportTicketStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class SupportPriority(val label: String) {
    LOW("Low"),
    NORMAL("Normal"),
    HIGH("High"),
    URGENT("Urgent");

    companion object {
        fun fromRaw(raw: String?): SupportPriority =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

enum class OtpChannel(val label: String) {
    FIREBASE_SMS("SMS Verification"),
    WHATSAPP_TEMPLATE("WhatsApp OTP")
}

@Keep
@IgnoreExtraProperties
data class StaffRoleRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "USER",
    val displayName: String = "",
    val email: String = "",
    val active: Boolean = true,
    val assignedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserEnforcement(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountStatus: String = DlAccountStatus.ACTIVE.name,
    val featureExpiries: Map<String, Long> = emptyMap(),
    val globalExpiryEpochMs: Long = 0L,
    val reasonCode: String = "",
    val reasonSummary: String = "",
    val warningCount: Int = 0,
    val activeRestrictionCount: Int = 0,
    val lastEnforcedBy: String = "",
    val lastEnforcedRole: String = "",
    val lastEnforcementId: String = "",
    val appealEligible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isGloballySuspended(nowMs: Long = System.currentTimeMillis()): Boolean {
        val status = DlAccountStatus.fromRaw(accountStatus)
        if (!status.isSevereSuspension) return false
        return globalExpiryEpochMs == -1L || globalExpiryEpochMs > nowMs
    }

    fun isFeatureBlocked(feature: DlFeatureRestriction, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (isGloballySuspended(nowMs)) return true
        val exp = featureExpiries[feature.key] ?: 0L
        return exp == -1L || exp > nowMs
    }

    fun activeBlockedFeatures(nowMs: Long = System.currentTimeMillis()): List<DlFeatureRestriction> {
        if (isGloballySuspended(nowMs)) return DlFeatureRestriction.entries
        return DlFeatureRestriction.entries.filter { isFeatureBlocked(it, nowMs) }
    }

    fun featureExpiry(feature: DlFeatureRestriction, nowMs: Long = System.currentTimeMillis()): Long? {
        if (isGloballySuspended(nowMs)) return globalExpiryEpochMs
        val exp = featureExpiries[feature.key] ?: 0L
        return exp.takeIf { it == -1L || it > nowMs }
    }
}

@Keep
@IgnoreExtraProperties
data class UserWarning(
    @DocumentId val id: String = "",
    val userId: String = "",
    val reasonCode: String = "",
    val title: String = "",
    val description: String = "",
    val policySection: String = "",
    val issuedByUid: String = "",
    val issuedByName: String = "",
    val severity: String = "LOW",
    val acknowledged: Boolean = false,
    val acknowledgedAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserRestrictionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val status: String = DlAccountStatus.ACTIVE.name,
    val features: List<String> = emptyList(),
    val durationLabel: String = "",
    val expiresAtEpochMs: Long = 0L,
    val reasonCode: String = "",
    val reason: String = "",
    val enforcedByUid: String = "",
    val enforcedByRole: String = "",
    val humanApproved: Boolean = true,
    val active: Boolean = true,
    val revokedByUid: String = "",
    val revokeReason: String = "",
    val appealEligible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ModerationCase(
    @DocumentId val id: String = "",
    val targetUid: String = "",
    val targetType: String = "USER",
    val category: String = "OTHER",
    val priority: String = "NORMAL",
    val status: String = "OPEN",
    val reportIds: List<String> = emptyList(),
    val assignedModeratorUid: String = "",
    val aiTriageSummary: String = "",
    val recommendedAction: String = "",
    val resolutionSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ModerationActionRecord(
    @DocumentId val id: String = "",
    val caseId: String = "",
    val targetUid: String = "",
    val actorUid: String = "",
    val actorRole: String = "",
    val actionType: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppealRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val restrictionId: String = "",
    val originalEnforcerUid: String = "",
    val status: String = AppealStatus.SUBMITTED.name,
    val reason: String = "",
    val additionalInfo: String = "",
    val attemptNumber: Int = 1,
    val reviewerUid: String = "",
    val reviewerDecisionNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhoneOrEmail: String = "",
    val category: String = SupportCategory.GENERAL_INQUIRY.name,
    val subject: String = "",
    val description: String = "",
    val status: String = SupportTicketStatus.OPEN.name,
    val priority: String = SupportPriority.NORMAL.name,
    val assignedAgentUid: String = "",
    val assignedAgentName: String = "",
    val lastMessageText: String = "",
    val lastMessageAtEpochMs: Long = 0L,
    val whatsappOptIn: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SupportTicketMessage(
    @DocumentId val id: String = "",
    val ticketId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "USER",
    val body: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SupportInternalNote(
    @DocumentId val id: String = "",
    val ticketId: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SupportAgentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val specialty: String = "General",
    val availabilityStatus: String = "ONLINE",
    val activeTicketCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class HelpArticle(
    @DocumentId val id: String = "",
    val category: String = SupportCategory.GENERAL_INQUIRY.name,
    val title: String = "",
    val summary: String = "",
    val bodyMarkdown: String = "",
    val keywords: List<String> = emptyList(),
    val published: Boolean = true,
    val sortOrder: Int = 0,
    val helpfulCount: Int = 0,
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PhoneAuthProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val phoneE164: String = "",
    val countryCode: String = "LK",
    val countryDialCode: String = "+94",
    val otpProvider: String = OtpChannel.FIREBASE_SMS.name,
    val verificationStatus: String = "VERIFIED",
    val whatsappOptIn: Boolean = false,
    val verifiedAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuthSecurityEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventType: String = "",
    val channel: String = "",
    val maskedTarget: String = "",
    val success: Boolean = true,
    val detail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminAuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorRole: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WhatsAppConfiguration(
    @DocumentId val id: String = "",
    val enabled: Boolean = false,
    val connectionStatus: String = "UNCONFIGURED",
    val displayPhoneNumber: String = "",
    val phoneNumberIdMasked: String = "",
    val wabaIdMasked: String = "",
    val webhookConfigured: Boolean = false,
    val otpProviderEnabled: Boolean = false,
    val otpTemplateApproved: Boolean = false,
    val lastErrorSummary: String = "",
    val lastHealthCheckAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WhatsAppMessageEvent(
    @DocumentId val id: String = "",
    val ticketId: String = "",
    val recipientMasked: String = "",
    val messageType: String = "TEXT",
    val direction: String = "OUTBOUND",
    val deliveryStatus: String = "SENT",
    val templateName: String = "",
    val errorSummary: String = "",
    val createdAtEpochMs: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class PlatformRuntimeSettings(
    @DocumentId val id: String = "",
    val whatsappOtpEnabled: Boolean = false,
    val whatsappSupportEnabled: Boolean = true,
    val antiSpamEnabled: Boolean = true,
    val rateLimitMaxPer10s: Int = 12,
    val autoEscalationReportThreshold: Int = 3,
    val maintenanceMode: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class LegalPolicyDocument(
    val id: String,
    val title: String,
    val versionTag: String,
    val isDraft: Boolean = true,
    val lastUpdatedLabel: String,
    val sections: List<Pair<String, String>>
)
