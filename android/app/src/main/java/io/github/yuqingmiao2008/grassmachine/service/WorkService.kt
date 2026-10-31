package io.github.yuqingmiao2008.grassmachine.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.yuqingmiao2008.grassmachine.MainActivity
import io.github.yuqingmiao2008.grassmachine.R

/**
 * 前台服务：翻译 / 音频合成在后台进行时提权，避免被系统回收，同时把进度显示在通知栏。
 */
class WorkService : Service() {

    companion object {
        const val CHANNEL_ID = "ggm_work"
        private const val NOTI_ID = 1001
        private const val ACTION_START = "ggm.action.START"
        private const val ACTION_UPDATE = "ggm.action.UPDATE"
        private const val ACTION_STOP = "ggm.action.STOP"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_TITLE = "title"

        @Volatile
        private var started = false

        fun start(context: Context, title: String = "谷歌生草机正在运行", text: String = "") {
            val i = Intent(context, WorkService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
                started = true
            }
        }

        fun update(context: Context, title: String, text: String) {
            if (!started) {
                start(context, title, text)
                return
            }
            val i = Intent(context, WorkService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
            }
            runCatching { context.startService(i) }
        }

        fun stop(context: Context) {
            if (!started) return
            started = false
            runCatching {
                context.startService(
                    Intent(context, WorkService::class.java).apply { action = ACTION_STOP }
                )
            }
        }
    }

    private var title = "谷歌生草机正在运行"
    private var text = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTI_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> {
                intent.getStringExtra(EXTRA_TITLE)?.let { title = it }
                intent.getStringExtra(EXTRA_TEXT)?.let { text = it }
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTI_ID, buildNotification())
            }
            ACTION_STOP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "后台任务", NotificationManager.IMPORTANCE_LOW).apply {
            description = "翻译与音频合成进度"
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val content = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, content,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ggm)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
