package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AiAssistantMessage
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.RichMessageText
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DlAiAssistantSheet(
    messages: List<AiAssistantMessage>,
    busy: Boolean,
    onAsk: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalDlTokens.current
    var prompt by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .imePadding()
                .padding(horizontal = DlSpacing.base)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.ai_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ai_sheet_close_cd))
                }
            }

            Spacer(Modifier.height(DlSpacing.sm))
            DlHairlineDivider()

            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    stringResource(R.string.ai_prompt_chip_1_label) to stringResource(R.string.ai_prompt_chip_1_full),
                    stringResource(R.string.ai_prompt_chip_2_label) to stringResource(R.string.ai_prompt_chip_2_full),
                    stringResource(R.string.ai_prompt_chip_3_label) to stringResource(R.string.ai_prompt_chip_3_full)
                ).forEach { (label, suggestion) ->
                    AssistChip(
                        onClick = { onAsk(suggestion) },
                        label = { Text(label) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (item.fromUser) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = 310.dp),
                            shape = RoundedCornerShape(14.dp),
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
                            RichMessageText(
                                text = item.text.ifBlank { if (item.streaming) stringResource(R.string.ai_thinking) else "" },
                                baseColor = tokens.textPrimary,
                                accentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text(stringResource(R.string.ai_sheet_placeholder)) },
                    maxLines = 3,
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
                IconButton(
                    onClick = {
                        if (prompt.isNotBlank() && !busy) {
                            onAsk(prompt)
                            prompt = ""
                        }
                    },
                    enabled = prompt.isNotBlank() && !busy,
                    modifier = Modifier.testTag("ai_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.ai_send_cd),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
