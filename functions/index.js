"use strict";

const admin = require("firebase-admin");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { onRequest } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");

const {
  hasRequiredRole,
  canAssignRole,
  evaluateActiveEnforcement,
  buildEnforcementMutation,
  validateAppealReview,
  assessMessageSpamRisk,
} = require("./lib/enforcement");
const {
  verifyWebhookChallenge,
  verifyMetaSignature,
  parseWebhookEvents,
  resolveWhatsAppRuntimeConfig,
  dispatchWhatsAppCloudMessage,
} = require("./lib/whatsapp");
const {
  resolveTextLkRuntimeConfig,
  callTextLkApi,
} = require("./lib/textlk");

if (!admin.apps.length) {
  admin.initializeApp();
}

const DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID ||
  "ai-studio-android-dlchat-bddcbd1b-8921-4576-a2b4-2b965a255784";

function getDb() {
  return getFirestore(DATABASE_ID);
}

async function authenticateBearer(req) {
  const authHeader = req.headers.authorization || "";
  if (!authHeader.startsWith("Bearer ")) {
    throw new Error("Missing Bearer token.");
  }
  const idToken = authHeader.slice("Bearer ".length).trim();
  const decoded = await admin.auth().verifyIdToken(idToken);
  const db = getDb();

  let role = decoded.dlRole || "USER";
  if (decoded.email === "gobbahexa@gmail.com" && decoded.email_verified) {
    role = "SUPER_ADMIN";
  } else {
    const roleSnap = await db.collection("staffRoles").doc(decoded.uid).get();
    if (roleSnap.exists && roleSnap.data().active === true) {
      role = roleSnap.data().role || role;
    } else {
      const adminSnap = await db.collection("admins").doc(decoded.uid).get();
      if (adminSnap.exists) role = "ADMIN";
    }
  }
  return { uid: decoded.uid, email: decoded.email || "", role };
}

async function writeAuditLog(db, { actorUid, actorRole, action, targetId, details }) {
  await db.collection("adminAuditLogs").add({
    actorUid,
    actorRole,
    action,
    targetId: String(targetId || ""),
    details: String(details || "").slice(0, 1000),
    createdAt: FieldValue.serverTimestamp(),
  });
}

/**
 * Official Meta WhatsApp Cloud API Webhook (GET verification + POST HMAC-SHA256 verified events).
 */
