package io.github.yuqingmiao2008.grassmachine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yuqingmiao2008.grassmachine.core.Net
import io.github.yuqingmiao2008.grassmachine.ui.AboutScreen
import io.github.yuqingmiao2008.grassmachine.ui.HistoryScreen
import io.github.yuqingmiao2008.grassmachine.ui.IpCheckScreen
import io.github.yuqingmiao2008.grassmachine.ui.KeepAliveScreen
import io.github.yuqingmiao2008.grassmachine.ui.SettingsScreen
import io.github.yuqingmiao2008.grassmachine.ui.TranslateScreen
import io.github.yuqingmiao2008.grassmachine.ui.theme.GgmTheme
import kotlinx.coroutines.launch

enum class Dest(val title: String, val icon: ImageVector) {
    TRANSLATE("生草主页", Icons.Filled.Translate),
    HISTORY("历史记录", Icons.Filled.History),
    IP("IP 检查", Icons.Filled.Search),
    KEEP("保活引导", Icons.Filled.BatteryFull),
    SETTINGS("设置", Icons.Filled.Settings),
    ABOUT("关于与帮助", Icons.Filled.Info),
}

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 先建好通知频道，保证设置页/诊断页能看到频道状态
        io.github.yuqingmiao2008.grassmachine.service.WorkService.ensureChannel(this)
        startForegroundIfNeeded()
        GrassApp.tts.init(GrassApp.settings.value.ttsEngine)

        setContent {
            val settings by GrassApp.settings.state.collectAsStateWithLifecycle()
            GgmTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                fontSp = settings.fontSizeSp,
            ) {
                AppRoot()
            }
        }
    }

    private fun startForegroundIfNeeded() {
        if (GrassApp.settings.value.foregroundService) {
            io.github.yuqingmiao2008.grassmachine.service.WorkService.start(
                this,
                text = "待命中，翻译时可切到后台",
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 任务还在跑时不要撤掉前台服务与通知，否则退出界面就会失去后台保活
        if (!isChangingConfigurations && !GrassApp.job.state.value.running) {
            io.github.yuqingmiao2008.grassmachine.service.WorkService.stop(this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var current by rememberSaveable { mutableStateOf(Dest.TRANSLATE.name) }
    val dest = Dest.valueOf(current)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ip by Net.activeIpFlow.collectAsStateWithLifecycle()
    var showGuide by rememberSaveable { mutableStateOf(GrassApp.settings.value.firstRun) }

    BackHandler(enabled = drawerState.isOpen || dest != Dest.TRANSLATE) {
        if (drawerState.isOpen) scope.launch { drawerState.close() } else current = Dest.TRANSLATE.name
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(20.dp)) {
                    Text("谷歌生草机", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Android 移植版 · v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (ip.isNullOrBlank()) "IP：系统 DNS" else "IP：$ip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
                Dest.entries.forEach { d ->
                    NavigationDrawerItem(
                        label = { Text(d.title) },
                        icon = { Icon(d.icon, contentDescription = null) },
                        selected = d == dest,
                        onClick = {
                            current = d.name
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(dest.title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "菜单")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                )
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                when (dest) {
                    Dest.TRANSLATE -> TranslateScreen()
                    Dest.HISTORY -> HistoryScreen()
                    Dest.IP -> IpCheckScreen()
                    Dest.KEEP -> KeepAliveScreen()
                    Dest.SETTINGS -> SettingsScreen(onOpenKeep = { current = Dest.KEEP.name })
                    Dest.ABOUT -> AboutScreen()
                }
            }
        }
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("先做三件事，保证后台不被打断") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("长文本生草可能要跑十几分钟，很多手机会在息屏后杀掉后台。建议先在「保活引导」里完成：")
                    Text("1. 允许通知（进度通知不被折叠）")
                    Text("2. 忽略电池优化（不限制后台运行）")
                    Text("3. 允许自启动 + 最近任务里下拉锁定本应用")
                    Text("4. 按需开启无障碍服务，进一步提升存活优先级")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "无障碍服务是本应用内的空实现，只用于提高进程优先级，不读取任何界面内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    current = Dest.KEEP.name
                    showGuide = false
                    GrassApp.settings.update { it.copy(firstRun = false) }
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGuide = false
                    GrassApp.settings.update { it.copy(firstRun = false) }
                }) { Text("以后再说") }
            },
        )
    }
}
