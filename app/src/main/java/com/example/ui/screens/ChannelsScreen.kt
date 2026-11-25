package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChatRoom
import com.example.data.model.UiState
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlEmptyState
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlLoadingState
import com.example.ui.components.DlSectionHeader
import com.example.ui.components.DlTextField
import com.example.ui.components.DlTopBar
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@Composable
fun ChannelsScreen(
    myUid: String,
    myChatsState: UiState<List<ChatRoom>>,
    publicChannelsState: UiState<List<ChatRoom>>,
    onOpenChannel: (String) -> Unit,
    onJoinChannel: (ChatRoom) -> Unit,
    onCreateChannel: (title: String, description: String, handle: String?, publicVisible: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    val subscribedChannels = remember(myChatsState) {
        (myChatsState as? UiState.Success)?.data.orEmpty().filter { it.type == "CHANNEL" }
    }
    val discoverChannels = remember(publicChannelsState, subscribedChannels) {
        val myIds = subscribedChannels.map { it.id }.toSet()
        (publicChannelsState as? UiState.Success)?.data.orEmpty().filter { it.id !in myIds }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DlTopBar(
                title = stringResource(R.string.channels_title),
                subtitle = stringResource(R.string.channels_subtitle)
            )

            if (publicChannelsState is UiState.Loading && myChatsState is UiState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DlLoadingState(message = stringResource(R.string.channels_loading))
                }
            } else if (subscribedChannels.isEmpty() && discoverChannels.isEmpty()) {
                DlEmptyState(
                    icon = Icons.Filled.Campaign,
                    title = stringResource(R.string.channels_empty_title),
                    subtitle = stringResource(R.string.channels_empty_subtitle),
                    actionLabel = stringResource(R.string.channels_empty_cta),
                    onAction = { showCreateDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    if (subscribedChannels.isNotEmpty()) {
                        item { DlSectionHeader(title = stringResource(R.string.channels_subscribed_header)) }
                        items(subscribedChannels, key = { it.id }) { channel ->
                            ChannelRow(
                                channel = channel,
                                isSubscribed = true,
                                onOpen = { onOpenChannel(channel.id) },
                                onJoin = {}
                            )
                        }
                    }

                    if (discoverChannels.isNotEmpty()) {
                        item { DlSectionHeader(title = stringResource(R.string.channels_discover_header)) }
                        items(discoverChannels, key = { it.id }) { channel ->
                            ChannelRow(
                                channel = channel,
                                isSubscribed = false,
                                onOpen = { onOpenChannel(channel.id) },
                                onJoin = { onJoinChannel(channel) }
                            )
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
                .testTag("create_channel_fab"),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.channels_create_fab_cd))
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var handle by remember { mutableStateOf("") }
        var publicVisible by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.channels_create_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DlTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = stringResource(R.string.new_chat_channel_name_label)
                    )
                    DlTextField(
                        value = handle,
                        onValueChange = { handle = it },
                        label = stringResource(R.string.new_chat_handle_label)
                    )
                    DlTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = stringResource(R.string.new_chat_desc_label),
                        singleLine = false,
                        maxLines = 3
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.channels_public_toggle),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(checked = publicVisible, onCheckedChange = { publicVisible = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) {
                        onCreateChannel(title, desc, handle.takeIf { it.isNotBlank() }, publicVisible)
                        showCreateDialog = false
                    }
                }) { Text(stringResource(R.string.channels_launch_cta)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun ChannelRow(
    channel: ChatRoom,
    isSubscribed: Boolean,
    onOpen: () -> Unit,
    onJoin: () -> Unit
) {
    val tokens = LocalDlTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DlSpacing.base, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DlAvatar(name = channel.title, colorHex = "#6C5CE7", size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        channel.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.publicVisible) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Public, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                val subText = stringResource(R.string.chats_subscribers_count, channel.memberUids.size)
                Text(
                    text = if (!channel.channelHandle.isNullOrBlank()) {
                        "@${channel.channelHandle} · $subText"
                    } else {
                        subText
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (channel.description.isNotBlank()) {
                    Text(
                        channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isSubscribed) {
                OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.common_open)) }
            } else {
                Button(onClick = onJoin) { Text(stringResource(R.string.common_join)) }
            }
        }
        DlHairlineDivider(modifier = Modifier.padding(start = 74.dp))
    }
}
