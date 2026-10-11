package com.sandolpin.gallery.data.analysis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

/**
 * 写真の分析中に出す、通知(進行状況のバーつき)。
 * 「分析中」と、「1,234 / 10,000枚」のバーを出す。音や振動はなし(重要度は低)。
 *
 * 通知は、WorkManagerの「フォアグラウンド」(アプリが閉じていても、処理を続けやすくする仕組み)として出す。
 * ただし、Android 12以降は、アプリが裏にあるときの自動の分析では、フォアグラウンドにできないことがある。
 * その場合は、ふつうの通知として、同じように出す。
 */
class AnalysisNotifier(private val context: Context) {

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        // Android 8以降は、通知の「チャンネル」が必要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ChannelId,
                "写真の分析",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "あいまい検索のために、写真の中身を分析している間、進み具合を表示します"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** フォアグラウンドにするための情報。[total] が 0 のときは、進み具合の長さを決めずに(くるくる)表示する。 */
    fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val notification = build(done, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationId, notification)
        }
    }

    /** 進み具合を更新する(通知の権限がない場合は、何も表示されない)。 */
    fun update(done: Int, total: Int) {
        try {
            manager.notify(NotificationId, build(done, total))
        } catch (e: SecurityException) {
            // 通知の権限がないとき。分析は、そのまま続ける
        }
    }

    /** 通知を消す。 */
    fun cancel() {
        manager.cancel(NotificationId)
    }

    private fun build(done: Int, total: Int): Notification {
        val indeterminate = total <= 0
        val text = if (indeterminate) {
            "準備しています…"
        } else {
            "%,d / %,d枚".format(done.coerceAtMost(total), total)
        }
        return NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("写真を分析中")
            .setContentText(text)
            .setProgress(total.coerceAtLeast(0), done.coerceAtLeast(0), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .build()
    }

    /** 通知を押したときに、アプリを開く。 */
    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val ChannelId = "gallery_analysis"
        const val NotificationId = 4001
    }
}
