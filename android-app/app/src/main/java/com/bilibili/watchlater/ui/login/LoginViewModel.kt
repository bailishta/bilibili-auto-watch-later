package com.bilibili.watchlater.ui.login

import androidx.lifecycle.ViewModel
import com.bilibili.watchlater.data.remote.CookieProvider

class LoginViewModel(private val cookieProvider: CookieProvider) : ViewModel() {

    val isLoggedIn: Boolean
        get() = cookieProvider.isLoggedIn

    fun saveCookies(fullCookies: String?, biliJct: String?, dedeUserId: String?) {
        cookieProvider.saveAll(fullCookies, biliJct, dedeUserId)
        com.bilibili.watchlater.util.AppLogger.i("LoginVM", "Cookie已保存，完整Cookie长度=${fullCookies?.length ?: 0}")
    }
}
