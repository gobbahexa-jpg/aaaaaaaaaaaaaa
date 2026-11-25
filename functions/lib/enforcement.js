"use strict";

const crypto = require("node:crypto");

const ROLE_LEVEL = {
  USER: 0,
  SUPPORT_AGENT: 10,
  SENIOR_SUPPORT_AGENT: 20,
  MODERATOR: 30,
  SENIOR_MODERATOR: 40,
  ADMIN: 50,
  SUPER_ADMIN: 60,
};

const VALID_ROLES = Object.keys(ROLE_LEVEL);

const ACCOUNT_STATUSES = [
  "ACTIVE",
  "WARNING_ISSUED",
  "MESSAGING_RESTRICTED",
  "MEDIA_RESTRICTED",
  "GROUP_CREATION_RESTRICTED",
  "GROUP_PARTICIPATION_RESTRICTED",
  "CHANNEL_CREATION_RESTRICTED",
  "CHANNEL_POSTING_RESTRICTED",
  "CALLING_RESTRICTED",
  "TEMP_SUSPENDED",
  "PERM_SUSPENDED",
  "UNDER_REVIEW",
  "DELETION_PENDING",
];

const FEATURE_KEYS = [
  "dm",
  "groupSend",
  "media",
  "files",
  "voice",
  "calls",
  "createGroup",
  "joinGroup",
  "createChannel",
  "channelPost",
  "comment",
  "stories",
  "editProfile",
  "invitations",
];

const DURATION_PRESETS_MS = {
  "1h": 3600 * 1000,
  "6h": 6 * 3600 * 1000,
  "12h": 12 * 3600 * 1000,
  "24h": 24 * 3600 * 1000,
  "3d": 3 * 24 * 3600 * 1000,
  "7d": 7 * 24 * 3600 * 1000,
  "30d": 30 * 24 * 3600 * 1000,
};

function hasRequiredRole(actorRole, minRole) {
  const actorLevel = ROLE_LEVEL[actorRole] ?? 0;
  const requiredLevel = ROLE_LEVEL[minRole] ?? 999;
  return actorLevel >= requiredLevel;
}

function canAssignRole(actorRole, targetRole) {
  if (!VALID_ROLES.includes(targetRole)) return false;
  if (actorRole === "SUPER_ADMIN") return true;
  if (actorRole === "ADMIN") {
    return targetRole !== "SUPER_ADMIN" && targetRole !== "ADMIN";
  }
  return false;
}

function computeExpiryEpochMs(durationCode, customDurationMs, nowMs = Date.now()) {
  if (durationCode === "permanent" || durationCode === "termination") {
    return -1;
  }
  if (durationCode === "custom") {
    const ms = Number(customDurationMs);
    if (!Number.isFinite(ms) || ms < 60_000) {
      throw new Error("Custom restriction duration must be at least 1 minute.");
    }
    return nowMs + Math.floor(ms);
  }
  const preset = DURATION_PRESETS_MS[durationCode];
  if (!preset) {
    throw new Error(`Unsupported restriction duration preset: ${durationCode}`);
  }
  return nowMs + preset;
}

/**
 * Evaluates active feature restrictions and expired cleanup using trusted server clock `nowMs`.
 */
function evaluateActiveEnforcement(enforcementDoc, nowMs = Date.now()) {
  if (!enforcementDoc) {
    return {
      accountStatus: "ACTIVE",
      globallyBlocked: false,
      activeFeatures: {},
      expiredFeatures: [],
      dirty: false,
    };
  }
  const currentFeatureMap = enforcementDoc.featureExpiries || {};
  const activeFeatures = {};
  const expiredFeatures = [];

  for (const [feature, expiryMs] of Object.entries(currentFeatureMap)) {
    if (!FEATURE_KEYS.includes(feature)) continue;
    if (expiryMs === -1 || expiryMs > nowMs) {
      activeFeatures[feature] = expiryMs;
    } else {
      expiredFeatures.push(feature);
    }
  }

  let nextStatus = ACCOUNT_STATUSES.includes(enforcementDoc.accountStatus)
    ? enforcementDoc.accountStatus
    : "ACTIVE";
  let globalExpiry = Number(enforcementDoc.globalExpiresAtEpochMs || 0);
  let globalExpired = false;

  if (
    ["TEMP_SUSPENDED", "PERM_SUSPENDED", "DELETION_PENDING"].includes(nextStatus)
  ) {
    if (nextStatus === "TEMP_SUSPENDED" && globalExpiry > 0 && globalExpiry <= nowMs) {
      globalExpired = true;
      globalExpiry = 0;
      nextStatus =
        Object.keys(activeFeatures).length > 0
          ? deriveStatusFromFeatures(activeFeatures)
          : "ACTIVE";
    }
  } else if (
    nextStatus !== "ACTIVE" &&
    nextStatus !== "WARNING_ISSUED" &&
    nextStatus !== "UNDER_REVIEW" &&
    Object.keys(activeFeatures).length === 0
  ) {
    nextStatus = "ACTIVE";
  }

  return {
    accountStatus: nextStatus,
    globallyBlocked:
      ["TEMP_SUSPENDED", "PERM_SUSPENDED", "DELETION_PENDING"].includes(nextStatus) &&
      (globalExpiry === -1 || globalExpiry > nowMs),
    globalExpiresAtEpochMs: globalExpiry,
    activeFeatures,
    expiredFeatures,
    dirty: expiredFeatures.length > 0 || globalExpired,
  };
}

