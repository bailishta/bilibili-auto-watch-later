package com.bilibili.watchlater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            BiliWatchLaterTheme {
                var showLogin by remember { mutableStateOf(!app.cookieProvider.isLoggedIn) }
                var showImport by remember {
                    mutableStateOf(app.cookieProvider.isLoggedIn && !app.settingsRepo.importDone)
                }
                var showLog by remember { mutableStateOf(false) }

                if (showLogin) {
                    val loginViewModel = remember { LoginViewModel(app.cookieProvider) }
                    LoginScreen(
                        onLoginSuccess = {
                            showLogin = false
                            showImport = !app.settingsRepo.importDone
                        },
                        viewModel = loginViewModel
                    )
                } else if (showImport) {
                    val uid = app.cookieProvider.dedeUserId?.toLongOrNull() ?: 0L
                    val importViewModel = remember {
                        ImportViewModel(app.checkRepo, app.creatorRepo, app.settingsRepo)
                    }
                    ImportScreen(
                        uid = uid,
                        viewModel = importViewModel,
                        onDone = { showImport = false }
                    )
                } else if (showLog) {
                    LogScreen(onBack = { showLog = false })
                } else {
                    val homeViewModel = remember {
                        HomeViewModel(
                            checkRepo = app.checkRepo,
                            creatorRepo = app.creatorRepo,
                            settingsRepo = app.settingsRepo,
                            api = app.api
                        )
                    }
                    HomeScreen(
                        viewModel = homeViewModel,
                        onLogout = {
                            app.cookieProvider.clear()
                            app.settingsRepo.importDone = false
                            showLogin = true
                        },
                        onOpenLog = { showLog = true }
                    )
                }
            }
        }
    }
}
