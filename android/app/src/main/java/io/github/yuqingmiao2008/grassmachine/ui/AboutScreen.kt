package io.github.yuqingmiao2008.grassmachine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.yuqingmiao2008.grassmachine.BuildConfig
import io.github.yuqingmiao2008.grassmachine.GrassApp
import io.github.yuqingmiao2008.grassmachine.core.Saver

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    var showCrash by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "谷歌生草机 Android", subtitle = "v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）") {
                Text(
                    "桌面版「谷歌生草机 4」的 Android 移植版本：把一段话经过多轮小语种翻译，最后翻回中文，" +
                        "让机器翻译的失真效果以视频/语音可用的形式呈现出来。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "上游项目：zhdbk3/GoogleGrassGrowingMachine（作者 @MC着火的冰块）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "IP 检查思路来自 Ponderfly/GoogleTranslateIpCheck，本移植改为应用内 DNS 注入实现。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "使用步骤") {
                Text("1. 粘贴文本，设置分隔符，点「切句」", style = MaterialTheme.typography.bodySmall)
                Text("2. 到「IP 检查」扫描并启用一个可用 IP（国内网络必需）", style = MaterialTheme.typography.bodySmall)
                Text("3. 回到主页点「开始生草」，可切后台（通知栏有进度）", style = MaterialTheme.typography.bodySmall)
                Text("4. 结果可逐行复制/朗读，也能导出 txt、下载音频", style = MaterialTheme.typography.bodySmall)
                Text("5. 「历史记录」会自动保存每次生草，可收藏与再次复制", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            SectionCard(title = "功能一览") {
                Text("· 全语种池（Google 翻译支持的全部语种，可搜索/全选/常用分组）", style = MaterialTheme.typography.bodySmall)
                Text("· 轮数 / 超时 / 重试 / 并发 / 间隔 / 最终语言 全部可调", style = MaterialTheme.typography.bodySmall)
                Text("· 原文、译文、对照三种复制，逐行复制与朗读", style = MaterialTheme.typography.bodySmall)
                Text("· Google 在线 TTS：完整 MP3 + 分段 MP3 压缩包，存到 Download", style = MaterialTheme.typography.bodySmall)
                Text("· 系统 TTS 朗读：Google TTS 优先、其次系统引擎", style = MaterialTheme.typography.bodySmall)
                Text("· Material 3 界面，纯黑 OLED 模式，动态取色，字体缩放", style = MaterialTheme.typography.bodySmall)
                Text("· 前台服务 + 保活引导（通知/电池白名单/自启动/后台锁定/无障碍）", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            SectionCard(title = "排错") {
                Text(
                    "· 全部行失败：多半是 IP 失效或被风控，去「IP 检查」重新扫描",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "· 提示 HTTP 302/403：说明被 Google 拦截，换 IP 或降低并发",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showCrash = true }) { Text("查看崩溃日志") }
                    TextButton(onClick = {
                        val crash = GrassApp.lastCrash
                        if (crash.isNullOrBlank()) {
                            ctx.toast("没有崩溃记录")
                        } else {
                            runCatching {
                                Saver.save(ctx, Saver.uniqueName("崩溃日志", "txt"), "text/plain", false) {
                                    it.write(crash.toByteArray())
                                }
                            }.onSuccess { ctx.toast("已导出到 Download") }
                                .onFailure { ctx.toast("导出失败：${it.message}") }
                        }
                    }) { Text("导出崩溃日志") }
                    TextButton(onClick = {
                        GrassApp.settings.update { it.copy(firstRun = true) }
                        ctx.toast("重新打开应用会再次显示保活引导")
                    }) { Text("重看引导") }
                }
            }
        }

        item {
            SectionCard(title = "免责声明") {
                Text(
                    "本应用仅用于语言学习与娱乐，翻译内容来自 Google 翻译公开接口，" +
                        "请勿用于违反当地法律法规或平台条款的用途。软件按「现状」提供，不附带任何担保。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showCrash) {
        AlertDialog(
            onDismissRequest = { showCrash = false },
            title = { Text("崩溃日志") },
            text = {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
                        MonoText(GrassApp.lastCrash ?: "暂无崩溃记录")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ctx.copy(GrassApp.lastCrash.orEmpty(), "崩溃日志")
                }) { Text("复制") }
            },
            dismissButton = { TextButton(onClick = { showCrash = false }) { Text("关闭") } },
        )
    }
}
