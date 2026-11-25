package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.DlRadius
import com.example.ui.theme.DlSizing
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@Composable
fun DlHairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = DlSizing.divider,
        color = LocalDlTokens.current.hairline
    )
}

@Composable
fun DlSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingActionLabel: String? = null,
    onTrailingAction: (() -> Unit)? = null
) {
    val tokens = LocalDlTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DlSpacing.base, vertical = DlSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = tokens.messagingType.sectionOverline,
            color = tokens.textSecondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingActionLabel != null && onTrailingAction != null) {
            TextButton(
                onClick = onTrailingAction,
                contentPadding = PaddingValues(horizontal = DlSpacing.sm, vertical = DlSpacing.xs)
            ) {
                Text(
                    text = trailingActionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DlStatusPill(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(DlRadius.xs),
        color = color.copy(alpha = if (LocalDlTokens.current.isDark) 0.16f else 0.11f),
        border = BorderStroke(DlSizing.divider, color.copy(alpha = 0.28f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = DlSpacing.xxs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DlTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    leadingAvatar: (@Composable () -> Unit)? = null,
    onLeadingClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DlSizing.topBarHeight)
                .padding(horizontal = DlSpacing.md, vertical = DlSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back)
                    )
                }
                Spacer(modifier = Modifier.width(DlSpacing.xs))
            }
            if (leadingAvatar != null) {
                Box(
                    modifier = if (onLeadingClick != null) {
                        Modifier.clip(CircleShape).clickable(onClick = onLeadingClick)
                    } else Modifier
                ) {
                    leadingAvatar()
                }
                Spacer(modifier = Modifier.width(DlSpacing.md))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (onLeadingClick != null && onBack == null) Modifier.clickable(onClick = onLeadingClick) else Modifier)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DlSpacing.xxs),
                content = actions
            )
        }
        DlHairlineDivider()
    }
}

@Composable
fun DlSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailingExtra: (@Composable RowScope.() -> Unit)? = null
) {
    val tokens = LocalDlTokens.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(DlRadius.pillInput),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = tokens.textSecondary
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingExtra?.invoke(this)
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.common_clear_search),
                            tint = tokens.textSecondary
                        )
                    }
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = tokens.surfaceElevated,
            unfocusedContainerColor = tokens.surfaceElevated,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = tokens.hairline
        )
    )
}

@Composable
fun DlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 4,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val tokens = LocalDlTokens.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = tokens.textTertiary) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(DlRadius.md),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = tokens.surfaceSubtle,
            unfocusedContainerColor = tokens.surfaceSubtle,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = tokens.hairline
        )
    )
}

@Composable
fun DlPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = DlSizing.touchTarget),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(DlRadius.md),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = DlSpacing.md)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(DlSizing.iconSm),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(DlSizing.iconSm))
            Spacer(modifier = Modifier.width(DlSpacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DlSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val tokens = LocalDlTokens.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = DlSizing.touchTarget),
        enabled = enabled,
        shape = RoundedCornerShape(DlRadius.md),
        border = BorderStroke(DlSizing.divider, tokens.hairline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = tokens.surfaceSubtle,
            contentColor = tokens.textPrimary
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = DlSpacing.md)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(DlSizing.iconSm))
            Spacer(modifier = Modifier.width(DlSpacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DlDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = DlSizing.touchTarget),
        enabled = enabled,
        shape = RoundedCornerShape(DlRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = DlSpacing.md)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(DlSizing.iconSm))
            Spacer(modifier = Modifier.width(DlSpacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DlFocalCard(
    modifier: Modifier = Modifier,
    accentBorder: Color? = null,
    contentPadding: PaddingValues = PaddingValues(DlSpacing.base),
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalDlTokens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DlRadius.lg),
        color = tokens.surfaceElevated,
        border = BorderStroke(DlSizing.divider, accentBorder?.copy(alpha = 0.45f) ?: tokens.hairline)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun DlListRow(
    title: String,
    subtitle: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    val verticalPad = DlSpacing.rowVertical(tokens.compactDensity)
    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = DlSpacing.rowMinHeight)
                .padding(horizontal = DlSpacing.base, vertical = verticalPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(DlSpacing.md))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(DlSpacing.xxs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(DlSpacing.sm))
                trailingContent()
            }
        }
        if (showDivider) {
            DlHairlineDivider(modifier = Modifier.padding(start = if (leadingContent != null) DlSizing.avatarLg else DlSpacing.base))
        }
    }
}

@Composable
fun DlInlineToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    switchModifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DlSizing.touchTarget)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = DlSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(DlSpacing.xxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
        }
        Spacer(Modifier.width(DlSpacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier
        )
    }
}

@Composable
fun DlSettingToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: ImageVector? = null,
    switchModifier: Modifier = Modifier,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    DlListRow(
        title = title,
        subtitle = subtitle,
        leadingContent = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = tokens.textSecondary,
                    modifier = Modifier.size(DlSizing.iconMd)
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = switchModifier
            )
        },
        onClick = { onCheckedChange(!checked) },
        showDivider = showDivider,
        modifier = modifier
    )
}

@Composable
fun DlErrorState(
    title: String = stringResource(R.string.error_unable_to_load),
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    DlFocalCard(
        modifier = modifier.padding(DlSpacing.base),
        accentBorder = MaterialTheme.colorScheme.error
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(DlSpacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(DlSpacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = LocalDlTokens.current.textSecondary
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(DlSpacing.md))
            DlSecondaryButton(text = stringResource(R.string.common_retry), onClick = onRetry)
        }
    }
}

@Composable
fun DlLoadingState(
    message: String = stringResource(R.string.loading_default),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(DlSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(strokeWidth = 2.5.dp)
        Spacer(modifier = Modifier.height(DlSpacing.md))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalDlTokens.current.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}
