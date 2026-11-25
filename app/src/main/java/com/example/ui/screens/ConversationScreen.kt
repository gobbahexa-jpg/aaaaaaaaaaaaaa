package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ImageUtils
import com.example.core.LocalStorageManager
import com.example.core.RecordedVoiceClip
import com.example.core.VoicePlaybackController
import com.example.core.VoicePlaybackState
import com.example.core.VoiceRecorderController
import com.example.data.model.ChatDraft
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.ChatTopic
import com.example.data.model.ChatType
import com.example.data.model.ContactEntry
import com.example.data.model.DlSticker
import com.example.data.model.DlStickerPack
import com.example.data.model.GroupEvent
import com.example.data.model.MessageType
import com.example.data.model.PrivateChatSetting
import com.example.data.model.ReportCategory
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.AudioWaveformBars
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlCodeBlock
import com.example.ui.components.DlEmptyState
import com.example.ui.components.DlErrorState
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlLoadingState
import com.example.ui.components.DlSearchField
import com.example.ui.components.DlSecondaryButton
import com.example.ui.components.DlStatusPill
import com.example.ui.components.RichMessageText
import com.example.ui.components.StickerPickerRow
import com.example.ui.components.containsRichFormatting
import com.example.ui.components.formatDateSeparatorLocalized
import com.example.ui.components.formatDuration
import com.example.ui.components.formatRelativeTimeLocalized
import com.example.ui.components.localizedLabel
import com.example.ui.components.localizedTitle
import com.example.ui.theme.DlCrimson
import com.example.ui.theme.DlEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDlTokens
import com.example.ui.theme.contrastInkFor
import com.example.ui.theme.resolveAccessibleAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
@Composable
fun ConversationScreen(
    myUid: String,
    myProfile: UserProfile?,
    chat: ChatRoom,
    messagesState: UiState<List<ChatMessage>>,
    allChats: List<ChatRoom>,
    allUsers: List<UserProfile>,
    contacts: List<ContactEntry>,
    bubbleStyle: String,
    wallpaperId: String,
    aiEnabled: Boolean,
    aiBusy: Boolean,
    smartReplies: List<String>,
    summaryResult: String?,
    translationResult: Pair<String, String>?,
    explainResult: String?,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onMarkRead: (List<ChatMessage>) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onSendText: (type: String, text: String, replyToId: String?, replyPreview: String?, forwardedFrom: String?, scheduledMs: Long?) -> Unit,
    onSendImage: (Uri, String) -> Unit,
    onSendFileAttachment: (Uri, String) -> Unit,
    onSendVoiceNote: (RecordedVoiceClip) -> Unit,
    onSendPoll: (String, List<String>) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onDeleteForEveryone: (String) -> Unit,
    onToggleStar: (String) -> Unit,
    onToggleReaction: (ChatMessage, String) -> Unit,
    onVotePoll: (ChatMessage, Int) -> Unit,
    onPinMessage: (ChatMessage) -> Unit,
    onSaveMessage: (ChatMessage) -> Unit,
    onForwardMessage: (ChatRoom, ChatMessage) -> Unit,
    onJoinChannel: () -> Unit,
    onRequestJoinGroup: () -> Unit,
    onApproveJoinRequest: (UserProfile, Boolean) -> Unit,
    onStartCall: (UserProfile, String) -> Unit,
    onSummarizeChat: (List<ChatMessage>) -> Unit,
    onSuggestSmartReplies: (List<ChatMessage>) -> Unit,
    onRewriteDraft: (String, String, (String) -> Unit) -> Unit,
    onTranslateMessage: (ChatMessage, String) -> Unit,
    onExplainMessage: (ChatMessage) -> Unit,
    onToggleMute: () -> Unit,
    onToggleArchive: () -> Unit,
    onSetDisappearing: (Long) -> Unit,
    onToggleAdminOnly: (Boolean) -> Unit,
    onUpdatePermissions: (Boolean, Boolean) -> Unit,
    onRegenerateInvite: () -> Unit,
    onRevokeInvite: (Boolean) -> Unit,
    onUpdateChatTheme: (String) -> Unit,
    onAddMember: (UserProfile) -> Unit,
    onPromoteAdmin: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onToggleFavorite: (UserProfile, ContactEntry?) -> Unit,
    onToggleBlock: (UserProfile, ContactEntry?) -> Unit,
    onSubmitReport: (String, String, String) -> Unit,
    onDismissSummary: () -> Unit,
    onDismissTranslation: () -> Unit,
    onDismissExplain: () -> Unit,
    modifier: Modifier = Modifier,
    topics: List<ChatTopic> = emptyList(),
    events: List<GroupEvent> = emptyList(),
    syncedDraft: ChatDraft? = null,
    privateSetting: PrivateChatSetting? = null,
    allGroupChats: List<ChatRoom> = emptyList(),
    onSaveDraft: (String, String?) -> Unit = { _, _ -> },
    onSendQuiz: (question: String, options: List<String>, correctIdx: Int, explanation: String, topicId: String?) -> Unit = { _, _, _, _, _ -> },
    onSendSticker: (DlSticker, String?) -> Unit = { _, _ -> },
    onSendTopicText: ((type: String, text: String, replyToId: String?, replyPreview: String?, forwardedFrom: String?, scheduledMs: Long?, topicId: String?) -> Unit)? = null,
    onCreateTopic: (title: String, emoji: String) -> Unit = { _, _ -> },
    onCreateGroupEvent: (title: String, desc: String, hoursFromNow: Long) -> Unit = { _, _, _ -> },
    onToggleEventRsvp: (String) -> Unit = {},
    onToggleHidden: () -> Unit = {},
    onToggleLocked: () -> Unit = {},
    onExportChat: (format: String) -> Unit = {},
    onSetSlowMode: (Int) -> Unit = {},
    onToggleRestrictedUid: (String) -> Unit = {},
    onToggleAntiSpam: (Boolean) -> Unit = {},
    onLinkDiscussionGroup: (String) -> Unit = {},
    onSummarizeVoiceTranscript: (ChatMessage, String) -> Unit = { _, _ -> },
    restrictionNotice: String? = null,
    onOpenAccountStatus: (() -> Unit)? = null,
    onSubmitDetailedReport: ((targetUid: String, targetType: String, category: ReportCategory, reason: String, messageId: String?, evidenceSnippet: String?) -> Unit)? = null
) {
    if (showBackButton) {
        BackHandler(onBack = onBack)
    }

    val tokens = LocalDlTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    val recorderController = remember(context) { VoiceRecorderController(context, scope) }
    val playbackController = remember(context) { VoicePlaybackController(context, scope) }
    val recordingState by recorderController.state.collectAsStateWithLifecycle()
    val playbackState by playbackController.state.collectAsStateWithLifecycle()

    DisposableEffect(chat.id) {
        onDispose {
            recorderController.cancelRecording()
            playbackController.stop()
            onTypingChanged(false)
        }
    }

    var draft by remember(chat.id) { mutableStateOf(syncedDraft?.draftText.orEmpty()) }
    var draftLoadedFromSync by remember(chat.id) { mutableStateOf(!syncedDraft?.draftText.isNullOrBlank()) }
    LaunchedEffect(chat.id, syncedDraft?.draftText) {
        if (!draftLoadedFromSync && draft.isEmpty() && !syncedDraft?.draftText.isNullOrBlank()) {
            draft = syncedDraft.draftText
            draftLoadedFromSync = true
        }
    }
    var replyTarget by remember(chat.id) { mutableStateOf<ChatMessage?>(null) }
    var actionTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editHistoryTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var multiSelectMode by remember(chat.id) { mutableStateOf(false) }
    val selectedMessageIds = remember(chat.id) { mutableStateListOf<String>() }
    var showMultiForwardDialog by remember { mutableStateOf(false) }
    var showAttachments by remember { mutableStateOf(false) }
    var showStickerTray by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showQuizDialog by remember { mutableStateOf(false) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var showTopicsDialog by remember { mutableStateOf(false) }
    var showEventsDialog by remember { mutableStateOf(false) }
    var selectedTopicId by remember(chat.id) { mutableStateOf<String?>(null) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var messageSearchQuery by remember { mutableStateOf("") }
    var scheduledDelayMin by remember { mutableStateOf<Int?>(null) }
    val initialReadCursor = remember(chat.id) { chat.readCursors[myUid] ?: 0L }

    var lastSyncedDraftText by remember(chat.id) { mutableStateOf(syncedDraft?.draftText.orEmpty()) }
    LaunchedEffect(chat.id, draft) {
        delay(750L)
        if (draft != lastSyncedDraftText) {
            lastSyncedDraftText = draft
            onSaveDraft(draft, replyTarget?.id)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) recorderController.startRecording()
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSendImage(uri, draft)
            draft = ""
            showAttachments = false
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSendFileAttachment(uri, "VIDEO")
            showAttachments = false
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onSendFileAttachment(uri, "DOCUMENT")
            showAttachments = false
        }
    }

    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onSendFileAttachment(uri, "AUDIO")
            showAttachments = false
        }
    }

    val chatType = ChatType.fromRaw(chat.type)
    val isMember = myUid in chat.memberUids
    val isAdmin = myUid in chat.adminUids
    val isRestricted = myUid in chat.restrictedUids
    val canPost = isMember && !isRestricted && (!chat.adminOnlyPost || isAdmin)
    val peerUid = if (chatType == ChatType.DIRECT) chat.memberUids.firstOrNull { it != myUid } else null
    val peerProfile = allUsers.firstOrNull { it.userId == peerUid }
    val chatAccent = chat.themeAccentHex?.let { resolveAccessibleAccent(it, tokens.isDark) }
        ?: MaterialTheme.colorScheme.primary

    val dispatchSend: (String, String, String?, String?, String?, Long?) -> Unit = { type, text, rId, rPrev, fwd, sched ->
        if (onSendTopicText != null) {
            onSendTopicText(type, text, rId, rPrev, fwd, sched, selectedTopicId)
        } else {
            onSendText(type, text, rId, rPrev, fwd, sched)
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val filteredMessages = remember(messages, messageSearchQuery, selectedTopicId) {
        val q = messageSearchQuery.trim().lowercase()
        messages.filter { msg ->
            val topicOk = selectedTopicId == null || msg.topicId == selectedTopicId
            val queryOk = q.isEmpty() || msg.text.lowercase().contains(q) || msg.senderName.lowercase().contains(q)
            topicOk && queryOk
        }
    }

    LaunchedEffect(chat.id, messages.size) {
        if (messages.isNotEmpty() && isMember) {
            onMarkRead(messages)
        }
    }

    LaunchedEffect(draft) {
        if (draft.isNotBlank() && canPost) {
            onTypingChanged(true)
            delay(4000L)
            onTypingChanged(false)
        } else {
            onTypingChanged(false)
        }
    }

    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.lastIndex)
        }
    }

    val activeTypers = chat.activeTypersExcluding(myUid)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (wallpaperId == "nebula") {
            Image(
                painter = painterResource(id = R.drawable.img_chat_wallpaper_nebula),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (tokens.isDark) 0.24f else 0.08f),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Architectural Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (multiSelectMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 60.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    multiSelectMode = false
                                    selectedMessageIds.clear()
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                                }
                                Text(
                                    stringResource(R.string.conv_multi_selected, selectedMessageIds.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.textPrimary
                                )
                            }
                            Row {
                                val chosenMsgs = messages.filter { it.id in selectedMessageIds }
                                IconButton(onClick = {
                                    val joined = chosenMsgs.joinToString("\n") { "${it.senderName}: ${it.text}" }
                                    clipboard.setText(AnnotatedString(joined))
                                    multiSelectMode = false
                                    selectedMessageIds.clear()
                                }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.common_copy))
                                }
                                IconButton(onClick = {
                                    chosenMsgs.forEach { onToggleStar(it.id) }
                                    multiSelectMode = false
                                    selectedMessageIds.clear()
                                }) {
                                    Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.conv_multi_star_cd))
                                }
                                IconButton(onClick = { showMultiForwardDialog = true }) {
                                    Icon(Icons.Filled.Forward, contentDescription = stringResource(R.string.conv_multi_forward_cd))
                                }
                                IconButton(onClick = {
                                    chosenMsgs.filter { it.senderId == myUid }.forEach { onDeleteForEveryone(it.id) }
                                    multiSelectMode = false
                                    selectedMessageIds.clear()
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.conv_multi_delete_cd))
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 60.dp)
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showBackButton) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier.testTag("conversation_back_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showInfoSheet = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val localizedChatTitle = chat.localizedTitle(myUid)
                                DlAvatar(
                                    name = localizedChatTitle,
                                    colorHex = chat.themeAccentHex ?: "#00E5FF",
                                    photoBase64 = peerProfile?.photoDataBase64,
                                    onlineStatus = if (peerProfile?.showOnline == true) peerProfile.onlineStatus else null,
                                    size = 40.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = localizedChatTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (chat.e2eeEnabled) {
                                            Spacer(Modifier.width(5.dp))
                                            Icon(
                                                Icons.Filled.Lock,
                                                contentDescription = stringResource(R.string.conv_msg_encrypted_cd),
                                                tint = tokens.statusOnline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        if (peerProfile != null && (
                                                peerProfile.legionVerificationStatus.equals("verified", ignoreCase = true) ||
                                                    !peerProfile.legionMemberNumber.isNullOrBlank()
                                                )
                                        ) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                Icons.Filled.Verified,
                                                contentDescription = stringResource(R.string.contacts_legion_badge),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = when {
                                            activeTypers.isNotEmpty() -> stringResource(R.string.conv_subtitle_typing, activeTypers.first())
                                            chatType == ChatType.DIRECT && peerProfile != null -> {
                                                if (peerProfile.showLastSeen) {
                                                    formatRelativeTimeLocalized(peerProfile.lastSeenAt)
                                                } else {
                                                    stringResource(R.string.conv_subtitle_e2ee)
                                                }
                                            }
                                            chatType == ChatType.DIRECT -> stringResource(R.string.conv_subtitle_e2ee)
                                            chatType == ChatType.GROUP -> stringResource(R.string.conv_subtitle_members, chat.memberUids.size)
                                            else -> stringResource(R.string.conv_subtitle_subscribers, chat.memberUids.size)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (activeTypers.isNotEmpty()) chatAccent else tokens.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (peerProfile != null) {
                                IconButton(
                                    onClick = { onStartCall(peerProfile, "VOICE") },
                                    modifier = Modifier.testTag("voice_call_button")
                                ) {
                                    Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.conv_voice_call_cd))
                                }
                                IconButton(
                                    onClick = { onStartCall(peerProfile, "VIDEO") },
                                    modifier = Modifier.testTag("video_call_button")
                                ) {
                                    Icon(Icons.Filled.Videocam, contentDescription = stringResource(R.string.conv_video_call_cd))
                                }
                            }
                            IconButton(onClick = { showSearchBar = !showSearchBar }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.common_search))
                            }
                            IconButton(
                                onClick = { showInfoSheet = true },
                                modifier = Modifier.testTag("chat_info_button")
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.conv_info_cd))
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showSearchBar,
                        enter = if (tokens.reducedMotion) EnterTransition.None else fadeIn() + expandVertically(),
                        exit = if (tokens.reducedMotion) ExitTransition.None else fadeOut() + shrinkVertically()
                    ) {
                        DlSearchField(
                            value = messageSearchQuery,
                            onValueChange = { messageSearchQuery = it },
                            placeholder = stringResource(R.string.conv_search_placeholder),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    if (!chat.pinnedMessageText.isNullOrBlank()) {
                        DlHairlineDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(chatAccent.copy(alpha = 0.08f))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(chatAccent)
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = null,
                                tint = chatAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = chat.pinnedMessageText,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (chatType == ChatType.GROUP) {
                        DlHairlineDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { showTopicsDialog = true },
                                leadingIcon = { Icon(Icons.Filled.Forum, null, Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.info_topics_btn, topics.size)) }
                            )
                            AssistChip(
                                onClick = { showEventsDialog = true },
                                leadingIcon = { Icon(Icons.Filled.Event, null, Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.info_events_btn, events.size)) }
                            )
                            if (topics.isNotEmpty() || chat.topicsEnabled) {
                                FilterChip(
                                    selected = selectedTopicId == null,
                                    onClick = { selectedTopicId = null },
                                    label = { Text(stringResource(R.string.chats_folder_all)) }
                                )
                                topics.forEach { topic ->
                                    FilterChip(
                                        selected = selectedTopicId == topic.id,
                                        onClick = { selectedTopicId = if (selectedTopicId == topic.id) null else topic.id },
                                        label = { Text("${topic.iconEmoji} ${topic.title}") }
                                    )
                                }
                            }
                        }
                    }

                    DlHairlineDivider()
                    if (!restrictionNotice.isNullOrBlank()) {
                        ContextualRestrictionBanner(
                            message = restrictionNotice,
                            onOpenAccountStatus = onOpenAccountStatus
                        )
                    }
                }
            }

            if (aiBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Message List
            Box(modifier = Modifier.weight(1f)) {
                when (messagesState) {
                    is UiState.Loading -> DlLoadingState(stringResource(R.string.conv_loading_messages), Modifier.align(Alignment.Center))
                    is UiState.Error -> DlErrorState(
                        title = stringResource(R.string.conv_error_title),
                        message = messagesState.message,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    is UiState.Success -> {
                        if (filteredMessages.isEmpty()) {
                            DlEmptyState(
                                icon = Icons.AutoMirrored.Filled.Send,
                                title = stringResource(R.string.conv_empty_title),
                                subtitle = stringResource(R.string.conv_empty_subtitle)
                            )
                        } else {
                            val firstUnreadIdx = remember(filteredMessages, initialReadCursor, myUid) {
                                if (initialReadCursor <= 0L) -1
                                else filteredMessages.indexOfFirst {
                                    it.senderId != myUid && (it.createdAt?.toDate()?.time ?: 0L) > initialReadCursor
                                }
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("conversation_messages_list"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (messages.size >= 100) {
                                    item(key = "load_older") {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            DlSecondaryButton(
                                                text = stringResource(R.string.conv_load_older),
                                                onClick = onLoadOlderMessages
                                            )
                                        }
                                    }
                                }

                                items(filteredMessages.size, key = { filteredMessages[it].id }) { index ->
                                    val msg = filteredMessages[index]
                                    val prevMsg = if (index > 0) filteredMessages[index - 1] else null
                                    val nextMsg = if (index < filteredMessages.lastIndex) filteredMessages[index + 1] else null

                                    val prevDate = prevMsg?.let { formatDateSeparatorLocalized(it.createdAt) }
                                    val curDate = formatDateSeparatorLocalized(msg.createdAt)
                                    val nextDate = nextMsg?.let { formatDateSeparatorLocalized(it.createdAt) }

                                    if (curDate != prevDate) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = tokens.surfaceElevated,
                                                border = BorderStroke(1.dp, tokens.hairline)
                                            ) {
                                                Text(
                                                    text = curDate,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = tokens.textSecondary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (index == firstUnreadIdx) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            DlHairlineDivider(modifier = Modifier.weight(1f))
                                            DlStatusPill(stringResource(R.string.chats_folder_unread), color = chatAccent)
                                            DlHairlineDivider(modifier = Modifier.weight(1f))
                                        }
                                    }

                                    val samePrev = prevMsg != null && prevMsg.senderId == msg.senderId && curDate == prevDate && index != firstUnreadIdx
                                    val sameNext = nextMsg != null && nextMsg.senderId == msg.senderId && curDate == nextDate && (index + 1) != firstUnreadIdx

                                    if (!samePrev && index > 0) {
                                        Spacer(Modifier.height(6.dp))
                                    }

                                    MessageBubbleRow(
                                        message = msg,
                                        isMine = msg.senderId == myUid,
                                        isGroupedWithPrev = samePrev,
                                        isGroupedWithNext = sameNext,
                                        bubbleStyle = bubbleStyle,
                                        chatAccent = chatAccent,
                                        myUid = myUid,
                                        multiSelectMode = multiSelectMode,
                                        selected = msg.id in selectedMessageIds,
                                        playbackState = playbackState,
                                        onTogglePlayVoice = { playbackController.togglePlay(msg.id, msg.mediaDataBase64) },
                                        onCyclePlaybackSpeed = { playbackController.cycleSpeed() },
                                        onTap = {
                                            if (multiSelectMode) {
                                                if (msg.id in selectedMessageIds) selectedMessageIds.remove(msg.id)
                                                else selectedMessageIds.add(msg.id)
                                            } else {
                                                actionTarget = msg
                                            }
                                        },
                                        onVotePoll = { idx -> onVotePoll(msg, idx) }
                                    )
                                }
                            }
                        }
                    }
                }

                val showJumpToLatest by remember(filteredMessages.size) {
                    derivedStateOf { filteredMessages.isNotEmpty() && listState.canScrollForward }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showJumpToLatest,
                    enter = if (tokens.reducedMotion) EnterTransition.None else fadeIn(),
                    exit = if (tokens.reducedMotion) ExitTransition.None else fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            if (filteredMessages.isNotEmpty()) {
                                scope.launch { listState.animateScrollToItem(filteredMessages.lastIndex) }
                            }
                        },
                        containerColor = chatAccent,
                        contentColor = contrastInkFor(chatAccent),
                        modifier = Modifier.testTag("jump_to_latest_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.conv_jump_latest_cd)
                        )
                    }
                }
            }

            // Smart Replies strip
            if (smartReplies.isNotEmpty() && canPost) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    smartReplies.forEach { reply ->
                        AssistChip(
                            onClick = { dispatchSend("TEXT", reply, null, null, null, null) },
                            label = { Text(reply) }
                        )
                    }
                }
            }

            // Reply banner
            if (replyTarget != null) {
                Surface(
                    color = tokens.surfaceElevated,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(chatAccent)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.conv_replying_to, replyTarget!!.senderName, ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = chatAccent
                            )
                            Text(
                                text = replyTarget!!.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { replyTarget = null }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.conv_cancel_reply_cd))
                        }
                    }
                }
            }

            // Attachment tray
            AnimatedVisibility(
                visible = showAttachments,
                enter = if (tokens.reducedMotion) EnterTransition.None else fadeIn() + expandVertically(),
                exit = if (tokens.reducedMotion) ExitTransition.None else fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = tokens.surfaceElevated,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            leadingIcon = { Icon(Icons.Filled.Photo, null) },
                            label = { Text(stringResource(R.string.conv_attach_photo)) }
                        )
                        AssistChip(
                            onClick = {
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            leadingIcon = { Icon(Icons.Filled.Videocam, null) },
                            label = { Text(stringResource(R.string.conv_attach_video)) }
                        )
                        AssistChip(
                            onClick = { documentPicker.launch(arrayOf("*/*")) },
                            leadingIcon = { Icon(Icons.Filled.Description, null) },
                            label = { Text(stringResource(R.string.conv_attach_file)) }
                        )
                        AssistChip(
                            onClick = { audioFilePicker.launch(arrayOf("audio/*")) },
                            leadingIcon = { Icon(Icons.Filled.AudioFile, null) },
                            label = { Text(stringResource(R.string.calls_type_voice)) }
                        )
                        AssistChip(
                            onClick = { showPollDialog = true; showAttachments = false },
                            leadingIcon = { Icon(Icons.Filled.Poll, null) },
                            label = { Text(stringResource(R.string.conv_attach_poll)) }
                        )
                        AssistChip(
                            onClick = { showQuizDialog = true; showAttachments = false },
                            leadingIcon = { Icon(Icons.Filled.Quiz, null) },
                            label = { Text(stringResource(R.string.conv_attach_quiz)) }
                        )
                        AssistChip(
                            onClick = { showStickerTray = !showStickerTray; showAttachments = false },
                            leadingIcon = { Icon(Icons.Filled.EmojiEmotions, null) },
                            label = { Text(stringResource(R.string.conv_sticker_cd)) }
                        )
                        AssistChip(
                            onClick = { showCodeDialog = true; showAttachments = false },
                            leadingIcon = { Icon(Icons.Filled.Code, null) },
                            label = { Text(stringResource(R.string.conv_attach_code)) }
                        )
                        AssistChip(
                            onClick = {
                                dispatchSend(
                                    "LOCATION",
                                    context.getString(R.string.conv_location_msg_text, "37.7749° N", "122.4194° W"),
                                    null,
                                    null,
                                    null,
                                    null
                                )
                                showAttachments = false
                            },
                            leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                            label = { Text(stringResource(R.string.conv_attach_location)) }
                        )
                        val nextDelayLabel = when (scheduledDelayMin) {
                            5 -> "5m"
                            30 -> stringResource(R.string.conv_schedule_30m)
                            60 -> stringResource(R.string.conv_schedule_1h)
                            1440 -> stringResource(R.string.conv_schedule_24h)
                            else -> null
                        }
                        AssistChip(
                            onClick = {
                                scheduledDelayMin = when (scheduledDelayMin) {
                                    null -> 5
                                    5 -> 30
                                    30 -> 60
                                    60 -> 1440
                                    else -> null
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                            label = {
                                Text(
                                    if (nextDelayLabel != null) stringResource(R.string.conv_schedule_chip_prefix, nextDelayLabel)
                                    else stringResource(R.string.conv_attach_schedule)
                                )
                            }
                        )
                    }
                }
            }

            // Slash Command Palette (triggers when typing "/")
            AnimatedVisibility(
                visible = canPost && draft.startsWith("/") && !draft.contains("\n") && draft.length <= 16,
                enter = if (tokens.reducedMotion) EnterTransition.None else fadeIn() + expandVertically(),
                exit = if (tokens.reducedMotion) ExitTransition.None else fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = tokens.surfaceElevated,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slash_command_palette")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.conv_slash_header),
                            style = MaterialTheme.typography.labelSmall,
                            color = chatAccent
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = { draft = ""; showPollDialog = true },
                                leadingIcon = { Icon(Icons.Filled.Poll, null, Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.conv_slash_poll)) },
                                modifier = Modifier.testTag("slash_cmd_poll")
                            )
                            AssistChip(
                                onClick = { draft = ""; showQuizDialog = true },
                                leadingIcon = { Icon(Icons.Filled.Quiz, null, Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.conv_slash_quiz)) },
                                modifier = Modifier.testTag("slash_cmd_quiz")
                            )
                            AssistChip(
                                onClick = { draft = ""; showCodeDialog = true },
                                leadingIcon = { Icon(Icons.Filled.Code, null, Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.conv_slash_code)) },
                                modifier = Modifier.testTag("slash_cmd_code")
                            )
                            AssistChip(
                                onClick = {
                                    scheduledDelayMin = if (scheduledDelayMin == null) 5 else null
                                    draft = ""
                                },
                                leadingIcon = { Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.conv_slash_schedule)) },
                                modifier = Modifier.testTag("slash_cmd_schedule")
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showStickerTray && canPost,
                enter = if (tokens.reducedMotion) EnterTransition.None else fadeIn() + expandVertically(),
                exit = if (tokens.reducedMotion) ExitTransition.None else fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = tokens.surfaceSubtle,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StickerPickerRow(
                        onSelectSticker = { sticker ->
                            onSendSticker(sticker, selectedTopicId)
                            showStickerTray = false
                        }
                    )
                }
            }

            // Live Voice Recording Controls OR Composer
            if (recordingState.recording) {
                Surface(
                    color = tokens.surfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        DlHairlineDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { recorderController.cancelRecording() }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.conv_cancel_rec_cd), tint = MaterialTheme.colorScheme.error)
                            }
                            Text(
                                text = formatDuration(recordingState.elapsedMs / 1000L),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(10.dp))
                            AudioWaveformBars(
                                samples = recordingState.waveform,
                                progressRatio = 1f,
                                activeColor = chatAccent,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { recorderController.togglePauseResume() }) {
                                Icon(
                                    if (recordingState.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = stringResource(
                                        if (recordingState.paused) R.string.conv_resume_rec_cd else R.string.conv_pause_rec_cd
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(chatAccent)
                                    .clickable {
                                        val clip = recorderController.finishRecording()
                                        if (clip != null) onSendVoiceNote(clip)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.conv_send_cd),
                                    tint = contrastInkFor(chatAccent),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else if (!isMember && chatType == ChatType.CHANNEL && chat.publicVisible) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        DlHairlineDivider()
                        Button(
                            onClick = onJoinChannel,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("subscribe_channel_button")
                        ) {
                            Text(stringResource(R.string.conv_join_channel_cta))
                        }
                    }
                }
            } else if (!isMember && chatType == ChatType.GROUP && chat.publicVisible) {
                val pending = myUid in chat.joinRequestUids
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        DlHairlineDivider()
                        Button(
                            onClick = onRequestJoinGroup,
                            enabled = !pending,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(
                                stringResource(
                                    if (pending) R.string.common_pending else R.string.conv_request_join_cta
                                )
                            )
                        }
                    }
                }
            } else if (!canPost) {
                Surface(color = tokens.surfaceElevated, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        DlHairlineDivider()
                        Text(
                            text = stringResource(
                                if (isRestricted) R.string.conv_restricted_banner else R.string.conv_channel_read_only_banner
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isRestricted) DlCrimson else tokens.textSecondary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        DlHairlineDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { showAttachments = !showAttachments },
                                modifier = Modifier.testTag("attach_button")
                            ) {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    contentDescription = stringResource(R.string.conv_attach_menu_cd),
                                    tint = if (showAttachments) chatAccent else tokens.textSecondary
                                )
                            }
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                placeholder = {
                                    val scheduleBadgeText = when (scheduledDelayMin) {
                                        5 -> "5m"
                                        30 -> stringResource(R.string.conv_schedule_30m)
                                        60 -> stringResource(R.string.conv_schedule_1h)
                                        1440 -> stringResource(R.string.conv_schedule_24h)
                                        else -> null
                                    }
                                    Text(
                                        text = if (scheduleBadgeText != null) {
                                            stringResource(R.string.conv_schedule_chip_prefix, scheduleBadgeText)
                                        } else {
                                            stringResource(R.string.conv_input_placeholder)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = tokens.surfaceElevated,
                                    unfocusedContainerColor = tokens.surfaceElevated,
                                    focusedBorderColor = chatAccent,
                                    unfocusedBorderColor = tokens.hairline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("message_input")
                            )
                            Spacer(Modifier.width(6.dp))
                            if (draft.isBlank()) {
                                IconButton(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            recorderController.startRecording()
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier.testTag("record_voice_button")
                                ) {
                                    Icon(
                                        Icons.Filled.Mic,
                                        contentDescription = stringResource(R.string.conv_record_voice_cd),
                                        tint = chatAccent
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        val textToSend = draft.trim()
                                        if (textToSend.isNotEmpty()) {
                                            val scheduledMs = scheduledDelayMin?.let {
                                                System.currentTimeMillis() + it * 60_000L
                                            }
                                            dispatchSend(
                                                "TEXT",
                                                textToSend,
                                                replyTarget?.id,
                                                replyTarget?.text?.take(100),
                                                null,
                                                scheduledMs
                                            )
                                            draft = ""
                                            replyTarget = null
                                            scheduledDelayMin = null
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(chatAccent)
                                        .testTag("send_message_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = stringResource(R.string.conv_send_cd),
                                        tint = contrastInkFor(chatAccent),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (actionTarget != null) {
        val target = actionTarget!!
        MessageActionsDialog(
            message = target,
            isMine = target.senderId == myUid,
            isE2eeChat = chat.e2eeEnabled && chatType == ChatType.DIRECT,
            allChats = allChats,
            myUid = myUid,
            onDismiss = { actionTarget = null },
            onReact = { emoji -> onToggleReaction(target, emoji) },
            onReply = { replyTarget = target },
            onCopy = { clipboard.setText(AnnotatedString(target.text)) },
            onEdit = { newText -> onEditMessage(target.id, newText) },
            onDeleteForEveryone = { onDeleteForEveryone(target.id) },
            onPin = { onPinMessage(target) },
            onStar = { onToggleStar(target.id) },
            onSave = { onSaveMessage(target) },
            onForward = { dest -> onForwardMessage(dest, target) },
            onTranslate = { lang -> onTranslateMessage(target, lang) },
            onExplainAi = { onExplainMessage(target) },
            onEnterMultiSelect = {
                multiSelectMode = true
                if (target.id !in selectedMessageIds) selectedMessageIds.add(target.id)
            },
            onReport = { reason -> onSubmitReport(target.senderId, "MESSAGE", reason) },
            onViewEditHistory = { editHistoryTarget = target },
            onSummarizeVoice = { transcript -> onSummarizeVoiceTranscript(target, transcript) },
            onSubmitCategorizedReport = { category, reason ->
                if (onSubmitDetailedReport != null) {
                    onSubmitDetailedReport(
                        target.senderId,
                        "MESSAGE",
                        category,
                        reason,
                        target.id,
                        target.text.take(200)
                    )
                } else {
                    onSubmitReport(target.senderId, "MESSAGE", "[${category.name}] $reason")
                }
            }
        )
    }

    if (editHistoryTarget != null) {
        EditHistoryDialog(
            message = editHistoryTarget!!,
            onDismiss = { editHistoryTarget = null }
        )
    }

    if (showMultiForwardDialog) {
        val chosen = messages.filter { it.id in selectedMessageIds }
        AlertDialog(
            onDismissRequest = { showMultiForwardDialog = false },
            containerColor = tokens.surfaceElevated,
            title = { Text(stringResource(R.string.msg_act_forward), color = tokens.textPrimary) },
            text = {
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(allChats.size, key = { allChats[it].id }) { idx ->
                        val dest = allChats[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chosen.forEach { onForwardMessage(dest, it) }
                                    showMultiForwardDialog = false
                                    multiSelectMode = false
                                    selectedMessageIds.clear()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Forward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(dest.localizedTitle(myUid), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMultiForwardDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showPollDialog) {
        CreatePollDialog(
            onDismiss = { showPollDialog = false },
            onCreatePoll = onSendPoll
        )
    }

    if (showQuizDialog) {
        CreateQuizDialog(
            onDismiss = { showQuizDialog = false },
            onCreateQuiz = { q, opts, correctIdx, expl ->
                onSendQuiz(q, opts, correctIdx, expl, selectedTopicId)
            }
        )
    }

    if (showTopicsDialog) {
        GroupTopicsDialog(
            chat = chat,
            topics = topics,
            selectedTopicId = selectedTopicId,
            onSelectTopic = { selectedTopicId = it },
            onCreateTopic = onCreateTopic,
            onDismiss = { showTopicsDialog = false }
        )
    }

    if (showEventsDialog) {
        GroupEventsDialog(
            myUid = myUid,
            chat = chat,
            events = events,
            onCreateEvent = onCreateGroupEvent,
            onToggleRsvp = onToggleEventRsvp,
            onDismiss = { showEventsDialog = false }
        )
    }

    if (showCodeDialog) {
        CodeSnippetDialog(
            onDismiss = { showCodeDialog = false },
            onSendCode = { code -> dispatchSend("CODE", code, null, null, null, null) }
        )
    }

    if (showInfoSheet) {
        ChatInfoSheet(
            myUid = myUid,
            myProfile = myProfile,
            chat = chat,
            messages = messages,
            allUsers = allUsers,
            contacts = contacts,
            onDismiss = { showInfoSheet = false },
            onToggleMute = onToggleMute,
            onToggleArchive = onToggleArchive,
            onSetDisappearing = onSetDisappearing,
            onToggleAdminOnly = onToggleAdminOnly,
            onUpdatePermissions = onUpdatePermissions,
            onRegenerateInvite = onRegenerateInvite,
            onRevokeInvite = onRevokeInvite,
            onApproveJoinRequest = onApproveJoinRequest,
            onUpdateChatTheme = onUpdateChatTheme,
            onAddMember = onAddMember,
            onPromoteAdmin = onPromoteAdmin,
            onRemoveMember = onRemoveMember,
            onToggleFavorite = onToggleFavorite,
            onToggleBlock = onToggleBlock,
            onSubmitReport = onSubmitReport,
            topics = topics,
            events = events,
            privateSetting = privateSetting,
            allGroupChats = allGroupChats,
            onOpenTopics = { showInfoSheet = false; showTopicsDialog = true },
            onOpenEvents = { showInfoSheet = false; showEventsDialog = true },
            onToggleHidden = onToggleHidden,
            onToggleLocked = onToggleLocked,
            onExportChat = onExportChat,
            onSetSlowMode = onSetSlowMode,
            onToggleRestrictedUid = onToggleRestrictedUid,
            onToggleAntiSpam = onToggleAntiSpam,
            onLinkDiscussionGroup = onLinkDiscussionGroup
        )
    }

    if (summaryResult != null) {
        AlertDialog(
            onDismissRequest = onDismissSummary,
            containerColor = tokens.surfaceElevated,
            title = { Text(stringResource(R.string.conv_ai_summary_title), color = tokens.textPrimary) },
            text = { Text(summaryResult, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary) },
            confirmButton = {
                TextButton(onClick = onDismissSummary) { Text(stringResource(R.string.common_done)) }
            }
        )
    }

    if (translationResult != null) {
        AlertDialog(
            onDismissRequest = onDismissTranslation,
            containerColor = tokens.surfaceElevated,
            title = { Text(stringResource(R.string.conv_ai_translation_title, translationResult.first), color = tokens.textPrimary) },
            text = { Text(translationResult.second, style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary) },
            confirmButton = {
                TextButton(onClick = onDismissTranslation) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    if (explainResult != null) {
        AlertDialog(
            onDismissRequest = onDismissExplain,
            containerColor = tokens.surfaceElevated,
            title = { Text(stringResource(R.string.conv_ai_explain_title), color = tokens.textPrimary) },
            text = { Text(explainResult, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary) },
            confirmButton = {
                TextButton(onClick = onDismissExplain) { Text(stringResource(R.string.common_close)) }
            }
        )
    }
}

@Composable
private fun MessageBubbleRow(
    message: ChatMessage,
    isMine: Boolean,
    isGroupedWithPrev: Boolean,
    isGroupedWithNext: Boolean,
    bubbleStyle: String,
    chatAccent: Color,
    myUid: String,
    multiSelectMode: Boolean,
    selected: Boolean,
    playbackState: VoicePlaybackState,
    onTogglePlayVoice: () -> Unit,
    onCyclePlaybackSpeed: () -> Unit,
    onTap: () -> Unit,
    onVotePoll: (Int) -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    val msgType = MessageType.fromRaw(message.type)
    val baseRadius = when (bubbleStyle) {
        "COMPACT" -> com.example.ui.theme.DlRadius.md
        "CLASSIC" -> com.example.ui.theme.DlRadius.sm
        else -> com.example.ui.theme.DlRadius.sheet
    }
    val groupedRadius = if (bubbleStyle == "CLASSIC") com.example.ui.theme.DlRadius.bubbleTail else com.example.ui.theme.DlRadius.bubbleGrouped
    val bubbleShape = RoundedCornerShape(
        topStart = if (!isMine && isGroupedWithPrev) groupedRadius else baseRadius,
        topEnd = if (isMine && isGroupedWithPrev) groupedRadius else baseRadius,
        bottomStart = if (!isMine && !isGroupedWithNext) com.example.ui.theme.DlRadius.bubbleTail else if (!isMine) groupedRadius else baseRadius,
        bottomEnd = if (isMine && !isGroupedWithNext) com.example.ui.theme.DlRadius.bubbleTail else if (isMine) groupedRadius else baseRadius
    )
    val bubbleColor = when {
        selected -> chatAccent.copy(alpha = if (tokens.isDark) 0.28f else 0.20f)
        isMine -> chatAccent.copy(alpha = if (tokens.isDark) 0.17f else 0.11f)
        else -> tokens.bubbleIncoming
    }
    val bubbleBorder = when {
        selected -> BorderStroke(1.5.dp, chatAccent)
        isMine -> BorderStroke(com.example.ui.theme.DlSizing.divider, chatAccent.copy(alpha = 0.32f))
        else -> BorderStroke(com.example.ui.theme.DlSizing.divider, tokens.bubbleIncomingBorder)
    }
    val isStarred = myUid in message.starredBy
    val isReadByPeer = message.readBy.any { it != message.senderId }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
        ) {
            if (multiSelectMode) {
                Checkbox(checked = selected, onCheckedChange = { onTap() })
            }

            Surface(
                modifier = Modifier
                    .widthIn(max = com.example.ui.theme.DlSpacing.bubbleMaxWidth)
                    .clip(bubbleShape)
                    .clickable(onClick = onTap),
                shape = bubbleShape,
                color = bubbleColor,
                border = bubbleBorder
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = com.example.ui.theme.DlSpacing.bubblePaddingH,
                        vertical = com.example.ui.theme.DlSpacing.bubblePaddingV
                    )
                ) {
                    if (!message.forwardedFrom.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.conv_msg_forwarded, message.forwardedFrom),
                            style = tokens.messagingType.messageTimestamp,
                            fontStyle = FontStyle.Italic,
                            color = chatAccent
                        )
                        Spacer(Modifier.height(com.example.ui.theme.DlSpacing.xxs))
                    }

                    if (!isMine && !isGroupedWithPrev) {
                        Text(
                            text = message.senderName,
                            style = tokens.messagingType.messageSender,
                            color = chatAccent
                        )
                        Spacer(Modifier.height(com.example.ui.theme.DlSpacing.xxs))
                    }

                    if (!message.replyToPreview.isNullOrBlank()) {
                        Surface(
                            color = tokens.replySurface,
                            shape = RoundedCornerShape(com.example.ui.theme.DlRadius.sm),
                            border = BorderStroke(com.example.ui.theme.DlSizing.divider, tokens.hairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(IntrinsicSize.Min)
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(com.example.ui.theme.DlRadius.xxs))
                                        .background(chatAccent)
                                )
                                Spacer(Modifier.width(com.example.ui.theme.DlSpacing.sm))
                                Text(
                                    text = message.replyToPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (message.deletedForEveryone) {
                        Text(
                            text = stringResource(R.string.conv_msg_deleted),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = tokens.textTertiary
                        )
                    } else {
                        if (msgType == MessageType.IMAGE && !message.mediaDataBase64.isNullOrBlank()) {
                            val bmp = remember(message.mediaDataBase64) {
                                ImageUtils.decodeBase64Bitmap(message.mediaDataBase64)
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = message.text,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }

                        if (msgType == MessageType.VOICE_NOTE) {
                            val isActive = playbackState.activeMessageId == message.id
                            val progress = if (isActive && playbackState.durationMs > 0) {
                                (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onTogglePlayVoice,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(chatAccent)
                                ) {
                                    Icon(
                                        if (isActive && playbackState.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = stringResource(
                                            if (isActive && playbackState.playing) R.string.conv_msg_pause_cd else R.string.conv_msg_play_cd
                                        ),
                                        tint = contrastInkFor(chatAccent)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                AudioWaveformBars(
                                    samples = message.waveformSamples ?: emptyList(),
                                    progressRatio = progress,
                                    activeColor = chatAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = tokens.surfaceCard,
                                    border = BorderStroke(1.dp, tokens.hairline),
                                    modifier = Modifier.clickable(onClick = onCyclePlaybackSpeed)
                                ) {
                                    Text(
                                        text = "${playbackState.speed}x",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (isActive && playbackState.error != null) {
                                Text(
                                    text = playbackState.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (message.audioDurationMs != null) {
                                Text(
                                    text = formatDuration(message.audioDurationMs / 1000L),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textSecondary
                                )
                            }
                            if (!message.aiTranscribedText.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = tokens.surfaceSubtle,
                                    border = BorderStroke(1.dp, tokens.hairline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(R.string.conv_msg_summary_prefix, message.aiTranscribedText),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textPrimary,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        } else if (msgType in setOf(MessageType.DOCUMENT, MessageType.VIDEO, MessageType.AUDIO)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = tokens.surfaceSubtle,
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Description, null, tint = chatAccent)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            message.fileName ?: message.type,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = tokens.textPrimary
                                        )
                                        val size = message.fileSizeBytes?.let { LocalStorageManager.formatByteCount(it) } ?: ""
                                        Text(
                                            stringResource(
                                                R.string.conv_file_local_only_note,
                                                message.fileName ?: message.type,
                                                size.ifBlank { message.type }
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.textSecondary
                                        )
                                    }
                                }
                            }
                        } else if ((msgType == MessageType.POLL || msgType == MessageType.QUIZ) && !message.pollOptions.isNullOrEmpty()) {
                            val isQuiz = msgType == MessageType.QUIZ
                            val quizTag = if (isQuiz) "${stringResource(R.string.conv_quiz_badge)}: " else ""
                            Text(
                                text = "$quizTag${message.pollQuestion ?: message.text}",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            val votes = message.pollVotes ?: emptyMap()
                            val totalVotes = votes.size.coerceAtLeast(1)
                            val myVote = votes[myUid]
                            message.pollOptions.forEachIndexed { idx, opt ->
                                val count = votes.values.count { it == idx.toString() }
                                val fraction = count.toFloat() / totalVotes.toFloat()
                                val animFraction by animateFloatAsState(
                                    targetValue = if (tokens.reducedMotion) fraction else fraction,
                                    label = "poll_progress_$idx"
                                )
                                val chosen = myVote == idx.toString()
                                val isCorrectOpt = isQuiz && myVote != null && message.quizCorrectIndex == idx
                                val isWrongChosen = isQuiz && chosen && message.quizCorrectIndex != null && message.quizCorrectIndex != idx
                                val fillColor = when {
                                    isCorrectOpt -> tokens.statusOnline.copy(alpha = 0.25f)
                                    isWrongChosen -> tokens.statusBusy.copy(alpha = 0.25f)
                                    chosen -> chatAccent.copy(alpha = 0.22f)
                                    else -> tokens.surfaceCard
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onVotePoll(idx) },
                                    color = tokens.surfaceSubtle,
                                    border = BorderStroke(1.dp, if (chosen) chatAccent else tokens.hairline)
                                ) {
                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(if (votes.isEmpty()) 0f else animFraction.coerceIn(0f, 1f))
                                                .height(34.dp)
                                                .background(fillColor)
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(opt, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                                            Text(
                                                text = "${(count * 100) / totalVotes}% ($count)",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                            if (isQuiz && myVote != null && !message.quizExplanation.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.conv_quiz_explanation_prefix, message.quizExplanation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.statusOnline
                                )
                            }
                        } else if (msgType == MessageType.STICKER) {
                            val sticker = DlStickerPack.findById(message.stickerId)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(sticker?.glyph ?: "🛡️", fontSize = 48.sp)
                                Text(
                                    sticker?.localizedLabel() ?: message.text,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = chatAccent
                                )
                            }
                        } else if (msgType == MessageType.CODE) {
                            DlCodeBlock(rawText = message.text, accentColor = chatAccent)
                        } else if (message.text.isNotBlank()) {
                            if (containsRichFormatting(message.text)) {
                                RichMessageText(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    accentColor = chatAccent
                                )
                            } else {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = tokens.textPrimary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.encrypted) {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = stringResource(R.string.conv_msg_encrypted_cd),
                                tint = tokens.statusOnline,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        if (isStarred) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.conv_msg_starred_cd),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        if (message.scheduledForEpochMs != null && message.scheduledForEpochMs > System.currentTimeMillis()) {
                            Text(
                                stringResource(R.string.conv_msg_scheduled_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = chatAccent
                            )
                        }
                        if (message.edited) {
                            Text(
                                stringResource(R.string.conv_msg_edited_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textSecondary
                            )
                        }
                        Text(
                            text = formatRelativeTimeLocalized(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textTertiary
                        )
                        if (isMine) {
                            Icon(
                                imageVector = if (isReadByPeer || message.deliveredTo.isNotEmpty()) Icons.Filled.DoneAll else Icons.Filled.Done,
                                contentDescription = stringResource(
                                    if (isReadByPeer) R.string.conv_msg_read_cd else R.string.conv_msg_delivered_cd
                                ),
                                tint = if (isReadByPeer) tokens.statusOnline else tokens.textTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        if (message.reactions.isNotEmpty()) {
            val grouped = message.reactions.values.groupingBy { it }.eachCount()
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (emoji, count) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.surfaceElevated,
                        border = BorderStroke(1.dp, tokens.hairline)
                    ) {
                        Text(
                            text = "$emoji $count",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
