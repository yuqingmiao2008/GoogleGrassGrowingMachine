package io.github.yuqingmiao2008.grassmachine.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.github.yuqingmiao2008.grassmachine.MainActivity
import io.github.yuqingmiao2008.grassmachine.R

/**
 * 前台服务：翻译 / 音频合成在后台进行时提权，避免被系统回收，同时把进度显示在通知栏。
 */
class WorkService : Service() {

    companion object {
        const val CHANNEL_ID = "ggm_work_v2"
        private const val OLD_CHANNEL_ID = "ggm_work"
        private const val NOTI_ID = 1001
        private const val TEST_NOTI_ID = 1002

        private const val ACTION_START = "ggm.action.START"
        private const val ACTION_UPDATE = "ggm.action.UPDATE"
        private const val ACTION_STOP = "ggm.action.STOP"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_TITLE = "title"

        @Volatile
        var isRunning = false
            private set

        /** 最近一次启动前台服务失败的原因（供「通知诊断」展示） */
        @Volatile
        var lastError: String? = null
            private set

        fun start(context: Context, title: String = "谷歌生草机正在运行", text: String = "") {
            ensureChannel(context)
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
            }.onFailure { lastError = "启动失败：${it.javaClass.simpleName} ${it.message}" }
        }

        fun update(context: Context, title: String, text: String) {
            if (!isRunning) {
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
            if (!isRunning) return
            runCatching {
                context.startService(
                    Intent(context, WorkService::class.java).apply { action = ACTION_STOP }
                )
            }
        }

        /** 提前建好通知频道（不依赖服务启动），避免首次通知因频道延迟而不显示 */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "后台任务进度",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "翻译与音频合成进度（静默显示，不响铃不震动）"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ch)
            }
            // 清理旧版本频道，避免用户在旧频道里关掉后一直收不到通知
            runCatching { nm.deleteNotificationChannel(OLD_CHANNEL_ID) }
        }

        /** 发送一条测试通知，返回给用户看的结果说明 */
        fun sendTestNotification(context: Context): String {
            ensureChannel(context)
            val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            val nm = context.getSystemService(NotificationManager::class.java)
            val ch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm?.getNotificationChannel(CHANNEL_ID)
            } else null
            val channelBlocked = ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE

            val pi = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ggm)
                .setContentTitle("测试通知：如果你看到这条，说明通知正常")
                .setContentText("谷歌生草机的进度通知会用同一个频道显示")
                .setStyle(NotificationCompat.BigTextStyle().bigText("如果你看到这条通知，说明权限与频道都正常；正式翻译时这里会显示进度。"))
                .setAutoCancel(true)
                .setSilent(true)
                .setShowWhen(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            return try {
                NotificationManagerCompat.from(context).notify(TEST_NOTI_ID, n)
                buildString {
                    append(if (enabled) "已发送测试通知。" else "已发送，但「允许通知」是关闭的，所以你什么都看不到。")
                    append("\n· 允许通知：${if (enabled) "是" else "否"}")
                    if (ch != null) {
                        append("\n· 频道「后台任务进度」：重要性 ${ch.importance}${if (channelBlocked) "（已被关闭）" else ""}")
                    }
                    append("\n· 前台服务运行中：${if (isRunning) "是" else "否"}")
                    lastError?.let { append("\n· 最近错误：$it") }
                }
            } catch (e: Exception) {
                "发送失败：${e.javaClass.simpleName} ${e.message}"
            }
        }
    }

    private var title = "谷歌生草机正在运行"
    private var text = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTI_ID, buildNotification(), type)
            isRunning = true
            lastError = null
        } catch (e: Exception) {
            lastError = "startForeground 失败：${e.javaClass.simpleName} ${e.message}"
            isRunning = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> {
                intent.getStringExtra(EXTRA_TITLE)?.let { title = it }
                intent.getStringExtra(EXTRA_TEXT)?.let { text = it }
                runCatching {
                    getSystemService(NotificationManager::class.java).notify(NOTI_ID, buildNotification())
                }
            }
            ACTION_STOP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                isRunning = false
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
