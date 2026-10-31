package io.github.yuqingmiao2008.grassmachine.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yuqingmiao2008.grassmachine.GrassApp
import io.github.yuqingmiao2008.grassmachine.service.KeepAliveService
import io.github.yuqingmiao2008.grassmachine.service.WorkService

@Composable
fun KeepAliveScreen() {
    val ctx = LocalContext.current
    val settings by GrassApp.settings.state.collectAsStateWithLifecycle()

    var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(ctx)) }
    var a11yOn by remember { mutableStateOf(isAccessibilityEnabled(ctx)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
                batteryOk = isIgnoringBattery(ctx)
                a11yOn = isAccessibilityEnabled(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            InfoBanner(
                "长文本生草往往要跑十几分钟。安卓厂商的省电策略会在息屏后杀掉后台，" +
                    "下面几项设置能显著提高存活率。全部都是可选项，不影响功能本身。"
            )
        }

        item {
            SectionCard(title = "1. 通知权限", subtitle = "进度通知常驻，系统才不容易回收进程") {
                SettingRow("已允许通知", if (notificationsOn) "已开启" else "未开启") {
                    StatusDot(notificationsOn)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openAppNotificationSettings(ctx)
                        }
                    }) { Text("去开启") }
                    OutlinedButton(onClick = { openAppNotificationSettings(ctx) }) { Text("通知设置") }
                }
            }
        }

        item {
            SectionCard(title = "2. 忽略电池优化", subtitle = "避免系统在息屏后限制后台网络与 CPU") {
                SettingRow("已忽略电池优化", if (batteryOk) "已加入白名单" else "仍受系统限制") {
                    StatusDot(batteryOk)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { requestIgnoreBattery(ctx) }) { Text("申请白名单") }
                    OutlinedButton(onClick = { openBatterySettings(ctx) }) { Text("电池设置") }
                }
            }
        }

        item {
            SectionCard(title = "3. 自启动 / 后台运行", subtitle = "点按钮会尝试打开对应厂商的自启动管理页；找不到就按下面步骤手动开") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openAutoStart(ctx) }) { Text("打开自启动管理") }
                    OutlinedButton(onClick = { openAppDetails(ctx) }) { Text("应用信息") }
                }
                Text(
                    "通用步骤：设置 → 应用管理 → 谷歌生草机 → 权限/省电策略 → 允许自启动、允许后台活动，" +
                        "省电策略设为「无限制」。MIUI 还需关闭「后台弹出界面」限制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "4. 最近任务里锁定后台", subtitle = "最简单有效的一招") {
                Text(
                    "打开最近任务（多任务）界面 → 长按或下拉本应用卡片 → 出现小锁图标即表示已锁定，" +
                        "清理全部任务时不会被清掉。部分机型叫「锁定」「固定」「加入白名单」。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            SectionCard(
                title = "5. 无障碍服务（可选，提升优先级）",
                subtitle = "本应用内置的是空实现，不读取任何界面内容、不采集任何数据",
            ) {
                SettingRow("无障碍服务已开启", if (a11yOn) "运行中" else "未开启") {
                    StatusDot(a11yOn)
                }
                OutlinedButton(onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure { ctx.toast("无法打开无障碍设置") }
                }) { Text("打开无障碍设置") }
                Text(
                    "在列表中找到「谷歌生草机 · 后台保活」并开启，可随时在本页或系统设置里停用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "6. 常驻前台服务", subtitle = "通知栏显示进度，翻译可安全切后台") {
                SettingRow("启用前台服务", if (settings.foregroundService) "已启用" else "已关闭") {
                    Switch(
                        checked = settings.foregroundService,
                        onCheckedChange = { on ->
                            GrassApp.settings.update { it.copy(foregroundService = on) }
                            if (on) WorkService.start(ctx, text = "待命中") else WorkService.stop(ctx)
                        },
                    )
                }
            }
        }

        item {
            SectionCard(title = "7. 其它建议") {
                Text("· 长任务建议插电，并让屏幕保持亮着最稳", style = MaterialTheme.typography.bodySmall)
                Text("· 关闭「锁屏后断开数据」「休眠时断开网络」", style = MaterialTheme.typography.bodySmall)
                Text("· 并发别开太大（8 左右）；被 Google 风控时降低并发更稳", style = MaterialTheme.typography.bodySmall)
                Text("· 出现大量失败先到「IP 检查」重新扫描并启用新 IP", style = MaterialTheme.typography.bodySmall)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatusDot(on: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            if (on) "OK" else "待处理",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun isIgnoringBattery(ctx: Context): Boolean = runCatching {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    pm.isIgnoringBatteryOptimizations(ctx.packageName)
}.getOrDefault(false)

internal fun isAccessibilityEnabled(ctx: Context): Boolean = runCatching {
    val expected = ComponentName(ctx, KeepAliveService::class.java)
    val enabled = Settings.Secure.getString(
        ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    enabled.split(':').any {
        it.equals(expected.flattenToString(), true) || it.startsWith(ctx.packageName, true)
    }
}.getOrDefault(false)

private fun requestIgnoreBattery(ctx: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${ctx.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(direct) }.onFailure { openBatterySettings(ctx) }
}

private fun openBatterySettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { openAppDetails(ctx) }
}

private fun openAppNotificationSettings(ctx: Context) {
    val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${ctx.packageName}"))
    }
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { openAppDetails(ctx) }
}

private fun openAppDetails(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { ctx.toast("无法打开应用信息页") }
}

private fun openAutoStart(ctx: Context) {
    val candidates = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
        ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
    )
    for (cn in candidates) {
        val intent = Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            runCatching { ctx.startActivity(intent) }.onSuccess {
                ctx.toast("已打开厂商自启动管理，请找到本应用并允许")
                return
            }
        }
    }
    ctx.toast("当前机型没有可用的自启动管理入口，请按步骤手动设置")
    openAppDetails(ctx)
}
