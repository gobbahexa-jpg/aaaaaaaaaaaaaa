package com.example.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SyncConflictWinner {
    LOCAL,
    REMOTE
}

data class PendingSyncMutation(
    val id: String = UUID.randomUUID().toString(),
    val entityType: String,
    val entityId: String,
    val operation: String,
    val versionEpochMs: Long,
    val payloadSummary: String
)

object OfflineSyncEngine {
    private fun prefsName(uid: String) = "dl_offline_sync_$uid"
    private const val KEY_QUEUE = "pending_mutations"

    fun resolveLwwConflict(localVersionEpochMs: Long, remoteVersionEpochMs: Long): SyncConflictWinner {
        return if (localVersionEpochMs >= remoteVersionEpochMs) {
            SyncConflictWinner.LOCAL
        } else {
            SyncConflictWinner.REMOTE
        }
    }

    fun getPendingMutations(context: Context, uid: String): List<PendingSyncMutation> {
        val raw = context.getSharedPreferences(prefsName(uid), Context.MODE_PRIVATE)
            .getString(KEY_QUEUE, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                PendingSyncMutation(
                    id = obj.optString("id"),
                    entityType = obj.optString("entityType"),
                    entityId = obj.optString("entityId"),
                    operation = obj.optString("operation"),
                    versionEpochMs = obj.optLong("versionEpochMs", 0L),
                    payloadSummary = obj.optString("payloadSummary")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun enqueueMutation(context: Context, uid: String, mutation: PendingSyncMutation): List<PendingSyncMutation> {
        // Coalesce older mutations for the exact same entityType + entityId using LWW
        val existing = getPendingMutations(context, uid).filterNot {
            it.entityType == mutation.entityType &&
                it.entityId == mutation.entityId &&
                resolveLwwConflict(mutation.versionEpochMs, it.versionEpochMs) == SyncConflictWinner.LOCAL
        }
        val updated = (existing + mutation).takeLast(100)
        saveQueue(context, uid, updated)
        return updated
    }

    fun markMutationSynced(context: Context, uid: String, mutationId: String): List<PendingSyncMutation> {
        val updated = getPendingMutations(context, uid).filterNot { it.id == mutationId }
        saveQueue(context, uid, updated)
        return updated
    }

    fun clearQueue(context: Context, uid: String) {
        context.getSharedPreferences(prefsName(uid), Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun saveQueue(context: Context, uid: String, items: List<PendingSyncMutation>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("entityType", item.entityType)
                    put("entityId", item.entityId)
                    put("operation", item.operation)
                    put("versionEpochMs", item.versionEpochMs)
                    put("payloadSummary", item.payloadSummary)
                }
            )
        }
        context.getSharedPreferences(prefsName(uid), Context.MODE_PRIVATE).edit()
            .putString(KEY_QUEUE, arr.toString())
            .apply()
    }
}
