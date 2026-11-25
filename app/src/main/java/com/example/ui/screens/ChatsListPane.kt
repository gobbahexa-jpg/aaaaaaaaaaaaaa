package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChatFolder
import com.example.data.model.ChatFolderMatcher
import com.example.data.model.ChatRoom
import com.example.data.model.ChatType
import com.example.data.model.ContactEntry
import com.example.data.model.PrivateChatSetting
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlEmptyState
import com.example.ui.components.DlErrorState
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlLoadingState
import com.example.ui.components.DlSearchField
import com.example.ui.components.DlSectionHeader
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.components.DlTopBar
import com.example.ui.components.formatRelativeTimeLocalized
import com.example.ui.components.localizedTitle
import com.example.ui.theme.DlSizing
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@Composable
fun ChatsListPane(
    myUid: String,
    myProfile: UserProfile?,
    chatsState: UiState<List<ChatRoom>>,
    publicChannelsState: UiState<List<ChatRoom>>,
    usersState: UiState<List<UserProfile>>,
    contacts: List<ContactEntry>,
    folders: List<ChatFolder>,
    selectedChatId: String?,
    aiSearchResult: String?,
    aiBusy: Boolean,
    onSelectChat: (String) -> Unit,
    onOpenDirectChat: (UserProfile) -> Unit,
    onJoinChannel: (ChatRoom) -> Unit,
    onCreateGroupOrChannel: (
        type: String,
        title: String,
        description: String,
        members: List<UserProfile>,
        publicVisible: Boolean,
        handle: String?,
        adminOnly: Boolean
    ) -> Unit,
    onCreateFolder: (name: String, filterType: String, includeIds: List<String>, excludeIds: List<String>) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onAiSearchAssist: (String) -> Unit,
    onClearAiSearch: () -> Unit,
    onOpenChannelsExplorer: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenSavedNotes: () -> Unit,
    modifier: Modifier = Modifier,
    draftsByChatId: Map<String, String> = emptyMap(),
    privateChatSettings: Map<String, PrivateChatSetting> = emptyMap(),
    localAccountsCount: Int = 1,
    onOpenHiddenVault: () -> Unit = {},
    onOpenAccountSwitcher: () -> Unit = {},
    onOpenQrScanner: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    restrictionNotice: String? = null,
    onOpenAccountStatus: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    var searchQuery by remember { mutableStateOf("") }
    var searchCategory by remember { mutableStateOf("ALL") }
    var activeFilter by remember { mutableStateOf("ALL") }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    val allUsers = (usersState as? UiState.Success)?.data.orEmpty().filter { it.userId != myUid }
    val blockedUids = remember(contacts) { contacts.filter { it.blocked }.map { it.contactUid }.toSet() }
    val favoritePeerUids = remember(contacts) { contacts.filter { it.favorite && !it.blocked }.map { it.contactUid }.toSet() }
    val allChats = (chatsState as? UiState.Success)?.data.orEmpty()
    val publicChannels = (publicChannelsState as? UiState.Success)?.data.orEmpty()

    val selectedCustomFolder = remember(activeFilter, folders) {
        if (activeFilter.startsWith("FOLDER:")) {
            val id = activeFilter.removePrefix("FOLDER:")
            folders.firstOrNull { it.id == id }
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Uncluttered Hallmark Header: Avatar + Title on left, 3 crisp actions on right
            DlTopBar(
                title = stringResource(R.string.chats_title),
                subtitle = myProfile?.username?.let { "@$it" } ?: stringResource(R.string.auth_brand_header),
                leadingAvatar = {
                    DlAvatar(
                        name = myProfile?.displayName ?: stringResource(R.string.chats_title),
                        colorHex = myProfile?.avatarColor ?: "#00E5FF",
                        photoBase64 = myProfile?.photoDataBase64,
                        onlineStatus = myProfile?.onlineStatus ?: "ONLINE",
                        size = 40.dp
                    )
                },
                onLeadingClick = onOpenAccountSwitcher,
                actions = {
                    IconButton(
                        onClick = onOpenQrScanner,
                        modifier = Modifier.testTag("open_qr_scanner_button")
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.shell_qr_scanner_cd))
                    }
                    IconButton(
                        onClick = onOpenHiddenVault,
                        modifier = Modifier.testTag("hidden_vault_button")
                    ) {
                        Icon(Icons.Filled.VisibilityOff, contentDescription = stringResource(R.string.shell_hidden_vault_cd))
                    }
                    if (onOpenSettings != null) {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("tab_settings")
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.shell_settings_cd))
                        }
                    }
                }
            )
            if (!restrictionNotice.isNullOrBlank()) {
                ContextualRestrictionBanner(
                    message = restrictionNotice,
                    onOpenAccountStatus = onOpenAccountStatus
                )
            }

            Spacer(modifier = Modifier.height(DlSpacing.md))

            // Search Field
            DlSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.chats_search_placeholder),
                modifier = Modifier
                    .padding(horizontal = DlSpacing.base)
                    .testTag("global_search_input")
            )

            // Workspace quick-access utility pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = DlSpacing.base, vertical = DlSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(DlSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onOpenChannelsExplorer,
                    label = { Text(stringResource(R.string.channels_title)) },
                    leadingIcon = { Icon(Icons.Filled.Campaign, contentDescription = null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = tokens.surfaceSubtle),
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.testTag("open_channels_explorer_button")
                )
                AssistChip(
                    onClick = onOpenSavedNotes,
                    label = { Text(stringResource(R.string.settings_vault_badge)) },
                    leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = tokens.surfaceSubtle),
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.testTag("saved_notes_button")
                )
                AssistChip(
                    onClick = onOpenAccountSwitcher,
                    label = { Text("${stringResource(R.string.settings_cat_account)} ($localAccountsCount)") },
                    leadingIcon = { Icon(Icons.Filled.SwitchAccount, contentDescription = null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = tokens.surfaceSubtle),
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.testTag("account_switcher_button")
                )
            }

            AnimatedVisibility(visible = searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = DlSpacing.base, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to stringResource(R.string.chats_folder_all),
                        "UNREAD" to stringResource(R.string.chats_folder_unread),
                        "MEDIA" to stringResource(R.string.conv_attach_photo),
                        "TYPING" to stringResource(R.string.conv_typing_ellipsis)
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = searchCategory == key,
                            onClick = { searchCategory = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = aiSearchResult != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DlSpacing.base, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.chats_ai_search_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                aiSearchResult.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textPrimary
                            )
                        }
                        IconButton(onClick = onClearAiSearch, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chats_ai_clear), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Folder Tabs Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = DlSpacing.base, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val builtIn = listOf(
                    "ALL" to stringResource(R.string.chats_folder_all),
                    "UNREAD" to stringResource(R.string.chats_folder_unread),
                    "FAVORITE" to stringResource(R.string.contacts_filter_favorites),
                    "DIRECT" to stringResource(R.string.chats_folder_direct),
                    "GROUP" to stringResource(R.string.chats_folder_group),
                    "CHANNEL" to stringResource(R.string.chats_folder_channel),
                    "BOT" to stringResource(R.string.chats_folder_bots),
                    "ARCHIVED" to stringResource(R.string.chats_folder_archived)
                )
                builtIn.forEach { (key, label) ->
                    val unreadCount = remember(allChats, key, favoritePeerUids, privateChatSettings) {
                        allChats.count { c ->
                            privateChatSettings[c.id]?.hidden != true &&
                                c.hasUnreadFor(myUid) &&
                                ChatFolderMatcher.matchesFolder(c, myUid, key, favoritePeerUids = favoritePeerUids)
                        }
                    }
                    FilterChip(
                        selected = activeFilter == key,
                        onClick = { activeFilter = key },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        label = { Text(if (unreadCount > 0) "$label · $unreadCount" else label) },
                        modifier = Modifier.testTag("folder_chip_${key.lowercase()}")
                    )
                }
                folders.forEach { folder ->
                    val folderKey = "FOLDER:${folder.id}"
                    FilterChip(
                        selected = activeFilter == folderKey,
                        onClick = { activeFilter = folderKey },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, Modifier.size(16.dp)) },
                        trailingIcon = if (activeFilter == folderKey) {
                            {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.chats_delete_folder_cd),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            activeFilter = "ALL"
                                            onDeleteFolder(folder.id)
                                        }
                                )
                            }
                        } else null,
                        label = { Text(folder.name) }
                    )
                }
                IconButton(
                    onClick = { showNewFolderDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("add_folder_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chats_new_folder_chip), modifier = Modifier.size(18.dp))
                }
            }

            DlHairlineDivider()

            when (chatsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DlLoadingState(message = stringResource(R.string.chats_loading))
                    }
                }
                is UiState.Error -> {
                    DlErrorState(
                        title = stringResource(R.string.chats_error_title),
                        message = chatsState.message
                    )
                }
                is UiState.Success -> {
                    val filteredChats = remember(
                        chatsState.data, activeFilter, selectedCustomFolder, searchQuery, searchCategory, favoritePeerUids, privateChatSettings, myUid
                    ) {
                        val q = searchQuery.trim().lowercase()
                        chatsState.data.filter { chat ->
                            val isHidden = privateChatSettings[chat.id]?.hidden == true
                            if (isHidden) return@filter false
                            val folderMatch = ChatFolderMatcher.matchesFolder(
                                chat = chat,
                                myUid = myUid,
                                filterKey = activeFilter,
                                customFolder = selectedCustomFolder,
                                favoritePeerUids = favoritePeerUids
                            )
                            val queryMatch = q.isEmpty() ||
                                chat.resolveTitle(myUid).lowercase().contains(q) ||
                                chat.lastMessageText.lowercase().contains(q) ||
                                (chat.channelHandle?.lowercase()?.contains(q) == true)
                            val catMatch = if (q.isEmpty()) true else when (searchCategory) {
                                "UNREAD" -> chat.hasUnreadFor(myUid)
                                "MEDIA" -> chat.lastMessageText.contains("photo", ignoreCase = true) ||
                                    chat.lastMessageText.contains("voice", ignoreCase = true) ||
                                    chat.lastMessageText.contains("image", ignoreCase = true) ||
                                    chat.lastMessageText.contains("attachment", ignoreCase = true) ||
                                    chat.lastMessageText.contains("document", ignoreCase = true)
                                "TYPING" -> chat.activeTypersExcluding(myUid).isNotEmpty()
                                else -> true
                            }
                            folderMatch && queryMatch && catMatch
                        }
                    }

                    val matchedUsers = remember(allUsers, searchQuery, blockedUids) {
                        val q = searchQuery.trim().lowercase()
                        if (q.isEmpty()) emptyList()
                        else allUsers.filter {
                            it.userId !in blockedUids &&
                                (it.displayName.lowercase().contains(q) || it.username.lowercase().contains(q))
                        }
                    }

                    val myChatIds = remember(chatsState.data) { chatsState.data.map { it.id }.toSet() }
                    val matchedPublicChannels = remember(publicChannels, myChatIds, searchQuery) {
                        val q = searchQuery.trim().lowercase()
                        if (q.isEmpty()) emptyList()
                        else publicChannels.filter {
                            it.id !in myChatIds && (
                                it.title.lowercase().contains(q) ||
                                    (it.channelHandle?.lowercase()?.contains(q) == true) ||
                                    it.description.lowercase().contains(q)
                                )
                        }
                    }

                    if (filteredChats.isEmpty() && matchedUsers.isEmpty() && matchedPublicChannels.isEmpty()) {
                        DlEmptyState(
                            icon = Icons.Filled.ChatBubbleOutline,
                            title = stringResource(if (searchQuery.isNotBlank()) R.string.contacts_empty_title else R.string.chats_empty_title),
                            subtitle = stringResource(R.string.chats_empty_subtitle),
                            actionLabel = stringResource(R.string.chats_empty_cta),
                            onAction = { showNewChatDialog = true }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            if (matchedUsers.isNotEmpty()) {
                                item { DlSectionHeader(title = stringResource(R.string.contacts_filter_directory)) }
                                items(matchedUsers, key = { "u_${it.userId}" }) { user ->
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onOpenDirectChat(user) }
                                                .padding(horizontal = DlSpacing.base, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            DlAvatar(
                                                name = user.displayName,
                                                colorHex = user.avatarColor,
                                                photoBase64 = user.photoDataBase64,
                                                onlineStatus = if (user.showOnline) user.onlineStatus else null
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "@${user.username}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            DlStatusPill(text = stringResource(R.string.contacts_message_cta))
                                        }
                                        DlHairlineDivider(modifier = Modifier.padding(start = 74.dp))
                                    }
                                }
                            }

                            if (matchedPublicChannels.isNotEmpty()) {
                                item { DlSectionHeader(title = stringResource(R.string.chats_public_channels_header)) }
                                items(matchedPublicChannels, key = { "ch_${it.id}" }) { channel ->
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onSelectChat(channel.id) }
                                                .padding(horizontal = DlSpacing.base, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            DlAvatar(name = channel.title, colorHex = "#6C5CE7", size = 44.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(channel.title, style = MaterialTheme.typography.titleMedium)
                                                val subCountText = stringResource(R.string.chats_subscribers_count, channel.memberUids.size)
                                                Text(
                                                    "${channel.channelHandle?.let { "@$it · " } ?: ""}$subCountText",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = tokens.textSecondary
                                                )
                                            }
                                            OutlinedButton(onClick = { onJoinChannel(channel) }) {
                                                Text(stringResource(R.string.common_join))
                                            }
                                        }
                                        DlHairlineDivider(modifier = Modifier.padding(start = 72.dp))
                                    }
                                }
                            }

                            items(filteredChats, key = { it.id }) { chat ->
                                ChatRowItem(
                                    chat = chat,
                                    myUid = myUid,
                                    selected = chat.id == selectedChatId,
                                    draftText = draftsByChatId[chat.id],
                                    isLockedChat = privateChatSettings[chat.id]?.locked == true,
                                    onClick = { onSelectChat(chat.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewChatDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(DlSpacing.base)
                .testTag("new_chat_fab"),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chats_new_chat_fab_cd))
        }
    }

    if (showNewChatDialog) {
        NewConversationDialog(
            users = allUsers.filter { it.userId !in blockedUids },
            onDismiss = { showNewChatDialog = false },
            onStartDirect = { user ->
                showNewChatDialog = false
                onOpenDirectChat(user)
            },
            onCreateGroupOrChannel = { type, title, desc, members, pub, handle, adminOnly ->
                showNewChatDialog = false
                onCreateGroupOrChannel(type, title, desc, members, pub, handle, adminOnly)
            }
        )
    }

    if (showNewFolderDialog) {
        CreateFolderDialog(
            myUid = myUid,
            allChats = allChats,
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name, filterType, includes, excludes ->
                onCreateFolder(name, filterType, includes, excludes)
                showNewFolderDialog = false
            }
        )
    }
}

