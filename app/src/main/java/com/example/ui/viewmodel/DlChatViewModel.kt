package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.CountryDialCode
import com.example.core.CryptoIdentityHelper
import com.example.core.ImageUtils
import com.example.core.LocalStorageManager
import com.example.core.LocaleHelper
import com.example.core.RecordedVoiceClip
import com.example.core.StorageBreakdown
import com.example.data.model.OtpChannel
import com.google.firebase.auth.PhoneAuthCredential
import com.example.data.model.AiAssistantMessage
import com.example.data.model.BackupRecord
import com.example.data.model.CallSession
import com.example.data.model.ChatFolder
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.ChatType
import com.example.data.model.ContactEntry
import com.example.data.model.ContactRequest
import com.example.data.model.DeviceSession
import com.example.data.model.DlFeatureRestriction
import com.example.data.model.ModerationReport
import com.example.data.model.SavedMessage
import com.example.data.model.StoryItem
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserSetting
import com.example.data.model.DlSticker
import com.example.data.repository.AppearancePrefs
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.DlAiRepository
import com.example.data.repository.GroupForumRepository
import com.example.data.repository.LinkedDevicesRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SupportRepository
import com.example.data.repository.TrustSafetyRepository
import com.example.data.repository.UserWorkspaceRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
class DlChatViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val workspaceRepository: UserWorkspaceRepository,
    private val preferencesRepository: PreferencesRepository,
    private val aiRepository: DlAiRepository,
    val currentUserId: String,
    val currentUserEmail: String?,
    val isEmailVerified: Boolean,
    private val appContext: Context? = null,
    private val linkedDevicesRepository: LinkedDevicesRepository? = null,
    private val groupForumRepository: GroupForumRepository? = null,
    private val trustSafetyRepository: TrustSafetyRepository? = null,
    private val supportRepository: SupportRepository? = null
) : ViewModel() {

    private val _currentLanguage = MutableStateFlow("en")

    private fun s(resId: Int, vararg args: Any): String =
        LocaleHelper.getString(appContext, _currentLanguage.value, resId, *args)

    fun setAppLanguage(code: String) {
        val normalized = LocaleHelper.normalizeCode(code)
        _currentLanguage.value = normalized
        viewModelScope.launch {
            preferencesRepository.saveLocalUiPreferences(appLanguage = normalized)
        }
    }

    val isAdminUser: Boolean =
        currentUserEmail == "gobbahexa@gmail.com" && isEmailVerified

    private fun <T> Flow<T>.toUiStateFlow(
        tagName: String,
        fallbackResId: Int
    ): StateFlow<UiState<T>> = this
        .map<T, UiState<T>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "$tagName stream error", root)
            emit(UiState.Error(root.message ?: s(fallbackResId)))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val myProfileState: StateFlow<UiState<UserProfile?>> =
        workspaceRepository.observeMyProfile(currentUserId)
            .toUiStateFlow("Profile", R.string.vm_error_load_profile)

    val discoverableUsersState: StateFlow<UiState<List<UserProfile>>> =
        workspaceRepository.observeDiscoverableUsers()
            .toUiStateFlow("Users", R.string.vm_error_load_users)

    val settingsState: StateFlow<UiState<UserSetting?>> =
        workspaceRepository.observeSettings(currentUserId)
            .map { remote ->
                if (remote != null) {
                    preferencesRepository.saveAppearance(
                        AppearancePrefs(
                            themeMode = remote.themeMode,
                            accentColorHex = remote.accentColorHex,
                            bubbleStyle = remote.bubbleStyle,
                            fontScale = remote.fontScale.toFloat(),
                            wallpaperId = remote.wallpaperId
                        )
                    )
                }
                remote
            }
            .toUiStateFlow("Settings", R.string.vm_error_load_settings)

    val devicesState: StateFlow<UiState<List<DeviceSession>>> =
        workspaceRepository.observeDevices(currentUserId)
            .toUiStateFlow("Devices", R.string.vm_error_load_devices)

    val contactsState: StateFlow<UiState<List<ContactEntry>>> =
        workspaceRepository.observeContacts(currentUserId)
            .toUiStateFlow("Contacts", R.string.vm_error_load_contacts)

    val contactRequestsState: StateFlow<UiState<List<ContactRequest>>> =
        workspaceRepository.observeContactRequests(currentUserId)
            .toUiStateFlow("ContactRequests", R.string.vm_error_load_requests)

    val foldersState: StateFlow<UiState<List<ChatFolder>>> =
        workspaceRepository.observeFolders(currentUserId)
            .toUiStateFlow("Folders", R.string.vm_error_load_folders)

    val savedMessagesState: StateFlow<UiState<List<SavedMessage>>> =
        workspaceRepository.observeSavedMessages(currentUserId)
            .toUiStateFlow("SavedMessages", R.string.vm_error_load_notes)

    val backupsState: StateFlow<UiState<List<BackupRecord>>> =
        workspaceRepository.observeBackups(currentUserId)
            .toUiStateFlow("Backups", R.string.vm_error_load_backups)

    val myChatsState: StateFlow<UiState<List<ChatRoom>>> =
        chatRepository.observeMyChats(currentUserId)
            .toUiStateFlow("MyChats", R.string.vm_error_load_chats)

    val publicChannelsState: StateFlow<UiState<List<ChatRoom>>> =
        chatRepository.observePublicChannels()
            .toUiStateFlow("PublicChannels", R.string.vm_error_load_channels)

    val storiesState: StateFlow<UiState<List<StoryItem>>> =
        chatRepository.observeStories()
            .map { stories ->
                stories.filter { currentUserId !in it.hiddenFromUids || it.authorId == currentUserId }
            }
            .toUiStateFlow("Stories", R.string.vm_error_load_stories)

    val callsState: StateFlow<UiState<List<CallSession>>> =
        chatRepository.observeCalls(currentUserId)
            .toUiStateFlow("Calls", R.string.vm_error_load_calls)

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val v6: DlChatV6Coordinator = DlChatV6Coordinator(
        scope = viewModelScope,
        currentUserId = currentUserId,
        isBootstrapAdmin = isAdminUser,
        trustSafetyRepo = trustSafetyRepository,
        supportRepo = supportRepository,
        onBanner = { msg -> _statusBanner.value = msg }
    )

    val reportsState: StateFlow<UiState<List<ModerationReport>>> =
        v6.staffRoleState.flatMapLatest { role ->
            workspaceRepository.observeReports(currentUserId, isAdminUser || role.canModerateUsers)
        }.toUiStateFlow("Reports", R.string.vm_error_load_reports)

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _messagePageSize = MutableStateFlow(100L)
    val messagePageSize: StateFlow<Long> = _messagePageSize.asStateFlow()

    val activeChat: StateFlow<ChatRoom?> =
        combine(_selectedChatId, myChatsState, publicChannelsState) { id, myChats, pubChannels ->
            if (id == null) null
            else {
                val mine = (myChats as? UiState.Success)?.data.orEmpty().firstOrNull { it.id == id }
                mine ?: (pubChannels as? UiState.Success)?.data.orEmpty().firstOrNull { it.id == id }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val activeMessagesState: StateFlow<UiState<List<ChatMessage>>> =
        combine(activeChat, _messagePageSize) { chat, pageSize -> chat to pageSize }
            .flatMapLatest { (chat, pageSize) ->
                if (chat == null) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    combine(
                        chatRepository.observeMessages(
                            chatId = chat.id,
                            uid = currentUserId,
                            publicChannel = chat.type == "CHANNEL" && chat.publicVisible,
                            pageSize = pageSize
                        ),
                        discoverableUsersState
                    ) { list, usersState ->
                        val users = (usersState as? UiState.Success)?.data.orEmpty()
                        val peerUid = if (ChatType.fromRaw(chat.type) == ChatType.DIRECT) {
                            chat.memberUids.firstOrNull { it != currentUserId }
                        } else null
                        val peerPubKey = users.firstOrNull { it.userId == peerUid }?.publicKeyBase64

                        val now = System.currentTimeMillis()
                        val processed = list.filter { msg ->
                            val notExpired = msg.expiresAtEpochMs == null || msg.expiresAtEpochMs > now
                            val visibleSchedule = msg.scheduledForEpochMs == null ||
                                msg.scheduledForEpochMs <= now ||
                                msg.senderId == currentUserId
                            notExpired && visibleSchedule
                        }.map { msg ->
                            if (msg.encrypted && !msg.deletedForEveryone && appContext != null && !msg.ciphertextBase64.isNullOrBlank()) {
                                val decrypted = CryptoIdentityHelper.decryptForDirectPeer(
                                    context = appContext,
                                    myUid = currentUserId,
                                    chatId = chat.id,
                                    peerPublicKeyBase64 = peerPubKey,
                                    ciphertextBase64 = msg.ciphertextBase64,
                                    ivBase64 = msg.ivBase64
                                )
                                if (decrypted != null) msg.copy(text = decrypted)
                                else msg.copy(text = s(R.string.vm_encrypted_key_mismatch))
                            } else {
                                msg
                            }
                        }
                        UiState.Success(processed) as UiState<List<ChatMessage>>
                    }.catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        Log.w(TAG, "Messages stream error", root)
                        emit(UiState.Error(root.message ?: s(R.string.vm_error_load_messages)))
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val _aiMessages = MutableStateFlow<List<AiAssistantMessage>>(
        listOf(
            AiAssistantMessage(
                id = "welcome",
                fromUser = false,
                text = s(R.string.vm_ai_welcome).ifBlank {
                    "Hi, I'm your DL Chat helper. Ask me to help write a message, summarize a chat, check grammar, or explain something you're reading."
                }
            )
        )
    )
    val aiMessages: StateFlow<List<AiAssistantMessage>> = _aiMessages.asStateFlow()

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    private val _smartReplies = MutableStateFlow<List<String>>(emptyList())
    val smartReplies: StateFlow<List<String>> = _smartReplies.asStateFlow()

    private val _translationResult = MutableStateFlow<Pair<String, String>?>(null)
    val translationResult: StateFlow<Pair<String, String>?> = _translationResult.asStateFlow()

    private val _summaryResult = MutableStateFlow<String?>(null)
    val summaryResult: StateFlow<String?> = _summaryResult.asStateFlow()

    private val _aiExplainResult = MutableStateFlow<String?>(null)
    val aiExplainResult: StateFlow<String?> = _aiExplainResult.asStateFlow()

    private val _aiSearchResult = MutableStateFlow<String?>(null)
    val aiSearchResult: StateFlow<String?> = _aiSearchResult.asStateFlow()

    private val _storageBreakdown = MutableStateFlow(StorageBreakdown())
    val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

    val v3: DlChatV3Coordinator = DlChatV3Coordinator(
        scope = viewModelScope,
        currentUserId = currentUserId,
        appContext = appContext,
        activeChatIdFlow = selectedChatId,
        authRepository = authRepository,
        workspaceRepository = workspaceRepository,
        chatRepository = chatRepository,
        linkedRepo = linkedDevicesRepository,
        forumRepo = groupForumRepository,
        onBanner = { msg -> _statusBanner.value = msg },
        resolveString = { resId, args -> s(resId, *args) }
    )

    init {
        refreshStorageStats()
        v3.flushOfflineSyncQueue()
        viewModelScope.launch {
            preferencesRepository.appearanceFlow.collect { prefs ->
                val lang = LocaleHelper.normalizeCode(prefs.appLanguage)
                val changed = _currentLanguage.value != lang
                _currentLanguage.value = lang
                if (changed && _aiMessages.value.size == 1 && _aiMessages.value.first().id == "welcome") {
                    val translated = s(R.string.vm_ai_welcome)
                    if (translated.isNotBlank()) {
                        _aiMessages.value = listOf(_aiMessages.value.first().copy(text = translated))
                    }
                }
            }
        }
    }

    fun postStatusBanner(message: String) {
        _statusBanner.value = message
    }

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
        _messagePageSize.value = 100L
        _smartReplies.value = emptyList()
        _summaryResult.value = null
        _aiExplainResult.value = null
    }

    fun loadOlderMessages() {
        _messagePageSize.value = (_messagePageSize.value + 100L).coerceAtMost(500L)
    }

    fun markActiveChatRead(chat: ChatRoom, messages: List<ChatMessage>) {
        val profile = (myProfileState.value as? UiState.Success)?.data
        if (profile?.readReceipts == false && !chat.hasUnreadFor(currentUserId)) return
        viewModelScope.launch {
            chatRepository.markConversationRead(chat, messages)
        }
    }

    fun updateTyping(chat: ChatRoom, typing: Boolean) {
        viewModelScope.launch {
            chatRepository.setTypingStatus(chat, typing)
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun clearSummary() {
        _summaryResult.value = null
    }

    fun clearTranslation() {
        _translationResult.value = null
    }

    fun clearAiExplain() {
        _aiExplainResult.value = null
    }

    fun clearAiSearch() {
        _aiSearchResult.value = null
    }

    fun refreshStorageStats() {
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _storageBreakdown.value = LocalStorageManager.inspectStorage(ctx)
        }
    }

    fun clearLocalCache() {
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = LocalStorageManager.clearLocalCache(ctx)
            _storageBreakdown.value = updated
            _statusBanner.value = s(
                R.string.vm_cache_cleared,
                LocalStorageManager.formatByteCount(updated.backupBytes)
            )
        }
    }

    fun openDirectChat(peer: UserProfile) {
        val me = (myProfileState.value as? UiState.Success)?.data
            ?: UserProfile(userId = currentUserId, displayName = s(R.string.common_me_fallback).ifBlank { "Me" })
        viewModelScope.launch {
            chatRepository.createOrOpenDirectChat(me, peer)
                .onSuccess { chatId -> _selectedChatId.value = chatId }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_open_chat_error) }
        }
    }

    fun createGroupOrChannel(
        type: String,
        title: String,
        description: String,
        members: List<UserProfile>,
        publicVisible: Boolean,
        handle: String?,
        adminOnlyPost: Boolean
    ) {
        val gate = v6.checkFeatureAllowed(
            if (type == "CHANNEL") DlFeatureRestriction.CREATE_CHANNELS else DlFeatureRestriction.CREATE_GROUPS
        )
        if (gate != null) {
            _statusBanner.value = gate
            return
        }
        val me = (myProfileState.value as? UiState.Success)?.data
            ?: UserProfile(userId = currentUserId, displayName = s(R.string.common_me_fallback).ifBlank { "Me" })
        viewModelScope.launch {
            chatRepository.createGroupOrChannel(
                me, type, title, description, members, publicVisible, handle, adminOnlyPost
            ).onSuccess { chatId ->
                _selectedChatId.value = chatId
                _statusBanner.value = if (type == "CHANNEL") s(R.string.vm_channel_created) else s(R.string.vm_group_created)
            }.onFailure {
                _statusBanner.value = it.message ?: s(R.string.vm_create_failed)
            }
        }
    }

    fun joinChannel(chat: ChatRoom) {
        v6.checkFeatureAllowed(DlFeatureRestriction.JOIN_GROUPS)?.let {
            _statusBanner.value = it
            return
        }
        viewModelScope.launch {
            chatRepository.joinPublicChannel(chat)
                .onSuccess {
                    _selectedChatId.value = chat.id
                    _statusBanner.value = s(R.string.vm_joined_channel, chat.title)
                }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_join_failed) }
        }
    }

    fun requestJoinGroup(chatId: String) {
        v6.checkFeatureAllowed(DlFeatureRestriction.JOIN_GROUPS)?.let {
            _statusBanner.value = it
            return
        }
        viewModelScope.launch {
            chatRepository.requestJoinGroup(chatId)
                .onSuccess { _statusBanner.value = s(R.string.vm_join_request_sent) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_join_failed) }
        }
    }

    fun approveJoinRequest(chatId: String, applicant: UserProfile, approve: Boolean) {
        viewModelScope.launch {
            chatRepository.approveJoinRequest(chatId, applicant, approve)
                .onSuccess {
                    _statusBanner.value = if (approve) s(R.string.vm_request_approved, applicant.displayName) else s(R.string.vm_request_declined)
                }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_request_review_failed) }
        }
    }

    private fun tryEncryptForChat(chat: ChatRoom, plaintext: String): Triple<Boolean, String?, String?> {
        if (ChatType.fromRaw(chat.type) != ChatType.DIRECT || !chat.e2eeEnabled || appContext == null) {
            return Triple(false, null, null)
        }
        val peerUid = chat.memberUids.firstOrNull { it != currentUserId } ?: return Triple(false, null, null)
        val users = (discoverableUsersState.value as? UiState.Success)?.data.orEmpty()
        val peerPubKey = users.firstOrNull { it.userId == peerUid }?.publicKeyBase64
        val payload = CryptoIdentityHelper.encryptForDirectPeer(
            context = appContext,
            myUid = currentUserId,
            chatId = chat.id,
            peerPublicKeyBase64 = peerPubKey,
            plaintext = plaintext
        ) ?: return Triple(false, null, null)
        return Triple(true, payload.ciphertextBase64, payload.ivBase64)
    }

    private fun sendRestrictionFeatureForChat(chat: ChatRoom): DlFeatureRestriction = when (ChatType.fromRaw(chat.type)) {
        ChatType.CHANNEL -> DlFeatureRestriction.POST_CHANNELS
        ChatType.GROUP -> DlFeatureRestriction.SEND_GROUP
        else -> DlFeatureRestriction.SEND_DM
    }

    fun sendTextMessage(
        chat: ChatRoom,
        type: String = "TEXT",
        text: String,
        replyToId: String? = null,
        replyToPreview: String? = null,
        forwardedFrom: String? = null,
        scheduledForEpochMs: Long? = null,
        topicId: String? = null
    ) {
        val clean = text.trim()
        v6.checkOutgoingMessageAllowed(sendRestrictionFeatureForChat(chat), clean)?.let {
            _statusBanner.value = it
            return
        }
        val myProf = (myProfileState.value as? UiState.Success)?.data
        val meName = myProf?.displayName ?: s(R.string.common_member_fallback).ifBlank { "Member" }
        val shouldEncrypt = type == "TEXT" && ChatType.fromRaw(chat.type) == ChatType.DIRECT && chat.e2eeEnabled
        val (encrypted, cipherB64, ivB64) = if (shouldEncrypt) tryEncryptForChat(chat, clean) else Triple(false, null, null)
        val wireText = if (encrypted) s(R.string.vm_encrypted_wire_placeholder).ifBlank { "[Encrypted message]" } else clean

        viewModelScope.launch {
            chatRepository.sendMessage(
                chat = chat,
                senderName = meName,
                type = type,
                text = wireText,
                encrypted = encrypted,
                ciphertextBase64 = cipherB64,
                ivBase64 = ivB64,
                senderKeyVersion = if (encrypted) (myProf?.keyVersion ?: 1) else null,
                replyToId = replyToId,
                replyToPreview = replyToPreview,
                forwardedFrom = forwardedFrom,
                scheduledForEpochMs = scheduledForEpochMs,
                topicId = topicId
            ).onSuccess {
                v3.saveDraft(chat.id, "")
                v3.recordOfflineSyncOrFlush("MESSAGE", chat.id, "UPSERT", "Sent $type message")
            }.onFailure {
                _statusBanner.value = it.message ?: s(R.string.vm_send_failed)
            }
        }
    }

    fun sendQuizMessage(
        chat: ChatRoom,
        question: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String,
        topicId: String? = null
    ) {
        v6.checkOutgoingMessageAllowed(sendRestrictionFeatureForChat(chat), question)?.let {
            _statusBanner.value = it
            return
        }
        val meName = (myProfileState.value as? UiState.Success)?.data?.displayName ?: s(R.string.common_member_fallback).ifBlank { "Member" }
        viewModelScope.launch {
            chatRepository.sendMessage(
                chat = chat,
                senderName = meName,
                type = "QUIZ",
                text = question,
                pollQuestion = question,
                pollOptions = options,
                quizCorrectIndex = correctIndex,
                quizExplanation = explanation,
                topicId = topicId
            ).onSuccess {
                _statusBanner.value = s(R.string.vm_quiz_published)
            }.onFailure {
                _statusBanner.value = it.message ?: s(R.string.vm_quiz_failed)
            }
        }
    }

    fun sendStickerMessage(chat: ChatRoom, sticker: DlSticker, topicId: String? = null) {
        v6.checkOutgoingMessageAllowed(sendRestrictionFeatureForChat(chat), sticker.label)?.let {
            _statusBanner.value = it
            return
        }
        val meName = (myProfileState.value as? UiState.Success)?.data?.displayName ?: s(R.string.common_member_fallback).ifBlank { "Member" }
        viewModelScope.launch {
            chatRepository.sendMessage(
                chat = chat,
                senderName = meName,
                type = "STICKER",
                text = "${sticker.glyph} ${sticker.label} (${sticker.tag})",
                stickerId = sticker.id,
                topicId = topicId
            ).onFailure {
                _statusBanner.value = it.message ?: s(R.string.vm_sticker_failed)
            }
        }
    }

    fun sendImageMessage(context: Context, chat: ChatRoom, uri: Uri, caption: String) {
        v6.checkOutgoingMessageAllowed(DlFeatureRestriction.SEND_MEDIA, caption)?.let {
            _statusBanner.value = it
            return
        }
        val meName = (myProfileState.value as? UiState.Success)?.data?.displayName ?: s(R.string.common_member_fallback).ifBlank { "Member" }
        viewModelScope.launch {
            val encoded = ImageUtils.compressUriToBase64(context, uri)
            if (encoded == null) {
                _statusBanner.value = s(R.string.vm_photo_large_disclosure)
                return@launch
            }
            val meta = LocalStorageManager.readUriMetadata(context, uri)
            chatRepository.sendMessage(
                chat = chat,
                senderName = meName,
                type = "IMAGE",
                text = caption.ifBlank { s(R.string.vm_shared_image_fallback) },
                mediaDataBase64 = encoded,
                mediaStorageStatus = "INLINE_FIRESTORE_IMAGE",
                fileName = meta.displayName,
                fileMimeType = meta.mimeType,
                fileSizeBytes = meta.sizeBytes
            ).onFailure {
                _statusBanner.value = it.message ?: s(R.string.vm_image_upload_failed)
            }
        }
    }

    fun sendFileAttachment(context: Context, chat: ChatRoom, uri: Uri, msgType: String) {
        val gateFeature = if (msgType == "VIDEO" || msgType == "IMAGE") {
            DlFeatureRestriction.SEND_MEDIA
        } else {
            DlFeatureRestriction.SEND_FILES
        }
        v6.checkOutgoingMessageAllowed(gateFeature, msgType)?.let {
            _statusBanner.value = it
            return
        }
        val meName = (myProfileState.value as? UiState.Success)?.data?.displayName ?: s(R.string.common_member_fallback).ifBlank { "Member" }
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { LocalStorageManager.readUriMetadata(context, uri) }
            val formattedSize = LocalStorageManager.formatByteCount(meta.sizeBytes)
            chatRepository.sendMessage(
                chat = chat,
                senderName = meName,
                type = msgType,
                text = s(R.string.conv_file_local_only_note, meta.displayName, formattedSize),
                mediaStorageStatus = "LOCAL_ONLY_NO_CLOUD_STORAGE",
                fileName = meta.displayName,
                fileMimeType = meta.mimeType,
                fileSizeBytes = meta.sizeBytes
            ).onSuccess {
                _statusBanner.value = s(R.string.vm_file_large_disclosure, meta.displayName, formattedSize)
            }.onFailure {
                _statusBanner.value = it.message ?: s(R.string.vm_file_upload_failed)
            }
        }
    }

    fun sendVoiceNote(chat: ChatRoom, clip: RecordedVoiceClip) {
        v6.checkOutgoingMessageAllowed(DlFeatureRestriction.SEND_VOICE, "voice")?.let {
            _statusBanner.value = it
            return
        }
        val meName = (myProfileState.value as? UiState.Success)?.data?.displayName ?: s(R.string.common_member_fallback).ifBlank { "Member" }
        viewModelScope.launch {
            val sec = (clip.durationMs / 1000L).coerceAtLeast(1L)
            val durationLabel = "0:${"%02d".format(sec)}"
            val storageStatus = if (clip.base64Audio != null) "INLINE_FIRESTORE_AUDIO" else "LOCAL_CACHE_ONLY_NO_CLOUD_STORAGE"
            chatRepository.sendMessage(
                chat = chat,
                senderName = meName,
                type = "VOICE_NOTE",
                text = if (clip.base64Audio != null) s(R.string.vm_voice_shared_msg, durationLabel)
                else s(R.string.conv_voice_over_limit_note),
                mediaDataBase64 = clip.base64Audio,
                mediaStorageStatus = storageStatus,
                fileName = "voice_note_${sec}s.m4a",
                fileMimeType = "audio/mp4",
                fileSizeBytes = clip.fileSizeBytes,
                audioDurationMs = clip.durationMs,
                waveformSamples = clip.waveform
            ).onSuccess {
                refreshStorageStats()
                if (clip.base64Audio == null) {
                    _statusBanner.value = s(R.string.vm_voice_large_disclosure, durationLabel)
                }
            }.onFailure {
                _statusBanner.value = it.message ?: s(R.string.vm_voice_failed)
            }
        }
    }

    fun sendPollMessage(chat: ChatRoom, question: String, options: List<String>) {
        v6.checkOutgoingMessageAllowed(sendRestrictionFeatureForChat(chat), question)?.let {
            _statusBanner.value = it
            return
        }
        val meName = (myProfileState.value as? UiState.Success)?.data?.displayName ?: s(R.string.common_member_fallback).ifBlank { "Member" }
        viewModelScope.launch {
            chatRepository.sendMessage(
                chat = chat,
                senderName = meName,
                type = "POLL",
                text = question,
                pollQuestion = question,
                pollOptions = options
            ).onFailure {
                _statusBanner.value = it.message ?: s(R.string.vm_poll_failed)
            }
        }
    }

    fun editMessage(chat: ChatRoom, messageId: String, newText: String) {
        val clean = newText.trim()
        val (encrypted, cipherB64, ivB64) = tryEncryptForChat(chat, clean)
        val wireText = if (encrypted) s(R.string.vm_encrypted_wire_placeholder).ifBlank { "[Encrypted message]" } else clean
        viewModelScope.launch {
            chatRepository.editMessage(chat.id, messageId, wireText, encrypted, cipherB64, ivB64)
                .onSuccess { _statusBanner.value = s(R.string.vm_msg_edited) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_edit_failed) }
        }
    }

    fun deleteMessageForEveryone(chatId: String, messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessageForEveryone(chatId, messageId)
                .onSuccess { _statusBanner.value = s(R.string.vm_msg_deleted) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_delete_failed) }
        }
    }

    fun toggleStarMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            chatRepository.toggleStarMessage(chatId, messageId)
                .onSuccess { _statusBanner.value = s(R.string.vm_msg_starred) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_star_failed) }
        }
    }

    fun toggleReaction(chatId: String, message: ChatMessage, emoji: String) {
        v6.checkFeatureAllowed(DlFeatureRestriction.COMMENT_POSTS)?.let {
            _statusBanner.value = it
            return
        }
        viewModelScope.launch {
            chatRepository.toggleReaction(chatId, message, emoji)
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_react_failed) }
        }
    }

    fun votePoll(chatId: String, message: ChatMessage, optionIndex: Int) {
        viewModelScope.launch {
            chatRepository.votePoll(chatId, message, optionIndex)
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_vote_failed) }
        }
    }

    fun pinMessage(chat: ChatRoom, message: ChatMessage) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(chat, pinMessage = message)
                .onSuccess { _statusBanner.value = s(R.string.vm_msg_pinned) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_pin_failed) }
        }
    }

    fun toggleChatArchive(chat: ChatRoom) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(chat, toggleArchive = true)
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_archive_failed) }
        }
    }

    fun toggleChatMute(chat: ChatRoom) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(chat, toggleMute = true)
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_mute_failed) }
        }
    }

    fun setDisappearingSeconds(chat: ChatRoom, seconds: Long) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(chat, disappearingSeconds = seconds)
                .onSuccess { _statusBanner.value = s(R.string.vm_timer_updated) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_timer_failed) }
        }
    }

    fun toggleAdminOnlyPost(chat: ChatRoom, enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(chat, toggleAdminOnly = enabled)
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_perm_failed) }
        }
    }

    fun regenerateInviteCode(chat: ChatRoom) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(chat, regenerateInvite = true)
                .onSuccess { _statusBanner.value = s(R.string.vm_invite_rotated) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_invite_failed) }
        }
    }

    fun revokeInviteCode(chat: ChatRoom, revoke: Boolean) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(chat, revokeInvite = revoke)
                .onSuccess { _statusBanner.value = if (revoke) s(R.string.vm_invite_revoked) else s(R.string.vm_invite_enabled) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_invite_failed) }
        }
    }

    fun updateGroupPermissions(chat: ChatRoom, allowPinning: Boolean, allowInvites: Boolean) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(
                chat,
                allowPinning = allowPinning,
                allowMemberInvites = allowInvites,
                activityNote = "Updated permissions (pin=$allowPinning, invites=$allowInvites)"
            ).onFailure { _statusBanner.value = it.message ?: s(R.string.vm_perm_failed) }
        }
    }

    fun updateChatThemeAccent(chat: ChatRoom, hex: String) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(chat, themeAccentHex = hex)
                .onSuccess { _statusBanner.value = s(R.string.vm_theme_updated) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_theme_failed) }
        }
    }

    fun addMemberToChat(chat: ChatRoom, user: UserProfile) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(chat, addMember = user)
                .onSuccess { _statusBanner.value = s(R.string.vm_member_added, user.displayName) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_member_add_failed) }
        }
    }

    fun promoteMemberAdmin(chat: ChatRoom, uid: String) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(chat, promoteAdminUid = uid)
                .onSuccess { _statusBanner.value = s(R.string.vm_member_promoted) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_member_promote_failed) }
        }
    }

    fun removeMemberFromChat(chat: ChatRoom, uid: String) {
        viewModelScope.launch {
            chatRepository.updateChatMetadata(chat, removeMemberUid = uid)
                .onSuccess { _statusBanner.value = s(R.string.vm_member_removed) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_member_remove_failed) }
        }
    }

    fun saveMessageOrNote(text: String, sourceChatName: String, sourceMessageId: String? = null, category: String = "SAVED") {
        viewModelScope.launch {
            workspaceRepository.savePersonalNoteOrMessage(text, sourceChatName, sourceMessageId, category)
                .onSuccess { _statusBanner.value = s(R.string.vm_saved_to_vault) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_save_failed) }
        }
    }

    fun deleteSavedNote(savedId: String) {
        viewModelScope.launch {
            workspaceRepository.deleteSavedMessage(savedId)
                .onSuccess { _statusBanner.value = s(R.string.vm_note_deleted) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_delete_failed) }
        }
    }

    fun sendContactRequest(target: UserProfile) {
        v6.checkFeatureAllowed(DlFeatureRestriction.SEND_INVITES)?.let {
            _statusBanner.value = it
            return
        }
        val me = (myProfileState.value as? UiState.Success)?.data
            ?: UserProfile(userId = currentUserId, displayName = s(R.string.common_member_fallback).ifBlank { "Member" }, username = "member")
        viewModelScope.launch {
            workspaceRepository.sendContactRequest(me, target)
                .onSuccess { _statusBanner.value = s(R.string.vm_contact_req_sent, target.displayName) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_contact_req_failed) }
        }
    }

    fun respondContactRequest(request: ContactRequest, accept: Boolean) {
        viewModelScope.launch {
            workspaceRepository.respondContactRequest(request, accept)
                .onSuccess {
                    _statusBanner.value = if (accept) s(R.string.vm_contact_req_accepted) else s(R.string.vm_contact_req_declined)
                }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_contact_resp_failed) }
        }
    }

    fun createStory(context: Context, text: String, bgHex: String, imageUri: Uri?, hiddenFrom: List<String>) {
        v6.checkFeatureAllowed(DlFeatureRestriction.POST_STORIES)?.let {
            _statusBanner.value = it
            return
        }
        val meName = (myProfileState.value as? UiState.Success)?.data?.displayName ?: s(R.string.common_member_fallback).ifBlank { "Member" }
        viewModelScope.launch {
            val encoded = imageUri?.let { ImageUtils.compressUriToBase64(context, it) }
            val type = if (encoded != null) "IMAGE" else "TEXT"
            chatRepository.createStory(
                authorName = meName,
                type = type,
                text = text,
                backgroundHex = bgHex,
                mediaDataBase64 = encoded,
                hiddenFromUids = hiddenFrom
            ).onSuccess {
                _statusBanner.value = s(R.string.vm_story_published)
            }.onFailure {
                _statusBanner.value = it.message ?: s(R.string.vm_story_failed)
            }
        }
    }

    fun viewOrReactStory(story: StoryItem, emoji: String? = null) {
        viewModelScope.launch {
            chatRepository.markStoryViewedAndReact(story, emoji)
        }
    }

    fun startCall(peer: UserProfile, callType: String) {
        v6.checkFeatureAllowed(DlFeatureRestriction.START_CALLS)?.let {
            _statusBanner.value = it
            return
        }
        val meName = (myProfileState.value as? UiState.Success)?.data?.displayName ?: s(R.string.common_member_fallback).ifBlank { "Member" }
        viewModelScope.launch {
            chatRepository.startCall(meName, peer, callType)
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_call_start_failed) }
        }
    }

    fun updateCallStatus(callId: String, status: String, durationSeconds: Long) {
        viewModelScope.launch {
            chatRepository.updateCallStatus(callId, status, durationSeconds)
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_call_update_failed) }
        }
    }

    fun toggleContactFavorite(user: UserProfile, existing: ContactEntry?) {
        viewModelScope.launch {
            val nextFav = !(existing?.favorite ?: false)
            workspaceRepository.upsertContact(
                contactUid = user.userId,
                displayName = user.displayName,
                username = user.username,
                favorite = nextFav,
                blocked = existing?.blocked ?: false
            ).onSuccess {
                _statusBanner.value = if (nextFav) s(R.string.vm_fav_added, user.displayName) else s(R.string.vm_fav_removed, user.displayName)
            }.onFailure { _statusBanner.value = it.message ?: s(R.string.vm_contact_update_failed) }
        }
    }

    fun toggleBlockUser(user: UserProfile, existing: ContactEntry?) {
        viewModelScope.launch {
            val nowBlocked = !(existing?.blocked ?: false)
            workspaceRepository.upsertContact(
                contactUid = user.userId,
                displayName = user.displayName,
                username = user.username,
                favorite = existing?.favorite ?: false,
                blocked = nowBlocked
            ).onSuccess {
                _statusBanner.value = if (nowBlocked) s(R.string.vm_user_blocked, user.displayName) else s(R.string.vm_user_unblocked, user.displayName)
            }.onFailure { _statusBanner.value = it.message ?: s(R.string.vm_contact_update_failed) }
        }
    }

    fun createFolder(
        name: String,
        filterType: String,
        includeIds: List<String> = emptyList(),
        excludeIds: List<String> = emptyList(),
        iconName: String = "Folder",
        sortOrder: Int = 0
    ) {
        viewModelScope.launch {
            workspaceRepository.createFolder(name, filterType, includeIds, excludeIds, iconName, sortOrder)
                .onSuccess { _statusBanner.value = s(R.string.vm_folder_created) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_folder_failed) }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            workspaceRepository.deleteFolder(folderId)
                .onSuccess { _statusBanner.value = s(R.string.vm_folder_deleted) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_folder_failed) }
        }
    }

    fun rotateIdentityKeys(context: Context) {
        viewModelScope.launch {
            val next = CryptoIdentityHelper.rotateIdentityKey(context, currentUserId)
            workspaceRepository.updateIdentityKey(next)
                .onSuccess { _statusBanner.value = s(R.string.vm_keys_rotated, next.keyVersion) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_keys_sync_failed) }
        }
    }

    fun exportEncryptedBackup(context: Context, uri: Uri, passphrase: String) {
        viewModelScope.launch {
            try {
                if (passphrase.length < 6) {
                    _statusBanner.value = s(R.string.vm_backup_passphrase_short)
                    return@launch
                }
                val chats = (myChatsState.value as? UiState.Success)?.data.orEmpty()
                val saved = (savedMessagesState.value as? UiState.Success)?.data.orEmpty()
                val contacts = (contactsState.value as? UiState.Success)?.data.orEmpty()

                val root = JSONObject().apply {
                    put("exportedAtEpochMs", System.currentTimeMillis())
                    put("uid", currentUserId)
                    put("chats", JSONArray().apply {
                        chats.forEach { c ->
                            put(JSONObject().put("id", c.id).put("title", c.title).put("type", c.type).put("last", c.lastMessageText))
                        }
                    })
                    put("savedNotes", JSONArray().apply {
                        saved.forEach { sItem ->
                            put(JSONObject().put("id", sItem.id).put("text", sItem.text).put("source", sItem.sourceChatName))
                        }
                    })
                    put("contactsCount", contacts.size)
                }
                val encryptedText = withContext(Dispatchers.Default) {
                    CryptoIdentityHelper.encryptBackupJson(root.toString(), passphrase)
                }
                val bytes = encryptedText.toByteArray(Charsets.UTF_8)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException(s(R.string.vm_backup_read_error))
                }
                val totalItems = chats.size + saved.size + contacts.size
                workspaceRepository.recordBackup("dlchat_backup.dlbkp", totalItems, bytes.size.toLong())
                _statusBanner.value = s(R.string.vm_backup_saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.message ?: s(R.string.vm_backup_read_error)
            }
        }
    }

    fun restoreEncryptedBackup(context: Context, uri: Uri, passphrase: String) {
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                } ?: throw IllegalStateException(s(R.string.vm_backup_read_error))
                val plainJson = withContext(Dispatchers.Default) {
                    CryptoIdentityHelper.decryptBackupJson(raw, passphrase)
                } ?: throw IllegalArgumentException(s(R.string.vm_backup_wrong_pass))
                val obj = JSONObject(plainJson)
                val notes = obj.optJSONArray("savedNotes") ?: JSONArray()
                var restoredCount = 0
                for (i in 0 until minOf(notes.length(), 25)) {
                    val item = notes.getJSONObject(i)
                    val txt = item.optString("text")
                    if (txt.isNotBlank()) {
                        workspaceRepository.savePersonalNoteOrMessage(
                            text = txt,
                            sourceChatName = item.optString("source", s(R.string.settings_vault_badge)),
                            noteCategory = "RESTORED"
                        )
                        restoredCount++
                    }
                }
                _statusBanner.value = s(R.string.vm_backup_restored, restoredCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.message ?: s(R.string.vm_backup_wrong_pass)
            }
        }
    }

    fun updateProfile(
        context: Context,
        username: String,
        displayName: String,
        bio: String,
        avatarColor: String,
        avatarUri: Uri?,
        onlineStatus: String,
        showLastSeen: Boolean,
        showOnline: Boolean,
        readReceipts: Boolean,
        discoverable: Boolean,
        twoFactorEnabled: Boolean = false,
        profilePhotoVisibility: String = "EVERYONE",
        statusMessage: String? = null
    ) {
        viewModelScope.launch {
            val encoded = avatarUri?.let { ImageUtils.compressUriToBase64(context, it, maxDimension = 360) }
            workspaceRepository.updateProfile(
                username, displayName, bio, avatarColor, encoded,
                onlineStatus, showLastSeen, showOnline, readReceipts, discoverable,
                twoFactorEnabled, profilePhotoVisibility, statusMessage
            ).onSuccess {
                _statusBanner.value = s(R.string.vm_profile_saved)
            }.onFailure {
                _statusBanner.value = it.message ?: s(R.string.vm_profile_failed)
            }
        }
    }

    fun updateSettings(setting: UserSetting) {
        viewModelScope.launch {
            preferencesRepository.saveAppearance(
                AppearancePrefs(
                    themeMode = setting.themeMode,
                    accentColorHex = setting.accentColorHex,
                    bubbleStyle = setting.bubbleStyle,
                    fontScale = setting.fontScale.toFloat(),
                    wallpaperId = setting.wallpaperId
                )
            )
            workspaceRepository.updateSettings(setting)
                .onSuccess { _statusBanner.value = s(R.string.vm_settings_saved) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_settings_sync_failed) }
        }
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            workspaceRepository.revokeDevice(deviceId)
                .onSuccess { _statusBanner.value = s(R.string.vm_device_revoked) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_revoke_failed) }
        }
    }

    fun submitReport(targetUid: String, targetType: String, reason: String) {
        viewModelScope.launch {
            workspaceRepository.submitReport(targetUid, targetType, reason)
                .onSuccess { _statusBanner.value = s(R.string.vm_report_sent) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_report_failed) }
        }
    }

    fun adminReviewReport(reportId: String, status: String) {
        viewModelScope.launch {
            workspaceRepository.adminUpdateReportStatus(reportId, status)
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_admin_action_failed) }
        }
    }

    fun adminToggleSuspendUser(targetUid: String, suspended: Boolean) {
        viewModelScope.launch {
            workspaceRepository.adminSetUserSuspended(targetUid, suspended)
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_admin_action_failed) }
        }
    }

    // --- DL AI Features (Firebase AI Logic) ---

    fun resetAiSession() {
        _aiMessages.value = listOf(
            AiAssistantMessage(
                id = "welcome_${System.currentTimeMillis()}",
                fromUser = false,
                text = s(R.string.vm_ai_reset)
            )
        )
    }

    fun askDlAi(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || _aiBusy.value) return
        val history = _aiMessages.value.map { it.fromUser to it.text }
        val userMsg = AiAssistantMessage(id = "u_${System.currentTimeMillis()}", fromUser = true, text = clean)
        val aiMsgId = "a_${System.currentTimeMillis()}"
        _aiMessages.value = _aiMessages.value + userMsg + AiAssistantMessage(
            id = aiMsgId,
            fromUser = false,
            text = "",
            streaming = true
        )
        _aiBusy.value = true

        viewModelScope.launch {
            try {
                aiRepository.streamAssistantReply(clean, history).collect { partial ->
                    _aiMessages.value = _aiMessages.value.map {
                        if (it.id == aiMsgId) it.copy(text = partial, streaming = true) else it
                    }
                }
                _aiMessages.value = _aiMessages.value.map {
                    if (it.id == aiMsgId) it.copy(streaming = false) else it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiMessages.value = _aiMessages.value.map {
                    if (it.id == aiMsgId) {
                        it.copy(
                            text = s(R.string.vm_ai_error, e.localizedMessage ?: ""),
                            streaming = false
                        )
                    } else it
                }
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun summarizeChat(chat: ChatRoom, messages: List<ChatMessage>) {
        if (chat.e2eeEnabled) {
            _statusBanner.value = s(R.string.vm_ai_e2ee_summary_blocked)
            return
        }
        val transcript = messages.takeLast(25)
            .filter { !it.deletedForEveryone && it.text.isNotBlank() }
            .joinToString("\n") { "${it.senderName}: ${it.text}" }
        if (transcript.isBlank()) {
            _statusBanner.value = s(R.string.vm_ai_no_messages)
            return
        }
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepository.summarizeConversation(chat.resolveTitle(currentUserId), transcript)
                .onSuccess { _summaryResult.value = it }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_ai_summary_fallback) }
            _aiBusy.value = false
        }
    }

    fun translateMessage(message: ChatMessage, language: String) {
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepository.translateMessage(message.text, language)
                .onSuccess { translated -> _translationResult.value = language to translated }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_ai_translate_fallback) }
            _aiBusy.value = false
        }
    }

    fun explainMessage(message: ChatMessage) {
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepository.explainMessage(message.text)
                .onSuccess { explanation -> _aiExplainResult.value = explanation }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_ai_explain_fallback) }
            _aiBusy.value = false
        }
    }

    fun aiSearchAssist(query: String, chats: List<ChatRoom>) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        val nonE2eeContext = chats.filter { !it.e2eeEnabled && it.lastMessageText.isNotBlank() }
            .take(25)
            .joinToString("\n") { "- [${it.type}] ${it.resolveTitle(currentUserId)}: ${it.lastMessageText}" }
        if (nonE2eeContext.isBlank()) {
            _aiSearchResult.value = s(R.string.vm_ai_e2ee_search_blocked)
            return
        }
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepository.aiSearchAssist(clean, nonE2eeContext)
                .onSuccess { _aiSearchResult.value = it }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_ai_search_fallback) }
            _aiBusy.value = false
        }
    }

    fun suggestSmartReplies(chat: ChatRoom, messages: List<ChatMessage>) {
        if (chat.e2eeEnabled) {
            _statusBanner.value = s(R.string.vm_ai_e2ee_replies_blocked)
            return
        }
        val context = messages.takeLast(5)
            .filter { !it.deletedForEveryone && it.text.isNotBlank() }
            .joinToString("\n") { "${it.senderName}: ${it.text}" }
        if (context.isBlank()) return
        viewModelScope.launch {
            aiRepository.generateSmartReplies(context)
                .onSuccess { _smartReplies.value = it }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_ai_smart_reply_failed) }
        }
    }

    fun rewriteDraft(draft: String, tone: String, onResult: (String) -> Unit) {
        if (draft.isBlank()) return
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepository.rewriteDraft(draft, tone)
                .onSuccess(onResult)
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_ai_rewrite_failed) }
            _aiBusy.value = false
        }
    }

    fun summarizeVoiceMessageTranscript(chatId: String, messageId: String, transcriptText: String) {
        if (transcriptText.isBlank()) return
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepository.summarizeVoiceTranscript(transcriptText)
                .onSuccess { summary ->
                    chatRepository.saveMessageAiTranscript(chatId, messageId, summary)
                    _statusBanner.value = s(R.string.vm_ai_voice_done)
                }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_ai_voice_summary_fallback) }
            _aiBusy.value = false
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authRepository.signOut(context)
        }
    }

    fun deleteAccount(context: Context) {
        viewModelScope.launch {
            authRepository.deleteAccount(context)
                .onSuccess { _statusBanner.value = s(R.string.vm_account_deleted) }
                .onFailure { _statusBanner.value = it.message ?: s(R.string.vm_delete_reauth) }
        }
    }

    fun linkPhoneCredential(
        context: Context,
        credential: PhoneAuthCredential,
        country: CountryDialCode,
        whatsappOptIn: Boolean,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            authRepository.linkOrUpdatePhoneCredential(
                context = context,
                credential = credential,
                country = country,
                otpChannel = OtpChannel.FIREBASE_SMS,
                whatsappOptIn = whatsappOptIn
            ).onSuccess {
                _statusBanner.value = "Phone number linked and verified."
                onComplete(true, null)
            }.onFailure { err ->
                val msg = err.message ?: "Couldn't link phone number."
                _statusBanner.value = msg
                onComplete(false, msg)
            }
        }
    }

    private companion object {
        const val TAG = "DlChatVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
