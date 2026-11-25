"use strict";

const V3_BASE_URL = "https://app.text.lk/api/v3";
const HTTP_BASE_URL = "https://app.text.lk/api/http";
const UID_REGEX = /^[A-Za-z0-9_-]{3,64}$/;

function validateGroupOrContactId(id, label = "ID") {
  const clean = String(id || "").trim();
  if (!UID_REGEX.test(clean)) {
    throw new Error(`Invalid Text.lk ${label}. Use 3-64 letters, numbers, '-' or '_'.`);
  }
  return clean;
}

function normalizeTextLkPhone(raw, defaultDial = "94") {
  const trimmed = String(raw || "").trim();
  if (!trimmed) throw new Error("Phone number is required.");
  const digits = trimmed.replace(/\D/g, "");
  let normalized = digits;
  if (trimmed.startsWith("+")) {
    normalized = digits;
  } else if (trimmed.startsWith("00")) {
    normalized = digits.slice(2);
  } else if (trimmed.startsWith("0") && digits.length >= 9 && digits.length <= 11) {
    normalized = `${String(defaultDial).replace(/\D/g, "")}${digits.slice(1)}`;
  } else if (digits.length === 9) {
    normalized = `${String(defaultDial).replace(/\D/g, "")}${digits}`;
  }
  if (!/^\d{9,15}$/.test(normalized)) {
    throw new Error("Invalid international phone number format for Text.lk.");
  }
  return normalized;
}

function resolveTextLkRuntimeConfig(env = process.env) {
  const apiToken = String(env.TEXT_LK_API_TOKEN || "").trim();
  const senderId = String(env.TEXT_LK_SENDER_ID || "DLChat").trim().slice(0, 11) || "DLChat";
  const defaultGroupId = String(env.TEXT_LK_DEFAULT_GROUP_ID || "").trim();
  return {
    configured: Boolean(apiToken),
    apiToken,
    senderId,
    defaultGroupId,
    v3BaseUrl: V3_BASE_URL,
    httpBaseUrl: HTTP_BASE_URL,
  };
}

function buildTextLkRequestConfig({
  action,
  groupId,
  contactUid,
  page = 1,
  phone,
  firstName = "",
  lastName = "",
  extraFields = {},
  recipient,
  senderId,
  message,
  useHttpEndpoint = false,
  runtimeConfig,
}) {
  if (!runtimeConfig || !runtimeConfig.configured) {
    throw new Error("TEXT_LK_API_TOKEN is not configured in Cloud Functions secrets.");
  }

  const headers = {
    Authorization: `Bearer ${runtimeConfig.apiToken}`,
    "Content-Type": "application/json",
    Accept: "application/json",
  };

  if (action === "LIST_CONTACTS") {
    const gid = validateGroupOrContactId(groupId, "group_id");
    const safePage = Math.max(1, Number(page) || 1);
    return {
      method: "POST",
      url: `${runtimeConfig.v3BaseUrl}/contacts/${gid}/all?page=${safePage}`,
      headers,
      body: JSON.stringify({}),
    };
  }

  if (action === "VIEW_CONTACT") {
    const gid = validateGroupOrContactId(groupId, "group_id");
    const uid = validateGroupOrContactId(contactUid, "uid");
    return {
      method: "POST",
      url: `${runtimeConfig.v3BaseUrl}/contacts/${gid}/search/${uid}`,
      headers,
      body: JSON.stringify({}),
    };
  }

  if (action === "CREATE_CONTACT" || action === "UPDATE_CONTACT") {
    const gid = validateGroupOrContactId(groupId, "group_id");
    const normalizedPhone = normalizeTextLkPhone(phone);
    const payload = { PHONE: normalizedPhone };
    if (String(firstName).trim()) payload.FIRST_NAME = String(firstName).trim();
    if (String(lastName).trim()) payload.LAST_NAME = String(lastName).trim();
    if (extraFields && typeof extraFields === "object") {
      for (const [k, v] of Object.entries(extraFields)) {
        const cleanKey = String(k || "").trim().toUpperCase();
        if (cleanKey && !["PHONE", "FIRST_NAME", "LAST_NAME"].includes(cleanKey) && String(v || "").trim()) {
          payload[cleanKey] = String(v).trim();
        }
      }
    }
    if (action === "UPDATE_CONTACT") {
      const uid = validateGroupOrContactId(contactUid, "uid");
      return {
        method: "PATCH",
        url: `${runtimeConfig.v3BaseUrl}/contacts/${gid}/update/${uid}`,
        headers,
        body: JSON.stringify(payload),
      };
    }
    return {
      method: "POST",
      url: `${runtimeConfig.v3BaseUrl}/contacts/${gid}/store`,
      headers,
      body: JSON.stringify(payload),
    };
  }

  if (action === "DELETE_CONTACT") {
    const gid = validateGroupOrContactId(groupId, "group_id");
    const uid = validateGroupOrContactId(contactUid, "uid");
    return {
      method: "DELETE",
      url: `${runtimeConfig.v3BaseUrl}/contacts/${gid}/delete/${uid}`,
      headers,
    };
  }

  if (action === "SEND_SMS") {
    const normalizedRecipient = normalizeTextLkPhone(recipient);
    const cleanMessage = String(message || "").trim();
    if (!cleanMessage) throw new Error("SMS message cannot be empty.");
    const cleanSender = String(senderId || runtimeConfig.senderId || "DLChat").trim().slice(0, 11);
    const base = useHttpEndpoint ? runtimeConfig.httpBaseUrl : runtimeConfig.v3BaseUrl;
    return {
      method: "POST",
      url: `${base}/sms/send`,
      headers,
      body: JSON.stringify({
        recipient: normalizedRecipient,
        sender_id: cleanSender,
        type: "plain",
        message: cleanMessage.slice(0, 1600),
      }),
    };
  }

  throw new Error(`Unsupported Text.lk action: ${action}`);
}

async function callTextLkApi(params, fetchImpl = globalThis.fetch) {
  const reqConfig = buildTextLkRequestConfig(params);
  const response = await fetchImpl(reqConfig.url, {
    method: reqConfig.method,
    headers: reqConfig.headers,
    body: reqConfig.body,
  });
  const json = await response.json().catch(() => ({}));
  if (!response.ok || String(json.status || "").toLowerCase() === "error") {
    throw new Error(json.message || json.error || `Text.lk returned HTTP ${response.status}`);
  }
  return json;
}

module.exports = {
  V3_BASE_URL,
  HTTP_BASE_URL,
  validateGroupOrContactId,
  normalizeTextLkPhone,
  resolveTextLkRuntimeConfig,
  buildTextLkRequestConfig,
  callTextLkApi,
};
