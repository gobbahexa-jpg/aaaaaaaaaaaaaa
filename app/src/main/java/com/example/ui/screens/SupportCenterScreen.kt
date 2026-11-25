package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.HelpArticle
import com.example.data.model.PhoneAuthProfile
import com.example.data.model.SupportCategory
import com.example.data.model.SupportPriority
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketMessage
import com.example.data.model.SupportTicketStatus
import com.example.data.model.UserProfile
import com.example.ui.components.DlEmptyState
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlPrimaryButton
import com.example.ui.components.DlSearchField
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.components.DlTopBar
import com.example.ui.components.formatRelativeTimeLocalized
import com.example.ui.theme.DlCrimson
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@Composable
fun SupportCenterScreen(
    myUid: String,
    myProfile: UserProfile?,
    phoneProfile: PhoneAuthProfile?,
    helpArticles: List<HelpArticle>,
    tickets: List<SupportTicket>,
    activeTicketId: String?,
    activeMessages: List<SupportTicketMessage>,
    onSelectTicket: (String?) -> Unit,
    onCreateTicket: (
        category: SupportCategory,
        priority: SupportPriority,
        subject: String,
        description: String,
        whatsappOptIn: Boolean
    ) -> Unit,
    onSendTicketMessage: (ticket: SupportTicket, body: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    val activeTicket = tickets.firstOrNull { it.id == activeTicketId }

    BackHandler(enabled = activeTicket != null) {
        onSelectTicket(null)
    }
    BackHandler(enabled = activeTicket == null, onBack = onBack)

    var selectedTab by remember { mutableStateOf("HELP") } // HELP or TICKETS
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<SupportCategory?>(null) }
    var readingArticle by remember { mutableStateOf<HelpArticle?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    if (activeTicket != null) {
        SupportTicketChatView(
            myUid = myUid,
            ticket = activeTicket,
            messages = activeMessages,
            onBack = { onSelectTicket(null) },
            onSendMessage = { body -> onSendTicketMessage(activeTicket, body) }
        )
        return
    }

    val filteredArticles = remember(helpArticles, searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        helpArticles.filter { article ->
            val matchesCat = selectedCategory == null || article.category == selectedCategory?.name
            val matchesQuery = q.isEmpty() ||
                article.title.lowercase().contains(q) ||
                article.summary.lowercase().contains(q) ||
                article.keywords.any { it.lowercase().contains(q) }
            matchesCat && matchesQuery
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("support_center_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DlTopBar(
                title = stringResource(R.string.support_center_title),
                subtitle = stringResource(R.string.support_center_subtitle),
                onBack = onBack
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = DlSpacing.base, vertical = DlSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == "HELP",
                    onClick = { selectedTab = "HELP" },
                    label = { Text(stringResource(R.string.support_tab_help_center, helpArticles.size)) },
                    modifier = Modifier.testTag("support_tab_help")
                )
                FilterChip(
                    selected = selectedTab == "TICKETS",
                    onClick = { selectedTab = "TICKETS" },
                    label = { Text(stringResource(R.string.support_tab_my_tickets, tickets.size)) },
                    modifier = Modifier.testTag("support_tab_tickets")
                )
            }
            DlHairlineDivider()

            if (selectedTab == "HELP") {
                Spacer(Modifier.height(10.dp))
                DlSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(R.string.support_search_placeholder),
                    modifier = Modifier
                        .padding(horizontal = DlSpacing.base)
                        .testTag("support_search_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = DlSpacing.base, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(stringResource(R.string.common_all)) }
                    )
                    SupportCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label) },
                            modifier = Modifier.testTag("support_cat_${cat.name.lowercase()}")
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = DlSpacing.base,
                        end = DlSpacing.base,
                        top = 4.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (filteredArticles.isEmpty()) {
                        item {
                            DlEmptyState(
                                icon = Icons.AutoMirrored.Filled.HelpOutline,
                                title = stringResource(R.string.support_help_empty_title),
                                subtitle = stringResource(R.string.support_help_empty_sub),
                                actionLabel = stringResource(R.string.support_new_ticket_cta),
                                onAction = { showCreateDialog = true }
                            )
                        }
                    } else {
                        items(filteredArticles, key = { it.id }) { article ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = tokens.surfaceElevated,
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { readingArticle = article }
                                    .testTag("help_article_${article.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = SupportCategory.fromRaw(article.category).label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = article.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = tokens.textPrimary
                                    )
                                    Text(
                                        text = article.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = DlSpacing.base,
                        end = DlSpacing.base,
                        top = DlSpacing.base,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (tickets.isEmpty()) {
                        item {
                            DlEmptyState(
                                icon = Icons.Filled.SupportAgent,
                                title = stringResource(R.string.support_tickets_empty_title),
                                subtitle = stringResource(R.string.support_tickets_empty_sub),
                                actionLabel = stringResource(R.string.support_new_ticket_cta),
                                onAction = { showCreateDialog = true }
                            )
                        }
                    } else {
                        items(tickets, key = { it.id }) { ticket ->
                            val st = SupportTicketStatus.fromRaw(ticket.status)
                            val stColor = when (st) {
                                SupportTicketStatus.RESOLVED, SupportTicketStatus.CLOSED -> tokens.statusOnline
                                SupportTicketStatus.ESCALATED -> DlCrimson
                                SupportTicketStatus.WAITING_ON_USER -> tokens.statusAway
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = tokens.surfaceElevated,
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectTicket(ticket.id) }
                                    .testTag("ticket_card_${ticket.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ticket.subject,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tokens.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        DlStatusPill(text = st.label, color = stColor)
                                    }
                                    Text(
                                        text = "${SupportCategory.fromRaw(ticket.category).label} · ${SupportPriority.fromRaw(ticket.priority).label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = ticket.lastMessageText.ifBlank { ticket.description },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary,
                                        maxLines = 2
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (ticket.assignedAgentName.isNotBlank()) {
                                                stringResource(R.string.support_assigned_agent, ticket.assignedAgentName)
                                            } else {
                                                stringResource(R.string.support_awaiting_agent)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.textTertiary
                                        )
                                        Text(
                                            text = formatRelativeTimeLocalized(ticket.updatedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.textTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(DlSpacing.base)
                .testTag("new_support_ticket_button"),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.support_new_ticket_cta))
        }
    }

    val inspectingArticle = readingArticle
    if (inspectingArticle != null) {
        AlertDialog(
            onDismissRequest = { readingArticle = null },
            containerColor = tokens.surfaceElevated,
            title = { Text(inspectingArticle.title, color = tokens.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DlStatusPill(
                        text = SupportCategory.fromRaw(inspectingArticle.category).label,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = inspectingArticle.bodyMarkdown,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    readingArticle = null
                    showCreateDialog = true
                }) { Text(stringResource(R.string.support_need_more_help_cta)) }
            },
            dismissButton = {
                TextButton(onClick = { readingArticle = null }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    if (showCreateDialog) {
        var category by remember { mutableStateOf(selectedCategory ?: SupportCategory.ACCOUNT_PHONE) }
        var priority by remember { mutableStateOf(SupportPriority.NORMAL) }
        var subject by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var whatsappOptIn by remember { mutableStateOf(phoneProfile?.whatsappOptIn ?: false) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = tokens.surfaceElevated,
            title = { Text(stringResource(R.string.support_new_ticket_cta), color = tokens.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SupportCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SupportPriority.entries.forEach { prio ->
                            FilterChip(
                                selected = priority == prio,
                                onClick = { priority = prio },
                                label = { Text(prio.label) }
                            )
                        }
                    }
                    DlTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = stringResource(R.string.support_subject_label),
                        modifier = Modifier.testTag("ticket_subject_input")
                    )
                    DlTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = stringResource(R.string.support_description_label),
                        singleLine = false,
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.testTag("ticket_description_input")
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = whatsappOptIn, onCheckedChange = { whatsappOptIn = it })
                        Text(
                            text = stringResource(R.string.support_ticket_whatsapp_optin),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (subject.trim().length >= 3 && description.trim().length >= 5) {
                            onCreateTicket(category, priority, subject.trim(), description.trim(), whatsappOptIn)
                            showCreateDialog = false
                            selectedTab = "TICKETS"
                        }
                    },
                    modifier = Modifier.testTag("create_ticket_confirm_button")
                ) { Text(stringResource(R.string.common_submit)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun SupportTicketChatView(
    myUid: String,
    ticket: SupportTicket,
    messages: List<SupportTicketMessage>,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    val tokens = LocalDlTokens.current
    var replyDraft by remember(ticket.id) { mutableStateOf("") }
    val status = SupportTicketStatus.fromRaw(ticket.status)
    val isClosed = status == SupportTicketStatus.CLOSED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("support_ticket_chat_view")
    ) {
        DlTopBar(
            title = ticket.subject,
            subtitle = "${status.label} · #${ticket.id.take(6)}",
            onBack = onBack
        )
        DlHairlineDivider()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(DlSpacing.base),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isStaff = msg.senderRole.equals("SUPPORT_AGENT", ignoreCase = true)
                val isMine = msg.senderId == myUid && !isStaff
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isMine) MaterialTheme.colorScheme.primaryContainer else tokens.surfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (isStaff) MaterialTheme.colorScheme.primary else tokens.hairline
                        ),
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = msg.senderName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isStaff) MaterialTheme.colorScheme.primary else tokens.textPrimary
                                )
                                if (isStaff) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    DlStatusPill(
                                        text = stringResource(R.string.support_official_staff_badge),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = msg.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else tokens.textPrimary
                            )
                            Text(
                                text = formatRelativeTimeLocalized(msg.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textTertiary
                            )
                        }
                    }
                }
            }
        }

        DlHairlineDivider()
        if (isClosed) {
            Text(
                text = stringResource(R.string.support_ticket_closed_banner),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                modifier = Modifier.padding(DlSpacing.base)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = DlSpacing.base, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DlTextField(
                    value = replyDraft,
                    onValueChange = { replyDraft = it },
                    label = stringResource(R.string.support_reply_placeholder),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("support_chat_input")
                )
                IconButton(
                    onClick = {
                        if (replyDraft.isNotBlank()) {
                            onSendMessage(replyDraft.trim())
                            replyDraft = ""
                        }
                    },
                    modifier = Modifier.testTag("support_chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.conv_send_cd),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
