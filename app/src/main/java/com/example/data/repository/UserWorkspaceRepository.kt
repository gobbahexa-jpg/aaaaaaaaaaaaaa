package com.example.data.repository

import com.example.core.IdentityKeyMaterial
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.BackupRecord
import com.example.data.model.ChatFolder
import com.example.data.model.ContactEntry
import com.example.data.model.ContactRequest
import com.example.data.model.DeviceSession
import com.example.data.model.ModerationReport
import com.example.data.model.SavedMessage
import com.example.data.model.UserProfile
import com.example.data.model.UserSetting
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserWorkspaceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeMyProfile(uid: String): Flow<UserProfile?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { snap -> snap.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeDiscoverableUsers(): Flow<List<UserProfile>> =
        db.collection("users")
            .whereEqualTo("discoverable", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.displayName.lowercase() }
            }

    fun observeSettings(uid: String): Flow<UserSetting?> =
        db.collection("users").document(uid)
            .collection("settings").document("preferences")
            .snapshots()
            .map { snap -> snap.toObject(UserSetting::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeDevices(uid: String): Flow<List<DeviceSession>> =
        db.collection("users").document(uid)
            .collection("devices")
            .snapshots()
            .map { snap -> snap.toObjects(DeviceSession::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeContacts(uid: String): Flow<List<ContactEntry>> =
        db.collection("users").document(uid)
            .collection("contacts")
            .snapshots()
            .map { snap ->
                snap.toObjects(ContactEntry::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.favorite }
            }

    fun observeContactRequests(uid: String): Flow<List<ContactRequest>> {
        val incoming = db.collection("contact_requests")
            .whereEqualTo("receiverId", uid)
            .snapshots()
            .map { it.toObjects(ContactRequest::class.java, ServerTimestampBehavior.ESTIMATE) }
        val outgoing = db.collection("contact_requests")
            .whereEqualTo("senderId", uid)
            .snapshots()
            .map { it.toObjects(ContactRequest::class.java, ServerTimestampBehavior.ESTIMATE) }
        return combine(incoming, outgoing) { inc, out ->
            (inc + out).distinctBy { it.id }
                .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
        }
    }

    fun observeFolders(uid: String): Flow<List<ChatFolder>> =
        db.collection("users").document(uid)
            .collection("folders")
            .snapshots()
            .map { snap -> snap.toObjects(ChatFolder::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeSavedMessages(uid: String): Flow<List<SavedMessage>> =
        db.collection("users").document(uid)
            .collection("saved_messages")
            .snapshots()
            .map { snap ->
                snap.toObjects(SavedMessage::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeBackups(uid: String): Flow<List<BackupRecord>> =
        db.collection("users").document(uid)
            .collection("backups")
            .snapshots()
            .map { snap ->
                snap.toObjects(BackupRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeReports(uid: String, adminMode: Boolean): Flow<List<ModerationReport>> {
        val query = if (adminMode) {
            db.collection("reports")
        } else {
            db.collection("reports").whereEqualTo("reporterId", uid)
        }
        return query.snapshots().map { snap ->
            snap.toObjects(ModerationReport::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }
    }

    suspend fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarColor: String,
        photoDataBase64: String?,
        onlineStatus: String,
        showLastSeen: Boolean,
        showOnline: Boolean,
        readReceipts: Boolean,
        discoverable: Boolean,
        twoFactorEnabled: Boolean = false,
        profilePhotoVisibility: String = "EVERYONE",
        statusMessage: String? = null
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val cleanUsername = username.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_").take(30)
            require(cleanUsername.length >= 3) { "Username must be at least 3 characters." }
            val cleanName = displayName.trim().take(60)
            require(cleanName.isNotEmpty()) { "Display name cannot be empty." }

            val updates = mutableMapOf<String, Any>(
                "username" to cleanUsername,
                "displayName" to cleanName,
                "bio" to bio.trim().take(240),
                "avatarColor" to avatarColor.take(16),
                "onlineStatus" to onlineStatus,
                "showLastSeen" to showLastSeen,
                "showOnline" to showOnline,
                "readReceipts" to readReceipts,
                "discoverable" to discoverable,
                "twoFactorEnabled" to twoFactorEnabled,
                "profilePhotoVisibility" to profilePhotoVisibility,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (statusMessage != null) {
                updates["statusMessage"] = statusMessage.trim().take(120)
            }
            if (!photoDataBase64.isNullOrBlank() && photoDataBase64.length <= 240_000) {
                updates["photoDataBase64"] = photoDataBase64
            }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun linkDeathLegionProfile(
        info: com.example.core.DeathLegionUserInfo
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "legionUserId" to info.legionUserId.take(128),
                    "legionUsername" to info.username.take(80),
                    "legionMemberNumber" to info.memberNumber.take(64),
                    "legionVerificationStatus" to info.verificationStatus.take(40),
                    "legionPrimaryUnit" to info.primaryUnitName.take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val privateRef = db.collection("users").document(uid).collection("private").document("info")
            if (privateRef.get().await().exists()) {
                privateRef.update(
                    mapOf(
                        "legionConnected" to true,
                        "legionMemberNumber" to info.memberNumber.take(64),
                        "legionUnit" to info.primaryUnitName.take(120),
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

    suspend fun unlinkDeathLegionProfile(): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "legionUserId" to "",
                    "legionUsername" to "",
                    "legionMemberNumber" to "",
                    "legionVerificationStatus" to "",
                    "legionPrimaryUnit" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val privateRef = db.collection("users").document(uid).collection("private").document("info")
            if (privateRef.get().await().exists()) {
                privateRef.update(
                    mapOf(
                        "legionConnected" to false,
                        "legionMemberNumber" to "",
                        "legionUnit" to "",
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

    suspend fun updateIdentityKey(keyMaterial: IdentityKeyMaterial): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val updates = mutableMapOf<String, Any>(
                "publicKeyFingerprint" to keyMaterial.fingerprint,
                "keyVersion" to keyMaterial.keyVersion,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (keyMaterial.publicKeyBase64.isNotBlank()) {
                updates["publicKeyBase64"] = keyMaterial.publicKeyBase64
            }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSettings(setting: UserSetting): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/settings/preferences"
            db.collection("users").document(uid)
                .collection("settings").document("preferences")
                .update(
                    mapOf(
                        "themeMode" to setting.themeMode,
                        "accentColorHex" to setting.accentColorHex,
                        "bubbleStyle" to setting.bubbleStyle,
                        "fontScale" to setting.fontScale.coerceIn(0.85, 1.35),
                        "wallpaperId" to setting.wallpaperId,
                        "notificationsEnabled" to setting.notificationsEnabled,
                        "soundEnabled" to setting.soundEnabled,
                        "dndEnabled" to setting.dndEnabled,
                        "aiEnabled" to setting.aiEnabled,
                        "autoDownloadMedia" to setting.autoDownloadMedia,
                        "appLockEnabled" to setting.appLockEnabled,
                        "autoLockSeconds" to setting.autoLockSeconds.coerceIn(0, 86400),
                        "biometricLockEnabled" to setting.biometricLockEnabled,
                        "screenshotProtectionEnabled" to setting.screenshotProtectionEnabled,
                        "hideNotificationContent" to setting.hideNotificationContent,
                        "autoSyncOfflineQueue" to setting.autoSyncOfflineQueue,
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

    suspend fun upsertContact(
        contactUid: String,
        displayName: String,
        username: String,
        favorite: Boolean,
        blocked: Boolean
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            require(contactUid.isNotBlank() && contactUid != uid) { "Invalid contact UID." }
            path = "users/$uid/contacts/$contactUid"
            val docRef = db.collection("users").document(uid).collection("contacts").document(contactUid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "displayName" to displayName.trim().take(60).ifBlank { "Operator" },
                        "username" to username.trim().take(30).ifBlank { "user" },
                        "favorite" to favorite,
                        "blocked" to blocked,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "ownerUid" to uid,
                        "contactUid" to contactUid,
                        "displayName" to displayName.trim().take(60).ifBlank { "Operator" },
                        "username" to username.trim().take(30).ifBlank { "user" },
                        "favorite" to favorite,
                        "blocked" to blocked,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun removeContact(contactUid: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/contacts/$contactUid"
            db.collection("users").document(uid).collection("contacts").document(contactUid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun sendContactRequest(
        me: UserProfile,
        target: UserProfile,
        message: String = "Let's connect on DL Chat"
    ): Result<Unit> {
        var path = "contact_requests"
        return try {
            val uid = requireUserId()
            require(target.userId.isNotBlank() && target.userId != uid) { "Cannot request yourself." }
            val reqId = "req_${uid.take(20)}_${target.userId.take(20)}"
            val docRef = db.collection("contact_requests").document(reqId)
            path = docRef.path
            docRef.set(
                mapOf(
                    "senderId" to uid,
                    "senderName" to me.displayName.trim().take(60).ifBlank { "Operator" },
                    "senderUsername" to me.username.trim().take(30).ifBlank { "operator" },
                    "receiverId" to target.userId,
                    "receiverName" to target.displayName.trim().take(60).ifBlank { target.username },
                    "status" to "PENDING",
                    "message" to message.trim().take(200),
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

    suspend fun respondContactRequest(request: ContactRequest, accept: Boolean): Result<Unit> {
        val path = "contact_requests/${request.id}"
        return try {
            val uid = requireUserId()
            val nextStatus = if (accept) "ACCEPTED" else "DECLINED"
            db.collection("contact_requests").document(request.id).update(
                mapOf(
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (accept && request.receiverId == uid) {
                upsertContact(
                    contactUid = request.senderId,
                    displayName = request.senderName,
                    username = request.senderUsername,
                    favorite = false,
                    blocked = false
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createFolder(
        name: String,
        filterType: String,
        includeChatIds: List<String> = emptyList(),
        excludeChatIds: List<String> = emptyList(),
        iconName: String = "Folder",
        sortOrder: Int = 0
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val clean = name.trim().take(30)
            require(clean.isNotEmpty()) { "Folder name required." }
            val docRef = db.collection("users").document(uid).collection("folders").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "ownerUid" to uid,
                    "name" to clean,
                    "filterType" to filterType,
                    "iconName" to iconName.take(40),
                    "sortOrder" to sortOrder.coerceIn(0, 1000),
                    "includeChatIds" to includeChatIds.take(50),
                    "excludeChatIds" to excludeChatIds.take(50),
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

    suspend fun deleteFolder(folderId: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/folders/$folderId"
            db.collection("users").document(uid).collection("folders").document(folderId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun savePersonalNoteOrMessage(
        text: String,
        sourceChatName: String,
        sourceMessageId: String? = null,
        noteCategory: String = "SAVED"
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val clean = text.trim().take(4000)
            require(clean.isNotEmpty()) { "Note text cannot be empty." }
            val docRef = db.collection("users").document(uid).collection("saved_messages").document()
            path = docRef.path
            val payload = mapOf(
                "ownerUid" to uid,
                "text" to clean,
                "sourceChatName" to sourceChatName.take(80),
                "sourceMessageId" to sourceMessageId?.take(128),
                "noteCategory" to noteCategory.take(30),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSavedMessage(savedId: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/saved_messages/$savedId"
            db.collection("users").document(uid).collection("saved_messages").document(savedId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun recordBackup(fileName: String, itemCount: Int, sizeBytes: Long): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("backups").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "ownerUid" to uid,
                    "fileName" to fileName.take(160),
                    "itemCount" to itemCount.coerceAtLeast(0),
                    "sizeBytes" to sizeBytes.coerceAtLeast(0L),
                    "encryptionAlg" to "PBKDF2+AES-256-GCM",
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

    suspend fun revokeDevice(deviceId: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/devices/$deviceId"
            db.collection("users").document(uid).collection("devices").document(deviceId)
                .update(mapOf("revoked" to true, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(targetUid: String, targetType: String, reason: String): Result<Unit> {
        var path = "reports"
        return try {
            val uid = requireUserId()
            val cleanReason = reason.trim().take(500)
            require(cleanReason.length >= 3) { "Please provide a brief reason (3+ chars)." }
            val docRef = db.collection("reports").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "reporterId" to uid,
                    "targetUid" to targetUid.take(128),
                    "targetType" to targetType,
                    "reason" to cleanReason,
                    "status" to "OPEN",
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

    suspend fun adminUpdateReportStatus(reportId: String, status: String): Result<Unit> {
        val path = "reports/$reportId"
        return try {
            requireUserId()
            db.collection("reports").document(reportId).update(
                mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminSetUserSuspended(targetUid: String, suspended: Boolean): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            requireUserId()
            db.collection("users").document(targetUid).update(
                mapOf("suspended" to suspended, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
