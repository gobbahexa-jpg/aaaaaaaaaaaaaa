package com.example.core

import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface BackendGatewayResult<out T> {
    data class Success<T>(val data: T) : BackendGatewayResult<T>
    data class NotConfigured(val reason: String) : BackendGatewayResult<Nothing>
    data class Failure(val code: Int, val message: String) : BackendGatewayResult<Nothing>
}

data class WhatsAppHealthSnapshot(
    val configured: Boolean,
    val enabled: Boolean,
    val webhookConfigured: Boolean,
    val otpProviderEnabled: Boolean,
    val otpTemplateApproved: Boolean,
    val displayPhoneNumber: String,
    val graphApiVersion: String,
    val missingVariables: List<String>
)

object BackendGatewayClient {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun configuredBaseUrl(): String? {
        val raw = runCatching { BuildConfig.DL_BACKEND_BASE_URL }.getOrDefault("").trim().trimEnd('/')
        if (raw.isBlank() || raw.contains("DEFAULT_VALUE") || !raw.startsWith("https://") && !raw.startsWith("http://127.0.0.1") && !raw.startsWith("http://10.0.2.2")) {
            return null
        }
        return raw
    }

    fun isConfigured(): Boolean = configuredBaseUrl() != null

    private suspend fun authToken(): String? = runCatching {
        Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
    }.getOrNull()

    suspend fun getWhatsAppHealth(): BackendGatewayResult<WhatsAppHealthSnapshot> {
        val baseUrl = configuredBaseUrl()
            ?: return BackendGatewayResult.NotConfigured(
                "Cloud Functions backend URL (DL_BACKEND_BASE_URL) is not configured yet."
            )
        val token = authToken()
            ?: return BackendGatewayResult.Failure(401, "Sign in with an Admin account to check WhatsApp Business health.")

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/whatsapp/health")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                    if (!resp.isSuccessful) {
                        return@withContext BackendGatewayResult.Failure(
                            resp.code,
                            json.optString("error", "WhatsApp health check failed (${resp.code})")
                        )
                    }
                    val missingArr = json.optJSONArray("missingVariables")
                    val missing = buildList {
                        if (missingArr != null) {
                            for (i in 0 until missingArr.length()) {
                                add(missingArr.optString(i))
                            }
                        }
                    }
                    BackendGatewayResult.Success(
                        WhatsAppHealthSnapshot(
                            configured = json.optBoolean("configured", false),
                            enabled = json.optBoolean("enabled", false),
                            webhookConfigured = json.optBoolean("webhookConfigured", false),
                            otpProviderEnabled = json.optBoolean("otpProviderEnabled", false),
                            otpTemplateApproved = json.optBoolean("otpTemplateApproved", false),
                            displayPhoneNumber = json.optString("displayPhoneNumber", ""),
                            graphApiVersion = json.optString("graphApiVersion", "v21.0"),
                            missingVariables = missing
                        )
                    )
                }
            } catch (e: Exception) {
                BackendGatewayResult.Failure(0, e.localizedMessage ?: "Network error contacting backend.")
            }
        }
    }

    suspend fun sendWhatsAppSupportNotification(
        ticketId: String,
        toPhoneE164: String,
        textBody: String,
        templateName: String? = null
    ): BackendGatewayResult<String> {
        val payload = JSONObject().apply {
            put("ticketId", ticketId)
            put("toPhoneE164", toPhoneE164)
            put("textBody", textBody)
            if (!templateName.isNullOrBlank()) put("templateName", templateName)
        }
        return postAuthenticated("/whatsapp/send-support", payload) { json ->
            json.optString("messageId", "dispatched")
        }
    }

    suspend fun dispatchWhatsAppOtp(
        toPhoneE164: String,
        otpCode: String
    ): BackendGatewayResult<String> {
        val payload = JSONObject().apply {
            put("toPhoneE164", toPhoneE164)
            put("isOtp", true)
            put("templateParams", org.json.JSONArray().put(otpCode))
        }
        return postAuthenticated("/whatsapp/send-support", payload) { json ->
            json.optString("messageId", "dispatched")
        }
    }

    private suspend fun <T> postAuthenticated(
        path: String,
        payload: JSONObject,
        parser: (JSONObject) -> T
    ): BackendGatewayResult<T> {
        val baseUrl = configuredBaseUrl()
            ?: return BackendGatewayResult.NotConfigured(
                "Cloud Functions backend URL (DL_BACKEND_BASE_URL) is not configured yet."
            )
        val token = authToken()
            ?: return BackendGatewayResult.Failure(401, "Authentication token unavailable.")

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl$path")
                    .header("Authorization", "Bearer $token")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
                httpClient.newCall(request).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                    if (!resp.isSuccessful) {
                        return@withContext BackendGatewayResult.Failure(
                            resp.code,
                            json.optString("error", json.optString("reason", "Backend error (${resp.code})"))
                        )
                    }
                    BackendGatewayResult.Success(parser(json))
                }
            } catch (e: Exception) {
                BackendGatewayResult.Failure(0, e.localizedMessage ?: "Network error contacting backend.")
            }
        }
    }
}
