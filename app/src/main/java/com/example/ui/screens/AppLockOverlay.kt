package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.BiometricAvailability
import com.example.core.SecurityPrivacyManager
import com.example.core.findFragmentActivity
import com.example.data.model.ChatRoom
import com.example.data.model.PrivateChatSetting
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlFocalCard
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlPrimaryButton
import com.example.ui.components.DlSecondaryButton
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.components.localizedTitle
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

@Composable
fun AppLockOverlay(
    uid: String,
    displayName: String,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    val activity = context.findFragmentActivity()
    val hasPin = remember(uid) { SecurityPrivacyManager.hasPinSet(context, uid) }
    val bioAvail = remember(context) { SecurityPrivacyManager.checkBiometricAvailability(context) }

    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .testTag("app_lock_overlay"),
        contentAlignment = Alignment.Center
    ) {
        DlFocalCard(
            modifier = Modifier.widthIn(max = 440.dp),
            accentBorder = MaterialTheme.colorScheme.primary
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.lock_title),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineMedium, color = tokens.textPrimary)
                Text(
                    stringResource(R.string.lock_subtitle, displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    textAlign = TextAlign.Center
                )

                if (hasPin) {
                    DlTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8 && it.all { ch -> ch.isDigit() }) pinInput = it },
                        label = stringResource(R.string.lock_pin_label),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.testTag("app_lock_pin_input")
                    )

                    if (errorMsg != null) {
                        Text(errorMsg.orEmpty(), color = tokens.statusBusy, style = MaterialTheme.typography.bodySmall)
                    }

                    DlPrimaryButton(
                        text = stringResource(R.string.lock_unlock_pin_btn),
                        icon = Icons.Filled.LockOpen,
                        enabled = pinInput.length >= 4,
                        onClick = {
                            if (SecurityPrivacyManager.verifyPin(context, uid, pinInput)) {
                                SecurityPrivacyManager.clearBackgroundTimestamp(context, uid)
                                pinInput = ""
                                errorMsg = null
                                onUnlocked()
                            } else {
                                errorMsg = context.getString(R.string.lock_wrong_pin)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_lock_unlock_button")
                    )
                } else {
                    DlPrimaryButton(
                        text = stringResource(R.string.lock_continue_no_pin),
                        onClick = onUnlocked,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (biometricEnabled && bioAvail == BiometricAvailability.AVAILABLE && activity != null) {
                    DlSecondaryButton(
                        text = stringResource(R.string.lock_unlock_bio_btn),
                        icon = Icons.Filled.Fingerprint,
                        onClick = {
                            SecurityPrivacyManager.authenticateWithBiometrics(
                                activity = activity,
                                title = context.getString(R.string.lock_bio_prompt_title),
                                subtitle = context.getString(R.string.lock_bio_prompt_sub),
                                failedMessage = context.getString(R.string.lock_bio_no_match),
                                onSuccess = {
                                    SecurityPrivacyManager.clearBackgroundTimestamp(context, uid)
                                    onUnlocked()
                                },
                                onError = { msg -> errorMsg = msg }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TextButton(onClick = onSignOut) {
                    Text(stringResource(R.string.lock_switch_sign_out), color = tokens.statusBusy)
                }
            }
        }
    }
}

@Composable
fun PinSetupDialog(
    uid: String,
    hasExistingPin: Boolean,
    onDismiss: () -> Unit,
    onPinSaved: () -> Unit,
    onPinCleared: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (hasExistingPin) R.string.pin_dialog_update_title else R.string.pin_dialog_set_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.pin_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                DlTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
                    label = stringResource(R.string.pin_new_label),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                DlTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = stringResource(R.string.pin_confirm_label),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (err != null) {
                    Text(err.orEmpty(), color = tokens.statusBusy, style = MaterialTheme.typography.bodySmall)
                }
                if (hasExistingPin) {
                    TextButton(onClick = {
                        SecurityPrivacyManager.clearPin(context, uid)
                        onPinCleared()
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.pin_remove_btn), color = tokens.statusBusy)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.length !in 4..8 || !newPin.all { it.isDigit() }) {
                        err = context.getString(R.string.pin_invalid_error)
                    } else if (newPin != confirmPin) {
                        err = context.getString(R.string.pin_mismatch_error)
                    } else {
                        SecurityPrivacyManager.setPin(context, uid, newPin)
                            .onSuccess {
                                onPinSaved()
                                onDismiss()
                            }
                            .onFailure { e -> err = e.message }
                    }
                }
            ) { Text(stringResource(R.string.pin_save_cta)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenChatsVaultSheet(
    myUid: String,
    chats: List<ChatRoom>,
    privateSettings: Map<String, PrivateChatSetting>,
    onOpenChat: (String) -> Unit,
    onUnhideChat: (PrivateChatSetting) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalDlTokens.current
    val protectedChats = chats.filter {
        val st = privateSettings[it.id]
        st?.hidden == true || st?.locked == true
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VisibilityOff, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vault_title), style = MaterialTheme.typography.titleLarge)
            }
            if (protectedChats.isEmpty()) {
                Text(
                    stringResource(R.string.vault_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = tokens.surfaceSubtle,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = DlSpacing.lg)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        items(protectedChats, key = { it.id }) { chat ->
                            val st = privateSettings[chat.id]
                            val chatTitle = chat.localizedTitle(myUid)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenChat(chat.id)
                                        onDismiss()
                                    }
                            ) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DlAvatar(name = chatTitle, size = 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(chatTitle, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.height(2.dp))
                                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (st?.hidden == true) DlStatusPill(text = stringResource(R.string.vault_pill_hidden))
                                            if (st?.locked == true) DlStatusPill(text = stringResource(R.string.vault_pill_locked))
                                        }
                                    }
                                    if (st != null && st.hidden) {
                                        TextButton(onClick = { onUnhideChat(st) }) {
                                            Text(stringResource(R.string.vault_unhide_btn))
                                        }
                                    }
                                }
                                DlHairlineDivider(modifier = Modifier.padding(start = 64.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
