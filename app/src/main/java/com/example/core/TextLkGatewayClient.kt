package com.example.core

import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface TextLkResult<out T> {
    data class Success<T>(val data: T) : TextLkResult<T>
    data class NotConfigured(val reason: String) : TextLkResult<Nothing>
    data class Failure(val code: Int, val message: String) : TextLkResult<Nothing>
}

data class TextLkContact(
    val uid: String = "",
    val groupId: String = "",
    val phone: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val extraFields: Map<String, String> = emptyMap()
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { phone }
}

data class TextLkContactsPage(
    val contacts: List<TextLkContact>,
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = contacts.size
)

data class TextLkSmsResult(
    val status: String,
    val messageId: String,
    val recipient: String,
    val message: String,
    val endpointMode: String = "API_V3"
)

object TextLkGatewayClient {
    const val V3_BASE_URL = "https://app.text.lk/api/v3"
    const val HTTP_BASE_URL = "https://app.text.lk/api/http"

    private val UID_REGEX = Regex("^[A-Za-z0-9_-]{3,64}$")
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun configuredApiToken(): String? {
        val raw = runCatching { BuildConfig.TEXT_LK_API_TOKEN }.getOrDefault("").trim()
        if (raw.isBlank() || raw.contains("DEFAULT_VALUE")) return null
        return raw
    }

    fun defaultSenderId(): String {
        val raw = runCatching { BuildConfig.TEXT_LK_SENDER_ID }.getOrDefault("").trim()
        return if (raw.isBlank() || raw.contains("DEFAULT_VALUE")) "DLChat" else raw.take(11)
    }

    fun defaultGroupId(): String {
        val raw = runCatching { BuildConfig.TEXT_LK_DEFAULT_GROUP_ID }.getOrDefault("").trim()
        return if (raw.isBlank() || raw.contains("DEFAULT_VALUE")) "" else raw
    }

    fun isDirectConfigured(): Boolean = configuredApiToken() != null

    fun isConfigured(): Boolean = isDirectConfigured() || BackendGatewayClient.isConfigured()

    fun isValidUid(id: String): Boolean = UID_REGEX.matches(id.trim())

