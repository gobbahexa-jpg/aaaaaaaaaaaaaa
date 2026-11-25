package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.model.AppealRecord
import com.example.data.model.AppealStatus
import com.example.data.model.DlAccountStatus
import com.example.data.model.DlFeatureRestriction
import com.example.data.model.UserEnforcement
import com.example.data.model.UserRestrictionRecord
import com.example.data.model.UserWarning
import com.example.ui.components.DlEmptyState
import com.example.ui.components.DlFocalCard
import com.example.ui.components.DlPrimaryButton
import com.example.ui.components.DlSectionHeader
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.components.DlTopBar
import com.example.ui.components.formatRelativeTimeLocalized
import com.example.ui.theme.DlCrimson
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@Composable
fun ContextualRestrictionBanner(
    message: String,
    onOpenAccountStatus: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DlSpacing.base, vertical = 6.dp)
            .testTag("contextual_restriction_banner"),
        shape = RoundedCornerShape(14.dp),
        color = DlCrimson.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, DlCrimson.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ReportProblem,
                contentDescription = null,
                tint = DlCrimson,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (onOpenAccountStatus != null) {
                TextButton(
                    onClick = onOpenAccountStatus,
                    modifier = Modifier.testTag("restriction_banner_action")
                ) {
                    Text(
                        text = stringResource(R.string.v6_review_status_cta),
                        style = MaterialTheme.typography.labelMedium,
                        color = DlCrimson
                    )
                }
            }
        }
    }
}

