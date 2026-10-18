package com.bilibili.watchlater.ui.home

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bilibili.watchlater.util.hasNetworkConnection

/** Only observe connectivity while this screen is in the foreground. */
@Composable
fun CheckResumeEffect(viewModel: HomeViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        var registered = false
        var active = true
        var wasOnline = false
        fun resume() {
            viewModel.resumePendingCheck { active && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                handler.post {
                    if (active && registered) {
                        val online = context.hasNetworkConnection()
                        if (online && !wasOnline) resume()
                        wasOnline = online
                    }
                }
            }
            override fun onLost(network: Network) { handler.post { if (active) wasOnline = context.hasNetworkConnection() } }
        }
        fun enter() {
            if (registered) return
            wasOnline = context.hasNetworkConnection()
            manager.registerDefaultNetworkCallback(callback)
            registered = true
            resume()
        }
        fun leave() {
            if (registered) manager.unregisterNetworkCallback(callback)
            registered = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> enter()
                Lifecycle.Event.ON_PAUSE -> leave()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) enter()
        onDispose { active = false; leave(); lifecycle.removeObserver(observer) }
    }
}
