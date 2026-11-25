package com.example.core

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.security.MessageDigest
import java.security.SecureRandom

sealed interface ParsedDlQrPayload {
    data class PairingQr(
        val ownerUid: String,
        val sessionId: String,
        val rawToken: String,
        val nonce: String,
        val expiresAtEpochMs: Long
    ) : ParsedDlQrPayload

    data class GroupInviteQr(
        val inviteCode: String,
        val chatId: String? = null
    ) : ParsedDlQrPayload

    data class UserProfileQr(
        val username: String
    ) : ParsedDlQrPayload

    data class Unknown(val rawText: String) : ParsedDlQrPayload
}

object QrCodeUtils {
    private val secureRandom = SecureRandom()

    fun generateSecureToken(byteCount: Int = 24): String {
        val bytes = ByteArray(byteCount).also { secureRandom.nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun sha256Hex(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun buildPairingUri(
        ownerUid: String,
        sessionId: String,
        rawToken: String,
        nonce: String,
        expiresAtEpochMs: Long
    ): String {
        return Uri.Builder()
            .scheme("dlchat")
            .authority("pair")
            .appendQueryParameter("uid", ownerUid)
            .appendQueryParameter("sid", sessionId)
            .appendQueryParameter("token", rawToken)
            .appendQueryParameter("nonce", nonce)
            .appendQueryParameter("exp", expiresAtEpochMs.toString())
            .build()
            .toString()
    }

    fun buildInviteUri(inviteCode: String, chatId: String? = null): String {
        val builder = Uri.Builder()
            .scheme("dlchat")
            .authority("join")
            .appendQueryParameter("code", inviteCode.trim())
        if (!chatId.isNullOrBlank()) {
            builder.appendQueryParameter("cid", chatId.trim())
        }
        return builder.build().toString()
    }

    fun buildProfileUri(username: String): String =
        "dlchat://u/${Uri.encode(username.trim())}"

    fun parseQrContent(rawText: String): ParsedDlQrPayload {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("dlchat://")) {
            return ParsedDlQrPayload.Unknown(trimmed)
        }
        return try {
            val uri = Uri.parse(trimmed)
            when (uri.host?.lowercase()) {
                "pair" -> {
                    val uid = uri.getQueryParameter("uid").orEmpty()
                    val sid = uri.getQueryParameter("sid").orEmpty()
                    val token = uri.getQueryParameter("token").orEmpty()
                    val nonce = uri.getQueryParameter("nonce").orEmpty()
                    val exp = uri.getQueryParameter("exp")?.toLongOrNull() ?: 0L
                    if (uid.isNotBlank() && sid.isNotBlank() && token.isNotBlank() && nonce.isNotBlank() && exp > 0L) {
                        ParsedDlQrPayload.PairingQr(uid, sid, token, nonce, exp)
                    } else {
                        ParsedDlQrPayload.Unknown(trimmed)
                    }
                }
                "join" -> {
                    val code = uri.getQueryParameter("code").orEmpty().trim()
                    val cid = uri.getQueryParameter("cid")?.trim()?.takeIf { it.isNotEmpty() }
                    if (code.isNotBlank()) ParsedDlQrPayload.GroupInviteQr(code, cid)
                    else ParsedDlQrPayload.Unknown(trimmed)
                }
                "u" -> {
                    val username = uri.lastPathSegment.orEmpty().trim()
                    if (username.isNotBlank()) ParsedDlQrPayload.UserProfileQr(username)
                    else ParsedDlQrPayload.Unknown(trimmed)
                }
                else -> ParsedDlQrPayload.Unknown(trimmed)
            }
        } catch (_: Exception) {
            ParsedDlQrPayload.Unknown(trimmed)
        }
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        if (content.isBlank() || sizePx <= 0) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }
}
