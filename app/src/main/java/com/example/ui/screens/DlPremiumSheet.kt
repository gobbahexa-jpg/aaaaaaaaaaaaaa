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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.LocalAccountSlot
import com.example.data.model.AccountSessionRecord
import com.example.data.model.DlPremiumCatalog
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlPrimaryButton
import com.example.ui.components.DlStatusPill
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DlPremiumSheet(
    currentTier: String,
    onDismiss: () -> Unit
) {
    val tokens = LocalDlTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pro_sheet_title), style = MaterialTheme.typography.titleLarge)
                    val tierDisplay = if (currentTier.equals("PRO", ignoreCase = true)) {
                        stringResource(R.string.settings_pro_tier_pro)
                    } else {
                        stringResource(R.string.settings_pro_tier_free)
                    }
                    Text(
                        stringResource(R.string.pro_sheet_tier, tierDisplay),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tokens.surfaceSubtle,
                border = BorderStroke(1.dp, tokens.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                val localizedRows = listOf(
                    Triple(R.string.pro_feat_folders_title, R.string.pro_feat_folders_free, R.string.pro_feat_folders_pro),
                    Triple(R.string.pro_feat_media_title, R.string.pro_feat_media_free, R.string.pro_feat_media_pro),
                    Triple(R.string.pro_feat_stickers_title, R.string.pro_feat_stickers_free, R.string.pro_feat_stickers_pro),
                    Triple(R.string.pro_feat_pins_title, R.string.pro_feat_pins_free, R.string.pro_feat_pins_pro),
                    Triple(R.string.pro_feat_ai_title, R.string.pro_feat_ai_free, R.string.pro_feat_ai_pro)
                )
                Column {
                    DlPremiumCatalog.features.forEachIndexed { idx, feat ->
                        val resTriple = localizedRows.getOrNull(idx)
                        val titleText = resTriple?.let { stringResource(it.first) } ?: feat.title
                        val freeText = resTriple?.let { stringResource(it.second) } ?: feat.freeLimit
                        val proText = resTriple?.let { stringResource(it.third) } ?: feat.proLimit
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(titleText, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                            Text(
                                stringResource(R.string.pro_sheet_free_pro, freeText, proText),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                        if (idx < DlPremiumCatalog.features.lastIndex) {
                            DlHairlineDivider()
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = tokens.surfaceElevated,
                border = BorderStroke(1.dp, tokens.hairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    stringResource(R.string.pro_sheet_disclosure),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiAccountSwitcherSheet(
    currentUid: String,
    localAccounts: List<LocalAccountSlot>,
    remoteSessions: List<AccountSessionRecord>,
    onAddOrSwitchAccount: () -> Unit,
    onRemoveLocalSlot: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalDlTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SwitchAccount, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.multi_account_title), style = MaterialTheme.typography.titleLarge)
            }
            Text(
                stringResource(R.string.multi_account_desc),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tokens.surfaceSubtle,
                border = BorderStroke(1.dp, tokens.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    items(localAccounts, key = { it.uid }) { slot ->
                        val isActive = slot.uid == currentUid
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isActive) {
                                        onAddOrSwitchAccount()
                                        onDismiss()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DlAvatar(name = slot.displayName, size = 38.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(slot.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "@${slot.username} · ${slot.email.ifBlank { slot.uid.take(10) }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isActive) tokens.statusOnline else tokens.textSecondary
                                    )
                                }
                                if (isActive) {
                                    DlStatusPill(text = stringResource(R.string.common_active), color = tokens.statusOnline)
                                } else {
                                    IconButton(onClick = { onRemoveLocalSlot(slot.uid) }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.multi_account_remove_cd),
                                            tint = tokens.statusBusy
                                        )
                                    }
                                }
                            }
                            DlHairlineDivider(modifier = Modifier.padding(start = 62.dp))
                        }
                    }
                }
            }

            if (remoteSessions.isNotEmpty()) {
                Text(
                    stringResource(R.string.multi_account_cloud_sessions, remoteSessions.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary
                )
            }

            DlPrimaryButton(
                text = stringResource(R.string.multi_account_add_cta),
                icon = Icons.Filled.Add,
                onClick = {
                    onAddOrSwitchAccount()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = DlSpacing.lg)
                    .testTag("switch_or_add_account_button")
            )
        }
    }
}
