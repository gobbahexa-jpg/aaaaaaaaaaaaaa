package com.example.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class DeathLegionOAuthConfig(
    val clientId: String = DeathLegionOAuthClient.DEFAULT_CLIENT_ID,
    val tokenExchangeProxyUrl: String = "",
    val redirectUri: String = DeathLegionOAuthClient.DEFAULT_REDIRECT_URI,
    val hasConfiguredClientSecret: Boolean = false
) {
    val canExchangeCode: Boolean
        get() = clientId.isNotBlank() && (tokenExchangeProxyUrl.isNotBlank() || hasConfiguredClientSecret)

    val isReadyForOAuthFlow: Boolean
        get() = clientId.isNotBlank()
}

data class DeathLegionUserInfo(
    val legionUserId: String,
    val username: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String,
    val verificationStatus: String,
    val memberNumber: String,
    val primaryUnitName: String,
    val primaryUnitSlug: String = "",
    val primaryUnitColor: String = "",
    val role: String = "",
    val currentRole: String = "",
    val status: String = "",
    val memberId: String = "",
    val joinedAt: String = "",
    val grantedScope: String = ""
)

data class DeathLegionTokenSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val scope: String
) {
    val isExpired: Boolean
        get() = expiresAtEpochMs > 0L && System.currentTimeMillis() >= (expiresAtEpochMs - 60_000L)
}

data class DeathLegionIntrospectionResult(
    val active: Boolean,
    val username: String = "",
    val scope: String = "",
    val expiresAtEpochSec: Long = 0L
)

data class DeathLegionCallbackPayload(
    val code: String?,
    val state: String?,
    val error: String?,
    val errorDescription: String?
)

object DeathLegionOAuthClient {
    const val BASE_URL = "https://deathlegion.vercel.app"
    const val AUTHORIZE_ENDPOINT = "$BASE_URL/api/oauth/authorize"
    const val TOKEN_ENDPOINT = "$BASE_URL/api/oauth/token"
    const val USERINFO_ENDPOINT = "$BASE_URL/api/oauth/userinfo"
    const val INTROSPECT_ENDPOINT = "$BASE_URL/api/oauth/introspect"
    const val REVOKE_ENDPOINT = "$BASE_URL/api/oauth/revoke"

    // Public OAuth App Client ID for DL CHAT (registered for dlchat://oauth/deathlegion/callback)
    const val DEFAULT_CLIENT_ID = "f182c16d374a31f263e025557fe78f93"
    const val DEFAULT_REDIRECT_URI = "dlchat://oauth/deathlegion/callback"
    const val DEFAULT_SCOPES = "profile email identity unit"

    private const val PREFS_NAME = "dl_legion_oauth_state"
    private const val KEY_CLIENT_ID = "dl_oauth_client_id"
    private const val KEY_PROXY_URL = "dl_oauth_proxy_url"
    private const val KEY_PENDING_STATE = "dl_oauth_pending_state"
    private const val KEY_PENDING_CODE = "dl_oauth_pending_code"
    private const val KEY_ACCESS_TOKEN = "dl_oauth_access_token"
    private const val KEY_REFRESH_TOKEN = "dl_oauth_refresh_token"
    private const val KEY_EXPIRES_AT_MS = "dl_oauth_expires_at_ms"
    private const val KEY_TOKEN_SCOPE = "dl_oauth_token_scope"
    private const val KEY_USERINFO_CACHE = "dl_oauth_userinfo_json"

    private val random = SecureRandom()
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun configuredBuildSecret(): String {
        val raw = runCatching { BuildConfig.DEATH_LEGION_CLIENT_SECRET }.getOrDefault("").trim()
        return if (
            raw.isNotBlank() &&
            !raw.endsWith("_DEFAULT_VALUE") &&
            !raw.startsWith("YOUR_")
        ) raw else ""
    }

