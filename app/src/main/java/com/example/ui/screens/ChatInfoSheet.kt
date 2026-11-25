package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.CryptoIdentityHelper
import com.example.core.ImageUtils
import com.example.core.LocalStorageManager
import com.example.core.QrCodeUtils
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.ChatTopic
import com.example.data.model.ChatType
import com.example.data.model.ContactEntry
import com.example.data.model.GroupEvent
import com.example.data.model.PrivateChatSetting
import com.example.data.model.UserProfile
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlFocalCard
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlSecondaryButton
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.components.QrDisplayDialog
import com.example.ui.components.localizedTitle
import com.example.ui.theme.AccentColorOptions
import com.example.ui.theme.DlCrimson
import com.example.ui.theme.DlEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDlTokens
import com.example.ui.theme.contrastInkFor
import com.example.ui.theme.resolveAccessibleAccent

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoSheet(
    myUid: String,
    myProfile: UserProfile?,
    chat: ChatRoom,
    messages: List<ChatMessage>,
    allUsers: List<UserProfile>,
    contacts: List<ContactEntry>,
    onDismiss: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArchive: () -> Unit,
    onSetDisappearing: (Long) -> Unit,
    onToggleAdminOnly: (Boolean) -> Unit,
    onUpdatePermissions: (allowPinning: Boolean, allowInvites: Boolean) -> Unit,
    onRegenerateInvite: () -> Unit,
    onRevokeInvite: (Boolean) -> Unit,
    onApproveJoinRequest: (UserProfile, Boolean) -> Unit,
    onUpdateChatTheme: (String) -> Unit,
    onAddMember: (UserProfile) -> Unit,
    onPromoteAdmin: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onToggleFavorite: (UserProfile, ContactEntry?) -> Unit,
    onToggleBlock: (UserProfile, ContactEntry?) -> Unit,
    onSubmitReport: (String, String, String) -> Unit,
    topics: List<ChatTopic> = emptyList(),
    events: List<GroupEvent> = emptyList(),
    privateSetting: PrivateChatSetting? = null,
    allGroupChats: List<ChatRoom> = emptyList(),
    onOpenTopics: () -> Unit = {},
    onOpenEvents: () -> Unit = {},
    onToggleHidden: () -> Unit = {},
    onToggleLocked: () -> Unit = {},
    onExportChat: (format: String) -> Unit = {},
    onSetSlowMode: (Int) -> Unit = {},
    onToggleRestrictedUid: (String) -> Unit = {},
    onToggleAntiSpam: (Boolean) -> Unit = {},
    onLinkDiscussionGroup: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    val clipboard = LocalClipboardManager.current
    val chatType = ChatType.fromRaw(chat.type)
    val isAdmin = myUid in chat.adminUids
    val isMuted = myUid in chat.mutedBy
    val isArchived = myUid in chat.archivedBy
    val peerUid = if (chatType == ChatType.DIRECT) chat.memberUids.firstOrNull { it != myUid } else null
    val peerProfile = allUsers.firstOrNull { it.userId == peerUid }
    val peerContact = contacts.firstOrNull { it.contactUid == peerUid }

    val mediaMessages = remember(messages) {
        messages.filter { !it.mediaDataBase64.isNullOrBlank() && it.type == "IMAGE" && !it.deletedForEveryone }
    }
    val documentMessages = remember(messages) {
        messages.filter { !it.deletedForEveryone && it.type in setOf("DOCUMENT", "AUDIO", "VIDEO", "VOICE_NOTE") }
    }

    var mediaTab by remember { mutableStateOf("MEDIA") }
    var showAddMember by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showInviteQrDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val chatTitle = chat.localizedTitle(myUid)
                    DlAvatar(
                        name = chatTitle,
                        colorHex = chat.themeAccentHex ?: "#00E5FF",
                        photoBase64 = peerProfile?.photoDataBase64,
                        size = 56.dp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(chatTitle, style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
                        Text(
                            text = when (chatType) {
                                ChatType.DIRECT -> peerProfile?.username?.let { "@$it" } ?: stringResource(R.string.info_direct_sub)
                                ChatType.GROUP -> stringResource(R.string.info_group_sub, chat.memberUids.size)
                                ChatType.CHANNEL -> stringResource(R.string.info_channel_sub, chat.memberUids.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.info_close_cd))
                    }
                }
            }

            if (chat.description.isNotBlank()) {
                item {
                    Text(
                        text = chat.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
            }

            // ECDH Key & Safety Number Verification
            item {
                DlFocalCard(
                    accentBorder = if (chat.e2eeEnabled) tokens.statusOnline else MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (chat.e2eeEnabled) Icons.Filled.Lock else Icons.Filled.Security,
                                contentDescription = null,
                                tint = if (chat.e2eeEnabled) tokens.statusOnline else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (chat.e2eeEnabled) R.string.info_e2ee_title else R.string.info_cloud_title
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.textPrimary
                            )
                        }
                        DlStatusPill(
                            text = stringResource(if (chat.e2eeEnabled) R.string.info_pill_e2ee else R.string.info_pill_cloud),
                            color = if (chat.e2eeEnabled) tokens.statusOnline else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val safetyNumber = if (myProfile != null && peerProfile != null) {
                        CryptoIdentityHelper.computeSharedSafetyNumber(
                            myProfile.publicKeyFingerprint,
                            peerProfile.publicKeyFingerprint
                        )
                    } else {
                        myProfile?.publicKeyFingerprint ?: stringResource(R.string.common_active)
                    }
                    val peerKeyVer = peerProfile?.keyVersion ?: myProfile?.keyVersion ?: 1
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.surfaceCard,
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (chat.e2eeEnabled) {
                                stringResource(R.string.info_safety_num, safetyNumber, peerKeyVer)
                            } else {
                                stringResource(R.string.info_your_key, myProfile?.keyVersion ?: 1, safetyNumber)
                            },
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    if (chat.e2eeEnabled) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.info_e2ee_ai_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }

            // Quick controls + Vault + Export
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isMuted,
                            onClick = onToggleMute,
                            leadingIcon = { Icon(Icons.Filled.NotificationsOff, null, Modifier.size(16.dp)) },
                            label = { Text(stringResource(if (isMuted) R.string.info_muted else R.string.info_mute)) }
                        )
                        FilterChip(
                            selected = isArchived,
                            onClick = onToggleArchive,
                            leadingIcon = { Icon(Icons.Filled.Archive, null, Modifier.size(16.dp)) },
                            label = { Text(stringResource(if (isArchived) R.string.info_archived else R.string.info_archive)) }
                        )
                        FilterChip(
                            selected = privateSetting?.hidden == true,
                            onClick = onToggleHidden,
                            leadingIcon = { Icon(Icons.Filled.VisibilityOff, null, Modifier.size(16.dp)) },
                            label = { Text(stringResource(if (privateSetting?.hidden == true) R.string.info_hidden else R.string.info_hide_chat)) }
                        )
                        FilterChip(
                            selected = privateSetting?.locked == true,
                            onClick = onToggleLocked,
                            leadingIcon = { Icon(Icons.Filled.Lock, null, Modifier.size(16.dp)) },
                            label = { Text(stringResource(if (privateSetting?.locked == true) R.string.info_locked else R.string.info_lock_chat)) }
                        )
                        if (peerProfile != null) {
                            FilterChip(
                                selected = peerContact?.favorite == true,
                                onClick = { onToggleFavorite(peerProfile, peerContact) },
                                leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.info_favorite)) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DlSecondaryButton(
                            text = stringResource(R.string.info_export_json),
                            onClick = { onExportChat("JSON") },
                            icon = Icons.Filled.Download,
                            modifier = Modifier.weight(1f)
                        )
                        DlSecondaryButton(
                            text = stringResource(R.string.info_export_txt),
                            onClick = { onExportChat("TXT") },
                            icon = Icons.Filled.Download,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Per-chat Accent Theme Swatches
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = tokens.surfaceElevated,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Palette, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.info_accent_title), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AccentColorOptions.forEach { (name, hex) ->
                                val swatchColor = resolveAccessibleAccent(hex, tokens.isDark)
                                val selected = chat.themeAccentHex.equals(hex, ignoreCase = true)
                                val accentCd = stringResource(R.string.info_accent_cd, name)
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) tokens.textPrimary else tokens.hairline,
                                            shape = CircleShape
                                        )
                                        .clickable { onUpdateChatTheme(hex) }
                                        .semantics { contentDescription = accentCd },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = contrastInkFor(swatchColor),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        DlHairlineDivider()

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Timer, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.info_disappearing_title), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                0L to stringResource(R.string.common_off),
                                3600L to "1h",
                                86400L to "24h",
                                604800L to "7d"
                            ).forEach { (secs, label) ->
                                FilterChip(
                                    selected = chat.disappearingSeconds == secs,
                                    onClick = { onSetDisappearing(secs) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // Shared Media & Documents Gallery
            if (mediaMessages.isNotEmpty() || documentMessages.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = mediaTab == "MEDIA",
                                onClick = { mediaTab = "MEDIA" },
                                label = { Text(stringResource(R.string.info_photos_tab, mediaMessages.size)) }
                            )
                            FilterChip(
                                selected = mediaTab == "DOCS",
                                onClick = { mediaTab = "DOCS" },
                                label = { Text(stringResource(R.string.info_files_tab, documentMessages.size)) }
                            )
                        }
                        if (mediaTab == "MEDIA" && mediaMessages.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(mediaMessages, key = { it.id }) { msg ->
                                    val bmp = remember(msg.mediaDataBase64) {
                                        ImageUtils.decodeBase64Bitmap(msg.mediaDataBase64)
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = msg.text,
                                            modifier = Modifier
                                                .size(88.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.dp, tokens.hairline, RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        } else if (mediaTab == "DOCS" && documentMessages.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                documentMessages.take(8).forEach { doc ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = tokens.surfaceElevated,
                                        border = BorderStroke(1.dp, tokens.hairline),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(doc.fileName ?: doc.type, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                                                val sizeLabel = doc.fileSizeBytes?.let { LocalStorageManager.formatByteCount(it) } ?: ""
                                                Text("${doc.type} $sizeLabel", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Group / Channel Forum Topics, Events, Invite Link & QR
            if (chatType != ChatType.DIRECT) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DlSecondaryButton(
                            text = stringResource(R.string.info_topics_btn, topics.size),
                            onClick = { onDismiss(); onOpenTopics() },
                            icon = Icons.Filled.Forum,
                            modifier = Modifier.weight(1f)
                        )
                        DlSecondaryButton(
                            text = stringResource(R.string.info_events_btn, events.size),
                            onClick = { onDismiss(); onOpenEvents() },
                            icon = Icons.Filled.Event,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (chatType == ChatType.CHANNEL) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.info_channel_stats, chat.channelViewsCount, chat.memberUids.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (isAdmin && allGroupChats.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.info_linked_group),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textSecondary
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                allGroupChats.take(6).forEach { grp ->
                                    FilterChip(
                                        selected = chat.linkedGroupId == grp.id,
                                        onClick = { onLinkDiscussionGroup(grp.id) },
                                        label = { Text(grp.title) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = tokens.surfaceElevated,
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.info_invite_title), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                                }
                                DlStatusPill(
                                    text = stringResource(if (chat.inviteRevoked) R.string.common_revoked else R.string.common_active),
                                    color = if (chat.inviteRevoked) tokens.statusBusy else tokens.statusOnline
                                )
                            }
                            val linkUri = QrCodeUtils.buildInviteUri(chat.inviteCode ?: chat.id.take(8), chat.id)
                            Text(
                                text = if (chat.inviteRevoked) stringResource(R.string.info_invite_revoked) else linkUri,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!chat.inviteRevoked && (isAdmin || chat.allowMemberInvites)) {
                                    DlSecondaryButton(
                                        text = stringResource(R.string.common_copy),
                                        onClick = { clipboard.setText(AnnotatedString(linkUri)) },
                                        icon = Icons.Filled.ContentCopy
                                    )
                                    DlSecondaryButton(
                                        text = stringResource(R.string.info_invite_qr_btn),
                                        onClick = { showInviteQrDialog = true },
                                        icon = Icons.Filled.QrCode2
                                    )
                                }
                                if (isAdmin) {
                                    DlSecondaryButton(
                                        text = stringResource(R.string.info_invite_rotate_btn),
                                        onClick = onRegenerateInvite,
                                        icon = Icons.Filled.Refresh
                                    )
                                    TextButton(onClick = { onRevokeInvite(!chat.inviteRevoked) }) {
                                        Text(stringResource(if (chat.inviteRevoked) R.string.info_invite_enable else R.string.info_invite_revoke))
                                    }
                                }
                            }
                        }
                    }
                }

                if (isAdmin && chat.joinRequestUids.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.info_join_requests, chat.joinRequestUids.size),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(chat.joinRequestUids, key = { "jr_$it" }) { applicantUid ->
                        val applicant = allUsers.firstOrNull { it.userId == applicantUid }
                            ?: UserProfile(userId = applicantUid, displayName = applicantUid.take(8))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DlAvatar(name = applicant.displayName, size = 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(applicant.displayName, modifier = Modifier.weight(1f), color = tokens.textPrimary)
                            IconButton(onClick = { onApproveJoinRequest(applicant, true) }) {
                                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.common_approve), tint = DlEmerald)
                            }
                            IconButton(onClick = { onApproveJoinRequest(applicant, false) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_decline), tint = DlCrimson)
                            }
                        }
                    }
                }

                item {
                    DlHairlineDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.info_members_title, chat.memberUids.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary
                        )
                        if (isAdmin) {
                            TextButton(onClick = { showAddMember = true }) {
                                Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.common_add))
                            }
                        }
                    }
                    if (isAdmin) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = tokens.surfaceElevated,
                            border = BorderStroke(1.dp, tokens.hairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.info_admin_only), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = chat.adminOnlyPost, onCheckedChange = onToggleAdminOnly)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.info_allow_pin), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = chat.allowPinning,
                                        onCheckedChange = { onUpdatePermissions(it, chat.allowMemberInvites) }
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.info_allow_invites), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = chat.allowMemberInvites,
                                        onCheckedChange = { onUpdatePermissions(chat.allowPinning, it) }
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.info_anti_spam), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = chat.antiSpamEnabled, onCheckedChange = onToggleAntiSpam)
                                }
                                Text(stringResource(R.string.info_slow_mode), style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        0 to stringResource(R.string.common_off),
                                        10 to "10s",
                                        30 to "30s",
                                        60 to stringResource(R.string.settings_autolock_1m)
                                    ).forEach { (sec, label) ->
                                        FilterChip(
                                            selected = chat.slowModeSeconds == sec,
                                            onClick = { onSetSlowMode(sec) },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(chat.memberUids, key = { it }) { uid ->
                    val name = chat.participantNames[uid]
                        ?: allUsers.firstOrNull { it.userId == uid }?.displayName
                        ?: uid.take(8)
                    val memberIsAdmin = uid in chat.adminUids
                    val isRestricted = uid in chat.restrictedUids
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DlAvatar(name = name, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (memberIsAdmin) DlStatusPill(stringResource(R.string.info_pill_admin), color = MaterialTheme.colorScheme.primary)
                                if (isRestricted) DlStatusPill(stringResource(R.string.info_pill_muted), color = tokens.statusBusy)
                            }
                        }
                        if (isAdmin && uid != myUid && uid != chat.ownerId) {
                            TextButton(onClick = { onToggleRestrictedUid(uid) }) {
                                Text(stringResource(if (isRestricted) R.string.info_unmute else R.string.info_mute))
                            }
                            if (!memberIsAdmin) {
                                TextButton(onClick = { onPromoteAdmin(uid) }) {
                                    Text(stringResource(R.string.info_promote))
                                }
                            }
                            TextButton(onClick = { onRemoveMember(uid) }) {
                                Text(stringResource(R.string.common_remove), color = DlCrimson)
                            }
                        }
                    }
                }

                if (chat.adminActivityLog.isNotEmpty()) {
                    item {
                        DlHairlineDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.History, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.info_activity_log), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                        }
                        Spacer(Modifier.height(4.dp))
                        chat.adminActivityLog.takeLast(5).reversed().forEach { log ->
                            Text("• $log", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                        }
                    }
                }
            }

            // Block & Report
            item {
                DlHairlineDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (peerProfile != null) {
                        val isBlocked = peerContact?.blocked == true
                        TextButton(onClick = { onToggleBlock(peerProfile, peerContact) }) {
                            Icon(Icons.Filled.Block, null, tint = DlCrimson)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(if (isBlocked) R.string.info_unblock_user else R.string.info_block_user),
                                color = DlCrimson
                            )
                        }
                    }
                    TextButton(onClick = { showReportDialog = true }) {
                        Icon(Icons.Filled.Report, null, tint = DlCrimson)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.info_report_user), color = DlCrimson)
                    }
                }
            }
        }
    }

    if (showAddMember) {
        val candidates = allUsers.filter { it.userId !in chat.memberUids }
        AlertDialog(
            onDismissRequest = { showAddMember = false },
            containerColor = tokens.surfaceElevated,
            title = { Text(stringResource(R.string.info_add_member_title), color = tokens.textPrimary) },
            text = {
                if (candidates.isEmpty()) {
                    Text(stringResource(R.string.info_add_member_empty), color = tokens.textSecondary)
                } else {
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(candidates, key = { it.userId }) { u ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddMember(u)
                                        showAddMember = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DlAvatar(name = u.displayName, size = 34.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("${u.displayName} (@${u.username})", color = tokens.textPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddMember = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    if (showReportDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            containerColor = tokens.surfaceElevated,
            title = { Text(stringResource(R.string.info_report_title), color = tokens.textPrimary) },
            text = {
                DlTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = stringResource(R.string.info_report_label)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (reason.trim().length >= 3) {
                        onSubmitReport(peerUid ?: chat.id, chat.type, reason)
                        showReportDialog = false
                    }
                }) { Text(stringResource(R.string.common_submit)) }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showInviteQrDialog && !chat.inviteRevoked) {
        val qrUri = QrCodeUtils.buildInviteUri(chat.inviteCode ?: chat.id.take(8), chat.id)
        QrDisplayDialog(
            title = stringResource(R.string.info_invite_qr_title, chat.title),
            qrContent = qrUri,
            subtitle = stringResource(R.string.info_invite_qr_sub),
            onDismiss = { showInviteQrDialog = false }
        )
    }
}
