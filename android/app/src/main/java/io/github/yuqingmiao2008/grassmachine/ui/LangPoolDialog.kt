package io.github.yuqingmiao2008.grassmachine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.yuqingmiao2008.grassmachine.data.LangData
import io.github.yuqingmiao2008.grassmachine.data.Settings

/**
 * 语种池选择：支持搜索、全选、清空、只看常用。默认全选（Google 翻译支持的全部语种）。
 */
@Composable
fun LangPoolDialog(
    settings: Settings,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(settings.selectedLangs) }
    var query by remember { mutableStateOf("") }
    var onlyCommon by remember { mutableStateOf(false) }

    val list = remember(query, onlyCommon) {
        val base = if (onlyCommon) LangData.COMMON else LangData.ALL_SORTED
        if (query.isBlank()) base
        else base.filter { LangData.name(it).contains(query, true) || it.contains(query, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("参与随机的语种（已选 ${selected.size}）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索语种") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { selected = selected + LangData.ALL_CODES }) { Text("全选") }
                    TextButton(onClick = { selected = emptySet() }) { Text("清空") }
                    TextButton(onClick = { onlyCommon = !onlyCommon }) {
                        Text(if (onlyCommon) "全部语种" else "只看常用")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selected.containsAll(list),
                        onCheckedChange = { c ->
                            selected = if (c) selected + list else selected - list.toSet()
                        },
                    )
                    Text("全选当前列表（${list.size}）", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.height(340.dp)) {
                    items(list, key = { it }) { code ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = selected.contains(code),
                                    role = Role.Checkbox,
                                    onValueChange = { c ->
                                        selected = if (c) selected + code else selected - code
                                    },
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = selected.contains(code), onCheckedChange = null)
                            Column(Modifier.padding(start = 6.dp)) {
                                Text(LangData.name(code), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (selected.isEmpty()) {
                    Text(
                        "语种池为空时会自动回退为全部语种",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selected)
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
