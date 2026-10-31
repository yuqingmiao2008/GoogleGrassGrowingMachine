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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yuqingmiao2008.grassmachine.GrassApp
import io.github.yuqingmiao2008.grassmachine.core.GoogleTts
import io.github.yuqingmiao2008.grassmachine.core.Net
import io.github.yuqingmiao2008.grassmachine.core.Saver
import io.github.yuqingmiao2008.grassmachine.data.LangData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onOpenKeep: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by GrassApp.settings.state.collectAsStateWithLifecycle()
    val history by GrassApp.history.flow.collectAsStateWithLifecycle()
    val ttsState by GrassApp.tts.state.collectAsStateWithLifecycle()

    var showLangPool by remember { mutableStateOf(false) }
    var showFinalLang by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "翻译参数", subtitle = "轮数越多越离谱，也越慢") {
                NumberStepper("翻译次数（轮）", settings.times, 1f..50f) {
                    GrassApp.settings.update { s -> s.copy(times = it) }
                }
                NumberStepper("单次请求超时（秒）", settings.timeoutSec, 3f..120f) {
                    GrassApp.settings.update { s -> s.copy(timeoutSec = it) }
                }
                NumberStepper("出错重试次数", settings.retries, 0f..20f) {
                    GrassApp.settings.update { s -> s.copy(retries = it) }
                }
                NumberStepper("并发线程数", settings.concurrency, 1f..40f) {
                    GrassApp.settings.update { s -> s.copy(concurrency = it) }
                }
                NumberStepper("请求最小间隔（毫秒）", settings.minIntervalMs, 0f..2000f, step = 50) {
                    GrassApp.settings.update { s -> s.copy(minIntervalMs = it) }
                }
                SettingRow("随机顺序", "每轮随机挑一个语种") {
                    Switch(checked = settings.randomOrder, onCheckedChange = {
                        GrassApp.settings.update { s -> s.copy(randomOrder = it) }
                    })
                }
                SettingRow("最终语言", LangData.name(settings.finalLang)) {
                    TextButton(onClick = { showFinalLang = true }) { Text("选择") }
                }
                OutlinedTextField(
                    value = settings.separator,
                    onValueChange = { v -> GrassApp.settings.update { s -> s.copy(separator = v) } },
                    label = { Text("切句分隔符") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SectionCard(
                title = "语种池",
                subtitle = "已选 ${settings.selectedLangs.size} / 共 ${LangData.ALL_CODES.size} 种（默认全选 Google 支持的全部语种）",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { showLangPool = true }) { Text("选择语种") }
                    TextButton(onClick = {
                        GrassApp.settings.update { it.copy(selectedLangs = LangData.ALL_CODES.toSet()) }
                        ctx.toast("已全选全部语种")
                    }) { Text("一键全选") }
                }
                Text(
                    "已选：" + settings.selectedLangs.take(12).joinToString("、") { LangData.name(it) } +
                        if (settings.selectedLangs.size > 12) " 等" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "网络与接口") {
                SettingRow(
                    "使用扫描到的 IP",
                    if (Net.activeIp == null) "当前为系统 DNS" else "当前 ${Net.activeIp}",
                ) {
                    Switch(
                        checked = settings.useScannedIp,
                        onCheckedChange = { on ->
                            GrassApp.settings.update { s -> s.copy(useScannedIp = on) }
                            Net.setActiveIp(
                                if (on) GrassApp.settings.value.activeIp.takeIf { it.isNotBlank() } else null
                            )
                        },
                    )
                }
                SettingRow("使用 Google Cloud API Key", "官方通道，更稳定；留空则用免密钥端点") {
                    Switch(checked = settings.useApiKey, onCheckedChange = { on ->
                        GrassApp.settings.update { s -> s.copy(useApiKey = on) }
                    })
                }
                OutlinedTextField(
                    value = settings.apiKey,
                    onValueChange = { v -> GrassApp.settings.update { s -> s.copy(apiKey = v.trim()) } },
                    label = { Text("API Key（可选）") },
                    singleLine = true,
                    enabled = settings.useApiKey,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "当前注入 IP：${settings.activeIp.ifBlank { "未设置（去「IP 检查」扫描）" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(
                title = "朗读与音频",
                subtitle = "系统朗读引擎：${ttsState.engineLabel.ifBlank { "未初始化" }}" +
                    (if (GrassApp.tts.isGoogleTtsInstalled()) " · 已检测到 Google TTS" else " · 未安装 Google TTS，用系统引擎"),
            ) {
                Text("引擎优先级", style = MaterialTheme.typography.bodyMedium)
                listOf(
                    "google_first" to "Google TTS 优先，其次系统引擎",
                    "google_only" to "仅用 Google TTS（未安装则回退）",
                    "system_first" to "系统引擎优先",
                    "system_only" to "仅用系统引擎",
                ).forEach { (key, label) ->
                    RadioRow(label, settings.ttsEngine == key) {
                        GrassApp.settings.update { s -> s.copy(ttsEngine = key) }
                        GrassApp.tts.init(key)
                    }
                }
                Text("朗读内容", style = MaterialTheme.typography.bodyMedium)
                listOf(
                    "translated" to "译文",
                    "original" to "原文",
                    "both" to "原文 + 译文",
                ).forEach { (key, label) ->
                    RadioRow(label, settings.ttsTarget == key) {
                        GrassApp.settings.update { s -> s.copy(ttsTarget = key) }
                    }
                }
                LabeledSlider(
                    "语速",
                    settings.ttsRate,
                    0.5f..2.0f,
                    display = String.format("%.1fx", settings.ttsRate),
                ) { GrassApp.settings.update { s -> s.copy(ttsRate = it) } }
                LabeledSlider(
                    "音调",
                    settings.ttsPitch,
                    0.5f..2.0f,
                    display = String.format("%.1f", settings.ttsPitch),
                ) { GrassApp.settings.update { s -> s.copy(ttsPitch = it) } }
                OutlinedTextField(
                    value = settings.audioPrefix,
                    onValueChange = { v -> GrassApp.settings.update { s -> s.copy(audioPrefix = v) } },
                    label = { Text("音频文件名前缀") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingRow("保存到 Download/谷歌生草机 子目录", "关闭则直接放在 Download 根目录") {
                    Switch(checked = settings.audioSubdir, onCheckedChange = {
                        GrassApp.settings.update { s -> s.copy(audioSubdir = it) }
                    })
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val mp3 = GoogleTts.synthesize(
                                    "你好，这是谷歌生草机的音色试听",
                                    settings.finalLang,
                                    settings.timeoutSec * 1000L,
                                )
                                withContext(Dispatchers.IO) {
                                    Saver.saveBytes(
                                        ctx, Saver.uniqueName("试听", "mp3"), "audio/mpeg",
                                        settings.audioSubdir, mp3,
                                    )
                                }
                                ctx.toast("试听音频已保存到 Download")
                            } catch (e: Exception) {
                                ctx.toast("试听失败：${e.message}")
                            }
                        }
                    },
                ) { Text("试听 Google TTS 音色") }
            }
        }

        item {
            SectionCard(title = "外观") {
                listOf(
                    "system" to "跟随系统",
                    "light" to "浅色",
                    "dark" to "深色",
                    "black" to "纯黑（OLED 省电）",
                ).forEach { (key, label) ->
                    RadioRow(label, settings.themeMode == key) {
                        GrassApp.settings.update { s -> s.copy(themeMode = key) }
                    }
                }
                SettingRow("动态取色（Material You）", "Android 12+ 跟随壁纸配色") {
                    Switch(checked = settings.dynamicColor, onCheckedChange = {
                        GrassApp.settings.update { s -> s.copy(dynamicColor = it) }
                    })
                }
                NumberStepper("字体大小（sp）", settings.fontSizeSp, 11f..24f) {
                    GrassApp.settings.update { s -> s.copy(fontSizeSp = it) }
                }
            }
        }

        item {
            SectionCard(title = "后台保活") {
                SettingRow("常驻前台服务", "翻译时可安全切到后台，通知栏显示进度") {
                    Switch(checked = settings.foregroundService, onCheckedChange = {
                        GrassApp.settings.update { s -> s.copy(foregroundService = it) }
                    })
                }
                OutlinedButton(onClick = onOpenKeep) { Text("打开保活引导") }
            }
        }

        item {
            SectionCard(title = "数据与备份", subtitle = "历史 ${history.size} 条") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        try {
                            val saved = Saver.save(
                                ctx, Saver.uniqueName("生草机设置", "json"), "application/json", false,
                            ) { it.write(GrassApp.settings.exportJson().toByteArray()) }
                            ctx.toast("设置已导出到 ${saved.displayPath}")
                        } catch (e: Exception) {
                            ctx.toast("导出失败：${e.message}")
                        }
                    }) { Text("导出设置") }
                    TextButton(onClick = { showImport = true }) { Text("导入设置") }
                    TextButton(onClick = {
                        GrassApp.history.clear()
                        ctx.toast("历史已清空")
                    }) { Text("清空历史") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val text = GrassApp.history.exportJson()
                        try {
                            val saved = Saver.save(
                                ctx, Saver.uniqueName("生草历史", "json"), "application/json", false,
                            ) { it.write(text.toByteArray()) }
                            ctx.toast("历史已导出到 ${saved.displayPath}")
                        } catch (e: Exception) {
                            ctx.toast("导出失败：${e.message}")
                        }
                    }) { Text("导出历史") }
                    TextButton(onClick = { ctx.copy(GrassApp.settings.exportJson(), "设置 JSON") }) {
                        Text("复制设置")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showLangPool) {
        LangPoolDialog(
            settings = settings,
            onDismiss = { showLangPool = false },
            onConfirm = { sel -> GrassApp.settings.update { it.copy(selectedLangs = sel) } },
        )
    }

    if (showFinalLang) {
        AlertDialog(
            onDismissRequest = { showFinalLang = false },
            title = { Text("最终语言") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    LangData.ALL_SORTED.forEach { code ->
                        RadioRow(LangData.name(code) + " ($code)", settings.finalLang == code) {
                            GrassApp.settings.update { it.copy(finalLang = code) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFinalLang = false }) { Text("完成") } },
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("把之前导出的 JSON 粘贴到下面", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 200.dp),
                        label = { Text("设置 JSON") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = GrassApp.settings.importJson(importText.trim())
                    ctx.toast(if (ok) "导入成功" else "导入失败：JSON 无法解析")
                    if (ok) showImport = false
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("取消") } },
        )
    }

    LaunchedEffect(Unit) {
        if (ttsState.engineLabel.isBlank()) GrassApp.tts.init(settings.ttsEngine)
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .selectable(selected = selected, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