    /**
     * Converts inputs like "+94 77 123 4567", "0771234567", "0094771234567", or "94771234567"
     * into Text.lk's expected digit-only international format (e.g. "94771234567").
     */
    fun toTextLkPhone(raw: String, defaultCountryDial: String = "94"): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val digitsOnly = buildString {
            trimmed.forEach { ch -> if (ch.isDigit()) append(ch) }
        }
        val normalized = when {
            trimmed.startsWith("+") -> digitsOnly
            trimmed.startsWith("00") -> digitsOnly.drop(2)
            trimmed.startsWith("0") && digitsOnly.length in 9..11 ->
                defaultCountryDial.filter(Char::isDigit) + digitsOnly.drop(1)
            digitsOnly.length == 9 ->
                defaultCountryDial.filter(Char::isDigit) + digitsOnly
            else -> digitsOnly
        }
        if (normalized.length !in 9..15 || !normalized.all(Char::isDigit)) return null
        return normalized
    }

    private suspend fun firebaseIdToken(): String? = runCatching {
        Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
    }.getOrNull()

    suspend fun listGroupContacts(
        groupId: String,
        page: Int = 1
    ): TextLkResult<TextLkContactsPage> {
        val gid = groupId.trim()
        if (!isValidUid(gid)) {
            return TextLkResult.Failure(400, "Enter a valid Text.lk Group ID (3–64 letters, digits, _ or -).")
        }
        val safePage = page.coerceAtLeast(1)

        val token = configuredApiToken()
        if (token != null) {
            val url = "$V3_BASE_URL/contacts/$gid/all".toHttpUrl()
                .newBuilder()
                .addQueryParameter("page", safePage.toString())
                .build()
                .toString()
            return executeDirect(
                method = "POST",
                url = url,
                apiToken = token,
                payload = JSONObject()
            ) { json ->
                parseContactsPageJson(json, gid, safePage)
            }
        }

        return executeBackendProxy(
            method = "POST",
            path = "/textlk/contacts/list",
            payload = JSONObject().apply {
                put("groupId", gid)
                put("page", safePage)
            }
        ) { json ->
            parseContactsPageJson(json, gid, safePage)
        }
    }

    suspend fun viewContact(
        groupId: String,
        contactUid: String
    ): TextLkResult<TextLkContact> {
        val gid = groupId.trim()
        val uid = contactUid.trim()
        if (!isValidUid(gid) || !isValidUid(uid)) {
            return TextLkResult.Failure(400, "Valid Group ID and Contact UID are required.")
        }

        val token = configuredApiToken()
        if (token != null) {
            return executeDirect(
                method = "POST",
                url = "$V3_BASE_URL/contacts/$gid/search/$uid",
                apiToken = token,
                payload = JSONObject()
            ) { json ->
                val dataObj = json.optJSONObject("data") ?: json
                parseContactJson(dataObj, gid)
            }
        }

        return executeBackendProxy(
            method = "POST",
            path = "/textlk/contacts/view",
            payload = JSONObject().apply {
                put("groupId", gid)
                put("contactUid", uid)
            }
        ) { json ->
            val dataObj = json.optJSONObject("data") ?: json
            parseContactJson(dataObj, gid)
        }
    }

    suspend fun createContact(
        groupId: String,
        phoneRaw: String,
        firstName: String,
        lastName: String,
        extraFields: Map<String, String> = emptyMap()
    ): TextLkResult<TextLkContact> {
        val gid = groupId.trim()
        if (!isValidUid(gid)) {
            return TextLkResult.Failure(400, "Enter a valid Text.lk Group ID.")
        }
        val phone = toTextLkPhone(phoneRaw)
            ?: return TextLkResult.Failure(400, "Enter a valid phone number (for example 94771234567 or 0771234567).")

        val payload = buildContactPayload(phone, firstName, lastName, extraFields)
        val token = configuredApiToken()
        if (token != null) {
            return executeDirect(
                method = "POST",
                url = "$V3_BASE_URL/contacts/$gid/store",
                apiToken = token,
                payload = payload
            ) { json ->
                val dataObj = json.optJSONObject("data") ?: json
                parseContactJson(dataObj, gid)
            }
        }

        return executeBackendProxy(
            method = "POST",
            path = "/textlk/contacts/store",
            payload = JSONObject().apply {
                put("groupId", gid)
                put("phone", phone)
                put("firstName", firstName.trim())
                put("lastName", lastName.trim())
                if (extraFields.isNotEmpty()) put("extraFields", JSONObject(extraFields))
            }
        ) { json ->
            val dataObj = json.optJSONObject("data") ?: json
            parseContactJson(dataObj, gid)
        }
    }

    suspend fun updateContact(
        groupId: String,
        contactUid: String,
        phoneRaw: String,
        firstName: String,
        lastName: String,
        extraFields: Map<String, String> = emptyMap()
    ): TextLkResult<TextLkContact> {
        val gid = groupId.trim()
        val uid = contactUid.trim()
        if (!isValidUid(gid) || !isValidUid(uid)) {
            return TextLkResult.Failure(400, "Valid Group ID and Contact UID are required.")
        }
        val phone = toTextLkPhone(phoneRaw)
            ?: return TextLkResult.Failure(400, "Enter a valid phone number.")

        val payload = buildContactPayload(phone, firstName, lastName, extraFields)
        val token = configuredApiToken()
        if (token != null) {
            return executeDirect(
                method = "PATCH",
                url = "$V3_BASE_URL/contacts/$gid/update/$uid",
                apiToken = token,
                payload = payload
            ) { json ->
                val dataObj = json.optJSONObject("data") ?: json
                parseContactJson(dataObj, gid)
            }
        }

        return executeBackendProxy(
            method = "PATCH",
            path = "/textlk/contacts/update",
            payload = JSONObject().apply {
                put("groupId", gid)
                put("contactUid", uid)
                put("phone", phone)
                put("firstName", firstName.trim())
                put("lastName", lastName.trim())
                if (extraFields.isNotEmpty()) put("extraFields", JSONObject(extraFields))
            }
        ) { json ->
            val dataObj = json.optJSONObject("data") ?: json
            parseContactJson(dataObj, gid)
        }
    }

    suspend fun deleteContact(
        groupId: String,
        contactUid: String
    ): TextLkResult<String> {
        val gid = groupId.trim()
        val uid = contactUid.trim()
        if (!isValidUid(gid) || !isValidUid(uid)) {
            return TextLkResult.Failure(400, "Valid Group ID and Contact UID are required.")
        }

        val token = configuredApiToken()
        if (token != null) {
            return executeDirect(
                method = "DELETE",
                url = "$V3_BASE_URL/contacts/$gid/delete/$uid",
                apiToken = token,
                payload = null
            ) { json ->
                json.optString("message", "Contact deleted")
            }
        }

        return executeBackendProxy(
            method = "DELETE",
            path = "/textlk/contacts/delete",
            payload = JSONObject().apply {
                put("groupId", gid)
                put("contactUid", uid)
            }
        ) { json ->
            json.optString("message", "Contact deleted")
        }
    }

    suspend fun sendSms(
        recipientRaw: String,
        message: String,
        senderId: String = defaultSenderId(),
        useHttpEndpoint: Boolean = false
    ): TextLkResult<TextLkSmsResult> {
        val recipient = toTextLkPhone(recipientRaw)
            ?: return TextLkResult.Failure(400, "Enter a valid recipient phone number.")
        val cleanMsg = message.trim()
        if (cleanMsg.isEmpty()) {
            return TextLkResult.Failure(400, "SMS message text cannot be empty.")
        }
        val cleanSender = senderId.trim().ifBlank { defaultSenderId() }.take(11)

        val token = configuredApiToken()
        if (token != null) {
            val endpoint = if (useHttpEndpoint) {
                "$HTTP_BASE_URL/sms/send"
            } else {
                "$V3_BASE_URL/sms/send"
            }
            val payload = JSONObject().apply {
                put("recipient", recipient)
                put("sender_id", cleanSender)
                put("type", "plain")
                put("message", cleanMsg)
            }
            return executeDirect(
                method = "POST",
                url = endpoint,
                apiToken = token,
                payload = payload
            ) { json ->
                val dataObj = json.optJSONObject("data") ?: JSONObject()
                TextLkSmsResult(
                    status = json.optString("status", "success"),
                    messageId = dataObj.optString("uid", dataObj.optString("id", "")),
                    recipient = recipient,
                    message = cleanMsg,
                    endpointMode = if (useHttpEndpoint) "HTTP" else "API_V3"
                )
            }
        }

        return executeBackendProxy(
            method = "POST",
            path = "/textlk/sms/send",
            payload = JSONObject().apply {
                put("recipient", recipient)
                put("senderId", cleanSender)
                put("message", cleanMsg)
                put("useHttpEndpoint", useHttpEndpoint)
            }
        ) { json ->
            val dataObj = json.optJSONObject("data") ?: JSONObject()
            TextLkSmsResult(
                status = json.optString("status", "success"),
                messageId = dataObj.optString("uid", json.optString("messageId", "")),
                recipient = recipient,
                message = cleanMsg,
                endpointMode = if (useHttpEndpoint) "HTTP" else "API_V3"
            )
        }
    }

    internal fun buildContactPayload(
        phone: String,
        firstName: String,
        lastName: String,
        extraFields: Map<String, String>
    ): JSONObject = JSONObject().apply {
        put("PHONE", phone)
        if (firstName.isNotBlank()) put("FIRST_NAME", firstName.trim())
        if (lastName.isNotBlank()) put("LAST_NAME", lastName.trim())
        extraFields.forEach { (k, v) ->
            val cleanKey = k.trim().uppercase()
            if (cleanKey.isNotEmpty() && cleanKey !in setOf("PHONE", "FIRST_NAME", "LAST_NAME") && v.isNotBlank()) {
                put(cleanKey, v.trim())
            }
        }
    }

    internal fun parseContactJson(obj: JSONObject, groupId: String): TextLkContact {
        val customObj = obj.optJSONObject("custom_fields") ?: obj
        val phone = customObj.optString("PHONE", obj.optString("phone", obj.optString("to", "")))
        val firstName = customObj.optString("FIRST_NAME", obj.optString("first_name", obj.optString("firstName", "")))
        val lastName = customObj.optString("LAST_NAME", obj.optString("last_name", obj.optString("lastName", "")))
        val reserved = setOf("uid", "id", "group_id", "PHONE", "phone", "to", "FIRST_NAME", "first_name", "firstName", "LAST_NAME", "last_name", "lastName", "custom_fields")
        val extras = mutableMapOf<String, String>()
        customObj.keys().forEach { key ->
            if (key !in reserved) {
                val value = customObj.optString(key, "")
                if (value.isNotBlank() && value != "null") {
                    extras[key] = value
                }
            }
        }
        return TextLkContact(
            uid = obj.optString("uid", obj.optString("id", "")),
            groupId = groupId,
            phone = phone,
            firstName = firstName,
            lastName = lastName,
            extraFields = extras
        )
    }

    internal fun parseContactsPageJson(
        root: JSONObject,
        groupId: String,
        fallbackPage: Int = 1
    ): TextLkContactsPage {
        val dataObj = root.optJSONObject("data")
        val directArray = root.optJSONArray("data")
        val itemsArray: JSONArray? = dataObj?.optJSONArray("data") ?: directArray
        val list = buildList {
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.optJSONObject(i) ?: continue
                    add(parseContactJson(item, groupId))
                }
            }
        }
        return TextLkContactsPage(
            contacts = list,
            currentPage = dataObj?.optInt("current_page", fallbackPage) ?: fallbackPage,
            lastPage = dataObj?.optInt("last_page", fallbackPage) ?: fallbackPage,
            total = dataObj?.optInt("total", list.size) ?: list.size
        )
    }

    internal fun parseErrorMessage(raw: String, code: Int): String {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return "Text.lk request failed ($code)"
        return json.optString("message")
            .ifBlank { json.optString("error") }
            .ifBlank { "Text.lk request failed ($code)" }
    }

    private suspend fun <T> executeDirect(
        method: String,
        url: String,
        apiToken: String,
        payload: JSONObject?,
        parser: (JSONObject) -> T
    ): TextLkResult<T> = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiToken")
                .header("Accept", "application/json")
            val body = payload?.toString()?.toRequestBody(jsonMedia)
            when (method) {
                "POST" -> reqBuilder.post(body ?: "{}".toRequestBody(jsonMedia))
                "PATCH" -> reqBuilder.patch(body ?: "{}".toRequestBody(jsonMedia))
                "DELETE" -> if (body != null) reqBuilder.delete(body) else reqBuilder.delete()
                else -> reqBuilder.get()
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                val logicalStatus = json.optString("status", "")
                if (!resp.isSuccessful || logicalStatus.equals("error", ignoreCase = true)) {
                    return@withContext TextLkResult.Failure(
                        resp.code,
                        parseErrorMessage(raw, resp.code)
                    )
                }
                TextLkResult.Success(parser(json))
            }
        } catch (e: Exception) {
            TextLkResult.Failure(0, e.localizedMessage ?: "Network error contacting Text.lk.")
        }
    }

    private suspend fun <T> executeBackendProxy(
        method: String,
        path: String,
        payload: JSONObject,
        parser: (JSONObject) -> T
    ): TextLkResult<T> {
        val baseUrl = BackendGatewayClient.configuredBaseUrl()
            ?: return TextLkResult.NotConfigured(
                "Text.lk API token is not set. Add TEXT_LK_API_TOKEN (or DL_BACKEND_BASE_URL) in Secrets and rebuild."
            )
        val token = firebaseIdToken()
            ?: return TextLkResult.Failure(401, "Sign in to your DL Chat account first.")

        return withContext(Dispatchers.IO) {
            try {
                val reqBuilder = Request.Builder()
                    .url("$baseUrl$path")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                val body = payload.toString().toRequestBody(jsonMedia)
                when (method) {
                    "PATCH" -> reqBuilder.patch(body)
                    "DELETE" -> reqBuilder.delete(body)
                    else -> reqBuilder.post(body)
                }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                    if (!resp.isSuccessful) {
                        return@withContext TextLkResult.Failure(
                            resp.code,
                            parseErrorMessage(raw, resp.code)
                        )
                    }
                    TextLkResult.Success(parser(json))
                }
            } catch (e: Exception) {
                TextLkResult.Failure(0, e.localizedMessage ?: "Network error contacting backend.")
            }
        }
    }
}
