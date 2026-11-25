package com.example.core

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class BiometricAvailability {
    AVAILABLE,
    NONE_ENROLLED,
    UNAVAILABLE
}

object SecurityPrivacyManager {
    private const val PREFS_NAME = "dl_chat_security_vault"
    private const val PBKDF2_ITERATIONS = 31_000
    private val random = SecureRandom()

    private fun hashKey(uid: String) = "pin_hash_$uid"
    private fun saltKey(uid: String) = "pin_salt_$uid"
    private fun lastBgKey(uid: String) = "last_bg_ms_$uid"

    fun hasPinSet(context: Context, uid: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(hashKey(uid), null).isNullOrBlank() &&
            !prefs.getString(saltKey(uid), null).isNullOrBlank()
    }

    fun setPin(context: Context, uid: String, pin: String): Result<Unit> {
        if (pin.length !in 4..8 || !pin.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("PIN must be 4 to 8 digits."))
        }
        return try {
            val salt = ByteArray(16).also { random.nextBytes(it) }
            val digest = pbkdf2Hash(pin, salt)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(saltKey(uid), Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(hashKey(uid), Base64.encodeToString(digest, Base64.NO_WRAP))
                .apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun verifyPin(context: Context, uid: String, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saltB64 = prefs.getString(saltKey(uid), null) ?: return false
        val expectedB64 = prefs.getString(hashKey(uid), null) ?: return false
        return try {
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val expected = Base64.decode(expectedB64, Base64.NO_WRAP)
            val candidate = pbkdf2Hash(pin, salt)
            MessageDigest.isEqual(expected, candidate)
        } catch (_: Exception) {
            false
        }
    }

    fun clearPin(context: Context, uid: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(saltKey(uid))
            .remove(hashKey(uid))
            .remove(lastBgKey(uid))
            .apply()
    }

    fun recordBackgroundTimestamp(context: Context, uid: String, epochMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(lastBgKey(uid), epochMs)
            .apply()
    }

    fun shouldAutoLock(context: Context, uid: String, autoLockSeconds: Int, appLockEnabled: Boolean): Boolean {
        if (!appLockEnabled || !hasPinSet(context, uid)) return false
        if (autoLockSeconds <= 0) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastBg = prefs.getLong(lastBgKey(uid), 0L)
        if (lastBg <= 0L) return false
        return (System.currentTimeMillis() - lastBg) >= autoLockSeconds * 1000L
    }

    fun clearBackgroundTimestamp(context: Context, uid: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(lastBgKey(uid))
            .apply()
    }

    fun checkBiometricAvailability(context: Context): BiometricAvailability {
        val authenticators = allowedAuthenticators()
        return when (BiometricManager.from(context).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }
    }

    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        title: String = "Unlock DL Chat",
        subtitle: String = "Confirm your identity to open your chats",
        failedMessage: String = "Biometric check didn't match. Try again.",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError(failedMessage)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators())
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun allowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    private fun pbkdf2Hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
