package net.alextaran.sms2tg

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDateTime

data class TelegramData(val userId: String, val token: String, val enabled: Boolean) {
    fun isValid(): Boolean {
        if (userId.isEmpty() || token.isEmpty()) {
            return false
        }
        if (!TOKEN_REGEX.matches(token)) return false
        if (userId.toLongOrNull() == null) return false
        return true
    }

    fun getUserIdSafe(): String {
        if (userId.isEmpty()) return "(empty)"
        if (userId.toLongOrNull() == null) return "(error: not a number)"
        if (userId.length < 5) return "(error: too short)"
        return userId.replaceRange(userId.length - 5, userId.length, "*".repeat(5))
    }

    fun getTokenSafe(): String {
        if (token.isEmpty()) return "(empty)"
        if (!TOKEN_REGEX.matches(token)) return "(error: wrong format)"
        return "(OK)"
    }

    fun createSendMessageRequest(message: String): Request {
        val apiUrl = "https://api.telegram.org/bot${token}/sendMessage"
        val payload = JSONObject().apply {
            put("chat_id", userId)
            put("text", message)
            put("parse_mode", "MarkdownV2")
        }
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(apiUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()
        return request
    }

    companion object {
        private val TOKEN_REGEX = Regex("^\\d+:[A-Za-z0-9_-]+$")
    }
}

class TelegramDataAccessor(private val ctx: Context) {

    fun readTelegramData(): TelegramData {
        val prefs = getPrefs()
        val userId = prefs.getString(USERID_KEY, "") ?: ""
        val token = prefs.getString(TOKEN_KEY, "") ?: ""
        val enabled = prefs.getBoolean(ENABLED_KEY, false)
        return TelegramData(userId, token, enabled)
    }

    fun updateTelegramUserId(value: String) {
        val editor = getPrefs().edit()
        editor.putString(USERID_KEY, value)
        editor.apply()
    }

    fun updateTelegramToken(value: String) {
        val editor = getPrefs().edit()
        editor.putString(TOKEN_KEY, value)
        editor.apply()
    }

    fun updateEnabledFlag(value: Boolean) {
        val editor = getPrefs().edit()
        editor.putBoolean(ENABLED_KEY, value)
        editor.apply()
    }

    private fun getPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, FILENAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val FILENAME = "tg_data"
        private const val USERID_KEY = "user_id"
        private const val TOKEN_KEY = "token"
        private const val ENABLED_KEY = "enabled"
    }
}