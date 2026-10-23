package com.callersphere.app

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

data class SupabaseActionResult(
    val ok: Boolean,
    val message: String
)

/**
 * Minimal Supabase Edge Function client using Android platform APIs.
 * The mobile app only receives the public anon/publishable client key.
 * Service-role keys must stay inside the Supabase Edge Function.
 */
object SupabaseCallerApi {
    private const val PREFS = "callersphere_supabase_session"
    private const val ACCESS_TOKEN = "anonymous_access_token"
    private const val REFRESH_TOKEN = "anonymous_refresh_token"
    private const val EXPIRES_AT = "anonymous_token_expires_at"
    private val executor = Executors.newCachedThreadPool()

    fun isConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.startsWith("https://") &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun lookup(context: Context, rawNumber: String, callback: (CallerLookupResult) -> Unit) {
        executor.execute {
            val result = try {
                val response = postToFunction(context, "lookup", rawNumber, null)
                val found = response.optBoolean("found", false)
                val record = response.optJSONObject("record")
                if (found && record != null) {
                    CallerLookupResult(
                        found = true,
                        label = record.optString("label", "Possible caller"),
                        category = record.optString("category", "unknown"),
                        message = "",
                        configured = true
                    )
                } else {
                    CallerLookupResult(
                        found = false,
                        message = response.optString(
                            "message",
                            "No public caller information was found for this number."
                        ),
                        configured = true
                    )
                }
            } catch (e: Exception) {
                CallerLookupResult(
                    found = false,
                    message = e.message ?: "Caller lookup failed. Check your connection and Supabase setup.",
                    configured = isConfigured()
                )
            }
            callback(result)
        }
    }

    fun reportSpam(context: Context, rawNumber: String, callback: (SupabaseActionResult) -> Unit) {
        executor.execute {
            val result = try {
                val response = postToFunction(context, "report_spam", rawNumber, "spam")
                if (response.optBoolean("ok", false)) {
                    SupabaseActionResult(true, "Spam report sent for review. It will not be treated as a verified identity.")
                } else {
                    SupabaseActionResult(false, response.optString("message", "Report could not be submitted."))
                }
            } catch (e: Exception) {
                SupabaseActionResult(
                    false,
                    e.message ?: "Could not submit report. Check your internet connection and Supabase setup."
                )
            }
            callback(result)
        }
    }

    private fun postToFunction(
        context: Context,
        action: String,
        rawNumber: String,
        reason: String?
    ): JSONObject {
        if (!isConfigured()) {
            throw IllegalStateException(
                "Cloud lookup is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY as GitHub Actions secrets, then rebuild."
            )
        }
        val token = getOrCreateAnonymousToken(context)
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val conn = (URL(baseUrl + "/functions/v1/lookup-caller").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 9000
            conn.readTimeout = 12000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer " + token)

            val body = JSONObject()
                .put("action", action)
                .put("phone", rawNumber.trim())
            if (reason != null) body.put("reason", reason)
            conn.outputStream.use { out ->
                out.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            val json = try {
                JSONObject(responseText)
            } catch (_: Exception) {
                JSONObject().put("message", "Supabase returned an unreadable response (HTTP " + status + ").")
            }
            if (status !in 200..299) {
                throw IllegalStateException(json.optString("message", "Supabase request failed (HTTP " + status + ")."))
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun getOrCreateAnonymousToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(EXPIRES_AT, 0L)
        if (!existing.isNullOrBlank() && expiresAt > System.currentTimeMillis() + 60_000L) {
            return existing
        }

        val refresh = prefs.getString(REFRESH_TOKEN, null)
        if (!refresh.isNullOrBlank()) {
            val renewed = refreshAccessToken(context, refresh)
            if (!renewed.isNullOrBlank()) return renewed
        }
        return createAnonymousSession(context)
    }

    private fun refreshAccessToken(context: Context, refreshToken: String): String? {
        return try {
            val json = requestAuthJson(
                "/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", refreshToken)
            )
            persistSession(context, json)
            json.optString("access_token", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun createAnonymousSession(context: Context): String {
        val json = requestAuthJson("/auth/v1/signup", JSONObject())
        val token = json.optString("access_token", "")
        if (token.isBlank()) {
            throw IllegalStateException(
                "Supabase did not return a session token. Enable anonymous sign-ins in Supabase Auth settings."
            )
        }
        persistSession(context, json)
        return token
    }

    private fun persistSession(context: Context, json: JSONObject) {
        val token = json.optString("access_token", "")
        val refresh = json.optString("refresh_token", "")
        val expiresInSeconds = json.optLong("expires_in", 3600L).coerceAtLeast(60L)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(ACCESS_TOKEN, token)
            .putString(REFRESH_TOKEN, refresh)
            .putLong(EXPIRES_AT, System.currentTimeMillis() + expiresInSeconds * 1000L)
            .apply()
    }

    private fun requestAuthJson(path: String, body: JSONObject): JSONObject {
        val conn = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
            .openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 9000
            conn.readTimeout = 12000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            // The public client key belongs in the apikey header. A publishable key
            // is not a user JWT and must not be used as the Bearer token.
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.outputStream.use { out ->
                out.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException(
                    "Supabase Auth failed (HTTP " + status +
                        "). Enable anonymous sign-ins and check the public client key."
                )
            }
            return JSONObject(responseText)
        } finally {
            conn.disconnect()
        }
    }
}
