package io.github.yuqingmiao2008.grassmachine.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yuqingmiao2008.grassmachine.GrassApp
import io.github.yuqingmiao2008.grassmachine.core.GoogleTts
import io.github.yuqingmiao2008.grassmachine.core.JobRow
import io.github.yuqingmiao2008.grassmachine.core.Net
import io.github.yuqingmiao2008.grassmachine.core.RowStatus
import io.github.yuqingmiao2008.grassmachine.core.Saver
import io.github.yuqingmiao2008.grassmachine.data.LangData
import io.github.yuqingmiao2008.grassmachine.data.Line
import io.github.yuqingmiao2008.grassmachine.data.Settings
import io.github.yuqingmiao2008.grassmachine.service.WorkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TranslateScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by GrassApp.settings.state.collectAsStateWithLifecycle()
    val state by GrassApp.job.state.collectAsStateWithLifecycle()
    val ttsState by GrassApp.tts.state.collectAsStateWithLifecycle()
    val activeIp by Net.activeIpFlow.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var showParams by remember { mutableStateOf(false) }
    var showLangPool by remember { mutableStateOf(false) }
    var audioBusy by remember { mutableStateOf(false) }
    var audioRunning by remember { mutableStateOf(false) }
    var audioText by remember { mutableStateOf("") }
    var showJobLog by remember { mutableStateOf(false) }

    val storagePerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) ctx.toast("未授予存储权限，音频可能无法写入 Download")
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val rows = state.rows
    val originalAll = rows.joinToString("\n") { it.original }
    val translatedAll = rows.joinToString("\n") { it.text }
    val compareAll = rows.joinToString("\n\n") { "${it.original}\n-> ${it.text}" }

    var savedFor by remember { mutableStateOf("") }
    LaunchedEffect(state.finished, state.chains) {
        val key = "${state.chains.joinToString("-")}#${rows.size}"
        if (state.finished && rows.isNotEmpty() && state.jobId != 0L && savedFor != key) {
            savedFor = key
            if (rows.count { it.stepDone >= state.chains.size } > 0) {
                GrassApp.history.add(
                    times = state.chains.size,
                    chains = state.chains,
                    lines = rows.map { Line(it.original, it.text) },
                )
                ctx.toast("已自动存入历史记录")
            }
            WorkService.stop(ctx)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "原文",
                subtitle = "按分隔符自动切句，换行也会切分；每行单独翻译，方便一一对照",
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 240.dp),
                    placeholder = { Text("把要生草的文本粘贴到这里…") },
                    minLines = 4,
                    maxLines = 10,
                )
                OutlinedTextField(
                    value = settings.separator,
                    onValueChange = { v -> GrassApp.settings.update { it.copy(separator = v) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("分隔符（每个字符都会切一次）") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        GrassApp.job.prepare(input, settings.separator)
                        ctx.toast("已切分为 ${GrassApp.job.state.value.rows.size} 行")
                    }) { Text("切句") }
                    TextButton(onClick = {
                        input = GrassApp.job.state.value.rows.joinToString("\n") { it.original }
                    }) { Text("取回原文") }
                    TextButton(onClick = {
                        input = ""
                        GrassApp.job.clear()
                    }) { Text("清空") }
                }
            }
        }

        item {
            SectionCard(title = "参数", subtitle = "全部可自主调整，设置自动保存") {
                Text(
                    "轮数 ${settings.times} · 超时 ${settings.timeoutSec}s · 重试 ${settings.retries} 次 · 并发 ${settings.concurrency}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "最终语言 ${LangData.name(settings.finalLang)} · 语言池 ${settings.selectedLangs.size} 种 · " +
                        (if (settings.randomOrder) "随机顺序" else "固定顺序"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showParams = true }) { Text("调整参数") }
                    TextButton(onClick = { showLangPool = true }) { Text("选择语种") }
                }
                if (activeIp == null) {
                    Text(
                        "⚠ 当前未启用可用 IP：国内网络直连 Google 会失败，建议先到「IP 检查」扫描（约 10~40 秒）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (GrassApp.job.state.value.rows.isEmpty()) {
                            GrassApp.job.prepare(input, settings.separator)
                        }
                        if (GrassApp.job.state.value.rows.isEmpty()) {
                            ctx.toast("请先粘贴文本并切句")
                        } else {
                            GrassApp.job.start(false)
                            if (settings.foregroundService) {
                                WorkService.update(ctx, "正在生草", "准备中…")
                            }
                        }
                    },
                    enabled = !state.running,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("开始生草")
                }
                if (state.running) {
                    OutlinedButton(onClick = { GrassApp.job.stop() }) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("停止")
                    }
                } else if (state.failedCount > 0) {
                    OutlinedButton(onClick = { GrassApp.job.start(true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("重试失败 ${state.failedCount}")
                    }
                }
            }
        }

        if (rows.isNotEmpty()) {
            item {
                SectionCard(title = "进度") {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(state.langLabel.ifBlank { "准备中…" }, style = MaterialTheme.typography.bodyMedium)
                    if (state.chains.isNotEmpty()) {
                        Text(
                            "第 ${(state.step + 1).coerceAtMost(state.chains.size)}/${state.chains.size} 轮 · " +
                                "本轮 ${state.stepDoneCount}/${state.stepTotal} 行 · 完成 ${state.okCount}/${rows.size} 行",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showJobLog = true }) { Text("任务日志") }
                        if (state.log.isNotEmpty()) {
                            TextButton(onClick = { GrassApp.job.clearLog() }) { Text("清空日志") }
                        }
                    }
                }
            }
        }

        if (audioRunning) {
            item {
                SectionCard(title = "音频任务", subtitle = "完成后会提示保存路径；可切到后台等待") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        audioText.ifBlank { "准备中…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item {
            SectionCard(title = "结果（${rows.size} 行）", subtitle = "每行可单独复制与朗读") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CopyIconButton(originalAll, "全部原文")
                    CopyIconButton(translatedAll, "全部译文")
                    CopyIconButton(compareAll, "对照文本")
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, compareAll)
                            }
                            ctx.startActivity(Intent.createChooser(i, "分享生草结果"))
                        },
                        enabled = compareAll.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "分享")
                    }
                }
                Text(
                    "左侧三个复制按钮依次为：原文 / 译文 / 对照",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { exportText(ctx, originalAll, translatedAll, compareAll) },
                        enabled = rows.isNotEmpty(),
                    ) { Text("导出 txt") }
                    TextButton(onClick = { audioBusy = true }) { Text("音频下载") }
                    TextButton(
                        onClick = {
                            if (ttsState.speaking) {
                                GrassApp.tts.stop()
                            } else {
                                val lines =
                                    if (settings.ttsTarget == "original") rows.map { it.original }
                                    else rows.map { it.text }
                                GrassApp.tts.speak(lines, settings.finalLang, settings.ttsRate, settings.ttsPitch)
                            }
                        },
                        enabled = rows.isNotEmpty(),
                    ) {
                        Icon(
                            if (ttsState.speaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(if (ttsState.speaking) "停止朗读" else "朗读")
                    }
                }
                if (ttsState.message.isNotBlank()) {
                    Text(
                        ttsState.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (rows.isEmpty()) {
            item { CenteredHint("还没有内容：粘贴文本 → 点「切句」→ 点「开始生草」。") }
        } else {
            items(rows, key = { it.index }) { row ->
                ResultRowCard(row, state.chains.size) {
                    GrassApp.tts.speak(listOf(row.text), settings.finalLang, settings.ttsRate, settings.ttsPitch)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showParams) {
        ParamsDialog(
            settings = settings,
            onDismiss = { showParams = false },
            onChange = { s -> GrassApp.settings.update { s } },
        )
    }

    if (showJobLog) {
        AlertDialog(
            onDismissRequest = { showJobLog = false },
            title = { Text("任务日志") },
            text = {
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (state.log.isEmpty()) {
                        Text("暂无日志", style = MaterialTheme.typography.bodySmall)
                    } else {
                        state.log.forEach { MonoText(it) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { ctx.copy(state.log.joinToString("\n"), "日志") }) { Text("复制") }
            },
            dismissButton = { TextButton(onClick = { showJobLog = false }) { Text("关闭") } },
        )
    }

    if (showLangPool) {
        LangPoolDialog(
            settings = settings,
            onDismiss = { showLangPool = false },
            onConfirm = { selected -> GrassApp.settings.update { it.copy(selectedLangs = selected) } },
        )
    }

    if (audioBusy) {
        AudioDialog(
            settings = settings,
            rowCount = rows.size,
            text = translatedAll,
            progress = audioText,
            onDismiss = { audioBusy = false },
            onStart = { lang, asFull, asZip, prefix ->
                audioBusy = false
                audioRunning = true
                audioText = "准备中…"
                scope.launch {
                    try {
                        runAudioExport(ctx, translatedAll, rows.map { it.text }, lang, asFull, asZip, prefix, settings) {
                            audioText = it
                        }
                    } finally {
                        audioRunning = false
                    }
                }
            },
        )
    }
}

@Composable
private fun ResultRowCard(row: JobRow, totalSteps: Int, onSpeak: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${row.index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(8.dp))
                StatusChip(row.status, row.stepDone, totalSteps)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = "朗读",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                CopyIconButton(row.text, "译文")
            }
            Text(row.original, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(
                row.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            row.error?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatusChip(status: RowStatus, stepDone: Int, total: Int) {
    val label = when (status) {
        RowStatus.PENDING -> "待翻译"
        RowStatus.RUNNING -> "进行中"
        RowStatus.OK -> "$stepDone/$total 轮"
        RowStatus.FAILED -> "失败"
    }
    val color = when (status) {
        RowStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
        RowStatus.RUNNING -> MaterialTheme.colorScheme.secondaryContainer
        RowStatus.OK -> MaterialTheme.colorScheme.primaryContainer
        RowStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun exportText(ctx: Context, original: String, translated: String, compare: String) {
    if (compare.isBlank()) {
        ctx.toast("没有可导出的内容")
        return
    }
    try {
        val name = Saver.uniqueName("生草结果", "txt")
        val content = buildString {
            appendLine("谷歌生草机 · 导出于 ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("===== 对照 =====")
            appendLine(compare)
            appendLine()
            appendLine("===== 原文 =====")
            appendLine(original)
            appendLine()
            appendLine("===== 译文 =====")
            appendLine(translated)
        }
        val saved = Saver.save(ctx, name, "text/plain", false) { it.write(content.toByteArray()) }
        ctx.toast("已导出到 ${saved.displayPath}")
    } catch (e: Exception) {
        ctx.toast("导出失败：${e.message}")
    }
}

private suspend fun runAudioExport(
    ctx: Context,
    fullText: String,
    lines: List<String>,
    lang: String,
    asFull: Boolean,
    asZip: Boolean,
    prefix: String,
    settings: Settings,
    progress: (String) -> Unit,
) {
    WorkService.update(ctx, "正在合成音频", "准备中…")
    try {
        val timeout = settings.timeoutSec * 1000L
        val subdir = settings.audioSubdir
        var fullPath = ""
        var zipPath = ""
        if (asFull) {
            progress("正在合成完整 MP3…")
            val mp3 = GoogleTts.synthesize(fullText, lang, timeout) { d, t ->
                progress("完整 MP3：$d/$t 段")
                WorkService.update(ctx, "正在合成完整音频", "$d/$t 段")
            }
            val saved = withContext(Dispatchers.IO) {
                Saver.saveBytes(ctx, Saver.uniqueName(prefix, "mp3"), "audio/mpeg", subdir, mp3)
            }
            fullPath = saved.displayPath
        }
        if (asZip) {
            progress("正在逐行合成并打包…")
            val segs = GoogleTts.synthesizeLines(lines.filter { it.isNotBlank() }, lang, timeout) { d, t ->
                progress("分段 MP3：$d/$t 行")
                WorkService.update(ctx, "正在合成分段音频", "$d/$t 行")
            }
            val entries = segs.map { (idx, bytes) -> String.format("%s_%03d.mp3", prefix, idx + 1) to bytes }
            val zipBytes = withContext(Dispatchers.IO) { Saver.zip(entries) }
            val saved = withContext(Dispatchers.IO) {
                Saver.saveBytes(ctx, Saver.uniqueName("${prefix}_分段", "zip"), "application/zip", subdir, zipBytes)
            }
            zipPath = saved.displayPath
        }
        val msg = buildString {
            append("音频已保存")
            if (fullPath.isNotEmpty()) append("\n· $fullPath")
            if (zipPath.isNotEmpty()) append("\n· $zipPath（${lines.count { it.isNotBlank() }} 个分段）")
        }
        progress(msg)
        ctx.toast("音频已保存到 Download 目录")
    } catch (e: Exception) {
        progress("失败：${e.message}")
        ctx.toast("音频生成失败：${e.message}")
    } finally {
        WorkService.stop(ctx)
    }
}

@Composable
private fun AudioDialog(
    settings: Settings,
    rowCount: Int,
    text: String,
    progress: String,
    onDismiss: () -> Unit,
    onStart: (lang: String, full: Boolean, zip: Boolean, prefix: String) -> Unit,
) {
    var asFull by remember { mutableStateOf(true) }
    var asZip by remember { mutableStateOf(true) }
    var prefix by remember { mutableStateOf(settings.audioPrefix) }
    var lang by remember { mutableStateOf(settings.finalLang) }
    val langs = remember { listOf(settings.finalLang) + LangData.COMMON.filter { it != settings.finalLang } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音频下载") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "使用 Google 在线 TTS（音色与网页版翻译一致）合成 mp3，保存到手机 Download 目录。",
                    style = MaterialTheme.typography.bodySmall,
                )
                SettingRow("完整 MP3", "把全部译文合成一个文件") {
                    Switch(checked = asFull, onCheckedChange = { asFull = it })
                }
                SettingRow("分段 MP3 压缩包", "每行一个 mp3，打包成 zip（$rowCount 行）") {
                    Switch(checked = asZip, onCheckedChange = { asZip = it })
                }
                SettingRow("朗读语言", LangData.name(lang)) {
                    TextButton(onClick = {
                        val i = langs.indexOf(lang)
                        lang = langs[(i + 1) % langs.size]
                    }) { Text("切换") }
                }
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text("文件名前缀") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (progress.isNotBlank()) {
                    Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(lang, asFull, asZip, prefix.ifBlank { "生草" }) },
                enabled = (asFull || asZip) && text.isNotBlank(),
            ) { Text("开始") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 参数快速调整弹窗 */
@Composable
fun ParamsDialog(settings: Settings, onDismiss: () -> Unit, onChange: (Settings) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生草参数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NumberStepper("翻译次数（轮）", settings.times, 1f..50f) { onChange(settings.copy(times = it)) }
                NumberStepper("单次请求超时（秒）", settings.timeoutSec, 3f..120f) { onChange(settings.copy(timeoutSec = it)) }
                NumberStepper("出错重试次数", settings.retries, 0f..20f) { onChange(settings.copy(retries = it)) }
                NumberStepper("并发线程数", settings.concurrency, 1f..40f) { onChange(settings.copy(concurrency = it)) }
                NumberStepper("请求最小间隔（毫秒）", settings.minIntervalMs, 0f..2000f, step = 50) {
                    onChange(settings.copy(minIntervalMs = it))
                }
                SettingRow("随机顺序", "关闭则按语种表顺序翻译") {
                    Switch(checked = settings.randomOrder, onCheckedChange = { onChange(settings.copy(randomOrder = it)) })
                }
                SettingRow("最终语言", LangData.name(settings.finalLang)) {
                    TextButton(onClick = {
                        val idx = LangData.COMMON.indexOf(settings.finalLang)
                        val next = LangData.COMMON[((idx + 1).coerceAtLeast(0)) % LangData.COMMON.size]
                        onChange(settings.copy(finalLang = next))
                    }) { Text("切换") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
fun NumberStepper(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceAtLeast(range.start.toInt())) }) { Text("－") }
        Text(
            "$value",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = { onChange((value + step).coerceAtMost(range.endInclusive.toInt())) }) { Text("＋") }
    }
}
