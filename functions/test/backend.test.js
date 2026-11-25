"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");

const {
  hasRequiredRole,
  canAssignRole,
  evaluateActiveEnforcement,
  buildEnforcementMutation,
  validateAppealReview,
  assessMessageSpamRisk,
} = require("../lib/enforcement");

const {
  verifyWebhookChallenge,
  verifyMetaSignature,
  parseWebhookEvents,
  resolveWhatsAppRuntimeConfig,
  dispatchWhatsAppCloudMessage,
} = require("../lib/whatsapp");

const {
  validateGroupOrContactId,
  normalizeTextLkPhone,
  resolveTextLkRuntimeConfig,
  buildTextLkRequestConfig,
  callTextLkApi,
} = require("../lib/textlk");

test("RBAC role hierarchy & delegation checks", () => {
  assert.equal(hasRequiredRole("SUPPORT_AGENT", "SUPPORT_AGENT"), true);
  assert.equal(hasRequiredRole("SUPPORT_AGENT", "MODERATOR"), false);
  assert.equal(hasRequiredRole("MODERATOR", "MODERATOR"), true);
  assert.equal(hasRequiredRole("SUPER_ADMIN", "ADMIN"), true);

  assert.equal(canAssignRole("ADMIN", "MODERATOR"), true);
  assert.equal(canAssignRole("ADMIN", "SUPER_ADMIN"), false);
  assert.equal(canAssignRole("SUPER_ADMIN", "ADMIN"), true);
});

test("Progressive warnings, permanent ban human-approval guard, and server-clock auto-expiry", () => {
  const now = 1_750_000_000_000;

  // Permanent ban blocked without explicit human approval & senior mod+
  assert.throws(() =>
    buildEnforcementMutation({
      existingEnforcement: null,
      actorUid: "mod_1",
      actorRole: "MODERATOR",
      targetUid: "user_1",
      actionType: "PERM_BAN",
      accountStatus: "PERM_SUSPENDED",
      reason: "Severe abuse",
      humanApprovedPermanentBan: true,
      nowMs: now,
    })
  );
  assert.throws(() =>
    buildEnforcementMutation({
      existingEnforcement: null,
      actorUid: "admin_1",
      actorRole: "ADMIN",
      targetUid: "user_1",
      actionType: "PERM_BAN",
      accountStatus: "PERM_SUSPENDED",
      reason: "Severe abuse",
      humanApprovedPermanentBan: false,
      nowMs: now,
    })
  );

  // Timed feature restriction expires automatically on trusted server clock
  const restricted = buildEnforcementMutation({
    existingEnforcement: null,
    actorUid: "mod_1",
    actorRole: "MODERATOR",
    targetUid: "user_1",
    actionType: "FEATURE_RESTRICTION",
    features: ["dm", "media"],
    durationCode: "1h",
    reason: "Spam links sent in DMs",
    nowMs: now,
  });
  assert.equal(restricted.accountStatus, "MESSAGING_RESTRICTED");
  assert.equal(restricted.featureExpiries.dm, now + 3600 * 1000);

  const midCheck = evaluateActiveEnforcement(restricted, now + 1800 * 1000);
  assert.equal(midCheck.dirty, false);
  assert.equal(Boolean(midCheck.activeFeatures.dm), true);

  const expiredCheck = evaluateActiveEnforcement(restricted, now + 3601 * 1000);
  assert.equal(expiredCheck.dirty, true);
  assert.equal(expiredCheck.accountStatus, "ACTIVE");
  assert.deepEqual(expiredCheck.activeFeatures, {});
  assert.deepEqual(expiredCheck.expiredFeatures, ["dm", "media"]);
});

test("Appeal review enforces distinct moderator from original enforcer", () => {
  const appeal = {
    id: "app_1",
    userId: "user_1",
    originalModeratorUid: "mod_a",
    status: "SUBMITTED",
  };
  assert.throws(() =>
    validateAppealReview({
      appealDoc: appeal,
      reviewerUid: "mod_a",
      reviewerRole: "MODERATOR",
      decision: "REJECTED",
      decisionNote: "Denied by self",
    })
  );

  const ok = validateAppealReview({
    appealDoc: appeal,
    reviewerUid: "mod_b",
    reviewerRole: "MODERATOR",
    decision: "APPROVED",
    decisionNote: "Context verified, lifting restriction.",
  });
  assert.equal(ok.status, "APPROVED");
});