    private fun configuredBuildProxyUrl(): String {
        val raw = runCatching { BuildConfig.DEATH_LEGION_TOKEN_PROXY_URL }.getOrDefault("").trim()
        return if (raw.startsWith("https://") || raw.startsWith("http://")) raw else ""
    }

    fun getConfig(context: Context): DeathLegionOAuthConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedClientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty().trim()
        val savedProxyUrl = prefs.getString(KEY_PROXY_URL, "").orEmpty().trim()
        val resolvedProxy = savedProxyUrl.ifBlank { configuredBuildProxyUrl() }
        return DeathLegionOAuthConfig(
            clientId = savedClientId.ifBlank { DEFAULT_CLIENT_ID },
            tokenExchangeProxyUrl = resolvedProxy,
            redirectUri = DEFAULT_REDIRECT_URI,
            hasConfiguredClientSecret = configuredBuildSecret().isNotBlank()
        )
    }

    fun saveConfig(context: Context, clientId: String, tokenExchangeProxyUrl: String) {
        val cleanClientId = clientId.trim().ifBlank { DEFAULT_CLIENT_ID }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CLIENT_ID, cleanClientId)
            .putString(KEY_PROXY_URL, tokenExchangeProxyUrl.trim())
            .apply()
    }

    fun generateAndStoreState(context: Context): String {
        val bytes = ByteArray(24).also { random.nextBytes(it) }
        val state = bytes.joinToString("") { "%02x".format(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PENDING_STATE, state)
            .apply()
        return state
    }

    fun validateAndConsumeState(context: Context, returnedState: String?): Boolean {
        if (returnedState.isNullOrBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expected = prefs.getString(KEY_PENDING_STATE, null) ?: return false
        prefs.edit().remove(KEY_PENDING_STATE).apply()
        return expected == returnedState
    }

    fun getPendingAuthCode(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_CODE, null)
            ?.takeIf { it.isNotBlank() }

    fun clearPendingAuthCode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_PENDING_CODE)
            .apply()
    }

    fun getTokenSession(context: Context): DeathLegionTokenSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val access = prefs.getString(KEY_ACCESS_TOKEN, null)?.trim().orEmpty()
        if (access.isBlank()) return null
        return DeathLegionTokenSession(
            accessToken = access,
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
            expiresAtEpochMs = prefs.getLong(KEY_EXPIRES_AT_MS, 0L),
            scope = prefs.getString(KEY_TOKEN_SCOPE, "").orEmpty()
        )
    }

    private fun saveTokenSession(
        context: Context,
        accessToken: String,
        refreshToken: String,
        expiresInSec: Long,
        scope: String
    ) {
        val expiresAt = if (expiresInSec > 0L) {
            System.currentTimeMillis() + expiresInSec * 1000L
        } else 0L
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken.trim())
            .putString(KEY_REFRESH_TOKEN, refreshToken.trim())
            .putLong(KEY_EXPIRES_AT_MS, expiresAt)
            .putString(KEY_TOKEN_SCOPE, scope.trim())
            .apply()
    }

    private fun clearTokenSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT_MS)
            .remove(KEY_TOKEN_SCOPE)
            .remove(KEY_PENDING_CODE)
            .apply()
    }

    fun buildAuthorizeUrl(
        clientId: String = DEFAULT_CLIENT_ID,
        state: String,
        redirectUri: String = DEFAULT_REDIRECT_URI,
        scope: String = DEFAULT_SCOPES
    ): String {
        val resolvedClientId = clientId.trim().ifBlank { DEFAULT_CLIENT_ID }
        return Uri.parse(AUTHORIZE_ENDPOINT).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", resolvedClientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", scope)
            .appendQueryParameter("state", state)
            .build()
            .toString()
    }

    fun launchAuthorizeInBrowser(context: Context): Result<String> {
        val config = getConfig(context)
        if (!config.isReadyForOAuthFlow) {
            return Result.failure(
                IllegalStateException("Death Legion OAuth Client ID is required.")
            )
        }
        val state = generateAndStoreState(context)
        val url = buildAuthorizeUrl(config.clientId, state, config.redirectUri)
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseCallbackUri(uri: Uri?): DeathLegionCallbackPayload? {
        if (uri == null) return null
        if (uri.scheme != "dlchat" || uri.host != "oauth") return null
        val path = uri.path.orEmpty()
        if (path.isNotBlank() && !path.startsWith("/deathlegion/callback")) return null
        return DeathLegionCallbackPayload(
            code = uri.getQueryParameter("code"),
            state = uri.getQueryParameter("state"),
            error = uri.getQueryParameter("error"),
            errorDescription = uri.getQueryParameter("error_description")
        )
    }

    suspend fun exchangeCodeViaBackendProxy(
        context: Context,
        code: String,
        returnedState: String?
    ): Result<DeathLegionUserInfo> = exchangeCodeForSession(context, code, returnedState, validateState = true)

    suspend fun completePendingAuthCode(context: Context): Result<DeathLegionUserInfo> {
        val pendingCode = getPendingAuthCode(context)
            ?: return Result.failure(IllegalStateException("No pending authorization code saved."))
        return exchangeCodeForSession(context, pendingCode, returnedState = null, validateState = false)
    }

    suspend fun exchangeCodeForSession(
        context: Context,
        code: String,
        returnedState: String?,
        validateState: Boolean = true
    ): Result<DeathLegionUserInfo> = withContext(Dispatchers.IO) {
        if (validateState && !validateAndConsumeState(context, returnedState)) {
            return@withContext Result.failure(SecurityException("Invalid or expired OAuth state parameter."))
        }
        val config = getConfig(context)
        val proxyUrl = config.tokenExchangeProxyUrl.trim()
        val clientSecret = configuredBuildSecret()

        if (proxyUrl.isBlank() && clientSecret.isBlank()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_PENDING_CODE, code)
                .apply()
            return@withContext Result.failure(
                IllegalStateException(
                    "Authorization code captured for ${config.redirectUri}. Add your Token-Exchange Proxy URL in Settings or configure DEATH_LEGION_CLIENT_SECRET in Secrets to finish linking."
                )
            )
        }

        val targetUrl = proxyUrl.ifBlank { TOKEN_ENDPOINT }
        return@withContext try {
            val payloadJson = JSONObject().apply {
                put("grant_type", "authorization_code")
                put("code", code)
                put("redirect_uri", config.redirectUri)
                put("client_id", config.clientId)
                if (proxyUrl.isBlank() && clientSecret.isNotBlank()) {
                    put("client_secret", clientSecret)
                }
            }
            val request = Request.Builder()
                .url(targetUrl)
                .post(payloadJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errDetail = runCatching {
                        val errJson = JSONObject(body)
                        errJson.optString("error_description").ifBlank { errJson.optString("error") }
                    }.getOrDefault("").ifBlank { body.take(180) }
                    return@use Result.failure(
                        IllegalStateException("OAuth token exchange HTTP ${response.code}: $errDetail")
                    )
                }
                clearPendingAuthCode(context)
                val json = JSONObject(body)
                val accessToken = json.optString("access_token")
                val refreshToken = json.optString("refresh_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                val scope = json.optString("scope", DEFAULT_SCOPES)
                if (accessToken.isNotBlank()) {
                    saveTokenSession(context, accessToken, refreshToken, expiresIn, scope)
                    fetchUserInfo(context, accessToken)
                } else if (json.has("id") || json.has("sub") || json.has("legionUserId")) {
                    val info = parseUserInfoJson(json)
                    cacheUserInfo(context, info)
                    Result.success(info)
                } else {
                    Result.failure(IllegalStateException("Token endpoint returned neither access_token nor userinfo."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAccessToken(context: Context): Result<DeathLegionUserInfo> = withContext(Dispatchers.IO) {
        val session = getTokenSession(context)
            ?: return@withContext Result.failure(IllegalStateException("No active Death Legion session found."))
        if (session.refreshToken.isBlank()) {
            // Fall back to refreshing user profile with existing access token if still valid
            return@withContext fetchUserInfo(context, session.accessToken)
        }
        val config = getConfig(context)
        val proxyUrl = config.tokenExchangeProxyUrl.trim()
        val clientSecret = configuredBuildSecret()
        if (proxyUrl.isBlank() && clientSecret.isBlank()) {
            return@withContext fetchUserInfo(context, session.accessToken)
        }
        val targetUrl = proxyUrl.ifBlank { TOKEN_ENDPOINT }
        return@withContext try {
            val payloadJson = JSONObject().apply {
                put("grant_type", "refresh_token")
                put("refresh_token", session.refreshToken)
                put("client_id", config.clientId)
                if (proxyUrl.isBlank() && clientSecret.isNotBlank()) {
                    put("client_secret", clientSecret)
                }
            }
            val request = Request.Builder()
                .url(targetUrl)
                .post(payloadJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use Result.failure(
                        IllegalStateException("Token refresh failed (${response.code}): ${body.take(160)}")
                    )
                }
                val json = JSONObject(body)
                val newAccess = json.optString("access_token")
                val newRefresh = json.optString("refresh_token").ifBlank { session.refreshToken }
                val expiresIn = json.optLong("expires_in", 3600L)
                val scope = json.optString("scope", session.scope)
                if (newAccess.isBlank()) {
                    return@use Result.failure(IllegalStateException("Refresh endpoint did not return an access token."))
                }
                saveTokenSession(context, newAccess, newRefresh, expiresIn, scope)
                fetchUserInfo(context, newAccess)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun introspectActiveToken(context: Context): Result<DeathLegionIntrospectionResult> =
        withContext(Dispatchers.IO) {
            val session = getTokenSession(context)
                ?: return@withContext Result.failure(IllegalStateException("No stored Death Legion access token."))
            val config = getConfig(context)
            val clientSecret = configuredBuildSecret()
            if (clientSecret.isBlank()) {
                // Without client_secret, validate directly against /api/oauth/userinfo
                val infoRes = fetchUserInfo(context, session.accessToken)
                return@withContext infoRes.map { info ->
                    DeathLegionIntrospectionResult(
                        active = true,
                        username = info.username,
                        scope = info.grantedScope.ifBlank { session.scope },
                        expiresAtEpochSec = session.expiresAtEpochMs / 1000L
                    )
                }
            }
            return@withContext try {
                val payload = JSONObject().apply {
                    put("token", session.accessToken)
                    put("client_id", config.clientId)
                    put("client_secret", clientSecret)
                }
                val request = Request.Builder()
                    .url(INTROSPECT_ENDPOINT)
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use Result.failure(
                            IllegalStateException("Introspect HTTP ${response.code}: ${body.take(140)}")
                        )
                    }
                    val json = JSONObject(body)
                    Result.success(
                        DeathLegionIntrospectionResult(
                            active = json.optBoolean("active", false),
                            username = json.optString("username"),
                            scope = json.optString("scope"),
                            expiresAtEpochSec = json.optLong("exp", 0L)
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun revokeSession(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        val session = getTokenSession(context)
        val config = getConfig(context)
        val clientSecret = configuredBuildSecret()
        try {
            if (session != null && clientSecret.isNotBlank()) {
                val payload = JSONObject().apply {
                    put("token", session.refreshToken.ifBlank { session.accessToken })
                    put("client_id", config.clientId)
                    put("client_secret", clientSecret)
                }
                val request = Request.Builder()
                    .url(REVOKE_ENDPOINT)
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(request).execute().close()
            }
        } catch (_: Exception) {
            // Still clear local session even if network revoke cannot reach endpoint
        } finally {
            clearTokenSession(context)
            clearCachedUserInfo(context)
        }
        Result.success(Unit)
    }

    suspend fun fetchUserInfo(context: Context, accessToken: String): Result<DeathLegionUserInfo> =
        withContext(Dispatchers.IO) {
            if (accessToken.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Access token is required."))
            }
            return@withContext try {
                val request = Request.Builder()
                    .url(USERINFO_ENDPOINT)
                    .get()
                    .header("Authorization", "Bearer ${accessToken.trim()}")
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use Result.failure(
                            IllegalStateException("Death Legion UserInfo HTTP ${response.code}: ${body.take(160)}")
                        )
                    }
                    val info = parseUserInfoJson(JSONObject(body))
                    val existingSession = getTokenSession(context)
                    if (existingSession == null) {
                        saveTokenSession(
                            context = context,
                            accessToken = accessToken.trim(),
                            refreshToken = "",
                            expiresInSec = 3600L,
                            scope = info.grantedScope.ifBlank { DEFAULT_SCOPES }
                        )
                    }
                    cacheUserInfo(context, info)
                    Result.success(info)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun getCachedUserInfo(context: Context): DeathLegionUserInfo? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USERINFO_CACHE, null) ?: return null
        return try {
            parseUserInfoJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    fun clearCachedUserInfo(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_USERINFO_CACHE)
            .apply()
    }

    private fun cacheUserInfo(context: Context, info: DeathLegionUserInfo) {
        val obj = JSONObject().apply {
            put("id", info.legionUserId)
            put("username", info.username)
            put("name", info.displayName)
            put("email", info.email)
            put("picture", info.avatarUrl)
            put("verificationStatus", info.verificationStatus)
            put("memberNumber", info.memberNumber)
            put("memberId", info.memberId)
            put("role", info.role)
            put("currentRole", info.currentRole)
            put("status", info.status)
            put("joinedAt", info.joinedAt)
            put("scope", info.grantedScope)
            put(
                "primaryUnit",
                JSONObject().apply {
                    put("name", info.primaryUnitName)
                    put("slug", info.primaryUnitSlug)
                    put("color", info.primaryUnitColor)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_USERINFO_CACHE, obj.toString())
            .apply()
    }

    fun parseUserInfoJson(json: JSONObject): DeathLegionUserInfo {
        val unitObj = json.optJSONObject("primaryUnit") ?: json.optJSONObject("primary_unit")
        val id = json.optString("id")
            .ifBlank { json.optString("sub") }
            .ifBlank { json.optString("legionUserId") }
        val username = json.optString("username")
            .ifBlank { json.optString("preferred_username") }
        val displayName = json.optString("name")
            .ifBlank { json.optString("displayName") }
            .ifBlank { username }
        val verificationStatus = json.optString("verificationStatus")
            .ifBlank { json.optString("verification_status", "UNVERIFIED") }
        val memberNumber = json.optString("memberNumber")
            .ifBlank { json.optString("member_number") }
        val primaryUnitName = unitObj?.optString("name").orEmpty()
            .ifBlank { json.optString("primaryUnitName") }
        return DeathLegionUserInfo(
            legionUserId = id,
            username = username,
            displayName = displayName,
            email = json.optString("email"),
            avatarUrl = json.optString("picture"),
            verificationStatus = verificationStatus,
            memberNumber = memberNumber,
            primaryUnitName = primaryUnitName,
            primaryUnitSlug = unitObj?.optString("slug").orEmpty(),
            primaryUnitColor = unitObj?.optString("color").orEmpty(),
            role = json.optString("role"),
            currentRole = json.optString("currentRole"),
            status = json.optString("status"),
            memberId = json.optString("memberId"),
            joinedAt = json.optString("joinedAt"),
            grantedScope = json.optString("scope")
        )
    }
}
