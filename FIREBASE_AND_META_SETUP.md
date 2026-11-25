# DL Chat V6 — Firebase Phone Auth, Cloud Functions & Meta WhatsApp Business Setup

This document explains the external console and secret-management steps required to activate Phone OTP Authentication, Cloud Functions enforcement APIs, and the official Meta WhatsApp Business Cloud API for **DL Chat**.

> **Security Rule:** Never place `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_APP_SECRET`, or `WHATSAPP_WEBHOOK_VERIFY_TOKEN` inside the Android app, `.env`, `BuildConfig`, or Cloud Firestore. They belong strictly in Firebase Secret Manager / Cloud Functions runtime environment.

---

## 1. Firebase Phone Number Authentication & App Check

1. Open the **Firebase Console** for your DL Chat project (`ai-studio-android-dlchat-bddcbd1b-8921-4576-a2b4-2b965a255784`).
2. **Enable Phone Provider:**
   - Go to **Authentication -> Sign-in method -> Add new provider -> Phone** and enable it.
   - Optionally add test phone numbers (for example `+94 77 123 4567` with a fixed 6-digit test code) under **Phone numbers for testing** for QA without consuming SMS quota.
3. **Register Android SHA-1 and SHA-256 Fingerprints:**
   - Go to **Project Settings -> Your apps -> Android (`com.aistudio.dlchat.kxvmpq`)**.
   - Add both debug and release signing SHA-1 & SHA-256 fingerprints so Play Integrity / SafetyNet and Firebase Phone Auth succeed without `ERROR_APP_NOT_AUTHORIZED`.
4. **Configure Firebase App Check (Play Integrity):**
   - Go to **Build -> App Check -> Apps -> Register Play Integrity** with your release/upload SHA-256 certificate.
   - This protects Firebase AI Logic and Phone Auth abuse checks.

---

## 2. Cloud Functions Backend Deployment (`/functions`)

The `/functions` directory includes:
- `whatsappWebhook` (GET challenge verification + POST `x-hub-signature-256` HMAC-SHA256 verified Meta delivery/inbound webhook)
- `whatsappHealth` (admin-only configuration & template readiness inspection)
- `sendWhatsAppSupportMessage` (staff-only support reply & security alert dispatch)
- `enforceModerationAction`, `reviewAppeal`, and `assignStaffRole` (RBAC + distinct-reviewer + audit logging endpoints)
- Scheduled cleanup (`expireTimedRestrictions`)

### Deploy Steps

1. Ensure the Firebase project is on the **Blaze (pay-as-you-go)** plan.
2. Install dependencies inside `functions/`:
   ```bash
   cd functions && npm install
   ```
3. Configure confidential secrets via **Firebase Secret Manager**:
   ```bash
   firebase functions:secrets:set WHATSAPP_ACCESS_TOKEN
   firebase functions:secrets:set WHATSAPP_APP_SECRET
   firebase functions:secrets:set WHATSAPP_WEBHOOK_VERIFY_TOKEN
   ```
4. Set non-secret runtime values in `functions/.env` (see `functions/.env.example`):
   - `FIREBASE_PROJECT_ID`
   - `FIRESTORE_DATABASE_ID=ai-studio-android-dlchat-bddcbd1b-8921-4576-a2b4-2b965a255784`
   - `WHATSAPP_GRAPH_API_VERSION=v21.0`
   - `WHATSAPP_BUSINESS_ACCOUNT_ID`
   - `WHATSAPP_PHONE_NUMBER_ID`
   - `WHATSAPP_DISPLAY_NUMBER`
   - `WHATSAPP_SUPPORT_TEMPLATE_NAME=dl_chat_support_update`
   - `WHATSAPP_SECURITY_TEMPLATE_NAME=dl_chat_security_alert`
   - `WHATSAPP_OTP_TEMPLATE_NAME` (only after Meta approves an Authentication template)
5. Deploy Cloud Functions and Firestore rules:
   ```bash
   firebase deploy --only functions,firestore:rules
   ```
6. Copy the deployed HTTPS base URL of your functions (e.g., `https://<region>-<project>.cloudfunctions.net`) into **DL_BACKEND_BASE_URL** in the Secrets panel (and rebuild the Android APK).

---

## 3. Meta WhatsApp Business Platform Setup

1. Create or open a **Meta Developer App** (Business type) linked to your **Meta Business Portfolio**.
2. Add the **WhatsApp** product and link your official **WhatsApp Business Account (WABA)** and business phone number.
3. Generate a **System User Permanent Access Token** with `whatsapp_business_messaging` and `whatsapp_business_management` permissions; store it in `WHATSAPP_ACCESS_TOKEN`.
4. Copy your **App Secret** (`WHATSAPP_APP_SECRET`), **Phone Number ID** (`WHATSAPP_PHONE_NUMBER_ID`), and **WABA ID** (`WHATSAPP_BUSINESS_ACCOUNT_ID`).
5. **Configure Webhook:**
   - Callback URL: `https://<region>-<project>.cloudfunctions.net/whatsappWebhook`
   - Verify Token: exact value of `WHATSAPP_WEBHOOK_VERIFY_TOKEN`
   - Subscribe to webhook fields: `messages` (covers inbound support messages and delivery status updates).
6. **Message Templates:**
   - Submit **Utility** templates (`dl_chat_support_update`, `dl_chat_security_alert`) for support ticket updates and security notices.
   - Only enable **WhatsApp OTP** (`WHATSAPP_OTP_TEMPLATE_NAME`) if Meta approves an **Authentication** template for your business account. Otherwise, Firebase Phone SMS OTP remains the primary and authoritative verification flow.

---

## 4. Verifying Inside DL Chat Admin Console

1. Sign in with an `ADMIN` or `SUPER_ADMIN` account.
2. Open **Settings -> Admin & Moderation Console -> WhatsApp Business**.
3. Tap **Refresh Health** to verify `configured`, `webhookConfigured`, `otpProviderEnabled`, and `otpTemplateApproved` directly from `whatsappHealth`.

---

## 5. Text.lk — Sri Lanka SMS Gateway & Contacts API v3

DL Chat supports Text.lk Contacts CRUD (`https://app.text.lk/api/v3/contacts`) and SMS dispatch (`https://app.text.lk/api/v3/sms/send` and `https://app.text.lk/api/http/`).

1. **Secrets Configuration:**
   - For direct client testing in this workspace, add `TEXT_LK_API_TOKEN` (plus optional `TEXT_LK_SENDER_ID` and `TEXT_LK_DEFAULT_GROUP_ID`) in the **Secrets panel**.
   - For production server-mediated dispatch, store the token in Firebase Secret Manager (`firebase functions:secrets:set TEXT_LK_API_TOKEN`) and configure `DL_BACKEND_BASE_URL`.
2. **Contacts & SMS Features:**
   - **Contacts Tab -> Text.lk SMS Contacts:** Create, view, update, delete, and browse paginated contacts inside any Text.lk Contact Group (`group_id`), or prefill directly from a DL Chat contact.
   - **Admin Console -> Text.lk SMS Gateway:** Manage Text.lk contact groups and send direct Sri Lanka / international SMS alerts via API v3 or the HTTP API endpoint.

