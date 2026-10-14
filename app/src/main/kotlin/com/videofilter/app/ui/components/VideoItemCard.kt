package com.videofilter.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videofilter.app.model.VideoItem
import com.videofilter.app.ui.loader.rememberVideoThumbnail
import com.videofilter.app.ui.theme.LocalAppDimens
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun VideoItemCard(
    video: VideoItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onCardClick: () -> Unit,
    onCardLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalAppDimens.current
    val cardShape = RoundedCornerShape(dimens.cardCornerRadius)
    val thumbnail: Bitmap? = rememberVideoThumbnail(video.path)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = cardShape
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = onCardLongClick
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardVerticalPadding)
        ) {
            // 缩略图 + 时长/分辨率角标 + 多选指示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.videoCardThumbnailHeight)
                    .clip(RoundedCornerShape(dimens.cardCornerRadius - 6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = video.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                }

                // 右上角：选择模式下的勾选圈（莫奈色块填充 + 缩放动画）
                SelectionBadge(
                    visible = isSelectionMode,
                    isSelected = isSelected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )

                // 右下角：时长标签
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(video.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // 左下角：分辨率尺寸
                if (video.width > 0 && video.height > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${video.width}×${video.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 视频标题
            Text(
                text = video.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 物理路径
            Text(
                text = video.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )

            // 核心元数据徽标区
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 1. 码率徽章
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = if (video.bitrateKbps > 0) "${video.bitrateKbps} kbps" else "未知码率",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = null
                )

                // 2. 编码格式徽章
                SuggestionChip(
                    onClick = {},
                    label = { Text(text = video.codec.displayName) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    border = null
                )

                // 3. 封装格式徽章
                SuggestionChip(
                    onClick = {},
                    label = { Text(text = video.containerFormat.displayName) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    border = null
                )

                // 4. 文件体积徽章
                SuggestionChip(
                    onClick = {},
                    label = { Text(text = formatFileSize(video.sizeBytes)) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null
                )
            }
        }
    }
}

/**
 * 多选勾选徽章。
 *
 * 刻意抽成**非扩展**的普通 Composable：`AnimatedVisibility` 同时存在
 * `RowScope` / `ColumnScope` 扩展版本与顶层版本，若直接写在 `Box` 内会因
 * 外层 `Column` 的隐式接收者而产生解析歧义。独立函数内没有作用域接收者，
 * 会稳定命中顶层重载。
 */
@Composable
private fun SelectionBadge(
    visible: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                    }
                )
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 格式化播放时长：mm:ss 或 hh:mm:ss
 */
private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/**
 * 格式化文件尺寸：MB 或 GB
 */
private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val mb = sizeBytes / (1024f * 1024f)
    return if (mb >= 1024f) {
        String.format(Locale.ROOT, "%.2f GB", mb / 1024f)
    } else {
        String.format(Locale.ROOT, "%.1f MB", mb)
    }
}
