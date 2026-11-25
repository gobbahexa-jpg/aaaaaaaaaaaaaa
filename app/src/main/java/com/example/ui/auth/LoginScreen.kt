package com.example.ui.auth

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.CountryDialCode
import com.example.core.DeathLegionOAuthClient
import com.example.core.PhoneNumberHelper
import com.example.data.model.OtpChannel
import com.example.ui.components.DlFocalCard
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlPrimaryButton
import com.example.ui.components.DlSearchField
import com.example.ui.components.DlSecondaryButton
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.screens.DraftPoliciesDialog
import com.example.ui.theme.DlRadius
import com.example.ui.theme.DlSizing
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDlTokens

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onGoogleLoginClick: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    onDeathLegionStatus: (String) -> Unit = {},
    onSelectCountry: (CountryDialCode) -> Unit = {},
    onPhoneInputChanged: (String) -> Unit = {},
    onOtpCodeChanged: (String) -> Unit = {},
    onSelectOtpChannel: (OtpChannel) -> Unit = {},
    onWhatsAppOptInChanged: (Boolean) -> Unit = {},
    onSendOtpClick: () -> Unit = {},
    onResendOtpClick: () -> Unit = {},
    onVerifyOtpClick: () -> Unit = {},
    onEditPhoneClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    var showLegionOAuthDialog by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showDraftPoliciesDialog by remember { mutableStateOf(false) }
    val isLoading = uiState.isLoading
    val isCheckingAuto = uiState.isCheckingAuto
    val errorMessage = uiState.errorMessage

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("login_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Standalone Hero Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(DlRadius.hero))
                .border(DlSizing.divider, tokens.hairline, RoundedCornerShape(DlRadius.hero))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_dl_hero),
                contentDescription = stringResource(R.string.auth_banner_cd),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                tokens.overlayBackdrop.copy(alpha = 0.14f),
                                tokens.overlayBackdrop.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(DlSpacing.lg)
            ) {
                Text(
                    text = stringResource(R.string.auth_brand_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.overlayAccent
                )
                Spacer(modifier = Modifier.height(DlSpacing.xs))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = tokens.overlayInk
                )
                Spacer(modifier = Modifier.height(DlSpacing.xxs))
                Text(
                    text = stringResource(R.string.auth_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.overlayInkMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(DlSpacing.base))

        if (errorMessage != null) {
            AuthErrorBanner(message = errorMessage)
        }
        if (!uiState.infoMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = DlSpacing.md),
                shape = RoundedCornerShape(DlRadius.md),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(DlSizing.divider, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Text(
                    text = uiState.infoMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Primary Sign-In Method: Phone Number + OTP Verification
        DlFocalCard(
            accentBorder = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("phone_otp_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.auth_phone_primary_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    )
                }
                DlStatusPill(
                    text = stringResource(R.string.auth_phone_primary_badge),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(DlSpacing.xs))
            Text(
                text = stringResource(R.string.auth_phone_primary_sub),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
            Spacer(modifier = Modifier.height(DlSpacing.md))

            if (uiState.otpStep == PhoneOtpStep.ENTER_PHONE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedButton(
                        onClick = { showCountryPicker = true },
                        enabled = !isLoading && !isCheckingAuto,
                        shape = RoundedCornerShape(DlRadius.md),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 56.dp)
                            .testTag("phone_country_button")
                    ) {
                        Text(
                            text = "${uiState.selectedCountry.flagEmoji} ${uiState.selectedCountry.dialCode}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = stringResource(R.string.auth_phone_pick_country_cd)
                        )
                    }

                    OutlinedTextField(
                        value = uiState.phoneInput,
                        onValueChange = onPhoneInputChanged,
                        enabled = !isLoading && !isCheckingAuto,
                        label = { Text(stringResource(R.string.auth_phone_input_label)) },
                        placeholder = { Text(uiState.selectedCountry.exampleLocal) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(DlRadius.md),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("phone_number_input")
                    )
                }

                if (!uiState.e164Preview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.auth_phone_e164_preview,
                            PhoneNumberHelper.formatReadable(uiState.e164Preview)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = tokens.statusOnline
                    )
                }

                if (uiState.whatsappOtpAvailable) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = uiState.selectedOtpChannel == OtpChannel.FIREBASE_SMS,
                            onClick = { onSelectOtpChannel(OtpChannel.FIREBASE_SMS) },
                            label = { Text(OtpChannel.FIREBASE_SMS.label) },
                            leadingIcon = { Icon(Icons.Filled.Sms, null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = uiState.selectedOtpChannel == OtpChannel.WHATSAPP_TEMPLATE,
                            onClick = { onSelectOtpChannel(OtpChannel.WHATSAPP_TEMPLATE) },
                            label = { Text(OtpChannel.WHATSAPP_TEMPLATE.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWhatsAppOptInChanged(!uiState.whatsappOptIn) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.whatsappOptIn,
                        onCheckedChange = onWhatsAppOptInChanged,
                        modifier = Modifier.testTag("whatsapp_support_opt_in_checkbox")
                    )
                    Text(
                        text = stringResource(R.string.auth_whatsapp_support_optin),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                DlPrimaryButton(
                    text = if (isLoading) stringResource(R.string.auth_phone_sending_otp)
                    else stringResource(R.string.auth_phone_send_otp_cta),
                    onClick = onSendOtpClick,
                    enabled = !isLoading && !isCheckingAuto && uiState.phoneInput.isNotBlank(),
                    loading = isLoading,
                    icon = Icons.Filled.Sms,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .testTag("send_otp_button")
                )
            } else {
                val targetLabel = uiState.e164Preview?.let { PhoneNumberHelper.formatReadable(it) }
                    ?: "${uiState.selectedCountry.dialCode} ${uiState.phoneInput}"
                Text(
                    text = stringResource(R.string.auth_phone_verify_hint, targetLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.otpCode,
                    onValueChange = onOtpCodeChanged,
                    enabled = !isLoading,
                    label = { Text(stringResource(R.string.auth_otp_code_label)) },
                    placeholder = { Text("123456") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(DlRadius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("otp_code_input")
                )

                Spacer(modifier = Modifier.height(10.dp))
                DlPrimaryButton(
                    text = if (isLoading) stringResource(R.string.auth_signing_in)
                    else stringResource(R.string.auth_phone_verify_cta),
                    onClick = onVerifyOtpClick,
                    enabled = !isLoading && uiState.otpCode.length >= 6,
                    loading = isLoading,
                    icon = Icons.Filled.Check,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .testTag("verify_otp_button")
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onEditPhoneClick,
                        enabled = !isLoading,
                        modifier = Modifier.testTag("edit_phone_button")
                    ) {
                        Text(stringResource(R.string.auth_phone_change_number))
                    }
                    TextButton(
                        onClick = onResendOtpClick,
                        enabled = !isLoading && uiState.resendSecondsRemaining == 0,
                        modifier = Modifier.testTag("resend_otp_button")
                    ) {
                        Text(
                            if (uiState.resendSecondsRemaining > 0) {
                                stringResource(R.string.auth_phone_resend_wait, uiState.resendSecondsRemaining)
                            } else {
                                stringResource(R.string.auth_phone_resend_now)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(DlSpacing.base))

        // Secondary sign-in divider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DlHairlineDivider(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.auth_other_ways_label),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textSecondary
            )
            DlHairlineDivider(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(DlSpacing.md))

        DlSecondaryButton(
            text = when {
                isCheckingAuto -> stringResource(R.string.auth_checking_session)
                isLoading -> stringResource(R.string.auth_signing_in)
                else -> stringResource(R.string.auth_google_login_cta)
            },
            onClick = onGoogleLoginClick,
            enabled = !isLoading && !isCheckingAuto,
            icon = Icons.Filled.Shield,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DlSizing.touchTarget)
                .testTag("google_sign_in_button")
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { showLegionOAuthDialog = true },
            enabled = !isLoading && !isCheckingAuto,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DlSizing.touchTarget)
                .testTag("death_legion_oauth_button"),
            shape = RoundedCornerShape(DlRadius.md),
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.brandLegion,
                contentColor = tokens.onBrandLegion
            )
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(DlSizing.iconSm))
            Spacer(modifier = Modifier.width(DlSpacing.sm))
            Text(
                text = stringResource(R.string.auth_legion_cta),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        DlSecondaryButton(
            text = stringResource(R.string.auth_new_user_cta),
            onClick = onNavigateToRegister,
            enabled = !isLoading && !isCheckingAuto,
            icon = Icons.Filled.PersonAdd,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("go_to_register_button")
        )

        Spacer(modifier = Modifier.height(DlSpacing.base))

        // Architectural capability summary
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DlRadius.lg),
            color = tokens.surfaceSubtle,
            border = BorderStroke(DlSizing.divider, tokens.hairline)
        ) {
            Column {
                AuthCapabilityRow(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.auth_cap_security_title),
                    subtitle = stringResource(R.string.auth_cap_security_desc)
                )
                DlHairlineDivider(modifier = Modifier.padding(start = 58.dp))
                AuthCapabilityRow(
                    icon = Icons.Filled.Forum,
                    title = stringResource(R.string.auth_cap_chats_title),
                    subtitle = stringResource(R.string.auth_cap_chats_desc)
                )
            }
        }

        Spacer(modifier = Modifier.height(DlSpacing.md))

        TextButton(
            onClick = { showDraftPoliciesDialog = true },
            modifier = Modifier.testTag("open_draft_policies_button")
        ) {
            Icon(Icons.Filled.Policy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.auth_policies_footer_cta))
        }

        Text(
            text = stringResource(R.string.auth_provider_note),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = DlSpacing.sm)
        )
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            selectedCountry = uiState.selectedCountry,
            onSelect = {
                onSelectCountry(it)
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false }
        )
    }

    if (showDraftPoliciesDialog) {
        DraftPoliciesDialog(onDismiss = { showDraftPoliciesDialog = false })
    }

    if (showLegionOAuthDialog) {
        val initialCfg = remember { DeathLegionOAuthClient.getConfig(context) }
        var clientId by remember { mutableStateOf(initialCfg.clientId) }
        var proxyUrl by remember { mutableStateOf(initialCfg.tokenExchangeProxyUrl) }
        var dialogError by remember { mutableStateOf<String?>(null) }
        val exchangeReady = clientId.isNotBlank() && (proxyUrl.isNotBlank() || initialCfg.hasConfiguredClientSecret)

        AlertDialog(
            onDismissRequest = { showLegionOAuthDialog = false },
            title = { Text(stringResource(R.string.auth_legion_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(DlRadius.sm),
                        color = tokens.brandLegion.copy(alpha = 0.14f),
                        border = BorderStroke(DlSizing.divider, tokens.brandLegion.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.legion_app_badge),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.brandLegion
                            )
                            Text(
                                text = stringResource(R.string.legion_callback_label, initialCfg.redirectUri),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textPrimary
                            )
                            Text(
                                text = stringResource(
                                    if (exchangeReady) R.string.legion_secret_configured
                                    else R.string.legion_secret_missing_hint
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (exchangeReady) tokens.statusOnline else tokens.textSecondary
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.auth_legion_dialog_desc, DeathLegionOAuthClient.BASE_URL),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                    DlTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = stringResource(R.string.auth_legion_client_id_label)
                    )
                    DlTextField(
                        value = proxyUrl,
                        onValueChange = { proxyUrl = it },
                        label = stringResource(R.string.auth_legion_proxy_label)
                    )
                    Text(
                        stringResource(R.string.auth_legion_redirect_hint, DeathLegionOAuthClient.DEFAULT_REDIRECT_URI),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (dialogError != null) {
                        Text(
                            dialogError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    DeathLegionOAuthClient.saveConfig(context, clientId, proxyUrl)
                    DeathLegionOAuthClient.launchAuthorizeInBrowser(context)
                        .onSuccess {
                            showLegionOAuthDialog = false
                            onDeathLegionStatus(context.getString(R.string.auth_legion_launched_notice))
                        }
                        .onFailure { err ->
                            dialogError = err.message ?: context.getString(R.string.auth_legion_launch_error)
                        }
                }) {
                    Text(stringResource(R.string.auth_legion_authorize_cta))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLegionOAuthDialog = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }
}

@Composable
fun CountryPickerDialog(
    selectedCountry: CountryDialCode,
    onSelect: (CountryDialCode) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalDlTokens.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) { PhoneNumberHelper.searchCountries(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = { Text(stringResource(R.string.auth_phone_country_dialog_title), color = tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DlSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.auth_phone_country_search_hint),
                    modifier = Modifier.testTag("country_search_input")
                )
                LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.isoCode }) { country ->
                        val isSelected = country.isoCode == selectedCountry.isoCode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else tokens.surfaceCard,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else tokens.hairline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(country) }
                                .testTag("country_option_${country.isoCode}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(country.flagEmoji, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = country.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = country.dialCode,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

@Composable
internal fun AuthErrorBanner(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = DlSpacing.base)
            .testTag("auth_error_banner"),
        shape = RoundedCornerShape(DlRadius.md),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(DlSizing.divider, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(14.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AuthCapabilityRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val tokens = LocalDlTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DlSpacing.base, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(DlSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
    }
}
