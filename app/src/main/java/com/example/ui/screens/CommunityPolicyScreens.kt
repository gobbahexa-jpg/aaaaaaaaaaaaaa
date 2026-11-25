package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.LegalPolicyDocument
import com.example.data.repository.SupportRepository
import com.example.ui.components.DlFocalCard
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTopBar
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@Composable
fun CommunityPoliciesScreen(
    onBack: () -> Unit,
    initialDocId: String? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    val docs = remember { SupportRepository.draftPolicyDocuments }
    var selectedDocId by remember(initialDocId) {
        mutableStateOf(initialDocId ?: docs.firstOrNull()?.id ?: "community_guidelines")
    }
    val selectedDoc = docs.firstOrNull { it.id == selectedDocId } ?: docs.first()

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("community_policies_screen")
    ) {
        DlTopBar(
            title = stringResource(R.string.policy_screen_title),
            subtitle = stringResource(R.string.policy_screen_subtitle),
            onBack = onBack
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = DlSpacing.base, vertical = DlSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            docs.forEach { doc ->
                FilterChip(
                    selected = doc.id == selectedDoc.id,
                    onClick = { selectedDocId = doc.id },
                    label = { Text(doc.title) },
                    modifier = Modifier.testTag("policy_tab_${doc.id}")
                )
            }
        }
        DlHairlineDivider()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("community_policies_list"),
            contentPadding = PaddingValues(DlSpacing.base),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = tokens.statusAway.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, tokens.statusAway.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Policy,
                            contentDescription = null,
                            tint = tokens.statusAway,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DlStatusPill(
                                    text = stringResource(R.string.policy_draft_badge),
                                    color = tokens.statusAway
                                )
                                Text(
                                    text = selectedDoc.versionTag,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary
                                )
                            }
                            Text(
                                text = stringResource(R.string.policy_draft_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    }
                }
            }

            item {
                DlFocalCard(accentBorder = MaterialTheme.colorScheme.primary) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selectedDoc.id == "enforcement_policy") Icons.Filled.Gavel else Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = selectedDoc.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = tokens.textPrimary
                            )
                        }
                        if (selectedDoc.isDraft) {
                            DlStatusPill(
                                text = "DRAFT",
                                color = tokens.statusAway
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = selectedDoc.lastUpdatedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                }
            }

            items(selectedDoc.sections) { (heading, body) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = tokens.surfaceElevated,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(DlSpacing.base),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = heading,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.textPrimary
                        )
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DraftPoliciesDialog(
    onDismiss: () -> Unit,
    initialDocId: String = "community_guidelines"
) {
    val tokens = LocalDlTokens.current
    val docs = remember { SupportRepository.draftPolicyDocuments }
    var selectedDoc by remember(initialDocId) {
        mutableStateOf(docs.firstOrNull { it.id == initialDocId } ?: docs.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(selectedDoc.title, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                DlStatusPill(text = "DRAFT", color = tokens.statusAway)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    docs.forEach { doc ->
                        FilterChip(
                            selected = doc.id == selectedDoc.id,
                            onClick = { selectedDoc = doc },
                            label = { Text(doc.title) }
                        )
                    }
                }
                Text(
                    text = selectedDoc.lastUpdatedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.statusAway
                )
                LazyColumn(
                    modifier = Modifier.height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedDoc.sections) { (heading, body) ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = heading,
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.textPrimary
                            )
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}
