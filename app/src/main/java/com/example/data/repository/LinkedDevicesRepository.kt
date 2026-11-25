package com.example.data.repository

import com.example.core.OperationType
import com.example.core.QrCodeUtils
import com.example.core.handleFirestoreError
import com.example.data.model.AccountSessionRecord
import com.example.data.model.ChatDraft
import com.example.data.model.ChatExportRecord
import com.example.data.model.DeviceKeyBundle
import com.example.data.model.DeviceNotification
import com.example.data.model.DevicePairingSession
import com.example.data.model.PrivateChatSetting
import com.example.data.model.SyncEvent
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
import java.util.UUID

data class CreatedPairingChallenge(
    val sessionId: String,
    val rawToken: String,
    val tokenHash: String,
    val nonce: String,
    val qrUri: String,
    val expiresAtEpochMs: Long
)

class LinkedDevicesRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observePairingSessions(uid: String): Flow<List<DevicePairingSession>> =
        db.collection("users").document(uid).collection("devicePairingSessions")
            .snapshots()
            .map { snap ->
                snap.toObjects(DevicePairingSession::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeDeviceNotifications(uid: String): Flow<List<DeviceNotification>> =
        db.collection("users").document(uid).collection("deviceNotifications")
            .snapshots()
            .map { snap ->
                snap.toObjects(DeviceNotification::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeSyncEvents(uid: String): Flow<List<SyncEvent>> =
        db.collection("users").document(uid).collection("syncEvents")
            .limit(80)
            .snapshots()
            .map { snap ->
                snap.toObjects(SyncEvent::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.versionEpochMs }
            }

    fun observeDeviceKeyBundles(uid: String): Flow<List<DeviceKeyBundle>> =
        db.collection("users").document(uid).collection("deviceKeyBundles")
            .snapshots()
            .map { snap -> snap.toObjects(DeviceKeyBundle::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeAccountSessions(uid: String): Flow<List<AccountSessionRecord>> =
        db.collection("users").document(uid).collection("accountSessions")
            .snapshots()
            .map { snap -> snap.toObjects(AccountSessionRecord::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observePrivateChatSettings(uid: String): Flow<List<PrivateChatSetting>> =
        db.collection("users").document(uid).collection("privateChatSettings")
            .snapshots()
            .map { snap -> snap.toObjects(PrivateChatSetting::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeChatDrafts(uid: String): Flow<List<ChatDraft>> =
        db.collection("users").document(uid).collection("chatDrafts")
            .snapshots()
            .map { snap -> snap.toObjects(ChatDraft::class.java, ServerTimestampBehavior.ESTIMATE) }

    fun observeChatExports(uid: String): Flow<List<ChatExportRecord>> =
        db.collection("users").document(uid).collection("chatExports")
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatExportRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    suspend fun createPairingChallenge(
        targetDeviceName: String,
        targetDeviceType: String,
        targetOs: String = "",
        targetBrowser: String = "",
        ttlSeconds: Long = 180L
    ): Result<CreatedPairingChallenge> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("devicePairingSessions").document()
            path = docRef.path
            val rawToken = QrCodeUtils.generateSecureToken(24)
            val tokenHash = QrCodeUtils.sha256Hex(rawToken)
            val nonce = QrCodeUtils.generateSecureToken(12)
            val expiresAt = System.currentTimeMillis() + ttlSeconds.coerceIn(30L, 600L) * 1000L
            val safeType = targetDeviceType.takeIf { it in listOf("ANDROID", "TABLET", "DESKTOP", "WEB") } ?: "DESKTOP"
            val payload = mapOf(
                "ownerUid" to uid,
                "tokenHash" to tokenHash,
                "nonce" to nonce,
                "targetDeviceName" to targetDeviceName.trim().take(80).ifBlank { "Linked $safeType" },
                "targetDeviceType" to safeType,
                "targetOs" to targetOs.trim().take(80),
                "targetBrowser" to targetBrowser.trim().take(80),
                "status" to "PENDING",
                "expiresAtEpochMs" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val qrUri = QrCodeUtils.buildPairingUri(uid, docRef.id, rawToken, nonce, expiresAt)
            Result.success(CreatedPairingChallenge(docRef.id, rawToken, tokenHash, nonce, qrUri, expiresAt))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun confirmPairingAndRegisterDevice(
        sessionId: String,
        rawToken: String,
        deviceNameOverride: String? = null
    ): Result<String> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val sessionRef = db.collection("users").document(uid)
                .collection("devicePairingSessions").document(sessionId)
            path = sessionRef.path
            val snap = sessionRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalArgumentException("Pairing session not found."))
            }
            val status = snap.getString("status").orEmpty()
            val expiresAt = snap.getLong("expiresAtEpochMs") ?: 0L
            val expectedHash = snap.getString("tokenHash").orEmpty()
            val candidateHash = QrCodeUtils.sha256Hex(rawToken.trim())

            if (status != "PENDING") {
                return Result.failure(IllegalStateException("Pairing token already $status (single-use enforced)."))
            }
            if (System.currentTimeMillis() > expiresAt) {
                sessionRef.update(mapOf("status" to "EXPIRED", "updatedAt" to FieldValue.serverTimestamp())).await()
                return Result.failure(IllegalStateException("QR pairing token expired."))
            }
            if (expectedHash != candidateHash) {
                return Result.failure(SecurityException("Pairing token signature mismatch."))
            }

            // Step 1: Authenticated device marks pairing APPROVED
            sessionRef.update(
                mapOf(
                    "status" to "APPROVED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Step 2: Register linked device referencing the APPROVED pairing session & matching tokenHash
            val deviceType = snap.getString("targetDeviceType") ?: "DESKTOP"
            val deviceName = deviceNameOverride?.trim()?.take(80)?.ifBlank { null }
                ?: snap.getString("targetDeviceName").orEmpty().ifBlank { "Linked $deviceType" }
            val osName = snap.getString("targetOs").orEmpty().ifBlank { deviceType }
            val browserName = snap.getString("targetBrowser").orEmpty()
            val deviceId = "linked_${deviceType.lowercase()}_${UUID.randomUUID().toString().take(10)}"
            val deviceRef = db.collection("users").document(uid).collection("devices").document(deviceId)
            deviceRef.set(
                mapOf(
                    "userId" to uid,
                    "deviceName" to deviceName,
                    "platform" to osName.take(40),
                    "deviceType" to deviceType,
                    "osName" to osName,
                    "browserName" to browserName,
                    "connectionStatus" to "ONLINE",
                    "pairingSessionId" to sessionId,
                    "pairingTokenHash" to expectedHash,
                    "tokenRotationCount" to 1,
                    "revoked" to false,
                    "firstLinkedAt" to FieldValue.serverTimestamp(),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Step 3: Consume single-use pairing token so replay is impossible
            sessionRef.update(
                mapOf(
                    "status" to "CONSUMED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            postDeviceNotification(
                targetDeviceId = deviceId,
                title = "New $deviceType linked",
                body = "$deviceName was authorized via single-use QR pairing.",
                category = "NEW_DEVICE"
            )

            Result.success(deviceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun cancelPairingSession(sessionId: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/devicePairingSessions/$sessionId"
            db.collection("users").document(uid)
                .collection("devicePairingSessions").document(sessionId)
                .update(mapOf("status" to "CANCELLED", "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameDevice(deviceId: String, newName: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val clean = newName.trim().take(80)
            require(clean.isNotEmpty()) { "Device name cannot be empty." }
            path = "users/$uid/devices/$deviceId"
            db.collection("users").document(uid).collection("devices").document(deviceId)
                .update(mapOf("deviceName" to clean, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun rotateDeviceSessionToken(deviceId: String, currentRotationCount: Int): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/devices/$deviceId"
            val rotatedHash = QrCodeUtils.sha256Hex(QrCodeUtils.generateSecureToken(24))
            db.collection("users").document(uid).collection("devices").document(deviceId)
                .update(
                    mapOf(
                        "pairingTokenHash" to rotatedHash,
                        "tokenRotationCount" to (currentRotationCount + 1),
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            postDeviceNotification(
                targetDeviceId = deviceId,
                title = "Device credential rotated",
                body = "Session key rotated (#${currentRotationCount + 1}) for linked device.",
                category = "SECURITY"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeAllOtherDevices(currentDeviceId: String): Result<Int> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/devices"
            val snap = db.collection("users").document(uid).collection("devices").get().await()
            var count = 0
            for (doc in snap.documents) {
                if (doc.id != currentDeviceId && doc.getBoolean("revoked") != true) {
                    doc.reference.update(
                        mapOf(
                            "revoked" to true,
                            "connectionStatus" to "REVOKED",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    count++
                }
            }
            if (count > 0) {
                postDeviceNotification(
                    targetDeviceId = currentDeviceId,
                    title = "Logged out $count other session(s)",
                    body = "All other linked devices had their access revoked.",
                    category = "SECURITY"
                )
            }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun postDeviceNotification(
        targetDeviceId: String,
        title: String,
        body: String,
        category: String = "SECURITY"
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("deviceNotifications").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "targetDeviceId" to targetDeviceId.take(128),
                    "title" to title.take(120),
                    "body" to body.take(500),
                    "category" to category,
                    "read" to false,
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

    suspend fun markNotificationRead(notifId: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/deviceNotifications/$notifId"
            db.collection("users").document(uid).collection("deviceNotifications").document(notifId)
                .update(mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun publishSyncEvent(
        entityType: String,
        entityId: String,
        operation: String,
        deviceId: String,
        payloadSummary: String
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("syncEvents").document()
            path = ref.path
            ref.set(
                mapOf(
                    "ownerUid" to uid,
                    "entityType" to entityType,
                    "entityId" to entityId.take(128),
                    "operation" to operation,
                    "versionEpochMs" to System.currentTimeMillis(),
                    "deviceId" to deviceId.take(128),
                    "payloadSummary" to payloadSummary.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun upsertPrivateChatSetting(
        chatId: String,
        hidden: Boolean,
        locked: Boolean,
        pinnedSortOrder: Int = 0
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("privateChatSettings").document(chatId)
            path = ref.path
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "hidden" to hidden,
                        "locked" to locked,
                        "pinnedSortOrder" to pinnedSortOrder.coerceIn(0, 10000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "ownerUid" to uid,
                        "chatId" to chatId.take(128),
                        "hidden" to hidden,
                        "locked" to locked,
                        "pinnedSortOrder" to pinnedSortOrder.coerceIn(0, 10000),
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

    suspend fun saveChatDraft(chatId: String, draftText: String, replyToId: String? = null): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("chatDrafts").document(chatId)
            path = ref.path
            val clean = draftText.take(4000)
            if (clean.isBlank()) {
                if (ref.get().await().exists()) ref.delete().await()
                return Result.success(Unit)
            }
            val snap = ref.get().await()
            val nowMs = System.currentTimeMillis()
            if (snap.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "draftText" to clean,
                    "updatedAtEpochMs" to nowMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (replyToId != null) updates["replyToId"] = replyToId.take(128)
                ref.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "ownerUid" to uid,
                    "chatId" to chatId.take(128),
                    "draftText" to clean,
                    "updatedAtEpochMs" to nowMs,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (replyToId != null) payload["replyToId"] = replyToId.take(128)
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun recordChatExport(
        chatId: String,
        chatTitle: String,
        format: String,
        itemCount: Int,
        sizeBytes: Long
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("chatExports").document()
            path = ref.path
            ref.set(
                mapOf(
                    "ownerUid" to uid,
                    "chatId" to chatId.take(128),
                    "chatTitle" to chatTitle.take(80).ifBlank { "Chat" },
                    "format" to format,
                    "itemCount" to itemCount.coerceAtLeast(0),
                    "sizeBytes" to sizeBytes.coerceAtLeast(0L),
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
}
