"use strict";

const crypto = require("node:crypto");

const DEFAULT_GRAPH_VERSION = "v21.0";
const SERVICE_WINDOW_MS = 24 * 3600 * 1000;

function timingSafeStringEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const bufA = Buffer.from(a, "utf8");
  const bufB = Buffer.from(b, "utf8");
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

/**
 * Meta Cloud API Webhook verification handshake (GET).
 */
function verifyWebhookChallenge(query, expectedVerifyToken) {
  const mode = query["hub.mode"];
  const token = query["hub.verify_token"];
  const challenge = query["hub.challenge"];

  if (!expectedVerifyToken) {
    return { ok: false, status: 503, body: "Webhook verify token is not configured." };
  }
  if (mode === "subscribe" && timingSafeStringEqual(String(token || ""), expectedVerifyToken)) {
    return { ok: true, status: 200, body: String(challenge || "") };
  }
  return { ok: false, status: 403, body: "Webhook verification failed." };
}

/**
 * Verifies Meta X-Hub-Signature-256 HMAC-SHA256 header against raw request body bytes.
 */
function verifyMetaSignature(rawBody, signatureHeader, appSecret) {
  if (!appSecret || !signatureHeader || typeof signatureHeader !== "string") {
    return false;
  }
  if (!signatureHeader.startsWith("sha256=")) {
    return false;
  }
  const bodyBuf = Buffer.isBuffer(rawBody) ? rawBody : Buffer.from(String(rawBody || ""), "utf8");
  const expected =
    "sha256=" + crypto.createHmac("sha256", appSecret).update(bodyBuf).digest("hex");
  return timingSafeStringEqual(signatureHeader.trim(), expected);
}

/**
 * Parses Meta WhatsApp Business webhook entries into normalized idempotent events.
 */
function parseWebhookEvents(payload) {
  const events = [];
  if (!payload || payload.object !== "whatsapp_business_account" || !Array.isArray(payload.entry)) {
    return events;
  }

  for (const entry of payload.entry) {
    const wabaId = String(entry.id || "");
    const changes = Array.isArray(entry.changes) ? entry.changes : [];
    for (const change of changes) {
      const value = change?.value || {};
      const phoneNumberId = String(value?.metadata?.phone_number_id || "");

      const statuses = Array.isArray(value.statuses) ? value.statuses : [];
      for (const st of statuses) {
        const messageId = String(st.id || "");
        const status = String(st.status || "").toUpperCase();
        const tsSec = Number(st.timestamp || 0);
        const dedupId = `status_${messageId}_${status}_${tsSec}`;
        events.push({
          eventId: dedupId,
          kind: "STATUS",
          wabaId,
          phoneNumberId,
          waMessageId: messageId,
          recipientPhone: String(st.recipient_id || ""),
          status,
          errorCode: st.errors?.[0]?.code ? String(st.errors[0].code) : null,
          errorMessage: st.errors?.[0]?.title ? String(st.errors[0].title) : null,
          timestampMs: tsSec > 0 ? tsSec * 1000 : Date.now(),
        });
      }

      const messages = Array.isArray(value.messages) ? value.messages : [];
      for (const msg of messages) {
        const messageId = String(msg.id || "");
        const tsSec = Number(msg.timestamp || 0);
        const dedupId = `inbound_${messageId}`;
        events.push({
          eventId: dedupId,
          kind: "INBOUND",
          wabaId,
          phoneNumberId,
          waMessageId: messageId,
          fromPhone: String(msg.from || ""),
          messageType: String(msg.type || "text").toUpperCase(),
          textBody: String(msg.text?.body || msg.button?.text || ""),
          timestampMs: tsSec > 0 ? tsSec * 1000 : Date.now(),
        });
      }
    }
  }
  return events;
}

function resolveWhatsAppRuntimeConfig(configDoc = {}, env = process.env) {
  const accessToken = env.WHATSAPP_ACCESS_TOKEN || "";
  const appSecret = env.WHATSAPP_APP_SECRET || "";
  const verifyToken = env.WHATSAPP_WEBHOOK_VERIFY_TOKEN || "";
  const phoneNumberId = env.WHATSAPP_PHONE_NUMBER_ID || configDoc.phoneNumberId || "";
  const wabaId = env.WHATSAPP_BUSINESS_ACCOUNT_ID || configDoc.wabaId || "";
  const graphApiVersion = env.WHATSAPP_GRAPH_API_VERSION || configDoc.graphApiVersion || DEFAULT_GRAPH_VERSION;
  const otpTemplateName = env.WHATSAPP_OTP_TEMPLATE_NAME || configDoc.otpTemplateName || "";
  const otpTemplateLang = env.WHATSAPP_OTP_TEMPLATE_LANG || configDoc.otpTemplateLang || "en_US";

  const hasCloudApiCreds = Boolean(accessToken && phoneNumberId && wabaId);
  const enabled = Boolean(configDoc.enabled && hasCloudApiCreds);
  const otpEligible = Boolean(
    enabled && configDoc.otpTemplateEnabled && otpTemplateName
  );

  return {
    enabled,
    hasCloudApiCreds,
    otpEligible,
    hasWebhookCreds: Boolean(appSecret && verifyToken),
    graphApiVersion,
    phoneNumberId,
    wabaId,
    displayPhoneNumber: env.WHATSAPP_DISPLAY_NUMBER || configDoc.displayPhoneNumber || "",
    otpTemplateName,
    otpTemplateLang,
    accessToken,
    appSecret,
    verifyToken,
  };
}

