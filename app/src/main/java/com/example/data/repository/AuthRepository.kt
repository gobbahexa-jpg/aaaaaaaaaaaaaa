package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.core.CountryDialCode
import com.example.core.CryptoIdentityHelper
import com.example.core.OperationType
import com.example.core.PhoneNumberHelper
import com.example.core.handleFirestoreError
import com.example.data.model.OtpChannel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

data class RegistrationInput(
    val username: String,
    val displayName: String,
    val bio: String = "Connected on DL Chat",
    val avatarColor: String = "#00E5FF",
    val discoverable: Boolean = true
) {
    fun sanitizedUsername(): String =
        username.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_').take(30)
}

class AuthRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    suspend fun attemptAutoSignIn(context: Context): Boolean {
        if (auth.currentUser != null) return true
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return false
        }
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                ensureUserBootstrap(context)
                true
            } else {
                false
            }
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No saved credentials available for auto sign-in: ${e.message}")
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent auto sign-in unavailable: ${e.message}")
            false
        }
    }

    suspend fun signInInteractive(
        activity: Activity,
        registration: RegistrationInput? = null,
        strContext: Context? = activity
    ): Result<FirebaseUser> {
        val resCtx = strContext ?: activity
        if (registration != null) {
            validateRegistrationInput(registration, resCtx)?.let { validationError ->
                return Result.failure(IllegalArgumentException(validationError))
            }
        }
        val clientId = try {
            resCtx.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return Result.failure(
                IllegalStateException(resCtx.getString(R.string.auth_err_config))
            )
        }
        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        return try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                val user = authResult.user
                    ?: return Result.failure(IllegalStateException(resCtx.getString(R.string.auth_signin_failed)))
                val bootstrap = ensureUserBootstrap(resCtx.applicationContext, registration)
                if (bootstrap.isFailure) {
                    return Result.failure(
                        bootstrap.exceptionOrNull()
                            ?: IllegalStateException(resCtx.getString(R.string.vm_profile_failed))
                    )
                }
                Result.success(user)
            } else {
                Result.failure(IllegalStateException(resCtx.getString(R.string.auth_signin_failed)))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Sign-in flow cancelled or dismissed: ${e.message}", e)
            Result.failure(e)
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No eligible account credential found: ${e.message}", e)
            Result.failure(IllegalStateException(resCtx.getString(R.string.auth_signin_failed), e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            Result.failure(e)
        }
    }

    suspend fun ensureUserBootstrap(
        context: Context,
        registration: RegistrationInput? = null
    ): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Not authenticated."))
        val uid = user.uid
        val userDocPath = "users/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val snap = userRef.get().await()
            val keyMaterial = CryptoIdentityHelper.getOrCreateIdentityKey(context, uid)
            val chosenColor = registration?.avatarColor
                ?.takeIf { it.length in 4..16 && it.startsWith("#") }
                ?: "#00E5FF"

            if (!snap.exists()) {
                val baseName = registration?.displayName?.trim()?.take(60)?.takeIf { it.isNotEmpty() }
                    ?: (user.displayName?.takeIf { it.isNotBlank() }
                        ?: user.email?.substringBefore("@")
                        ?: user.phoneNumber?.let { PhoneNumberHelper.maskPhone(it) }
                        ?: "Operator").take(60)
                val chosenHandle = registration?.sanitizedUsername()?.takeIf { it.length >= 3 }
                    ?: (baseName.lowercase()
                        .replace(Regex("[^a-z0-9_]"), "_")
                        .trim('_')
                        .ifBlank { "dl_user" }
                        .take(20) + "_" + uid.take(4).lowercase())
                val chosenBio = registration?.bio?.trim()?.take(240)?.ifBlank { "Connected on DL Chat" }
                    ?: "Connected on DL Chat"

                val profilePayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "username" to chosenHandle,
                    "displayName" to baseName,
                    "bio" to chosenBio,
                    "avatarColor" to chosenColor,
                    "onlineStatus" to "ONLINE",
                    "showLastSeen" to true,
                    "showOnline" to true,
                    "readReceipts" to true,
                    "discoverable" to (registration?.discoverable ?: true),
                    "suspended" to false,
                    "publicKeyFingerprint" to keyMaterial.fingerprint,
                    "keyVersion" to keyMaterial.keyVersion,
                    "twoFactorEnabled" to false,
                    "profilePhotoVisibility" to "EVERYONE",
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (keyMaterial.publicKeyBase64.isNotBlank()) {
                    profilePayload["publicKeyBase64"] = keyMaterial.publicKeyBase64
                }
                userRef.set(profilePayload).await()
            } else if (registration != null) {
                val cleanName = registration.displayName.trim().take(60)
                val cleanHandle = registration.sanitizedUsername()
                val cleanBio = registration.bio.trim().take(240)
                val updates = mutableMapOf<String, Any>(
                    "onlineStatus" to "ONLINE",
                    "publicKeyFingerprint" to keyMaterial.fingerprint,
                    "keyVersion" to keyMaterial.keyVersion,
                    "avatarColor" to chosenColor,
                    "discoverable" to registration.discoverable,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (keyMaterial.publicKeyBase64.isNotBlank()) {
                    updates["publicKeyBase64"] = keyMaterial.publicKeyBase64
                }
                if (cleanName.isNotEmpty()) updates["displayName"] = cleanName
                if (cleanHandle.length in 3..30) updates["username"] = cleanHandle
                if (cleanBio.isNotEmpty()) updates["bio"] = cleanBio
                userRef.update(updates).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "onlineStatus" to "ONLINE",
                    "publicKeyFingerprint" to keyMaterial.fingerprint,
                    "keyVersion" to keyMaterial.keyVersion,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (keyMaterial.publicKeyBase64.isNotBlank()) {
                    updates["publicKeyBase64"] = keyMaterial.publicKeyBase64
                }
                userRef.update(updates).await()
            }

            // Isolated PII record
            val privateRef = userRef.collection("private").document("info")
            val privateSnap = privateRef.get().await()
            if (!privateSnap.exists()) {
                privateRef.set(
                    mapOf(
                        "userId" to uid,
                        "email" to (user.email ?: ""),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Default settings record
            val settingsRef = userRef.collection("settings").document("preferences")
            val settingsSnap = settingsRef.get().await()
            if (!settingsSnap.exists()) {
                settingsRef.set(
                    mapOf(
                        "userId" to uid,
                        "themeMode" to "DARK",
                        "accentColorHex" to chosenColor,
                        "bubbleStyle" to "MODERN",
                        "fontScale" to 1.0,
                        "wallpaperId" to "nebula",
                        "notificationsEnabled" to true,
                        "soundEnabled" to true,
                        "dndEnabled" to false,
                        "aiEnabled" to true,
                        "autoDownloadMedia" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (registration != null) {
                settingsRef.update(
                    mapOf(
                        "accentColorHex" to chosenColor,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Current device session + public key bundle + isolated multi-account registry
            val deviceId = ("android_${Build.MODEL}").replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
            val deviceRef = userRef.collection("devices").document(deviceId)
            val deviceSnap = deviceRef.get().await()
            val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80).ifBlank { "Android Device" }
            val osLabel = "Android ${Build.VERSION.RELEASE}"
            val wasRevoked = deviceSnap.exists() && deviceSnap.getBoolean("revoked") == true
            if (wasRevoked) {
                deviceRef.delete().await()
            }
            if (!deviceSnap.exists() || wasRevoked) {
                deviceRef.set(
                    mapOf(
                        "userId" to uid,
                        "deviceName" to deviceLabel,
                        "platform" to osLabel,
                        "deviceType" to "ANDROID",
                        "osName" to osLabel,
                        "browserName" to "DL Chat Android",
                        "connectionStatus" to "ONLINE",
                        "tokenRotationCount" to 1,
                        "revoked" to false,
                        "firstLinkedAt" to FieldValue.serverTimestamp(),
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                deviceRef.update(
                    mapOf(
                        "connectionStatus" to "ONLINE",
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (keyMaterial.publicKeyBase64.isNotBlank()) {
                val bundleRef = userRef.collection("deviceKeyBundles").document(deviceId)
                val bundleSnap = bundleRef.get().await()
                if (!bundleSnap.exists()) {
                    bundleRef.set(
                        mapOf(
                            "ownerUid" to uid,
                            "deviceId" to deviceId,
                            "publicKeyBase64" to keyMaterial.publicKeyBase64,
                            "fingerprint" to keyMaterial.fingerprint,
                            "keyVersion" to keyMaterial.keyVersion,
                            "revoked" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    bundleRef.update(
                        mapOf(
                            "publicKeyBase64" to keyMaterial.publicKeyBase64,
                            "fingerprint" to keyMaterial.fingerprint,
                            "keyVersion" to keyMaterial.keyVersion,
                            "revoked" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            val finalName = snap.getString("displayName") ?: registration?.displayName ?: user.displayName ?: "Operator"
            val finalHandle = snap.getString("username") ?: registration?.sanitizedUsername() ?: "dl_${uid.take(6)}"
            val acctRef = userRef.collection("accountSessions").document(uid)
            val acctSnap = acctRef.get().await()
            if (!acctSnap.exists()) {
                acctRef.set(
                    mapOf(
                        "userId" to uid,
                        "email" to (user.email ?: ""),
                        "displayName" to finalName.take(60),
                        "username" to finalHandle.take(30),
                        "active" to true,
                        "lastUsedAtEpochMs" to System.currentTimeMillis(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                acctRef.update(
                    mapOf(
                        "displayName" to finalName.take(60),
                        "username" to finalHandle.take(30),
                        "active" to true,
                        "lastUsedAtEpochMs" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            com.example.core.MultiAccountManager.recordAccount(
                context = context,
                uid = uid,
                email = user.email ?: "",
                displayName = finalName,
                username = finalHandle
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDocPath)
            Result.failure(e)
        }
    }

    suspend fun signInWithPhoneCredential(
        context: Context,
        credential: PhoneAuthCredential,
        country: CountryDialCode,
        otpChannel: OtpChannel = OtpChannel.FIREBASE_SMS,
        whatsappOptIn: Boolean = false,
        registration: RegistrationInput? = null
    ): Result<FirebaseUser> {
        return try {
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user
                ?: return Result.failure(IllegalStateException("Phone sign-in failed."))
            val bootstrap = ensureUserBootstrap(context.applicationContext, registration)
            if (bootstrap.isFailure) {
                return Result.failure(bootstrap.exceptionOrNull() ?: IllegalStateException("Profile setup failed."))
            }
            val phoneE164 = user.phoneNumber.orEmpty()
            if (PhoneNumberHelper.isValidE164(phoneE164)) {
                syncPhoneAuthProfile(
                    uid = user.uid,
                    phoneE164 = phoneE164,
                    country = country,
                    otpChannel = otpChannel,
                    verificationStatus = "VERIFIED",
                    whatsappOptIn = whatsappOptIn
                )
            }
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Phone sign-in failed", e)
            Result.failure(IllegalStateException(PhoneAuthErrorMapper.map(context, e), e))
        }
    }

    suspend fun linkOrUpdatePhoneCredential(
        context: Context,
        credential: PhoneAuthCredential,
        country: CountryDialCode,
        otpChannel: OtpChannel = OtpChannel.FIREBASE_SMS,
        whatsappOptIn: Boolean = false
    ): Result<FirebaseUser> {
        val current = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in first to link a phone number."))
        return try {
            val hasPhoneAlready = current.providerData.any { it.providerId == PhoneAuthProvider.PROVIDER_ID }
            if (hasPhoneAlready) {
                current.updatePhoneNumber(credential).await()
            } else {
                current.linkWithCredential(credential).await()
            }
            val phoneE164 = current.phoneNumber.orEmpty()
            if (PhoneNumberHelper.isValidE164(phoneE164)) {
                syncPhoneAuthProfile(
                    uid = current.uid,
                    phoneE164 = phoneE164,
                    country = country,
                    otpChannel = otpChannel,
                    verificationStatus = if (hasPhoneAlready) "MIGRATED" else "LINKED",
                    whatsappOptIn = whatsappOptIn
                )
            }
            Result.success(current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Phone link/update failed", e)
            Result.failure(IllegalStateException(PhoneAuthErrorMapper.map(context, e), e))
        }
    }

    private suspend fun syncPhoneAuthProfile(
        uid: String,
        phoneE164: String,
        country: CountryDialCode,
        otpChannel: OtpChannel,
        verificationStatus: String,
        whatsappOptIn: Boolean
    ) {
        try {
            val ref = db.collection("phoneAuthProfiles").document(uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "phoneE164" to phoneE164,
                        "countryCode" to country.isoCode,
                        "countryDialCode" to country.dialCode,
                        "otpProvider" to otpChannel.name,
                        "verificationStatus" to verificationStatus,
                        "whatsappOptIn" to whatsappOptIn,
                        "verifiedAtEpochMs" to System.currentTimeMillis(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "phoneE164" to phoneE164,
                        "countryCode" to country.isoCode,
                        "countryDialCode" to country.dialCode,
                        "otpProvider" to otpChannel.name,
                        "verificationStatus" to verificationStatus,
                        "whatsappOptIn" to whatsappOptIn,
                        "verifiedAtEpochMs" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            db.collection("authSecurityEvents").document().set(
                mapOf(
                    "userId" to uid,
                    "eventType" to "PHONE_$verificationStatus",
                    "channel" to otpChannel.name,
                    "maskedTarget" to PhoneNumberHelper.maskPhone(phoneE164),
                    "success" to true,
                    "detail" to "Phone verified (${country.isoCode} ${country.dialCode})",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Phone profile metadata sync warning: ${e.message}")
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            val uid = auth.currentUser?.uid
            if (!uid.isNullOrBlank()) {
                try {
                    db.collection("users").document(uid).update(
                        mapOf(
                            "onlineStatus" to "OFFLINE",
                            "lastSeenAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (_: Exception) {
                }
            }
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    suspend fun deleteAccount(context: Context): Result<Unit> = withContext(NonCancellable) {
        val user = auth.currentUser ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
        val uid = user.uid
        try {
            db.collection("users").document(uid).delete().await()
            user.delete().await()
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "AuthRepository"

        @JvmOverloads
        fun validateRegistrationInput(input: RegistrationInput, context: Context? = null): String? {
            if (input.displayName.trim().length < 2) {
                return context?.getString(R.string.auth_err_invalid_name)
                    ?: "Enter a display name with at least 2 characters."
            }
            val cleanHandle = input.sanitizedUsername()
            if (cleanHandle.length < 3) {
                return context?.getString(R.string.auth_err_invalid_username)
                    ?: "Usernames need 3–30 letters, numbers, or underscores."
            }
            if (input.bio.trim().length > 240) {
                return "Bio must be 240 characters or fewer."
            }
            return null
        }
    }
}
