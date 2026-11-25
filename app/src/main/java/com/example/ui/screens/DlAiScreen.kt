package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AiAssistantMessage
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTopBar
import com.example.ui.components.RichMessageText
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@Composable
fun DlAiScreen(
    messages: List<AiAssistantMessage>,
    busy: Boolean,
    aiEnabled: Boolean = true,
    onAsk: (String) -> Unit,
    onClearChat: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var prompt by remember { mutableStateOf("") }
    var copiedMessageId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("ai_assistant_screen")
    ) {
        val starterPrompts = listOf(
            stringResource(R.string.ai_prompt_chip_1_label) to stringResource(R.string.ai_prompt_chip_1_full),
            stringResource(R.string.ai_prompt_chip_2_label) to stringResource(R.string.ai_prompt_chip_2_full),
            stringResource(R.string.ai_prompt_chip_3_label) to stringResource(R.string.ai_prompt_chip_3_full),
            stringResource(R.string.ai_prompt_chip_4_label) to stringResource(R.string.ai_prompt_chip_4_full)
        )

        DlTopBar(
            title = stringResource(R.string.ai_title),
            subtitle = stringResource(R.string.ai_subtitle),
            leadingAvatar = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            actions = {
                if (onClearChat != null && messages.size > 1) {
                    IconButton(onClick = onClearChat) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.ai_clear_cd))
                    }
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

        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = tokens.surfaceElevated
            )
        }

        // Privacy guardrail bar
        Surface(
            color = tokens.surfaceSubtle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DlSpacing.base, vertical = DlSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = tokens.statusOnline,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(
                        if (aiEnabled) R.string.ai_privacy_ready else R.string.ai_privacy_paused
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                DlStatusPill(
                    text = stringResource(if (aiEnabled) R.string.ai_pill_ready else R.string.ai_pill_paused),
                    color = if (aiEnabled) tokens.statusOnline else tokens.statusAway
                )
            }
        }
        DlHairlineDivider()

        // Starter prompt chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = DlSpacing.base, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            starterPrompts.forEach { (shortLabel, fullPrompt) ->
                FilterChip(
                    selected = false,
                    enabled = aiEnabled && !busy,
                    onClick = { onAsk(fullPrompt) },
                    label = { Text(shortLabel) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = DlSpacing.base, vertical = DlSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { item ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (item.fromUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 330.dp),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (item.fromUser) 16.dp else 4.dp,
                            bottomEnd = if (item.fromUser) 4.dp else 16.dp
                        ),
                        color = if (item.fromUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            tokens.surfaceElevated
                        },
                        border = BorderStroke(
                            1.dp,
                            if (item.fromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else tokens.hairline
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (!item.fromUser) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (item.streaming) R.string.ai_streaming_label else R.string.ai_title
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!item.streaming && item.text.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(item.text))
                                                copiedMessageId = item.id
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.ContentCopy,
                                                contentDescription = stringResource(R.string.ai_copy_cd),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(
                                                    if (copiedMessageId == item.id) R.string.common_copied else R.string.common_copy
                                                ),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            RichMessageText(
                                text = item.text.ifBlank { if (item.streaming) stringResource(R.string.ai_thinking) else "" },
                                baseColor = tokens.textPrimary,
                                accentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        DlHairlineDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .imePadding()
                .padding(horizontal = DlSpacing.base, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                enabled = aiEnabled,
                placeholder = {
                    Text(
                        stringResource(
                            if (aiEnabled) R.string.ai_input_placeholder else R.string.ai_input_paused_placeholder
                        )
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = tokens.surfaceElevated,
                    unfocusedContainerColor = tokens.surfaceElevated,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = tokens.hairline
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_prompt_input")
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (aiEnabled && prompt.isNotBlank() && !busy) {
                    MaterialTheme.colorScheme.primary
                } else {
                    tokens.surfaceElevated
                }
            ) {
                IconButton(
                    onClick = {
                        if (aiEnabled && prompt.isNotBlank() && !busy) {
                            onAsk(prompt)
                            prompt = ""
                        }
                    },
                    enabled = aiEnabled && prompt.isNotBlank() && !busy,
                    modifier = Modifier.testTag("ai_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.ai_send_cd),
                        tint = if (aiEnabled && prompt.isNotBlank() && !busy) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            tokens.textTertiary
                        }
                    )
                }
            }
        }
    }
}
