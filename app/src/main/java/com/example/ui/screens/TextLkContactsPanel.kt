package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.TextLkContact
import com.example.core.TextLkGatewayClient
import com.example.core.TextLkResult
import com.example.ui.components.DlFocalCard
import com.example.ui.components.DlPrimaryButton
import com.example.ui.components.DlSecondaryButton
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTextField
import com.example.ui.theme.DlCrimson
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDlTokens
import kotlinx.coroutines.launch

@Composable
fun TextLkContactsDialog(
    initialFirstName: String = "",
    initialLastName: String = "",
    onDismiss: () -> Unit
) {
    val tokens = LocalDlTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = {
            Column {
                Text(stringResource(R.string.textlk_title), color = tokens.textPrimary)
                Text(
                    stringResource(R.string.textlk_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                TextLkContactsPanel(
                    initialFirstName = initialFirstName,
                    initialLastName = initialLastName
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("textlk_close_button")
            ) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
fun TextLkContactsPanel(
    initialFirstName: String = "",
    initialLastName: String = ""
) {
    val tokens = LocalDlTokens.current
    val scope = rememberCoroutineScope()
    val configured = remember { TextLkGatewayClient.isConfigured() }

    var groupId by remember { mutableStateOf(TextLkGatewayClient.defaultGroupId()) }
    var editingUid by remember { mutableStateOf<String?>(null) }
    var phoneInput by remember { mutableStateOf("") }
    var firstNameInput by remember(initialFirstName) { mutableStateOf(initialFirstName) }
    var lastNameInput by remember(initialLastName) { mutableStateOf(initialLastName) }

    var senderIdInput by remember { mutableStateOf(TextLkGatewayClient.defaultSenderId()) }
    var smsRecipientInput by remember { mutableStateOf("") }
    var smsMessageInput by remember { mutableStateOf("") }
    var useHttpEndpoint by remember { mutableStateOf(false) }

    var contacts by remember { mutableStateOf<List<TextLkContact>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(1) }
    var lastPage by remember { mutableIntStateOf(1) }
    var totalContacts by remember { mutableIntStateOf(0) }
    var inspectedContact by remember { mutableStateOf<TextLkContact?>(null) }

    var busy by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun loadPage(page: Int) {
        scope.launch {
            busy = true
            feedbackMessage = null
            when (val res = TextLkGatewayClient.listGroupContacts(groupId, page)) {
                is TextLkResult.Success -> {
                    contacts = res.data.contacts
                    currentPage = res.data.currentPage
                    lastPage = res.data.lastPage
                    totalContacts = res.data.total
                    isError = false
                    feedbackMessage = "Loaded ${res.data.contacts.size} contacts (page ${res.data.currentPage}/${res.data.lastPage})."
                }
                is TextLkResult.NotConfigured -> {
                    isError = true
                    feedbackMessage = res.reason
                }
                is TextLkResult.Failure -> {
                    isError = true
                    feedbackMessage = res.message
                }
            }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("textlk_contacts_panel"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DlFocalCard(accentBorder = if (configured) tokens.statusOnline else tokens.statusAway) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Text.lk Gateway",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.textPrimary
                )
                DlStatusPill(
                    text = if (configured) "CONFIGURED" else "SETUP NEEDED",
                    color = if (configured) tokens.statusOnline else tokens.statusAway
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (configured) {
                    "Connected via Secrets (${TextLkGatewayClient.V3_BASE_URL})"
                } else {
                    stringResource(R.string.textlk_not_configured)
                },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }

        if (!feedbackMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = (if (isError) DlCrimson else tokens.statusOnline).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (isError) DlCrimson else tokens.statusOnline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = feedbackMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) DlCrimson else tokens.statusOnline,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.loading_default), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
        }

        // Group selector & Load All (POST /contacts/{group_id}/all)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tokens.surfaceCard,
            border = BorderStroke(1.dp, tokens.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DlTextField(
                    value = groupId,
                    onValueChange = { groupId = it },
                    label = stringResource(R.string.textlk_group_id_label),
                    modifier = Modifier.testTag("textlk_group_id_input")
                )
                DlSecondaryButton(
                    text = stringResource(R.string.textlk_load_group_cta),
                    icon = Icons.Filled.Refresh,
                    enabled = !busy && groupId.isNotBlank(),
                    onClick = { loadPage(1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("textlk_load_contacts_button")
                )
            }
        }

        // Create / Update Contact Form (POST /store, PATCH /update/{uid})
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tokens.surfaceCard,
            border = BorderStroke(1.dp, tokens.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            if (editingUid == null) R.string.textlk_add_contact_cta else R.string.textlk_edit_contact_cta
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary
                    )
                    if (editingUid != null) {
                        TextButton(onClick = {
                            editingUid = null
                            phoneInput = ""
                            firstNameInput = ""
                            lastNameInput = ""
                        }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                }
                DlTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = stringResource(R.string.textlk_phone_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.testTag("textlk_phone_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DlTextField(
                        value = firstNameInput,
                        onValueChange = { firstNameInput = it },
                        label = stringResource(R.string.textlk_first_name_label),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("textlk_first_name_input")
                    )
                    DlTextField(
                        value = lastNameInput,
                        onValueChange = { lastNameInput = it },
                        label = stringResource(R.string.textlk_last_name_label),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("textlk_last_name_input")
                    )
                }
                DlPrimaryButton(
                    text = stringResource(
                        if (editingUid == null) R.string.textlk_add_contact_cta else R.string.textlk_edit_contact_cta
                    ),
                    icon = if (editingUid == null) Icons.Filled.Add else Icons.Filled.Edit,
                    enabled = !busy && groupId.isNotBlank() && phoneInput.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            feedbackMessage = null
                            val uidToUpdate = editingUid
                            val res = if (uidToUpdate == null) {
                                TextLkGatewayClient.createContact(
                                    groupId = groupId,
                                    phoneRaw = phoneInput,
                                    firstName = firstNameInput,
                                    lastName = lastNameInput
                                )
                            } else {
                                TextLkGatewayClient.updateContact(
                                    groupId = groupId,
                                    contactUid = uidToUpdate,
                                    phoneRaw = phoneInput,
                                    firstName = firstNameInput,
                                    lastName = lastNameInput
                                )
                            }
                            when (res) {
                                is TextLkResult.Success -> {
                                    isError = false
                                    feedbackMessage = if (uidToUpdate == null) {
                                        "Created contact ${res.data.fullName} (${res.data.uid})."
                                    } else {
                                        "Updated contact ${res.data.fullName}."
                                    }
                                    editingUid = null
                                    phoneInput = ""
                                    firstNameInput = ""
                                    lastNameInput = ""
                                    loadPage(currentPage)
                                }
                                is TextLkResult.NotConfigured -> {
                                    isError = true
                                    feedbackMessage = res.reason
                                }
                                is TextLkResult.Failure -> {
                                    isError = true
                                    feedbackMessage = res.message
                                }
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("textlk_save_contact_button")
                )
            }
        }

        // Contacts List + View / Edit / Delete / Quick-SMS
        if (contacts.isEmpty()) {
            Text(
                text = stringResource(R.string.textlk_empty_group),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        } else {
            Text(
                text = stringResource(R.string.textlk_page_indicator, currentPage, lastPage, totalContacts),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textSecondary
            )
            contacts.forEach { contact ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.surfaceElevated,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.fullName, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Text(
                                contact.phone,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                            if (contact.uid.isNotBlank()) {
                                Text("UID: ${contact.uid}", style = MaterialTheme.typography.labelSmall, color = tokens.textTertiary)
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                busy = true
                                when (val res = TextLkGatewayClient.viewContact(groupId, contact.uid)) {
                                    is TextLkResult.Success -> inspectedContact = res.data
                                    is TextLkResult.NotConfigured -> {
                                        isError = true
                                        feedbackMessage = res.reason
                                    }
                                    is TextLkResult.Failure -> {
                                        isError = true
                                        feedbackMessage = res.message
                                    }
                                }
                                busy = false
                            }
                        }) {
                            Icon(Icons.Filled.Visibility, contentDescription = stringResource(R.string.textlk_view_contact_cta))
                        }
                        IconButton(onClick = {
                            editingUid = contact.uid
                            phoneInput = contact.phone
                            firstNameInput = contact.firstName
                            lastNameInput = contact.lastName
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.textlk_edit_contact_cta))
                        }
                        IconButton(onClick = {
                            smsRecipientInput = contact.phone
                        }) {
                            Icon(Icons.Filled.Sms, contentDescription = stringResource(R.string.textlk_send_sms_cta), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                busy = true
                                when (val res = TextLkGatewayClient.deleteContact(groupId, contact.uid)) {
                                    is TextLkResult.Success -> {
                                        isError = false
                                        feedbackMessage = res.data
                                        loadPage(currentPage)
                                    }
                                    is TextLkResult.NotConfigured -> {
                                        isError = true
                                        feedbackMessage = res.reason
                                    }
                                    is TextLkResult.Failure -> {
                                        isError = true
                                        feedbackMessage = res.message
                                    }
                                }
                                busy = false
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete), tint = DlCrimson)
                        }
                    }
                }
            }
            if (lastPage > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = !busy && currentPage > 1,
                        onClick = { loadPage(currentPage - 1) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Prev") }
                    OutlinedButton(
                        enabled = !busy && currentPage < lastPage,
                        onClick = { loadPage(currentPage + 1) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Next") }
                }
            }
        }

        // Send SMS via Text.lk (API v3 or HTTP endpoint)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tokens.surfaceCard,
            border = BorderStroke(1.dp, tokens.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.textlk_send_sms_cta), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DlTextField(
                        value = smsRecipientInput,
                        onValueChange = { smsRecipientInput = it },
                        label = stringResource(R.string.textlk_phone_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("textlk_sms_recipient_input")
                    )
                    DlTextField(
                        value = senderIdInput,
                        onValueChange = { senderIdInput = it.take(11) },
                        label = stringResource(R.string.textlk_sender_id_label),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("textlk_sender_id_input")
                    )
                }
                DlTextField(
                    value = smsMessageInput,
                    onValueChange = { smsMessageInput = it },
                    label = stringResource(R.string.textlk_sms_message_label),
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.testTag("textlk_sms_message_input")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useHttpEndpoint,
                        onCheckedChange = { useHttpEndpoint = it }
                    )
                    Text(
                        text = stringResource(R.string.textlk_use_http_endpoint),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
                DlPrimaryButton(
                    text = stringResource(R.string.textlk_send_sms_cta),
                    icon = Icons.Filled.Sms,
                    enabled = !busy && smsRecipientInput.isNotBlank() && smsMessageInput.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            feedbackMessage = null
                            when (val res = TextLkGatewayClient.sendSms(
                                recipientRaw = smsRecipientInput,
                                message = smsMessageInput,
                                senderId = senderIdInput,
                                useHttpEndpoint = useHttpEndpoint
                            )) {
                                is TextLkResult.Success -> {
                                    isError = false
                                    feedbackMessage = "SMS sent to ${res.data.recipient} (${res.data.endpointMode})."
                                    smsMessageInput = ""
                                }
                                is TextLkResult.NotConfigured -> {
                                    isError = true
                                    feedbackMessage = res.reason
                                }
                                is TextLkResult.Failure -> {
                                    isError = true
                                    feedbackMessage = res.message
                                }
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("textlk_send_sms_button")
                )
            }
        }
    }

    val inspected = inspectedContact
    if (inspected != null) {
        AlertDialog(
            onDismissRequest = { inspectedContact = null },
            containerColor = tokens.surfaceElevated,
            title = { Text(inspected.fullName, color = tokens.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("UID: ${inspected.uid}", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    Text("Group: ${inspected.groupId}", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    Text("Phone: ${inspected.phone}", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    inspected.extraFields.forEach { (k, v) ->
                        Text("$k: $v", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedContact = null }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
}
