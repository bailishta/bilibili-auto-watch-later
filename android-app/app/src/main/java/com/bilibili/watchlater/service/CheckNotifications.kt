package com.bilibili.watchlater.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bilibili.watchlater.MainActivity
import com.bilibili.watchlater.R
import com.bilibili.watchlater.data.repository.CheckReport
import com.bilibili.watchlater.util.AppLogger

object CheckNotifications {
    const val PROGRESS_ID = 1001
    const val RESULT_ID = 1002
    const val RESULT_CHANNEL = "check_result"
    /** OriginOS supplies the app badge from launcher metadata; a large icon is a separate attachment. */
    fun builder(context: Context, channel: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification_bookmark)
            .setColor(0xFFD66079.toInt())
    }

    /** Clear only our old notifications once after the artwork upgrade, preserving channel preferences. */
    fun migrateAppearance(context: Context) {
        val prefs = context.getSharedPreferences("notification_appearance", Context.MODE_PRIVATE)
        if (prefs.getInt("version", 0) >= 2) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(PROGRESS_ID)
        manager.cancel(RESULT_ID)
        prefs.edit().putInt("version", 2).apply()
    }

    fun completed(context: Context, report: CheckReport) {
        if (report.status != "ok") return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            RESULT_CHANNEL, "检查结果", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "检查完成后提醒新增视频数量" })
        if (!manager.areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            AppLogger.i("CheckNotifications", "通知未获允许，结果保留在 App 内")
            return
        }
        val text = if (report.added == 0) "本次没有新增视频" else "已将 ${report.added} 个视频加入稍后观看"
        val detail = text + if (report.errors > 0) "，${report.errors} 项未成功，可在 App 内查看" else ""
        try {
            manager.notify(RESULT_ID, builder(context, RESULT_CHANNEL)
                .setContentTitle(if (report.errors > 0) "检查结束，部分项目未成功" else "检查完成")
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setContentIntent(PendingIntent.getActivity(context, RESULT_ID,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build())
        } catch (error: SecurityException) {
            AppLogger.w("CheckNotifications", "通知权限已变更，结果保留在 App 内", error)
        }
    }
}
