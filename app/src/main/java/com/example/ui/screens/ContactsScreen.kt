package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.CryptoIdentityHelper
import com.example.data.model.ContactEntry
import com.example.data.model.ContactRequest
import com.example.data.model.OnlineStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlEmptyState
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlLoadingState
import com.example.ui.components.DlSearchField
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTopBar
import com.example.ui.components.localizedLabel
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@Composable
fun ContactsScreen(
    myUid: String,
    myProfile: UserProfile?,
    usersState: UiState<List<UserProfile>>,
    contacts: List<ContactEntry>,
    contactRequests: List<ContactRequest>,
    onOpenDirectChat: (UserProfile) -> Unit,
    onSendContactRequest: (UserProfile) -> Unit,
    onRespondContactRequest: (ContactRequest, Boolean) -> Unit,
    onToggleFavorite: (UserProfile, ContactEntry?) -> Unit,
    onToggleBlock: (UserProfile, ContactEntry?) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    restrictionNotice: String? = null,
    onOpenAccountStatus: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    var query by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf("DIRECTORY") }
    var inspectingUser by remember { mutableStateOf<UserProfile?>(null) }
    var showTextLkDialog by remember { mutableStateOf(false) }
    var textLkInitialFirst by remember { mutableStateOf("") }
    var textLkInitialLast by remember { mutableStateOf("") }

    val allUsers = (usersState as? UiState.Success)?.data.orEmpty().filter { it.userId != myUid }
    val contactByUid = remember(contacts) { contacts.associateBy { it.contactUid } }
    val verifiedUsersCount = remember(allUsers) {
        allUsers.count {
            it.legionVerificationStatus.equals("verified", ignoreCase = true) ||
                !it.legionMemberNumber.isNullOrBlank()
        }
    }
    val pendingIncoming = remember(contactRequests, myUid) {
        contactRequests.filter { it.receiverId == myUid && it.status == "PENDING" }
    }
    val pendingOutgoingUids = remember(contactRequests, myUid) {
        contactRequests.filter { it.senderId == myUid && it.status == "PENDING" }.map { it.receiverId }.toSet()
    }

    val filteredUsers = remember(allUsers, contacts, query, filterTab) {
        val q = query.trim().lowercase()
        allUsers.filter { user ->
            val entry = contactByUid[user.userId]
            val isLegionVerified = user.legionVerificationStatus.equals("verified", ignoreCase = true) ||
                !user.legionMemberNumber.isNullOrBlank()
            val matchesFilter = when (filterTab) {
                "CONTACTS" -> entry != null && !entry.blocked
                "VERIFIED" -> isLegionVerified && entry?.blocked != true
                "FAVORITES" -> entry?.favorite == true && !entry.blocked
                "BLOCKED" -> entry?.blocked == true
                else -> entry?.blocked != true
            }
            val matchesSearch = q.isEmpty() ||
                user.displayName.lowercase().contains(q) ||
                user.username.lowercase().contains(q)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DlTopBar(
            title = stringResource(R.string.contacts_title),
            subtitle = stringResource(R.string.contacts_subtitle, allUsers.size, pendingIncoming.size),
            actions = {
                IconButton(
                    onClick = {
                        textLkInitialFirst = ""
                        textLkInitialLast = ""
                        showTextLkDialog = true
                    },
                    modifier = Modifier.testTag("open_textlk_contacts_button")
                ) {
                    Icon(Icons.Filled.Sms, contentDescription = stringResource(R.string.textlk_contacts_cd))
                }
                IconButton(
                    onClick = {
                        val handle = myProfile?.username ?: "member"
                        val keyFingerprint = myProfile?.publicKeyFingerprint ?: ""
                        val shareText = context.getString(R.string.contacts_share_text, handle, keyFingerprint)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.contacts_share_chooser)))
                    },
                    modifier = Modifier.testTag("share_profile_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.contacts_share_cd))
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

        Spacer(Modifier.height(DlSpacing.md))

        DlSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.contacts_search_placeholder),
            modifier = Modifier
                .padding(horizontal = DlSpacing.base)
                .testTag("contacts_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = DlSpacing.base, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "DIRECTORY" to stringResource(R.string.contacts_filter_directory),
                "CONTACTS" to stringResource(R.string.contacts_filter_saved, contacts.count { !it.blocked }),
                "VERIFIED" to stringResource(R.string.contacts_filter_verified, verifiedUsersCount),
                "FAVORITES" to stringResource(R.string.contacts_filter_favorites),
                "REQUESTS" to stringResource(R.string.contacts_filter_requests, pendingIncoming.size),
                "BLOCKED" to stringResource(R.string.contacts_filter_blocked)
            ).forEach { (key, label) ->
                FilterChip(
                    selected = filterTab == key,
                    onClick = { filterTab = key },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    label = { Text(label) },
                    modifier = Modifier.testTag("contacts_filter_${key.lowercase()}")
                )
            }
        }

        DlHairlineDivider()

        when {
            filterTab == "REQUESTS" -> {
                if (contactRequests.isEmpty()) {
                    DlEmptyState(
                        icon = Icons.Filled.PersonAdd,
                        title = stringResource(R.string.contacts_requests_empty_title),
                        subtitle = stringResource(R.string.contacts_requests_empty_subtitle)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(contactRequests, key = { it.id }) { req ->
                            val isIncoming = req.receiverId == myUid
                            val localizedStatus = when (req.status.uppercase()) {
                                "ACCEPTED" -> stringResource(R.string.common_accept)
                                "DECLINED" -> stringResource(R.string.common_decline)
                                else -> stringResource(R.string.common_pending)
                            }
                            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = DlSpacing.base, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DlAvatar(
                                        name = if (isIncoming) req.senderName else req.receiverName,
                                        size = 44.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (isIncoming) req.senderName else req.receiverName,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = if (isIncoming) {
                                                stringResource(R.string.contacts_incoming, req.senderUsername)
                                            } else {
                                                stringResource(R.string.contacts_outgoing, localizedStatus)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.textSecondary
                                        )
                                    }
                                    if (isIncoming && req.status == "PENDING") {
                                        IconButton(onClick = { onRespondContactRequest(req, true) }) {
                                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.contacts_accept_cd), tint = tokens.statusOnline)
                                        }
                                        IconButton(onClick = { onRespondContactRequest(req, false) }) {
                                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.contacts_decline_cd), tint = tokens.statusBusy)
                                        }
                                    } else {
                                        DlStatusPill(text = localizedStatus)
                                    }
                                }
                                DlHairlineDivider(modifier = Modifier.padding(start = 72.dp))
                            }
                        }
                    }
                }
            }
            usersState is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DlLoadingState(message = stringResource(R.string.contacts_loading))
                }
            }
            filteredUsers.isEmpty() -> {
                DlEmptyState(
                    icon = Icons.Filled.Contacts,
                    title = stringResource(R.string.contacts_empty_title),
                    subtitle = stringResource(R.string.contacts_empty_subtitle)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filteredUsers, key = { it.userId }) { user ->
                        val entry = contactByUid[user.userId]
                        val requested = user.userId in pendingOutgoingUids
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { inspectingUser = user }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = DlSpacing.base, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DlAvatar(
                                    name = user.displayName,
                                    colorHex = user.avatarColor,
                                    photoBase64 = user.photoDataBase64,
                                    onlineStatus = if (user.showOnline) user.onlineStatus else null,
                                    size = 46.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                val isLegionVerified = user.legionVerificationStatus.equals("verified", ignoreCase = true) ||
                                    !user.legionMemberNumber.isNullOrBlank()
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            user.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = tokens.textPrimary
                                        )
                                        if (isLegionVerified) {
                                            Spacer(Modifier.width(5.dp))
                                            Icon(
                                                imageVector = Icons.Filled.Verified,
                                                contentDescription = stringResource(R.string.contacts_legion_badge),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "@${user.username} · ${OnlineStatus.fromRaw(user.onlineStatus).localizedLabel()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary
                                    )
                                }
                                IconButton(onClick = { onToggleFavorite(user, entry) }) {
                                    Icon(
                                        if (entry?.favorite == true) Icons.Filled.Star else Icons.Filled.StarOutline,
                                        contentDescription = stringResource(R.string.contacts_fav_cd),
                                        tint = if (entry?.favorite == true) MaterialTheme.colorScheme.primary
                                        else tokens.textTertiary
                                    )
                                }
                                if (entry == null && !requested) {
                                    IconButton(onClick = { onSendContactRequest(user) }) {
                                        Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.contacts_add_cd))
                                    }
                                }
                                IconButton(
                                    onClick = { onOpenDirectChat(user) },
                                    modifier = Modifier.testTag("message_contact_${user.userId}")
                                ) {
                                    Icon(Icons.Filled.Chat, contentDescription = stringResource(R.string.contacts_msg_cd), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DlHairlineDivider(modifier = Modifier.padding(start = 74.dp))
                        }
                    }
                }
            }
        }
    }

    if (inspectingUser != null) {
        val target = inspectingUser!!
        val entry = contactByUid[target.userId]
        val safetyNum = if (myProfile != null) {
            CryptoIdentityHelper.computeSharedSafetyNumber(myProfile.publicKeyFingerprint, target.publicKeyFingerprint)
        } else target.publicKeyFingerprint

        AlertDialog(
            onDismissRequest = { inspectingUser = null },
            title = { Text(target.displayName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DlAvatar(
                            name = target.displayName,
                            colorHex = target.avatarColor,
                            photoBase64 = target.photoDataBase64,
                            size = 52.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("@${target.username}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.contacts_key_version, target.keyVersion),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textSecondary
                            )
                        }
                    }
                    if (target.legionVerificationStatus.equals("verified", ignoreCase = true) ||
                        !target.legionMemberNumber.isNullOrBlank()
                    ) {
                        DlStatusPill(
                            text = stringResource(
                                R.string.contacts_legion_member,
                                target.legionMemberNumber.orEmpty().ifBlank { "DL" },
                                target.legionPrimaryUnit.orEmpty().ifBlank { stringResource(R.string.contacts_legion_badge) }
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (target.bio.isNotBlank()) {
                        Text(target.bio, style = MaterialTheme.typography.bodyMedium)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.surfaceElevated,
                        border = BorderStroke(1.dp, tokens.hairline)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.QrCode2, null, modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(stringResource(R.string.contacts_safety_label), style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                                Text(safetyNum, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onToggleBlock(target, entry)
                                inspectingUser = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Block, null, Modifier.size(16.dp), tint = tokens.statusBusy)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(if (entry?.blocked == true) R.string.contacts_unblock else R.string.contacts_block),
                                color = tokens.statusBusy
                            )
                        }
                        Button(
                            onClick = {
                                onOpenDirectChat(target)
                                inspectingUser = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.contacts_message_cta))
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val parts = target.displayName.trim().split(Regex("\\s+"), limit = 2)
                        textLkInitialFirst = parts.getOrElse(0) { "" }
                        textLkInitialLast = parts.getOrElse(1) { "" }
                        inspectingUser = null
                        showTextLkDialog = true
                    }) {
                        Text(stringResource(R.string.textlk_contacts_cd))
                    }
                    TextButton(onClick = { inspectingUser = null }) { Text(stringResource(R.string.common_close)) }
                }
            }
        )
    }

    if (showTextLkDialog) {
        TextLkContactsDialog(
            initialFirstName = textLkInitialFirst,
            initialLastName = textLkInitialLast,
            onDismiss = { showTextLkDialog = false }
        )
    }
}
