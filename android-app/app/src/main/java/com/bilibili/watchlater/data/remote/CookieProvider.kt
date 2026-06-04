package com.bilibili.watchlater.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CookieProvider(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "cookies_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var sessData: String?
        get() = prefs.getString("SESSDATA", null)
        set(value) = prefs.edit().putString("SESSDATA", value).apply()

    var biliJct: String?
        get() = prefs.getString("bili_jct", null)
        set(value) = prefs.edit().putString("bili_jct", value).apply()

    var dedeUserId: String?
        get() = prefs.getString("DedeUserID", null)
        set(value) = prefs.edit().putString("DedeUserID", value).apply()

    var fullCookies: String?
        get() = prefs.getString("full_cookies", null)
        set(value) = prefs.edit().putString("full_cookies", value).apply()

    val isLoggedIn: Boolean
        get() = !sessData.isNullOrEmpty()

    fun saveAll(fullCookies: String?, jct: String?, userId: String?) {
        val sessdata = extractCookieValue(fullCookies, "SESSDATA")
        prefs.edit()
            .putString("full_cookies", fullCookies)
            .putString("SESSDATA", sessdata)
            .putString("bili_jct", jct)
            .putString("DedeUserID", userId)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun getCookieString(): String? {
        val s = sessData ?: return null
        val j = biliJct ?: ""
        val d = dedeUserId ?: ""
        val authCookies = "SESSDATA=$s; bili_jct=$j; DedeUserID=$d"
        // WebView 的完整 Cookie 串（含 buvid3/buvid4 等设备标识），
        // 但 CookieManager 可能不返回 HttpOnly Cookie（如 SESSDATA），
        // 所以必须把独立提取的认证 Cookie 放在前面确保生效
        if (!fullCookies.isNullOrEmpty()) return "$authCookies; $fullCookies"
        return authCookies
    }

    private fun extractCookieValue(cookies: String?, name: String): String? {
        if (cookies.isNullOrEmpty()) return null
        val pattern = Regex.escape(name) + "=([^;]+)"
        return Regex(pattern).find(cookies)?.groupValues?.getOrNull(1)
    }
}