test("Anti-spam detector flags rapid bursts & phishing without auto-banning", () => {
  const now = 1_750_000_000_000;
  const recent = Array.from({ length: 10 }, (_, idx) => now - idx * 500);
  const res = assessMessageSpamRisk({
    text: "Claim reward now at https://phish.example.com and https://bad.example.com",
    recentTimestamps: recent,
    accountAgeMs: 1000 * 60 * 60,
    nowMs: now,
  });
  assert.equal(res.rateLimited, true);
  assert.equal(res.escalateToHumanQueue, true);
  assert.ok(res.riskScore >= 70);
});

test("WhatsApp webhook challenge verification, HMAC-SHA256 validation, and event parsing", () => {
  const challengeOk = verifyWebhookChallenge(
    {
      "hub.mode": "subscribe",
      "hub.verify_token": "verify_secret_123",
      "hub.challenge": "998877",
    },
    "verify_secret_123"
  );
  assert.equal(challengeOk.ok, true);
  assert.equal(challengeOk.body, "998877");

  const challengeFail = verifyWebhookChallenge(
    {
      "hub.mode": "subscribe",
      "hub.verify_token": "wrong_token",
      "hub.challenge": "998877",
    },
    "verify_secret_123"
  );
  assert.equal(challengeFail.ok, false);
  assert.equal(challengeFail.status, 403);

  const rawBody = JSON.stringify({
    object: "whatsapp_business_account",
    entry: [
      {
        id: "waba_123",
        changes: [
          {
            value: {
              metadata: { phone_number_id: "pn_456" },
              statuses: [
                { id: "wamid.1", status: "delivered", timestamp: "1750000000", recipient_id: "94771234567" },
              ],
              messages: [
                { id: "wamid.2", from: "94771234567", timestamp: "1750000010", type: "text", text: { body: "Need help with OTP" } },
              ],
            },
          },
        ],
      },
    ],
  });
  const appSecret = "meta_app_secret_xyz";
  const sig = "sha256=" + crypto.createHmac("sha256", appSecret).update(rawBody).digest("hex");

  assert.equal(verifyMetaSignature(rawBody, sig, appSecret), true);
  assert.equal(verifyMetaSignature(rawBody, "sha256=deadbeef", appSecret), false);

  const parsed = parseWebhookEvents(JSON.parse(rawBody));
  assert.equal(parsed.length, 2);
  assert.equal(parsed[0].eventId, "status_wamid.1_DELIVERED_1750000000");
  assert.equal(parsed[1].kind, "INBOUND");
  assert.equal(parsed[1].textBody, "Need help with OTP");
});

test("WhatsApp Cloud API dispatcher enforces opt-out, 24h customer window, and OTP template capability", async () => {
  const runtime = resolveWhatsAppRuntimeConfig(
    { enabled: true, otpTemplateEnabled: false },
    {
      WHATSAPP_ACCESS_TOKEN: "EAAG_TEST_ONLY",
      WHATSAPP_PHONE_NUMBER_ID: "123456789",
      WHATSAPP_BUSINESS_ACCOUNT_ID: "987654321",
    }
  );
  assert.equal(runtime.enabled, true);
  assert.equal(runtime.otpEligible, false);

  // Refuses WhatsApp OTP when authentication template is not configured
  await assert.rejects(() =>
    dispatchWhatsAppCloudMessage({
      runtimeConfig: runtime,
      toPhoneE164: "+94771234567",
      mode: "AUTH_OTP",
      otpCode: "654321",
    })
  );

  // Refuses non-OTP outbound message when user opted out
  await assert.rejects(() =>
    dispatchWhatsAppCloudMessage({
      runtimeConfig: runtime,
      toPhoneE164: "+94771234567",
      userProfile: { whatsappOptOut: true },
      mode: "TEMPLATE",
      templateName: "dl_chat_support_update",
    })
  );

  // Refuses free-form session text outside 24h customer-service window
  const now = 1_750_000_000_000;
  await assert.rejects(() =>
    dispatchWhatsAppCloudMessage({
      runtimeConfig: runtime,
      toPhoneE164: "+94771234567",
      userProfile: { lastWhatsAppInboundAtMs: now - 25 * 3600 * 1000 },
      mode: "SESSION_TEXT",
      textBody: "Hello",
      nowMs: now,
    })
  );

  // Dispatches approved template via fetch when valid
  let capturedUrl = "";
  const mockFetch = async (url) => {
    capturedUrl = url;
    return {
      ok: true,
      status: 200,
      json: async () => ({ messages: [{ id: "wamid.out.1" }] }),
    };
  };
  const sent = await dispatchWhatsAppCloudMessage({
    runtimeConfig: runtime,
    toPhoneE164: "+94771234567",
    mode: "TEMPLATE",
    templateName: "dl_chat_support_update",
    bodyParameters: ["TKT-101", "Agent assigned"],
    fetchImpl: mockFetch,
  });
  assert.equal(sent.waMessageId, "wamid.out.1");
  assert.ok(capturedUrl.includes("/v21.0/123456789/messages"));
});

