package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageUtils {
    suspend fun compressUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 720,
        quality: Int = 72
    ): String? = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val original = input.use { BitmapFactory.decodeStream(it) } ?: return@withContext null

            val ratio = minOf(
                maxDimension.toFloat() / original.width.coerceAtLeast(1),
                maxDimension.toFloat() / original.height.coerceAtLeast(1),
                1f
            )
            val targetW = (original.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (original.height * ratio).toInt().coerceAtLeast(1)
            val scaled = if (ratio < 1f) {
                Bitmap.createScaledBitmap(original, targetW, targetH, true)
            } else {
                original
            }

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            var bytes = out.toByteArray()
            if (bytes.size > 160_000) {
                out.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, 50, out)
                bytes = out.toByteArray()
            }
            if (scaled != original) scaled.recycle()
            original.recycle()

            if (bytes.size > 220_000) return@withContext null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64Bitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