exports.whatsappWebhook = onRequest(
  { cors: false, maxInstances: 10 },
  async (req, res) => {
    const db = getDb();
    const configSnap = await db.collection("whatsappConfigurations").doc("primary").get();
    const runtime = resolveWhatsAppRuntimeConfig(configSnap.exists ? configSnap.data() : {}, process.env);

    if (req.method === "GET") {
      const check = verifyWebhookChallenge(req.query || {}, runtime.verifyToken);
      if (check.ok) {
        await db.collection("whatsappConfigurations").doc("primary").set(
          {
            webhookConfigured: true,
            webhookLastVerifiedAtEpochMs: Date.now(),
            updatedAt: FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
      }
      res.status(check.status).send(check.body);
      return;
    }

    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    const signature = req.headers["x-hub-signature-256"];
    if (!verifyMetaSignature(req.rawBody || JSON.stringify(req.body || {}), signature, runtime.appSecret)) {
      res.status(401).json({ error: "Invalid webhook signature" });
      return;
    }

    const events = parseWebhookEvents(req.body);
    let processed = 0;

    for (const ev of events) {
      const evRef = db.collection("whatsappWebhookEvents").doc(ev.eventId);
      const already = await evRef.get();
      if (already.exists) continue; // Replay/idempotency protection

      await evRef.set({
        ...ev,
        processedAt: FieldValue.serverTimestamp(),
      });

      if (ev.kind === "STATUS" && ev.waMessageId) {
        await db.collection("whatsappMessageEvents").doc(ev.waMessageId).set(
          {
            waMessageId: ev.waMessageId,
            status: ev.status,
            errorCode: ev.errorCode,
            errorMessage: ev.errorMessage,
            updatedAt: FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
      } else if (ev.kind === "INBOUND" && ev.fromPhone) {
        const phoneE164 = ev.fromPhone.startsWith("+") ? ev.fromPhone : `+${ev.fromPhone}`;
        const profileQ = await db
          .collection("phoneAuthProfiles")
          .where("phoneE164", "==", phoneE164)
          .limit(1)
          .get();
        const matchedUid = profileQ.empty ? "" : profileQ.docs[0].id;
        if (matchedUid) {
          await db.collection("phoneAuthProfiles").doc(matchedUid).set(
            {
              lastWhatsAppInboundAtMs: ev.timestampMs,
              updatedAt: FieldValue.serverTimestamp(),
            },
            { merge: true }
          );
        }
        if (ev.textBody) {
          const ticketRef = db.collection("supportTickets").doc();
          await ticketRef.set({
            userId: matchedUid || `wa_${ev.fromPhone}`,
            subject: `WhatsApp Support (${phoneE164})`,
            category: "ACCOUNT",
            priority: "NORMAL",
            status: "OPEN",
            channel: "WHATSAPP",
            contactPhoneE164: phoneE164,
            lastMessagePreview: ev.textBody.slice(0, 200),
            createdAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(),
          });
          await ticketRef.collection("messages").add({
            ticketId: ticketRef.id,
            senderId: matchedUid || `wa_${ev.fromPhone}`,
            senderName: phoneE164,
            senderRole: "USER",
            text: ev.textBody.slice(0, 2000),
            channel: "WHATSAPP",
            createdAt: FieldValue.serverTimestamp(),
          });
        }
      }
      processed += 1;
    }

    res.status(200).json({ ok: true, processed });
  }
);

/**
 * Trusted HTTPS API for DL Chat Enforcement, Appeals, RBAC Roles, Anti-Spam, and Official WhatsApp Messaging.
 */
exports.dlChatBackend = onRequest(
  { cors: true, maxInstances: 20 },
  async (req, res) => {
    const db = getDb();
    try {
      const actor = await authenticateBearer(req);
      const path = (req.path || "/").replace(/\/+$/, "") || "/";
      const body = req.body || {};

      if (req.method === "POST" && path === "/enforcement/apply") {
        const targetRef = db.collection("userEnforcements").doc(body.targetUid);
        const targetSnap = await targetRef.get();
        const mutation = buildEnforcementMutation({
          existingEnforcement: targetSnap.exists ? targetSnap.data() : null,
          actorUid: actor.uid,
          actorRole: actor.role,
          targetUid: body.targetUid,
          actionType: body.actionType,
          accountStatus: body.accountStatus,
          features: body.features || [],
          durationCode: body.durationCode || "24h",
          customDurationMs: body.customDurationMs || 0,
          reason: body.reason,
          humanApprovedPermanentBan: Boolean(body.humanApprovedPermanentBan),
        });

        await targetRef.set(
          {
            ...mutation,
            updatedAt: FieldValue.serverTimestamp(),
          },
          { merge: false }
        );

        await db.collection("users").doc(body.targetUid).set(
          {
            suspended: ["TEMP_SUSPENDED", "PERM_SUSPENDED", "DELETION_PENDING"].includes(
              mutation.accountStatus
            ),
            updatedAt: FieldValue.serverTimestamp(),
          },
          { merge: true }
        );

        await writeAuditLog(db, {
          actorUid: actor.uid,
          actorRole: actor.role,
          action: `ENFORCEMENT_${body.actionType}`,
          targetId: body.targetUid,
          details: `${mutation.accountStatus} (${body.durationCode || "custom"}): ${body.reason}`,
        });

        res.status(200).json({ ok: true, enforcement: mutation });
        return;
      }

      if (req.method === "POST" && path === "/appeals/review") {
        const appealRef = db.collection("appeals").doc(body.appealId);
        const appealSnap = await appealRef.get();
        const appealData = appealSnap.exists ? { id: appealSnap.id, ...appealSnap.data() } : null;
        const review = validateAppealReview({
          appealDoc: appealData,
          reviewerUid: actor.uid,
          reviewerRole: actor.role,
          decision: body.decision,
          decisionNote: body.decisionNote,
        });

        await appealRef.update({
          status: review.status,
          reviewerUid: review.reviewerUid,
          decisionNote: review.decisionNote,
          updatedAt: FieldValue.serverTimestamp(),
        });

        if (review.status === "APPROVED" && appealData?.userId) {
          await db.collection("userEnforcements").doc(appealData.userId).set(
            {
              userId: appealData.userId,
              accountStatus: "ACTIVE",
              featureExpiries: {},
              globalExpiresAtEpochMs: 0,
              appealEligible: false,
              lastReason: `Appeal approved: ${review.decisionNote}`,
              lastModeratorUid: actor.uid,
              updatedAt: FieldValue.serverTimestamp(),
              updatedAtEpochMs: Date.now(),
            },
            { merge: true }
          );
          await db.collection("users").doc(appealData.userId).set(
            { suspended: false, updatedAt: FieldValue.serverTimestamp() },
            { merge: true }
          );
        }

        await writeAuditLog(db, {
          actorUid: actor.uid,
          actorRole: actor.role,
          action: `APPEAL_${review.status}`,
          targetId: body.appealId,
          details: review.decisionNote,
        });

        res.status(200).json({ ok: true, review });
        return;
      }

      if (req.method === "POST" && path === "/roles/assign") {
        if (!canAssignRole(actor.role, body.role)) {
          res.status(403).json({ error: "Insufficient role authority to assign target role." });
          return;
        }
        await db.collection("staffRoles").doc(body.targetUid).set({
          userId: body.targetUid,
          role: body.role,
          active: body.active !== false,
          assignedByUid: actor.uid,
          updatedAt: FieldValue.serverTimestamp(),
          createdAt: FieldValue.serverTimestamp(),
        });
        await admin.auth().setCustomUserClaims(body.targetUid, { dlRole: body.role });
        await writeAuditLog(db, {
          actorUid: actor.uid,
          actorRole: actor.role,
          action: "ROLE_ASSIGN",
          targetId: body.targetUid,
          details: `Assigned ${body.role}`,
        });
        res.status(200).json({ ok: true });
        return;
      }

      if (req.method === "GET" && path === "/whatsapp/health") {
        if (!hasRequiredRole(actor.role, "ADMIN")) {
          res.status(403).json({ error: "Admin role required" });
          return;
        }
        const cfgSnap = await db.collection("whatsappConfigurations").doc("primary").get();
        const runtime = resolveWhatsAppRuntimeConfig(cfgSnap.exists ? cfgSnap.data() : {}, process.env);
        res.status(200).json({
          enabled: runtime.enabled,
          hasCloudApiCreds: runtime.hasCloudApiCreds,
          otpEligible: runtime.otpEligible,
          hasWebhookCreds: runtime.hasWebhookCreds,
          graphApiVersion: runtime.graphApiVersion,
          displayPhoneNumber: runtime.displayPhoneNumber,
          wabaId: runtime.wabaId,
          phoneNumberId: runtime.phoneNumberId,
        });
        return;
      }

      if (req.method === "POST" && path === "/whatsapp/send-support") {
        if (!hasRequiredRole(actor.role, "SUPPORT_AGENT")) {
          res.status(403).json({ error: "Support staff role required" });
          return;
        }
        const cfgSnap = await db.collection("whatsappConfigurations").doc("primary").get();
        const runtime = resolveWhatsAppRuntimeConfig(cfgSnap.exists ? cfgSnap.data() : {}, process.env);
        const targetProfileSnap = body.targetUid
          ? await db.collection("phoneAuthProfiles").doc(body.targetUid).get()
          : null;
        const targetProfile = targetProfileSnap?.exists ? targetProfileSnap.data() : {};
        const phone = body.toPhoneE164 || targetProfile.phoneE164;

        const result = await dispatchWhatsAppCloudMessage({
          runtimeConfig: runtime,
          toPhoneE164: phone,
          userProfile: targetProfile,
          mode: body.mode || "TEMPLATE",
          templateName: body.templateName || process.env.WHATSAPP_SUPPORT_TEMPLATE_NAME || "dl_chat_support_update",
          languageCode: body.languageCode || "en_US",
          bodyParameters: body.bodyParameters || [],
          textBody: body.textBody || "",
        });

        await db.collection("whatsappMessageEvents").doc(result.waMessageId || db.collection("_").doc().id).set({
          waMessageId: result.waMessageId,
          targetUid: body.targetUid || "",
          recipientPhone: phone,
          mode: result.mode,
          templateName: result.templateName,
          status: result.status,
          actorUid: actor.uid,
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });

        res.status(200).json({ ok: true, result });
        return;
      }

      if (req.method === "POST" && path === "/moderation/spam-check") {
        const risk = assessMessageSpamRisk({
          text: body.text || "",
          recentTimestamps: body.recentTimestamps || [],
          accountAgeMs: Number(body.accountAgeMs || 86400000),
        });
        if (risk.escalateToHumanQueue) {
          await db.collection("moderationCases").add({
            targetUid: actor.uid,
            sourceType: "AUTO_ANTISPAM",
            status: "OPEN",
            severityScore: risk.riskScore,
            triageReasons: risk.reasons,
            createdAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(),
          });
        }
        res.status(200).json({ ok: true, risk });
        return;
      }

      if (req.method === "GET" && path === "/textlk/health") {
        if (!hasRequiredRole(actor.role, "SUPPORT_AGENT")) {
          res.status(403).json({ error: "Support or Admin role required" });
          return;
        }
        const cfg = resolveTextLkRuntimeConfig(process.env);
        res.status(200).json({
          configured: cfg.configured,
          senderId: cfg.senderId,
          defaultGroupId: cfg.defaultGroupId,
          v3BaseUrl: cfg.v3BaseUrl,
          httpBaseUrl: cfg.httpBaseUrl,
        });
        return;
      }

      if (req.method === "POST" && path === "/textlk/contacts/list") {
        const cfg = resolveTextLkRuntimeConfig(process.env);
        const result = await callTextLkApi({
          action: "LIST_CONTACTS",
          groupId: body.groupId,
          page: body.page || 1,
          runtimeConfig: cfg,
        });
        res.status(200).json(result);
        return;
      }

      if (req.method === "POST" && path === "/textlk/contacts/view") {
        const cfg = resolveTextLkRuntimeConfig(process.env);
        const result = await callTextLkApi({
          action: "VIEW_CONTACT",
          groupId: body.groupId,
          contactUid: body.contactUid,
          runtimeConfig: cfg,
        });
        res.status(200).json(result);
        return;
      }

      if (req.method === "POST" && path === "/textlk/contacts/store") {
        const cfg = resolveTextLkRuntimeConfig(process.env);
        const result = await callTextLkApi({
          action: "CREATE_CONTACT",
          groupId: body.groupId,
          phone: body.phone || body.PHONE,
          firstName: body.firstName || body.FIRST_NAME || "",
          lastName: body.lastName || body.LAST_NAME || "",
          extraFields: body.extraFields || {},
          runtimeConfig: cfg,
        });
        res.status(200).json(result);
        return;
      }

      if (req.method === "PATCH" && path === "/textlk/contacts/update") {
        const cfg = resolveTextLkRuntimeConfig(process.env);
        const result = await callTextLkApi({
          action: "UPDATE_CONTACT",
          groupId: body.groupId,
          contactUid: body.contactUid,
          phone: body.phone || body.PHONE,
          firstName: body.firstName || body.FIRST_NAME || "",
          lastName: body.lastName || body.LAST_NAME || "",
          extraFields: body.extraFields || {},
          runtimeConfig: cfg,
        });
        res.status(200).json(result);
        return;
      }

      if (req.method === "DELETE" && path === "/textlk/contacts/delete") {
        const cfg = resolveTextLkRuntimeConfig(process.env);
        const result = await callTextLkApi({
          action: "DELETE_CONTACT",
          groupId: body.groupId,
          contactUid: body.contactUid,
          runtimeConfig: cfg,
        });
        res.status(200).json(result);
        return;
      }

      if (req.method === "POST" && path === "/textlk/sms/send") {
        if (!hasRequiredRole(actor.role, "SUPPORT_AGENT")) {
          res.status(403).json({ error: "Support or Admin role required to send SMS" });
          return;
        }
        const cfg = resolveTextLkRuntimeConfig(process.env);
        const result = await callTextLkApi({
          action: "SEND_SMS",
          recipient: body.recipient,
          senderId: body.senderId,
          message: body.message,
          useHttpEndpoint: Boolean(body.useHttpEndpoint),
          runtimeConfig: cfg,
        });
        await writeAuditLog(db, {
          actorUid: actor.uid,
          actorRole: actor.role,
          action: "TEXT_LK_SMS_SENT",
          targetId: String(body.recipient || "").slice(-4),
          details: `Sent SMS via ${body.useHttpEndpoint ? "HTTP" : "v3"}`,
        });
        res.status(200).json(result);
        return;
      }

      res.status(404).json({ error: "Not found" });
    } catch (err) {
      const status = err.message === "Missing Bearer token." ? 401 : 400;
      res.status(status).json({ error: err.message || "Request failed" });
    }
  }
);

/**
 * Scheduled server-clock restriction expiry sweeper (runs every 15 minutes).
 */
exports.expireRestrictionsSweep = onSchedule("every 15 minutes", async () => {
  const db = getDb();
  const nowMs = Date.now();
  const snap = await db
    .collection("userEnforcements")
    .where("accountStatus", "!=", "ACTIVE")
    .limit(200)
    .get();

  for (const doc of snap.docs) {
    const evalRes = evaluateActiveEnforcement(doc.data(), nowMs);
    if (evalRes.dirty) {
      await doc.ref.update({
        accountStatus: evalRes.accountStatus,
        featureExpiries: evalRes.activeFeatures,
        globalExpiresAtEpochMs: evalRes.globalExpiresAtEpochMs,
        updatedAt: FieldValue.serverTimestamp(),
      });
      await db.collection("users").doc(doc.id).set(
        {
          suspended: evalRes.globallyBlocked,
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    }
  }
});
