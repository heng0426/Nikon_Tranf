package com.example.nikontransfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** 相机连接保持前台服务：
 *  App 退后台后系统会冻结/限流普通进程，事件心跳（0x941c）停摆会让相机
 *  判定客户端失联并拆掉 PTP 会话。此服务让连接期间进程保持活跃，
 *  事件轮询线程得以在后台继续消费相机事件。
 *  连接成功时启动，断开/退出时由 MainActivity 停止。 */
class CameraKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(
            CHANNEL_ID, "相机连接保持", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("相机连接保持中")
            .setContentText("Nikon Wi-Fi 会话活跃，切后台不断开")
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else
            startForeground(NOTIF_ID, notif)
        return START_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "camera_keepalive"
        private const val NOTIF_ID = 1
        private const val PROGRESS_NOTIF_ID = 2

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CameraKeepAliveService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraKeepAliveService::class.java))
        }

        /** 下载进行中：进度通知（与保活通知共存，不同 id）。 */
        fun notifyProgress(context: Context, text: String) {
            val ch = NotificationChannel(
                CHANNEL_ID, "相机连接保持", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)   // 幂等；服务未启动时也保渠道存在
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("下载中")
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
                .build()
            (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(PROGRESS_NOTIF_ID, n)
        }

        /** 下载结束：清除进度通知。 */
        fun clearProgress(context: Context) {
            (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(PROGRESS_NOTIF_ID)
        }

        /** 队列完成：结果通知（可滑动清除）。 */
        fun notifyDone(context: Context, text: String) {
            val ch = NotificationChannel(
                CHANNEL_ID, "相机连接保持", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("下载完成")
                .setContentText(text)
                .setOngoing(false)
                .setSilent(true)
                .build()
            (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(PROGRESS_NOTIF_ID + 1, n)
        }
    }
}
