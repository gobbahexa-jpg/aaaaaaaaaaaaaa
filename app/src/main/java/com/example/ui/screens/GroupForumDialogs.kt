package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.ChatTopic
import com.example.data.model.GroupEvent
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.theme.LocalDlTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GroupTopicsDialog(
    chat: ChatRoom,
    topics: List<ChatTopic>,
    selectedTopicId: String?,
    onSelectTopic: (String?) -> Unit,
    onCreateTopic: (title: String, emoji: String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalDlTokens.current
    var newTitle by remember { mutableStateOf("") }
    var newEmoji by remember { mutableStateOf("💬") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Forum, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.topics_dialog_title, chat.title), color = tokens.textPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("💬", "🚀", "🛡️", "🎮", "📢").forEach { e ->
                        FilterChip(selected = newEmoji == e, onClick = { newEmoji = e }, label = { Text(e) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(stringResource(R.string.topics_new_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(
                        enabled = newTitle.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            onCreateTopic(newTitle.trim(), newEmoji)
                            newTitle = ""
                        }
                    ) { Text(stringResource(R.string.common_add)) }
                }
                FilterChip(
                    selected = selectedTopicId == null,
                    onClick = { onSelectTopic(null); onDismiss() },
                    label = { Text(stringResource(R.string.topics_all_stream)) }
                )
                LazyColumn(modifier = Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(topics, key = { it.id }) { topic ->
                        val active = selectedTopicId == topic.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else tokens.surfaceCard,
                            border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else tokens.hairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectTopic(topic.id)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(topic.iconEmoji)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    topic.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

@Composable
fun GroupEventsDialog(
    myUid: String,
    chat: ChatRoom,
    events: List<GroupEvent>,
    onCreateEvent: (title: String, description: String, hoursFromNow: Long) -> Unit,
    onToggleRsvp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalDlTokens.current
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var hours by remember { mutableLongStateOf(24L) }
    val fmt = remember { SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Event, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.events_dialog_title, chat.title), color = tokens.textPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DlTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.events_title_label)
                )
                DlTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = stringResource(R.string.events_details_label),
                    singleLine = false,
                    maxLines = 2
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(2L to "+2h", 24L to "+24h", 72L to "+3d").forEach { (h, label) ->
                        FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text(label) })
                    }
                    Button(
                        enabled = title.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            onCreateEvent(title.trim(), desc.trim(), hours)
                            title = ""
                            desc = ""
                        }
                    ) { Text(stringResource(R.string.events_schedule_cta)) }
                }

                LazyColumn(modifier = Modifier.height(190.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(events, key = { it.id }) { ev ->
                        val going = myUid in ev.rsvpUids
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.surfaceCard,
                            border = BorderStroke(1.dp, if (going) tokens.statusOnline.copy(alpha = 0.45f) else tokens.hairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ev.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                                    Text(
                                        stringResource(
                                            R.string.events_going_count,
                                            fmt.format(Date(ev.startsAtEpochMs)),
                                            ev.rsvpUids.size
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (ev.description.isNotBlank()) {
                                        Text(ev.description, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                    }
                                }
                                Button(
                                    onClick = { onToggleRsvp(ev.id) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(stringResource(if (going) R.string.events_going_btn else R.string.events_rsvp_btn))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

@Composable
fun CreateQuizDialog(
    onDismiss: () -> Unit,
    onCreateQuiz: (question: String, options: List<String>, correctIdx: Int, explanation: String) -> Unit
) {
    val tokens = LocalDlTokens.current
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "", "") }
    var correctIdx by remember { mutableIntStateOf(0) }
    var explanation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Quiz, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.quiz_dialog_title), color = tokens.textPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DlTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = stringResource(R.string.quiz_question_label),
                    singleLine = false,
                    maxLines = 2
                )
                options.forEachIndexed { idx, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = correctIdx == idx, onClick = { correctIdx = idx })
                        OutlinedTextField(
                            value = value,
                            onValueChange = { options[idx] = it },
                            label = {
                                val optBase = stringResource(R.string.quiz_option_label, idx + 1)
                                val suffix = if (correctIdx == idx) stringResource(R.string.quiz_option_correct_suffix) else ""
                                Text("$optBase$suffix")
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                DlTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = stringResource(R.string.quiz_explanation_label)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val valid = options.map { it.trim() }.filter { it.isNotEmpty() }
                if (question.isNotBlank() && valid.size >= 2) {
                    onCreateQuiz(question.trim(), valid, correctIdx.coerceIn(0, valid.lastIndex), explanation.trim())
                    onDismiss()
                }
            }) { Text(stringResource(R.string.quiz_post_cta)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
fun EditHistoryDialog(
    message: ChatMessage,
    onDismiss: () -> Unit
) {
    val tokens = LocalDlTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.edit_history_title), color = tokens.textPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.edit_history_current),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DlStatusPill(stringResource(R.string.edit_history_latest), color = MaterialTheme.colorScheme.primary)
                }
                Text(message.text, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                DlHairlineDivider()
                if (message.editHistory.isNotEmpty()) {
                    Text(
                        stringResource(R.string.edit_history_previous),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textSecondary
                    )
                    message.editHistory.forEachIndexed { i, prev ->
                        Text("${i + 1}. $prev", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    }
                } else {
                    Text(
                        stringResource(R.string.edit_history_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}