test("Text.lk phone normalization, UID validation, Contacts CRUD & SMS dispatch builders", async () => {
  assert.equal(normalizeTextLkPhone("077 123 4567"), "94771234567");
  assert.equal(normalizeTextLkPhone("+94710000000"), "94710000000");
  assert.equal(normalizeTextLkPhone("0094710000000"), "94710000000");
  assert.throws(() => normalizeTextLkPhone("123"));
  assert.throws(() => validateGroupOrContactId("../etc/passwd"));

  const runtimeConfig = resolveTextLkRuntimeConfig({
    TEXT_LK_API_TOKEN: "test_token_placeholder",
    TEXT_LK_SENDER_ID: "DeathLegion",
    TEXT_LK_DEFAULT_GROUP_ID: "grp_901",
  });
  assert.equal(runtimeConfig.configured, true);

  const createReq = buildTextLkRequestConfig({
    action: "CREATE_CONTACT",
    groupId: "grp_901",
    phone: "0710000000",
    firstName: "Kasun",
    lastName: "Silva",
    runtimeConfig,
  });
  assert.equal(createReq.method, "POST");
  assert.equal(createReq.url, "https://app.text.lk/api/v3/contacts/grp_901/store");
  assert.equal(createReq.headers.Authorization, "Bearer test_token_placeholder");
  assert.deepEqual(JSON.parse(createReq.body), {
    PHONE: "94710000000",
    FIRST_NAME: "Kasun",
    LAST_NAME: "Silva",
  });

  const viewReq = buildTextLkRequestConfig({
    action: "VIEW_CONTACT",
    groupId: "grp_901",
    contactUid: "cnt_77",
    runtimeConfig,
  });
  assert.equal(viewReq.method, "POST");
  assert.equal(viewReq.url, "https://app.text.lk/api/v3/contacts/grp_901/search/cnt_77");

  const updateReq = buildTextLkRequestConfig({
    action: "UPDATE_CONTACT",
    groupId: "grp_901",
    contactUid: "cnt_77",
    phone: "94771234567",
    firstName: "Kasun",
    lastName: "Perera",
    runtimeConfig,
  });
  assert.equal(updateReq.method, "PATCH");
  assert.equal(updateReq.url, "https://app.text.lk/api/v3/contacts/grp_901/update/cnt_77");

  const deleteReq = buildTextLkRequestConfig({
    action: "DELETE_CONTACT",
    groupId: "grp_901",
    contactUid: "cnt_77",
    runtimeConfig,
  });
  assert.equal(deleteReq.method, "DELETE");
  assert.equal(deleteReq.url, "https://app.text.lk/api/v3/contacts/grp_901/delete/cnt_77");

  const listReq = buildTextLkRequestConfig({
    action: "LIST_CONTACTS",
    groupId: "grp_901",
    page: 2,
    runtimeConfig,
  });
  assert.equal(listReq.method, "POST");
  assert.equal(listReq.url, "https://app.text.lk/api/v3/contacts/grp_901/all?page=2");

  let capturedSmsUrl = "";
  const smsRes = await callTextLkApi(
    {
      action: "SEND_SMS",
      recipient: "+94771234567",
      message: "DL Chat alert test",
      useHttpEndpoint: false,
      runtimeConfig,
    },
    async (url) => {
      capturedSmsUrl = url;
      return {
        ok: true,
        status: 200,
        json: async () => ({ status: "success", data: { uid: "sms_101" } }),
      };
    }
  );
  assert.equal(capturedSmsUrl, "https://app.text.lk/api/v3/sms/send");
  assert.equal(smsRes.data.uid, "sms_101");
});

