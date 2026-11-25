package com.example.data.repository

import com.example.core.BackendGatewayClient
import com.example.core.BackendGatewayResult
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.DlStaffRole
import com.example.data.model.HelpArticle
import com.example.data.model.LegalPolicyDocument
import com.example.data.model.SupportAgentRecord
import com.example.data.model.SupportCategory
import com.example.data.model.SupportInternalNote
import com.example.data.model.SupportPriority
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketMessage
import com.example.data.model.SupportTicketStatus
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

class SupportRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Authentication required.")

    fun observeTickets(uid: String, isStaff: Boolean): Flow<List<SupportTicket>> {
        val query = if (isStaff) db.collection("supportTickets")
        else db.collection("supportTickets").whereEqualTo("userId", uid)
        return query.snapshots().map { snap ->
            snap.toObjects(SupportTicket::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.lastMessageAtEpochMs.takeIf { ts -> ts > 0 } ?: (it.updatedAt?.toDate()?.time ?: 0L) }
        }
    }

    fun observeTicketMessages(ticketId: String): Flow<List<SupportTicketMessage>> =
        db.collection("supportTickets").document(ticketId)
            .collection("messages")
            .snapshots()
            .map { snap ->
                snap.toObjects(SupportTicketMessage::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }

    fun observeInternalNotes(ticketId: String): Flow<List<SupportInternalNote>> =
        db.collection("supportTickets").document(ticketId)
            .collection("internalNotes")
            .snapshots()
            .map { snap ->
                snap.toObjects(SupportInternalNote::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeHelpArticles(includeUnpublished: Boolean): Flow<List<HelpArticle>> {
        val query = if (includeUnpublished) db.collection("helpArticles")
        else db.collection("helpArticles").whereEqualTo("published", true)
        return query.snapshots().map { snap ->
            val remote = snap.toObjects(HelpArticle::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.sortOrder }
            if (remote.isEmpty()) builtInHelpArticles else (remote + builtInHelpArticles).distinctBy { it.title }
        }
    }

    fun observeSupportAgents(): Flow<List<SupportAgentRecord>> =
        db.collection("supportAgents")
            .snapshots()
            .map { snap -> snap.toObjects(SupportAgentRecord::class.java, ServerTimestampBehavior.ESTIMATE) }

    suspend fun createTicket(
        userName: String,
        userPhoneOrEmail: String,
        category: SupportCategory,
        subject: String,
        description: String,
        priority: SupportPriority,
        whatsappOptIn: Boolean
    ): Result<String> {
        var path = "supportTickets"
        return try {
            val uid = requireUid()
            val cleanSubject = subject.trim().take(160)
            val cleanDesc = description.trim().take(3000)
            require(cleanSubject.length >= 3) { "Please add a short subject (at least 3 characters)." }
            require(cleanDesc.length >= 5) { "Please describe the issue so our team can help." }

            val ticketRef = db.collection("supportTickets").document()
            path = ticketRef.path
            val nowMs = System.currentTimeMillis()
            ticketRef.set(
                mapOf(
                    "userId" to uid,
                    "userName" to userName.trim().take(60).ifBlank { "Operator" },
                    "userPhoneOrEmail" to userPhoneOrEmail.trim().take(120),
                    "category" to category.name,
                    "subject" to cleanSubject,
                    "description" to cleanDesc,
                    "status" to SupportTicketStatus.OPEN.name,
                    "priority" to priority.name,
                    "assignedAgentUid" to "",
                    "assignedAgentName" to "",
                    "lastMessageText" to cleanDesc.take(240),
                    "lastMessageAtEpochMs" to nowMs,
                    "whatsappOptIn" to whatsappOptIn,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            ticketRef.collection("messages").document().set(
                mapOf(
                    "ticketId" to ticketRef.id,
                    "senderId" to uid,
                    "senderName" to userName.trim().take(60).ifBlank { "Operator" },
                    "senderRole" to "USER",
                    "body" to cleanDesc,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(ticketRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendTicketMessage(
        ticket: SupportTicket,
        senderName: String,
        body: String,
        asSupportStaff: Boolean,
        alsoNotifyWhatsApp: Boolean = false,
        whatsappRecipientE164: String? = null
    ): Result<String?> {
        var path = "supportTickets/${ticket.id}/messages"
        return try {
            val uid = requireUid()
            val clean = body.trim().take(3000)
            require(clean.isNotEmpty()) { "Message cannot be empty." }
            val msgRef = db.collection("supportTickets").document(ticket.id)
                .collection("messages").document()
            path = msgRef.path
            val senderRole = if (asSupportStaff) "SUPPORT_AGENT" else "USER"
            msgRef.set(
                mapOf(
                    "ticketId" to ticket.id,
                    "senderId" to uid,
                    "senderName" to senderName.trim().take(60).ifBlank { if (asSupportStaff) "DL Support Agent" else "User" },
                    "senderRole" to senderRole,
                    "body" to clean,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val nextStatus = if (asSupportStaff) {
                SupportTicketStatus.WAITING_ON_USER.name
            } else {
                SupportTicketStatus.WAITING_ON_SUPPORT.name
            }
            db.collection("supportTickets").document(ticket.id).update(
                mapOf(
                    "status" to nextStatus,
                    "lastMessageText" to clean.take(240),
                    "lastMessageAtEpochMs" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            var waNotice: String? = null
            if (asSupportStaff && alsoNotifyWhatsApp && ticket.whatsappOptIn && !whatsappRecipientE164.isNullOrBlank()) {
                waNotice = when (val res = BackendGatewayClient.sendWhatsAppSupportNotification(
                    ticketId = ticket.id,
                    toPhoneE164 = whatsappRecipientE164,
                    textBody = "[DL Chat Support #${ticket.id.take(6)}] $clean"
                )) {
                    is BackendGatewayResult.Success -> "WhatsApp notification delivered."
                    is BackendGatewayResult.NotConfigured -> "Reply saved in-app (WhatsApp Cloud API backend not configured)."
                    is BackendGatewayResult.Failure -> "Reply saved in-app (WhatsApp: ${res.message})."
                }
            }
            Result.success(waNotice)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addInternalNote(
        ticketId: String,
        authorName: String,
        note: String,
        staffRole: DlStaffRole
    ): Result<Unit> {
        if (!staffRole.canOpenStaffConsole) return Result.failure(SecurityException("Support staff role required."))
        var path = "supportTickets/$ticketId/internalNotes"
        return try {
            val uid = requireUid()
            val clean = note.trim().take(2000)
            require(clean.isNotEmpty()) { "Note cannot be empty." }
            val ref = db.collection("supportTickets").document(ticketId)
                .collection("internalNotes").document()
            path = ref.path
            ref.set(
                mapOf(
                    "ticketId" to ticketId,
                    "authorUid" to uid,
                    "authorName" to authorName.trim().take(60).ifBlank { "Support Staff" },
                    "note" to clean,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTicketMetadata(
        ticketId: String,
        status: SupportTicketStatus? = null,
        priority: SupportPriority? = null,
        assignedAgentUid: String? = null,
        assignedAgentName: String? = null
    ): Result<Unit> {
        val path = "supportTickets/$ticketId"
        return try {
            requireUid()
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (status != null) updates["status"] = status.name
            if (priority != null) updates["priority"] = priority.name
            if (assignedAgentUid != null) updates["assignedAgentUid"] = assignedAgentUid
            if (assignedAgentName != null) updates["assignedAgentName"] = assignedAgentName
            db.collection("supportTickets").document(ticketId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertHelpArticle(
        articleId: String?,
        category: SupportCategory,
        title: String,
        summary: String,
        bodyMarkdown: String,
        published: Boolean,
        staffRole: DlStaffRole
    ): Result<Unit> {
        if (!staffRole.canOpenStaffConsole) return Result.failure(SecurityException("Support staff role required."))
        var path = "helpArticles"
        return try {
            val uid = requireUid()
            val cleanTitle = title.trim().take(120)
            require(cleanTitle.length >= 3) { "Article title must be at least 3 characters." }
            val ref = if (articleId.isNullOrBlank()) db.collection("helpArticles").document()
            else db.collection("helpArticles").document(articleId)
            path = ref.path
            val snap = ref.get().await()
            ref.set(
                mapOf(
                    "category" to category.name,
                    "title" to cleanTitle,
                    "summary" to summary.trim().take(240),
                    "bodyMarkdown" to bodyMarkdown.trim().take(5000),
                    "keywords" to cleanTitle.lowercase().split(Regex("\\s+")).filter { it.length >= 3 },
                    "published" to published,
                    "sortOrder" to 1,
                    "helpfulCount" to (snap.getLong("helpfulCount")?.toInt() ?: 0),
                    "updatedByUid" to uid,
                    "createdAt" to (snap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertSupportAgent(
        userId: String,
        displayName: String,
        specialty: String,
        availabilityStatus: String,
        actorRole: DlStaffRole
    ): Result<Unit> {
        if (!actorRole.canManageRolesAndPlatform) return Result.failure(SecurityException("Admin permission required."))
        val path = "supportAgents/$userId"
        return try {
            val ref = db.collection("supportAgents").document(userId)
            val snap = ref.get().await()
            ref.set(
                mapOf(
                    "userId" to userId,
                    "displayName" to displayName.trim().take(60),
                    "specialty" to specialty.trim().take(60),
                    "availabilityStatus" to availabilityStatus,
                    "activeTicketCount" to (snap.getLong("activeTicketCount")?.toInt() ?: 0),
                    "createdAt" to (snap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    companion object {
        val builtInHelpArticles: List<HelpArticle> = listOf(
            HelpArticle(
                id = "help_otp_srilanka",
                category = SupportCategory.OTP_LOGIN.name,
                title = "Verifying Sri Lanka (+94) & International Phone Numbers",
                summary = "How to enter your mobile number in E.164 format and troubleshoot delayed OTP SMS codes.",
                bodyMarkdown = "Select Sri Lanka (+94) or your country in the phone selector and enter your mobile number (leading zeros like 077... are normalized automatically to +9477...). If the 6-digit SMS code does not arrive within 60 seconds, check signal coverage, disable SMS spam filtering, and tap Resend code once the countdown finishes.",
                keywords = listOf("sri lanka", "+94", "otp", "sms", "verification", "login"),
                published = true,
                sortOrder = 1
            ),
            HelpArticle(
                id = "help_link_phone",
                category = SupportCategory.ACCOUNT_PHONE.name,
                title = "Linking or Changing Your Verified Phone Number",
                summary = "Keep your existing chats, contacts, and linked devices when adding or updating a phone number.",
                bodyMarkdown = "Open Settings > Account > Phone & Security to link a phone number to your existing DL Chat or Death Legion account. Linking attaches your verified phone credential to your current UID so none of your messages or linked devices are lost.",
                keywords = listOf("link", "change", "phone", "uid", "migration"),
                published = true,
                sortOrder = 2
            ),
            HelpArticle(
                id = "help_restrictions_appeals",
                category = SupportCategory.RESTRICTIONS_APPEALS.name,
                title = "Understanding Account Restrictions & Submitting Appeals",
                summary = "Feature-specific limits, expiration timers, and independent moderator review.",
                bodyMarkdown = "DL Chat applies targeted feature restrictions (such as temporary DM or media limits) before severe suspensions whenever possible. You can always view active restrictions, exact expiration times, and submit up to 3 appeals from Settings > Account Status & Appeals. Every appeal is reviewed by a moderator who did not issue the original enforcement.",
                keywords = listOf("ban", "restriction", "appeal", "suspended", "warning"),
                published = true,
                sortOrder = 3
            ),
            HelpArticle(
                id = "help_linked_devices",
                category = SupportCategory.LINKED_DEVICES.name,
                title = "Linking Desktop, Web & Tablet Devices Securely",
                summary = "Short-lived QR pairing tokens and revoking active sessions.",
                bodyMarkdown = "In Settings > Linked Devices, tap Link a New Device to generate a single-use short-lived QR pairing token. Approve the pairing from an already authenticated phone. Revoking a device invalidates its session immediately.",
                keywords = listOf("qr", "linked", "device", "desktop", "revoke"),
                published = true,
                sortOrder = 4
            ),
            HelpArticle(
                id = "help_whatsapp_notices",
                category = SupportCategory.WHATSAPP_BUSINESS.name,
                title = "Official WhatsApp Business Support & Opt-In",
                summary = "When DL Chat contacts you on WhatsApp and how to opt out.",
                bodyMarkdown = "DL Chat uses the official WhatsApp Business Cloud API exclusively when enabled by Death Legion administrators and opted into by you in Settings > Account > Phone & Security. You can opt out anytime inside DL Chat or by replying STOP on WhatsApp.",
                keywords = listOf("whatsapp", "business", "opt-in", "support", "meta"),
                published = true,
                sortOrder = 5
            )
        )

        val draftPolicyDocuments: List<LegalPolicyDocument> = listOf(
            LegalPolicyDocument(
                id = "community_guidelines",
                title = "Community Guidelines",
                versionTag = "DRAFT v6.0",
                isDraft = true,
                lastUpdatedLabel = "Draft Policy — Subject to Legal Review",
                sections = listOf(
                    "1. Respect & Anti-Harassment" to "Do not threaten, dox, stalk, or target individuals or communities with coordinated harassment or hate speech.",
                    "2. No Spam, Scams or Phishing" to "Automated bulk messaging, credential phishing, fake giveaways, impersonation of Death Legion staff, and malicious links are prohibited.",
                    "3. Safe Content & Lawful Conduct" to "Do not share illegal content, malware, or non-consensual private media in direct chats, groups, channels, or status stories.",
                    "4. Progressive Enforcement" to "DL Chat prioritizes formal warnings and scoped feature restrictions over immediate permanent bans, except in severe safety cases reviewed by human moderators."
                )
            ),
            LegalPolicyDocument(
                id = "terms_of_service",
                title = "Terms of Service",
                versionTag = "DRAFT v6.0",
                isDraft = true,
                lastUpdatedLabel = "Draft Policy — Subject to Legal Review",
                sections = listOf(
                    "1. Eligibility & Account Security" to "You are responsible for keeping your verified phone number, single sign-in credentials, and linked devices secure.",
                    "2. Acceptable Use" to "You agree not to circumvent anti-spam rate limits, reverse engineer private endpoints, or abuse support channels.",
                    "3. Service Availability" to "Phone OTP SMS, E2EE messaging, and optional WhatsApp Business notifications depend on carrier and third-party network availability."
                )
            ),
            LegalPolicyDocument(
                id = "privacy_policy",
                title = "Privacy Policy",
                versionTag = "DRAFT v6.0",
                isDraft = true,
                lastUpdatedLabel = "Draft Policy — Subject to Legal Review",
                sections = listOf(
                    "1. Data We Collect" to "DL Chat stores your profile details, verified phone number (E.164), linked device metadata, and encrypted message payloads strictly needed to operate messaging.",
                    "2. Moderation Evidence Privacy" to "When a user reports a specific message, only the reported snippet and identifiers are shared with human moderators—unrelated private chats remain inaccessible.",
                    "3. WhatsApp Business Opt-In" to "Optional support notifications sent over WhatsApp Business Cloud API require explicit user opt-in and can be disabled anytime."
                )
            ),
            LegalPolicyDocument(
                id = "enforcement_policy",
                title = "Ban, Enforcement & Appeal Policy",
                versionTag = "DRAFT v6.0",
                isDraft = true,
                lastUpdatedLabel = "Draft Policy — Subject to Legal Review",
                sections = listOf(
                    "1. Human-in-the-Loop Guarantee" to "Automated spam and triage heuristics may trigger temporary rate limits or moderation queues, but permanent suspensions require human Senior Moderator or Admin confirmation.",
                    "2. Independent Appeals" to "Users may appeal eligible restrictions up to 3 times. Appeals cannot be adjudicated by the moderator who issued the original enforcement."
                )
            ),
            LegalPolicyDocument(
                id = "support_retention",
                title = "Support & Data Retention Policy",
                versionTag = "DRAFT v6.0",
                isDraft = true,
                lastUpdatedLabel = "Draft Policy — Subject to Legal Review",
                sections = listOf(
                    "1. Support Tickets" to "Support conversations are visible to you and authorized DL Chat Support Agents. Internal triage notes are restricted strictly to staff.",
                    "2. Audit & Security Logs" to "Immutable moderation and authentication security events are retained for platform integrity and abuse investigation."
                )
            )
        )
    }
}
