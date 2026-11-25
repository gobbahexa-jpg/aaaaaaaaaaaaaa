package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.ImageUtils
import com.example.data.model.StoryItem
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlEmptyState
import com.example.ui.components.DlErrorState
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlLoadingState
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.components.DlTopBar
import com.example.ui.components.formatRelativeTimeLocalized
import com.example.ui.theme.AccentColorOptions
import com.example.ui.theme.DlRadius
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens
import com.example.ui.theme.parseHexColor
import kotlinx.coroutines.delay

@Composable
fun StoriesScreen(
    myUid: String,
    storiesState: UiState<List<StoryItem>>,
    allUsers: List<UserProfile>,
    onCreateStory: (text: String, bgHex: String, imageUri: Uri?, hiddenFrom: List<String>) -> Unit,
    onViewOrReactStory: (StoryItem, String?) -> Unit,
    onReplyToStory: (UserProfile, String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    restrictionNotice: String? = null,
    onOpenAccountStatus: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var activeStoryIndex by remember { mutableStateOf<Int?>(null) }

    val stories = (storiesState as? UiState.Success)?.data.orEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DlTopBar(
                title = stringResource(R.string.stories_title),
                subtitle = stringResource(R.string.stories_subtitle),
                actions = {
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

            when (storiesState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DlLoadingState(message = stringResource(R.string.stories_loading))
                }
                is UiState.Error -> DlErrorState(
                    title = stringResource(R.string.stories_error_title),
                    message = storiesState.message
                )
                is UiState.Success -> {
                    if (stories.isEmpty()) {
                        DlEmptyState(
                            icon = Icons.Filled.AutoStories,
                            title = stringResource(R.string.stories_empty_title),
                            subtitle = stringResource(R.string.stories_empty_subtitle),
                            actionLabel = stringResource(R.string.stories_empty_cta),
                            onAction = { showCreateDialog = true }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(stories.size, key = { stories[it].id }) { index ->
                                val story = stories[index]
                                val viewed = myUid in story.viewedBy
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable {
                                            activeStoryIndex = index
                                            onViewOrReactStory(story, null)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = DlSpacing.base, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .border(
                                                    width = 2.dp,
                                                    color = if (viewed) tokens.hairline
                                                    else MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                )
                                                .padding(3.dp)
                                        ) {
                                            DlAvatar(
                                                name = story.authorName,
                                                colorHex = story.backgroundHex,
                                                size = 48.dp
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(story.authorName, style = MaterialTheme.typography.titleMedium)
                                                if (!viewed) {
                                                    Spacer(Modifier.width(8.dp))
                                                    DlStatusPill(text = stringResource(R.string.stories_badge_new))
                                                }
                                            }
                                            Text(
                                                text = story.text.ifBlank { stringResource(R.string.stories_photo_label) },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.textSecondary,
                                                maxLines = 1
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                formatRelativeTimeLocalized(story.createdAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.textTertiary
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Filled.Visibility,
                                                    null,
                                                    Modifier.size(14.dp),
                                                    tint = tokens.textSecondary
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    "${story.viewedBy.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = tokens.textSecondary
                                                )
                                            }
                                        }
                                    }
                                    DlHairlineDivider(modifier = Modifier.padding(start = 80.dp))
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
                .testTag("create_story_fab"),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.stories_post_fab_cd))
        }
    }

    if (showCreateDialog) {
        var text by remember { mutableStateOf("") }
        var bgHex by remember { mutableStateOf("#00E5FF") }
        var pickedUri by remember { mutableStateOf<Uri?>(null) }
        val hiddenUids = remember { mutableStateListOf<String>() }

        val picker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri -> pickedUri = uri }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.stories_create_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DlTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = stringResource(R.string.stories_caption_label),
                        singleLine = false,
                        maxLines = 3
                    )
                    OutlinedButton(
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Photo, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (pickedUri != null) R.string.stories_photo_selected else R.string.stories_attach_photo
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AccentColorOptions.forEach { (label, hex) ->
                            val colorName = when (hex.uppercase()) {
                                "#00E5FF" -> stringResource(R.string.color_cyan)
                                "#6C5CE7" -> stringResource(R.string.color_indigo)
                                "#00D68F", "#12B886" -> stringResource(R.string.color_emerald)
                                "#FF4D6D" -> stringResource(R.string.color_crimson)
                                "#FFB800", "#F59F00" -> stringResource(R.string.color_amber)
                                else -> label
                            }
                            FilterChip(
                                selected = bgHex == hex,
                                onClick = { bgHex = hex },
                                label = { Text(colorName) }
                            )
                        }
                    }
                    val others = allUsers.filter { it.userId != myUid }
                    if (others.isNotEmpty()) {
                        Text(stringResource(R.string.stories_hide_from), style = MaterialTheme.typography.labelSmall)
                        others.take(3).forEach { u ->
                            val hidden = u.userId in hiddenUids
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    if (hidden) hiddenUids.remove(u.userId) else hiddenUids.add(u.userId)
                                }
                            ) {
                                Checkbox(checked = hidden, onCheckedChange = {
                                    if (it) hiddenUids.add(u.userId) else hiddenUids.remove(u.userId)
                                })
                                Text(u.displayName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (text.isNotBlank() || pickedUri != null) {
                        onCreateStory(text, bgHex, pickedUri, hiddenUids.toList())
                        showCreateDialog = false
                    }
                }) { Text(stringResource(R.string.stories_publish_cta)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    val currentIndex = activeStoryIndex
    if (currentIndex != null && currentIndex in stories.indices) {
        val story = stories[currentIndex]
        var progress by remember(story.id) { mutableFloatStateOf(0f) }
        var replyDraft by remember(story.id) { mutableStateOf("") }
        var showViewersList by remember(story.id) { mutableStateOf(false) }

        LaunchedEffect(story.id, showViewersList) {
            onViewOrReactStory(story, null)
            while (progress < 1f) {
                delay(100L)
                if (!showViewersList && replyDraft.isEmpty()) {
                    progress = (progress + 0.02f).coerceAtMost(1f)
                }
            }
            if (currentIndex + 1 < stories.size) {
                activeStoryIndex = currentIndex + 1
            } else {
                activeStoryIndex = null
            }
        }

        Dialog(
            onDismissRequest = { activeStoryIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val bgColor = parseHexColor(story.backgroundHex, MaterialTheme.colorScheme.primary)
            val bmp = remember(story.mediaDataBase64) {
                ImageUtils.decodeBase64Bitmap(story.mediaDataBase64)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor.copy(alpha = 0.25f))
                    .background(tokens.overlayBackdrop.copy(alpha = 0.92f))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DlSpacing.xs)
                    ) {
                        stories.forEachIndexed { idx, _ ->
                            val value = when {
                                idx < currentIndex -> 1f
                                idx == currentIndex -> progress
                                else -> 0f
                            }
                            LinearProgressIndicator(
                                progress = { value },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(DlRadius.xxs)),
                                color = tokens.overlayInk,
                                trackColor = tokens.overlayInk.copy(alpha = 0.25f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DlAvatar(name = story.authorName, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(story.authorName, color = tokens.overlayInk, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(
                                        R.string.stories_meta_views,
                                        formatRelativeTimeLocalized(story.createdAt),
                                        story.viewedBy.size
                                    ),
                                    color = tokens.overlayInkMuted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Row {
                            if (story.authorId == myUid) {
                                IconButton(onClick = { showViewersList = true }) {
                                    Icon(Icons.Filled.Visibility, contentDescription = stringResource(R.string.stories_viewers_cd), tint = tokens.overlayInk)
                                }
                            }
                            IconButton(onClick = { activeStoryIndex = null }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.stories_close_cd), tint = tokens.overlayInk)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                if (currentIndex > 0) activeStoryIndex = currentIndex - 1
                            }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                if (currentIndex + 1 < stories.size) activeStoryIndex = currentIndex + 1
                                else activeStoryIndex = null
                            }
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = story.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(DlRadius.xl)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(DlSpacing.base))
                    }
                    if (story.text.isNotBlank()) {
                        Text(
                            text = story.text,
                            style = MaterialTheme.typography.headlineMedium,
                            color = tokens.overlayInk,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("🔥", "⚡", "❤️", "😂", "🫡").forEach { emoji ->
                            Surface(
                                modifier = Modifier.clickable {
                                    onViewOrReactStory(story, emoji)
                                    activeStoryIndex = null
                                },
                                shape = CircleShape,
                                color = tokens.overlaySurface
                            ) {
                                Text(emoji, modifier = Modifier.padding(DlSpacing.md))
                            }
                        }
                    }

                    if (story.authorId != myUid) {
                        val authorProfile = allUsers.firstOrNull { it.userId == story.authorId }
                            ?: UserProfile(userId = story.authorId, displayName = story.authorName)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = replyDraft,
                                onValueChange = { replyDraft = it },
                                placeholder = {
                                    Text(
                                        stringResource(R.string.stories_reply_placeholder, story.authorName),
                                        color = tokens.overlayInkMuted
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = tokens.overlayInk,
                                    unfocusedTextColor = tokens.overlayInk,
                                    focusedBorderColor = tokens.overlayAccent,
                                    unfocusedBorderColor = tokens.overlayInk.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(DlRadius.sheet)
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (replyDraft.isNotBlank()) {
                                        val quote = context.getString(R.string.stories_reply_quote_prefix, story.text.take(40))
                                        onReplyToStory(authorProfile, "$quote\n${replyDraft.trim()}")
                                        replyDraft = ""
                                        activeStoryIndex = null
                                    }
                                },
                                enabled = replyDraft.isNotBlank()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.stories_reply_cd), tint = tokens.overlayAccent)
                            }
                        }
                    }
                }
            }

            if (showViewersList) {
                AlertDialog(
                    onDismissRequest = { showViewersList = false },
                    title = { Text(stringResource(R.string.stories_viewers_title, story.viewedBy.size)) },
                    text = {
                        if (story.viewedBy.isEmpty()) {
                            Text(stringResource(R.string.stories_viewers_empty))
                        } else {
                            LazyColumn(modifier = Modifier.height(180.dp)) {
                                items(story.viewedBy.size) { vIdx ->
                                    val viewerUid = story.viewedBy[vIdx]
                                    val viewer = allUsers.firstOrNull { it.userId == viewerUid }
                                    val reaction = story.reactions[viewerUid]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        DlAvatar(name = viewer?.displayName ?: viewerUid.take(6), size = 32.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(viewer?.displayName ?: viewerUid.take(8), modifier = Modifier.weight(1f))
                                        if (reaction != null) Text(reaction)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showViewersList = false }) { Text(stringResource(R.string.common_close)) }
                    }
                )
            }
        }
    }
}