function deriveStatusFromFeatures(activeFeatures) {
  const keys = Object.keys(activeFeatures);
  if (keys.length === 0) return "ACTIVE";
  if (keys.includes("dm") || keys.includes("groupSend")) return "MESSAGING_RESTRICTED";
  if (keys.includes("media") || keys.includes("files") || keys.includes("voice"))
    return "MEDIA_RESTRICTED";
  if (keys.includes("createGroup")) return "GROUP_CREATION_RESTRICTED";
  if (keys.includes("joinGroup")) return "GROUP_PARTICIPATION_RESTRICTED";
  if (keys.includes("createChannel")) return "CHANNEL_CREATION_RESTRICTED";
  if (keys.includes("channelPost")) return "CHANNEL_POSTING_RESTRICTED";
  if (keys.includes("calls")) return "CALLING_RESTRICTED";
  return "WARNING_ISSUED";
}

/**
 * Progressive discipline & safety check:
 * Never permit automated permanent suspension without explicit human moderator/admin approval.
 */
function buildEnforcementMutation({
  existingEnforcement,
  actorUid,
  actorRole,
  targetUid,
  actionType,
  accountStatus,
  features = [],
  durationCode = "24h",
  customDurationMs = 0,
  reason,
  humanApprovedPermanentBan = false,
  nowMs = Date.now(),
}) {
  if (!hasRequiredRole(actorRole, "MODERATOR")) {
    throw new Error("Insufficient role: moderator or higher required.");
  }
  if (!reason || String(reason).trim().length < 5) {
    throw new Error("Enforcement reason must be at least 5 characters.");
  }
  if (actorUid === targetUid) {
    throw new Error("Administrators cannot apply enforcement to their own account.");
  }
  if (
    (accountStatus === "PERM_SUSPENDED" || actionType === "PERM_BAN") &&
    (!hasRequiredRole(actorRole, "SENIOR_MODERATOR") || !humanApprovedPermanentBan)
  ) {
    throw new Error(
      "Permanent suspension requires Senior Moderator/Admin role and explicit human confirmation."
    );
  }

  const base = evaluateActiveEnforcement(existingEnforcement, nowMs);
  const nextFeatures = { ...base.activeFeatures };
  const warningCount = Number(existingEnforcement?.warningCount || 0);
  let nextWarningCount = warningCount;
  let nextGlobalExpiry = base.globalExpiresAtEpochMs || 0;
  let nextStatus = accountStatus || base.accountStatus;

  if (actionType === "CLEAR_ALL") {
    return {
      userId: targetUid,
      accountStatus: "ACTIVE",
      featureExpiries: {},
      globalExpiresAtEpochMs: 0,
      warningCount: nextWarningCount,
      lastReason: String(reason).trim().slice(0, 500),
      lastModeratorUid: actorUid,
      appealEligible: false,
      updatedAtEpochMs: nowMs,
    };
  }

  const expiryMs = computeExpiryEpochMs(durationCode, customDurationMs, nowMs);
  if (actionType === "WARNING") {
    nextWarningCount += 1;
    if (nextStatus === "ACTIVE") {
      nextStatus = nextWarningCount >= 3 ? "TEMP_SUSPENDED" : "WARNING_ISSUED";
    }
    if (nextWarningCount >= 3 && nextStatus === "TEMP_SUSPENDED") {
      nextGlobalExpiry = nowMs + DURATION_PRESETS_MS["24h"];
    }
  } else if (actionType === "FEATURE_RESTRICTION") {
    for (const feat of features) {
      if (!FEATURE_KEYS.includes(feat)) {
        throw new Error(`Invalid feature restriction key: ${feat}`);
      }
      nextFeatures[feat] = expiryMs;
    }
    if (!accountStatus) {
      nextStatus = deriveStatusFromFeatures(nextFeatures);
    }
  } else if (actionType === "TEMP_SUSPEND") {
    nextStatus = "TEMP_SUSPENDED";
    nextGlobalExpiry = expiryMs === -1 ? nowMs + DURATION_PRESETS_MS["7d"] : expiryMs;
  } else if (actionType === "PERM_BAN") {
    nextStatus = "PERM_SUSPENDED";
    nextGlobalExpiry = -1;
  } else if (actionType === "UNDER_REVIEW") {
    nextStatus = "UNDER_REVIEW";
  }

  return {
    userId: targetUid,
    accountStatus: ACCOUNT_STATUSES.includes(nextStatus) ? nextStatus : "UNDER_REVIEW",
    featureExpiries: nextFeatures,
    globalExpiresAtEpochMs: nextGlobalExpiry,
    warningCount: nextWarningCount,
    lastReason: String(reason).trim().slice(0, 500),
    lastModeratorUid: actorUid,
    appealEligible: nextStatus !== "ACTIVE",
    updatedAtEpochMs: nowMs,
  };
}

