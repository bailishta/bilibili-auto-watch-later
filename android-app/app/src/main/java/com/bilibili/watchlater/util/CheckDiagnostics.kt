package com.bilibili.watchlater.util

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager

fun Context.checkEnvironment(): String {
    val power = getSystemService(PowerManager::class.java)
    val activity = getSystemService(ActivityManager::class.java)
    val network = getSystemService(ConnectivityManager::class.java)
    val caps = network.getNetworkCapabilities(network.activeNetwork)
    return "${Build.MANUFACTURER}/${Build.MODEL} Android ${Build.VERSION.RELEASE} SDK ${Build.VERSION.SDK_INT}" +
        "，亮屏=${power.isInteractive}，休眠=${power.isDeviceIdleMode}，省电=${power.isPowerSaveMode}" +
        "，电池优化豁免=${power.isIgnoringBatteryOptimizations(packageName)}" +
        "，后台受限=${if (Build.VERSION.SDK_INT >= 28) activity.isBackgroundRestricted else false}" +
        "，网络连接=${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true}" +
        "，系统联网验证=${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true}"
}
