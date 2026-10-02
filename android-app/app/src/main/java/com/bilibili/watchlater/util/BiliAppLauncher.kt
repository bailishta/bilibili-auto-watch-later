package com.bilibili.watchlater.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat

object BiliAppLauncher {
    private const val TAG = "BiliAppLauncher"
    private const val PACKAGE = "tv.danmaku.bili"
    private const val WATCH_LATER_URI = "bilibili://user_center/watch_later_v2"
    private const val LEGACY_WATCH_LATER_URI = "bilibili://main/drawer/watch-later"

    // 返回需要显示的提示；null 表示启动请求已发出，不代表已确认 B站显示了目标页面。
    @Suppress("DEPRECATION")
    fun openWatchLater(context: Context): String? {
        try {
            val info = context.packageManager.getPackageInfo(PACKAGE, 0)
            AppLogger.i(TAG, "B站版本=${info.versionName}，versionCode=${PackageInfoCompat.getLongVersionCode(info)}")
        } catch (error: PackageManager.NameNotFoundException) {
            AppLogger.w(TAG, "当前用户空间未找到包 $PACKAGE", error)
            return "当前用户空间未找到哔哩哔哩 APP"
        }

        fun viewIntent(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setPackage(PACKAGE)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun tryStart(label: String, intent: Intent): Boolean {
            AppLogger.i(TAG, "尝试 $label：component=${intent.component?.className ?: "系统匹配"}，uri=${intent.data}")
            return try {
                context.startActivity(intent)
                AppLogger.i(TAG, "$label 启动请求已发出")
                true
            } catch (error: ActivityNotFoundException) {
                AppLogger.w(TAG, "$label 无可用入口：${error.message}", error)
                false
            } catch (error: SecurityException) {
                AppLogger.w(TAG, "$label 不允许外部启动：${error.message}", error)
                false
            }
        }

        // 旧版若开放了独立页面，可直接进入，避免依赖链接路由的版本变化。
        val direct = viewIntent(WATCH_LATER_URI)
            .setClassName(PACKAGE, "$PACKAGE.ui.main2.WatchLaterActivity")
        if (tryStart("稍后再看页面", direct)) return null

        // 显式指定 B站的链接接收入口，避免仅依赖系统的 URI 匹配。
        val handler = viewIntent(WATCH_LATER_URI)
            .setClassName(PACKAGE, "$PACKAGE.ui.intent.IntentHandlerActivity")
        if (tryStart("B站链接接收入口", handler)) return null
        if (tryStart("稍后再看链接", viewIntent(WATCH_LATER_URI))) return null
        if (tryStart("旧版稍后再看链接", viewIntent(LEGACY_WATCH_LATER_URI))) return null

        // 页面入口均不可用时仍允许打开已安装的 APP，并明确提示手动进入。
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (launch != null && tryStart("B站主界面", launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            return "已打开 B站，请在“我的”中进入“稍后再看”；跳转详情已记录到运行日志"
        }
        AppLogger.e(TAG, "全部 B站启动入口均失败")
        return "B站已安装，但启动失败，请在设置的运行日志中查看跳转详情"
    }
}
