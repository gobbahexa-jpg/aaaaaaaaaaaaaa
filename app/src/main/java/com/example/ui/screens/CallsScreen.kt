package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.CallAudioRoute
import com.example.core.CallAudioRouter
import com.example.core.findActivity
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.DlAvatar
import com.example.ui.components.DlEmptyState
import com.example.ui.components.DlErrorState
import com.example.ui.components.DlHairlineDivider
import com.example.ui.components.DlLoadingState
import com.example.ui.components.DlSectionHeader
import com.example.ui.components.DlStatusPill
import com.example.ui.components.DlTopBar
import com.example.ui.components.formatDuration
import com.example.ui.components.formatRelativeTimeLocalized
import com.example.ui.theme.DlCrimson
import com.example.ui.theme.DlEmerald
import com.example.ui.theme.DlRadius
import com.example.ui.theme.DlSizing
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens
import com.example.ui.theme.contrastInkFor
import kotlinx.coroutines.delay

@Composable
fun CallsScreen(
    myUid: String,
    callsState: UiState<List<CallSession>>,
    allUsers: List<UserProfile>,
    onStartCall: (UserProfile, String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    restrictionNotice: String? = null,
    onOpenAccountStatus: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val tokens = LocalDlTokens.current
    var pendingCall by remember { mutableStateOf<Pair<UserProfile, String>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val req = pendingCall
        pendingCall = null
        if (req != null && grants[Manifest.permission.RECORD_AUDIO] == true) {
            onStartCall(req.first, req.second)
        }
    }

    fun launchWithPermissions(user: UserProfile, callType: String) {
        val perms = if (callType == "VIDEO") {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            onStartCall(user, callType)
        } else {
            pendingCall = user to callType
            permissionLauncher.launch(perms)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DlTopBar(
            title = stringResource(R.string.calls_title),
            subtitle = stringResource(R.string.calls_subtitle),
            actions = {
                if (onOpenSettings != null) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("tab_settings")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.shell_settings_cd))
                    }
                }
            }
        )
        if (!restrictionNotice.isNullOrBlank()) {
            ContextualRestrictionBanner(
                message = restrictionNotice,
                onOpenAccountStatus = onOpenAccountStatus
            )
        }

        val others = allUsers.filter { it.userId != myUid }
        if (others.isNotEmpty()) {
            DlSectionHeader(title = stringResource(R.string.calls_quick_dial))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = DlSpacing.base, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(DlSpacing.md)
            ) {
                others.take(6).forEach { peer ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.surfaceSubtle,
                        border = BorderStroke(1.dp, tokens.hairline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DlAvatar(
                                name = peer.displayName,
                                colorHex = peer.avatarColor,
                                photoBase64 = peer.photoDataBase64,
                                onlineStatus = peer.onlineStatus,
                                size = 38.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(peer.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "@${peer.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = { launchWithPermissions(peer, "VOICE") }) {
                                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.calls_voice_cd), tint = tokens.statusOnline)
                            }
                            IconButton(onClick = { launchWithPermissions(peer, "VIDEO") }) {
                                Icon(Icons.Filled.Videocam, contentDescription = stringResource(R.string.calls_video_cd), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(DlSpacing.sm))
            DlHairlineDivider()
        }

        DlSectionHeader(title = stringResource(R.string.calls_recent))

        when (callsState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DlLoadingState(message = stringResource(R.string.calls_loading))
            }
            is UiState.Error -> DlErrorState(
                title = stringResource(R.string.calls_error_title),
                message = callsState.message
            )
            is UiState.Success -> {
                if (callsState.data.isEmpty()) {
                    DlEmptyState(
                        icon = Icons.Filled.Call,
                        title = stringResource(R.string.calls_empty_title),
                        subtitle = stringResource(R.string.calls_empty_subtitle)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(callsState.data, key = { it.id }) { call ->
                            val isOutgoing = call.callerId == myUid
                            val partnerName = if (isOutgoing) call.calleeName else call.callerName
                            val isMissedOrDeclined = call.status == "MISSED" || call.status == "DECLINED"
                            val directionIcon = when {
                                isMissedOrDeclined -> Icons.AutoMirrored.Filled.CallMissed
                                isOutgoing -> Icons.AutoMirrored.Filled.CallMade
                                else -> Icons.AutoMirrored.Filled.CallReceived
                            }
                            val directionTint = if (isMissedOrDeclined) tokens.statusBusy else tokens.statusOnline
                            val directionText = stringResource(
                                if (isOutgoing) R.string.calls_outgoing else R.string.calls_incoming
                            )
                            val callStatusText = localizedCallStatus(call.status)
                            val callTypeText = localizedCallType(call.callType)

                            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = DlSpacing.base, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DlAvatar(name = partnerName, size = 44.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (call.groupCall) stringResource(R.string.calls_group_suffix, partnerName) else partnerName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isMissedOrDeclined) tokens.statusBusy else tokens.textPrimary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            DlStatusPill(
                                                text = callTypeText,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = directionIcon,
                                                contentDescription = null,
                                                tint = directionTint,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "$directionText · $callStatusText · ${formatDuration(call.durationSeconds)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.textSecondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = formatRelativeTimeLocalized(call.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textTertiary
                                    )
                                }
                                DlHairlineDivider(modifier = Modifier.padding(start = 72.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    myUid: String,
    call: CallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: (Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val tokens = LocalDlTokens.current
    var muted by remember { mutableStateOf(false) }
    var audioRoute by remember {
        mutableStateOf(if (call.callType == "VIDEO") CallAudioRoute.SPEAKER else CallAudioRoute.EARPIECE)
    }
    var cameraOn by remember { mutableStateOf(call.callType == "VIDEO") }
    var elapsedSec by remember(call.id, call.status) { mutableLongStateOf(0L) }

    val status = CallStatus.fromRaw(call.status)
    val isIncoming = call.calleeId == myUid
    val peerName = if (call.callerId == myUid) call.calleeName else call.callerName
    val routeLabel = audioRoute.localizedLabel()
    val callTypeLabel = localizedCallType(call.callType)

    DisposableEffect(call.id) {
        onDispose {
            CallAudioRouter.releaseAudioFocus(context)
        }
    }

    LaunchedEffect(status) {
        if (status == CallStatus.ACTIVE) {
            while (true) {
                delay(1000L)
                elapsedSec++
            }
        }
    }

    LaunchedEffect(audioRoute, muted) {
        CallAudioRouter.applyAudioRoute(context, audioRoute, muted)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(tokens.overlayBackdrop, tokens.overlayBackdropMid, tokens.overlayBackdrop)
                )
            )
            .padding(DlSpacing.lg)
    ) {
        if (call.callType == "VIDEO" && activity != null && CallAudioRouter.isPipSupported(context)) {
            IconButton(
                onClick = { CallAudioRouter.enterCallPipMode(activity) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(tokens.overlaySurface)
            ) {
                Icon(
                    Icons.Filled.PictureInPictureAlt,
                    contentDescription = stringResource(R.string.call_pip_cd),
                    tint = tokens.overlayInk
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DlAvatar(name = peerName, size = DlSizing.avatarHero)
            Spacer(Modifier.height(20.dp))
            Text(peerName, style = MaterialTheme.typography.displaySmall, color = tokens.overlayInk)
            Spacer(Modifier.height(DlSpacing.sm))
            Text(
                text = when (status) {
                    CallStatus.RINGING -> if (isIncoming) {
                        stringResource(R.string.call_incoming_type, callTypeLabel)
                    } else {
                        stringResource(R.string.call_ringing)
                    }
                    CallStatus.ACTIVE -> stringResource(
                        R.string.call_active_header,
                        callTypeLabel,
                        formatDuration(elapsedSec),
                        routeLabel
                    )
                    else -> localizedCallStatus(status.name)
                },
                style = MaterialTheme.typography.titleMedium,
                color = tokens.overlayAccent
            )
            if (call.groupCall) {
                Spacer(Modifier.height(DlSpacing.sm))
                DlStatusPill(
                    text = stringResource(R.string.call_group_pill, call.participantUids.size),
                    color = tokens.overlayAccent
                )
            }
            Spacer(Modifier.height(DlSpacing.base))
            Surface(
                shape = RoundedCornerShape(DlRadius.pillInput),
                color = tokens.overlayInk.copy(alpha = 0.08f),
                border = BorderStroke(DlSizing.divider, tokens.overlayBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = DlSpacing.base, vertical = DlSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val offerState = if (!call.sdpOffer.isNullOrBlank()) {
                        stringResource(R.string.call_signaling_offer_sent)
                    } else {
                        stringResource(R.string.call_signaling_pending)
                    }
                    val answerSuffix = if (!call.sdpAnswer.isNullOrBlank()) {
                        stringResource(R.string.call_signaling_answer_linked)
                    } else ""
                    Text(
                        text = stringResource(R.string.call_signaling_status, offerState + answerSuffix),
                        style = MaterialTheme.typography.labelMedium,
                        color = DlEmerald,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(DlSpacing.xs))
                    Text(
                        text = stringResource(R.string.call_signaling_disclosure, routeLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.overlayInkMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == CallStatus.RINGING && isIncoming) {
                Button(
                    onClick = onDecline,
                    colors = ButtonDefaults.buttonColors(containerColor = DlCrimson, contentColor = tokens.overlayInk),
                    modifier = Modifier.testTag("decline_call_button")
                ) {
                    Icon(Icons.Filled.CallEnd, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.call_decline_cta))
                }
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DlEmerald,
                        contentColor = contrastInkFor(DlEmerald)
                    ),
                    modifier = Modifier.testTag("accept_call_button")
                ) {
                    Icon(Icons.Filled.Call, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.call_accept_cta))
                }
            } else {
                IconButton(
                    onClick = { muted = !muted },
                    modifier = Modifier
                        .size(DlSizing.avatarLg)
                        .clip(CircleShape)
                        .background(if (muted) tokens.overlayInk else tokens.overlaySurface)
                ) {
                    Icon(
                        if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.call_mute_cd),
                        tint = if (muted) tokens.overlayBackdrop else tokens.overlayInk
                    )
                }
                IconButton(
                    onClick = {
                        val all = CallAudioRoute.entries
                        audioRoute = all[(all.indexOf(audioRoute) + 1) % all.size]
                    },
                    modifier = Modifier
                        .size(DlSizing.avatarLg)
                        .clip(CircleShape)
                        .background(if (audioRoute != CallAudioRoute.EARPIECE) tokens.overlayInk else tokens.overlaySurface)
                ) {
                    val icon = when (audioRoute) {
                        CallAudioRoute.SPEAKER -> Icons.Filled.VolumeUp
                        CallAudioRoute.BLUETOOTH -> Icons.Filled.BluetoothAudio
                        CallAudioRoute.EARPIECE -> Icons.Filled.Hearing
                    }
                    Icon(
                        icon,
                        contentDescription = stringResource(R.string.call_audio_route_cd, routeLabel),
                        tint = if (audioRoute != CallAudioRoute.EARPIECE) tokens.overlayBackdrop else tokens.overlayInk
                    )
                }
                if (call.callType == "VIDEO") {
                    IconButton(
                        onClick = { cameraOn = !cameraOn },
                        modifier = Modifier
                            .size(DlSizing.avatarLg)
                            .clip(CircleShape)
                            .background(if (!cameraOn) tokens.overlayInk else tokens.overlaySurface)
                    ) {
                        Icon(
                            if (cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                            contentDescription = stringResource(R.string.call_camera_cd),
                            tint = if (!cameraOn) tokens.overlayBackdrop else tokens.overlayInk
                        )
                    }
                }
                IconButton(
                    onClick = { onEnd(elapsedSec) },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(DlCrimson)
                        .testTag("end_call_button")
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = stringResource(R.string.call_end_cd), tint = tokens.overlayInk)
                }
            }
        }
    }
}

@Composable
private fun CallAudioRoute.localizedLabel(): String = when (this) {
    CallAudioRoute.EARPIECE -> stringResource(R.string.audio_route_earpiece)
    CallAudioRoute.SPEAKER -> stringResource(R.string.audio_route_speaker)
    CallAudioRoute.BLUETOOTH -> stringResource(R.string.audio_route_bluetooth)
}

@Composable
private fun localizedCallType(raw: String): String = when (raw.uppercase()) {
    "VIDEO" -> stringResource(R.string.calls_type_video)
    else -> stringResource(R.string.calls_type_voice)
}

@Composable
private fun localizedCallStatus(raw: String): String = when (CallStatus.fromRaw(raw)) {
    CallStatus.RINGING -> stringResource(R.string.calls_status_ringing)
    CallStatus.ACTIVE -> stringResource(R.string.calls_status_active)
    CallStatus.ENDED -> stringResource(R.string.calls_status_ended)
    CallStatus.DECLINED -> stringResource(R.string.calls_status_declined)
    CallStatus.MISSED -> stringResource(R.string.calls_status_missed)
}
