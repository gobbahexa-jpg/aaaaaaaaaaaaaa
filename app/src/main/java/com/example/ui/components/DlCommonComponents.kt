package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ImageUtils
import com.example.core.LocaleHelper
import com.example.data.model.ChatRoom
import com.example.data.model.ChatType
import com.example.data.model.DlSticker
import com.example.data.model.OnlineStatus
import com.example.data.model.SettingsCategory
import com.example.ui.theme.DlRadius
import com.example.ui.theme.DlSizing
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens
import com.example.ui.theme.resolveAccessibleAccent
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DlAvatar(
    name: String,
    colorHex: String = "#00E5FF",
    photoBase64: String? = null,
    onlineStatus: String? = null,
    size: Dp = DlSizing.avatarMd,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    val accent = resolveAccessibleAccent(colorHex, tokens.isDark)
    val fill = accent.copy(alpha = if (tokens.isDark) 0.22f else 0.14f)
    val ink = accent
    val bitmap: Bitmap? = remember(photoBase64) { ImageUtils.decodeBase64Bitmap(photoBase64) }
    val initials = remember(name) {
        name.trim().split(" ").filter { it.isNotBlank() }
            .take(2).joinToString("") { it.first().uppercase() }
            .ifBlank { "DL" }
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (bitmap != null) tokens.surfaceElevated else fill)
                .border(1.dp, accent.copy(alpha = 0.42f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.avatar_cd, name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = initials,
                    style = if (size >= 52.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = ink,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (onlineStatus != null && onlineStatus != "OFFLINE") {
            val dotColor = when (onlineStatus) {
                "ONLINE" -> tokens.statusOnline
                "AWAY" -> tokens.statusAway
                "DND" -> tokens.statusBusy
                else -> tokens.textTertiary
            }
            val dotSize = if (size >= 56.dp) 15.dp else 12.dp
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

@Composable
fun DlLanguageSelector(
    selectedLanguage: String,
    onSelectLanguage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    val normalized = LocaleHelper.normalizeCode(selectedLanguage)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = stringResource(R.string.language_label),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        LocaleHelper.supportedLanguages.forEach { option ->
            val selected = normalized == option.code
            Surface(
                shape = RoundedCornerShape(DlRadius.md),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else tokens.surfaceSubtle,
                border = BorderStroke(
                    width = if (selected) 1.5.dp else DlSizing.divider,
                    color = if (selected) MaterialTheme.colorScheme.primary else tokens.hairline
                ),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = DlSizing.touchTarget)
                    .clip(RoundedCornerShape(DlRadius.md))
                    .clickable { onSelectLanguage(option.code) }
                    .testTag("language_option_${option.code}")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = option.nativeName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary else tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineStatus.localizedLabel(): String = when (this) {
    OnlineStatus.ONLINE -> stringResource(R.string.status_online)
    OnlineStatus.AWAY -> stringResource(R.string.status_away)
    OnlineStatus.DND -> stringResource(R.string.status_dnd)
    OnlineStatus.OFFLINE -> stringResource(R.string.status_offline)
}

@Composable
fun SettingsCategory.localizedTitle(): String = when (this) {
    SettingsCategory.ACCOUNT -> stringResource(R.string.settings_cat_account)
    SettingsCategory.PRIVACY -> stringResource(R.string.settings_cat_privacy)
    SettingsCategory.APPEARANCE -> stringResource(R.string.settings_cat_appearance)
    SettingsCategory.CHATS -> stringResource(R.string.settings_cat_chats)
    SettingsCategory.NOTIFICATIONS -> stringResource(R.string.settings_cat_notifications)
    SettingsCategory.STORAGE -> stringResource(R.string.settings_cat_storage)
    SettingsCategory.DEVICES -> stringResource(R.string.settings_cat_devices)
    SettingsCategory.SECURITY -> stringResource(R.string.settings_cat_security)
    SettingsCategory.ABOUT -> stringResource(R.string.settings_cat_about)
}

@Composable
fun DlSticker.localizedLabel(): String = when (id) {
    "dl_shield" -> stringResource(R.string.sticker_shield)
    "dl_bolt" -> stringResource(R.string.sticker_bolt)
    "dl_salute" -> stringResource(R.string.sticker_salute)
    "dl_fire" -> stringResource(R.string.sticker_fire)
    "dl_lock" -> stringResource(R.string.sticker_lock)
    "dl_target" -> stringResource(R.string.sticker_target)
    "dl_rocket" -> stringResource(R.string.sticker_rocket)
    "dl_skull" -> stringResource(R.string.sticker_skull)
    else -> label
}

@Composable
fun ChatRoom.localizedTitle(myUid: String): String {
    if (ChatType.fromRaw(type) != ChatType.DIRECT) {
        return title.ifBlank { stringResource(R.string.common_untitled_fallback) }
    }
    val otherEntry = participantNames.entries.firstOrNull { it.key != myUid }
    return otherEntry?.value?.takeIf { it.isNotBlank() }
        ?: title.ifBlank { stringResource(R.string.common_direct_chat_fallback) }
}

@Composable
fun DlEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(DlSpacing.base),
        shape = RoundedCornerShape(DlRadius.sheet),
        color = tokens.surfaceSubtle,
        border = BorderStroke(DlSizing.divider, tokens.hairline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = DlSpacing.lg, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(DlSizing.avatarLg)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(DlSizing.divider, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(DlSizing.iconLg)
                )
            }
            Spacer(modifier = Modifier.height(DlSpacing.base))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(DlSpacing.base))
                DlPrimaryButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
fun AudioWaveformBars(
    samples: List<Int>,
    progressRatio: Float = 0f,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = LocalDlTokens.current.textTertiary.copy(alpha = 0.45f),
    modifier: Modifier = Modifier
) {
    val bars = remember(samples) {
        if (samples.isEmpty()) listOf(22, 46, 75, 58, 88, 42, 64, 38, 72, 50, 30, 62, 80, 44, 28, 54)
        else samples.take(24)
    }
    Row(
        modifier = modifier.height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        bars.forEachIndexed { index, value ->
            val fraction = (index + 1).toFloat() / bars.size.toFloat()
            val barColor = if (progressRatio > 0f && fraction <= progressRatio) activeColor else inactiveColor
            val barHeight = (5 + (value.coerceIn(5, 100) * 0.19f)).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(DlRadius.xxs))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun formatRelativeTimeLocalized(ts: Timestamp?): String {
    if (ts == null) return stringResource(R.string.time_now)
    val date = ts.toDate()
    val diff = System.currentTimeMillis() - date.time
    return when {
        diff < 60_000L -> stringResource(R.string.time_now)
        diff < 3_600_000L -> stringResource(R.string.time_minutes_ago, (diff / 60_000L).toInt())
        diff < 86_400_000L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}

@Composable
fun formatDateSeparatorLocalized(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return stringResource(R.string.time_today)
    val calMsg = Calendar.getInstance().apply { time = date }
    val calToday = Calendar.getInstance()
    if (calMsg.get(Calendar.YEAR) == calToday.get(Calendar.YEAR) &&
        calMsg.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR)
    ) {
        return stringResource(R.string.time_today)
    }
    calToday.add(Calendar.DAY_OF_YEAR, -1)
    if (calMsg.get(Calendar.YEAR) == calToday.get(Calendar.YEAR) &&
        calMsg.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR)
    ) {
        return stringResource(R.string.time_yesterday)
    }
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
}

fun formatRelativeTime(ts: Timestamp?): String {
    if (ts == null) return "Now"
    val date = ts.toDate()
    val diff = System.currentTimeMillis() - date.time
    return when {
        diff < 60_000L -> "Now"
        diff < 3_600_000L -> "${diff / 60_000L}m"
        diff < 86_400_000L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}

fun formatDateSeparator(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return "Today"
    val calMsg = Calendar.getInstance().apply { time = date }
    val calToday = Calendar.getInstance()
    if (calMsg.get(Calendar.YEAR) == calToday.get(Calendar.YEAR) &&
        calMsg.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR)
    ) {
        return "Today"
    }
    calToday.add(Calendar.DAY_OF_YEAR, -1)
    if (calMsg.get(Calendar.YEAR) == calToday.get(Calendar.YEAR) &&
        calMsg.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR)
    ) {
        return "Yesterday"
    }
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}

fun formatTimestampClock(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
