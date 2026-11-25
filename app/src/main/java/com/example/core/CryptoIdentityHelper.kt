package com.example.core

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class IdentityKeyMaterial(
    val fingerprint: String,
    val publicKeyBase64: String,
    val keyVersion: Int
)

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

object CryptoIdentityHelper {
    private const val PREFS_NAME = "dl_chat_crypto_identity"
    private const val KEY_FINGERPRINT = "identity_fingerprint"
    private const val KEY_PUB_B64 = "identity_pub_b64"
    private const val KEY_PRIV_B64 = "identity_priv_b64"
    private const val KEY_VERSION = "identity_key_version"

    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private val secureRandom = SecureRandom()

    fun getOrCreateFingerprint(context: Context, uid: String): String =
        getOrCreateIdentityKey(context, uid).fingerprint

    fun getOrCreateIdentityKey(context: Context, uid: String): IdentityKeyMaterial {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedFp = prefs.getString("${KEY_FINGERPRINT}_$uid", null)
        val cachedPub = prefs.getString("${KEY_PUB_B64}_$uid", null)
        val cachedPriv = prefs.getString("${KEY_PRIV_B64}_$uid", null)
        val cachedVer = prefs.getInt("${KEY_VERSION}_$uid", 1)

        if (!cachedFp.isNullOrBlank() && !cachedPub.isNullOrBlank() && !cachedPriv.isNullOrBlank()) {
            return IdentityKeyMaterial(
                fingerprint = cachedFp,
                publicKeyBase64 = cachedPub,
                keyVersion = cachedVer.coerceAtLeast(1)
            )
        }

        return generateAndPersistKeyMaterial(context, uid, version = 1)
    }

    fun rotateIdentityKey(context: Context, uid: String): IdentityKeyMaterial {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nextVersion = prefs.getInt("${KEY_VERSION}_$uid", 1) + 1
        return generateAndPersistKeyMaterial(context, uid, version = nextVersion)
    }

    private fun generateAndPersistKeyMaterial(
        context: Context,
        uid: String,
        version: Int
    ): IdentityKeyMaterial {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            val pair = generateEcKeyPair()
            val pubBytes = pair.public.encoded
            val privBytes = pair.private.encoded
            val pubB64 = Base64.encodeToString(pubBytes, Base64.NO_WRAP)
            val privB64 = Base64.encodeToString(privBytes, Base64.NO_WRAP)
            val fp = formatFingerprint(pubBytes)

            prefs.edit()
                .putString("${KEY_PUB_B64}_$uid", pubB64)
                .putString("${KEY_PRIV_B64}_$uid", privB64)
                .putString("${KEY_FINGERPRINT}_$uid", fp)
                .putInt("${KEY_VERSION}_$uid", version)
                .apply()

            IdentityKeyMaterial(fp, pubB64, version)
        } catch (_: Exception) {
            val fp = formatFingerprint(uid.toByteArray())
            IdentityKeyMaterial(fp, "", version)
        }
    }

    fun generateEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    fun formatFingerprint(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
        val hex = digest.take(8).joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString("-")
    }

    fun computeSharedSafetyNumber(myFingerprint: String, peerFingerprint: String): String {
        val ordered = listOf(myFingerprint, peerFingerprint).sorted().joinToString(":")
        val digest = MessageDigest.getInstance("SHA-256").digest(ordered.toByteArray())
        return digest.take(12).joinToString("") { "%02d".format((it.toInt() and 0xFF) % 100) }
            .chunked(4)
            .joinToString(" ")
    }

    fun deriveSharedAesKey(myPrivateKey: PrivateKey, peerPublicKey: PublicKey, contextInfo: String): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(myPrivateKey)
        agreement.doPhase(peerPublicKey, true)
        val sharedSecret = agreement.generateSecret()
        return hkdfSha256(
            ikm = sharedSecret,
            salt = "dl-chat-e2ee-v2".toByteArray(Charsets.UTF_8),
            info = contextInfo.toByteArray(Charsets.UTF_8),
            outputLen = 32
        )
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLen: Int): ByteArray {
        val extractMac = Mac.getInstance("HmacSHA256")
        extractMac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extractMac.doFinal(ikm)

        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        expandMac.update(info)
        expandMac.update(0x01.toByte())
        return expandMac.doFinal().copyOf(outputLen)
    }

    fun encryptAesGcm(plaintext: String, aesKeyBytes: ByteArray): EncryptedPayload {
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKeyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decryptAesGcm(ciphertextBase64: String, ivBase64: String, aesKeyBytes: ByteArray): String? = try {
        val cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKeyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    fun encryptForDirectPeer(
        context: Context,
        myUid: String,
        chatId: String,
        peerPublicKeyBase64: String?,
        plaintext: String
    ): EncryptedPayload? {
        if (peerPublicKeyBase64.isNullOrBlank()) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val privB64 = prefs.getString("${KEY_PRIV_B64}_$myUid", null) ?: return null
        return try {
            val kf = KeyFactory.getInstance("EC")
            val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privB64, Base64.NO_WRAP)))
            val peerPub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)))
            val aesKey = deriveSharedAesKey(privKey, peerPub, "chat:$chatId")
            encryptAesGcm(plaintext, aesKey)
        } catch (_: Exception) {
            null
        }
    }

    fun decryptForDirectPeer(
        context: Context,
        myUid: String,
        chatId: String,
        peerPublicKeyBase64: String?,
        ciphertextBase64: String?,
        ivBase64: String?
    ): String? {
        if (peerPublicKeyBase64.isNullOrBlank() || ciphertextBase64.isNullOrBlank() || ivBase64.isNullOrBlank()) {
            return null
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val privB64 = prefs.getString("${KEY_PRIV_B64}_$myUid", null) ?: return null
        return try {
            val kf = KeyFactory.getInstance("EC")
            val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privB64, Base64.NO_WRAP)))
            val peerPub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)))
            val aesKey = deriveSharedAesKey(privKey, peerPub, "chat:$chatId")
            decryptAesGcm(ciphertextBase64, ivBase64, aesKey)
        } catch (_: Exception) {
            null
        }
    }

    fun encryptBackupJson(plainJson: String, passphrase: String): String {
        require(passphrase.length >= 6) { "Backup passphrase must be at least 6 characters." }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, 65_536, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derivedBytes = factory.generateSecret(keySpec).encoded
        val encrypted = encryptAesGcm(plainJson, derivedBytes)
        return JSONObject().apply {
            put("format", "DL_CHAT_BACKUP_V2")
            put("alg", "PBKDF2WithHmacSHA256+AES-256-GCM")
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", encrypted.ivBase64)
            put("ciphertext", encrypted.ciphertextBase64)
        }.toString(2)
    }

    fun decryptBackupJson(encryptedEnvelope: String, passphrase: String): String? = try {
        val obj = JSONObject(encryptedEnvelope)
        if (obj.optString("format") != "DL_CHAT_BACKUP_V2") return null
        val salt = Base64.decode(obj.getString("salt"), Base64.NO_WRAP)
        val iv = obj.getString("iv")
        val ciphertext = obj.getString("ciphertext")
        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, 65_536, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derivedBytes = factory.generateSecret(keySpec).encoded
        decryptAesGcm(ciphertext, iv, derivedBytes)
    } catch (_: Exception) {
        null
    }
}