@Composable
private fun ChatRowItem(
    chat: ChatRoom,
    myUid: String,
    selected: Boolean,
    draftText: String? = null,
    isLockedChat: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    val title = chat.localizedTitle(myUid)
    val isMuted = myUid in chat.mutedBy
    val isArchived = myUid in chat.archivedBy
    val chatType = ChatType.fromRaw(chat.type)
    val unread = chat.hasUnreadFor(myUid)
    val typers = chat.activeTypersExcluding(myUid)
    val verticalPad = DlSpacing.rowVertical(tokens.compactDensity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .testTag("chat_item_${chat.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DlSpacing.base, vertical = verticalPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                DlAvatar(
                    name = title,
                    colorHex = chat.themeAccentHex ?: when (chatType) {
                        ChatType.CHANNEL -> "#6C5CE7"
                        ChatType.GROUP -> "#00D68F"
                        ChatType.DIRECT -> "#00E5FF"
                    },
                    size = DlSizing.avatarChat
                )
                if (chatType != ChatType.DIRECT) {
                    Box(
                        modifier = Modifier
                            .size(19.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(DlSpacing.xxs),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (chatType == ChatType.CHANNEL) Icons.Filled.Campaign else Icons.Filled.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(DlSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = tokens.messagingType.chatTitle,
                        color = tokens.textPrimary,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (chat.e2eeEnabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.conv_msg_encrypted_cd),
                            tint = tokens.statusOnline,
                            modifier = Modifier.size(DlSizing.iconXs)
                        )
                    }
                    if (isLockedChat) {
                        Spacer(modifier = Modifier.width(DlSpacing.xs))
                        Icon(
                            Icons.Filled.VisibilityOff,
                            contentDescription = stringResource(R.string.chats_badge_locked),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(DlSizing.iconXs)
                        )
                    }
                    if (isMuted) {
                        Spacer(modifier = Modifier.width(DlSpacing.xs))
                        Icon(
                            Icons.Filled.NotificationsOff,
                            contentDescription = stringResource(R.string.chats_mute_cd),
                            tint = tokens.textTertiary,
                            modifier = Modifier.size(DlSizing.iconXs)
                        )
                    }
                    if (isArchived) {
                        Spacer(modifier = Modifier.width(DlSpacing.xs))
                        Icon(
                            Icons.Filled.Archive,
                            contentDescription = stringResource(R.string.info_archived),
                            tint = tokens.textTertiary,
                            modifier = Modifier.size(DlSizing.iconXs)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val preview = when {
                        typers.isNotEmpty() -> stringResource(R.string.chats_typing_single, typers.first())
                        !draftText.isNullOrBlank() -> stringResource(R.string.chats_draft_prefix, draftText)
                        else -> chat.lastMessageText.ifBlank { stringResource(R.string.chats_no_messages_yet) }
                    }
                    Text(
                        text = preview,
                        style = tokens.messagingType.chatPreview,
                        color = when {
                            typers.isNotEmpty() -> MaterialTheme.colorScheme.primary
                            !draftText.isNullOrBlank() -> MaterialTheme.colorScheme.error
                            unread -> tokens.textPrimary
                            else -> tokens.textSecondary
                        },
                        fontWeight = if (unread || typers.isNotEmpty()) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatRelativeTimeLocalized(chat.lastMessageAt),
                    style = tokens.messagingType.messageTimestamp,
                    color = if (unread) MaterialTheme.colorScheme.primary else tokens.textTertiary
                )
                if (unread) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(tokens.badgeUnread)
                            .padding(horizontal = 7.dp, vertical = DlSpacing.xxs)
                    ) {
                        Text(
                            text = stringResource(R.string.stories_badge_new),
                            style = tokens.messagingType.unreadCount,
                            color = tokens.onBadgeUnread
                        )
                    }
                } else if (chatType != ChatType.DIRECT) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "${chat.memberUids.size}",
                        style = tokens.messagingType.messageTimestamp,
                        color = tokens.textTertiary
                    )
                }
            }
        }
        DlHairlineDivider(modifier = Modifier.padding(start = 76.dp))
    }
}