/**
 * Dispatches an official Meta Graph API WhatsApp Cloud message.
 * Never simulates delivery: throws explicit error when credentials, consent, or session window are missing.
 */
async function dispatchWhatsAppCloudMessage({
  runtimeConfig,
  toPhoneE164,
  userProfile = {},
  mode = "TEMPLATE", // TEMPLATE | SESSION_TEXT | AUTH_OTP
  templateName = "",
  languageCode = "en_US",
  bodyParameters = [],
  textBody = "",
  otpCode = "",
  nowMs = Date.now(),
  fetchImpl = globalThis.fetch,
}) {
  if (!runtimeConfig.hasCloudApiCreds) {
    throw new Error(
      "WhatsApp Business Cloud API credentials are not configured on the backend."
    );
  }
  if (!runtimeConfig.enabled) {
    throw new Error("WhatsApp Business integration is disabled in configuration.");
  }
  if (!toPhoneE164 || !/^\+[1-9]\d{6,14}$/.test(toPhoneE164)) {
    throw new Error("Valid E.164 recipient phone number is required.");
  }
  if (userProfile.whatsappOptOut === true && mode !== "AUTH_OTP") {
    throw new Error("User has opted out of WhatsApp Business notifications.");
  }
  if (mode === "AUTH_OTP" && !runtimeConfig.otpEligible) {
    throw new Error(
      "Official WhatsApp authentication template is not configured or approved; use SMS OTP."
    );
  }
  if (mode === "SESSION_TEXT") {
    const lastInboundMs = Number(userProfile.lastWhatsAppInboundAtMs || 0);
    if (!lastInboundMs || nowMs - lastInboundMs > SERVICE_WINDOW_MS) {
      throw new Error(
        "Outside the 24-hour WhatsApp customer-service window. Send an approved template message instead."
      );
    }
  }

  const endpoint = `https://graph.facebook.com/${runtimeConfig.graphApiVersion}/${runtimeConfig.phoneNumberId}/messages`;
  const normalizedPhone = toPhoneE164.replace(/^\+/, "");

  let payload;
  if (mode === "SESSION_TEXT") {
    payload = {
      messaging_product: "whatsapp",
      recipient_type: "individual",
      to: normalizedPhone,
      type: "text",
      text: { preview_url: false, body: String(textBody).slice(0, 4096) },
    };
  } else if (mode === "AUTH_OTP") {
    payload = {
      messaging_product: "whatsapp",
      recipient_type: "individual",
      to: normalizedPhone,
      type: "template",
      template: {
        name: templateName || runtimeConfig.otpTemplateName,
        language: { code: languageCode || runtimeConfig.otpTemplateLang },
        components: [
          {
            type: "body",
            parameters: [{ type: "text", text: String(otpCode) }],
          },
          {
            type: "button",
            sub_type: "url",
            index: "0",
            parameters: [{ type: "text", text: String(otpCode) }],
          },
        ],
      },
    };
  } else {
    if (!templateName) {
      throw new Error("Approved template name is required for outbound template messaging.");
    }
    payload = {
      messaging_product: "whatsapp",
      recipient_type: "individual",
      to: normalizedPhone,
      type: "template",
      template: {
        name: templateName,
        language: { code: languageCode || "en_US" },
        components:
          bodyParameters.length > 0
            ? [
                {
                  type: "body",
                  parameters: bodyParameters.map((v) => ({ type: "text", text: String(v) })),
                },
              ]
            : [],
      },
    };
  }

  const response = await fetchImpl(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${runtimeConfig.accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
  const json = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errMsg =
      json?.error?.message || `Meta Graph API returned HTTP ${response.status}`;
    const err = new Error(errMsg);
    err.metaErrorCode = json?.error?.code;
    throw err;
  }

  return {
    waMessageId: json?.messages?.[0]?.id || "",
    status: "SENT",
    recipientPhone: toPhoneE164,
    mode,
    templateName: payload.template?.name || null,
  };
}

module.exports = {
  DEFAULT_GRAPH_VERSION,
  SERVICE_WINDOW_MS,
  timingSafeStringEqual,
  verifyWebhookChallenge,
  verifyMetaSignature,
  parseWebhookEvents,
  resolveWhatsAppRuntimeConfig,
  dispatchWhatsAppCloudMessage,
};
