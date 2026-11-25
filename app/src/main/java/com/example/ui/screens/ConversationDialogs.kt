package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.MessageType
import com.example.data.model.ReportCategory
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlTextField
import com.example.ui.components.localizedTitle
import com.example.ui.theme.LocalDlTokens

@Composable
fun MessageActionsDialog(
    message: ChatMessage,
    isMine: Boolean,
    isE2eeChat: Boolean,
    allChats: List<ChatRoom>,
    myUid: String,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleteForEveryone: () -> Unit,
    onPin: () -> Unit,
    onStar: () -> Unit,
    onSave: () -> Unit,
    onForward: (ChatRoom) -> Unit,
    onTranslate: (String) -> Unit,
    onExplainAi: () -> Unit,
    onEnterMultiSelect: () -> Unit,
    onReport: (String) -> Unit,
    onViewEditHistory: () -> Unit = {},
    onSummarizeVoice: ((String) -> Unit)? = null,
    onSubmitCategorizedReport: ((ReportCategory, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    var subMode by remember { mutableStateOf("MENU") } // MENU, EDIT, FORWARD, TRANSLATE, REPORT, CUSTOM_EMOJI, VOICE_TRANSCRIPT
    var editText by remember { mutableStateOf(message.text) }
    var reportReason by remember { mutableStateOf("") }
    var reportCategory by remember { mutableStateOf(ReportCategory.SPAM) }
    var customEmoji by remember { mutableStateOf("") }
    var voiceTranscriptDraft by remember { mutableStateOf(message.aiTranscribedText.orEmpty()) }
    val starred = myUid in message.starredBy
    val msgType = MessageType.fromRaw(message.type)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = {
            Text(
                text = stringResource(
                    when (subMode) {
                        "EDIT" -> R.string.msg_act_edit
                        "FORWARD" -> R.string.msg_act_forward
                        "TRANSLATE" -> R.string.msg_act_translate
                        "REPORT" -> R.string.msg_act_report
                        "CUSTOM_EMOJI" -> R.string.msg_act_emoji
                        "VOICE_TRANSCRIPT" -> R.string.msg_act_voice_transcript
                        else -> R.string.msg_act_menu
                    }
                ),
                color = tokens.textPrimary
            )
        },
        text = {
            when (subMode) {
                "EDIT" -> {
                    DlTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        label = stringResource(R.string.msg_act_edit_label),
                        singleLine = false,
                        maxLines = 4
                    )
                }
                "VOICE_TRANSCRIPT" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.msg_act_voice_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                        DlTextField(
                            value = voiceTranscriptDraft,
                            onValueChange = { voiceTranscriptDraft = it },
                            label = stringResource(R.string.msg_act_voice_label),
                            singleLine = false,
                            minLines = 3,
                            maxLines = 6
                        )
                    }
                }
                "FORWARD" -> {
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(allChats, key = { it.id }) { target ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clickable {
                                        onForward(target)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Forward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    target.localizedTitle(myUid),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.textPrimary
                                )
                            }
                            DlHairlineDivider()
                        }
                    }
                }
                "TRANSLATE" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isE2eeChat) {
                            Text(
                                text = stringResource(R.string.msg_act_translate_e2ee_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            stringResource(R.string.msg_act_translate_choose),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                        listOf("English", "Spanish", "French", "German", "Japanese", "Arabic", "Hindi").forEach { lang ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onTranslate(lang)
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = tokens.surfaceCard,
                                border = BorderStroke(1.dp, tokens.hairline)
                            ) {
                                Text(
                                    lang,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.textPrimary
                                )
                            }
                        }
                    }
                }
                "CUSTOM_EMOJI" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DlTextField(
                            value = customEmoji,
                            onValueChange = { customEmoji = it.take(8) },
                            label = stringResource(R.string.msg_act_emoji_label)
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("🚀", "💎", "🛡️", "🎯", "👀", "👏").forEach { e ->
                                FilterChip(
                                    selected = customEmoji == e,
                                    onClick = { customEmoji = e },
                                    label = { Text(e) }
                                )
                            }
                        }
                    }
                }
                "REPORT" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReportCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = reportCategory == cat,
                                    onClick = { reportCategory = cat },
                                    label = { Text(cat.label) }
                                )
                            }
                        }
                        DlTextField(
                            value = reportReason,
                            onValueChange = { reportReason = it },
                            label = stringResource(R.string.msg_act_report_label),
                            singleLine = false,
                            maxLines = 3
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.height(350.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf("🔥", "⚡", "❤️", "👍", "😂", "➕").forEach { emoji ->
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (emoji == "➕") subMode = "CUSTOM_EMOJI"
                                                else {
                                                    onReact(emoji)
                                                    onDismiss()
                                                }
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = tokens.surfaceCard,
                                        border = BorderStroke(1.dp, tokens.hairline)
                                    ) {
                                        Text(emoji, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                                    }
                                }
                            }
                            DlHairlineDivider(Modifier.padding(vertical = 4.dp))
                        }
                        item {
                            ActionMenuItem(Icons.Filled.Reply, stringResource(R.string.msg_act_reply)) { onReply(); onDismiss() }
                            ActionMenuItem(Icons.Filled.ContentCopy, stringResource(R.string.msg_act_copy)) { onCopy(); onDismiss() }
                            ActionMenuItem(
                                if (starred) Icons.Filled.Star else Icons.Filled.StarOutline,
                                stringResource(if (starred) R.string.msg_act_unstar else R.string.msg_act_star)
                            ) { onStar(); onDismiss() }
                            ActionMenuItem(Icons.Filled.PushPin, stringResource(R.string.msg_act_pin)) { onPin(); onDismiss() }
                            ActionMenuItem(Icons.Filled.Bookmark, stringResource(R.string.msg_act_save)) { onSave(); onDismiss() }
                            ActionMenuItem(Icons.Filled.Forward, stringResource(R.string.msg_act_forward_item)) { subMode = "FORWARD" }
                            ActionMenuItem(Icons.Filled.Checklist, stringResource(R.string.msg_act_select_multi)) {
                                onEnterMultiSelect()
                                onDismiss()
                            }
                            DlHairlineDivider(Modifier.padding(vertical = 4.dp))
                            ActionMenuItem(
                                Icons.Filled.Translate,
                                stringResource(R.string.msg_act_translate_item),
                                tint = MaterialTheme.colorScheme.primary
                            ) { subMode = "TRANSLATE" }
                            ActionMenuItem(
                                Icons.Filled.AutoAwesome,
                                stringResource(R.string.msg_act_explain_item),
                                tint = MaterialTheme.colorScheme.primary
                            ) {
                                onExplainAi()
                                onDismiss()
                            }
                            if (msgType == MessageType.VOICE_NOTE && onSummarizeVoice != null) {
                                ActionMenuItem(
                                    Icons.Filled.Mic,
                                    stringResource(R.string.msg_act_summarize_voice),
                                    tint = MaterialTheme.colorScheme.primary
                                ) {
                                    subMode = "VOICE_TRANSCRIPT"
                                }
                            }
                            if (message.edited || message.editHistory.isNotEmpty()) {
                                ActionMenuItem(
                                    Icons.Filled.History,
                                    stringResource(R.string.msg_act_edit_history, message.editHistory.size)
                                ) {
                                    onViewEditHistory()
                                    onDismiss()
                                }
                            }
                            if (isMine && !message.deletedForEveryone) {
                                DlHairlineDivider(Modifier.padding(vertical = 4.dp))
                                ActionMenuItem(Icons.Filled.Edit, stringResource(R.string.msg_act_edit_item)) { subMode = "EDIT" }
                                ActionMenuItem(
                                    Icons.Filled.Delete,
                                    stringResource(R.string.msg_act_delete_everyone),
                                    tint = MaterialTheme.colorScheme.error
                                ) {
                                    onDeleteForEveryone()
                                    onDismiss()
                                }
                            }
                            if (!isMine) {
                                DlHairlineDivider(Modifier.padding(vertical = 4.dp))
                                ActionMenuItem(
                                    Icons.Filled.Report,
                                    stringResource(R.string.msg_act_report_item),
                                    tint = MaterialTheme.colorScheme.error
                                ) { subMode = "REPORT" }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (subMode) {
                "EDIT" -> TextButton(onClick = {
                    if (editText.isNotBlank()) {
                        onEdit(editText)
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.common_save)) }
                "VOICE_TRANSCRIPT" -> TextButton(onClick = {
                    if (voiceTranscriptDraft.isNotBlank()) {
                        onSummarizeVoice?.invoke(voiceTranscriptDraft.trim())
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.msg_act_summarize_btn)) }
                "CUSTOM_EMOJI" -> TextButton(onClick = {
                    if (customEmoji.isNotBlank()) {
                        onReact(customEmoji.trim())
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.msg_act_react_btn)) }
                "REPORT" -> TextButton(onClick = {
                    if (reportReason.trim().length >= 3) {
                        val clean = reportReason.trim()
                        if (onSubmitCategorizedReport != null) {
                            onSubmitCategorizedReport(reportCategory, clean)
                        } else {
                            onReport(clean)
                        }
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.common_submit)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color = LocalDlTokens.current.textPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
fun CreatePollDialog(
    onDismiss: () -> Unit,
    onCreatePoll: (question: String, options: List<String>) -> Unit
) {
    val tokens = LocalDlTokens.current
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = { Text(stringResource(R.string.poll_dialog_title), color = tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DlTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = stringResource(R.string.poll_question_label),
                    singleLine = false,
                    maxLines = 2
                )
                options.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { options[index] = it },
                        label = { Text(stringResource(R.string.poll_option_label, index + 1)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (options.size < 5) {
                    TextButton(onClick = { options.add("") }) {
                        Text(stringResource(R.string.poll_add_option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val validOpts = options.map { it.trim() }.filter { it.isNotEmpty() }
                if (question.isNotBlank() && validOpts.size >= 2) {
                    onCreatePoll(question.trim(), validOpts)
                    onDismiss()
                }
            }) { Text(stringResource(R.string.poll_post_cta)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
fun CodeSnippetDialog(
    onDismiss: () -> Unit,
    onSendCode: (String) -> Unit
) {
    val tokens = LocalDlTokens.current
    var codeText by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("kotlin") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = { Text(stringResource(R.string.code_dialog_title), color = tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("kotlin", "ts", "sql", "bash").forEach { lang ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { language = lang },
                            label = { Text(lang) }
                        )
                    }
                }
                DlTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    label = stringResource(R.string.code_paste_label),
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (codeText.isNotBlank()) {
                    onSendCode("[$language]\n${codeText.trim()}")
                    onDismiss()
                }
            }) { Text(stringResource(R.string.common_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
