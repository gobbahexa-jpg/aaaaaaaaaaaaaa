package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlPrimaryButton
import com.example.ui.components.DlTextField
import com.example.ui.theme.AccentColorOptions
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens
import com.example.ui.theme.contrastInkFor
import com.example.ui.theme.parseHexColor
import com.example.ui.theme.resolveAccessibleAccent

@Composable
fun RegisterScreen(
    uiState: RegisterUiState,
    onDisplayNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onBioChanged: (String) -> Unit,
    onAvatarColorSelected: (String) -> Unit,
    onDiscoverableChanged: (Boolean) -> Unit,
    onSubmitRegister: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDlTokens.current
    val previewHandle = uiState.previewHandle
    val previewName = if (uiState.displayName.isBlank()) {
        stringResource(R.string.auth_new_member_preview)
    } else {
        uiState.previewDisplayName
    }
    val previewAccent = resolveAccessibleAccent(uiState.avatarColorHex, tokens.isDark)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("register_screen"),
        verticalArrangement = Arrangement.spacedBy(DlSpacing.md)
    ) {
        // Editorial Header Row with Back to Login
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateToLogin,
                modifier = Modifier.testTag("back_to_login_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.auth_back_to_login)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.auth_register_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = tokens.textPrimary
                )
                Text(
                    text = stringResource(R.string.auth_register_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
        }

        // Focal Identity Preview Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = tokens.surfaceElevated,
            border = BorderStroke(1.dp, previewAccent.copy(alpha = 0.42f))
        ) {
            Row(
                modifier = Modifier.padding(DlSpacing.base),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DlAvatar(
                    name = previewName,
                    colorHex = uiState.avatarColorHex,
                    onlineStatus = "ONLINE",
                    size = 56.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = previewName,
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "@$previewHandle",
                        style = MaterialTheme.typography.labelMedium,
                        color = previewAccent
                    )
                    Text(
                        text = uiState.bio.trim().ifBlank { stringResource(R.string.auth_default_bio) },
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        // Profile Registration Inputs
        DlTextField(
            value = uiState.displayName,
            onValueChange = onDisplayNameChanged,
            label = stringResource(R.string.auth_display_name_label),
            placeholder = stringResource(R.string.auth_display_name_placeholder),
            leadingIcon = Icons.Filled.Badge,
            modifier = Modifier.testTag("register_display_name_input")
        )

        DlTextField(
            value = uiState.username,
            onValueChange = onUsernameChanged,
            label = stringResource(R.string.auth_username_label),
            placeholder = stringResource(R.string.auth_username_placeholder),
            leadingIcon = Icons.Filled.AlternateEmail,
            supportingText = stringResource(R.string.auth_username_supporting, previewHandle),
            modifier = Modifier.testTag("register_username_input")
        )

        DlTextField(
            value = uiState.bio,
            onValueChange = onBioChanged,
            label = stringResource(R.string.auth_bio_label),
            singleLine = false,
            maxLines = 2,
            modifier = Modifier.testTag("register_bio_input")
        )

        // Accent Color Picker
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.auth_accent_label),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.textSecondary
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AccentColorOptions.forEach { (label, hex) ->
                    val localizedColorName = when (label.lowercase()) {
                        "cyan" -> stringResource(R.string.color_cyan)
                        "indigo" -> stringResource(R.string.color_indigo)
                        "emerald" -> stringResource(R.string.color_emerald)
                        "crimson" -> stringResource(R.string.color_crimson)
                        "amber" -> stringResource(R.string.color_amber)
                        else -> label
                    }
                    val swatch = parseHexColor(hex)
                    val selected = uiState.avatarColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(swatch.copy(alpha = if (tokens.isDark) 0.2f else 0.14f))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) resolveAccessibleAccent(hex, tokens.isDark) else tokens.hairline,
                                shape = CircleShape
                            )
                            .clickable { onAvatarColorSelected(hex) }
                            .testTag("register_color_${label.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(swatch),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.auth_color_selected_cd, localizedColorName),
                                    tint = contrastInkFor(swatch),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Discoverability Toggle
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tokens.surfaceSubtle,
            border = BorderStroke(1.dp, tokens.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auth_discoverable_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.auth_discoverable_desc, previewHandle),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
                Switch(
                    checked = uiState.discoverable,
                    onCheckedChange = onDiscoverableChanged,
                    modifier = Modifier.testTag("register_discoverable_switch")
                )
            }
        }

        val combinedError = uiState.combinedError
        if (combinedError != null) {
            AuthErrorBanner(message = combinedError)
        }

        DlPrimaryButton(
            text = if (uiState.isLoading) {
                stringResource(R.string.auth_creating_account)
            } else {
                stringResource(R.string.auth_google_register_cta)
            },
            onClick = onSubmitRegister,
            enabled = !uiState.isLoading,
            loading = uiState.isLoading,
            icon = Icons.Filled.Shield,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .testTag("register_submit_button")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.auth_provider_note),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.auth_already_have_account),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = onNavigateToLogin,
                modifier = Modifier.testTag("go_to_login_button")
            ) {
                Text(stringResource(R.string.auth_sign_in_cta))
            }
        }
    }
}
