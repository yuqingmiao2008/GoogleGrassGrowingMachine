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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yuqingmiao2008.grassmachine.GrassApp
import io.github.yuqingmiao2008.grassmachine.core.IpScanner
import io.github.yuqingmiao2008.grassmachine.core.Net
import io.github.yuqingmiao2008.grassmachine.core.RootHosts
import io.github.yuqingmiao2008.grassmachine.core.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IpCheckScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by GrassApp.settings.state.collectAsStateWithLifecycle()

    val logFlow = remember { MutableStateFlow<List<String>>(emptyList()) }
    val log by logFlow.collectAsStateWithLifecycle()
    val progFlow = remember { MutableStateFlow(0 to 0) }
    val prog by progFlow.collectAsStateWithLifecycle()

    var scanning by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var results by remember { mutableStateOf<List<IpScanner.Result>>(emptyList()) }
    var selectedIp by remember { mutableStateOf(settings.activeIp) }
    var manualIp by remember { mutableStateOf("") }
    var rooted by remember { mutableStateOf(false) }
    var rootBusy by remember { mutableStateOf(false) }

    fun append(msg: String) = logFlow.update { (it + msg).takeLast(500) }

    LaunchedEffect(Unit) {
        rooted = RootHosts.isRooted()
        if (settings.ipList.isNotEmpty()) {
            append("载入上次保存的 IP ${settings.ipList.size} 个")
        }
        append("当前使用：${Net.activeIp ?: "系统 DNS（未注入 IP）"}")
    }

    fun applyIp(ip: String) {
        Net.setActiveIp(ip)
        GrassApp.settings.update { it.copy(activeIp = ip, useScannedIp = true) }
        selectedIp = ip
        append("已启用 IP：$ip（翻译与 TTS 请求都会走它）")
        ctx.toast("已启用 $ip")
    }

    fun useSystemDns() {
        Net.setActiveIp(null)
        GrassApp.settings.update { it.copy(activeIp = "", useScannedIp = false) }
        selectedIp = ""
        append("已恢复系统 DNS（国内网络下通常无法直连）")
        ctx.toast("已恢复系统 DNS")
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Google 翻译 IP 检查",
                subtitle = "移植自 GoogleTranslateIpCheck 的思路：扫描 Google 边缘 IP，把可用 IP 直接注入本应用的 DNS，无需 root 即可让翻译与 TTS 走通",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("当前 IP", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        Net.activeIp ?: "系统 DNS",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (scanning) {
                    LinearProgressIndicator(
                        progress = {
                            if (prog.second <= 0) 0f else prog.first.toFloat() / prog.second
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "扫描中 ${prog.first}/${prog.second}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (scanning) return@Button
                            scanning = true
                            results = emptyList()
                            append("==== 开始扫描 ====")
                            scanJob = scope.launch {
                                try {
                                    val list = IpScanner().scan(
                                        sampleCount = settings.scanSamples,
                                        threads = settings.scanThreads,
                                        timeoutMs = settings.scanTimeoutMs.toLong(),
                                        log = { append(it) },
                                        onProgress = { d, t -> progFlow.value = d to t },
                                    )
                                    results = list
                                    GrassApp.settings.update { it.copy(ipList = list.map { r -> r.ip }) }
                                    if (list.isNotEmpty()) {
                                        applyIp(list.first().ip)
                                    } else {
                                        append("未找到可用 IP。可调大取样量或换网络（如移动数据）再试")
                                    }
                                } catch (e: Exception) {
                                    append("扫描失败：${e.message}")
                                } finally {
                                    scanning = false
                                    progFlow.value = 0 to 0
                                }
                            }
                        },
                        enabled = !scanning,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("开始扫描")
                    }
                    if (scanning) {
                        OutlinedButton(onClick = {
                            scanJob?.cancel()
                            scanning = false
                            append("已停止扫描")
                        }) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("停止")
                        }
                    }
                    OutlinedButton(onClick = { useSystemDns() }) { Text("系统 DNS") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            append("==== 网络诊断 ====")
                            Translator.diagnose { append(it) }
                        }
                    }) { Text("网络诊断") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            append("==== 保存的 IP 逐个复测 ====")
                            val saved = settings.ipList
                            if (saved.isEmpty()) {
                                append("没有保存的 IP")
                                return@launch
                            }
                            withContext(Dispatchers.IO) {
                                saved.take(20).forEach { ip ->
                                    val ms = IpScanner().verify(ip, 8000)
                                    append(if (ms != null) "✓ $ip  ${ms}ms" else "✗ $ip  不可用")
                                }
                            }
                        }
                    }) { Text("复测保存 IP") }
                }
            }
        }

        item {
            SectionCard(title = "扫描参数", subtitle = "取样越多越慢；国内移动网络通常 600 个就能扫到可用 IP") {
                NumberStepper("取样 IP 数量", settings.scanSamples, 50f..3000f, step = 50) {
                    GrassApp.settings.update { s -> s.copy(scanSamples = it) }
                }
                NumberStepper("并发线程", settings.scanThreads, 4f..256f, step = 4) {
                    GrassApp.settings.update { s -> s.copy(scanThreads = it) }
                }
                NumberStepper("单 IP 超时（毫秒）", settings.scanTimeoutMs, 500f..10000f, step = 100) {
                    GrassApp.settings.update { s -> s.copy(scanTimeoutMs = it) }
                }
            }
        }

        if (results.isNotEmpty()) {
            item {
                SectionCard(title = "可用 IP（${results.size}）", subtitle = "点击选择后启用；已自动启用最快的一个") {
                    results.forEach { r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = selectedIp == r.ip, onClick = { applyIp(r.ip) })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedIp == r.ip, onClick = { applyIp(r.ip) })
                            Text(r.ip, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                "${r.latencyMs}ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            label = { Text("手动添加 IP") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val ip = manualIp.trim()
                                if (ip.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) {
                                    applyIp(ip)
                                    manualIp = ""
                                } else {
                                    ctx.toast("IP 格式不正确")
                                }
                            },
                        ) { Text("启用") }
                    }
                }
            }
        }

        item {
            SectionCard(title = "运行日志", subtitle = "扫描与诊断的实时输出，可复制反馈问题") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
                ) {
                    Column(
                        Modifier
                            .height(200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                    ) {
                        if (log.isEmpty()) {
                            Text(
                                "暂无日志，点击「开始扫描」",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            log.forEach { MonoText(it) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { ctx.copy(log.joinToString("\n"), "日志") }) { Text("复制日志") }
                    TextButton(onClick = { logFlow.value = emptyList() }) { Text("清空") }
                }
            }
        }

        item {
            SectionCard(
                title = "写入系统 hosts（实验，需 root）",
                subtitle = "本应用自身不需要 root。只有想让其它 App（如 Google 翻译）也用这些 IP 时才需要；未 root 时此项无效",
            ) {
                SettingRow("设备已 root", if (rooted) "已检测到 root 权限" else "未检测到 root（su 不可用）") {
                    Switch(
                        checked = settings.rootHosts && rooted,
                        enabled = rooted,
                        onCheckedChange = { on ->
                            GrassApp.settings.update { it.copy(rootHosts = on) }
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            rootBusy = true
                            scope.launch {
                                val ips = results.map { it.ip }.ifEmpty { settings.ipList }
                                val res = withContext(Dispatchers.IO) { RootHosts.apply(ips) }
                                append(if (res.ok) "hosts 写入成功" else "hosts 写入失败：${res.out}")
                                append(res.out)
                                rootBusy = false
                            }
                        },
                        enabled = rooted && !rootBusy,
                    ) { Text("写入 hosts") }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val res = withContext(Dispatchers.IO) { RootHosts.restore() }
                                append(if (res.ok) "已还原 hosts 备份" else "还原失败：${res.out}")
                            }
                        },
                        enabled = rooted,
                    ) { Text("还原备份") }
                }
            }
        }

        item {
            InfoBanner(
                "原理说明：Desktop 版调用 GoogleTranslateIpCheck 写 hosts；Android 非 root 无法改 hosts，" +
                    "因此本应用把扫描结果注入自己的 DNS 解析（OkHttp Dns 覆盖），效果等价且更安全。" +
                    "扫描分两阶段：先测 TLS 握手筛掉不通的 IP，再用真实翻译请求校验，避免「能握手但返回 404」的假可用 IP。"
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
