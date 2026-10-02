package com.bilibili.watchlater

import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bilibili.watchlater.ui.home.HomeScreen
import com.bilibili.watchlater.ui.home.HomeViewModel
import com.bilibili.watchlater.ui.importlist.ImportScreen
import com.bilibili.watchlater.ui.importlist.ImportViewModel
import com.bilibili.watchlater.ui.log.LogScreen
import com.bilibili.watchlater.ui.login.LoginScreen
import com.bilibili.watchlater.ui.login.LoginViewModel
import com.bilibili.watchlater.ui.theme.BiliWatchLaterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = BiliApp.instance
        setContent {
            val factory = remember { object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                    HomeViewModel::class.java -> HomeViewModel(app.checkRepo, app.creatorRepo, app.settingsRepo, app.api, app.activityRepo, app.backupRepo)
                    ImportViewModel::class.java -> ImportViewModel(app.checkRepo, app.creatorRepo, app.settingsRepo)
                    LoginViewModel::class.java -> LoginViewModel(app.cookieProvider)
                    else -> error("未知 ViewModel")
                } as T
            } }
            val homeViewModel: HomeViewModel = viewModel(factory = factory)
            val importViewModel: ImportViewModel = viewModel(factory = factory)
            val loginViewModel: LoginViewModel = viewModel(factory = factory)
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            var showLogin by rememberSaveable { mutableStateOf(!app.cookieProvider.isLoggedIn) }
            var showImport by rememberSaveable { mutableStateOf(app.cookieProvider.isLoggedIn && !app.settingsRepo.importDone) }
            var showLog by rememberSaveable { mutableStateOf(false) }
            val dark = when (state.themeMode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            BiliWatchLaterTheme(darkTheme = dark) {
                when {
                    showLogin -> LoginScreen(onLoginSuccess = { importViewModel.reset(); showLogin = false; showImport = !app.settingsRepo.importDone }, viewModel = loginViewModel)
                    showImport -> ImportScreen(uid = app.cookieProvider.dedeUserId?.toLongOrNull() ?: 0,
                        viewModel = importViewModel, onDone = { showImport = false })
                    showLog -> {
                        BackHandler { showLog = false }
                        LogScreen(onBack = { showLog = false })
                    }
                    else -> HomeScreen(homeViewModel, onLogout = {
                        app.cookieProvider.clear()
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        app.settingsRepo.importDone = false
                        showLogin = true
                    }, onOpenLog = { showLog = true }, userId = app.cookieProvider.dedeUserId?.toLongOrNull() ?: 0)
                }
            }
        }
    }
}