@Composable
fun AccountStatusScreen(
    myEnforcement: UserEnforcement?,
    warnings: List<UserWarning>,
    restrictions: List<UserRestrictionRecord>,
    appeals: List<AppealRecord>,
    onBack: () -> Unit,
    onAcknowledgeWarning: (String) -> Unit,
    onSubmitAppeal: (restrictionId: String, originalEnforcerUid: String, reason: String) -> Unit,
    onProvideAppealInfo: (appealId: String, info: String) -> Unit,
    onOpenPolicies: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    BackHandler(onBack = onBack)

    var appealTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // restrictionId to originalEnforcerUid
    var appealReason by remember { mutableStateOf("") }
    var infoTargetAppeal by remember { mutableStateOf<AppealRecord?>(null) }
    var infoDraft by remember { mutableStateOf("") }

    val nowMs = System.currentTimeMillis()
    val status = DlAccountStatus.fromRaw(myEnforcement?.accountStatus)
    val activeBlocked = remember(myEnforcement, nowMs) {
        myEnforcement?.activeBlockedFeatures(nowMs).orEmpty()
    }
    val statusColor = when {
        status.isSevereSuspension -> DlCrimson
        activeBlocked.isNotEmpty() || status != DlAccountStatus.ACTIVE -> tokens.statusAway
        else -> tokens.statusOnline
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("account_status_screen")
    ) {
        DlTopBar(
            title = stringResource(R.string.account_status_title),
            subtitle = stringResource(R.string.account_status_subtitle),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("account_status_list"),
            contentPadding = PaddingValues(DlSpacing.base),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DlFocalCard(accentBorder = statusColor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (status == DlAccountStatus.ACTIVE && activeBlocked.isEmpty()) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.Gavel
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.account_status_standing_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                        }
                        DlStatusPill(text = status.label, color = statusColor)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (myEnforcement == null || (status == DlAccountStatus.ACTIVE && activeBlocked.isEmpty())) {
                        Text(
                            text = stringResource(R.string.account_status_good_standing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    } else {
                        if (myEnforcement.reasonSummary.isNotBlank()) {
                            Text(
                                text = myEnforcement.reasonSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        val globalCountdown = formatExpiryCountdown(myEnforcement.globalExpiryEpochMs, nowMs)
                        if (globalCountdown != null) {
                            Text(
                                text = stringResource(R.string.account_status_expires_in, globalCountdown),
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenPolicies) {
                        Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.account_status_view_policies))
                    }
                }
            }

            if (activeBlocked.isNotEmpty()) {
                item { DlSectionHeader(title = stringResource(R.string.account_status_active_limits)) }
                items(activeBlocked, key = { it.key }) { feature ->
                    val expiryMs = myEnforcement?.featureExpiry(feature, nowMs) ?: 0L
                    val countdown = formatExpiryCountdown(expiryMs, nowMs) ?: stringResource(R.string.account_status_permanent)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.surfaceElevated,
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = feature.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = countdown,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DlCrimson
                                )
                            }
                            DlStatusPill(text = stringResource(R.string.account_status_restricted_pill), color = DlCrimson)
                        }
                    }
                }

                if (restrictions.isEmpty() && myEnforcement?.appealEligible == true) {
                    item {
                        val fallbackId = myEnforcement.lastEnforcementId.ifBlank { "current_enforcement" }
                        val usedCount = appeals.count { it.restrictionId == fallbackId }
                        if (usedCount < 3) {
                            DlPrimaryButton(
                                text = stringResource(R.string.account_status_submit_appeal_left, 3 - usedCount),
                                onClick = {
                                    appealTarget = fallbackId to myEnforcement.lastEnforcedBy
                                    appealReason = ""
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("appeal_current_enforcement_button")
                            )
                        }
                    }
                }
            }

            item { DlSectionHeader(title = stringResource(R.string.account_status_warnings_header, warnings.size)) }
            if (warnings.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.account_status_no_warnings),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
            } else {
                items(warnings, key = { it.id }) { warning ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.surfaceElevated,
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier.fillMaxWidth()
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
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = tokens.statusAway, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = warning.title.ifBlank { warning.reasonCode },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = tokens.textPrimary
                                    )
                                }
                                DlStatusPill(
                                    text = warning.severity,
                                    color = if (warning.severity == "HIGH") DlCrimson else tokens.statusAway
                                )
                            }
                            Text(
                                text = warning.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatRelativeTimeLocalized(warning.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textTertiary
                                )
                                if (warning.acknowledged) {
                                    DlStatusPill(
                                        text = stringResource(R.string.account_status_acknowledged),
                                        color = tokens.statusOnline
                                    )
                                } else {
                                    Button(onClick = { onAcknowledgeWarning(warning.id) }) {
                                        Text(stringResource(R.string.account_status_acknowledge_cta))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { DlSectionHeader(title = stringResource(R.string.account_status_restrictions_history, restrictions.size)) }
            if (restrictions.isEmpty()) {
                item {
                    DlEmptyState(
                        icon = Icons.Filled.Shield,
                        title = stringResource(R.string.account_status_clean_title),
                        subtitle = stringResource(R.string.account_status_clean_sub)
                    )
                }
            } else {
                items(restrictions, key = { it.id }) { rec ->
                    val usedAttempts = appeals.count { it.restrictionId == rec.id }
                    val remainingAttempts = (3 - usedAttempts).coerceAtLeast(0)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.surfaceElevated,
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier.fillMaxWidth()
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
                                    text = DlAccountStatus.fromRaw(rec.status).label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary
                                )
                                DlStatusPill(
                                    text = if (rec.active) stringResource(R.string.common_active) else stringResource(R.string.common_resolved),
                                    color = if (rec.active) DlCrimson else tokens.statusOnline
                                )
                            }
                            if (rec.reason.isNotBlank()) {
                                Text(rec.reason, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            }
                            if (rec.features.isNotEmpty()) {
                                Text(
                                    text = rec.features.mapNotNull { DlFeatureRestriction.fromKey(it)?.label }.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            val expText = formatExpiryCountdown(rec.expiresAtEpochMs, nowMs)
                            if (expText != null && rec.active) {
                                Text(
                                    text = stringResource(R.string.account_status_expires_in, expText),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DlCrimson
                                )
                            }
                            if (rec.appealEligible && remainingAttempts > 0) {
                                OutlinedButton(
                                    onClick = {
                                        appealTarget = rec.id to rec.enforcedByUid
                                        appealReason = ""
                                    },
                                    modifier = Modifier.testTag("appeal_restriction_${rec.id}")
                                ) {
                                    Text(stringResource(R.string.account_status_submit_appeal_left, remainingAttempts))
                                }
                            }
                        }
                    }
                }
            }

            item { DlSectionHeader(title = stringResource(R.string.account_status_appeals_header, appeals.size)) }
            if (appeals.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.account_status_no_appeals),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
            } else {
                items(appeals, key = { it.id }) { appeal ->
                    val aStatus = AppealStatus.fromRaw(appeal.status)
                    val aColor = when (aStatus) {
                        AppealStatus.APPROVED -> tokens.statusOnline
                        AppealStatus.REJECTED -> DlCrimson
                        AppealStatus.INFO_REQUESTED, AppealStatus.UNDER_REVIEW -> tokens.statusAway
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.surfaceElevated,
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier.fillMaxWidth()
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
                                    text = stringResource(R.string.account_status_appeal_attempt, appeal.attemptNumber),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.textPrimary
                                )
                                DlStatusPill(text = aStatus.label, color = aColor)
                            }
                            Text(appeal.reason, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            if (appeal.additionalInfo.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.account_status_extra_info_prefix, appeal.additionalInfo),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textPrimary
                                )
                            }
                            if (appeal.reviewerDecisionNote.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.account_status_reviewer_note_prefix, appeal.reviewerDecisionNote),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = aColor
                                )
                            }
                            if (aStatus == AppealStatus.INFO_REQUESTED) {
                                Button(onClick = {
                                    infoTargetAppeal = appeal
                                    infoDraft = ""
                                }) {
                                    Text(stringResource(R.string.account_status_provide_info_cta))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingAppealTarget = appealTarget
    if (pendingAppealTarget != null) {
        AlertDialog(
            onDismissRequest = { appealTarget = null },
            containerColor = tokens.surfaceElevated,
            title = { Text(stringResource(R.string.account_status_appeal_dialog_title), color = tokens.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.account_status_appeal_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                    DlTextField(
                        value = appealReason,
                        onValueChange = { appealReason = it },
                        label = stringResource(R.string.account_status_appeal_reason_label),
                        singleLine = false,
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.testTag("appeal_reason_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (appealReason.trim().length >= 5) {
                            onSubmitAppeal(pendingAppealTarget.first, pendingAppealTarget.second, appealReason.trim())
                            appealTarget = null
                        }
                    },
                    modifier = Modifier.testTag("submit_appeal_confirm_button")
                ) { Text(stringResource(R.string.common_submit)) }
            },
            dismissButton = {
                TextButton(onClick = { appealTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    val pendingInfo = infoTargetAppeal
    if (pendingInfo != null) {
        AlertDialog(
            onDismissRequest = { infoTargetAppeal = null },
            containerColor = tokens.surfaceElevated,
            title = { Text(stringResource(R.string.account_status_provide_info_cta), color = tokens.textPrimary) },
            text = {
                DlTextField(
                    value = infoDraft,
                    onValueChange = { infoDraft = it },
                    label = stringResource(R.string.account_status_extra_info_input_label),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (infoDraft.trim().length >= 3) {
                        onProvideAppealInfo(pendingInfo.id, infoDraft.trim())
                        infoTargetAppeal = null
                    }
                }) { Text(stringResource(R.string.common_submit)) }
            },
            dismissButton = {
                TextButton(onClick = { infoTargetAppeal = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

fun formatExpiryCountdown(expiresAtEpochMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
    if (expiresAtEpochMs == 0L) return null
    if (expiresAtEpochMs == -1L) return "Permanent"
    val diff = expiresAtEpochMs - nowMs
    if (diff <= 0L) return "Expired"
    val mins = (diff / 60_000L).coerceAtLeast(1L)
    val hrs = mins / 60L
    val days = hrs / 24L
    return when {
        days >= 1L -> "${days}d ${hrs % 24L}h remaining"
        hrs >= 1L -> "${hrs}h ${mins % 60L}m remaining"
        else -> "${mins}m remaining"
    }
}
