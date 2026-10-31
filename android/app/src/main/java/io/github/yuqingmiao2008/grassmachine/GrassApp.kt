package io.github.yuqingmiao2008.grassmachine

import android.app.Application
import android.util.Log
import io.github.yuqingmiao2008.grassmachine.core.Net
import io.github.yuqingmiao2008.grassmachine.core.SystemTts
import io.github.yuqingmiao2008.grassmachine.core.TranslateJob
import io.github.yuqingmiao2008.grassmachine.data.HistoryStore
import io.github.yuqingmiao2008.grassmachine.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * 全局容器。任务引擎挂在 Application 作用域，所以切页面 / 息屏 / 旋转都不会打断进度。
 */
class GrassApp : Application() {

    companion object {
        lateinit var instance: GrassApp
            private set

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        lateinit var settings: SettingsStore
            private set
        lateinit var history: HistoryStore
            private set
        lateinit var job: TranslateJob
            private set
        lateinit var tts: SystemTts
            private set

        /** 最近一次崩溃堆栈（用于「关于」页导出） */
        @Volatile
        var lastCrash: String? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        history = HistoryStore(this)
        job = TranslateJob(appScope) { settings.value }
        tts = SystemTts(this)

        val s = settings.value
        Net.setActiveIp(if (s.useScannedIp) s.activeIp.takeIf { it.isNotBlank() } else null)

        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            val trace = Log.getStackTraceString(e)
            lastCrash = trace
            runCatching { File(filesDir, "last_crash.txt").writeText(trace) }
            prev?.uncaughtException(t, e)
        }
        lastCrash = runCatching {
            File(filesDir, "last_crash.txt").takeIf { it.exists() }?.readText()
        }.getOrNull()
    }
}
