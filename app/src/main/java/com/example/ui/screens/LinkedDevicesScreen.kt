package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.QrCodeUtils
import com.example.data.model.DeviceKeyBundle
import com.example.data.model.DeviceNotification
import com.example.data.model.DevicePairingSession
import com.example.data.model.DeviceSession
import com.example.data.repository.CreatedPairingChallenge
import com.example.ui.components.DlFocalCard
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlPrimaryButton
import com.example.ui.components.DlSecondaryButton
import com.example.ui.components.DlSectionHeader
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.components.QrScannerDialog
import com.example.ui.components.formatDuration
import com.example.ui.components.formatRelativeTimeLocalized
import com.example.ui.theme.DlRadius
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens
import kotlinx.coroutines.delay

@Composable
fun LinkedDevicesSection(
    currentDeviceId: String,
    devices: List<DeviceSession>,
    pairingSessions: List<DevicePairingSession>,
    deviceKeyBundles: List<DeviceKeyBundle>,
    deviceNotifications: List<DeviceNotification>,
    activeChallenge: CreatedPairingChallenge?,
    onGeneratePairingQr: (deviceName: String, deviceType: String, osName: String, browserName: String) -> Unit,
    onCancelPairing: (sessionId: String) -> Unit,
    onConfirmScannedQr: (String) -> Unit,
    onConfirmManualPairing: (sessionId: String, rawToken: String) -> Unit,
    onRenameDevice: (deviceId: String, newName: String) -> Unit,
    onRotateDeviceToken: (DeviceSession) -> Unit,
    onRevokeDevice: (String) -> Unit,
    onRevokeAllOtherDevices: () -> Unit,
    onMarkNotificationRead: (String) -> Unit
) {
    val tokens = LocalDlTokens.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var showCreateQrDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showManualPairDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DeviceSession?>(null) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeChallenge?.sessionId) {
        if (activeChallenge != null) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val thisDevice = devices.firstOrNull { it.id == currentDeviceId }
    val otherDevices = devices.filter { it.id != currentDeviceId }

    Column(verticalArrangement = Arrangement.spacedBy(DlSpacing.base)) {
        // Focal Pairing Hub
        DlFocalCard(accentBorder = MaterialTheme.colorScheme.primary) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Devices, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.devices_hub_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.devices_hub_desc),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DlPrimaryButton(
                    text = stringResource(R.string.devices_link_qr_btn),
                    icon = Icons.Filled.QrCode2,
                    onClick = { showCreateQrDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("generate_pairing_qr_button")
                )
                DlSecondaryButton(
                    text = stringResource(R.string.devices_scan_qr_btn),
                    icon = Icons.Filled.QrCodeScanner,
                    onClick = { showScannerDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_pairing_qr_button")
                )
            }

            TextButton(
                onClick = { showManualPairDialog = true },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.devices_manual_token_btn))
            }

            if (activeChallenge != null) {
                val remainingSec = ((activeChallenge.expiresAtEpochMs - nowMs) / 1000L).coerceAtLeast(0L)
                val qrBmp = remember(activeChallenge.qrUri) {
                    QrCodeUtils.generateQrBitmap(activeChallenge.qrUri, sizePx = 420)
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (remainingSec > 0L) tokens.statusOnline.copy(alpha = 0.5f) else tokens.statusBusy.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_pairing_qr_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DlStatusPill(
                            text = if (remainingSec > 0L) {
                                stringResource(R.string.devices_expires_in, formatDuration(remainingSec))
                            } else {
                                stringResource(R.string.common_expired)
                            },
                            color = if (remainingSec > 0L) tokens.statusOnline else tokens.statusBusy
                        )
                        if (qrBmp != null && remainingSec > 0L) {
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(DlRadius.md))
                                    .background(tokens.qrCanvas)
                                    .padding(DlSpacing.sm),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.devices_qr_cd),
                                    modifier = Modifier.size(164.dp)
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.devices_session_id_prefix, activeChallenge.sessionId.take(12)),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(activeChallenge.qrUri)) }) {
                                Text(stringResource(R.string.devices_copy_qr_uri))
                            }
                            TextButton(onClick = { onCancelPairing(activeChallenge.sessionId) }) {
                                Text(stringResource(R.string.common_cancel), color = tokens.statusBusy)
                            }
                        }
                    }
                }
            }
        }

        // Current Device Summary (clean separation)
        if (thisDevice != null) {
            DlSectionHeader(title = stringResource(R.string.devices_current_header))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tokens.surfaceSubtle,
                border = BorderStroke(1.dp, tokens.hairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("linked_device_item_${thisDevice.id}")
            ) {
                DeviceSessionRowContent(
                    dev = thisDevice,
                    isThisDevice = true,
                    onRename = { renameTarget = thisDevice },
                    onRotate = {},
                    onRevoke = {}
                )
            }
        }

        // Connected Companion Devices List
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DlSectionHeader(
                    title = stringResource(R.string.devices_connected_header, otherDevices.size),
                    modifier = Modifier.weight(1f)
                )
                val activeOthers = otherDevices.count { !it.revoked }
                if (activeOthers > 0) {
                    TextButton(
                        onClick = onRevokeAllOtherDevices,
                        modifier = Modifier.testTag("revoke_all_other_devices_button")
                    ) {
                        Icon(Icons.Filled.Logout, null, Modifier.size(16.dp), tint = tokens.statusBusy)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.devices_logout_others), color = tokens.statusBusy)
                    }
                }
            }

            if (otherDevices.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = tokens.surfaceSubtle,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.devices_none_linked),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(DlSpacing.base)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = tokens.surfaceSubtle,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        otherDevices.forEachIndexed { idx, dev ->
                            Column(modifier = Modifier.testTag("linked_device_item_${dev.id}")) {
                                DeviceSessionRowContent(
                                    dev = dev,
                                    isThisDevice = false,
                                    onRename = { renameTarget = dev },
                                    onRotate = { onRotateDeviceToken(dev) },
                                    onRevoke = { onRevokeDevice(dev.id) }
                                )
                                if (idx < otherDevices.lastIndex) {
                                    DlHairlineDivider()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Per-Device Public Key Bundles & Security Events
        DlSectionHeader(title = stringResource(R.string.devices_security_header))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tokens.surfaceSubtle,
            border = BorderStroke(1.dp, tokens.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(DlSpacing.base),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, null, tint = tokens.statusOnline, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.devices_keys_title, deviceKeyBundles.size), style = MaterialTheme.typography.titleMedium)
                }
                if (deviceKeyBundles.isEmpty()) {
                    Text(
                        stringResource(R.string.devices_keys_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                } else {
                    deviceKeyBundles.forEach { kb ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(kb.deviceId, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "v${kb.keyVersion} · ${kb.fingerprint}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textSecondary
                                )
                            }
                            DlStatusPill(
                                text = stringResource(if (kb.revoked) R.string.common_revoked else R.string.common_active),
                                color = if (kb.revoked) tokens.statusBusy else tokens.statusOnline
                            )
                        }
                    }
                }

                DlHairlineDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.devices_alerts_title, deviceNotifications.size), style = MaterialTheme.typography.titleMedium)
                }
                if (deviceNotifications.isEmpty()) {
                    Text(
                        stringResource(R.string.devices_alerts_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                } else {
                    deviceNotifications.take(8).forEach { n ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (n.read) tokens.surfaceElevated else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (!n.read) onMarkNotificationRead(n.id) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${n.title} · ${formatRelativeTimeLocalized(n.createdAt)}", style = MaterialTheme.typography.labelMedium)
                                Text(n.body, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            }
                        }
                    }
                }

                if (pairingSessions.isNotEmpty()) {
                    DlHairlineDivider()
                    Text(stringResource(R.string.devices_recent_pairings), style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
                    pairingSessions.take(5).forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${s.targetDeviceName} (${localizedDeviceType(s.targetDeviceType)}) · ${formatRelativeTimeLocalized(s.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            val localizedPairStatus = when (s.status.uppercase()) {
                                "APPROVED", "ACTIVE" -> stringResource(R.string.common_active)
                                "EXPIRED" -> stringResource(R.string.common_expired)
                                "CANCELLED" -> stringResource(R.string.common_cancel)
                                else -> stringResource(R.string.common_pending)
                            }
                            DlStatusPill(
                                text = localizedPairStatus,
                                color = when (s.status) {
                                    "APPROVED", "ACTIVE" -> tokens.statusOnline
                                    "EXPIRED", "CANCELLED" -> tokens.statusBusy
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateQrDialog) {
        var devName by remember { mutableStateOf("DL Workstation") }
        var devType by remember { mutableStateOf("DESKTOP") }
        var osName by remember { mutableStateOf("Windows 11") }
        var browserName by remember { mutableStateOf("Chrome") }

        AlertDialog(
            onDismissRequest = { showCreateQrDialog = false },
            title = { Text(stringResource(R.string.devices_qr_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.devices_qr_platform_label), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "DESKTOP" to stringResource(R.string.devices_type_desktop),
                            "WEB" to stringResource(R.string.devices_type_web),
                            "TABLET" to stringResource(R.string.devices_type_tablet),
                            "ANDROID" to stringResource(R.string.devices_type_android)
                        ).forEach { (t, label) ->
                            FilterChip(
                                selected = devType == t,
                                onClick = {
                                    devType = t
                                    when (t) {
                                        "WEB" -> { devName = "DL Web Browser"; osName = "Web"; browserName = "Chrome" }
                                        "TABLET" -> { devName = "DL Tablet"; osName = "Android Tablet"; browserName = "" }
                                        "ANDROID" -> { devName = "Secondary Android"; osName = "Android"; browserName = "" }
                                        else -> { devName = "DL Desktop"; osName = "Windows 11"; browserName = "Desktop App" }
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    DlTextField(value = devName, onValueChange = { devName = it }, label = stringResource(R.string.devices_name_label))
                    DlTextField(value = osName, onValueChange = { osName = it }, label = stringResource(R.string.devices_os_label))
                    DlTextField(value = browserName, onValueChange = { browserName = it }, label = stringResource(R.string.devices_browser_label))
                }
            },
            confirmButton = {
                Button(onClick = {
                    onGeneratePairingQr(devName, devType, osName, browserName)
                    showCreateQrDialog = false
                }) { Text(stringResource(R.string.devices_generate_cta)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateQrDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showScannerDialog) {
        QrScannerDialog(
            title = stringResource(R.string.devices_scanner_title),
            hint = stringResource(R.string.devices_scanner_hint),
            onDismiss = { showScannerDialog = false },
            onQrScanned = onConfirmScannedQr
        )
    }

    if (showManualPairDialog) {
        var sessionId by remember { mutableStateOf("") }
        var rawToken by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualPairDialog = false },
            title = { Text(stringResource(R.string.devices_manual_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DlTextField(value = sessionId, onValueChange = { sessionId = it }, label = stringResource(R.string.devices_manual_session_label))
                    DlTextField(value = rawToken, onValueChange = { rawToken = it }, label = stringResource(R.string.devices_manual_token_label))
                }
            },
            confirmButton = {
                Button(
                    enabled = sessionId.isNotBlank() && rawToken.isNotBlank(),
                    onClick = {
                        onConfirmManualPairing(sessionId, rawToken)
                        showManualPairDialog = false
                    }
                ) { Text(stringResource(R.string.devices_authorize_cta)) }
            },
            dismissButton = {
                TextButton(onClick = { showManualPairDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (renameTarget != null) {
        val target = renameTarget!!
        var newName by remember(target.id) { mutableStateOf(target.deviceName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.devices_rename_dialog_title)) },
            text = {
                DlTextField(value = newName, onValueChange = { newName = it }, label = stringResource(R.string.devices_name_label))
            },
            confirmButton = {
                Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onRenameDevice(target.id, newName)
                        renameTarget = null
                    }
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun localizedDeviceType(rawType: String): String = when (rawType.uppercase()) {
    "DESKTOP" -> stringResource(R.string.devices_type_desktop)
    "WEB" -> stringResource(R.string.devices_type_web)
    "TABLET" -> stringResource(R.string.devices_type_tablet)
    else -> stringResource(R.string.devices_type_android)
}

@Composable
private fun DeviceSessionRowContent(
    dev: DeviceSession,
    isThisDevice: Boolean,
    onRename: () -> Unit,
    onRotate: () -> Unit,
    onRevoke: () -> Unit
) {
    val tokens = LocalDlTokens.current
    val icon = when (dev.deviceType) {
        "TABLET" -> Icons.Filled.TabletAndroid
        "DESKTOP" -> Icons.Filled.Computer
        "WEB" -> Icons.Filled.Language
        else -> Icons.Filled.PhoneAndroid
    }
    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (dev.revoked) tokens.statusBusy else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dev.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    DlStatusPill(
                        text = when {
                            dev.revoked -> stringResource(R.string.common_revoked)
                            isThisDevice -> stringResource(R.string.devices_this_device_pill)
                            dev.connectionStatus == "ONLINE" -> stringResource(R.string.status_online)
                            else -> stringResource(R.string.status_offline)
                        },
                        color = when {
                            dev.revoked -> tokens.statusBusy
                            isThisDevice || dev.connectionStatus == "ONLINE" -> tokens.statusOnline
                            else -> tokens.textSecondary
                        }
                    )
                }
                val typeLabel = localizedDeviceType(dev.deviceType)
                val platformLine = buildString {
                    append(typeLabel)
                    if (dev.osName.isNotBlank()) append(" · ${dev.osName}")
                    if (dev.browserName.isNotBlank()) append(" (${dev.browserName})")
                }
                Text(platformLine, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
            if (!dev.revoked) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.testTag("rename_device_${dev.id}")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.devices_rename_cd), modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(
            text = stringResource(
                R.string.devices_meta_line,
                formatRelativeTimeLocalized(dev.firstLinkedAt),
                formatRelativeTimeLocalized(dev.lastActiveAt),
                dev.tokenRotationCount
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (dev.revoked) tokens.statusBusy else tokens.textTertiary
        )
        if (!dev.revoked && !isThisDevice) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRotate) {
                    Icon(Icons.Filled.Key, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.devices_rotate_btn))
                }
                Button(
                    onClick = onRevoke,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.devices_revoke_btn))
                }
            }
        }
    }
}
