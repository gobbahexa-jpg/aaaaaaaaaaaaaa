package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ChatType {
    DIRECT, GROUP, CHANNEL;
    companion object {
        fun fromRaw(raw: String?): ChatType =
            entries.firstOrNull { it.name == raw } ?: DIRECT
    }
}

enum class MessageType {
    TEXT, IMAGE, POLL, QUIZ, STICKER, CODE, VOICE_NOTE, LOCATION, CONTACT, AI_SUMMARY, DOCUMENT, VIDEO, AUDIO;
    companion object {
        fun fromRaw(raw: String?): MessageType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class OnlineStatus(val label: String) {
    ONLINE("Online"),
    AWAY("Away"),
    DND("Do Not Disturb"),
    OFFLINE("Offline");
    companion object {
        fun fromRaw(raw: String?): OnlineStatus =
            entries.firstOrNull { it.name == raw } ?: ONLINE
    }
}

enum class CallType {
    VOICE, VIDEO;
    companion object {
        fun fromRaw(raw: String?): CallType =
            entries.firstOrNull { it.name == raw } ?: VOICE
    }
}

enum class CallStatus {
    RINGING, ACTIVE, ENDED, DECLINED, MISSED;
    companion object {
        fun fromRaw(raw: String?): CallStatus =
            entries.firstOrNull { it.name == raw } ?: ENDED
    }
}

enum class SettingsCategory(val title: String) {
    ACCOUNT("Account"),
    PRIVACY("Privacy & Lock"),
    APPEARANCE("Appearance"),
    CHATS("Chats"),
    NOTIFICATIONS("Notifications"),
    STORAGE("Storage & Data"),
    DEVICES("Linked Devices"),
    SECURITY("Security & Backups"),
    ABOUT("About DL Chat")
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val statusMessage: String = "",
    val avatarColor: String = "#00E5FF",
    val photoDataBase64: String? = null,
    val onlineStatus: String = "ONLINE",
    val showLastSeen: Boolean = true,
    val showOnline: Boolean = true,
    val readReceipts: Boolean = true,
    val discoverable: Boolean = true,
    val suspended: Boolean = false,
    val publicKeyFingerprint: String = "",
    val publicKeyBase64: String? = null,
    val keyVersion: Int = 1,
    val twoFactorEnabled: Boolean = false,
    val profilePhotoVisibility: String = "EVERYONE",
    val legionUserId: String? = null,
    val legionUsername: String? = null,
    val legionMemberNumber: String? = null,
    val legionVerificationStatus: String? = null,
    val legionPrimaryUnit: String? = null,
    val premiumTier: String = "FREE",
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserSetting(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeMode: String = "DARK",
    val accentColorHex: String = "#00E5FF",
    val bubbleStyle: String = "MODERN",
    val fontScale: Double = 1.0,
    val wallpaperId: String = "nebula",
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val dndEnabled: Boolean = false,
    val aiEnabled: Boolean = true,
    val autoDownloadMedia: Boolean = true,
    val appLockEnabled: Boolean = false,
    val autoLockSeconds: Int = 60,
    val biometricLockEnabled: Boolean = false,
    val screenshotProtectionEnabled: Boolean = false,
    val hideNotificationContent: Boolean = false,
    val autoSyncOfflineQueue: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeviceSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val platform: String = "Android",
    val deviceType: String = "ANDROID",
    val osName: String = "",
    val browserName: String = "",
    val connectionStatus: String = "ONLINE",
    val pairingSessionId: String? = null,
    val pairingTokenHash: String? = null,
    val tokenRotationCount: Int = 1,
    val revoked: Boolean = false,
    @ServerTimestamp val firstLinkedAt: Timestamp? = null,
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DevicePairingSession(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val tokenHash: String = "",
    val nonce: String = "",
    val targetDeviceName: String = "",
    val targetDeviceType: String = "DESKTOP",
    val targetOs: String = "",
    val targetBrowser: String = "",
    val status: String = "PENDING",
    val expiresAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AccountSessionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val username: String = "",
    val active: Boolean = true,
    val lastUsedAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeviceNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetDeviceId: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "SECURITY",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SyncEvent(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val entityType: String = "MESSAGE",
    val entityId: String = "",
    val operation: String = "UPSERT",
    val versionEpochMs: Long = 0L,
    val deviceId: String = "",
    val payloadSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeviceKeyBundle(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val deviceId: String = "",
    val publicKeyBase64: String = "",
    val fingerprint: String = "",
    val keyVersion: Int = 1,
    val revoked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatExportRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val chatId: String = "",
    val chatTitle: String = "",
    val format: String = "JSON",
    val itemCount: Int = 0,
    val sizeBytes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PrivateChatSetting(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val chatId: String = "",
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val pinnedSortOrder: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatDraft(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val chatId: String = "",
    val draftText: String = "",
    val replyToId: String? = null,
    val updatedAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ContactEntry(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val contactUid: String = "",
    val displayName: String = "",
    val username: String = "",
    val favorite: Boolean = false,
    val blocked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ContactRequest(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderUsername: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val status: String = "PENDING",
    val message: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatFolder(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val filterType: String = "ALL",
    val iconName: String = "Folder",
    val sortOrder: Int = 0,
    val includeChatIds: List<String> = emptyList(),
    val excludeChatIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SavedMessage(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val text: String = "",
    val sourceChatName: String = "",
    val sourceMessageId: String? = null,
    val noteCategory: String = "SAVED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BackupRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val fileName: String = "",
    val itemCount: Int = 0,
    val sizeBytes: Long = 0L,
    val encryptionAlg: String = "PBKDF2+AES-256-GCM",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val type: String = "DIRECT",
    val title: String = "",
    val description: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val publicVisible: Boolean = false,
    val channelHandle: String? = null,
    val adminOnlyPost: Boolean = false,
    val e2eeEnabled: Boolean = true,
    val archivedBy: List<String> = emptyList(),
    val mutedBy: List<String> = emptyList(),
    val pinnedMessageText: String? = null,
    val pinnedMessageId: String? = null,
    val pinnedMessages: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val disappearingSeconds: Long = 0L,
    val inviteCode: String? = null,
    val inviteRevoked: Boolean = false,
    val inviteExpiresAtEpochMs: Long? = null,
    val joinRequestUids: List<String> = emptyList(),
    val typingUids: Map<String, Long> = emptyMap(),
    val readCursors: Map<String, Long> = emptyMap(),
    val allowPinning: Boolean = true,
    val allowMemberInvites: Boolean = true,
    val themeAccentHex: String? = null,
    val adminActivityLog: List<String> = emptyList(),
    val linkedGroupId: String? = null,
    val slowModeSeconds: Int = 0,
    val restrictedUids: List<String> = emptyList(),
    val antiSpamEnabled: Boolean = false,
    val topicsEnabled: Boolean = false,
    val channelViewsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolveTitle(myUid: String): String {
        if (ChatType.fromRaw(type) != ChatType.DIRECT) return title.ifBlank { "Untitled" }
        val otherEntry = participantNames.entries.firstOrNull { it.key != myUid }
        return otherEntry?.value?.takeIf { it.isNotBlank() } ?: title.ifBlank { "Direct Chat" }
    }

    fun hasUnreadFor(myUid: String): Boolean {
        if (lastMessageSenderId.isBlank() || lastMessageSenderId == myUid) return false
        val msgMs = lastMessageAt?.toDate()?.time ?: return false
        val cursorMs = readCursors[myUid] ?: 0L
        return msgMs > cursorMs
    }

    fun activeTypersExcluding(myUid: String, nowMs: Long = System.currentTimeMillis()): List<String> =
        typingUids.filter { (uid, ts) -> uid != myUid && nowMs - ts in 0L..10_000L }
            .keys
            .map { uid -> participantNames[uid] ?: "Someone" }
}

@Keep
@IgnoreExtraProperties
data class ChatTopic(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val creatorId: String = "",
    val title: String = "",
    val iconEmoji: String = "💬",
    val closed: Boolean = false,
    val messageCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupEvent(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val creatorId: String = "",
    val title: String = "",
    val description: String = "",
    val startsAtEpochMs: Long = 0L,
    val rsvpUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val publicChannel: Boolean = false,
    val type: String = "TEXT",
    val text: String = "",
    val encrypted: Boolean = false,
    val ciphertextBase64: String? = null,
    val ivBase64: String? = null,
    val senderKeyVersion: Int? = null,
    val mediaDataBase64: String? = null,
    val mediaStorageStatus: String? = null,
    val fileName: String? = null,
    val fileMimeType: String? = null,
    val fileSizeBytes: Long? = null,
    val audioDurationMs: Long? = null,
    val waveformSamples: List<Int>? = null,
    val replyToId: String? = null,
    val replyToPreview: String? = null,
    val forwardedFrom: String? = null,
    val edited: Boolean = false,
    val editHistory: List<String> = emptyList(),
    val deletedForEveryone: Boolean = false,
    val pinnedInChat: Boolean = false,
    val reactions: Map<String, String> = emptyMap(),
    val deliveredTo: List<String> = emptyList(),
    val readBy: List<String> = emptyList(),
    val starredBy: List<String> = emptyList(),
    val pollQuestion: String? = null,
    val pollOptions: List<String>? = null,
    val pollVotes: Map<String, String>? = null,
    val quizCorrectIndex: Int? = null,
    val quizExplanation: String? = null,
    val stickerId: String? = null,
    val topicId: String? = null,
    val threadRootId: String? = null,
    val reminderEpochMs: Long? = null,
    val aiTranscribedText: String? = null,
    val scheduledForEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoryItem(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val type: String = "TEXT",
    val text: String = "",
    val backgroundHex: String = "#00E5FF",
    val mediaDataBase64: String? = null,
    val viewedBy: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap(),
    val hiddenFromUids: List<String> = emptyList(),
    val discoverable: Boolean = true,
    val expiresAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = "VOICE",
    val status: String = "RINGING",
    val durationSeconds: Long = 0L,
    val groupCall: Boolean = false,
    val sdpOffer: String? = null,
    val sdpAnswer: String? = null,
    val iceCandidates: List<String>? = null,
    val signalingNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ModerationReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetUid: String = "",
    val targetType: String = "USER",
    val category: String = "OTHER",
    val reason: String = "",
    val status: String = "OPEN",
    val chatId: String? = null,
    val messageId: String? = null,
    val storyId: String? = null,
    val targetName: String? = null,
    val evidenceSnippet: String? = null,
    val triageLabel: String? = null,
    val severityScore: Int? = null,
    val aiTriaged: Boolean = false,
    val caseId: String? = null,
    val reviewedByUid: String? = null,
    val resolutionNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class AiAssistantMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val streaming: Boolean = false
)

data class DlSticker(
    val id: String,
    val label: String,
    val glyph: String,
    val tag: String
)

object DlStickerPack {
    val builtInStickers: List<DlSticker> = listOf(
        DlSticker("dl_shield", "Legion Shield", "🛡️", "DEATH LEGION"),
        DlSticker("dl_bolt", "Overdrive", "⚡", "READY"),
        DlSticker("dl_salute", "Legion Salute", "🫡", "COPY THAT"),
        DlSticker("dl_fire", "Obsidian Flame", "🔥", "ELITE"),
        DlSticker("dl_lock", "Zero Trust", "🔐", "VERIFIED"),
        DlSticker("dl_target", "Lock On", "🎯", "ON TARGET"),
        DlSticker("dl_rocket", "Deploy", "🚀", "LAUNCHED"),
        DlSticker("dl_skull", "Legion Crest", "💀", "LEGION")
    )

    fun findById(id: String?): DlSticker? = builtInStickers.firstOrNull { it.id == id }
}

data class DlPremiumFeature(
    val title: String,
    val freeLimit: String,
    val proLimit: String
)

object DlPremiumCatalog {
    val features: List<DlPremiumFeature> = listOf(
        DlPremiumFeature("Custom Chat Folders", "Up to 10 folders", "Unlimited folders & icons"),
        DlPremiumFeature("Media Attachment Cap", "Standard Firestore caps", "Cloud Storage bucket ready"),
        DlPremiumFeature("Exclusive DL Stickers", "Standard Legion pack", "Animated Legion Crests"),
        DlPremiumFeature("Pinned Conversations", "Up to 5 pinned chats", "Unlimited pinned chats"),
        DlPremiumFeature("Voice Note Summaries", "On-demand summaries", "Priority voice summarization")
    )
}

object ChatFolderMatcher {
    fun matchesFolder(
        chat: ChatRoom,
        myUid: String,
        filterKey: String,
        customFolder: ChatFolder? = null,
        favoritePeerUids: Set<String> = emptySet()
    ): Boolean {
        val archived = myUid in chat.archivedBy
        if (customFolder != null) {
            if (chat.id in customFolder.excludeChatIds) return false
            if (customFolder.includeChatIds.isNotEmpty() && chat.id in customFolder.includeChatIds) return true
            if (customFolder.filterType == "CUSTOM") return chat.id in customFolder.includeChatIds
        }
        val activeType = customFolder?.filterType ?: filterKey
        return when (activeType) {
            "ARCHIVED" -> archived
            "DIRECT" -> !archived && chat.type == "DIRECT"
            "GROUP" -> !archived && chat.type == "GROUP"
            "CHANNEL" -> !archived && chat.type == "CHANNEL"
            "BOT" -> !archived && (
                chat.title.contains("bot", ignoreCase = true) ||
                    chat.title.contains("ai", ignoreCase = true) ||
                    chat.channelHandle?.contains("bot", ignoreCase = true) == true
                )
            "UNREAD" -> !archived && chat.hasUnreadFor(myUid)
            "FAVORITE" -> !archived && chat.type == "DIRECT" && chat.memberUids.any { it != myUid && it in favoritePeerUids }
            else -> !archived
        }
    }
}
