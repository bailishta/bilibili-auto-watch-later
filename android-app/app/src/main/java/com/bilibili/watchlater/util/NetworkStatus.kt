package com.bilibili.watchlater.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

// VALIDATED reflects Android's probe, not whether Bili's API can currently be reached.
// Use this only as a connection hint; actual requests determine reachability.
fun Context.hasNetworkConnection(): Boolean {
    val manager = getSystemService(ConnectivityManager::class.java)
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
