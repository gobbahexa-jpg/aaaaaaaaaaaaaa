package com.example.core

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale

data class PickedFileMetadata(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class StorageBreakdown(
    val voiceCacheBytes: Long = 0L,
    val imageCacheBytes: Long = 0L,
    val backupBytes: Long = 0L,
    val otherCacheBytes: Long = 0L
) {
    val totalBytes: Long get() = voiceCacheBytes + imageCacheBytes + backupBytes + otherCacheBytes
}

data class AppApkInfo(
    val versionName: String,
    val versionCode: Long,
    val apkSizeBytes: Long?,
    val sourcePath: String?,
    val suggestedFileName: String
)

object LocalStorageManager {
    fun readUriMetadata(context: Context, uri: Uri): PickedFileMetadata {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var name = "attachment_${System.currentTimeMillis()}"
        var size = 0L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                    }
                }
        } catch (_: Exception) {
        }
        return PickedFileMetadata(
            displayName = name.take(160),
            mimeType = mime.take(80),
            sizeBytes = size.coerceAtLeast(0L)
        )
    }

    fun inspectStorage(context: Context): StorageBreakdown {
        val voiceBytes = dirSize(File(context.cacheDir, "dl_voice_notes")) +
            dirSize(File(context.cacheDir, "dl_voice_playback"))
        val imageBytes = dirSize(File(context.cacheDir, "image_manager_disk_cache")) +
            dirSize(File(context.cacheDir, "coil_cache"))
        val backupBytes = dirSize(File(context.filesDir, "dl_backups"))
        val totalCache = dirSize(context.cacheDir)
        val otherBytes = (totalCache - voiceBytes - imageBytes).coerceAtLeast(0L)
        return StorageBreakdown(
            voiceCacheBytes = voiceBytes,
            imageCacheBytes = imageBytes,
            backupBytes = backupBytes,
            otherCacheBytes = otherBytes
        )
    }

    fun clearLocalCache(context: Context): StorageBreakdown {
        deleteChildren(File(context.cacheDir, "dl_voice_notes"))
        deleteChildren(File(context.cacheDir, "dl_voice_playback"))
        deleteChildren(File(context.cacheDir, "coil_cache"))
        deleteChildren(context.cacheDir, keepNames = setOf("dl_voice_notes", "dl_voice_playback", "coil_cache"))
        return inspectStorage(context)
    }

    fun inspectInstalledApk(context: Context): AppApkInfo {
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
        val versionName = packageInfo?.versionName?.takeIf { it.isNotBlank() } ?: "1.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: 1L
        } else {
            @Suppress("DEPRECATION")
            (packageInfo?.versionCode?.toLong() ?: 1L)
        }
        val rawSourcePath = context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }
        val validApkFile = rawSourcePath?.let { File(it) }?.takeIf { it.exists() && it.isFile && it.canRead() }
        val safeVersion = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return AppApkInfo(
            versionName = versionName,
            versionCode = versionCode,
            apkSizeBytes = validApkFile?.length(),
            sourcePath = validApkFile?.absolutePath,
            suggestedFileName = "DL-Chat-v$safeVersion.apk"
        )
    }

    suspend fun exportInstalledApk(
        context: Context,
        destinationUri: Uri,
        sourcePathOverride: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val sourcePath = sourcePathOverride
                ?.takeIf { it.isNotBlank() }
                ?: context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }
                ?: error("APK source path unavailable")
            val sourceFile = File(sourcePath)
            require(sourceFile.exists() && sourceFile.isFile && sourceFile.canRead()) {
                "APK file cannot be read"
            }
            val output = context.contentResolver.openOutputStream(destinationUri)
                ?: error("Could not open destination file")
            output.use { out ->
                FileInputStream(sourceFile).use { input ->
                    val copied = input.copyTo(out)
                    out.flush()
                    copied
                }
            }
        }
    }

    fun formatByteCount(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }

    private fun deleteChildren(dir: File, keepNames: Set<String> = emptySet()) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            if (child.name !in keepNames) {
                child.deleteRecursively()
            }
        }
    }
}