@Composable
private fun CreateFolderDialog(
    myUid: String,
    allChats: List<ChatRoom>,
    onDismiss: () -> Unit,
    onCreate: (name: String, filterType: String, includeIds: List<String>, excludeIds: List<String>) -> Unit
) {
    val context = LocalContext.current
    var folderName by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("CUSTOM") }
    val includeIds = remember { mutableStateListOf<String>() }
    val excludeIds = remember { mutableStateListOf<String>() }

    val filterLabels = mapOf(
        "CUSTOM" to stringResource(R.string.chats_folder_all),
        "UNREAD" to stringResource(R.string.chats_folder_unread),
        "FAVORITE" to stringResource(R.string.contacts_filter_favorites),
        "DIRECT" to stringResource(R.string.chats_folder_direct),
        "GROUP" to stringResource(R.string.chats_folder_group),
        "CHANNEL" to stringResource(R.string.chats_folder_channel),
        "BOT" to stringResource(R.string.chats_folder_bots)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DlTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = stringResource(R.string.folder_name_label)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CUSTOM", "UNREAD", "FAVORITE", "DIRECT", "GROUP", "CHANNEL", "BOT").forEach { ft ->
                        FilterChip(
                            selected = filterType == ft,
                            onClick = { filterType = ft },
                            label = { Text(filterLabels[ft] ?: ft) }
                        )
                    }
                }
                if (allChats.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.folder_filter_label),
                        style = MaterialTheme.typography.labelSmall
                    )
                    LazyColumn(modifier = Modifier.height(140.dp)) {
                        items(allChats.take(20), key = { it.id }) { c ->
                            val isIncluded = c.id in includeIds
                            val isExcluded = c.id in excludeIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (filterType == "CUSTOM") {
                                            if (isIncluded) includeIds.remove(c.id) else includeIds.add(c.id)
                                        } else {
                                            if (isExcluded) excludeIds.remove(c.id) else excludeIds.add(c.id)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = if (filterType == "CUSTOM") isIncluded else isExcluded,
                                    onCheckedChange = { checked ->
                                        if (filterType == "CUSTOM") {
                                            if (checked) includeIds.add(c.id) else includeIds.remove(c.id)
                                        } else {
                                            if (checked) excludeIds.add(c.id) else excludeIds.remove(c.id)
                                        }
                                    }
                                )
                                Text(c.localizedTitle(myUid), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (folderName.isNotBlank()) {
                    onCreate(folderName.trim(), filterType, includeIds.toList(), excludeIds.toList())
                }
            }) { Text(stringResource(R.string.folder_create_cta)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun NewConversationDialog(
    users: List<UserProfile>,
    onDismiss: () -> Unit,
    onStartDirect: (UserProfile) -> Unit,
    onCreateGroupOrChannel: (
        type: String,
        title: String,
        description: String,
        members: List<UserProfile>,
        publicVisible: Boolean,
        handle: String?,
        adminOnly: Boolean
    ) -> Unit
) {
    var mode by remember { mutableStateOf("DIRECT") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var publicVisible by remember { mutableStateOf(true) }
    var adminOnly by remember { mutableStateOf(false) }
    val selectedMembers = remember { mutableStateListOf<UserProfile>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_chat_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "DIRECT" to stringResource(R.string.new_chat_type_direct),
                        "GROUP" to stringResource(R.string.new_chat_type_group),
                        "CHANNEL" to stringResource(R.string.new_chat_type_channel)
                    ).forEach { (k, label) ->
                        FilterChip(
                            selected = mode == k,
                            onClick = { mode = k },
                            label = { Text(label) }
                        )
                    }
                }

                if (mode == "DIRECT") {
                    if (users.isEmpty()) {
                        Text(
                            stringResource(R.string.new_chat_no_users),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalDlTokens.current.textSecondary
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(users, key = { it.userId }) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onStartDirect(user) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DlAvatar(
                                        name = user.displayName,
                                        colorHex = user.avatarColor,
                                        photoBase64 = user.photoDataBase64,
                                        size = 38.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                                        Text("@${user.username}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else {
                    DlTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = stringResource(
                            if (mode == "CHANNEL") R.string.new_chat_channel_name_label else R.string.new_chat_group_name_label
                        )
                    )
                    DlTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = stringResource(R.string.new_chat_desc_label),
                        singleLine = false,
                        maxLines = 2
                    )
                    if (mode == "CHANNEL") {
                        DlTextField(
                            value = handle,
                            onValueChange = { handle = it },
                            label = stringResource(R.string.new_chat_handle_label)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.new_chat_public_channel),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Switch(checked = publicVisible, onCheckedChange = { publicVisible = it })
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.new_chat_admin_only),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Switch(checked = adminOnly, onCheckedChange = { adminOnly = it })
                        }
                    }
                    if (users.isNotEmpty()) {
                        Text(stringResource(R.string.new_chat_add_members), style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.height(120.dp)) {
                            items(users, key = { it.userId }) { user ->
                                val checked = selectedMembers.any { it.userId == user.userId }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (checked) selectedMembers.removeAll { it.userId == user.userId }
                                            else selectedMembers.add(user)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            if (it) selectedMembers.add(user)
                                            else selectedMembers.removeAll { m -> m.userId == user.userId }
                                        }
                                    )
                                    Text("${user.displayName} (@${user.username})", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (mode != "DIRECT") {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onCreateGroupOrChannel(
                                mode,
                                title,
                                description,
                                selectedMembers.toList(),
                                publicVisible,
                                handle.takeIf { it.isNotBlank() },
                                adminOnly
                            )
                        }
                    }
                ) {
                    Text(
                        stringResource(
                            if (mode == "CHANNEL") R.string.new_chat_launch_channel else R.string.new_chat_create_group
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}
