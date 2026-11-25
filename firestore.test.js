const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const EVE_UID = "eve_uid_3";
const ADMIN_UID = "admin_uid_0";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

function aliceProfilePayload(ts) {
  return {
    userId: ALICE_UID,
    username: "alice_dl",
    displayName: "Alice Vanguard",
    bio: "Death Legion Operator",
    avatarColor: "#00E5FF",
    onlineStatus: "ONLINE",
    showLastSeen: true,
    showOnline: true,
    readReceipts: true,
    discoverable: true,
    suspended: false,
    publicKeyFingerprint: "A1B2-C3D4-E5F6-7890",
    lastSeenAt: ts,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  const ts = pastDate();
  await assertFails(unauth.collection("users").where("discoverable", "==", true).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).set(aliceProfilePayload(ts)));
  await assertFails(unauth.collection("chats").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("stories").where("discoverable", "==", true).get());
  await assertFails(unauth.collection("calls").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("reports").where("reporterId", "==", ALICE_UID).get());
});

test("UserProfile: owner create/update, discoverable query, suspended tamper rejection, and PII isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(aliceProfilePayload(ts)));

  // Reject ghost `id` field (@DocumentId parity)
  await assertFails(
    aliceDb.collection("users").doc("extra_user").set({
      ...aliceProfilePayload(ts),
      id: "extra_user",
      userId: "extra_user",
    })
  );

  // Reject future timestamp
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      ...aliceProfilePayload(new Date(Date.now() + 60000)),
      userId: BOB_UID,
      username: "bob_dl",
    })
  );

  // Owner partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Updated DL Bio",
      onlineStatus: "AWAY",
      updatedAt: pastDate(500),
    })
  );

  // Owner cannot self-unsuspend or mutate suspended flag
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      suspended: true,
      updatedAt: pastDate(500),
    })
  );

  // Discoverable query succeeds for signed-in users; unfiltered list fails when non-discoverable exists
  await assertSucceeds(
    bobDb.collection("users").where("discoverable", "==", true).get()
  );
  await assertFails(bobDb.collection("users").get());

  // Private PII isolation
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get()
  );
});

