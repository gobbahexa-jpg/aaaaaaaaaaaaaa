package com.example.data.repository

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.StoryItem
import com.example.data.model.UserProfile
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

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeMyChats(uid: String): Flow<List<ChatRoom>> =
        db.collection("chats")
            .whereArrayContains("memberUids", uid)
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatRoom::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastMessageAt?.toDate()?.time ?: 0L }
            }

    fun observePublicChannels(): Flow<List<ChatRoom>> =
        db.collection("chats")
            .whereEqualTo("type", "CHANNEL")
            .whereEqualTo("publicVisible", true)
            .limit(60)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatRoom::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.memberUids.size }
            }

    fun observeMessages(
        chatId: String,
        uid: String,
        publicChannel: Boolean,
        pageSize: Long = 100L
    ): Flow<List<ChatMessage>> {
        val ref = db.collection("chats").document(chatId).collection("messages")
        val query = if (publicChannel) {
            ref.whereEqualTo("publicChannel", true).limit(pageSize)
        } else {
            ref.whereArrayContains("memberUids", uid).limit(pageSize)
        }
        return query.snapshots().map { snap ->
            snap.toObjects(ChatMessage::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
        }
    }

    fun observeStories(): Flow<List<StoryItem>> =
        db.collection("stories")
            .whereEqualTo("discoverable", true)
            .limit(80)
            .snapshots()
            .map { snap ->
                val now = System.currentTimeMillis()
                snap.toObjects(StoryItem::class.java, ServerTimestampBehavior.ESTIMATE)
                    .filter { it.expiresAtEpochMs > now }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeCalls(uid: String): Flow<List<CallSession>> =
        db.collection("calls")
            .whereArrayContains("participantUids", uid)
            .limit(80)
            .snapshots()
            .map { snap ->
                snap.toObjects(CallSession::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    suspend fun createOrOpenDirectChat(me: UserProfile, peer: UserProfile): Result<String> {
        var path = "chats"
        return try {
            val uid = requireUserId()
            require(peer.userId.isNotBlank() && peer.userId != uid) { "Select another user." }
            val ordered = listOf(uid, peer.userId).sorted()
            val deterministicId = "dm_${ordered[0].take(20)}_${ordered[1].take(20)}"
            val docRef = db.collection("chats").document(deterministicId)
            path = docRef.path
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "type" to "DIRECT",
                    "title" to peer.displayName.take(80),
                    "description" to "End-to-end verified direct channel",
                    "ownerId" to uid,
                    "memberUids" to ordered,
                    "adminUids" to ordered,
                    "participantNames" to mapOf(
                        uid to me.displayName.ifBlank { "Me" },
                        peer.userId to peer.displayName.ifBlank { peer.username }
                    ),
                    "publicVisible" to false,
                    "adminOnlyPost" to false,
                    "e2eeEnabled" to true,
                    "archivedBy" to emptyList<String>(),
                    "mutedBy" to emptyList<String>(),
                    "lastMessageText" to "Conversation started",
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "disappearingSeconds" to 0L,
                    "readCursors" to mapOf(uid to System.currentTimeMillis()),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(deterministicId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createGroupOrChannel(
        me: UserProfile,
        type: String,
        title: String,
        description: String,
        memberProfiles: List<UserProfile>,
        publicVisible: Boolean,
        channelHandle: String?,
        adminOnlyPost: Boolean
    ): Result<String> {
        var path = "chats"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(80)
            require(cleanTitle.isNotEmpty()) { "Title is required." }
            val members = (listOf(uid) + memberProfiles.map { it.userId }).distinct().take(50)
            val names = buildMap {
                put(uid, me.displayName.ifBlank { "Me" })
                memberProfiles.forEach { put(it.userId, it.displayName.ifBlank { it.username }) }
            }
            val docRef = db.collection("chats").document()
            path = docRef.path
            val cleanHandle = channelHandle?.trim()?.lowercase()
                ?.replace(Regex("[^a-z0-9_]"), "_")
                ?.take(40)
                ?.takeIf { it.isNotEmpty() }
            val inviteCode = "DL-${UUID.randomUUID().toString().take(6).uppercase()}"

            val payload = mapOf(
                "type" to type,
                "title" to cleanTitle,
                "description" to description.trim().take(300),
                "ownerId" to uid,
                "memberUids" to members,
                "adminUids" to listOf(uid),
                "participantNames" to names,
                "publicVisible" to (type == "CHANNEL" && publicVisible),
                "channelHandle" to cleanHandle,
                "adminOnlyPost" to (type == "CHANNEL" || adminOnlyPost),
                "e2eeEnabled" to (type == "GROUP" && !publicVisible),
                "archivedBy" to emptyList<String>(),
                "mutedBy" to emptyList<String>(),
                "lastMessageText" to if (type == "CHANNEL") "Channel launched" else "Group created",
                "lastMessageSenderId" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "disappearingSeconds" to 0L,
                "inviteCode" to inviteCode,
                "inviteRevoked" to false,
                "joinRequestUids" to emptyList<String>(),
                "allowPinning" to true,
                "allowMemberInvites" to true,
                "adminActivityLog" to listOf("${me.displayName.ifBlank { "Owner" }} created $type"),
                "readCursors" to mapOf(uid to System.currentTimeMillis()),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinPublicChannel(chat: ChatRoom): Result<Unit> {
        val path = "chats/${chat.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("chats").document(chat.id)
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val currentMembers = (snap.get("memberUids") as? List<*>)
                    ?.mapNotNull { it as? String }.orEmpty()
                if (uid !in currentMembers) {
                    val nextMembers = (currentMembers + uid).distinct().take(50)
                    tx.update(
                        docRef,
                        mapOf(
                            "memberUids" to nextMembers,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun requestJoinGroup(chatId: String): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("chats").document(chatId)
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val members = (snap.get("memberUids") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val requests = (snap.get("joinRequestUids") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                if (uid !in members && uid !in requests) {
                    tx.update(
                        docRef,
                        mapOf(
                            "joinRequestUids" to (requests + uid).take(50),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun approveJoinRequest(chatId: String, applicant: UserProfile, approve: Boolean): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            requireUserId()
            val docRef = db.collection("chats").document(chatId)
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val members = (snap.get("memberUids") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val requests = (snap.get("joinRequestUids") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val names = (snap.get("participantNames") as? Map<*, *>)
                    ?.entries?.associate { (k, v) -> k.toString() to v.toString() }
                    ?.toMutableMap() ?: mutableMapOf()
                val log = (snap.get("adminActivityLog") as? List<*>)?.mapNotNull { it as? String }.orEmpty().toMutableList()

                val nextRequests = requests.filterNot { it == applicant.userId }
                val updates = mutableMapOf<String, Any>(
                    "joinRequestUids" to nextRequests,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (approve) {
                    updates["memberUids"] = (members + applicant.userId).distinct().take(50)
                    names[applicant.userId] = applicant.displayName.ifBlank { applicant.username }
                    updates["participantNames"] = names
                    log.add("Approved join request: ${applicant.displayName}")
                } else {
                    log.add("Declined join request: ${applicant.displayName}")
                }
                updates["adminActivityLog"] = log.takeLast(30)
                tx.update(docRef, updates)
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        chat: ChatRoom,
        senderName: String,
        type: String,
        text: String,
        encrypted: Boolean = false,
        ciphertextBase64: String? = null,
        ivBase64: String? = null,
        senderKeyVersion: Int? = null,
        mediaDataBase64: String? = null,
        mediaStorageStatus: String? = null,
        fileName: String? = null,
        fileMimeType: String? = null,
        fileSizeBytes: Long? = null,
        audioDurationMs: Long? = null,
        waveformSamples: List<Int>? = null,
        replyToId: String? = null,
        replyToPreview: String? = null,
        forwardedFrom: String? = null,
        pollQuestion: String? = null,
        pollOptions: List<String>? = null,
        scheduledForEpochMs: Long? = null,
        quizCorrectIndex: Int? = null,
        quizExplanation: String? = null,
        stickerId: String? = null,
        topicId: String? = null,
        threadRootId: String? = null
    ): Result<Unit> {
        var path = "chats/${chat.id}/messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(4000)
            require(
                cleanText.isNotEmpty() ||
                    !mediaDataBase64.isNullOrBlank() ||
                    !ciphertextBase64.isNullOrBlank() ||
                    !pollQuestion.isNullOrBlank() ||
                    !stickerId.isNullOrBlank() ||
                    !fileName.isNullOrBlank()
            ) {
                "Message cannot be empty."
            }
            val msgRef = db.collection("chats").document(chat.id).collection("messages").document()
            path = msgRef.path
            val expiresAt = if (chat.disappearingSeconds > 0) {
                System.currentTimeMillis() + chat.disappearingSeconds * 1000L
            } else null

            val payload = mapOf(
                "chatId" to chat.id,
                "senderId" to uid,
                "senderName" to senderName.trim().take(60).ifBlank { "Operator" },
                "memberUids" to chat.memberUids.take(50),
                "publicChannel" to (chat.type == "CHANNEL" && chat.publicVisible),
                "type" to type,
                "text" to cleanText,
                "encrypted" to encrypted,
                "ciphertextBase64" to ciphertextBase64?.take(12_000),
                "ivBase64" to ivBase64?.take(64),
                "senderKeyVersion" to senderKeyVersion,
                "mediaDataBase64" to mediaDataBase64?.takeIf { it.length <= 440_000 },
                "mediaStorageStatus" to mediaStorageStatus?.take(64),
                "fileName" to fileName?.take(160),
                "fileMimeType" to fileMimeType?.take(80),
                "fileSizeBytes" to fileSizeBytes?.coerceAtLeast(0L),
                "audioDurationMs" to audioDurationMs?.coerceIn(0L, 3_600_000L),
                "waveformSamples" to waveformSamples?.take(64),
                "replyToId" to replyToId?.take(128),
                "replyToPreview" to replyToPreview?.take(200),
                "forwardedFrom" to forwardedFrom?.take(80),
                "edited" to false,
                "editHistory" to emptyList<String>(),
                "deletedForEveryone" to false,
                "pinnedInChat" to false,
                "reactions" to emptyMap<String, String>(),
                "deliveredTo" to listOf(uid),
                "readBy" to listOf(uid),
                "starredBy" to emptyList<String>(),
                "pollQuestion" to pollQuestion?.trim()?.take(200),
                "pollOptions" to pollOptions?.take(6),
                "pollVotes" to if (pollQuestion != null) emptyMap<String, String>() else null,
                "quizCorrectIndex" to quizCorrectIndex,
                "quizExplanation" to quizExplanation?.trim()?.take(300),
                "stickerId" to stickerId?.take(80),
                "topicId" to topicId?.take(128),
                "threadRootId" to threadRootId?.take(128),
                "scheduledForEpochMs" to scheduledForEpochMs,
                "expiresAtEpochMs" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            msgRef.set(payload).await()

            val preview = when {
                encrypted -> "Encrypted message"
                type == "IMAGE" -> if (cleanText.isBlank()) "Photo" else "Photo: $cleanText"
                type == "POLL" -> "Poll: ${pollQuestion ?: cleanText}"
                type == "QUIZ" -> "Quiz: ${pollQuestion ?: cleanText}"
                type == "STICKER" -> "Sticker: ${cleanText.ifBlank { stickerId ?: "DL Sticker" }}"
                type == "CODE" -> "Code snippet"
                type == "VOICE_NOTE" -> "Voice message (${(audioDurationMs ?: 0L) / 1000L}s)"
                type == "DOCUMENT" -> "Document: ${fileName ?: "File"}"
                type == "VIDEO" -> "Video: ${fileName ?: "Clip"}"
                type == "AUDIO" -> "Audio: ${fileName ?: "Track"}"
                type == "LOCATION" -> "Shared location"
                type == "CONTACT" -> "Shared contact"
                else -> cleanText
            }.take(500)

            val nextReadCursors = chat.readCursors.toMutableMap().apply {
                put(uid, System.currentTimeMillis())
            }
            val nextTyping = chat.typingUids.toMutableMap().apply { remove(uid) }

            db.collection("chats").document(chat.id).update(
                mapOf(
                    "lastMessageText" to preview,
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "readCursors" to nextReadCursors,
                    "typingUids" to nextTyping,
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

    suspend fun setTypingStatus(chat: ChatRoom, typing: Boolean): Result<Unit> {
        val path = "chats/${chat.id}"
        return try {
            val uid = requireUserId()
            val next = chat.typingUids.toMutableMap()
            if (typing) {
                next[uid] = System.currentTimeMillis()
            } else {
                next.remove(uid)
            }
            db.collection("chats").document(chat.id).update(
                mapOf("typingUids" to next, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun markConversationRead(chat: ChatRoom, messages: List<ChatMessage>): Result<Unit> {
        val path = "chats/${chat.id}"
        return try {
            val uid = requireUserId()
            val nextCursors = chat.readCursors.toMutableMap().apply {
                put(uid, System.currentTimeMillis())
            }
            db.collection("chats").document(chat.id).update(
                mapOf("readCursors" to nextCursors, "updatedAt" to FieldValue.serverTimestamp())
            ).await()

            messages.filter { uid !in it.readBy }.takeLast(12).forEach { msg ->
                val ref = db.collection("chats").document(chat.id).collection("messages").document(msg.id)
                db.runTransaction { tx ->
                    val snap = tx.get(ref)
                    val curRead = (snap.get("readBy") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                    val curDelivered = (snap.get("deliveredTo") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                    if (uid !in curRead) {
                        tx.update(
                            ref,
                            mapOf(
                                "readBy" to (curRead + uid).distinct().take(50),
                                "deliveredTo" to (curDelivered + uid).distinct().take(50),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }.await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleStarMessage(chatId: String, messageId: String): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("chats").document(chatId).collection("messages").document(messageId)
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                val current = (snap.get("starredBy") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val next = if (uid in current) current - uid else (current + uid).take(50)
                tx.update(ref, mapOf("starredBy" to next, "updatedAt" to FieldValue.serverTimestamp()))
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun editMessage(
        chatId: String,
        messageId: String,
        newText: String,
        encrypted: Boolean = false,
        ciphertextBase64: String? = null,
        ivBase64: String? = null
    ): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            val clean = newText.trim().take(4000)
            require(clean.isNotEmpty()) { "Message text cannot be empty." }
            val ref = db.collection("chats").document(chatId).collection("messages").document(messageId)
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                val oldText = snap.getString("text").orEmpty()
                val oldHistory = (snap.get("editHistory") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val nextHistory = if (oldText.isNotBlank() && oldText != clean) {
                    (oldHistory + oldText).takeLast(20)
                } else {
                    oldHistory
                }
                val updates = mutableMapOf<String, Any>(
                    "text" to clean,
                    "encrypted" to encrypted,
                    "edited" to true,
                    "editHistory" to nextHistory,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (ciphertextBase64 != null && ivBase64 != null) {
                    updates["ciphertextBase64"] = ciphertextBase64
                    updates["ivBase64"] = ivBase64
                }
                tx.update(ref, updates)
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveMessageAiTranscript(
        chatId: String,
        messageId: String,
        transcriptText: String
    ): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            db.collection("chats").document(chatId).collection("messages").document(messageId)
                .update(
                    mapOf(
                        "aiTranscribedText" to transcriptText.trim().take(4000),
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

    fun formatChatExport(
        chatTitle: String,
        messages: List<ChatMessage>,
        format: String
    ): String {
        val visible = messages.filterNot { it.deletedForEveryone }
        return if (format == "JSON") {
            val root = org.json.JSONObject().apply {
                put("chatTitle", chatTitle)
                put("exportedAtEpochMs", System.currentTimeMillis())
                put("messageCount", visible.size)
                val arr = org.json.JSONArray()
                visible.forEach { msg ->
                    arr.put(
                        org.json.JSONObject().apply {
                            put("id", msg.id)
                            put("senderName", msg.senderName)
                            put("type", msg.type)
                            put("text", if (msg.encrypted) "[E2EE Encrypted]" else msg.text)
                            put("timestampMs", msg.createdAt?.toDate()?.time ?: 0L)
                        }
                    )
                }
                put("messages", arr)
            }
            root.toString(2)
        } else {
            buildString {
                appendLine("=== DL CHAT EXPORT: $chatTitle ===")
                appendLine("Messages: ${visible.size}")
                appendLine()
                visible.forEach { msg ->
                    val ts = msg.createdAt?.toDate()?.toString() ?: "Pending"
                    val body = if (msg.encrypted) "[E2EE Encrypted]" else msg.text
                    appendLine("[$ts] ${msg.senderName} (${msg.type}): $body")
                }
            }
        }
    }

    suspend fun deleteMessageForEveryone(chatId: String, messageId: String): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            db.collection("chats").document(chatId).collection("messages").document(messageId)
                .update(
                    mapOf(
                        "text" to "This message was deleted",
                        "encrypted" to false,
                        "deletedForEveryone" to true,
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

    suspend fun toggleReaction(chatId: String, message: ChatMessage, emoji: String): Result<Unit> {
        val path = "chats/$chatId/messages/${message.id}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("chats").document(chatId).collection("messages").document(message.id)
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                val current = (snap.get("reactions") as? Map<*, *>)
                    ?.entries?.associate { (k, v) -> k.toString() to v.toString() }
                    ?.toMutableMap() ?: mutableMapOf()
                if (current[uid] == emoji) current.remove(uid) else current[uid] = emoji
                tx.update(ref, mapOf("reactions" to current, "updatedAt" to FieldValue.serverTimestamp()))
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun votePoll(chatId: String, message: ChatMessage, optionIndex: Int): Result<Unit> {
        val path = "chats/$chatId/messages/${message.id}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("chats").document(chatId).collection("messages").document(message.id)
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                val current = (snap.get("pollVotes") as? Map<*, *>)
                    ?.entries?.associate { (k, v) -> k.toString() to v.toString() }
                    ?.toMutableMap() ?: mutableMapOf()
                current[uid] = optionIndex.toString()
                tx.update(ref, mapOf("pollVotes" to current, "updatedAt" to FieldValue.serverTimestamp()))
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateChatMetadata(
        chat: ChatRoom,
        pinMessage: ChatMessage? = null,
        toggleArchive: Boolean = false,
        toggleMute: Boolean = false,
        disappearingSeconds: Long? = null,
        toggleAdminOnly: Boolean? = null,
        addMember: UserProfile? = null,
        promoteAdminUid: String? = null,
        removeMemberUid: String? = null,
        regenerateInvite: Boolean = false,
        revokeInvite: Boolean? = null,
        allowPinning: Boolean? = null,
        allowMemberInvites: Boolean? = null,
        themeAccentHex: String? = null,
        activityNote: String? = null
    ): Result<Unit> {
        val path = "chats/${chat.id}"
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            val nextLog = chat.adminActivityLog.toMutableList()
            if (pinMessage != null) {
                updates["pinnedMessageText"] = pinMessage.text.take(500)
                updates["pinnedMessageId"] = pinMessage.id.take(128)
                nextLog.add("Pinned message: ${pinMessage.text.take(36)}")
            }
            if (toggleArchive) {
                val next = if (uid in chat.archivedBy) chat.archivedBy - uid else chat.archivedBy + uid
                updates["archivedBy"] = next.take(50)
            }
            if (toggleMute) {
                val next = if (uid in chat.mutedBy) chat.mutedBy - uid else chat.mutedBy + uid
                updates["mutedBy"] = next.take(50)
            }
            if (disappearingSeconds != null) {
                updates["disappearingSeconds"] = disappearingSeconds.coerceIn(0L, 604800L)
                nextLog.add("Disappearing timer set to ${disappearingSeconds}s")
            }
            if (toggleAdminOnly != null) {
                updates["adminOnlyPost"] = toggleAdminOnly
                nextLog.add("Admin-only post: $toggleAdminOnly")
            }
            if (addMember != null && addMember.userId !in chat.memberUids) {
                updates["memberUids"] = (chat.memberUids + addMember.userId).take(50)
                updates["participantNames"] = chat.participantNames + (addMember.userId to addMember.displayName)
                nextLog.add("Added member: ${addMember.displayName}")
            }
            if (promoteAdminUid != null && promoteAdminUid !in chat.adminUids) {
                updates["adminUids"] = (chat.adminUids + promoteAdminUid).take(20)
                val name = chat.participantNames[promoteAdminUid] ?: promoteAdminUid.take(6)
                nextLog.add("Promoted $name to Admin")
            }
            if (removeMemberUid != null && removeMemberUid != chat.ownerId) {
                updates["memberUids"] = chat.memberUids.filterNot { it == removeMemberUid }
                updates["adminUids"] = chat.adminUids.filterNot { it == removeMemberUid }
                val name = chat.participantNames[removeMemberUid] ?: removeMemberUid.take(6)
                nextLog.add("Removed member: $name")
            }
            if (regenerateInvite) {
                val newCode = "DL-${UUID.randomUUID().toString().take(6).uppercase()}"
                updates["inviteCode"] = newCode
                updates["inviteRevoked"] = false
                nextLog.add("Rotated invite code")
            }
            if (revokeInvite != null) {
                updates["inviteRevoked"] = revokeInvite
                nextLog.add(if (revokeInvite) "Revoked invite link" else "Re-enabled invite link")
            }
            if (allowPinning != null) updates["allowPinning"] = allowPinning
            if (allowMemberInvites != null) updates["allowMemberInvites"] = allowMemberInvites
            if (themeAccentHex != null) updates["themeAccentHex"] = themeAccentHex.take(16)
            if (!activityNote.isNullOrBlank()) nextLog.add(activityNote.take(80))

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

    suspend fun createStory(
        authorName: String,
        type: String,
        text: String,
        backgroundHex: String,
        mediaDataBase64: String? = null,
        hiddenFromUids: List<String> = emptyList()
    ): Result<Unit> {
        var path = "stories"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("stories").document()
            path = docRef.path
            val payload = mapOf(
                "authorId" to uid,
                "authorName" to authorName.trim().take(60).ifBlank { "Operator" },
                "type" to type,
                "text" to text.trim().take(500),
                "backgroundHex" to backgroundHex.take(16),
                "mediaDataBase64" to mediaDataBase64?.takeIf { it.length <= 440_000 },
                "viewedBy" to emptyList<String>(),
                "reactions" to emptyMap<String, String>(),
                "hiddenFromUids" to hiddenFromUids.take(50),
                "discoverable" to true,
                "expiresAtEpochMs" to (System.currentTimeMillis() + 86_400_000L),
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

    suspend fun markStoryViewedAndReact(story: StoryItem, emoji: String? = null): Result<Unit> {
        val path = "stories/${story.id}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("stories").document(story.id)
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                val viewers = (snap.get("viewedBy") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                val reactions = (snap.get("reactions") as? Map<*, *>)
                    ?.entries?.associate { (k, v) -> k.toString() to v.toString() }
                    ?.toMutableMap() ?: mutableMapOf()
                val nextViewers = if (uid in viewers) viewers else (viewers + uid).take(200)
                if (emoji != null) reactions[uid] = emoji
                tx.update(
                    ref,
                    mapOf(
                        "viewedBy" to nextViewers,
                        "reactions" to reactions,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun startCall(callerName: String, callee: UserProfile, callType: String): Result<String> {
        var path = "calls"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("calls").document()
            path = docRef.path
            val sdpOfferStub = "v=0\r\no=dlchat-${uid.take(8)} 1 2 IN IP4 0.0.0.0\r\ns=DLChat-${callType}\r\n"
            docRef.set(
                mapOf(
                    "callerId" to uid,
                    "callerName" to callerName.trim().take(60).ifBlank { "Operator" },
                    "calleeId" to callee.userId,
                    "calleeName" to callee.displayName.trim().take(60).ifBlank { callee.username },
                    "participantUids" to listOf(uid, callee.userId),
                    "callType" to callType,
                    "status" to "RINGING",
                    "durationSeconds" to 0L,
                    "sdpOffer" to sdpOfferStub,
                    "iceCandidates" to emptyList<String>(),
                    "signalingNote" to "Firestore SDP/ICE signaling live; external WebRTC TURN/SFU relay required for NAT audio/video.",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(callId: String, status: String, durationSeconds: Long): Result<Unit> {
        val path = "calls/$callId"
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "durationSeconds" to durationSeconds.coerceIn(0L, 86400L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (status == "ACTIVE") {
                updates["sdpAnswer"] = "v=0\r\no=dlchat-${uid.take(8)} 2 2 IN IP4 0.0.0.0\r\ns=DLChat-Answer\r\n"
            }
            db.collection("calls").document(callId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
