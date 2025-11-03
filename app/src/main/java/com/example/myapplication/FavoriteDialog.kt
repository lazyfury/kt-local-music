package com.example.myapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 收藏选择弹窗：显示现有收藏列表，支持新建列表，并将指定歌曲加入选择的列表。
 */
@Composable
fun FavoriteSelectorDialog(
    trackId: Long,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember { FavoriteManager(context) }
    var listNames by remember { mutableStateOf(manager.getAllLists().toList()) }
    var newListName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    // 初始化：默认勾选已包含该歌曲的列表
    LaunchedEffect(trackId) {
        val initiallySelected = listNames.filter { manager.isTrackInList(it, trackId) }.toSet()
        selected = initiallySelected
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "选择收藏列表") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (listNames.isEmpty()) {
                    Text(text = "暂无收藏列表，可在下方新建", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listNames.forEach { name ->
                            val checked = name in selected
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = name, style = MaterialTheme.typography.bodyMedium)
                                Checkbox(checked = checked, onCheckedChange = { c ->
                                    selected = if (c) selected + name else selected - name
                                })
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("新建收藏列表") },
                    placeholder = { Text("输入列表名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = {
                        val name = newListName.trim()
                        if (name.isNotBlank()) {
                            if (manager.createList(name)) {
                                listNames = manager.getAllLists().toList()
                                // 新建的列表默认选中
                                selected = selected + name
                                newListName = ""
                            }
                        }
                    }) { Text("新建并选中") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 应用：将歌曲加入选中的所有列表
                selected.forEach { manager.addTrackToList(it, trackId) }
                onApplied()
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}