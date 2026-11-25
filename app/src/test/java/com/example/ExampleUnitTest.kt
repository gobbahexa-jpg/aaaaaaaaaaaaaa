package com.example

import com.example.core.CryptoIdentityHelper
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.ChatFolder
import com.example.data.model.ChatFolderMatcher
import com.example.data.model.ChatRoom
import com.example.data.model.ChatType
import com.example.data.model.MessageType
import com.example.data.model.OnlineStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ExampleUnitTest {

  @Test
  fun safetyNumber_isSymmetricAndFormatted() {
    val fpAlice = "A1B2-C3D4-E5F6-7890"
    val fpBob = "1122-3344-5566-7788"
    val fpCharlie = "99AA-BBCC-DDEE-FF00"

    val ab = CryptoIdentityHelper.computeSharedSafetyNumber(fpAlice, fpBob)
    val ba = CryptoIdentityHelper.computeSharedSafetyNumber(fpBob, fpAlice)
    val ac = CryptoIdentityHelper.computeSharedSafetyNumber(fpAlice, fpCharlie)

    assertEquals(ab, ba)
    assertNotEquals(ab, ac)
    assertTrue(
      "Expected 6 groups of 4 digits, got: $ab",
      Regex("^\\d{4}( \\d{4}){5}$").matches(ab)
    )
  }

  @Test
  fun ecdhSharedAesKey_isSymmetricAcrossPeers() {
    val alice = CryptoIdentityHelper.generateEcKeyPair()
    val bob = CryptoIdentityHelper.generateEcKeyPair()
    val charlie = CryptoIdentityHelper.generateEcKeyPair()

    val aliceToBob = CryptoIdentityHelper.deriveSharedAesKey(alice.private, bob.public, "chat:dm_ab")
    val bobToAlice = CryptoIdentityHelper.deriveSharedAesKey(bob.private, alice.public, "chat:dm_ab")
    val aliceToCharlie = CryptoIdentityHelper.deriveSharedAesKey(alice.private, charlie.public, "chat:dm_ac")

    assertEquals(32, aliceToBob.size)
    assertArrayEquals(aliceToBob, bobToAlice)
    assertFalse(aliceToBob.contentEquals(aliceToCharlie))
  }

  @Test
  fun chatRoom_resolveTitle_unreadAndTypingIndicatorsWork() {
    val nowMs = System.currentTimeMillis()
    val dm = ChatRoom(
      id = "dm_1",
      type = "DIRECT",
      title = "Fallback",
      participantNames = mapOf("u1" to "Alice", "u2" to "Bob"),
      lastMessageSenderId = "u2",
      lastMessageAt = Timestamp(Date(nowMs)),
      readCursors = mapOf("u1" to nowMs - 60_000L),
      typingUids = mapOf("u2" to nowMs - 1_500L, "u1" to nowMs)
    )
    assertEquals("Bob", dm.resolveTitle("u1"))
    assertEquals("Alice", dm.resolveTitle("u2"))
    assertTrue(dm.hasUnreadFor("u1"))
    assertFalse(dm.hasUnreadFor("u2"))
    assertEquals(listOf("Bob"), dm.activeTypersExcluding("u1", nowMs))

    val group = ChatRoom(
      id = "grp_1",
      type = "GROUP",
      title = "Death Legion Core",
      participantNames = mapOf("u1" to "Alice", "u2" to "Bob")
    )
    assertEquals("Death Legion Core", group.resolveTitle("u1"))
  }

  @Test
  fun chatFolderMatcher_handlesBuiltInAndCustomIncludeExcludeRules() {
    val nowMs = System.currentTimeMillis()
    val dmFav = ChatRoom(
      id = "dm_fav",
      type = "DIRECT",
      memberUids = listOf("me", "fav_user"),
      lastMessageSenderId = "fav_user",
      lastMessageAt = Timestamp(Date(nowMs)),
      readCursors = mapOf("me" to nowMs - 10_000L)
    )
    val grpExcluded = ChatRoom(
      id = "grp_muted",
      type = "GROUP",
      memberUids = listOf("me", "other")
    )
    val archived = ChatRoom(
      id = "dm_arch",
      type = "DIRECT",
      memberUids = listOf("me", "other"),
      archivedBy = listOf("me")
    )

    assertTrue(ChatFolderMatcher.matchesFolder(dmFav, "me", "UNREAD"))
    assertTrue(ChatFolderMatcher.matchesFolder(dmFav, "me", "FAVORITE", favoritePeerUids = setOf("fav_user")))
    assertFalse(ChatFolderMatcher.matchesFolder(grpExcluded, "me", "FAVORITE", favoritePeerUids = setOf("fav_user")))
    assertTrue(ChatFolderMatcher.matchesFolder(archived, "me", "ARCHIVED"))
    assertFalse(ChatFolderMatcher.matchesFolder(archived, "me", "ALL"))

    val customFolder = ChatFolder(
      id = "f1",
      ownerUid = "me",
      name = "VIP Ops",
      filterType = "CUSTOM",
      includeChatIds = listOf("dm_fav"),
      excludeChatIds = listOf("grp_muted")
    )
    assertTrue(ChatFolderMatcher.matchesFolder(dmFav, "me", "FOLDER:f1", customFolder = customFolder))
    assertFalse(ChatFolderMatcher.matchesFolder(grpExcluded, "me", "FOLDER:f1", customFolder = customFolder))
  }

  @Test
  fun enumParsers_handleValidAndFallbackValues() {
    assertEquals(ChatType.CHANNEL, ChatType.fromRaw("CHANNEL"))
    assertEquals(ChatType.DIRECT, ChatType.fromRaw("UNKNOWN"))
    assertEquals(ChatType.DIRECT, ChatType.fromRaw(null))

    assertEquals(MessageType.POLL, MessageType.fromRaw("POLL"))
    assertEquals(MessageType.DOCUMENT, MessageType.fromRaw("DOCUMENT"))
    assertEquals(MessageType.TEXT, MessageType.fromRaw("INVALID"))

    assertEquals(OnlineStatus.DND, OnlineStatus.fromRaw("DND"))
    assertEquals(OnlineStatus.ONLINE, OnlineStatus.fromRaw(null))

    assertEquals(CallType.VIDEO, CallType.fromRaw("VIDEO"))
    assertEquals(CallType.VOICE, CallType.fromRaw("OTHER"))

    assertEquals(CallStatus.ACTIVE, CallStatus.fromRaw("ACTIVE"))
    assertEquals(CallStatus.ENDED, CallStatus.fromRaw("OTHER"))
  }

  @Test
  fun registrationInput_validatesAndSanitizesHandles() {
    val valid = com.example.data.repository.RegistrationInput(
      username = "  Nova.Sentinel! ",
      displayName = "Nova Sentinel",
      bio = "Signal Operator"
    )
    assertEquals("nova_sentinel", valid.sanitizedUsername())
    assertEquals(null, com.example.data.repository.AuthRepository.validateRegistrationInput(valid))

    val missingName = valid.copy(displayName = "   ")
    assertTrue(com.example.data.repository.AuthRepository.validateRegistrationInput(missingName) != null)

    val shortHandle = valid.copy(username = "ab")
    assertTrue(com.example.data.repository.AuthRepository.validateRegistrationInput(shortHandle) != null)
  }

  @Test
  fun authUiStates_exposeSanitizedPreviewAndCombinedErrors() {
    val registerState = com.example.ui.auth.RegisterUiState(
      displayName = "  Death Legion Lead ",
      username = "DL.Lead#1",
      validationError = "Handle error",
      authError = "Auth error"
    )
    assertEquals("dl_lead_1", registerState.previewHandle)
    assertEquals("Death Legion Lead", registerState.previewDisplayName)
    assertEquals("Handle error", registerState.combinedError)
    assertEquals(false, registerState.isEmailPasswordEnabled)

    val loginState = com.example.ui.auth.LoginUiState()
    assertEquals(false, loginState.isEmailPasswordEnabled)
  }

  @Test
  fun offlineSyncEngine_resolveLwwConflict_prefersLatestOrLocalTie() {
    assertEquals(
      com.example.core.SyncConflictWinner.LOCAL,
      com.example.core.OfflineSyncEngine.resolveLwwConflict(2000L, 1000L)
    )
    assertEquals(
      com.example.core.SyncConflictWinner.LOCAL,
      com.example.core.OfflineSyncEngine.resolveLwwConflict(2000L, 2000L)
    )
    assertEquals(
      com.example.core.SyncConflictWinner.REMOTE,
      com.example.core.OfflineSyncEngine.resolveLwwConflict(1000L, 2000L)
    )
  }

  @Test
  fun qrCodeUtils_sha256Hex_isDeterministicAnd64HexChars() {
    val hash1 = com.example.core.QrCodeUtils.sha256Hex("secret-pairing-token-123")
    val hash2 = com.example.core.QrCodeUtils.sha256Hex("secret-pairing-token-123")
    val hash3 = com.example.core.QrCodeUtils.sha256Hex("secret-pairing-token-456")

    assertEquals(64, hash1.length)
    assertEquals(hash1, hash2)
    assertNotEquals(hash1, hash3)
  }

  @Test
  fun chatFolderMatcher_botFolderAndStickerAndPremiumCatalogWork() {
    val botChat = ChatRoom(
      id = "bot_1",
      type = "CHANNEL",
      title = "DL Support Bot",
      channelHandle = "dl_helper_bot"
    )
    val normalChat = ChatRoom(
      id = "grp_1",
      type = "GROUP",
      title = "Squad Alpha"
    )
    assertTrue(ChatFolderMatcher.matchesFolder(botChat, "me", "BOT"))
    assertFalse(ChatFolderMatcher.matchesFolder(normalChat, "me", "BOT"))

    assertTrue(com.example.data.model.DlStickerPack.builtInStickers.size >= 8)
    assertEquals("Legion Shield", com.example.data.model.DlStickerPack.findById("dl_shield")?.label)
    assertTrue(com.example.data.model.DlPremiumCatalog.features.size >= 5)
  }

  @Test
  fun deathLegionOAuthClient_usesConfiguredDefaults() {
    assertEquals(
      "f182c16d374a31f263e025557fe78f93",
      com.example.core.DeathLegionOAuthClient.DEFAULT_CLIENT_ID
    )
    assertEquals(
      "dlchat://oauth/deathlegion/callback",
      com.example.core.DeathLegionOAuthClient.DEFAULT_REDIRECT_URI
    )
    assertEquals(
      "profile email identity unit",
      com.example.core.DeathLegionOAuthClient.DEFAULT_SCOPES
    )
  }

  @Test
  fun phoneNumberHelper_defaultsToSriLankaAndNormalizesE164() {
    val lk = com.example.core.PhoneNumberHelper.defaultCountry
    assertEquals("LK", lk.isoCode)
    assertEquals("+94", lk.dialCode)

    assertEquals("+94771234567", com.example.core.PhoneNumberHelper.normalizeToE164(lk, "077 123 4567"))
    assertEquals("+94771234567", com.example.core.PhoneNumberHelper.normalizeToE164(lk, "0094771234567"))
    assertEquals("+94771234567", com.example.core.PhoneNumberHelper.normalizeToE164(lk, "+94 77 123 4567"))
    assertEquals("+94771234567", com.example.core.PhoneNumberHelper.normalizeToE164(lk, "771234567"))
    assertEquals(null, com.example.core.PhoneNumberHelper.normalizeToE164(lk, "123"))

    val us = com.example.core.PhoneNumberHelper.findCountry("US")
    assertEquals("+14155550142", com.example.core.PhoneNumberHelper.normalizeToE164(us, "(415) 555-0142"))
    assertEquals("LK", com.example.core.PhoneNumberHelper.searchCountries("Sri").first().isoCode)
    assertEquals("LK", com.example.core.PhoneNumberHelper.searchCountries("+94").first().isoCode)

    val masked = com.example.core.PhoneNumberHelper.maskPhone("+94771234567")
    assertTrue(masked.startsWith("+947"))
    assertTrue(masked.endsWith("4567"))
    assertFalse(masked.contains("7123"))
  }

  @Test
  fun userEnforcement_enforcesScopedFeaturesExpiriesAndGlobalSuspension() {
    val nowMs = 1_750_000_000_000L
    val scoped = com.example.data.model.UserEnforcement(
      userId = "u_scoped",
      accountStatus = com.example.data.model.DlAccountStatus.MEDIA_RESTRICTED.name,
      featureExpiries = mapOf(
        com.example.data.model.DlFeatureRestriction.SEND_MEDIA.key to (nowMs + 3_600_000L),
        com.example.data.model.DlFeatureRestriction.START_CALLS.key to (nowMs - 1_000L)
      )
    )
    assertFalse(scoped.isGloballySuspended(nowMs))
    assertTrue(scoped.isFeatureBlocked(com.example.data.model.DlFeatureRestriction.SEND_MEDIA, nowMs))
    assertFalse(scoped.isFeatureBlocked(com.example.data.model.DlFeatureRestriction.START_CALLS, nowMs))
    assertFalse(scoped.isFeatureBlocked(com.example.data.model.DlFeatureRestriction.SEND_DM, nowMs))
    assertEquals(1, scoped.activeBlockedFeatures(nowMs).size)

    val permBan = com.example.data.model.UserEnforcement(
      userId = "u_perm",
      accountStatus = com.example.data.model.DlAccountStatus.PERM_SUSPENDED.name,
      globalExpiryEpochMs = -1L
    )
    assertTrue(permBan.isGloballySuspended(nowMs))
    assertEquals(14, permBan.activeBlockedFeatures(nowMs).size)
  }

  @Test
  fun antiSpamEngine_rateLimitsAndAssessesReportRisk() {
    val triage = com.example.data.repository.AntiSpamEngine.assessReportRisk(
      category = com.example.data.model.ReportCategory.SCAM,
      reason = "Asked for OTP code to verify wallet",
      evidenceSnippet = "https://bit.ly/claim-reward"
    )
    assertTrue(triage.severityScore >= 80)
    assertTrue(triage.shouldOpenCase)
    assertEquals("HIGH_RISK_HUMAN_REVIEW", triage.label)

    val baseMs = System.currentTimeMillis() + 50_000_000L
    val r1 = com.example.data.repository.AntiSpamEngine.checkAndRecordOutgoingMessage(
      text = "Hello",
      maxPerWindow = 2,
      windowMs = 10_000L,
      nowMs = baseMs
    )
    val r2 = com.example.data.repository.AntiSpamEngine.checkAndRecordOutgoingMessage(
      text = "Second",
      maxPerWindow = 2,
      windowMs = 10_000L,
      nowMs = baseMs + 100L
    )
    val r3 = com.example.data.repository.AntiSpamEngine.checkAndRecordOutgoingMessage(
      text = "Burst",
      maxPerWindow = 2,
      windowMs = 10_000L,
      nowMs = baseMs + 200L
    )
    val r4 = com.example.data.repository.AntiSpamEngine.checkAndRecordOutgoingMessage(
      text = "After cooldown",
      maxPerWindow = 2,
      windowMs = 10_000L,
      nowMs = baseMs + 11_000L
    )
    assertTrue(r1.first)
    assertTrue(r2.first)
    assertFalse(r3.first)
    assertTrue(r4.first)
  }

  @Test
  fun v6EnumsAndStaffRbacHierarchy_matchSpecification() {
    assertEquals(13, com.example.data.model.DlAccountStatus.entries.size)
    assertEquals(14, com.example.data.model.DlFeatureRestriction.entries.size)
    assertEquals(13, com.example.data.model.SupportCategory.entries.size)
    assertEquals(6, com.example.data.model.ReportCategory.entries.size)

    assertFalse(com.example.data.model.DlStaffRole.USER.canOpenStaffConsole)
    assertTrue(com.example.data.model.DlStaffRole.SUPPORT_AGENT.canOpenStaffConsole)
    assertFalse(com.example.data.model.DlStaffRole.SUPPORT_AGENT.canModerateUsers)
    assertTrue(com.example.data.model.DlStaffRole.MODERATOR.canModerateUsers)
    assertFalse(com.example.data.model.DlStaffRole.MODERATOR.canPermanentBan)
    assertTrue(com.example.data.model.DlStaffRole.SENIOR_MODERATOR.canPermanentBan)
    assertFalse(com.example.data.model.DlStaffRole.SENIOR_MODERATOR.canManageRolesAndPlatform)
    assertTrue(com.example.data.model.DlStaffRole.ADMIN.canManageRolesAndPlatform)
    assertTrue(com.example.data.model.DlStaffRole.SUPER_ADMIN.canManageRolesAndPlatform)
  }

  @Test
  fun textLkGatewayClient_normalizesPhonesAndValidatesUids() {
    assertEquals("94710000000", com.example.core.TextLkGatewayClient.toTextLkPhone("0710000000"))
    assertEquals("94771234567", com.example.core.TextLkGatewayClient.toTextLkPhone("+94 77 123 4567"))
    assertEquals("94771234567", com.example.core.TextLkGatewayClient.toTextLkPhone("0094771234567"))
    assertEquals("94771234567", com.example.core.TextLkGatewayClient.toTextLkPhone("771234567"))
    assertEquals(null, com.example.core.TextLkGatewayClient.toTextLkPhone("123"))

    assertTrue(com.example.core.TextLkGatewayClient.isValidUid("grp_srilanka_01"))
    assertFalse(com.example.core.TextLkGatewayClient.isValidUid("../bad"))
    assertFalse(com.example.core.TextLkGatewayClient.isValidUid("ab"))
  }
}


