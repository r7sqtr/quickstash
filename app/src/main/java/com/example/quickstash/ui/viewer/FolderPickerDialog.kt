package com.example.quickstash.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.quickstash.data.nas.NasFile

@Composable
fun FolderPickerDialog(
    startPath: String,
    loadDirectories: suspend (String) -> List<NasFile>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var currentPath by remember { mutableStateOf(startPath) }
    var directories by remember { mutableStateOf<List<NasFile>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath) {
        directories = null
        error = null
        try {
            directories = loadDirectories(currentPath)
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            directories = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "移動先: /${currentPath.ifEmpty { "（ルート）" }}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Box(modifier = Modifier.height(320.dp)) {
                when {
                    directories == null -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    error != null -> {
                        Text(
                            text = error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        LazyColumn {
                            if (currentPath.isNotEmpty()) {
                                item(key = "..") {
                                    PickerRow(label = "⬆ 上のフォルダへ") {
                                        currentPath = currentPath.substringBeforeLast('/', "")
                                    }
                                }
                            }
                            items(directories.orEmpty(), key = { it.path }) { dir ->
                                PickerRow(label = "📁 ${dir.name}") {
                                    currentPath = dir.path
                                }
                            }
                            if (directories.orEmpty().isEmpty() && currentPath.isNotEmpty()) {
                                item(key = "empty") {
                                    Text(
                                        text = "サブフォルダはありません",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(currentPath) }) {
                Text("ここに移動")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

@Composable
private fun PickerRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
