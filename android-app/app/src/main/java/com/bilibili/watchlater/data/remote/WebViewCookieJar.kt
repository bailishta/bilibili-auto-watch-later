package com.bilibili.watchlater.data.remote

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebViewCookieJar : CookieJar {

    private val cookieMgr = CookieManager.getInstance()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieStr = cookieMgr.getCookie(url.toString()) ?: return emptyList()
        return cookieStr.split("; ")
            .filter { it.isNotEmpty() }
            .mapNotNull { Cookie.parse(url, it) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            cookieMgr.setCookie(url.toString(), cookie.toString())
        }
        CookieManager.getInstance().flush()
    }
}
