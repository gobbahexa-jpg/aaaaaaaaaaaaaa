package com.example

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.example.core.CryptoIdentityHelper
import com.example.core.DeathLegionOAuthClient
import com.example.core.LocalStorageManager
import com.example.core.LocaleHelper
import com.example.core.MultiAccountManager
import com.example.core.OfflineSyncEngine
import com.example.core.ParsedDlQrPayload
import com.example.core.PendingSyncMutation
import com.example.core.QrCodeUtils
import com.example.core.SecurityPrivacyManager
import com.example.core.StorageBreakdown
import com.example.core.PhoneNumberHelper
import com.example.data.model.AppealRecord
import com.example.data.model.AppealStatus
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.ContactRequest
import com.example.data.model.DeviceSession
import com.example.data.model.DlAccountStatus
import com.example.data.model.DlFeatureRestriction
import com.example.data.model.DlStaffRole
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketMessage
import com.example.data.model.UiState
import com.example.data.model.UserEnforcement
import com.example.data.model.UserProfile
import com.example.data.model.UserRestrictionRecord
import com.example.data.model.UserWarning
import com.example.data.repository.AuthRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.RegistrationInput
import com.example.data.repository.SupportRepository
import com.example.ui.auth.AuthPortalContainer
import com.example.ui.auth.AuthRoute
import com.example.ui.auth.LoginUiState
import com.example.ui.auth.PhoneOtpStep
import com.example.ui.auth.RegisterUiState
import com.example.ui.components.DlEmptyState
import com.example.ui.components.DlLanguageSelector
import com.example.ui.screens.AccountStatusScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminDashboardSection
import com.example.ui.screens.AppLockOverlay
import com.example.ui.screens.CommunityPoliciesScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.ContextualRestrictionBanner
import com.example.ui.screens.ConversationScreen
import com.example.ui.screens.DlAiScreen
import com.example.ui.screens.LinkedDevicesSection
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SupportCenterScreen
import com.example.ui.theme.DlChatTheme
import com.example.ui.theme.DlMotion
import com.example.ui.theme.DlTheme
import com.example.ui.theme.DlTokens
import com.example.ui.theme.NotoSansSinhalaFontFamily
import com.example.ui.theme.NotoSansTamilFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.contrastInkFor
import com.example.ui.theme.createDlMessagingTypography
import com.example.ui.theme.createDlTypography
import com.example.ui.theme.resolveAccessibleAccent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun appResources_reflectDlChatAndProvisionedFirestoreDatabase() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    val dbId = context.getString(R.string.firestore_database_id)
    assertEquals("DL Chat", appName)
    assertTrue("Expected provisioned Firestore DB ID, got: $dbId", dbId.startsWith("ai-studio-android-dlchat-"))
  }

  @Test
  fun cryptoIdentityHelper_rotatesKeysEncryptsPeerMessagesAndBackups() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val aliceKey = CryptoIdentityHelper.getOrCreateIdentityKey(context, "alice_e2ee_uid")
    val bobKey = CryptoIdentityHelper.getOrCreateIdentityKey(context, "bob_e2ee_uid")
    assertEquals(1, aliceKey.keyVersion)
    assertTrue(aliceKey.publicKeyBase64.isNotBlank())

    val payload = CryptoIdentityHelper.encryptForDirectPeer(
      context = context,
      myUid = "alice_e2ee_uid",
      chatId = "dm_alice_bob",
      peerPublicKeyBase64 = bobKey.publicKeyBase64,
      plaintext = "Zero-trust verified payload"
    )
    assertNotNull(payload)
    assertNotEquals("Zero-trust verified payload", payload!!.ciphertextBase64)

    val decrypted = CryptoIdentityHelper.decryptForDirectPeer(
      context = context,
      myUid = "bob_e2ee_uid",
      chatId = "dm_alice_bob",
      peerPublicKeyBase64 = aliceKey.publicKeyBase64,
      ciphertextBase64 = payload.ciphertextBase64,
      ivBase64 = payload.ivBase64
    )
    assertEquals("Zero-trust verified payload", decrypted)

    val rotated = CryptoIdentityHelper.rotateIdentityKey(context, "alice_e2ee_uid")
    assertEquals(2, rotated.keyVersion)
    assertNotEquals(aliceKey.fingerprint, rotated.fingerprint)

    val rawJson = """{"chats":[{"id":"c1"}],"savedNotes":[{"text":"Keep secret"}]}"""
    val encryptedBkp = CryptoIdentityHelper.encryptBackupJson(rawJson, "StrongPass#99")
    assertEquals(rawJson, CryptoIdentityHelper.decryptBackupJson(encryptedBkp, "StrongPass#99"))
    assertNull(CryptoIdentityHelper.decryptBackupJson(encryptedBkp, "WrongPass#11"))
  }

  @Test
  fun localStorageManager_formatsBytesAndClearsCacheSafely() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("512 B", LocalStorageManager.formatByteCount(512))
    assertEquals("2 KB", LocalStorageManager.formatByteCount(2048))
    val stats = LocalStorageManager.clearLocalCache(context)
    assertEquals(0L, stats.voiceCacheBytes)
  }

  @Test
  fun dlEmptyState_rendersAndTriggersAction() {
    var clicked = false
    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        DlEmptyState(
          icon = Icons.Filled.Forum,
          title = "No active chats yet",
          subtitle = "Start a direct chat, group, or public channel.",
          actionLabel = "New Chat",
          onAction = { clicked = true },
          modifier = Modifier.testTag("empty_state_card")
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("empty_state_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("No active chats yet").assertIsDisplayed()
    composeTestRule.onNodeWithText("New Chat").performClick()
    composeTestRule.waitForIdle()
    assertTrue(clicked)
  }

  @Test
  fun loginAndRegisterScreens_switchValidateAndSubmit() {
    var loginTriggered = false
    var registeredInput: RegistrationInput? = null

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        var route by remember { mutableStateOf(AuthRoute.LOGIN) }
        var registerState by remember { mutableStateOf(RegisterUiState()) }

        AuthPortalContainer(
          currentRoute = route,
          onRouteChange = { route = it },
          loginState = LoginUiState(isLoading = false, isCheckingAuto = false),
          registerState = registerState,
          onGoogleLoginClick = { loginTriggered = true },
          onDisplayNameChanged = { registerState = registerState.copy(displayName = it, validationError = null) },
          onUsernameChanged = { registerState = registerState.copy(username = it, validationError = null) },
          onBioChanged = { registerState = registerState.copy(bio = it, validationError = null) },
          onAvatarColorSelected = { registerState = registerState.copy(avatarColorHex = it) },
          onDiscoverableChanged = { registerState = registerState.copy(discoverable = it) },
          onSubmitRegister = {
            val draft = registerState.toRegistrationInput()
            val err = AuthRepository.validateRegistrationInput(draft)
            if (err != null) {
              registerState = registerState.copy(validationError = err)
            } else {
              registeredInput = draft
            }
          }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(loginTriggered)

    composeTestRule.onNodeWithTag("go_to_register_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("register_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("register_submit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auth_error_banner").performScrollTo().assertIsDisplayed()
    assertEquals(null, registeredInput)

    composeTestRule.onNodeWithTag("register_display_name_input").performScrollTo().performTextInput("Nova Sentinel")
    composeTestRule.onNodeWithTag("register_username_input").performScrollTo().performTextInput("nova_dl")
    composeTestRule.onNodeWithTag("register_color_indigo").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("register_submit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals("Nova Sentinel", registeredInput?.displayName)
    assertEquals("nova_dl", registeredInput?.sanitizedUsername())
    assertEquals("#6C5CE7", registeredInput?.avatarColor)
  }

  @Test
  fun contactsScreen_searchesOperatorsAndDisplaysIncomingRequests() {
    var acceptedRequestId: String? = null
    val users = listOf(
      UserProfile(userId = "me", username = "alpha", displayName = "Alpha"),
      UserProfile(userId = "u2", username = "cipher_fox", displayName = "Cipher Fox"),
      UserProfile(userId = "u3", username = "ghost_unit", displayName = "Ghost Unit")
    )
    val requests = listOf(
      ContactRequest(
        id = "req_1",
        senderId = "u3",
        senderName = "Ghost Unit",
        senderUsername = "ghost_unit",
        receiverId = "me",
        receiverName = "Alpha",
        status = "PENDING"
      )
    )

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        ContactsScreen(
          myUid = "me",
          myProfile = users.first(),
          usersState = UiState.Success(users),
          contacts = emptyList(),
          contactRequests = requests,
          onOpenDirectChat = {},
          onSendContactRequest = {},
          onRespondContactRequest = { req, accept -> if (accept) acceptedRequestId = req.id },
          onToggleFavorite = { _, _ -> },
          onToggleBlock = { _, _ -> }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Cipher Fox").assertIsDisplayed()
    composeTestRule.onNodeWithTag("contacts_search_input").performTextInput("Cipher")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Cipher Fox").assertIsDisplayed()

    composeTestRule.onNodeWithTag("contacts_filter_requests").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Ghost Unit").assertIsDisplayed()
  }

  @Test
  fun settingsScreen_switchesCategoriesAndTriggersKeyRotationAndCacheClear() {
    var rotated = false
    var clearedCache = false

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        SettingsScreen(
          myProfile = UserProfile(userId = "me", username = "alpha", displayName = "Alpha Lead", keyVersion = 2),
          settings = null,
          devices = emptyList(),
          savedMessages = emptyList(),
          backups = emptyList(),
          storageBreakdown = StorageBreakdown(voiceCacheBytes = 4096, imageCacheBytes = 2048),
          isAdminUser = false,
          onUpdateProfile = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
          onUpdateSettings = {},
          onRevokeDevice = {},
          onAddPersonalNote = {},
          onDeleteSaved = {},
          onRefreshStorage = {},
          onClearLocalCache = { clearedCache = true },
          onRotateKeys = { rotated = true },
          onExportBackup = { _, _ -> },
          onRestoreBackup = { _, _ -> },
          onOpenAdminDashboard = {},
          onSignOut = {},
          onDeleteAccount = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_cat_storage").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("clear_cache_button").performScrollTo().performClick()
    assertTrue(clearedCache)

    composeTestRule.onNodeWithTag("settings_cat_security").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("rotate_keys_button").performScrollTo().performClick()
    assertTrue(rotated)
  }

  @Test
  fun conversationScreen_rendersE2eeVoiceNoteAndSendsTextMessage() {
    var sentMessage: String? = null
    val chat = ChatRoom(
      id = "dm_test",
      type = "DIRECT",
      title = "Direct Chat",
      memberUids = listOf("me", "u2"),
      participantNames = mapOf("me" to "Alpha", "u2" to "Nova"),
      e2eeEnabled = true
    )
    val messages = listOf(
      ChatMessage(
        id = "m1",
        chatId = "dm_test",
        senderId = "u2",
        senderName = "Nova",
        type = "TEXT",
        text = "Encrypted check-in",
        encrypted = true
      ),
      ChatMessage(
        id = "m2",
        chatId = "dm_test",
        senderId = "me",
        senderName = "Alpha",
        type = "VOICE_NOTE",
        text = "Voice message (0:04)",
        audioDurationMs = 4000L,
        waveformSamples = listOf(20, 60, 40, 80)
      )
    )

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        ConversationScreen(
          myUid = "me",
          myProfile = UserProfile(userId = "me", username = "alpha", displayName = "Alpha"),
          chat = chat,
          messagesState = UiState.Success(messages),
          allChats = listOf(chat),
          allUsers = listOf(UserProfile(userId = "u2", username = "nova", displayName = "Nova")),
          contacts = emptyList(),
          bubbleStyle = "MODERN",
          wallpaperId = "solid",
          aiEnabled = true,
          aiBusy = false,
          smartReplies = emptyList(),
          summaryResult = null,
          translationResult = null,
          explainResult = null,
          showBackButton = false,
          onBack = {},
          onLoadOlderMessages = {},
          onMarkRead = {},
          onTypingChanged = {},
          onSendText = { _, text, _, _, _, _ -> sentMessage = text },
          onSendImage = { _, _ -> },
          onSendFileAttachment = { _, _ -> },
          onSendVoiceNote = {},
          onSendPoll = { _, _ -> },
          onEditMessage = { _, _ -> },
          onDeleteForEveryone = {},
          onToggleStar = {},
          onToggleReaction = { _, _ -> },
          onVotePoll = { _, _ -> },
          onPinMessage = {},
          onSaveMessage = {},
          onForwardMessage = { _, _ -> },
          onJoinChannel = {},
          onRequestJoinGroup = {},
          onApproveJoinRequest = { _, _ -> },
          onStartCall = { _, _ -> },
          onSummarizeChat = {},
          onSuggestSmartReplies = {},
          onRewriteDraft = { _, _, _ -> },
          onTranslateMessage = { _, _ -> },
          onExplainMessage = {},
          onToggleMute = {},
          onToggleArchive = {},
          onSetDisappearing = {},
          onToggleAdminOnly = {},
          onUpdatePermissions = { _, _ -> },
          onRegenerateInvite = {},
          onRevokeInvite = {},
          onUpdateChatTheme = {},
          onAddMember = {},
          onPromoteAdmin = {},
          onRemoveMember = {},
          onToggleFavorite = { _, _ -> },
          onToggleBlock = { _, _ -> },
          onSubmitReport = { _, _, _ -> },
          onDismissSummary = {},
          onDismissTranslation = {},
          onDismissExplain = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Encrypted check-in").assertIsDisplayed()
    composeTestRule.onNodeWithTag("message_input").performTextInput("Copy that, Nova")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("send_message_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals("Copy that, Nova", sentMessage)
  }

  @Test
  fun v3CoreUtilities_qrPairingMultiAccountOfflineSyncAndDeathLegionOAuthWork() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // QR Code URI construction & parsing
    val pairUri = QrCodeUtils.buildPairingUri("uid_1", "sess_9", "tok_xyz", "nonce_abc", 1999999999999L)
    val parsedPair = QrCodeUtils.parseQrContent(pairUri) as ParsedDlQrPayload.PairingQr
    assertEquals("uid_1", parsedPair.ownerUid)
    assertEquals("sess_9", parsedPair.sessionId)
    assertEquals("tok_xyz", parsedPair.rawToken)

    val inviteUri = QrCodeUtils.buildInviteUri("INVITE88", "chat_grp_1")
    val parsedInvite = QrCodeUtils.parseQrContent(inviteUri) as ParsedDlQrPayload.GroupInviteQr
    assertEquals("INVITE88", parsedInvite.inviteCode)
    assertEquals("chat_grp_1", parsedInvite.chatId)

    // OfflineSyncEngine LWW coalescing
    OfflineSyncEngine.clearQueue(context, "uid_sync")
    OfflineSyncEngine.enqueueMutation(
      context,
      "uid_sync",
      PendingSyncMutation(id = "m1", entityType = "DRAFT", entityId = "c1", operation = "UPSERT", versionEpochMs = 100L, payloadSummary = "old")
    )
    val coalesced = OfflineSyncEngine.enqueueMutation(
      context,
      "uid_sync",
      PendingSyncMutation(id = "m2", entityType = "DRAFT", entityId = "c1", operation = "UPSERT", versionEpochMs = 200L, payloadSummary = "new")
    )
    assertEquals(1, coalesced.size)
    assertEquals("new", coalesced.first().payloadSummary)

    // MultiAccountManager isolated purge never wipes another UID's PIN
    SecurityPrivacyManager.setPin(context, "uid_a", "1357")
    SecurityPrivacyManager.setPin(context, "uid_b", "2468")
    MultiAccountManager.recordAccount(context, "uid_a", "a@dl.test", "Alpha", "alpha")
    MultiAccountManager.recordAccount(context, "uid_b", "b@dl.test", "Bravo", "bravo")
    MultiAccountManager.removeAccount(context, "uid_a")
    assertEquals(false, SecurityPrivacyManager.hasPinSet(context, "uid_a"))
    assertTrue(SecurityPrivacyManager.verifyPin(context, "uid_b", "2468"))

    // DeathLegionOAuthClient URL builder, callback state & UserInfo parser
    val state = DeathLegionOAuthClient.generateAndStoreState(context)
    val authUrl = DeathLegionOAuthClient.buildAuthorizeUrl("dl_client_1", state)
    assertTrue(authUrl.startsWith("https://deathlegion.vercel.app/api/oauth/authorize"))
    val callback = DeathLegionOAuthClient.parseCallbackUri(
      android.net.Uri.parse("dlchat://oauth/deathlegion/callback?code=auth_99&state=$state")
    )
    assertEquals("auth_99", callback?.code)
    assertTrue(DeathLegionOAuthClient.validateAndConsumeState(context, callback?.state))
    assertEquals(false, DeathLegionOAuthClient.validateAndConsumeState(context, state))

    val parsedLegionUser = DeathLegionOAuthClient.parseUserInfoJson(
      org.json.JSONObject(
        """{"sub":"dl_42","username":"ghost_cmdr","name":"Ghost","verification_status":"VERIFIED","member_number":"DL-0042","primary_unit":{"name":"Obsidian Vanguard"}}"""
      )
    )
    assertEquals("ghost_cmdr", parsedLegionUser.username)
    assertEquals("Obsidian Vanguard", parsedLegionUser.primaryUnitName)
  }

  @Test
  fun loginScreen_displaysDeathLegionOAuthButton() {
    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        AuthPortalContainer(
          currentRoute = AuthRoute.LOGIN,
          onRouteChange = {},
          loginState = LoginUiState(),
          registerState = RegisterUiState(),
          onGoogleLoginClick = {},
          onDisplayNameChanged = {},
          onUsernameChanged = {},
          onBioChanged = {},
          onAvatarColorSelected = {},
          onDiscoverableChanged = {},
          onSubmitRegister = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("death_legion_oauth_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun appLockOverlay_verifiesPinAndUnlocks() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    SecurityPrivacyManager.setPin(context, "uid_lock_test", "4826")
    var unlocked = false

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        AppLockOverlay(
          uid = "uid_lock_test",
          displayName = "Alpha",
          biometricEnabled = false,
          onUnlocked = { unlocked = true },
          onSignOut = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("app_lock_pin_input").performTextInput("4826")
    composeTestRule.onNodeWithTag("app_lock_unlock_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(unlocked)
  }

  @Test
  fun linkedDevicesSection_displaysControlsAndRevokesOtherDevices() {
    var revokedAll = false
    val dev = DeviceSession(
      id = "desktop_1",
      userId = "me",
      deviceName = "Obsidian Rig",
      deviceType = "DESKTOP",
      osName = "Linux",
      connectionStatus = "ONLINE"
    )

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        LinkedDevicesSection(
          currentDeviceId = "android_me",
          devices = listOf(dev),
          pairingSessions = emptyList(),
          deviceKeyBundles = emptyList(),
          deviceNotifications = emptyList(),
          activeChallenge = null,
          onGeneratePairingQr = { _, _, _, _ -> },
          onCancelPairing = {},
          onConfirmScannedQr = {},
          onConfirmManualPairing = { _, _ -> },
          onRenameDevice = { _, _ -> },
          onRotateDeviceToken = {},
          onRevokeDevice = {},
          onRevokeAllOtherDevices = { revokedAll = true },
          onMarkNotificationRead = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("generate_pairing_qr_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("revoke_all_other_devices_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(revokedAll)
  }

  @Test
  fun conversationScreen_rendersV3QuizStickerAndRichFormatting() {
    var quizVotedIndex: Int? = null
    val chat = ChatRoom(
      id = "grp_v3",
      type = "GROUP",
      title = "V3 Ops Room",
      memberUids = listOf("me", "u2"),
      e2eeEnabled = false
    )
    val v3Messages = listOf(
      ChatMessage(
        id = "q1",
        chatId = "grp_v3",
        senderId = "u2",
        senderName = "Nova",
        type = "QUIZ",
        text = "What cipher protects direct messages?",
        pollQuestion = "What cipher protects direct messages?",
        pollOptions = listOf("AES-256-GCM", "ROT13"),
        quizCorrectIndex = 0,
        quizExplanation = "ECDH + AES-256-GCM"
      ),
      ChatMessage(
        id = "s1",
        chatId = "grp_v3",
        senderId = "me",
        senderName = "Alpha",
        type = "STICKER",
        text = "Legion Shield",
        stickerId = "dl_shield"
      ),
      ChatMessage(
        id = "r1",
        chatId = "grp_v3",
        senderId = "u2",
        senderName = "Nova",
        type = "TEXT",
        text = "Check **priority** and ||secret||"
      )
    )

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        ConversationScreen(
          myUid = "me",
          myProfile = UserProfile(userId = "me", username = "alpha", displayName = "Alpha"),
          chat = chat,
          messagesState = UiState.Success(v3Messages),
          allChats = listOf(chat),
          allUsers = emptyList(),
          contacts = emptyList(),
          bubbleStyle = "MODERN",
          wallpaperId = "solid",
          aiEnabled = false,
          aiBusy = false,
          smartReplies = emptyList(),
          summaryResult = null,
          translationResult = null,
          explainResult = null,
          showBackButton = false,
          onBack = {},
          onLoadOlderMessages = {},
          onMarkRead = {},
          onTypingChanged = {},
          onSendText = { _, _, _, _, _, _ -> },
          onSendImage = { _, _ -> },
          onSendFileAttachment = { _, _ -> },
          onSendVoiceNote = {},
          onSendPoll = { _, _ -> },
          onEditMessage = { _, _ -> },
          onDeleteForEveryone = {},
          onToggleStar = {},
          onToggleReaction = { _, _ -> },
          onVotePoll = { _, idx -> quizVotedIndex = idx },
          onPinMessage = {},
          onSaveMessage = {},
          onForwardMessage = { _, _ -> },
          onJoinChannel = {},
          onRequestJoinGroup = {},
          onApproveJoinRequest = { _, _ -> },
          onStartCall = { _, _ -> },
          onSummarizeChat = {},
          onSuggestSmartReplies = {},
          onRewriteDraft = { _, _, _ -> },
          onTranslateMessage = { _, _ -> },
          onExplainMessage = {},
          onToggleMute = {},
          onToggleArchive = {},
          onSetDisappearing = {},
          onToggleAdminOnly = {},
          onUpdatePermissions = { _, _ -> },
          onRegenerateInvite = {},
          onRevokeInvite = {},
          onUpdateChatTheme = {},
          onAddMember = {},
          onPromoteAdmin = {},
          onRemoveMember = {},
          onToggleFavorite = { _, _ -> },
          onToggleBlock = { _, _ -> },
          onSubmitReport = { _, _, _ -> },
          onDismissSummary = {},
          onDismissTranslation = {},
          onDismissExplain = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("conversation_messages_list")
      .performScrollToNode(hasText("AES-256-GCM"))
    composeTestRule.onNodeWithText("AES-256-GCM").performClick()
    composeTestRule.waitForIdle()
    assertEquals(0, quizVotedIndex)
    composeTestRule.onNodeWithTag("conversation_messages_list")
      .performScrollToNode(hasText("Legion Shield"))
    composeTestRule.onNodeWithText("Legion Shield").assertIsDisplayed()
  }

  @Test
  fun settingsScreen_v3PrivacyAppLockAndLinkedDevicesRender() {
    var appLockEnabledValue: Boolean? = null
    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        SettingsScreen(
          myProfile = UserProfile(userId = "me_v3", username = "alpha", displayName = "Alpha"),
          settings = com.example.data.model.UserSetting(userId = "me_v3"),
          devices = emptyList(),
          savedMessages = emptyList(),
          backups = emptyList(),
          storageBreakdown = StorageBreakdown(),
          isAdminUser = false,
          onUpdateProfile = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
          onUpdateSettings = { appLockEnabledValue = it.appLockEnabled },
          onRevokeDevice = {},
          onAddPersonalNote = {},
          onDeleteSaved = {},
          onRefreshStorage = {},
          onClearLocalCache = {},
          onRotateKeys = {},
          onExportBackup = { _, _ -> },
          onRestoreBackup = { _, _ -> },
          onOpenAdminDashboard = {},
          onSignOut = {},
          onDeleteAccount = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_cat_privacy").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_lazy_column")
      .performScrollToNode(hasTestTag("app_lock_toggle"))
    composeTestRule.onNodeWithTag("app_lock_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(true, appLockEnabledValue)

    composeTestRule.onNodeWithTag("settings_lazy_column").performScrollToIndex(1)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_cat_devices").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_lazy_column")
      .performScrollToNode(hasTestTag("generate_pairing_qr_button"))
    composeTestRule.onNodeWithTag("generate_pairing_qr_button").assertIsDisplayed()
  }

  @Test
  fun v4Theme_resolvesAccessibleLightAccentAndContrastInk() {
    val lightCyan = resolveAccessibleAccent("#00E5FF", isDark = false)
    val darkCyan = resolveAccessibleAccent("#00E5FF", isDark = true)
    assertNotEquals(darkCyan, lightCyan)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF007791), lightCyan)
    // Bright neon cyan requires dark text; deep alabaster cyan requires white ink
    assertEquals(androidx.compose.ui.graphics.Color(0xFF070B12), contrastInkFor(darkCyan))
    assertEquals(androidx.compose.ui.graphics.Color.White, contrastInkFor(lightCyan))
  }

  @Test
  fun dlAiScreen_rendersWorkspaceAndSubmitsPrompt() {
    var submittedPrompt: String? = null
    composeTestRule.setContent {
      DlChatTheme(themeMode = "LIGHT") {
        DlAiScreen(
          messages = emptyList(),
          busy = false,
          aiEnabled = true,
          onAsk = { submittedPrompt = it },
          onClearChat = {},
          onOpenSettings = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("ai_assistant_screen").assertIsDisplayed()
    composeTestRule.onNodeWithText("Draft an update").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ai_prompt_input").performTextInput("Summarize channel status")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("ai_send_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals("Summarize channel status", submittedPrompt)
  }

  @Test
  fun settingsScreen_v4LocalAppearanceAndStorageTogglesInvokeCallbacks() {
    var reducedMotionCaptured: Boolean? = null
    var wifiOnlyCaptured: Boolean? = null
    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        SettingsScreen(
          myProfile = UserProfile(userId = "me_v4", username = "vanguard", displayName = "Vanguard"),
          settings = com.example.data.model.UserSetting(userId = "me_v4"),
          devices = emptyList(),
          savedMessages = emptyList(),
          backups = emptyList(),
          storageBreakdown = StorageBreakdown(voiceCacheBytes = 1024, imageCacheBytes = 2048),
          isAdminUser = false,
          onUpdateProfile = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
          onUpdateSettings = {},
          onRevokeDevice = {},
          onAddPersonalNote = {},
          onDeleteSaved = {},
          onRefreshStorage = {},
          onClearLocalCache = {},
          onRotateKeys = {},
          onExportBackup = { _, _ -> },
          onRestoreBackup = { _, _ -> },
          onOpenAdminDashboard = {},
          onSignOut = {},
          onDeleteAccount = {},
          localAppearancePrefs = com.example.data.repository.AppearancePrefs(),
          onUpdateLocalUiPrefs = { redMotion, _, _, _, _, wifiOnly, _ ->
            if (redMotion != null) reducedMotionCaptured = redMotion
            if (wifiOnly != null) wifiOnlyCaptured = wifiOnly
          },
          onBack = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_cat_appearance").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_lazy_column")
      .performScrollToNode(hasText("Reduce animations"))
    composeTestRule.onNodeWithText("Reduce animations").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(true, reducedMotionCaptured)

    composeTestRule.onNodeWithTag("settings_lazy_column").performScrollToIndex(0)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_cat_storage").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_lazy_column")
      .performScrollToNode(hasText("Only download media over Wi-Fi"))
    composeTestRule.onNodeWithText("Only download media over Wi-Fi").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(true, wifiOnlyCaptured)
  }

  @Test
  fun v5Localization_localeHelperResolvesEnglishSinhalaAndTamilStrings() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val enLogin = LocaleHelper.getString(context, "en", R.string.auth_tab_login)
    val siLogin = LocaleHelper.getString(context, "si", R.string.auth_tab_login)
    val taLogin = LocaleHelper.getString(context, "ta", R.string.auth_tab_login)

    assertEquals("Sign in", enLogin)
    assertTrue(siLogin.isNotBlank() && siLogin != enLogin)
    assertTrue(taLogin.isNotBlank() && taLogin != enLogin && taLogin != siLogin)

    val enChats = LocaleHelper.getString(context, "en", R.string.nav_chats)
    val siChats = LocaleHelper.getString(context, "si", R.string.nav_chats)
    val taChats = LocaleHelper.getString(context, "ta", R.string.nav_chats)
    assertEquals("Chats", enChats)
    assertNotEquals(enChats, siChats)
    assertNotEquals(enChats, taChats)

    val enSettings = LocaleHelper.getString(context, "en", R.string.settings_title)
    val siSettings = LocaleHelper.getString(context, "si", R.string.settings_title)
    val taSettings = LocaleHelper.getString(context, "ta", R.string.settings_title)
    assertEquals("Settings", enSettings)
    assertNotEquals(enSettings, siSettings)
    assertNotEquals(enSettings, taSettings)

    // Normalize unknown codes safely back to English
    assertEquals("en", LocaleHelper.normalizeCode("fr"))
    assertEquals("si", LocaleHelper.normalizeCode(" SI "))
    assertEquals("ta", LocaleHelper.normalizeCode("TA"))
  }

  @Test
  fun v5Typography_indicLanguagesUseBundledFontsWithZeroLetterSpacing() {
    val enTypo = createDlTypography(1.0f, "en")
    val siTypo = createDlTypography(1.0f, "si")
    val taTypo = createDlTypography(1.0f, "ta")

    assertEquals(SpaceGroteskFontFamily, enTypo.headlineMedium.fontFamily)
    assertEquals(PlusJakartaSansFontFamily, enTypo.bodyLarge.fontFamily)

    assertEquals(NotoSansSinhalaFontFamily, siTypo.headlineMedium.fontFamily)
    assertEquals(NotoSansSinhalaFontFamily, siTypo.bodyLarge.fontFamily)
    assertEquals(0.sp, siTypo.displaySmall.letterSpacing)
    assertEquals(0.sp, siTypo.headlineMedium.letterSpacing)

    assertEquals(NotoSansTamilFontFamily, taTypo.headlineMedium.fontFamily)
    assertEquals(NotoSansTamilFontFamily, taTypo.bodyLarge.fontFamily)
    assertEquals(0.sp, taTypo.displaySmall.letterSpacing)
    assertEquals(0.sp, taTypo.headlineMedium.letterSpacing)
  }

  @Test
  fun v5PreferencesRepository_persistsAppLanguageAcrossEnSiTa() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = PreferencesRepository(context)

    repo.saveLocalUiPreferences(appLanguage = "si")
    assertEquals("si", repo.appearanceFlow.first().appLanguage)

    repo.saveLocalUiPreferences(appLanguage = "ta")
    assertEquals("ta", repo.appearanceFlow.first().appLanguage)

    repo.saveLocalUiPreferences(appLanguage = "en")
    assertEquals("en", repo.appearanceFlow.first().appLanguage)
  }

  @Test
  fun v5LanguageSelector_triggersCallbacksForSinhalaAndTamil() {
    var selected = "en"
    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        DlLanguageSelector(
          selectedLanguage = selected,
          onSelectLanguage = { selected = it }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("language_option_si").performClick()
    composeTestRule.waitForIdle()
    assertEquals("si", selected)

    composeTestRule.onNodeWithTag("language_option_ta").performClick()
    composeTestRule.waitForIdle()
    assertEquals("ta", selected)
  }

  @Test
  fun designTokens_resolveDarkLightPalettesSpacingAndSizing() {
    var darkTokens: DlTokens? = null
    var lightTokens: DlTokens? = null

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK", compactDensity = true, reducedMotion = true) {
        darkTokens = DlTheme.tokens
      }
      DlChatTheme(themeMode = "LIGHT", compactDensity = false, reducedMotion = false) {
        lightTokens = DlTheme.tokens
      }
    }

    composeTestRule.waitForIdle()
    val resolvedDark = requireNotNull(darkTokens)
    val resolvedLight = requireNotNull(lightTokens)
    assertTrue(resolvedDark.isDark)
    assertEquals(false, resolvedLight.isDark)
    assertNotEquals(resolvedDark.bubbleIncoming, resolvedLight.bubbleIncoming)
    assertNotEquals(resolvedDark.textPrimary, resolvedLight.textPrimary)
    assertEquals(resolvedDark.brandLegion, resolvedLight.brandLegion)
    assertEquals(48.dp, DlTheme.sizing.touchTarget)
    assertEquals(320.dp, DlTheme.spacing.bubbleMaxWidth)
    assertEquals(9.dp, DlTheme.spacing.rowVertical(compact = true))
    assertEquals(13.dp, DlTheme.spacing.rowVertical(compact = false))
    assertEquals(0, DlMotion.duration(240, reducedMotion = true))
    assertEquals(240, DlMotion.duration(240, reducedMotion = false))
  }

  @Test
  fun designTokens_messagingTypographySupportsScaleAndTabularNumbers() {
    val baseTypo = createDlTypography(1.0f, "en")
    assertEquals(SpaceGroteskFontFamily, baseTypo.titleSmall.fontFamily)

    val msgDefault = createDlMessagingTypography(1.0f, "en")
    val msgScaled = createDlMessagingTypography(1.2f, "en")
    assertEquals("tnum", msgDefault.messageTimestamp.fontFeatureSettings)
    assertEquals("tnum", msgDefault.unreadCount.fontFeatureSettings)
    assertTrue(msgScaled.messageBody.fontSize.value > msgDefault.messageBody.fontSize.value)

    val msgSinhala = createDlMessagingTypography(1.0f, "si")
    assertEquals(NotoSansSinhalaFontFamily, msgSinhala.chatTitle.fontFamily)
    assertEquals(NotoSansSinhalaFontFamily, msgSinhala.chatPreview.fontFamily)
  }

  @Test
  fun loginScreen_hidesGoogleAndAiStudioTextAndShowsDeathLegionCallbackDialog() {
    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        AuthPortalContainer(
          currentRoute = AuthRoute.LOGIN,
          onRouteChange = {},
          loginState = LoginUiState(isLoading = false, isCheckingAuto = false),
          registerState = RegisterUiState(),
          onGoogleLoginClick = {},
          onDisplayNameChanged = {},
          onUsernameChanged = {},
          onBioChanged = {},
          onAvatarColorSelected = {},
          onDiscoverableChanged = {},
          onSubmitRegister = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodes(hasText("Google", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodes(hasText("AI Studio", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodes(hasText("DL AI", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)

    val appContext = ApplicationProvider.getApplicationContext<Context>()
    val checkedIds = listOf(
      R.string.auth_cap_ai_title,
      R.string.nav_ai,
      R.string.chats_search_placeholder,
      R.string.chats_ask_ai_cd,
      R.string.chats_ai_search_label,
      R.string.conv_ai_tools_cd,
      R.string.conv_ai_panel_title,
      R.string.msg_act_translate,
      R.string.msg_act_voice_hint,
      R.string.msg_act_translate_e2ee_note,
      R.string.info_e2ee_ai_note,
      R.string.ai_title,
      R.string.ai_privacy_ready,
      R.string.ai_privacy_paused,
      R.string.ai_streaming_label,
      R.string.ai_input_placeholder,
      R.string.ai_input_paused_placeholder,
      R.string.ai_send_cd,
      R.string.ai_sheet_placeholder,
      R.string.settings_section_chats,
      R.string.settings_enable_ai,
      R.string.settings_about_desc,
      R.string.vm_ai_welcome,
      R.string.vm_ai_disabled,
      R.string.vm_ai_error,
      R.string.vm_ai_e2ee_summary_blocked,
      R.string.vm_ai_e2ee_search_blocked,
      R.string.conv_slash_ai
    )
    listOf("en", "si", "ta").forEach { lang ->
      checkedIds.forEach { resId ->
        val txt = LocaleHelper.getString(appContext, lang, resId)
        assertEquals("Unexpected AI Studio in $lang", false, txt.contains("AI Studio", ignoreCase = true))
        assertEquals("Unexpected Google in $lang", false, txt.contains("Google", ignoreCase = true))
        assertEquals("Unexpected DL AI in $lang", false, txt.contains("DL AI", ignoreCase = true))
        assertEquals("Unexpected standalone AI in $lang ($txt)", false, Regex("\\bAI\\b").containsMatchIn(txt))
      }
    }

    composeTestRule.onNodeWithTag("death_legion_oauth_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(
      composeTestRule.onAllNodes(hasText("dlchat://oauth/deathlegion/callback", substring = true))
        .fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeTestRule.onAllNodes(hasText("f182c16d374a31f263e025557fe78f93", substring = true))
        .fetchSemanticsNodes().isNotEmpty()
    )
  }

  @Test
  fun deathLegionOAuthClient_parsesCamelCaseUserInfoPayload() {
    val parsed = DeathLegionOAuthClient.parseUserInfoJson(
      org.json.JSONObject(
        """
        {
          "id": "dl_user_99",
          "username": "vanguard_zero",
          "displayName": "Vanguard Zero",
          "email": "vanguard@deathlegion.org",
          "memberNumber": "DL-0099",
          "verificationStatus": "VERIFIED",
          "primaryUnit": {"name": "Crimson Spear"},
          "currentRole": "Operations Lead",
          "role": "Commander"
        }
        """.trimIndent()
      )
    )
    assertEquals("dl_user_99", parsed.legionUserId)
    assertEquals("vanguard_zero", parsed.username)
    assertEquals("Vanguard Zero", parsed.displayName)
    assertEquals("vanguard@deathlegion.org", parsed.email)
    assertEquals("DL-0099", parsed.memberNumber)
    assertEquals("VERIFIED", parsed.verificationStatus)
    assertEquals("Crimson Spear", parsed.primaryUnitName)
    assertEquals("Operations Lead", parsed.currentRole)
    assertEquals("Commander", parsed.role)
  }

  @Test
  fun contactsScreen_verifiedFilterShowsOnlyDeathLegionVerifiedUsers() {
    val users = listOf(
      UserProfile(userId = "me", username = "alpha", displayName = "Alpha"),
      UserProfile(
        userId = "u_verified",
        username = "legion_lead",
        displayName = "Verified Lead",
        legionUsername = "legion_lead",
        legionVerificationStatus = "VERIFIED",
        legionPrimaryUnit = "Obsidian Vanguard"
      ),
      UserProfile(
        userId = "u_regular",
        username = "casual_peer",
        displayName = "Regular Peer"
      )
    )

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        ContactsScreen(
          myUid = "me",
          myProfile = users.first(),
          usersState = UiState.Success(users),
          contacts = emptyList(),
          contactRequests = emptyList(),
          onOpenDirectChat = {},
          onSendContactRequest = {},
          onRespondContactRequest = { _, _ -> },
          onToggleFavorite = { _, _ -> },
          onToggleBlock = { _, _ -> }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Verified Lead").assertIsDisplayed()
    composeTestRule.onNodeWithText("Regular Peer").assertIsDisplayed()

    composeTestRule.onNodeWithTag("contacts_filter_verified").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Verified Lead").assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodes(hasText("Regular Peer")).fetchSemanticsNodes().size)
  }

  @Test
  fun settingsScreen_quickPresenceAndDeathLegionSessionControlsTriggerCallbacks() {
    var updatedStatus: String? = null
    var refreshedSession = false
    var introspectedSession = false
    var disconnectedSession = false

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        SettingsScreen(
          myProfile = UserProfile(
            userId = "me_legion",
            username = "vanguard",
            displayName = "Vanguard",
            legionUserId = "dl_101",
            legionUsername = "vanguard_dl",
            legionMemberNumber = "DL-101",
            legionPrimaryUnit = "Vanguard Alpha",
            legionVerificationStatus = "VERIFIED"
          ),
          settings = com.example.data.model.UserSetting(userId = "me_legion"),
          devices = emptyList(),
          savedMessages = emptyList(),
          backups = emptyList(),
          storageBreakdown = StorageBreakdown(),
          isAdminUser = false,
          onUpdateProfile = { _, _, _, _, _, status, _, _, _, _, _, _ -> updatedStatus = status },
          onUpdateSettings = {},
          onRevokeDevice = {},
          onAddPersonalNote = {},
          onDeleteSaved = {},
          onRefreshStorage = {},
          onClearLocalCache = {},
          onRotateKeys = {},
          onExportBackup = { _, _ -> },
          onRestoreBackup = { _, _ -> },
          onOpenAdminDashboard = {},
          onRefreshDeathLegionSession = { refreshedSession = true },
          onIntrospectDeathLegionSession = { introspectedSession = true },
          onDisconnectDeathLegion = { disconnectedSession = true },
          onSignOut = {},
          onDeleteAccount = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_lazy_column")
      .performScrollToNode(hasTestTag("presence_chip_dnd"))
    composeTestRule.onNodeWithTag("presence_chip_dnd").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals("DND", updatedStatus)

    composeTestRule.onNodeWithTag("settings_lazy_column")
      .performScrollToNode(hasTestTag("legion_refresh_button"))
    composeTestRule.onNodeWithTag("legion_refresh_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("legion_introspect_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("legion_disconnect_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertTrue(refreshedSession)
    assertTrue(introspectedSession)
    assertTrue(disconnectedSession)
  }

  @Test
  fun conversationScreen_slashCommandPaletteOpensAndTogglesSchedule() {
    val chat = ChatRoom(
      id = "dm_slash",
      type = "DIRECT",
      title = "Direct Chat",
      memberUids = listOf("me", "u2"),
      participantNames = mapOf("me" to "Alpha", "u2" to "Nova")
    )

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        ConversationScreen(
          myUid = "me",
          myProfile = UserProfile(userId = "me", username = "alpha", displayName = "Alpha"),
          chat = chat,
          messagesState = UiState.Success(emptyList()),
          allChats = listOf(chat),
          allUsers = listOf(UserProfile(userId = "u2", username = "nova", displayName = "Nova")),
          contacts = emptyList(),
          bubbleStyle = "MODERN",
          wallpaperId = "solid",
          aiEnabled = true,
          aiBusy = false,
          smartReplies = emptyList(),
          summaryResult = null,
          translationResult = null,
          explainResult = null,
          showBackButton = false,
          onBack = {},
          onLoadOlderMessages = {},
          onMarkRead = {},
          onTypingChanged = {},
          onSendText = { _, _, _, _, _, _ -> },
          onSendImage = { _, _ -> },
          onSendFileAttachment = { _, _ -> },
          onSendVoiceNote = {},
          onSendPoll = { _, _ -> },
          onEditMessage = { _, _ -> },
          onDeleteForEveryone = {},
          onToggleStar = {},
          onToggleReaction = { _, _ -> },
          onVotePoll = { _, _ -> },
          onPinMessage = {},
          onSaveMessage = {},
          onForwardMessage = { _, _ -> },
          onJoinChannel = {},
          onRequestJoinGroup = {},
          onApproveJoinRequest = { _, _ -> },
          onStartCall = { _, _ -> },
          onSummarizeChat = {},
          onSuggestSmartReplies = {},
          onRewriteDraft = { _, _, _ -> },
          onTranslateMessage = { _, _ -> },
          onExplainMessage = {},
          onToggleMute = {},
          onToggleArchive = {},
          onSetDisappearing = {},
          onToggleAdminOnly = {},
          onUpdatePermissions = { _, _ -> },
          onRegenerateInvite = {},
          onRevokeInvite = {},
          onUpdateChatTheme = {},
          onAddMember = {},
          onPromoteAdmin = {},
          onRemoveMember = {},
          onToggleFavorite = { _, _ -> },
          onToggleBlock = { _, _ -> },
          onSubmitReport = { _, _, _ -> },
          onDismissSummary = {},
          onDismissTranslation = {},
          onDismissExplain = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("message_input").performTextInput("/")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("slash_command_palette").assertIsDisplayed()
    composeTestRule.onNodeWithTag("slash_cmd_schedule").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Send in 5m", substring = true).assertIsDisplayed()
  }

  @Test
  fun v6PhoneOtpAuth_defaultsToSriLankaCountryPickerAndOtpStep() {
    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        var loginState by remember { mutableStateOf(LoginUiState(isLoading = false, isCheckingAuto = false)) }
        AuthPortalContainer(
          currentRoute = AuthRoute.LOGIN,
          onRouteChange = {},
          loginState = loginState,
          registerState = RegisterUiState(),
          onGoogleLoginClick = {},
          onDisplayNameChanged = {},
          onUsernameChanged = {},
          onBioChanged = {},
          onAvatarColorSelected = {},
          onDiscoverableChanged = {},
          onSubmitRegister = {},
          onSelectCountry = { country ->
            loginState = loginState.copy(
              selectedCountry = country,
              e164Preview = PhoneNumberHelper.normalizeToE164(country, loginState.phoneInput)
            )
          },
          onPhoneInputChanged = { input ->
            loginState = loginState.copy(
              phoneInput = input,
              e164Preview = PhoneNumberHelper.normalizeToE164(loginState.selectedCountry, input)
            )
          },
          onSendOtpClick = {
            loginState = loginState.copy(otpStep = PhoneOtpStep.VERIFY_CODE, verificationId = "vid_test")
          },
          onOtpCodeChanged = { code -> loginState = loginState.copy(otpCode = code) }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("phone_country_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("+94", substring = true).performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("phone_country_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("country_search_input").performTextInput("United States")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("country_option_US").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("phone_number_input").performScrollTo().performTextInput("4155550142")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("+14155550142", substring = true).performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("send_otp_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("otp_code_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("verify_otp_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun v6AccountStatusAndAppeals_displaysRestrictionsAndSubmitsAppeal() {
    var ackWarningId: String? = null
    var submittedAppealReason: String? = null
    val nowMs = System.currentTimeMillis()
    val enforcement = UserEnforcement(
      userId = "me",
      accountStatus = DlAccountStatus.MEDIA_RESTRICTED.name,
      featureExpiries = mapOf(DlFeatureRestriction.SEND_MEDIA.key to (nowMs + 7_200_000L)),
      reasonSummary = "Temporary media sharing hold",
      lastEnforcementId = "r1",
      lastEnforcedBy = "mod_1",
      appealEligible = true
    )
    val warnings = listOf(
      UserWarning(
        id = "w1",
        userId = "me",
        title = "Repeated link spam",
        description = "Please avoid posting unverified links.",
        severity = "MEDIUM",
        acknowledged = false
      )
    )
    val restrictions = listOf(
      UserRestrictionRecord(
        id = "r1",
        userId = "me",
        status = DlAccountStatus.MEDIA_RESTRICTED.name,
        features = listOf(DlFeatureRestriction.SEND_MEDIA.key),
        expiresAtEpochMs = nowMs + 7_200_000L,
        reason = "Temporary media sharing hold",
        enforcedByUid = "mod_1",
        active = true,
        appealEligible = true
      )
    )

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        androidx.compose.foundation.layout.Column {
          ContextualRestrictionBanner(
            message = "Media sharing is temporarily restricted.",
            onOpenAccountStatus = {}
          )
          AccountStatusScreen(
            myEnforcement = enforcement,
            warnings = warnings,
            restrictions = restrictions,
            appeals = listOf(
              AppealRecord(id = "a_prev", userId = "me", restrictionId = "r0", status = AppealStatus.APPROVED.name, reason = "Resolved")
            ),
            onBack = {},
            onAcknowledgeWarning = { ackWarningId = it },
            onSubmitAppeal = { _, _, reason -> submittedAppealReason = reason },
            onProvideAppealInfo = { _, _ -> }
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("contextual_restriction_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("account_status_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("account_status_list").performScrollToNode(hasText("Photos & videos"))
    composeTestRule.onNodeWithText("Photos & videos").assertIsDisplayed()

    composeTestRule.onNodeWithTag("account_status_list").performScrollToNode(hasText("Acknowledge"))
    composeTestRule.onNodeWithText("Acknowledge").performClick()
    composeTestRule.waitForIdle()
    assertEquals("w1", ackWarningId)

    composeTestRule.onNodeWithTag("account_status_list").performScrollToNode(hasTestTag("appeal_restriction_r1"))
    composeTestRule.onNodeWithTag("appeal_restriction_r1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("appeal_reason_input").performTextInput("It was a false positive in our squad chat.")
    composeTestRule.onNodeWithTag("submit_appeal_confirm_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals("It was a false positive in our squad chat.", submittedAppealReason)
  }

  @Test
  fun v6SupportCenter_searchesHelpAndOpensOfficialTwoWaySupportChat() {
    var activeId by mutableStateOf<String?>(null)
    var sentSupportReply: String? = null
    val ticket = SupportTicket(
      id = "t1",
      userId = "me",
      userName = "Alpha",
      subject = "Sri Lanka OTP delayed",
      description = "Code took 2 minutes",
      status = "WAITING_ON_USER",
      priority = "HIGH",
      assignedAgentName = "Agent Nimali"
    )
    val msgs = listOf(
      SupportTicketMessage(id = "m1", ticketId = "t1", senderId = "me", senderName = "Alpha", senderRole = "USER", body = "Hi, SMS took a while."),
      SupportTicketMessage(id = "m2", ticketId = "t1", senderId = "agent_1", senderName = "Agent Nimali", senderRole = "SUPPORT_AGENT", body = "Carrier delay cleared, please retry.")
    )

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        SupportCenterScreen(
          myUid = "me",
          myProfile = UserProfile(userId = "me", username = "alpha", displayName = "Alpha"),
          phoneProfile = null,
          helpArticles = SupportRepository.builtInHelpArticles,
          tickets = listOf(ticket),
          activeTicketId = activeId,
          activeMessages = if (activeId == "t1") msgs else emptyList(),
          onSelectTicket = { activeId = it },
          onCreateTicket = { _, _, _, _, _ -> },
          onSendTicketMessage = { _, body -> sentSupportReply = body },
          onBack = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("support_search_input").performTextInput("Sri Lanka")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("help_article_help_otp_srilanka").assertIsDisplayed()

    composeTestRule.onNodeWithTag("support_tab_tickets").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("ticket_card_t1").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("support_ticket_chat_view").assertIsDisplayed()
    composeTestRule.onNodeWithText("Official Support").assertIsDisplayed()
    composeTestRule.onNodeWithTag("support_chat_input").performTextInput("Working now, thank you!")
    composeTestRule.onNodeWithTag("support_chat_send_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals("Working now, thank you!", sentSupportReply)
  }

  @Test
  fun v6CommunityPolicies_rendersAllDraftDocumentsWithDraftBadge() {
    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        CommunityPoliciesScreen(onBack = {})
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("community_policies_screen").assertIsDisplayed()
    assertTrue(
      composeTestRule.onAllNodes(hasText("DRAFT", substring = true))
        .fetchSemanticsNodes().isNotEmpty()
    )
    composeTestRule.onNodeWithTag("policy_tab_enforcement_policy").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("community_policies_list")
      .performScrollToNode(hasText("Human-in-the-Loop Guarantee", substring = true))
    composeTestRule.onNodeWithText("Human-in-the-Loop Guarantee", substring = true).assertIsDisplayed()
  }

  @Test
  fun v6AdminDashboard_renders14SectionsAndEnforcesHumanPermanentBanConfirmation() {
    var appliedPermanent = false
    val targetUser = UserProfile(userId = "u_target", username = "spam_actor", displayName = "Spam Actor")

    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        AdminDashboardScreen(
          users = listOf(targetUser),
          chats = emptyList(),
          reports = emptyList(),
          onBack = {},
          onToggleSuspend = { _, _ -> },
          onUpdateReport = { _, _ -> },
          currentStaffUid = "staff_senior",
          staffRole = DlStaffRole.SENIOR_MODERATOR,
          onApplyEnforcement = { _, status, _, _, _, _, _, humanApproved ->
            if (status == DlAccountStatus.PERM_SUSPENDED && humanApproved) {
              appliedPermanent = true
            }
          }
        )
      }
    }

    composeTestRule.waitForIdle()
    AdminDashboardSection.entries.forEach { sec ->
      composeTestRule.onNodeWithTag("admin_section_${sec.name.lowercase()}").performScrollTo().assertIsDisplayed()
    }

    composeTestRule.onNodeWithTag("admin_section_restrictions_warnings").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("admin_dashboard_list")
      .performScrollToNode(hasTestTag("enforcement_status_perm_suspended"))
    composeTestRule.onNodeWithTag("enforcement_status_perm_suspended").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("admin_dashboard_list")
      .performScrollToNode(hasTestTag("admin_apply_enforcement_button"))
    composeTestRule.onNodeWithTag("admin_apply_enforcement_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Confirm Permanent Enforcement").assertIsDisplayed()
    composeTestRule.onNodeWithTag("confirm_permanent_checkbox").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("confirm_permanent_action_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(appliedPermanent)
  }

  @Test
  fun contactsScreen_opensTextLkContactsPanelAndValidatesInputs() {
    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        ContactsScreen(
          myUid = "me",
          myProfile = UserProfile(userId = "me", username = "alpha", displayName = "Alpha"),
          usersState = UiState.Success(listOf(UserProfile(userId = "u2", username = "nimali", displayName = "Nimali Perera"))),
          contacts = emptyList(),
          contactRequests = emptyList(),
          onOpenDirectChat = {},
          onSendContactRequest = {},
          onRespondContactRequest = { _, _ -> },
          onToggleFavorite = { _, _ -> },
          onToggleBlock = { _, _ -> }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("open_textlk_contacts_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("textlk_contacts_panel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("textlk_group_id_input").performScrollTo().performTextInput("grp_lk_01")
    composeTestRule.onNodeWithTag("textlk_phone_input").performScrollTo().performTextInput("0771234567")
    composeTestRule.onNodeWithTag("textlk_first_name_input").performScrollTo().performTextInput("Nimali")
    composeTestRule.onNodeWithTag("textlk_save_contact_button").performScrollTo().assertIsEnabled()
    composeTestRule.onNodeWithTag("textlk_close_button").performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun textLkGatewayClient_buildsPayloadAndParsesPaginatedContacts() {
    val payload = com.example.core.TextLkGatewayClient.buildContactPayload(
      phone = "94710000000",
      firstName = "Nimali",
      lastName = "Perera",
      extraFields = mapOf("city" to "Colombo")
    )
    assertEquals("94710000000", payload.getString("PHONE"))
    assertEquals("Nimali", payload.getString("FIRST_NAME"))
    assertEquals("Perera", payload.getString("LAST_NAME"))
    assertEquals("Colombo", payload.getString("CITY"))

    val paginatedJson = org.json.JSONObject(
      """
      {
        "status": "success",
        "data": {
          "current_page": 2,
          "last_page": 4,
          "total": 40,
          "data": [
            {
              "uid": "cnt_99",
              "PHONE": "94771234567",
              "FIRST_NAME": "Kasun",
              "LAST_NAME": "Silva"
            }
          ]
        }
      }
      """.trimIndent()
    )
    val page = com.example.core.TextLkGatewayClient.parseContactsPageJson(paginatedJson, "grp_1")
    assertEquals(2, page.currentPage)
    assertEquals(4, page.lastPage)
    assertEquals(40, page.total)
    assertEquals(1, page.contacts.size)
    assertEquals("cnt_99", page.contacts.first().uid)
    assertEquals("Kasun Silva", page.contacts.first().fullName)
    assertEquals("94771234567", page.contacts.first().phone)
  }

  @Test
  fun settingsScreen_aboutAndStorageDisplayBuiltApkExportSetting() {
    composeTestRule.setContent {
      DlChatTheme(themeMode = "DARK") {
        SettingsScreen(
          myProfile = UserProfile(userId = "me", username = "alpha", displayName = "Alpha Lead"),
          settings = null,
          devices = emptyList(),
          savedMessages = emptyList(),
          backups = emptyList(),
          storageBreakdown = StorageBreakdown(voiceCacheBytes = 1024),
          isAdminUser = false,
          onUpdateProfile = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
          onUpdateSettings = {},
          onRevokeDevice = {},
          onAddPersonalNote = {},
          onDeleteSaved = {},
          onRefreshStorage = {},
          onClearLocalCache = {},
          onRotateKeys = {},
          onExportBackup = { _, _ -> },
          onRestoreBackup = { _, _ -> },
          onOpenAdminDashboard = {},
          onSignOut = {},
          onDeleteAccount = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_cat_storage").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("storage_export_apk_button").performScrollTo().assertIsDisplayed().assertIsEnabled()

    composeTestRule.onNodeWithTag("settings_cat_about").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("about_apk_meta_text").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_apk_button").performScrollTo().assertIsDisplayed().assertIsEnabled()
  }

  @Test
  fun localStorageManager_inspectsAndExportsApkToDestinationUri() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val meta = LocalStorageManager.inspectInstalledApk(context)
    assertTrue(meta.suggestedFileName.startsWith("DL-Chat-v"))
    assertTrue(meta.suggestedFileName.endsWith(".apk"))

    val fakeApk = java.io.File(context.cacheDir, "sample-debug.apk").apply {
      writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4))
    }
    val destFile = java.io.File(context.cacheDir, "exported-debug.apk")
    val destUri = android.net.Uri.fromFile(destFile)

    val result = runBlocking {
      LocalStorageManager.exportInstalledApk(
        context = context,
        destinationUri = destUri,
        sourcePathOverride = fakeApk.absolutePath
      )
    }
    assertTrue(result.isSuccess)
    assertEquals(8L, result.getOrNull())
    assertEquals(8L, destFile.length())
  }
}


