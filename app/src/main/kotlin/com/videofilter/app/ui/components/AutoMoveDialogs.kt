package com.videofilter.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videofilter.app.model.VideoItem
import com.videofilter.app.scanner.VideoFolder
import java.util.Locale

/**
 * 扫描完成后的自动询问弹窗：是否把本次筛选出的视频移动到目标目录。
 *
 * 弹窗内直接提供「以后是否继续询问」的开关，避免用户还要再去设置里关一遍。
 */
@Composable
fun AutoMovePromptDialog(
    folder: VideoFolder,
    candidates: List<VideoItem>,
    targetPath: String,
    isFolderPromptEnabled: Boolean,
    onToggleFolderPrompt: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalBytes = candidates.sumOf { it.sizeBytes }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "是否移动到 ${shortPath(targetPath)}？",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${folder.displayName} 扫描完成，筛选出 " +
                        "${candidates.size} 个视频（${formatSize(totalBytes)}）" +
                        "需要归档。",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "目标位置：$targetPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 就地开关：不想每次扫 DCIM 都弹窗，在这里关掉即可
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleFolderPrompt(!isFolderPromptEnabled) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "扫描完 ${folder.displayName} 后自动询问",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "关闭后可在顶部 ⚙ 里重新打开",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isFolderPromptEnabled,
                        onCheckedChange = onToggleFolderPrompt
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = "移动", modifier = Modifier.padding(start = 6.dp))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("暂不") }
        }
    )
}

/**
 * 自动询问开关的设置面板：按目录逐个开关，互不影响。
 */
@Composable
fun AutoMoveSettingsDialog(
    enabledFolders: Set<VideoFolder>,
    onToggle: (VideoFolder, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(text = "扫描后自动询问移动", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "打开后，扫描该目录结束时若筛出了视频，会直接询问是否移动到目标目录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    VideoFolder.entries.forEachIndexed { index, folder ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onToggle(folder, folder !in enabledFolders)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folder.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = folder.relativePath?.let { "/$it 目录" }
                                        ?: "重点目录之外的视频",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = folder in enabledFolders,
                                onCheckedChange = { onToggle(folder, it) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("完成") }
        }
    )
}

/** 只保留路径的最后两段，用于弹窗标题，避免过长换行 */
private fun shortPath(path: String): String {
    val segments = path.trimEnd('/').split('/').filter { it.isNotEmpty() }
    return when {
        segments.isEmpty() -> path
        segments.size == 1 -> segments[0]
        else -> segments.takeLast(2).joinToString("/")
    }
}

private fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "0 B"
    val mb = sizeBytes / (1024f * 1024f)
    return if (mb >= 1024f) {
        String.format(Locale.ROOT, "%.2f GB", mb / 1024f)
    } else {
        String.format(Locale.ROOT, "%.1f MB", mb)
    }
}
