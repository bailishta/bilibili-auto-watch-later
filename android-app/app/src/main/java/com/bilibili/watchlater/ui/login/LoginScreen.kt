package com.bilibili.watchlater.ui.login

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bilibili.watchlater.R
import com.bilibili.watchlater.ui.theme.Pink
import com.bilibili.watchlater.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel
) {
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Pink
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.login_description),
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            errorMsg?.let {
                Text(
                    text = it,
                    color = androidx.compose.ui.graphics.Color.Red,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                LoginWebView(
                    onLoginSuccess = {
                        viewModel.saveCookies(it.first, it.second, it.third)
                        onLoginSuccess()
                    },
                    onError = { errorMsg = it }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(
    onLoginSuccess: (Triple<String?, String?, String?>) -> Unit,
    onError: (String) -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        // 登录成功后 B站 会从 passport 重定向到主站，此时提取 Cookie
                        if (url.contains("bilibili.com") && !url.contains("passport")) {
                            val cookieMgr = CookieManager.getInstance()
                            val allCookies = cookieMgr.getCookie("https://www.bilibili.com") ?: ""

                            val sessdata = extractCookie(allCookies, "SESSDATA")
                            val biliJct = extractCookie(allCookies, "bili_jct")
                            val dedeUserId = extractCookie(allCookies, "DedeUserID")

                            if (!sessdata.isNullOrEmpty()) {
                                com.bilibili.watchlater.util.AppLogger.i(
                                    "Login",
                                    "提取Cookie成功，完整长度=${allCookies.length}"
                                )
                                onLoginSuccess(Triple(
                                    if (allCookies.isNotEmpty()) allCookies else null,
                                    biliJct,
                                    dedeUserId
                                ))
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        errorCode: Int,
                        description: String,
                        failingUrl: String
                    ) {
                        if (errorCode != ERROR_TIMEOUT && errorCode != ERROR_CONNECT) {
                            onError("加载失败: $description")
                        }
                    }
                }
            }.also { webView = it }
        },
        modifier = Modifier.fillMaxSize()
    )

    androidx.compose.runtime.LaunchedEffect(webView) {
        webView?.loadUrl("https://passport.bilibili.com/login")
    }
}

private fun extractCookie(allCookies: String, name: String): String? {
    val regex = Regex("$name=([^;]+)")
    return regex.find(allCookies)?.groupValues?.getOrNull(1)
}