/**
 * Appeal validation enforcing distinct reviewer where possible and preventing infinite spam appeals.
 */
function validateAppealReview({
  appealDoc,
  reviewerUid,
  reviewerRole,
  decision,
  decisionNote,
}) {
  if (!hasRequiredRole(reviewerRole, "MODERATOR")) {
    throw new Error("Moderator permissions required to review appeals.");
  }
  if (!appealDoc) {
    throw new Error("Appeal not found.");
  }
  if (
    appealDoc.originalModeratorUid &&
    appealDoc.originalModeratorUid === reviewerUid &&
    !hasRequiredRole(reviewerRole, "ADMIN")
  ) {
    throw new Error(
      "Appeal must be reviewed by a different moderator from the original enforcer."
    );
  }
  const allowedDecisions = [
    "UNDER_REVIEW",
    "INFO_REQUESTED",
    "APPROVED",
    "REJECTED",
    "CLOSED",
  ];
  if (!allowedDecisions.includes(decision)) {
    throw new Error(`Unsupported appeal decision: ${decision}`);
  }
  if (!decisionNote || String(decisionNote).trim().length < 5) {
    throw new Error("Decision explanation note is required.");
  }
  return {
    status: decision,
    reviewerUid,
    decisionNote: String(decisionNote).trim().slice(0, 1000),
    appealId: appealDoc.id || crypto.randomUUID(),
  };
}

/**
 * Sliding-window anti-spam & suspicious link heuristic scorer (triages into human queue, never auto-bans).
 */
function assessMessageSpamRisk({
  text = "",
  recentTimestamps = [],
  accountAgeMs = 86_400_000,
  nowMs = Date.now(),
}) {
  const window10s = recentTimestamps.filter((ts) => nowMs - ts <= 10_000).length;
  const window60s = recentTimestamps.filter((ts) => nowMs - ts <= 60_000).length;
  const lower = String(text).toLowerCase();
  const urlMatches = lower.match(/https?:\/\/\S+/g) || [];
  const suspiciousKeywords = [
    "free nitro",
    "claim reward",
    "verify wallet",
    "seed phrase",
    "otp code",
    "urgent transfer",
  ].filter((kw) => lower.includes(kw));

  let riskScore = 0;
  const reasons = [];

  if (window10s >= 8) {
    riskScore += 45;
    reasons.push("rapid_burst_10s");
  }
  if (window60s >= 25) {
    riskScore += 35;
    reasons.push("high_volume_60s");
  }
  if (accountAgeMs < 24 * 3600 * 1000 && urlMatches.length >= 2) {
    riskScore += 30;
    reasons.push("new_account_multi_links");
  }
  if (suspiciousKeywords.length > 0) {
    riskScore += 40;
    reasons.push(`phishing_keywords:${suspiciousKeywords.join(",")}`);
  }

  return {
    riskScore: Math.min(100, riskScore),
    rateLimited: window10s >= 10 || window60s >= 30,
    escalateToHumanQueue: riskScore >= 40,
    reasons,
  };
}

module.exports = {
  ROLE_LEVEL,
  VALID_ROLES,
  ACCOUNT_STATUSES,
  FEATURE_KEYS,
  hasRequiredRole,
  canAssignRole,
  computeExpiryEpochMs,
  evaluateActiveEnforcement,
  deriveStatusFromFeatures,
  buildEnforcementMutation,
  validateAppealReview,
  assessMessageSpamRisk,
};
