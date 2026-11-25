package com.example.data.repository

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.ChatRoom
import com.example.data.model.ChatTopic
import com.example.data.model.GroupEvent
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

class GroupForumRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeTopics(chatId: String): Flow<List<ChatTopic>> =
        db.collection("chats").document(chatId).collection("topics")
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatTopic::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }

    fun observeGroupEvents(chatId: String): Flow<List<GroupEvent>> =
        db.collection("chats").document(chatId).collection("events")
            .snapshots()
            .map { snap ->
                snap.toObjects(GroupEvent::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.startsAtEpochMs }
            }

    suspend fun createTopic(chat: ChatRoom, title: String, iconEmoji: String = "💬"): Result<String> {
        var path = "chats/${chat.id}/topics"
        return try {
            val uid = requireUserId()
            val clean = title.trim().take(80)
            require(clean.isNotEmpty()) { "Topic title cannot be empty." }
            val ref = db.collection("chats").document(chat.id).collection("topics").document()
            path = ref.path
            ref.set(
                mapOf(
                    "chatId" to chat.id,
                    "creatorId" to uid,
                    "title" to clean,
                    "iconEmoji" to iconEmoji.take(16).ifBlank { "💬" },
                    "closed" to false,
                    "messageCount" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (!chat.topicsEnabled && uid in chat.adminUids) {
                db.collection("chats").document(chat.id).update(
                    mapOf("topicsEnabled" to true, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createGroupEvent(
        chatId: String,
        title: String,
        description: String,
        startsAtEpochMs: Long
    ): Result<String> {
        var path = "chats/$chatId/events"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(100)
            require(cleanTitle.isNotEmpty()) { "Event title cannot be empty." }
            val ref = db.collection("chats").document(chatId).collection("events").document()
            path = ref.path
            ref.set(
                mapOf(
                    "chatId" to chatId,
                    "creatorId" to uid,
                    "title" to cleanTitle,
                    "description" to description.trim().take(500),
                    "startsAtEpochMs" to startsAtEpochMs.coerceAtLeast(System.currentTimeMillis() + 60_000L),
                    "rsvpUids" to listOf(uid),
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

    suspend fun toggleEventRsvp(chatId: String, eventId: String): Result<Unit> {
        val path = "chats/$chatId/events/$eventId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("chats").document(chatId).collection("events").document(eventId)
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                val current = (snap.get("rsvpUids") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val next = if (uid in current) current - uid else (current + uid).distinct().take(100)
                tx.update(ref, mapOf("rsvpUids" to next, "updatedAt" to FieldValue.serverTimestamp()))
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGroupModerationV3(
        chat: ChatRoom,
        slowModeSeconds: Int? = null,
        toggleRestrictedUid: String? = null,
        antiSpamEnabled: Boolean? = null,
        topicsEnabled: Boolean? = null,
        linkedGroupId: String? = null,
        inviteExpiresAtEpochMs: Long? = null,
        incrementChannelView: Boolean = false
    ): Result<Unit> {
        val path = "chats/${chat.id}"
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            val nextLog = chat.adminActivityLog.toMutableList()
            if (slowModeSeconds != null) {
                val safe = slowModeSeconds.coerceIn(0, 3600)
                updates["slowModeSeconds"] = safe
                nextLog.add("Slow mode set to ${safe}s")
            }
            if (toggleRestrictedUid != null && toggleRestrictedUid != chat.ownerId) {
                val next = if (toggleRestrictedUid in chat.restrictedUids) {
                    nextLog.add("Unrestricted ${chat.participantNames[toggleRestrictedUid] ?: toggleRestrictedUid.take(6)}")
                    chat.restrictedUids - toggleRestrictedUid
                } else {
                    nextLog.add("Muted/restricted ${chat.participantNames[toggleRestrictedUid] ?: toggleRestrictedUid.take(6)}")
                    (chat.restrictedUids + toggleRestrictedUid).take(50)
                }
                updates["restrictedUids"] = next
            }
            if (antiSpamEnabled != null) {
                updates["antiSpamEnabled"] = antiSpamEnabled
                nextLog.add("Anti-spam filter: $antiSpamEnabled")
            }
            if (topicsEnabled != null) {
                updates["topicsEnabled"] = topicsEnabled
                nextLog.add("Forum topics: $topicsEnabled")
            }
            if (linkedGroupId != null) {
                updates["linkedGroupId"] = linkedGroupId.take(128)
                nextLog.add("Linked discussion group")
            }
            if (inviteExpiresAtEpochMs != null) {
                updates["inviteExpiresAtEpochMs"] = inviteExpiresAtEpochMs.coerceAtLeast(0L)
            }
            if (incrementChannelView) {
                updates["channelViewsCount"] = chat.channelViewsCount + 1L
            }
            if (uid in chat.adminUids && nextLog != chat.adminActivityLog) {
                updates["adminActivityLog"] = nextLog.takeLast(30)
            }
            db.collection("chats").document(chat.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinGroupByInvite(chatId: String?, inviteCode: String): Result<Pair<String, String>> {
        val cid = chatId?.trim().orEmpty()
        if (cid.isBlank()) {
            return Result.failure(IllegalArgumentException("Invite QR is missing group ID (cid). Ask admin to share the full invite URI."))
        }
        val path = "chats/$cid"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("chats").document(cid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalArgumentException("Invited group or channel was not found."))
            }
            if (snap.getBoolean("inviteRevoked") == true) {
                return Result.failure(IllegalStateException("This invite link has been revoked by an admin."))
            }
            val expectedCode = snap.getString("inviteCode").orEmpty()
            if (!expectedCode.equals(inviteCode.trim(), ignoreCase = true)) {
                return Result.failure(SecurityException("Invite code does not match or has been rotated."))
            }
            val expiresAt = snap.getLong("inviteExpiresAtEpochMs") ?: 0L
            if (expiresAt > 0L && System.currentTimeMillis() > expiresAt) {
                return Result.failure(IllegalStateException("This invite link has expired."))
            }
            val members = (snap.get("memberUids") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            if (uid in members) {
                return Result.success(cid to "OPENED")
            }
            val type = snap.getString("type").orEmpty()
            val pub = snap.getBoolean("publicVisible") ?: false
            if (type == "CHANNEL" && pub) {
                db.runTransaction { tx ->
                    val s = tx.get(docRef)
                    val cur = (s.get("memberUids") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                    if (uid !in cur) {
                        tx.update(docRef, mapOf("memberUids" to (cur + uid).distinct().take(50), "updatedAt" to FieldValue.serverTimestamp()))
                    }
                }.await()
                return Result.success(cid to "JOINED")
            }
            db.runTransaction { tx ->
                val s = tx.get(docRef)
                val reqs = (s.get("joinRequestUids") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                if (uid !in reqs) {
                    tx.update(docRef, mapOf("joinRequestUids" to (reqs + uid).distinct().take(50), "updatedAt" to FieldValue.serverTimestamp()))
                }
            }.await()
            Result.success(cid to "REQUESTED")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}

