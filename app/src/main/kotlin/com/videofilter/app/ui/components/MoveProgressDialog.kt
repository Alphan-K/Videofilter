package com.videofilter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videofilter.app.model.MoveProgressEvent

/**
 * 移动任务进度与结果反馈弹窗
 */
@Composable
fun MoveProgressDialog(
    event: MoveProgressEvent,
    onDismiss: () -> Unit
) {
    when (event) {
        is MoveProgressEvent.Validating -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("准备中") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(event.message)
                    }
                }
            )
        }

        is MoveProgressEvent.InsufficientStorage -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(" 存储空间不足", color = MaterialTheme.colorScheme.error)
                    }
                },
                text = {
                    val reqMb = event.requiredBytes / (1024 * 1024)
                    val availMb = event.availableBytes / (1024 * 1024)
                    Text(
                        "目标驱动器剩余可用空间不足。\n\n" +
                            "需要空间: $reqMb MB\n" +
                            "当前剩余: $availMb MB\n\n" +
                            "为避免写满存储导致系统异常，本次移动已被安全熔断，源文件保持原状。"
                    )
                },
                confirmButton = {
                    Button(onClick = onDismiss) { Text("了解") }
                }
            )
        }

        is MoveProgressEvent.Processing -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("正在迁移视频 (${event.currentIndex}/${event.totalCount})") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = event.currentFileName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val fraction = if (event.totalBytes > 0) {
                            (event.processedBytes.toFloat() / event.totalBytes.toFloat())
                                .coerceIn(0f, 1f)
                        } else if (event.totalCount > 0) {
                            (event.currentIndex.toFloat() / event.totalCount.toFloat())
                                .coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${(fraction * 100).toInt()}% 完成",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }

        is MoveProgressEvent.Completed -> {
            val report = event.report
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (report.isAllSucceeded) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.ErrorOutline
                            },
                            contentDescription = null,
                            tint = if (report.isAllSucceeded) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Text(if (report.isAllSucceeded) " 迁移顺利完成" else " 迁移处理完毕")
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "成功移动: ${report.successList.size} 个视频",
                            fontWeight = FontWeight.SemiBold
                        )
                        if (report.skippedCount > 0) {
                            Text("无需移动（已在目标目录）: ${report.skippedCount} 个")
                        }
                        if (report.failedList.isNotEmpty()) {
                            Text(
                                text = "迁移失败: ${report.failedList.size} 个",
                                color = MaterialTheme.colorScheme.error
                            )
                            report.failedList.take(3).forEach {
                                Text(
                                    text = "• ${it.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = onDismiss) { Text("完成") }
                }
            )
        }
    }
}
