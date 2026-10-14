package com.videofilter.app.ui.components

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videofilter.app.data.pref.FolderPreferences
import com.videofilter.app.ui.theme.LocalAppDimens
import java.io.File

/**
 * 底部悬浮操作栏：目标路径展示 + 白名单 / 移动 双动作
 */
@Composable
fun MoveBottomBar(
    selectedCount: Int,
    targetPath: String,
    onTargetFolderChange: (String) -> Unit,
    onAddToWhitelist: () -> Unit,
    onExecuteMove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalAppDimens.current
    var showFolderDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = dimens.cardCornerRadius,
                    topEnd = dimens.cardCornerRadius
                )
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenHorizontalPadding, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = "目标目录",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = FolderPreferences.toDisplayPath(targetPath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                IconButton(
                    onClick = { showFolderDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "修改目标目录",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 加入白名单：永久免审核
                OutlinedButton(
                    onClick = onAddToWhitelist,
                    enabled = selectedCount > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(dimens.minTouchTargetSize),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "白名单 ($selectedCount)",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                // 移动到此目录
                Button(
                    onClick = onExecuteMove,
                    enabled = selectedCount > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(dimens.minTouchTargetSize),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "移动 ($selectedCount)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }

    if (showFolderDialog) {
        FolderSelectDialog(
            currentPath = targetPath,
            onDismiss = { showFolderDialog = false },
            onConfirm = {
                onTargetFolderChange(it)
                showFolderDialog = false
            }
        )
    }
}

/**
 * 输出目录选择弹窗。
 *
 * 交互以**相对路径输入**为主（如 `a/v/b` 即 `/storage/emulated/0/a/v/b`），
 * 免去在手机输入法下敲一长串绝对路径；同时提供「用文件管理器选择」走系统 SAF 选择器。
 */
@Composable
fun FolderSelectDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var relativeInput by remember {
        mutableStateOf(FolderPreferences.toDisplayPath(currentPath))
    }
    var pickedHint by remember { mutableStateOf("") }

    val resolvedFile: File? = FolderPreferences.resolveUserInput(relativeInput)

    // 系统文件管理器（SAF）选择器
    val treePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val picked = uri?.let { treeUriToFile(it) }
        if (picked != null) {
            relativeInput = FolderPreferences.toDisplayPath(picked.absolutePath)
            pickedHint = "已从文件管理器选择"
        } else if (uri != null) {
            pickedHint = "该来源无法解析为本地路径，请手动输入"
        }
    }

    val quickPicks = listOf(
        "Movies/FilteredVideos",
        "Download/Videos",
        "DCIM/Camera",
        "Pictures"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输出目录") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "直接输入相对路径即可，例如 a/v/b",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = relativeInput,
                    onValueChange = {
                        relativeInput = it
                        pickedHint = ""
                    },
                    label = { Text("相对路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "实际位置：${resolvedFile?.absolutePath ?: "（路径无效）"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (resolvedFile != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )

                if (pickedHint.isNotEmpty()) {
                    Text(
                        text = pickedHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = { treePicker.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(text = "用文件管理器选择", modifier = Modifier.padding(start = 6.dp))
                }

                Text(
                    text = "快捷相对路径：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                quickPicks.forEach { path ->
                    SuggestionChip(
                        onClick = {
                            relativeInput = path
                            pickedHint = ""
                        },
                        label = {
                            Text(text = path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = FolderPreferences.resolveUserInput(relativeInput)
                    if (target != null) {
                        if (!target.exists()) target.mkdirs()
                        onConfirm(target.absolutePath)
                    }
                },
                enabled = resolvedFile != null
            ) {
                Text("确定沿用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 把 SAF 返回的 tree Uri 反解为本地绝对路径。
 *
 * 仅处理外部存储提供方（`com.android.externalstorage.documents`）；
 * 例如 `content://com.android.externalstorage.documents/tree/primary%3AMovies%2FFilteredVideos`
 * 会被解析为 `/storage/emulated/0/Movies/FilteredVideos`。
 * 由于本应用已持有 MANAGE_EXTERNAL_STORAGE，直接使用真实路径即可，无需持久化 SAF 授权。
 */
private fun treeUriToFile(uri: Uri): File? {
    if (uri.authority != EXTERNAL_STORAGE_AUTHORITY) return null

    val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        ?: return null

    val segments = documentId.split(":", limit = 2)
    val volumeId = segments.firstOrNull().orEmpty()
    val relative = segments.getOrNull(1).orEmpty()

    val baseDir = when {
        volumeId.equals("primary", ignoreCase = true) -> Environment.getExternalStorageDirectory()
        volumeId.isBlank() -> return null
        else -> File("/storage", volumeId)
    }

    return if (relative.isBlank()) baseDir else File(baseDir, relative)
}

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
