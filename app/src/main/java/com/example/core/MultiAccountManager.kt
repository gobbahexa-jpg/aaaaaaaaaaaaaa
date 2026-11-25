package com.example.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LocalAccountSlot(
    val uid: String,
    val email: String,
    val displayName: String,
    val username: String,
    val lastUsedEpochMs: Long
)

object MultiAccountManager {
    private const val PREFS_NAME = "dl_multi_accounts_registry"
    private const val KEY_ACCOUNTS_JSON = "accounts_json"

    fun listAccounts(context: Context): List<LocalAccountSlot> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNTS_JSON, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val uid = obj.optString("uid")
                if (uid.isBlank()) return@mapNotNull null
                LocalAccountSlot(
                    uid = uid,
                    email = obj.optString("email"),
                    displayName = obj.optString("displayName"),
                    username = obj.optString("username"),
                    lastUsedEpochMs = obj.optLong("lastUsedEpochMs", 0L)
                )
            }.sortedByDescending { it.lastUsedEpochMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun recordAccount(
        context: Context,
        uid: String,
        email: String,
        displayName: String,
        username: String
    ): List<LocalAccountSlot> {
        if (uid.isBlank()) return listAccounts(context)
        val current = listAccounts(context).filterNot { it.uid == uid }.toMutableList()
        current.add(
            0,
            LocalAccountSlot(
                uid = uid,
                email = email,
                displayName = displayName.ifBlank { "Operator" },
                username = username.ifBlank { "user_${uid.take(6)}" },
                lastUsedEpochMs = System.currentTimeMillis()
            )
        )
        saveAccounts(context, current)
        return current
    }

    fun removeAccount(context: Context, uid: String): List<LocalAccountSlot> {
        val updated = listAccounts(context).filterNot { it.uid == uid }
        saveAccounts(context, updated)
        purgeIsolatedAccountLocalState(context, uid)
        return updated
    }

    fun purgeIsolatedAccountLocalState(context: Context, uid: String) {
        // Strictly remove only keys scoped to `uid`, never touching other accounts
        SecurityPrivacyManager.clearPin(context, uid)
        OfflineSyncEngine.clearQueue(context, uid)
        val cryptoPrefs = context.getSharedPreferences("dl_chat_crypto_identity", Context.MODE_PRIVATE)
        cryptoPrefs.edit()
            .remove("identity_fingerprint_$uid")
            .remove("identity_pub_b64_$uid")
            .remove("identity_priv_b64_$uid")
            .remove("identity_key_version_$uid")
            .apply()
    }

    private fun saveAccounts(context: Context, slots: List<LocalAccountSlot>) {
        val arr = JSONArray()
        slots.forEach { slot ->
            arr.put(
                JSONObject().apply {
                    put("uid", slot.uid)
                    put("email", slot.email)
                    put("displayName", slot.displayName)
                    put("username", slot.username)
                    put("lastUsedEpochMs", slot.lastUsedEpochMs)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACCOUNTS_JSON, arr.toString())
            .apply()
    }
}