test("User subcollections: settings, devices, contacts, folders, saved_messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(4000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").set({
      userId: ALICE_UID,
      themeMode: "DARK",
      accentColorHex: "#00E5FF",
      bubbleStyle: "MODERN",
      fontScale: 1.0,
      wallpaperId: "nebula",
      notificationsEnabled: true,
      soundEnabled: true,
      dndEnabled: false,
      aiEnabled: true,
      autoDownloadMedia: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_1").set({
      userId: ALICE_UID,
      deviceName: "Pixel 9 Pro",
      platform: "Android",
      revoked: false,
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("contacts").doc(BOB_UID).set({
      ownerUid: ALICE_UID,
      contactUid: BOB_UID,
      displayName: "Bob",
      username: "bob_dl",
      favorite: true,
      blocked: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("folders").doc("folder_work").set({
      ownerUid: ALICE_UID,
      name: "Squad",
      filterType: "GROUP",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("saved_messages").doc("saved_1").set({
      ownerUid: ALICE_UID,
      text: "Launch coordinates: 37.7749, -122.4194",
      sourceChatName: "Personal Notes",
      noteCategory: "NOTE",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Chats and messages: direct/group messaging, adminOnlyPost channel, reactions, and polls", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const ts = pastDate(5000);

  // Create DM room between Alice and Bob
  await assertSucceeds(
    aliceDb.collection("chats").doc("dm_alice_bob").set({
      type: "DIRECT",
      title: "Alice & Bob",
      description: "",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      publicVisible: false,
      adminOnlyPost: false,
      e2eeEnabled: true,
      archivedBy: [],
      mutedBy: [],
      lastMessageText: "",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: ts,
      disappearingSeconds: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Filtered memberUids query succeeds; unfiltered query fails
  await assertSucceeds(
    bobDb.collection("chats").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(eveDb.collection("chats").get());

  // Alice sends message + poll
  await assertSucceeds(
    aliceDb.collection("chats").doc("dm_alice_bob").collection("messages").doc("msg_1").set({
      chatId: "dm_alice_bob",
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      publicChannel: false,
      type: "POLL",
      text: "Next squad sync?",
      edited: false,
      deletedForEveryone: false,
      reactions: {},
      pollQuestion: "Next squad sync?",
      pollOptions: ["18:00 UTC", "20:00 UTC"],
      pollVotes: {},
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Eve (non-member) cannot send or read message
  await assertFails(
    eveDb.collection("chats").doc("dm_alice_bob").collection("messages").doc("msg_eve").set({
      chatId: "dm_alice_bob",
      senderId: EVE_UID,
      senderName: "Eve",
      memberUids: [ALICE_UID, BOB_UID, EVE_UID],
      publicChannel: false,
      type: "TEXT",
      text: "Intruder",
      edited: false,
      deletedForEveryone: false,
      reactions: {},
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    eveDb.collection("chats").doc("dm_alice_bob").collection("messages").doc("msg_1").get()
  );

  // Bob reacts and votes in poll
  await assertSucceeds(
    bobDb.collection("chats").doc("dm_alice_bob").collection("messages").doc("msg_1").update({
      reactions: { [BOB_UID]: "🔥" },
      pollVotes: { [BOB_UID]: "0" },
      updatedAt: pastDate(500),
    })
  );

  // Alice creates a public admin-only Channel
  await assertSucceeds(
    aliceDb.collection("chats").doc("chan_dl_news").set({
      type: "CHANNEL",
      title: "DL Announcements",
      description: "Official Death Legion updates",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      adminUids: [ALICE_UID],
      participantNames: { [ALICE_UID]: "Alice" },
      publicVisible: true,
      channelHandle: "dl_news",
      adminOnlyPost: true,
      e2eeEnabled: false,
      archivedBy: [],
      mutedBy: [],
      lastMessageText: "Channel created",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: ts,
      disappearingSeconds: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob discovers and subscribes to public channel
  await assertSucceeds(
    bobDb.collection("chats").where("type", "==", "CHANNEL").where("publicVisible", "==", true).get()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("chan_dl_news").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(400),
    })
  );

  // Bob (non-admin subscriber) cannot post to adminOnlyPost channel
  await assertFails(
    bobDb.collection("chats").doc("chan_dl_news").collection("messages").doc("post_bob").set({
      chatId: "chan_dl_news",
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      publicChannel: true,
      type: "TEXT",
      text: "Unauthorized broadcast",
      edited: false,
      deletedForEveryone: false,
      reactions: {},
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Stories, Calls (with terminal state lock), and Reports (with email spoofing protection)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "gobbahexa@gmail.com",
    email_verified: false,
  }).firestore();
  const verifiedAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "gobbahexa@gmail.com",
    email_verified: true,
  }).firestore();
  const ts = pastDate(5000);

  // Story with hiddenFromUids
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      type: "TEXT",
      text: "Testing DL Chat Stories!",
      backgroundHex: "#00E5FF",
      viewedBy: [],
      reactions: {},
      hiddenFromUids: [EVE_UID],
      discoverable: true,
      expiresAtEpochMs: Date.now() + 86400000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("stories").doc("story_1").update({
      viewedBy: [BOB_UID],
      reactions: { [BOB_UID]: "⚡" },
      updatedAt: pastDate(400),
    })
  );
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  await assertFails(eveDb.collection("stories").doc("story_1").get());

  // Call lifecycle & terminal lock
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      callType: "VOICE",
      status: "RINGING",
      durationSeconds: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "ACTIVE",
      updatedAt: pastDate(300),
    })
  );
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ENDED",
      durationSeconds: 42,
      updatedAt: pastDate(200),
    })
  );
  // Terminal lock prevents mutating ended call
  await assertFails(
    bobDb.collection("calls").doc("call_1").update({
      status: "ACTIVE",
      updatedAt: pastDate(100),
    })
  );

  // Reports & Admin anti-spoofing
  await assertSucceeds(
    bobDb.collection("reports").doc("rep_1").set({
      reporterId: BOB_UID,
      targetUid: EVE_UID,
      targetType: "USER",
      reason: "Spam links in group",
      status: "OPEN",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(spoofedAdminDb.collection("reports").doc("rep_1").get());
  await assertSucceeds(verifiedAdminDb.collection("reports").doc("rep_1").get());
  await assertSucceeds(
    verifiedAdminDb.collection("reports").doc("rep_1").update({
      status: "REVIEWED",
      updatedAt: pastDate(100),
    })
  );
});

test("V2.0: Contact requests, encrypted messages, read/starred receipts, folders V2, backups, and WebRTC SDP signaling", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const ts = pastDate(5000);

  // UserProfile with ECDH public key & rotation version
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...aliceProfilePayload(ts),
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFakePubKeyExampleForTest",
      keyVersion: 2,
      twoFactorEnabled: true,
      profilePhotoVisibility: "CONTACTS",
    })
  );

  // Contact request lifecycle
  await assertSucceeds(
    aliceDb.collection("contact_requests").doc("req_alice_bob").set({
      senderId: ALICE_UID,
      senderName: "Alice",
      senderUsername: "alice_dl",
      receiverId: BOB_UID,
      receiverName: "Bob",
      status: "PENDING",
      message: "Add me on DL Chat",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  // Eve cannot read Alice->Bob contact request
  await assertFails(eveDb.collection("contact_requests").doc("req_alice_bob").get());
  // Bob accepts
  await assertSucceeds(
    bobDb.collection("contact_requests").doc("req_alice_bob").update({
      status: "ACCEPTED",
      updatedAt: pastDate(300),
    })
  );

  // Backup metadata & custom folder with include/exclude
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("bkp_1").set({
      ownerUid: ALICE_UID,
      fileName: "dlchat_backup_v2.dlbkp",
      itemCount: 42,
      sizeBytes: 12040,
      encryptionAlg: "AES-256-GCM",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("backups").doc("bkp_1").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("folders").doc("f_unread").set({
      ownerUid: ALICE_UID,
      name: "Unread Priority",
      filterType: "UNREAD",
      includeChatIds: ["dm_alice_bob"],
      excludeChatIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Group V2 with invite code & join request + encrypted message + read/star
  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_v2").set({
      type: "GROUP",
      title: "Ops Room",
      description: "Encrypted V2 squad",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      publicVisible: false,
      adminOnlyPost: false,
      e2eeEnabled: true,
      archivedBy: [],
      mutedBy: [],
      lastMessageText: "",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: ts,
      disappearingSeconds: 0,
      inviteCode: "DL-OPS-9921",
      inviteRevoked: false,
      joinRequestUids: [],
      allowPinning: true,
      allowMemberInvites: true,
      themeAccentHex: "#6C5CE7",
      adminActivityLog: ["Group created by Alice"],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Eve can submit a join request cleanly without adding herself directly to memberUids
  await assertSucceeds(
    eveDb.collection("chats").doc("grp_v2").update({
      joinRequestUids: [EVE_UID],
      updatedAt: pastDate(300),
    })
  );
  await assertFails(
    eveDb.collection("chats").doc("grp_v2").update({
      memberUids: [ALICE_UID, BOB_UID, EVE_UID],
      updatedAt: pastDate(300),
    })
  );

  // Encrypted E2EE message & read/starred receipts
  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_v2").collection("messages").doc("enc_1").set({
      chatId: "grp_v2",
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      publicChannel: false,
      type: "VOICE_NOTE",
      text: "Voice note (0:06)",
      encrypted: true,
      ciphertextBase64: "dGVzdGNpcGhlcnRleHQ=",
      ivBase64: "MTIzNDU2Nzg5MDEy",
      senderKeyVersion: 2,
      audioDurationMs: 6000,
      waveformSamples: [12, 48, 82, 34, 65],
      edited: false,
      deletedForEveryone: false,
      pinnedInChat: false,
      reactions: {},
      deliveredTo: [ALICE_UID],
      readBy: [ALICE_UID],
      starredBy: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("chats").doc("grp_v2").collection("messages").doc("enc_1").update({
      readBy: [ALICE_UID, BOB_UID],
      starredBy: [BOB_UID],
      updatedAt: pastDate(200),
    })
  );

  // WebRTC SDP signaling on call session
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_v2").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      callType: "VIDEO",
      status: "RINGING",
      durationSeconds: 0,
      sdpOffer: "v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\n",
      iceCandidates: ["candidate:1 1 UDP 2122252543 192.0.2.1 54321 typ host"],
      signalingNote: "SDP offer published; TURN/SFU media server required for NAT media.",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc("call_v2").update({
      status: "ACTIVE",
      sdpAnswer: "v=0\r\no=- 67890 2 IN IP4 127.0.0.1\r\n",
      updatedAt: pastDate(100),
    })
  );
});

test("V3: QR device pairing state machine, replay prevention, and zero-trust device registration", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(3000);
  const expiresFuture = Date.now() + 300000;

  // Attempting to register a DESKTOP device directly without an approved pairing session fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("desk_unauthorized").set({
      userId: ALICE_UID,
      deviceName: "Rogue Desktop",
      platform: "Linux",
      deviceType: "DESKTOP",
      revoked: false,
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Create short-lived PENDING QR pairing session
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devicePairingSessions").doc("pair_1").set({
      ownerUid: ALICE_UID,
      tokenHash: "sha256_9f86d081884c7d659a2feaa0c55ad015",
      nonce: "nonce_xyz_12345",
      targetDeviceName: "DL Workstation",
      targetDeviceType: "DESKTOP",
      targetOs: "Ubuntu 24.04",
      targetBrowser: "DL Web Client",
      status: "PENDING",
      expiresAtEpochMs: expiresFuture,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read or approve Alice's pairing session
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("devicePairingSessions").doc("pair_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("devicePairingSessions").doc("pair_1").update({
      status: "APPROVED",
      updatedAt: pastDate(500),
    })
  );

  // Registering DESKTOP while session is still PENDING fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("desk_1").set({
      userId: ALICE_UID,
      deviceName: "DL Workstation",
      platform: "Linux",
      deviceType: "DESKTOP",
      pairingSessionId: "pair_1",
      pairingTokenHash: "sha256_9f86d081884c7d659a2feaa0c55ad015",
      revoked: false,
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Approve pairing from authenticated device
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devicePairingSessions").doc("pair_1").update({
      status: "APPROVED",
      updatedAt: pastDate(400),
    })
  );

  // Registering with wrong tokenHash fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("desk_wrong_hash").set({
      userId: ALICE_UID,
      deviceName: "DL Workstation",
      platform: "Linux",
      deviceType: "DESKTOP",
      pairingSessionId: "pair_1",
      pairingTokenHash: "sha256_wrong_hash_000000000000000",
      revoked: false,
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Authorized linked DESKTOP device registration succeeds while APPROVED
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("desk_1").set({
      userId: ALICE_UID,
      deviceName: "DL Workstation",
      platform: "Linux",
      deviceType: "DESKTOP",
      osName: "Ubuntu 24.04",
      browserName: "DL Web",
      connectionStatus: "ONLINE",
      pairingSessionId: "pair_1",
      pairingTokenHash: "sha256_9f86d081884c7d659a2feaa0c55ad015",
      tokenRotationCount: 1,
      revoked: false,
      firstLinkedAt: ts,
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Mark pairing session CONSUMED (single-use)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devicePairingSessions").doc("pair_1").update({
      status: "CONSUMED",
      updatedAt: pastDate(200),
    })
  );

  // Replay attempt: cannot revert CONSUMED session back to APPROVED or re-register device
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devicePairingSessions").doc("pair_1").update({
      status: "APPROVED",
      updatedAt: pastDate(100),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("desk_replay").set({
      userId: ALICE_UID,
      deviceName: "Replay Attack",
      platform: "Linux",
      deviceType: "DESKTOP",
      pairingSessionId: "pair_1",
      pairingTokenHash: "sha256_9f86d081884c7d659a2feaa0c55ad015",
      revoked: false,
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Revoke device and verify it cannot un-revoke itself
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("desk_1").update({
      revoked: true,
      connectionStatus: "REVOKED",
      updatedAt: pastDate(100),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("desk_1").update({
      revoked: false,
      updatedAt: pastDate(50),
    })
  );
});

test("V3: Multi-account isolation, deviceKeyBundles (no private keys), syncEvents, privateChatSettings, drafts, exports", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(3000);

  // Device public key bundle succeeds, private keys rejected
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("deviceKeyBundles").doc("dev_android_1").set({
      ownerUid: ALICE_UID,
      deviceId: "dev_android_1",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEV3PublicBundleKey",
      fingerprint: "V3FP-1122-3344",
      keyVersion: 1,
      revoked: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("deviceKeyBundles").doc("dev_bad").set({
      ownerUid: ALICE_UID,
      deviceId: "dev_bad",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEV3PublicBundleKey",
      privateKeyBase64: "SECRET_SHOULD_FAIL",
      fingerprint: "V3FP-1122-3344",
      keyVersion: 1,
      revoked: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  // Signed-in Bob can fetch Alice's public device bundle for E2EE verification
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("deviceKeyBundles").doc("dev_android_1").get()
  );

  // Account sessions, deviceNotifications, syncEvents, privateChatSettings, drafts, chatExports are strictly owner-only
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("accountSessions").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice Vanguard",
      username: "alice_dl",
      active: true,
      lastUsedAtEpochMs: Date.now() - 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("accountSessions").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("deviceNotifications").doc("notif_1").set({
      userId: ALICE_UID,
      targetDeviceId: "desk_1",
      title: "New device linked",
      body: "DL Workstation linked via QR.",
      category: "NEW_DEVICE",
      read: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("deviceNotifications").doc("notif_1").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("syncEvents").doc("sync_1").set({
      ownerUid: ALICE_UID,
      entityType: "DRAFT",
      entityId: "dm_alice_bob",
      operation: "UPSERT",
      versionEpochMs: Date.now() - 500,
      deviceId: "dev_android_1",
      payloadSummary: "Draft synced",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("syncEvents").doc("sync_1").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("privateChatSettings").doc("dm_alice_bob").set({
      ownerUid: ALICE_UID,
      chatId: "dm_alice_bob",
      hidden: true,
      locked: true,
      pinnedSortOrder: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("privateChatSettings").doc("dm_alice_bob").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatDrafts").doc("dm_alice_bob").set({
      ownerUid: ALICE_UID,
      chatId: "dm_alice_bob",
      draftText: "Unsent synced tactical message",
      updatedAtEpochMs: Date.now() - 500,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chatDrafts").doc("dm_alice_bob").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatExports").doc("exp_1").set({
      ownerUid: ALICE_UID,
      chatId: "dm_alice_bob",
      chatTitle: "Alice & Bob",
      format: "JSON",
      itemCount: 15,
      sizeBytes: 4096,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chatExports").doc("exp_1").get());

  // UserProfile premiumTier client escalation prevention
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(aliceProfilePayload(ts)));
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      premiumTier: "PRO",
      updatedAt: pastDate(100),
    })
  );
});

test("V3: Group forum topics, group events + RSVP, restrictedUids enforcement, and QUIZ/editHistory messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const ts = pastDate(4000);

  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_v3").set({
      type: "GROUP",
      title: "Death Legion Command",
      description: "Topics, Events, Slow Mode",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      publicVisible: false,
      adminOnlyPost: false,
      e2eeEnabled: true,
      archivedBy: [],
      mutedBy: [],
      pinnedMessages: [],
      lastMessageText: "",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: ts,
      disappearingSeconds: 0,
      slowModeSeconds: 10,
      restrictedUids: [BOB_UID],
      antiSpamEnabled: true,
      topicsEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice creates a forum topic & group event
  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_v3").collection("topics").doc("topic_ops").set({
      chatId: "grp_v3",
      creatorId: ALICE_UID,
      title: "Mission Planning",
      iconEmoji: "🛡️",
      closed: false,
      messageCount: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_v3").collection("events").doc("evt_1").set({
      chatId: "grp_v3",
      creatorId: ALICE_UID,
      title: "Legion Weekly Sync",
      description: "Architecture & Encryption Review",
      startsAtEpochMs: Date.now() + 86400000,
      rsvpUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Eve (non-member) cannot read topics or events
  await assertFails(eveDb.collection("chats").doc("grp_v3").collection("topics").get());
  await assertFails(eveDb.collection("chats").doc("grp_v3").collection("events").get());

  // Restricted Bob cannot post messages or topics, though he can RSVP to existing event
  await assertFails(
    bobDb.collection("chats").doc("grp_v3").collection("messages").doc("msg_restricted").set({
      chatId: "grp_v3",
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      publicChannel: false,
      type: "TEXT",
      text: "Muted attempt",
      edited: false,
      deletedForEveryone: false,
      reactions: {},
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("grp_v3").collection("events").doc("evt_1").update({
      rsvpUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(200),
    })
  );

  // Alice sends a QUIZ message in topic_ops and edits with editHistory
  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_v3").collection("messages").doc("quiz_1").set({
      chatId: "grp_v3",
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      publicChannel: false,
      type: "QUIZ",
      text: "Which curve does DL Chat E2EE use?",
      edited: false,
      editHistory: [],
      deletedForEveryone: false,
      reactions: {},
      pollQuestion: "Which curve does DL Chat E2EE use?",
      pollOptions: ["secp256r1 (P-256)", "RSA-1024"],
      pollVotes: {},
      quizCorrectIndex: 0,
      quizExplanation: "ECDH secp256r1 with AES-256-GCM",
      topicId: "topic_ops",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_v3").collection("messages").doc("quiz_1").update({
      text: "Which elliptic curve does DL Chat E2EE use?",
      edited: true,
      editHistory: ["Which curve does DL Chat E2EE use?"],
      updatedAt: pastDate(100),
    })
  );
});

test("V6: RBAC roles, server-clock restriction enforcement & auto-expiry, and human senior-mod permanent ban guard", async () => {
  const mod1Uid = "mod_1";
  const seniorModUid = "senior_mod_1";
  const agentUid = "agent_1";
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "gobbahexa@gmail.com",
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const mod1Db = testEnv.authenticatedContext(mod1Uid).firestore();
  const seniorModDb = testEnv.authenticatedContext(seniorModUid).firestore();
  const agentDb = testEnv.authenticatedContext(agentUid).firestore();
  const ts = pastDate(5000);

  // Non-admin cannot self-assign staff role
  await assertFails(
    aliceDb.collection("staffRoles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      role: "ADMIN",
      active: true,
      assignedBy: ALICE_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Admin assigns MODERATOR, SENIOR_MODERATOR, SUPPORT_AGENT
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(mod1Uid).set({
      userId: mod1Uid,
      role: "MODERATOR",
      active: true,
      assignedBy: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(seniorModUid).set({
      userId: seniorModUid,
      role: "SENIOR_MODERATOR",
      active: true,
      assignedBy: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(agentUid).set({
      userId: agentUid,
      role: "SUPPORT_AGENT",
      active: true,
      assignedBy: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Support agent cannot enforce bans or write userEnforcements
  await assertFails(
    agentDb.collection("userEnforcements").doc(BOB_UID).set({
      userId: BOB_UID,
      accountStatus: "MESSAGING_RESTRICTED",
      featureExpiries: { dm: Date.now() + 3600000 },
      globalExpiryEpochMs: 0,
      reasonCode: "SPAM",
      reasonSummary: "Unauthorized attempt",
      warningCount: 0,
      activeRestrictionCount: 1,
      appealEligible: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Moderator CANNOT apply PERM_SUSPENDED (requires Senior Moderator or Admin)
  await assertFails(
    mod1Db.collection("userEnforcements").doc(BOB_UID).set({
      userId: BOB_UID,
      accountStatus: "PERM_SUSPENDED",
      featureExpiries: {},
      globalExpiryEpochMs: -1,
      reasonCode: "SCAM",
      reasonSummary: "Permanent ban attempt by junior mod",
      warningCount: 1,
      activeRestrictionCount: 1,
      appealEligible: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Moderator CAN apply temporary DM + story + call restrictions
  await assertSucceeds(
    mod1Db.collection("userEnforcements").doc(BOB_UID).set({
      userId: BOB_UID,
      accountStatus: "MESSAGING_RESTRICTED",
      featureExpiries: {
        dm: Date.now() + 3600000,
        stories: Date.now() + 3600000,
        calls: Date.now() + 3600000,
      },
      globalExpiryEpochMs: 0,
      reasonCode: "SPAM_BURST",
      reasonSummary: "Repeated spam links in DM.",
      warningCount: 1,
      activeRestrictionCount: 3,
      lastEnforcedBy: mod1Uid,
      lastEnforcedRole: "MODERATOR",
      lastEnforcementId: "rest_bob_1",
      appealEligible: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob can read his own enforcement status
  await assertSucceeds(bobDb.collection("userEnforcements").doc(BOB_UID).get());
  // Bob cannot lift or edit his own enforcement
  await assertFails(
    bobDb.collection("userEnforcements").doc(BOB_UID).update({
      accountStatus: "ACTIVE",
      featureExpiries: {},
      updatedAt: pastDate(100),
    })
  );

  // Alice creates a DM with Bob
  await assertSucceeds(
    aliceDb.collection("chats").doc("dm_v6_test").set({
      type: "DIRECT",
      title: "Alice & Bob",
      description: "",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      publicVisible: false,
      adminOnlyPost: false,
      e2eeEnabled: true,
      archivedBy: [],
      mutedBy: [],
      lastMessageText: "",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: ts,
      disappearingSeconds: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob IS BLOCKED from sending DM message, story, and starting call while restriction active
  await assertFails(
    bobDb.collection("chats").doc("dm_v6_test").collection("messages").doc("msg_blocked").set({
      chatId: "dm_v6_test",
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      publicChannel: false,
      type: "TEXT",
      text: "Blocked message",
      edited: false,
      deletedForEveryone: false,
      reactions: {},
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("stories").doc("story_blocked").set({
      authorId: BOB_UID,
      authorName: "Bob",
      type: "TEXT",
      text: "Blocked story",
      backgroundHex: "#112233",
      viewedBy: [],
      reactions: {},
      hiddenFromUids: [],
      discoverable: true,
      expiresAtEpochMs: Date.now() + 86400000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("calls").doc("call_blocked").set({
      callerId: BOB_UID,
      callerName: "Bob",
      calleeId: ALICE_UID,
      calleeName: "Alice",
      participantUids: [BOB_UID, ALICE_UID],
      callType: "VOICE",
      status: "RINGING",
      durationSeconds: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Server-clock auto-expiry check: when expiry timestamp is in the past, Bob can send DM again!
  await assertSucceeds(
    mod1Db.collection("userEnforcements").doc(BOB_UID).update({
      accountStatus: "ACTIVE",
      featureExpiries: { dm: Date.now() - 5000 },
      activeRestrictionCount: 0,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("dm_v6_test").collection("messages").doc("msg_allowed_after_expiry").set({
      chatId: "dm_v6_test",
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      publicChannel: false,
      type: "TEXT",
      text: "Restriction expired cleanly on server clock!",
      edited: false,
      deletedForEveryone: false,
      reactions: {},
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Senior Moderator CAN apply PERM_SUSPENDED
  await assertSucceeds(
    seniorModDb.collection("userEnforcements").doc(EVE_UID).set({
      userId: EVE_UID,
      accountStatus: "PERM_SUSPENDED",
      featureExpiries: {},
      globalExpiryEpochMs: -1,
      reasonCode: "PHISHING",
      reasonSummary: "Confirmed credential phishing campaign",
      warningCount: 3,
      activeRestrictionCount: 1,
      lastEnforcedBy: seniorModUid,
      lastEnforcedRole: "SENIOR_MODERATOR",
      lastEnforcementId: "rest_eve_perm",
      appealEligible: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("V6: Appeals distinct-reviewer rule, Support internalNotes isolation, Phone E.164 & WhatsApp zero-secrets guard", async () => {
  const mod1Uid = "mod_enforcer";
  const mod2Uid = "mod_reviewer";
  const agentUid = "support_agent_1";
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "gobbahexa@gmail.com",
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const mod1Db = testEnv.authenticatedContext(mod1Uid).firestore();
  const mod2Db = testEnv.authenticatedContext(mod2Uid).firestore();
  const agentDb = testEnv.authenticatedContext(agentUid).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    adminDb.collection("staffRoles").doc(mod1Uid).set({
      userId: mod1Uid, role: "MODERATOR", active: true, assignedBy: ADMIN_UID, createdAt: ts, updatedAt: ts,
    })
  );
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(mod2Uid).set({
      userId: mod2Uid, role: "MODERATOR", active: true, assignedBy: ADMIN_UID, createdAt: ts, updatedAt: ts,
    })
  );
  await assertSucceeds(
    adminDb.collection("staffRoles").doc(agentUid).set({
      userId: agentUid, role: "SUPPORT_AGENT", active: true, assignedBy: ADMIN_UID, createdAt: ts, updatedAt: ts,
    })
  );

  // Appeal submitted by Alice
  await assertSucceeds(
    aliceDb.collection("appeals").doc("app_1").set({
      userId: ALICE_UID,
      restrictionId: "rest_alice_1",
      originalEnforcerUid: mod1Uid,
      status: "SUBMITTED",
      reason: "My message was a legitimate project link, not spam.",
      attemptNumber: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Original enforcer mod1 CANNOT review their own enforcement appeal
  await assertFails(
    mod1Db.collection("appeals").doc("app_1").update({
      status: "REJECTED",
      reviewerUid: mod1Uid,
      reviewerDecisionNote: "Self-review blocked",
      updatedAt: pastDate(300),
    })
  );

  // Second independent moderator mod2 CAN approve the appeal
  await assertSucceeds(
    mod2Db.collection("appeals").doc("app_1").update({
      status: "APPROVED",
      reviewerUid: mod2Uid,
      reviewerDecisionNote: "Verified safe technical link.",
      updatedAt: pastDate(200),
    })
  );

  // Support ticket + two-way messages + strictly staff-only internalNotes
  await assertSucceeds(
    aliceDb.collection("supportTickets").doc("tkt_1").set({
      userId: ALICE_UID,
      userName: "Alice Vanguard",
      category: "PHONE_OTP",
      subject: "Sri Lanka +94 OTP delay",
      description: "Delayed SMS delivery on Dialog network.",
      status: "OPEN",
      priority: "NORMAL",
      whatsappOptIn: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bobDb.collection("supportTickets").doc("tkt_1").get());

  await assertSucceeds(
    aliceDb.collection("supportTickets").doc("tkt_1").collection("messages").doc("tmsg_1").set({
      ticketId: "tkt_1",
      senderId: ALICE_UID,
      senderName: "Alice Vanguard",
      senderRole: "USER",
      body: "Here is my carrier info.",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    agentDb.collection("supportTickets").doc("tkt_1").collection("messages").doc("tmsg_2").set({
      ticketId: "tkt_1",
      senderId: agentUid,
      senderName: "DL Official Support",
      senderRole: "SUPPORT_AGENT",
      body: "Thanks Alice, checking delivery health now.",
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Support agent creates internal note; Alice CANNOT read or write internalNotes
  await assertSucceeds(
    agentDb.collection("supportTickets").doc("tkt_1").collection("internalNotes").doc("inote_1").set({
      ticketId: "tkt_1",
      authorUid: agentUid,
      authorName: "Support Agent",
      note: "Check +94 carrier logs; do not expose carrier routing details.",
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
  await assertFails(aliceDb.collection("supportTickets").doc("tkt_1").collection("internalNotes").get());
  await assertFails(aliceDb.collection("supportTickets").doc("tkt_1").collection("internalNotes").doc("inote_1").get());

  // Phone Auth Profile E.164 validation
  await assertSucceeds(
    aliceDb.collection("phoneAuthProfiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      phoneE164: "+94771234567",
      countryCode: "LK",
      countryDialCode: "+94",
      otpProvider: "FIREBASE_SMS",
      verificationStatus: "VERIFIED",
      whatsappOptIn: true,
      verifiedAtEpochMs: Date.now() - 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("phoneAuthProfiles").doc(BOB_UID).set({
      userId: BOB_UID,
      phoneE164: "0771234567",
      countryCode: "LK",
      countryDialCode: "+94",
      otpProvider: "FIREBASE_SMS",
      verificationStatus: "VERIFIED",
      whatsappOptIn: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // WhatsApp zero-secrets guard (even admin cannot persist accessToken or appSecret in Firestore)
  await assertFails(
    adminDb.collection("whatsappConfigurations").doc("default").set({
      enabled: true,
      connectionStatus: "CONFIGURED",
      accessToken: "EAAG_SHOULD_FAIL",
      webhookConfigured: true,
      otpProviderEnabled: false,
      otpTemplateApproved: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    adminDb.collection("whatsappConfigurations").doc("default").set({
      enabled: true,
      connectionStatus: "CONFIGURED",
      displayPhoneNumber: "+94 11 234 5678",
      phoneNumberIdMasked: "***5678",
      wabaIdMasked: "***9910",
      webhookConfigured: true,
      otpProviderEnabled: false,
      otpTemplateApproved: false,
      lastErrorSummary: "",
      lastHealthCheckAtEpochMs: Date.now() - 500,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Immutable adminAuditLogs
  await assertSucceeds(
    agentDb.collection("adminAuditLogs").doc("audit_1").set({
      actorUid: agentUid,
      actorRole: "SUPPORT_AGENT",
      action: "SUPPORT_REPLY",
      targetType: "TICKET",
      targetId: "tkt_1",
      summary: "Replied to ticket tkt_1",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    adminDb.collection("adminAuditLogs").doc("audit_1").update({
      summary: "Tampered audit log",
    })
  );
  await assertFails(adminDb.collection("adminAuditLogs").doc("audit_1").delete());
});
