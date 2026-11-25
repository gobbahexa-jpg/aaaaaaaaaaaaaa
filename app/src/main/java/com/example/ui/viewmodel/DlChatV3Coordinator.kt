package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.R
import com.example.core.DeathLegionOAuthClient
import com.example.core.DeathLegionUserInfo
import com.example.core.LocalAccountSlot
import com.example.core.MultiAccountManager
import com.example.core.OfflineSyncEngine
import com.example.core.ParsedDlQrPayload
import com.example.core.PendingSyncMutation
import com.example.core.QrCodeUtils
import com.example.core.SecurityPrivacyManager
import com.example.data.model.AccountSessionRecord
import com.example.data.model.ChatDraft
import com.example.data.model.ChatExportRecord
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.ChatTopic
import com.example.data.model.DeviceKeyBundle
import com.example.data.model.DeviceNotification
import com.example.data.model.DevicePairingSession
import com.example.data.model.DeviceSession
import com.example.data.model.GroupEvent
import com.example.data.model.PrivateChatSetting
import com.example.data.model.SyncEvent
import com.example.data.model.UiState
import com.example.data.model.UserSetting
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.CreatedPairingChallenge
import com.example.data.repository.GroupForumRepository
import com.example.data.repository.LinkedDevicesRepository
import com.example.data.repository.UserWorkspaceRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class DlChatV3Coordinator(
    private val scope: CoroutineScope,
    private val currentUserId: String,
    private val appContext: Context?,
    private val activeChatIdFlow: StateFlow<String?>,
    private val authRepository: AuthRepository,
    private val workspaceRepository: UserWorkspaceRepository,
    private val chatRepository: ChatRepository,
    private val linkedRepo: LinkedDevicesRepository?,
    private val forumRepo: GroupForumRepository?,
    private val onBanner: (String) -> Unit,
    private val resolveString: (Int, Array<out Any>) -> String = { resId, args ->
        if (appContext != null) {
            if (args.isEmpty()) appContext.getString(resId) else appContext.getString(resId, *args)
        } else ""
    }
) {
    private fun s(resId: Int, vararg args: Any): String = resolveString(resId, args)

    val currentDeviceId: String =
        ("android_${Build.MODEL}").replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)

    private fun <T> Flow<T>.toState(tag: String, initial: T): StateFlow<UiState<T>> = this
        .map<T, UiState<T>> { UiState.Success(it) }
        .catch { err ->
            val ffe = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (ffe == null && err is CancellationException) throw err
            Log.w("DlChatV3", "$tag error", ffe ?: err)
            emit(UiState.Error((ffe ?: err).message ?: "Stream error"))
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000L), UiState.Success(initial))

    val pairingSessionsState: StateFlow<UiState<List<DevicePairingSession>>> =
        (linkedRepo?.observePairingSessions(currentUserId) ?: flowOf(emptyList()))
            .toState("Pairing", emptyList())

    val deviceNotificationsState: StateFlow<UiState<List<DeviceNotification>>> =
        (linkedRepo?.observeDeviceNotifications(currentUserId) ?: flowOf(emptyList()))
            .toState("DeviceNotifs", emptyList())

    val syncEventsState: StateFlow<UiState<List<SyncEvent>>> =
        (linkedRepo?.observeSyncEvents(currentUserId) ?: flowOf(emptyList()))
            .toState("SyncEvents", emptyList())

    val deviceKeyBundlesState: StateFlow<UiState<List<DeviceKeyBundle>>> =
        (linkedRepo?.observeDeviceKeyBundles(currentUserId) ?: flowOf(emptyList()))
            .toState("KeyBundles", emptyList())

    val accountSessionsState: StateFlow<UiState<List<AccountSessionRecord>>> =
        (linkedRepo?.observeAccountSessions(currentUserId) ?: flowOf(emptyList()))
            .toState("Accounts", emptyList())

    val privateChatSettingsState: StateFlow<UiState<List<PrivateChatSetting>>> =
        (linkedRepo?.observePrivateChatSettings(currentUserId) ?: flowOf(emptyList()))
            .toState("PrivateChats", emptyList())

    val chatDraftsState: StateFlow<UiState<List<ChatDraft>>> =
        (linkedRepo?.observeChatDrafts(currentUserId) ?: flowOf(emptyList()))
            .toState("Drafts", emptyList())

    val chatExportsState: StateFlow<UiState<List<ChatExportRecord>>> =
        (linkedRepo?.observeChatExports(currentUserId) ?: flowOf(emptyList()))
            .toState("Exports", emptyList())

    val activeTopicsState: StateFlow<UiState<List<ChatTopic>>> =
        activeChatIdFlow.flatMapLatest { chatId ->
            if (chatId == null || forumRepo == null) flowOf(emptyList())
            else forumRepo.observeTopics(chatId)
        }.toState("Topics", emptyList())

    val activeEventsState: StateFlow<UiState<List<GroupEvent>>> =
        activeChatIdFlow.flatMapLatest { chatId ->
            if (chatId == null || forumRepo == null) flowOf(emptyList())
            else forumRepo.observeGroupEvents(chatId)
        }.toState("Events", emptyList())

    private val _activeChallenge = MutableStateFlow<CreatedPairingChallenge?>(null)
    val activeChallenge: StateFlow<CreatedPairingChallenge?> = _activeChallenge.asStateFlow()

    private val _appLocked = MutableStateFlow(false)
    val appLocked: StateFlow<Boolean> = _appLocked.asStateFlow()

    private val _pendingSyncCount = MutableStateFlow(
        appContext?.let { OfflineSyncEngine.getPendingMutations(it, currentUserId).size } ?: 0
    )
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    private val _localAccounts = MutableStateFlow(
        appContext?.let { MultiAccountManager.listAccounts(it) }.orEmpty()
    )
    val localAccounts: StateFlow<List<LocalAccountSlot>> = _localAccounts.asStateFlow()

    private val _hiddenUnlocked = MutableStateFlow(false)
    val hiddenUnlocked: StateFlow<Boolean> = _hiddenUnlocked.asStateFlow()

    fun setAppLocked(locked: Boolean) {
        _appLocked.value = locked
    }

    fun setHiddenVaultUnlocked(unlocked: Boolean) {
        _hiddenUnlocked.value = unlocked
    }

    fun checkAutoLockOnForeground(settings: UserSetting?) {
        val ctx = appContext ?: return
        if (settings != null && SecurityPrivacyManager.shouldAutoLock(
                ctx,
                currentUserId,
                settings.autoLockSeconds,
                settings.appLockEnabled
            )
        ) {
            _appLocked.value = true
            _hiddenUnlocked.value = false
        }
    }

    fun recordBackgroundForAutoLock() {
        val ctx = appContext ?: return
        SecurityPrivacyManager.recordBackgroundTimestamp(ctx, currentUserId)
    }

    fun generatePairingQr(
        deviceName: String,
        deviceType: String,
        osName: String,
        browserName: String
    ) {
        val repo = linkedRepo ?: return
        scope.launch {
            repo.createPairingChallenge(deviceName, deviceType, osName, browserName)
                .onSuccess {
                    _activeChallenge.value = it
                    onBanner(s(R.string.v3_qr_created))
                }
                .onFailure { onBanner(it.message ?: s(R.string.v3_qr_create_failed)) }
        }
    }

    fun cancelActivePairing(sessionId: String) {
        val repo = linkedRepo ?: return
        scope.launch {
            repo.cancelPairingSession(sessionId)
                .onSuccess {
                    if (_activeChallenge.value?.sessionId == sessionId) {
                        _activeChallenge.value = null
                    }
                    onBanner(s(R.string.vm_qr_cancelled))
                }
                .onFailure { onBanner(it.message ?: s(R.string.common_cancel)) }
        }
    }

    fun confirmScannedPairingQr(rawQrText: String, onInviteScanned: (String?, String) -> Unit = { _, _ -> }) {
        when (val parsed = QrCodeUtils.parseQrContent(rawQrText)) {
            is ParsedDlQrPayload.PairingQr -> {
                if (parsed.ownerUid != currentUserId) {
                    onBanner(s(R.string.v3_qr_wrong_account))
                    return
                }
                val repo = linkedRepo ?: return
                scope.launch {
                    repo.confirmPairingAndRegisterDevice(parsed.sessionId, parsed.rawToken)
                        .onSuccess {
                            _activeChallenge.value = null
                            onBanner(s(R.string.vm_qr_paired))
                        }
                        .onFailure { onBanner(it.message ?: s(R.string.v3_qr_confirm_failed)) }
                }
            }
            is ParsedDlQrPayload.GroupInviteQr -> {
                joinByInviteQr(parsed.chatId, parsed.inviteCode) { openedId ->
                    onInviteScanned(openedId, parsed.inviteCode)
                }
            }
            is ParsedDlQrPayload.UserProfileQr -> onBanner("@${parsed.username}")
            is ParsedDlQrPayload.Unknown -> onBanner(s(R.string.vm_qr_unrecognized))
        }
    }

    fun joinByInviteQr(chatId: String?, inviteCode: String, onOpenChat: (String) -> Unit = {}) {
        val repo = forumRepo ?: return
        scope.launch {
            repo.joinGroupByInvite(chatId, inviteCode)
                .onSuccess { (resolvedChatId, outcome) ->
                    when (outcome) {
                        "OPENED", "JOINED" -> {
                            onOpenChat(resolvedChatId)
                            onBanner(s(R.string.vm_invite_joined))
                        }
                        else -> onBanner(s(R.string.vm_invite_requested))
                    }
                }
                .onFailure { onBanner(it.message ?: s(R.string.vm_join_failed)) }
        }
    }

    fun confirmManualPairing(sessionId: String, rawToken: String) {
        val repo = linkedRepo ?: return
        scope.launch {
            repo.confirmPairingAndRegisterDevice(sessionId.trim(), rawToken.trim())
                .onSuccess {
                    _activeChallenge.value = null
                    onBanner(s(R.string.v3_manual_paired))
                }
                .onFailure { onBanner(it.message ?: s(R.string.v3_manual_pair_failed)) }
        }
    }

    fun renameDevice(deviceId: String, newName: String) {
        val repo = linkedRepo ?: return
        scope.launch {
            repo.renameDevice(deviceId, newName)
                .onSuccess { onBanner(s(R.string.vm_device_renamed)) }
                .onFailure { onBanner(it.message ?: s(R.string.v3_rename_failed)) }
        }
    }

    fun rotateDeviceToken(device: DeviceSession) {
        val repo = linkedRepo ?: return
        scope.launch {
            repo.rotateDeviceSessionToken(device.id, device.tokenRotationCount)
                .onSuccess { onBanner(s(R.string.v3_token_rotated, device.deviceName)) }
                .onFailure { onBanner(it.message ?: s(R.string.v3_token_rotate_failed)) }
        }
    }

    fun revokeAllOtherDevices() {
        val repo = linkedRepo ?: return
        scope.launch {
            repo.revokeAllOtherDevices(currentDeviceId)
                .onSuccess { count -> onBanner(s(R.string.vm_devices_revoked_others, count)) }
                .onFailure { onBanner(it.message ?: s(R.string.v3_bulk_revoke_failed)) }
        }
    }

    fun markNotificationRead(notifId: String) {
        val repo = linkedRepo ?: return
        scope.launch { repo.markNotificationRead(notifId) }
    }

    fun toggleChatHidden(chatId: String, currentlyHidden: Boolean, currentlyLocked: Boolean) {
        val repo = linkedRepo ?: return
        scope.launch {
            repo.upsertPrivateChatSetting(chatId, hidden = !currentlyHidden, locked = currentlyLocked)
                .onSuccess {
                    onBanner(if (!currentlyHidden) s(R.string.vm_chat_hidden) else s(R.string.vm_chat_unhidden))
                }
                .onFailure { onBanner(it.message ?: s(R.string.v3_hide_failed)) }
        }
    }

    fun toggleChatLocked(chatId: String, currentlyHidden: Boolean, currentlyLocked: Boolean) {
        val repo = linkedRepo ?: return
        scope.launch {
            repo.upsertPrivateChatSetting(chatId, hidden = currentlyHidden, locked = !currentlyLocked)
                .onSuccess {
                    onBanner(if (!currentlyLocked) s(R.string.vm_chat_locked) else s(R.string.vm_chat_unlocked))
                }
                .onFailure { onBanner(it.message ?: s(R.string.v3_lock_failed)) }
        }
    }

    fun saveDraft(chatId: String, text: String, replyToId: String? = null) {
        val repo = linkedRepo ?: return
        scope.launch {
            repo.saveChatDraft(chatId, text, replyToId)
            recordOfflineSyncOrFlush("DRAFT", chatId, "UPSERT", "Synced draft (${text.length} chars)")
        }
    }

    fun exportSingleChat(context: Context, uri: Uri, chat: ChatRoom, messages: List<ChatMessage>, format: String) {
        scope.launch {
            try {
                val title = chat.resolveTitle(currentUserId)
                val formatted = withContext(Dispatchers.Default) {
                    chatRepository.formatChatExport(title, messages, format)
                }
                val bytes = formatted.toByteArray(Charsets.UTF_8)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException(s(R.string.v3_export_failed))
                }
                linkedRepo?.recordChatExport(chat.id, title, format, messages.size, bytes.size.toLong())
                onBanner(s(R.string.vm_chat_exported, title, messages.size))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onBanner(e.message ?: s(R.string.v3_export_failed))
            }
        }
    }

    fun createTopic(chat: ChatRoom, title: String, iconEmoji: String) {
        val repo = forumRepo ?: return
        scope.launch {
            repo.createTopic(chat, title, iconEmoji)
                .onSuccess { onBanner(s(R.string.vm_topic_created, title)) }
                .onFailure { onBanner(it.message ?: s(R.string.v3_topic_failed)) }
        }
    }

    fun createGroupEvent(chatId: String, title: String, description: String, hoursFromNow: Long) {
        val repo = forumRepo ?: return
        val startMs = System.currentTimeMillis() + hoursFromNow.coerceIn(1L, 720L) * 3_600_000L
        scope.launch {
            repo.createGroupEvent(chatId, title, description, startMs)
                .onSuccess { onBanner(s(R.string.vm_event_created, title)) }
                .onFailure { onBanner(it.message ?: s(R.string.v3_event_failed)) }
        }
    }

    fun toggleEventRsvp(chatId: String, eventId: String) {
        val repo = forumRepo ?: return
        scope.launch {
            repo.toggleEventRsvp(chatId, eventId)
                .onFailure { onBanner(it.message ?: s(R.string.v3_rsvp_failed)) }
        }
    }

    fun updateGroupModeration(
        chat: ChatRoom,
        slowModeSeconds: Int? = null,
        toggleRestrictedUid: String? = null,
        antiSpamEnabled: Boolean? = null,
        topicsEnabled: Boolean? = null,
        linkedGroupId: String? = null,
        inviteExpiresAtEpochMs: Long? = null
    ) {
        val repo = forumRepo ?: return
        scope.launch {
            repo.updateGroupModerationV3(
                chat,
                slowModeSeconds,
                toggleRestrictedUid,
                antiSpamEnabled,
                topicsEnabled,
                linkedGroupId,
                inviteExpiresAtEpochMs
            ).onSuccess { onBanner(s(R.string.v3_group_synced)) }
                .onFailure { onBanner(it.message ?: s(R.string.v3_group_sync_failed)) }
        }
    }

    fun incrementChannelView(chat: ChatRoom) {
        val repo = forumRepo ?: return
        if (chat.type != "CHANNEL") return
        scope.launch {
            repo.updateGroupModerationV3(chat, incrementChannelView = true)
        }
    }

    fun recordOfflineSyncOrFlush(entityType: String, entityId: String, operation: String, summary: String) {
        val ctx = appContext ?: return
        val mutation = PendingSyncMutation(
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            versionEpochMs = System.currentTimeMillis(),
            payloadSummary = summary
        )
        OfflineSyncEngine.enqueueMutation(ctx, currentUserId, mutation)
        _pendingSyncCount.value = OfflineSyncEngine.getPendingMutations(ctx, currentUserId).size
        flushOfflineSyncQueue()
    }

    fun flushOfflineSyncQueue() {
        val ctx = appContext ?: return
        val repo = linkedRepo ?: return
        val pending = OfflineSyncEngine.getPendingMutations(ctx, currentUserId)
        if (pending.isEmpty()) return
        scope.launch {
            for (item in pending) {
                val res = repo.publishSyncEvent(
                    entityType = item.entityType,
                    entityId = item.entityId,
                    operation = item.operation,
                    deviceId = currentDeviceId,
                    payloadSummary = item.payloadSummary
                )
                if (res.isSuccess) {
                    OfflineSyncEngine.markMutationSynced(ctx, currentUserId, item.id)
                }
            }
            _pendingSyncCount.value = OfflineSyncEngine.getPendingMutations(ctx, currentUserId).size
        }
    }

    fun removeLocalAccountSlot(uid: String) {
        val ctx = appContext ?: return
        _localAccounts.value = MultiAccountManager.removeAccount(ctx, uid)
        onBanner(s(R.string.v3_account_removed))
    }

    fun switchAccountInteractive(activity: Activity) {
        scope.launch {
            authRepository.signOut(activity)
            authRepository.signInInteractive(activity)
                .onFailure { onBanner(it.message ?: s(R.string.vm_account_switch_cancelled)) }
        }
    }

    fun handleDeathLegionOAuthCallback(context: Context, uri: Uri) {
        val callback = DeathLegionOAuthClient.parseCallbackUri(uri) ?: return
        if (!callback.error.isNullOrBlank()) {
            onBanner(s(R.string.v3_legion_error, callback.errorDescription ?: callback.error))
            return
        }
        val code = callback.code
        if (code.isNullOrBlank()) {
            onBanner(s(R.string.v3_legion_missing_code))
            return
        }
        scope.launch {
            DeathLegionOAuthClient.exchangeCodeForSession(context, code, callback.state, validateState = true)
                .onSuccess { info ->
                    linkDeathLegionUserInfo(info)
                }
                .onFailure { onBanner(it.message ?: s(R.string.v3_legion_token_failed)) }
        }
    }

    fun completePendingDeathLegionAuthCode(context: Context) {
        scope.launch {
            DeathLegionOAuthClient.completePendingAuthCode(context)
                .onSuccess { info -> linkDeathLegionUserInfo(info) }
                .onFailure { onBanner(it.message ?: s(R.string.v3_legion_token_failed)) }
        }
    }

    fun linkDeathLegionWithAccessToken(context: Context, accessToken: String) {
        scope.launch {
            DeathLegionOAuthClient.fetchUserInfo(context, accessToken)
                .onSuccess { info -> linkDeathLegionUserInfo(info) }
                .onFailure { onBanner(it.message ?: s(R.string.v3_legion_token_failed)) }
        }
    }

    fun refreshDeathLegionSession(context: Context) {
        scope.launch {
            DeathLegionOAuthClient.refreshAccessToken(context)
                .onSuccess { info ->
                    workspaceRepository.linkDeathLegionProfile(info)
                        .onSuccess {
                            onBanner(s(R.string.v3_legion_refreshed, info.username.ifBlank { info.legionUserId }))
                        }
                        .onFailure { onBanner(it.message ?: s(R.string.v3_legion_save_failed)) }
                }
                .onFailure { onBanner(it.message ?: s(R.string.v3_legion_token_failed)) }
        }
    }

    fun introspectDeathLegionSession(context: Context) {
        scope.launch {
            DeathLegionOAuthClient.introspectActiveToken(context)
                .onSuccess { res ->
                    if (res.active) {
                        val scopeNote = res.scope.ifBlank { DeathLegionOAuthClient.DEFAULT_SCOPES }
                        onBanner(s(R.string.v3_legion_token_active, res.username.ifBlank { "member" }, scopeNote))
                    } else {
                        onBanner(s(R.string.v3_legion_token_inactive))
                    }
                }
                .onFailure { onBanner(it.message ?: s(R.string.v3_legion_token_failed)) }
        }
    }

    fun disconnectDeathLegion(context: Context) {
        scope.launch {
            DeathLegionOAuthClient.revokeSession(context)
            workspaceRepository.unlinkDeathLegionProfile()
                .onSuccess { onBanner(s(R.string.v3_legion_unlinked)) }
                .onFailure { onBanner(it.message ?: s(R.string.v3_legion_save_failed)) }
        }
    }

    private suspend fun linkDeathLegionUserInfo(info: DeathLegionUserInfo) {
        workspaceRepository.linkDeathLegionProfile(info)
            .onSuccess {
                onBanner(s(R.string.vm_legion_linked, info.username.ifBlank { info.legionUserId }))
            }
            .onFailure { onBanner(it.message ?: s(R.string.v3_legion_save_failed)) }
    }
}
