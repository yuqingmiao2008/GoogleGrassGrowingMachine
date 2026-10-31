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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yuqingmiao2008.grassmachine.GrassApp
import io.github.yuqingmiao2008.grassmachine.core.Saver
import io.github.yuqingmiao2008.grassmachine.data.HistoryRecord
import io.github.yuqingmiao2008.grassmachine.data.LangData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val history by GrassApp.history.flow.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var onlyFav by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<HistoryRecord?>(null) }

    val list = remember(history, query, onlyFav) {
        history.filter { r ->
            (!onlyFav || r.favorite) && (
                query.isBlank() || r.lines.any { it.src.contains(query, true) || it.dst.contains(query, true) }
                )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionCard(title = "历史记录（${history.size} 条）", subtitle = "生草完成后会自动保存；最多保留 300 条") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索原文或译文") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onlyFav = !onlyFav }) {
                        Text(if (onlyFav) "显示全部" else "只看收藏")
                    }
                    TextButton(onClick = {
                        val text = history.joinToString("\n\n") { r ->
                            "===== ${formatTime(r.time)} · ${r.times} 轮 =====\n" +
                                r.lines.joinToString("\n") { "${it.src}\n-> ${it.dst}" }
                        }
                        if (text.isBlank()) {
                            ctx.toast("没有历史")
                        } else {
                            runCatching {
                                Saver.save(ctx, Saver.uniqueName("生草历史", "txt"), "text/plain", false) {
                                    it.write(text.toByteArray())
                                }
                            }.onSuccess { ctx.toast("历史已导出到 Download") }
                                .onFailure { ctx.toast("导出失败：${it.message}") }
                        }
                    }) { Text("导出全部") }
                    TextButton(onClick = {
                        GrassApp.history.clear()
                        ctx.toast("已清空")
                    }) { Text("清空") }
                }
            }
        }

        if (list.isEmpty()) {
            item { CenteredHint("没有匹配的记录。") }
        } else {
            items(list, key = { it.id }) { rec ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatTime(rec.time),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "${rec.times} 轮 · ${rec.lines.size} 行",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { GrassApp.history.toggleFavorite(rec.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (rec.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "收藏",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { GrassApp.history.delete(rec.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Text(
                            rec.preview,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            TextButton(onClick = { detail = rec }) { Text("查看") }
                            TextButton(onClick = {
                                ctx.copy(rec.lines.joinToString("\n") { it.dst }, "译文")
                            }) { Text("复制译文") }
                            TextButton(onClick = {
                                ctx.copy(
                                    rec.lines.joinToString("\n\n") { "${it.src}\n-> ${it.dst}" },
                                    "对照",
                                )
                            }) { Text("复制对照") }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    detail?.let { rec ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(formatTime(rec.time)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        rec.chains.joinToString(" → ") { LangData.name(it) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    rec.lines.forEach { l ->
                        Text(l.src, style = MaterialTheme.typography.bodySmall)
                        Text(
                            l.dst,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ctx.copy(rec.lines.joinToString("\n") { it.dst }, "译文")
                }) { Text("复制译文") }
            },
            dismissButton = {
                TextButton(onClick = { detail = null }) { Text("关闭") }
            },
        )
    }
}

private fun formatTime(t: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t))
